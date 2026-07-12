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
import okio.BufferedSource;

/** Complete-buffer Gemini speech synthesis authenticated with a Google AI Studio API key. */
@Slf4j
public final class GeminiAiStudioTtsBackend implements StreamingSynthesisBackend {

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
  private final String streamingEndpoint;
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
    this.streamingEndpoint = streamingEndpoint(endpoint);
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
  public boolean streamingEnabled() {
    return config.experimentalStreamingPlayback();
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
        buildRequest(
            endpoint, input, model.voiceFor(request.voice()), request.preparedLanguageCode(), key);
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

  @Override
  public Pcm synthesizeStreaming(SynthesisRequest request, Consumer<Pcm> onChunk) {
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
        buildRequest(
            streamingEndpoint,
            input,
            model.voiceFor(request.voice()),
            request.preparedLanguageCode(),
            key);
    ByteArrayOutputStream complete = new ByteArrayOutputStream();
    PcmChunkDecoder decoder = new PcmChunkDecoder();
    boolean emitted = false;
    String finishReason = null;
    try (Response response = httpClient.newCall(httpRequest).execute()) {
      if (!response.isSuccessful()) {
        warnOnce(
            "Google AI Studio streaming TTS failed (HTTP "
                + response.code()
                + "); check your API key and quota. This line was not voiced.");
        return null;
      }
      ResponseBody body = response.body();
      if (body == null) {
        warnOnce("Google AI Studio streaming TTS returned no audio; this line was not voiced.");
        return null;
      }
      BufferedSource source = body.source();
      String data;
      while ((data = readSseData(source)) != null) {
        if (data.isEmpty() || "[DONE]".equals(data)) {
          continue;
        }
        String eventFinishReason = extractFinishReason(data);
        if (eventFinishReason != null) {
          finishReason = eventFinishReason;
        }
        for (byte[] audio : extractAudioChunks(data)) {
          complete.write(audio);
          Pcm chunk = decoder.decode(audio);
          if (chunk != null) {
            emitted = true;
            onChunk.accept(chunk);
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      log.warn("[TTS AI Studio] streaming request failed: {}", e.getMessage());
      warnOnce("Google AI Studio TTS could not reach the network; this line was not voiced.");
      return null;
    }

    Pcm pcm = model.decodeResponse(complete.toByteArray());
    if (pcm != null
        && emitted
        && "STOP".equals(finishReason)
        && !PcmCompleteness.isTruncated(pcm)) {
      return pcm;
    }
    warnOnce("Google AI Studio returned incomplete streamed audio; this line was not cached.");
    return null;
  }

  private Request buildRequest(
      String target, String input, String voice, String languageCode, String key) {
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
        .url(target)
        .addHeader("x-goog-api-key", key)
        .addHeader("User-Agent", "runelite-voiced-dialogue")
        .post(
            RequestBody.create(
                JSON_MEDIA_TYPE, gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
        .build();
  }

  private byte[] extractAudio(String raw) {
    List<byte[]> chunks = extractAudioChunks(raw);
    if (chunks.isEmpty()) {
      return null;
    }
    ByteArrayOutputStream audio = new ByteArrayOutputStream();
    for (byte[] chunk : chunks) {
      audio.write(chunk, 0, chunk.length);
    }
    return audio.toByteArray();
  }

  private List<byte[]> extractAudioChunks(String raw) {
    List<byte[]> chunks = new ArrayList<>();
    try {
      JsonObject response = gson.fromJson(raw, JsonObject.class);
      JsonArray candidates = response == null ? null : response.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return chunks;
      }
      JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
      JsonArray parts = content == null ? null : content.getAsJsonArray("parts");
      if (parts == null) {
        return chunks;
      }
      for (JsonElement element : parts) {
        JsonObject part = element.getAsJsonObject();
        JsonObject inlineData = part.getAsJsonObject("inlineData");
        if (inlineData == null) {
          inlineData = part.getAsJsonObject("inline_data");
        }
        if (inlineData != null && inlineData.has("data")) {
          chunks.add(Base64.getDecoder().decode(inlineData.get("data").getAsString()));
        }
      }
      return chunks;
    } catch (RuntimeException e) {
      log.debug("[TTS AI Studio] response parse error: {}", e.getMessage());
      return chunks;
    }
  }

  private static String streamingEndpoint(String endpoint) {
    return endpoint.contains(":generateContent")
        ? endpoint.replace(":generateContent", ":streamGenerateContent") + "?alt=sse"
        : endpoint + "-stream";
  }

  /** Reads one SSE event, joining its data fields as required by the SSE framing rules. */
  private static String readSseData(BufferedSource source) throws IOException {
    StringBuilder data = null;
    String line;
    while ((line = source.readUtf8Line()) != null) {
      if (line.isEmpty()) {
        if (data != null) {
          return data.toString();
        }
        continue;
      }
      if (!line.startsWith("data:")) {
        continue;
      }
      if (data == null) {
        data = new StringBuilder();
      } else {
        data.append('\n');
      }
      String value = line.substring("data:".length());
      data.append(value.startsWith(" ") ? value.substring(1) : value);
    }
    return data == null ? null : data.toString();
  }

  private String extractFinishReason(String raw) {
    try {
      JsonObject response = gson.fromJson(raw, JsonObject.class);
      JsonArray candidates = response == null ? null : response.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return null;
      }
      JsonObject candidate = candidates.get(0).getAsJsonObject();
      return candidate.has("finishReason") ? candidate.get("finishReason").getAsString() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Keeps a trailing odd byte until the next SSE audio part completes its 16-bit PCM sample. */
  private static final class PcmChunkDecoder {
    private int trailingByte = -1;

    Pcm decode(byte[] bytes) {
      if (bytes == null || bytes.length == 0) {
        return null;
      }
      int prefix = trailingByte < 0 ? 0 : 1;
      byte[] combined = new byte[prefix + bytes.length];
      if (prefix == 1) {
        combined[0] = (byte) trailingByte;
      }
      System.arraycopy(bytes, 0, combined, prefix, bytes.length);
      int playableLength = combined.length & ~1;
      trailingByte = playableLength < combined.length ? combined[combined.length - 1] & 0xff : -1;
      if (playableLength == 0) {
        return null;
      }
      byte[] playable = new byte[playableLength];
      System.arraycopy(combined, 0, playable, 0, playableLength);
      return RawPcmDecoder.decode(playable, GeminiTtsModel.SAMPLE_RATE);
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
