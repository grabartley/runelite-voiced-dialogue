package com.grahambartley.runelite.voiced.dialogue.speech.aistudio;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudBackendSupport;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudSpeechExecutor;
import com.grahambartley.runelite.voiced.dialogue.speech.RateLimitBackoff;
import com.grahambartley.runelite.voiced.dialogue.speech.RetryTuning;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiEmotionStyle;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiVoiceMap;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * Cloud synthesis directly through the Gemini API, authenticated with a Google AI Studio key.
 *
 * <p>The direct-to-Google counterpart of {@code OpenRouterTtsBackend}: the same Gemini TTS model,
 * the same {@link GeminiVoiceMap} voices, {@link GeminiEmotionStyle} emotion tags, character
 * profile prompt block, and translation-hop behavior, so a line sounds identical whichever provider
 * voices it. It differs only in transport: requests POST to {@code
 * generativelanguage.googleapis.com}'s {@code generateContent} endpoint (audio comes back as base64
 * PCM inside JSON rather than a raw body), the streaming path reads {@code streamGenerateContent}
 * server-sent events so playback starts on the first decoded chunk, and there is no {@code speed}
 * parameter, so a non-default pace is rendered as a prompt direction.
 *
 * <p>Failure handling mirrors OpenRouter: every failure path (missing key, non-2xx, network error,
 * empty/undecodable audio) returns {@code null} and surfaces a one-time notice rather than
 * throwing, a 429 opens this backend's {@link RateLimitBackoff} window (the wait it states, or a
 * computed one that stands prefetch down), and a truncated line is never cached.
 */
@Slf4j
public final class AiStudioTtsBackend implements SynthesisBackend {

  /** Stable backend id, folded into the synthesis cache key. */
  public static final String ID = "cloud-google-ai-studio";

  /** The Gemini API name of the same model {@link GeminiTtsModel} pins through OpenRouter. */
  static final String MODEL = GeminiTtsModel.GEMINI_MODEL_ID;

  static final String PRODUCTION_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

  /**
   * User-facing notice shown when no API key is set. Shared with the plugin's startup check so the
   * two paths never drift.
   */
  public static final String NO_KEY_NOTICE =
      "Add your Google AI Studio API key in the Voiced Dialogue settings to hear dialogue; without"
          + " a key, lines are not voiced.";

  /** Idle connections are kept this long so back-to-back lines reuse a pooled connection. */
  private static final Duration KEEP_ALIVE = Duration.ofMinutes(5);

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;
  private final String streamingEndpoint;
  private final GeminiTtsModel model = new GeminiTtsModel();
  private final AiStudioTranslator translator;

  /** The shared notice/logging/backoff plumbing, parameterized by this provider's budgets. */
  private final CloudBackendSupport support;

  /** The shared prepare/retry/decode control flow, parameterized by this provider's quirks. */
  private final CloudSpeechExecutor executor;

  public AiStudioTtsBackend(OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson) {
    this(
        httpClient,
        config,
        gson,
        PRODUCTION_ENDPOINT,
        AiStudioTranslator.PRODUCTION_ENDPOINT,
        RetryTuning.googleAiStudio());
  }

  /**
   * Test seam: lets a test point the speech and translation requests at a mock web server (and
   * shrink the timeout/backoff budgets) while exercising the real header, JSON body, SSE decode,
   * and error handling.
   */
  AiStudioTtsBackend(
      OkHttpClient httpClient,
      VoicedDialogueConfig config,
      Gson gson,
      String endpoint,
      String translatorEndpoint,
      RetryTuning tuning) {
    this.httpClient = CloudHttp.deriveClient(httpClient, tuning, KEEP_ALIVE, false);
    this.support =
        new CloudBackendSupport(
            config,
            VoicedDialogueConfig.TtsProvider.GOOGLE_AI_STUDIO,
            CloudSpeechExecutor.MAX_SPEECH_ATTEMPTS,
            tuning);
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
    this.streamingEndpoint = streamingEndpoint(endpoint);
    this.translator = new AiStudioTranslator(this.httpClient, config, gson, translatorEndpoint);
    this.executor =
        new CloudSpeechExecutor(config, support, model, "Google AI Studio", MODEL, new Ops());
  }

  /** Registers a one-time notice hook (e.g. a chat or log message) for cloud failures. */
  public void setNotice(Consumer<String> notice) {
    support.setNotice(notice);
  }

