package com.grahambartley.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.tts.Pcm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Complete-buffer Gemini speech synthesis authenticated with a Google AI Studio API key. */
@Slf4j
public final class GeminiAiStudioTtsBackend implements SynthesisBackend {

  public static final String ID = "cloud-google-ai-studio";
  public static final String NO_KEY_NOTICE =
      "Add your Google AI Studio API key in the Voiced Dialogue settings to hear dialogue; without"
          + " a key, lines are not voiced.";

  static final String MODEL = "gemini-3.1-flash-tts-preview";
  static final String PRODUCTION_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
  private static final int DEFAULT_SPEED_PERCENT = 100;

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;
  private final GeminiTtsModel model = new GeminiTtsModel();
  private final GeminiAiStudioTranslator translator;
  private final ProfanityFilter profanityFilter = new ProfanityFilter();
  private Consumer<String> notice = message -> {};
  private final AtomicBoolean warned = new AtomicBoolean();

  public GeminiAiStudioTtsBackend(OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson) {
    this(
        httpClient,
        config,
        gson,
        PRODUCTION_ENDPOINT,
        GeminiAiStudioTranslator.PRODUCTION_ENDPOINT);
  }

  GeminiAiStudioTtsBackend(
      OkHttpClient httpClient,
      VoicedDialogueConfig config,
      Gson gson,
      String endpoint,
      String translatorEndpoint) {
    this.httpClient =
        httpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(30))
            .callTimeout(Duration.ofSeconds(45))
            .build();
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
    this.translator =
        new GeminiAiStudioTranslator(this.httpClient, config, gson, translatorEndpoint);
  }

  public void setNotice(Consumer<String> notice) {
    this.notice = notice == null ? message -> {} : notice;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isAvailable() {
    return isNonBlank(config.googleAiStudioApiKey());
  }

  @Override
  public SynthesisRequest prepare(SynthesisRequest request) {
    int creativity = VoicedDialogueConfig.normalizeDialogueCreativity(config.dialogueCreativity());
    VoicedDialogueConfig.SpeakingStyle configured =
        request.player() ? config.cloudPlayerSpeakingStyle() : config.cloudNpcSpeakingStyle();
    VoicedDialogueConfig.SpeakingStyle style =
        request.skipTranslation()
            ? VoicedDialogueConfig.SpeakingStyle.NONE
            : SpeakingStyleResolver.resolve(configured, request);
    if (creativity == 0) {
      style = VoicedDialogueConfig.SpeakingStyle.NONE;
    }
    String voiceDirection =
        style.voiceDirection().isEmpty()
            ? request.profile() != null
                    && config.cloudLanguage() == VoicedDialogueConfig.SpokenLanguage.ENGLISH
                ? ""
                : config.cloudLanguage().pronunciationDirection()
            : style.voiceDirection();
    String language = OpenRouterTtsBackend.combineLanguage(config.cloudLanguage().label(), style);
    boolean maturePersona = creativity > 0 && PersonaRewriteTarget.enabled(config, request);
    if (maturePersona) {
      language = PersonaRewriteTarget.apply(config, request, language);
    }
    return request.withBackendSettings(
        language,
        request.skipTranslation()
                || style.forcesEnglish()
                || config.cloudLanguage() == VoicedDialogueConfig.SpokenLanguage.ENGLISH
            ? null
            : config.cloudLanguage().code(),
        request.skipTranslation() ? "" : voiceDirection,
        maturePersona,
        creativity,
        speedPercent(),
        config.cloudMaxChars());
  }

  @Override
  public String missingKeyNotice() {
    return NO_KEY_NOTICE;
  }

  @Override
  public EnumSet<Emotion> supportedEmotions() {
    return model.supportedEmotions();
  }

  @Override
  public void warmUp() {
    if (!isAvailable()) {
      return;
    }
    Request request =
        new Request.Builder()
            .url(modelEndpoint(endpoint))
            .addHeader("x-goog-api-key", config.googleAiStudioApiKey().trim())
            .addHeader("User-Agent", "runelite-voiced-dialogue")
            .get()
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      log.debug("[TTS AI Studio] warm-up HTTP {}", response.code());
    } catch (IOException | RuntimeException e) {
      log.debug("[TTS AI Studio] warm-up failed: {}", e.getMessage());
    }
  }

  @Override
  public String cacheVariant(SynthesisRequest request) {
    String language = effectiveSpokenLanguage(request);
    boolean rewritten =
        (OpenRouterTtsBackend.needsTranslation(language) || usesContext(request))
            && !request.skipTranslation();
    String languageFragment = rewritten ? language.toLowerCase(Locale.ROOT) : null;
    String variant =
        CloudCacheKeyBuilder.build(
            MODEL,
            model.voiceFor(request.voice()),
            speedPercent(request),
            DEFAULT_SPEED_PERCENT,
            request.text(),
            maxChars(request),
            request.profile(),
            languageFragment);
    return creativityVariant(
        request, rewritten, contextVariant(request, pronunciationVariant(request, variant)));
  }

  @Override
  public Pcm synthesize(SynthesisRequest request) {
    if (!isAvailable()) {
      warnOnce(NO_KEY_NOTICE);
      return null;
    }
    String key = config.googleAiStudioApiKey().trim();
    String text = OpenRouterTtsBackend.capLength(request.text(), maxChars(request));
    String language = effectiveSpokenLanguage(request);
    boolean translating =
        (OpenRouterTtsBackend.needsTranslation(language) || usesContext(request))
            && !request.skipTranslation();
    if (translating) {
      text =
          translator.translate(
              text,
              language,
              key,
              usesContext(request) ? request.context() : null,
              creativity(request));
      if (text == null) {
        warnOnce("Google AI Studio translation failed; this line was not voiced.");
        return null;
      }
      text = OpenRouterTtsBackend.capLength(filterGeneratedText(request, text), maxChars(request));
    }

    String input = model.styleInput(text, request.emotion());
    input = TtsPromptDirections.render(request.profile(), input, request.preparedVoiceDirection());
    int speed = speedPercent(request);
    if (speed != DEFAULT_SPEED_PERCENT) {
      input = "SPEAKING PACE: " + speed + "% of normal.\n\n" + input;
    }

    Request httpRequest =
        buildRequest(input, model.voiceFor(request.voice()), request.preparedLanguageCode(), key);
    try (Response response = httpClient.newCall(httpRequest).execute()) {
      ResponseBody body = response.body();
      String raw = body == null ? "" : body.string();
      if (!response.isSuccessful()) {
        warnOnce(
            "Google AI Studio TTS request failed (HTTP "
                + response.code()
                + "); check your API key and quota. This line was not voiced.");
        return null;
      }
      byte[] audio = extractAudio(raw);
      Pcm pcm = audio == null ? null : model.decodeResponse(audio);
      if (pcm == null) {
        warnOnce("Google AI Studio returned missing or invalid audio; this line was not voiced.");
      }
      return pcm;
    } catch (IOException | RuntimeException e) {
      log.warn("[TTS AI Studio] request failed: {}", e.getMessage());
      warnOnce("Google AI Studio TTS could not reach the network; this line was not voiced.");
      return null;
    }
  }

  private Request buildRequest(String input, String voice, String languageCode, String key) {
    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", input);
    JsonArray parts = new JsonArray();
    parts.add(textPart);
    JsonObject content = new JsonObject();
    content.add("parts", parts);
    JsonArray contents = new JsonArray();
    contents.add(content);

    JsonObject prebuiltVoice = new JsonObject();
    prebuiltVoice.addProperty("voiceName", voice);
    JsonObject voiceConfig = new JsonObject();
    voiceConfig.add("prebuiltVoiceConfig", prebuiltVoice);
    JsonObject speechConfig = new JsonObject();
    speechConfig.add("voiceConfig", voiceConfig);
    if (languageCode != null) {
      speechConfig.addProperty("languageCode", languageCode);
    }
    JsonArray modalities = new JsonArray();
    modalities.add("AUDIO");
    JsonObject generationConfig = new JsonObject();
    generationConfig.add("responseModalities", modalities);
    generationConfig.add("speechConfig", speechConfig);

    JsonObject payload = new JsonObject();
    payload.add("contents", contents);
    payload.add("generationConfig", generationConfig);
    return new Request.Builder()
        .url(endpoint)
        .addHeader("x-goog-api-key", key)
        .addHeader("User-Agent", "runelite-voiced-dialogue")
        .post(
            RequestBody.create(
                JSON_MEDIA_TYPE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
        .build();
  }

  private byte[] extractAudio(String raw) {
    try {
      JsonObject response = gson.fromJson(raw, JsonObject.class);
      JsonArray candidates = response == null ? null : response.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return null;
      }
      JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
      JsonArray parts = content == null ? null : content.getAsJsonArray("parts");
      if (parts == null) {
        return null;
      }
      List<byte[]> chunks = new ArrayList<>();
      for (JsonElement element : parts) {
        JsonObject inlineData = element.getAsJsonObject().getAsJsonObject("inlineData");
        if (inlineData != null && inlineData.has("data")) {
          chunks.add(Base64.getDecoder().decode(inlineData.get("data").getAsString()));
        }
      }
      if (chunks.isEmpty()) {
        return null;
      }
      ByteArrayOutputStream audio = new ByteArrayOutputStream();
      for (byte[] chunk : chunks) {
        audio.write(chunk, 0, chunk.length);
      }
      return audio.toByteArray();
    } catch (RuntimeException e) {
      log.debug("[TTS AI Studio] response parse error: {}", e.getMessage());
      return null;
    }
  }

  private String effectiveSpokenLanguage(SynthesisRequest request) {
    if (request.preparedLanguage() != null) {
      return request.preparedLanguage();
    }
    VoicedDialogueConfig.SpeakingStyle style =
        request.player() ? config.cloudPlayerSpeakingStyle() : config.cloudNpcSpeakingStyle();
    return OpenRouterTtsBackend.combineLanguage(config.cloudLanguage().label(), style);
  }

  private int speedPercent() {
    return Math.max(50, Math.min(200, config.speakingPace()));
  }

  private int speedPercent(SynthesisRequest request) {
    return request.preparedSpeedPercent() > 0 ? request.preparedSpeedPercent() : speedPercent();
  }

  private int maxChars(SynthesisRequest request) {
    return request.preparedMaxChars() >= 0 ? request.preparedMaxChars() : config.cloudMaxChars();
  }

  private String filterGeneratedText(SynthesisRequest request, String text) {
    return request.preparedMaturePersona()
        ? profanityFilter.maskSlurs(text)
        : profanityFilter.mask(text);
  }

  private static String pronunciationVariant(SynthesisRequest request, String variant) {
    if ((request.preparedVoiceDirection() == null
            || request.preparedVoiceDirection().trim().isEmpty())
        && request.preparedLanguageCode() == null) {
      return variant;
    }
    return variant
        + "|accent-v4-"
        + CacheVariantDigest.of(
            String.valueOf(request.preparedVoiceDirection())
                + '\u0001'
                + String.valueOf(request.preparedLanguageCode()));
  }

  private static String contextVariant(SynthesisRequest request, String variant) {
    return !usesContext(request)
        ? variant
        : variant + "|ctx" + CacheVariantDigest.of(request.context());
  }

  private static String creativityVariant(
      SynthesisRequest request, boolean rewritten, String variant) {
    return !rewritten || request.preparedCreativity() < 0
        ? variant
        : variant + "|creative-v2-" + creativity(request);
  }

  private static boolean usesContext(SynthesisRequest request) {
    return request.context() != null && creativity(request) >= 2;
  }

  private static int creativity(SynthesisRequest request) {
    return request.preparedCreativity() >= 0 ? request.preparedCreativity() : 1;
  }

  private static String modelEndpoint(String endpoint) {
    int action = endpoint.indexOf(":generateContent");
    return action < 0 ? endpoint : endpoint.substring(0, action);
  }

  private void warnOnce(String message) {
    log.debug(message);
    if (warned.compareAndSet(false, true)) {
      notice.accept(message);
    }
  }

  private static boolean isNonBlank(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