  /** Points this backend's billable-call counting at the plugin's session spend tracker. */
  public void setSpendTracker(SpendTracker spend) {
    support.setSpendTracker(spend);
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isAvailable() {
    return CloudHttp.isNonBlank(config.googleAiStudioApiKey());
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
  public boolean isThrottled() {
    return executor.isThrottled();
  }

  @Override
  public void clearRateLimit() {
    executor.clearRateLimit();
  }

  /**
   * Warms the connection with a cheap GET of the model's metadata, so the first line does not pay
   * the TCP/TLS handshake. Also the earliest signal of a bad key, logged at debug.
   */
  @Override
  public void warmUp() {
    if (!isAvailable()) {
      return;
    }
    Request request =
        new Request.Builder()
            .url(modelEndpoint(endpoint))
            .addHeader("x-goog-api-key", config.googleAiStudioApiKey().trim())
            .addHeader("User-Agent", CloudHttp.USER_AGENT)
            .get()
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      log.debug("[TTS cloud] AI Studio warm-up HTTP {}", response.code());
    } catch (IOException | RuntimeException e) {
      log.debug("[TTS cloud] AI Studio warm-up failed: {}", e.getMessage());
    }
  }

  @Override
  public String cacheVariant(SynthesisRequest request) {
    return executor.cacheVariant(request);
  }

  @Override
  public Pcm synthesize(SynthesisRequest request) {
    return executor.synthesize(request);
  }

  @Override
  public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
    return executor.synthesizeStreaming(request, sink);
  }

  /**
   * The one-time user notice for a non-2xx speech response. A 429 is the Gemini API's quota
   * rejection and is worded from the quota its body reports; anything else keeps the generic
   * check-your-key message with the code for context.
   */
  static String failureNotice(Gson gson, int httpCode, byte[] body) {
    if (httpCode == CloudHttp.HTTP_TOO_MANY_REQUESTS) {
      return AiStudioQuotaFailure.noticeFor(gson, body);
    }
    return "Google AI Studio TTS request failed (HTTP "
        + httpCode
        + "); check your API key. This line was not voiced.";
  }

  /** The AI-Studio-specific half of the shared speech call: payload, transport, and notices. */
  private final class Ops implements CloudSpeechExecutor.Ops {

    @Override
    public String apiKey() {
      return config.googleAiStudioApiKey();
    }

    @Override
    public String missingKeyNotice() {
      return NO_KEY_NOTICE;
    }

    @Override
    public String translate(String text, String language, String apiKey) {
      AiStudioTranslator.Translation translation = translator.translate(text, language, apiKey);
      if (translation == null) {
        return null;
      }
      support.recordTranslationSpend(
          text.length(), translation.usage.promptTokens, translation.usage.textTokens);
      return translation.text;
    }

    @Override
    public CloudSpeechExecutor.PreparedSpeech buildRequests(
        CloudSpeechExecutor.SpokenLine line, SynthesisRequest request) {
      String input = line.input;
      if (line.speedPercent != CloudBackendSupport.DEFAULT_SPEED_PERCENT) {
        // The Gemini API has no speed parameter, so a non-default pace becomes a prompt direction.
        // Prepended (not appended) so the profile block and emotion tag still lead the transcript.
        input = "SPEAKING PACE: " + line.speedPercent + "% of normal.\n\n" + input;
      }
      String languageCode = line.translating ? config.cloudLanguage().code() : null;
      JsonObject payload = buildPayload(input, model.voiceFor(request.voice()), languageCode);
      byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
      return new CloudSpeechExecutor.PreparedSpeech(
          buildHttpRequest(endpoint, line.apiKey, body),
          buildHttpRequest(streamingEndpoint, line.apiKey, body),
          line.speedRatio,
          input.length(),
          request.prefetch());
    }

    @Override
    public Call newCall(Request httpRequest, int inputLength) {
      return httpClient.newCall(httpRequest);
    }

    @Override
    public CloudSpeechExecutor.DecodedSpeech decodeBuffered(
        byte[] bytes, CloudSpeechExecutor.PreparedSpeech prepared) {
      JsonObject document = parseResponse(new String(bytes, StandardCharsets.UTF_8));
      byte[] audio = extractAudio(document);
      if (audio == null || audio.length == 0) {
        return CloudSpeechExecutor.DecodedSpeech.EMPTY;
      }
      AiStudioTokenUsage usage = AiStudioTokenUsage.forSpeech(document);
      return new CloudSpeechExecutor.DecodedSpeech(
          model.decodeResponse(audio),
          audio.length,
          () ->
              support.recordSpeechSpend(
                  prepared.inputLen, prepared.prefetch, usage.audioTokens, usage.promptTokens));
    }

    @Override
    public CloudSpeechExecutor.StreamDrain newStreamDrain() {
      return new SseStreamDrain();
    }

    @Override
    public String failureNotice(int httpCode, byte[] body) {
      return AiStudioTtsBackend.failureNotice(gson, httpCode, body);
    }

    @Override
    public long statedWaitMillis(byte[] body) {
      return AiStudioRetryInfo.retryDelayMillis(gson, body);
    }

    @Override
    public String emptyBodyNotice() {
      return "Google AI Studio TTS returned no audio; this line was not voiced.";
    }
  }

  /**
   * Reads {@code streamGenerateContent} server-sent events, base64-decoding each audio part. Each
   * event's document is parsed once and shared by the finish-reason, usage, and audio extraction.
   * The API reports {@code usageMetadata} on its events as a running total, so the drain holds the
   * largest reading seen; the token totals only complete as the stream drains, which is why the
   * spend is banked after the drain rather than at the first chunk.
   */
  private final class SseStreamDrain implements CloudSpeechExecutor.StreamDrain {

    private String finishReason;
    private AiStudioTokenUsage usage = AiStudioTokenUsage.NONE;

    @Override
    public void drain(ResponseBody body, CloudSpeechExecutor.ChunkSink chunk) throws IOException {
      BufferedSource source = body.source();
      String data;
      while ((data = readSseData(source)) != null) {
        if (data.isEmpty()) {
          continue;
        }
        JsonObject event = parseResponse(data);
        String eventFinishReason = extractFinishReason(event);
        if (eventFinishReason != null) {
          finishReason = eventFinishReason;
        }
        usage = usage.max(AiStudioTokenUsage.forSpeech(event));
        for (byte[] audio : extractAudioChunks(event)) {
          chunk.accept(audio, audio.length);
        }
      }
    }

    @Override
    public boolean finishedCleanly() {
      return "STOP".equals(finishReason);
    }

    @Override
    public void bankSpend(CloudSpeechExecutor.PreparedSpeech prepared) {
      support.recordSpeechSpend(
          prepared.inputLen, prepared.prefetch, usage.audioTokens, usage.promptTokens);
    }
  }

  /**
   * The Gemini {@code generateContent} body: the prompt as a single user part, and a generation
   * config requesting audio in the mapped prebuilt voice (with a BCP-47 language code when the line
   * was translated, so it is pronounced natively).
   */
  private static JsonObject buildPayload(String input, String voice, String languageCode) {
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
    return payload;
  }

  private static Request buildHttpRequest(String target, String apiKey, byte[] body) {
    return new Request.Builder()
        .url(target)
        .addHeader("x-goog-api-key", apiKey)
        .addHeader("User-Agent", CloudHttp.USER_AGENT)
        .post(RequestBody.create(CloudHttp.JSON_MEDIA_TYPE, body))
        .build();
  }

  /** One {@code GenerateContentResponse} JSON document parsed, or {@code null} when unreadable. */
  private JsonObject parseResponse(String raw) {
    try {
      return gson.fromJson(raw, JsonObject.class);
    } catch (RuntimeException e) {
      log.debug("[TTS cloud] AI Studio response parse error: {}", e.getMessage());
      return null;
    }
  }

  /** Concatenated audio bytes of a complete JSON response, or {@code null} when it has none. */
  private byte[] extractAudio(JsonObject response) {
    List<byte[]> chunks = extractAudioChunks(response);
    if (chunks.isEmpty()) {
      return null;
    }
    ByteArrayOutputStream audio = new ByteArrayOutputStream();
    for (byte[] chunk : chunks) {
      audio.write(chunk, 0, chunk.length);
    }
    return audio.toByteArray();
  }

  /**
   * The base64-decoded audio bytes of every {@code inlineData} part in one parsed {@code
   * GenerateContentResponse} document (a whole buffered body, or one SSE event). Empty on an
   * unreadable document or one with no audio, so callers treat it as an empty response.
   */
  private List<byte[]> extractAudioChunks(JsonObject response) {
    List<byte[]> chunks = new ArrayList<>();
    try {
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
      log.debug("[TTS cloud] AI Studio response parse error: {}", e.getMessage());
      return chunks;
    }
  }

  /** The {@code candidates[0].finishReason} of one response document, or {@code null} if absent. */
  private static String extractFinishReason(JsonObject response) {
    try {
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

  /**
   * Reads one server-sent event off the stream, joining multi-line {@code data:} fields per the SSE
   * framing rules, and returns its data payload; {@code null} at end of stream.
   */
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

  /** The streaming SSE endpoint derived from the buffered one, shared by the test seam. */
  private static String streamingEndpoint(String endpoint) {
    return endpoint.replace(":generateContent", ":streamGenerateContent") + "?alt=sse";
  }

  /** The bare model URL (no {@code :generateContent} action), used by the warm-up GET. */
  private static String modelEndpoint(String endpoint) {
    int action = endpoint.indexOf(":generateContent");
    return action < 0 ? endpoint : endpoint.substring(0, action);
  }
}
