package com.grahambartley.synthesis;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.tts.Pcm;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * Cloud synthesis directly through the Gemini API, authenticated with a Google AI Studio key.
 *
 * <p>The direct-to-Google counterpart of {@link OpenRouterTtsBackend}: the same Gemini TTS model,
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
 * throwing, a 429 opens the shared {@link RateLimitBackoff} window so prefetch backs off, and a
 * truncated line is never cached.
 */
@Slf4j
public final class GeminiAiStudioTtsBackend implements SynthesisBackend {

  /** Stable backend id, folded into the synthesis cache key. */
  public static final String ID = "cloud-google-ai-studio";

  /** The Gemini API name of the same model {@link GeminiTtsModel} pins through OpenRouter. */
  static final String MODEL = "gemini-3.1-flash-tts-preview";

  static final String PRODUCTION_ENDPOINT =
      "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";

  /**
   * User-facing notice shown when no API key is set. Shared with the plugin's startup check so the
   * two paths never drift.
   */
  public static final String NO_KEY_NOTICE =
      "Add your Google AI Studio API key in the Voiced Dialogue settings to hear dialogue; without"
          + " a key, lines are not voiced.";

  /**
   * User-facing notice for HTTP 429, which on the Gemini API means the key's quota or rate limit is
   * exhausted rather than a transient blip, so the fix is the account rather than the key.
   */
  static final String QUOTA_NOTICE =
      "Your Google AI Studio quota was hit, so dialogue cannot be voiced right now. The free tier"
          + " allows only a handful of speech requests per day, so enable billing at"
          + " aistudio.google.com, or switch Voice Provider to OpenRouter.";

  private static final String USER_AGENT = "runelite-voiced-dialogue";

  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  /** Speaking pace as a percentage of normal: the default (no prompt direction) and clamp range. */
  private static final int DEFAULT_SPEED_PERCENT = 100;

  private static final int MIN_SPEED_PERCENT = 50;

  private static final int MAX_SPEED_PERCENT = 200;

  /** Idle connections kept warm so back-to-back lines reuse a pooled connection. */
  private static final int MAX_IDLE_CONNECTIONS = 8;

  private static final Duration KEEP_ALIVE = Duration.ofMinutes(5);

  /** One speech call plus a single retry, for a transient empty, truncated, or timed-out line. */
  private static final int MAX_SPEECH_ATTEMPTS = 2;

  /** Max bytes of a non-audio response body echoed into a diagnostic log line. */
  private static final int BODY_SNIPPET_MAX_BYTES = 300;

  static final int HTTP_TOO_MANY_REQUESTS = 429;

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;
  private final String streamingEndpoint;
  private final GeminiTtsModel model = new GeminiTtsModel();
  private final GeminiAiStudioTranslator translator;
  private final RateLimitBackoff backoff = new RateLimitBackoff();

  /** Backoff budget for the network-timeout retry; overridable in tests to run in milliseconds. */
  private final long networkRetryBaseMillis;

  private final long networkRetryJitterMillis;

  /** One-time user notice hook for cloud failures; defaults to a no-op. */
  private Consumer<String> notice = msg -> {};

  /** Guards the one-time notice so a sustained outage does not spam the chat box. */
  private boolean warned;

  public GeminiAiStudioTtsBackend(OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson) {
    this(
        httpClient,
        config,
        gson,
        PRODUCTION_ENDPOINT,
        GeminiAiStudioTranslator.PRODUCTION_ENDPOINT,
        OpenRouterTtsBackend.RetryTuning.defaults());
  }

  /**
   * Test seam: lets a test point the speech and translation requests at a mock web server (and
   * shrink the timeout/backoff budgets) while exercising the real header, JSON body, SSE decode,
   * and error handling.
   */
  GeminiAiStudioTtsBackend(
      OkHttpClient httpClient,
      VoicedDialogueConfig config,
      Gson gson,
      String endpoint,
      String translatorEndpoint,
      OpenRouterTtsBackend.RetryTuning tuning) {
    // Derive a long-lived keepalive client from the injected one (Hub rule: never new an
    // OkHttpClient): own warm connection pool so back-to-back lines skip the TCP/TLS handshake,
    // own connect/read/call timeouts without mutating the shared client's globals.
    this.httpClient =
        httpClient
            .newBuilder()
            .connectionPool(
                new ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE.toMinutes(), TimeUnit.MINUTES))
            .connectTimeout(tuning.connectTimeout)
            .readTimeout(tuning.readTimeout)
            .callTimeout(tuning.callTimeout)
            .retryOnConnectionFailure(true)
            .build();
    this.networkRetryBaseMillis = tuning.retryBackoffBaseMillis;
    this.networkRetryJitterMillis = tuning.retryJitterMillis;
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
    this.streamingEndpoint = streamingEndpoint(endpoint);
    this.translator =
        new GeminiAiStudioTranslator(this.httpClient, config, gson, translatorEndpoint);
  }

  /** Registers a one-time notice hook (e.g. a chat or log message) for cloud failures. */
  public void setNotice(Consumer<String> notice) {
    this.notice = notice == null ? msg -> {} : notice;
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
  public String missingKeyNotice() {
    return NO_KEY_NOTICE;
  }

  @Override
  public EnumSet<Emotion> supportedEmotions() {
    return model.supportedEmotions();
  }

  @Override
  public boolean isThrottled() {
    return backoff.isThrottled();
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
            .addHeader("User-Agent", USER_AGENT)
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
    String language = effectiveSpokenLanguage(request);
    String languageFragment =
        OpenRouterTtsBackend.needsTranslation(language) && !request.skipTranslation()
            ? language.toLowerCase()
            : null;
    return CloudCacheKeyBuilder.build(
        MODEL,
        model.voiceFor(request.voice()),
        speedPercent(),
        DEFAULT_SPEED_PERCENT,
        request.text(),
        config.cloudMaxChars(),
        request.profile(),
        languageFragment);
  }

  @Override
  public Pcm synthesize(SynthesisRequest request) {
    PreparedCall prepared = prepare(request);
    if (prepared == null) {
      return null;
    }
    return executeBuffered(prepared);
  }

  @Override
  public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
    PreparedCall prepared = prepare(request);
    if (prepared == null) {
      return null;
    }
    return executeStreaming(prepared, sink);
  }

  /**
   * Builds the speech call shared by the buffered and streaming paths: availability check, optional
   * translation hop, emotion styling, character-profile block, pace direction, and language code.
   * Returns {@code null} (after surfacing the one-time notice) when the line cannot be voiced at
   * all: no API key, or a failed translation.
   */
  private PreparedCall prepare(SynthesisRequest request) {
    if (!isAvailable()) {
      log.debug(NO_KEY_NOTICE);
      notice.accept(NO_KEY_NOTICE);
      return null;
    }
    String key = config.googleAiStudioApiKey().trim();
    String cappedText = OpenRouterTtsBackend.capLength(request.text(), config.cloudMaxChars());
    // Optional first hop, identical in behavior to the OpenRouter path: a non-English target
    // language (or a global quirk) routes the capped line through the translation model, a failed
    // translation fails the line, and a skip-translation request (public chat) bypasses the hop.
    String language = effectiveSpokenLanguage(request);
    boolean translating =
        OpenRouterTtsBackend.needsTranslation(language) && !request.skipTranslation();
    String spokenText = cappedText;
    if (translating) {
      String translated = translator.translate(cappedText, language.trim(), key);
      if (translated == null) {
        warnOnce(
            "Google AI Studio translation to "
                + language.trim()
                + " failed; this line was not voiced.");
        return null;
      }
      spokenText = translated;
    }
    String styledInput = model.styleInput(spokenText, request.emotion());
    CharacterProfile profile = request.profile();
    String input = profile == null ? styledInput : profile.renderPromptBlock() + styledInput;
    int speed = speedPercent();
    double speedRatio = speed / (double) DEFAULT_SPEED_PERCENT;
    if (speed != DEFAULT_SPEED_PERCENT) {
      // The Gemini API has no speed parameter, so a non-default pace becomes a prompt direction.
      // Prepended (not appended) so the profile block and emotion tag still lead the transcript.
      input = "SPEAKING PACE: " + speed + "% of normal.\n\n" + input;
    }

    if (config.debugMode()) {
      String tag = GeminiEmotionStyle.tagFor(request.emotion());
      log.info(
          "[TTS voice] cloud emotion {} -> {}",
          request.emotion(),
          tag == null ? "no tag (neutral input)" : "inline tag [" + tag + "]");
      if (profile == null) {
        log.info("[TTS cloud] no character profile (plain input)");
      } else {
        log.info(
            "[TTS cloud] character profile '{}' accent='{}' (cacheKey={})",
            profile.name(),
            profile.accent(),
            profile.cacheKey());
      }
      if (cappedText.length() != request.text().length()) {
        log.info(
            "[TTS cloud] line capped {} -> {} chars (cloudMaxChars={})",
            request.text().length(),
            cappedText.length(),
            config.cloudMaxChars());
      }
      if (speed != DEFAULT_SPEED_PERCENT) {
        log.info("[TTS cloud] speed {}", speedRatio);
      }
    }

    String languageCode = translating ? config.cloudLanguage().code() : null;
    JsonObject payload = buildPayload(input, model.voiceFor(request.voice()), languageCode);
    byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
    return new PreparedCall(key, body, speedRatio, input.length());
  }

  /**
   * The buffered path: one {@code generateContent} call, audio decoded from the base64 parts of the
   * JSON body. A transient empty or truncated line gets one retry, a network timeout gets one
   * backed-off retry, and every failure returns {@code null}, mirroring the OpenRouter path.
   */
  private Pcm executeBuffered(PreparedCall prepared) {
    Request httpRequest = buildHttpRequest(endpoint, prepared);
    for (int attempt = 1; attempt <= MAX_SPEECH_ATTEMPTS; attempt++) {
      long attemptStart = System.nanoTime();
      try (Response response = httpClient.newCall(httpRequest).execute()) {
        ResponseBody body = response.body();
        byte[] bytes = body == null ? new byte[0] : body.bytes();
        String contentType = headerOrEmpty(response, "Content-Type");
        long elapsedMs = elapsedMs(attemptStart);

        if (!response.isSuccessful()) {
          if (response.code() == HTTP_TOO_MANY_REQUESTS) {
            backoff.recordRateLimited();
          }
          warnOnce(failureNotice(response.code()));
          logFailure(
              "non-2xx",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              bytes);
          return null;
        }
        backoff.recordSuccess();
        byte[] audio = extractAudio(new String(bytes, StandardCharsets.UTF_8));
        if (audio == null || audio.length == 0) {
          logFailure(
              "empty-body",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              bytes);
          if (attempt < MAX_SPEECH_ATTEMPTS) {
            log.debug(CloudSynthTrace.retry("empty-body", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
            continue;
          }
          warnOnce("Google AI Studio TTS returned no audio; this line was not voiced.");
          return null;
        }
        Pcm pcm = model.decodeResponse(audio);
        if (pcm == null) {
          warnOnce(
              "Google AI Studio TTS returned audio that could not be decoded; this line was not"
                  + " voiced.");
          logFailure(
              "undecodable",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              bytes);
          return null;
        }
        if (PcmCompleteness.isTruncated(pcm, prepared.speedRatio)) {
          if (attempt < MAX_SPEECH_ATTEMPTS) {
            log.debug(CloudSynthTrace.retry("truncated", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
            continue;
          }
          warnOnce("Google AI Studio TTS returned a truncated line; this line was not voiced.");
          logFailure(
              "truncated",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              bytes);
          return null;
        }
        if (config.debugMode()) {
          log.info(
              CloudSynthTrace.success(
                  attempt, MAX_SPEECH_ATTEMPTS, elapsedMs, prepared.inputLen, audio.length, ""));
        }
        return pcm;
      } catch (ConnectException e) {
        warnOnce(NETWORK_NOTICE);
        logNetworkFailure("connect", attempt, elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      } catch (IOException e) {
        long elapsedMs = elapsedMs(attemptStart);
        if (attempt < MAX_SPEECH_ATTEMPTS) {
          log.debug(CloudSynthTrace.retry("network", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
          backoffBeforeNetworkRetry(attempt);
          continue;
        }
        warnOnce(NETWORK_NOTICE);
        logNetworkFailure("network", attempt, elapsedMs, prepared.inputLen, e);
        return null;
      } catch (RuntimeException e) {
        warnOnce("Google AI Studio TTS request failed unexpectedly; this line was not voiced.");
        logNetworkFailure("unexpected", attempt, elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      }
    }
    return null;
  }

  /**
   * The streaming path: reads {@code streamGenerateContent} server-sent events, base64-decodes each
   * audio part, hands the decoded samples to {@code sink} for immediate playback, and accumulates
   * the whole line for caching. An empty stream (nothing handed over yet) is retried like the
   * buffered path; once any chunk has reached the sink the line is committed, so a mid-stream
   * failure plays what arrived and is not retried. A line whose accumulated audio is incomplete (no
   * {@code STOP} finish, odd byte count, or a truncated tail) still played but returns {@code null}
   * so it is not cached, and re-fetches next time.
   */
  private Pcm executeStreaming(PreparedCall prepared, PcmSink sink) {
    Request httpRequest = buildHttpRequest(streamingEndpoint, prepared);
    int rate = model.sampleRate();
    for (int attempt = 1; attempt <= MAX_SPEECH_ATTEMPTS; attempt++) {
      long attemptStart = System.nanoTime();
      boolean fedSink = false;
      long firstChunkMs = -1;
      String finishReason = null;
      try (Response response = httpClient.newCall(httpRequest).execute()) {
        String contentType = headerOrEmpty(response, "Content-Type");
        if (!response.isSuccessful()) {
          if (response.code() == HTTP_TOO_MANY_REQUESTS) {
            backoff.recordRateLimited();
          }
          warnOnce(failureNotice(response.code()));
          logFailure(
              "non-2xx",
              attempt,
              elapsedMs(attemptStart),
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              errorBody(response));
          return null;
        }
        backoff.recordSuccess();
        StreamingPcmDecoder decoder = new StreamingPcmDecoder();
        List<float[]> chunks = new ArrayList<>();
        int sampleCount = 0;
        long totalBytes = 0;
        ResponseBody body = response.body();
        if (body != null) {
          BufferedSource source = body.source();
          String data;
          while ((data = readSseData(source)) != null) {
            if (data.isEmpty()) {
              continue;
            }
            String eventFinishReason = extractFinishReason(data);
            if (eventFinishReason != null) {
              finishReason = eventFinishReason;
            }
            for (byte[] audio : extractAudioChunks(data)) {
              totalBytes += audio.length;
              float[] chunk = decoder.decode(audio, audio.length);
              if (chunk.length > 0) {
                // Feed playback first so it starts on the earliest samples, then keep the chunk
                // for the cache. After a skip the sink drops the chunk cheaply, so the loop keeps
                // draining the stream to completion and the finished line is still cached, never
                // re-billed on a later hearing.
                sink.accept(chunk, rate);
                if (!fedSink) {
                  firstChunkMs = elapsedMs(attemptStart);
                }
                fedSink = true;
                chunks.add(chunk);
                sampleCount += chunk.length;
              }
            }
          }
        }
        long elapsedMs = elapsedMs(attemptStart);
        if (totalBytes == 0) {
          logFailure(
              "empty-body",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              new byte[0]);
          if (attempt < MAX_SPEECH_ATTEMPTS) {
            log.debug(CloudSynthTrace.retry("empty-body", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
            continue;
          }
          warnOnce("Google AI Studio TTS returned no audio; this line was not voiced.");
          return null;
        }
        if (config.debugMode()) {
          // firstChunkMs is the streamed line's real time-to-first-sound; elapsedMs is the full
          // stream. A first chunk that lands nearly at elapsedMs means the provider sent the audio
          // in one burst and streaming playback could not start any earlier.
          log.info(
              "{} firstChunkMs={}",
              CloudSynthTrace.success(
                  attempt, MAX_SPEECH_ATTEMPTS, elapsedMs, prepared.inputLen, (int) totalBytes, ""),
              firstChunkMs);
        }
        Pcm pcm = new Pcm(flatten(chunks, sampleCount), rate);
        // The audio already played through the sink; only return it for caching when it is a
        // whole, complete line: the stream finished with STOP, no half sample is pending, and the
        // tail releases into silence. There is no retry here since replaying would double it.
        if (!"STOP".equals(finishReason)
            || decoder.hasPendingByte()
            || PcmCompleteness.isTruncated(pcm, prepared.speedRatio)) {
          log.debug("[TTS cloud] streamed line played but not cached (incomplete tail)");
          return null;
        }
        return pcm;
      } catch (ConnectException e) {
        warnOnce(NETWORK_NOTICE);
        logNetworkFailure("connect", attempt, elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      } catch (IOException e) {
        long elapsedMs = elapsedMs(attemptStart);
        // Retry only while no audio has played; once a chunk reached the sink, replaying the line
        // would double it, so a mid-stream cut plays what arrived and fails without a retry.
        if (!fedSink && attempt < MAX_SPEECH_ATTEMPTS) {
          log.debug(CloudSynthTrace.retry("network", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
          backoffBeforeNetworkRetry(attempt);
          continue;
        }
        warnOnce(NETWORK_NOTICE);
        logNetworkFailure("network", attempt, elapsedMs, prepared.inputLen, e);
        return null;
      } catch (RuntimeException e) {
        warnOnce("Google AI Studio TTS request failed unexpectedly; this line was not voiced.");
        logNetworkFailure("unexpected", attempt, elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      }
    }
    return null;
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

  private Request buildHttpRequest(String target, PreparedCall prepared) {
    return new Request.Builder()
        .url(target)
        .addHeader("x-goog-api-key", prepared.key)
        .addHeader("User-Agent", USER_AGENT)
        .post(RequestBody.create(JSON_MEDIA_TYPE, prepared.body))
        .build();
  }

  /** Concatenated audio bytes of a complete JSON response, or {@code null} when it has none. */
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

  /**
   * The base64-decoded audio bytes of every {@code inlineData} part in one {@code
   * GenerateContentResponse} JSON document (a whole buffered body, or one SSE event's data). Empty
   * on a parse failure or a document with no audio, so callers treat it as an empty response.
   */
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
      log.debug("[TTS cloud] AI Studio response parse error: {}", e.getMessage());
      return chunks;
    }
  }

  /** The {@code candidates[0].finishReason} of one response document, or {@code null} if absent. */
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

  /**
   * The spoken language actually requested of the model for this line, identical to the OpenRouter
   * rule: the configured language with the speaker-class Speaking Style appended.
   */
  String effectiveSpokenLanguage(SynthesisRequest request) {
    VoicedDialogueConfig.SpeakingStyle style =
        request.player() ? config.cloudPlayerSpeakingStyle() : config.cloudNpcSpeakingStyle();
    return OpenRouterTtsBackend.combineLanguage(config.cloudLanguage().label(), style);
  }

  /**
   * The one-time user notice for a non-2xx speech response. A 429 is the Gemini API's
   * quota/rate-limit rejection and gets the dedicated notice; anything else keeps the generic
   * check-your-key message with the code for context.
   */
  static String failureNotice(int httpCode) {
    if (httpCode == HTTP_TOO_MANY_REQUESTS) {
      return QUOTA_NOTICE;
    }
    return "Google AI Studio TTS request failed (HTTP "
        + httpCode
        + "); check your API key. This line was not voiced.";
  }

  private static final String NETWORK_NOTICE =
      "Google AI Studio TTS request could not reach the network; this line was not voiced.";

  /** Reads a small non-audio error body for diagnostics, tolerating a read failure. */
  private static byte[] errorBody(Response response) {
    try {
      ResponseBody body = response.body();
      return body == null ? new byte[0] : body.bytes();
    } catch (IOException e) {
      return new byte[0];
    }
  }

  /** Concatenates the decoded stream chunks into one sample buffer for caching. */
  private static float[] flatten(List<float[]> chunks, int totalSamples) {
    float[] out = new float[totalSamples];
    int pos = 0;
    for (float[] chunk : chunks) {
      System.arraycopy(chunk, 0, out, pos, chunk.length);
      pos += chunk.length;
    }
    return out;
  }

  /** The prepared speech call: the key and JSON body plus the values both response loops need. */
  private static final class PreparedCall {
    final String key;
    final byte[] body;
    final double speedRatio;
    final int inputLen;

    PreparedCall(String key, byte[] body, double speedRatio, int inputLen) {
      this.key = key;
      this.body = body;
      this.speedRatio = speedRatio;
      this.inputLen = inputLen;
    }
  }

  /** The configured pace as a percentage of normal, clamped to the supported range. */
  private int speedPercent() {
    int percent = config.speakingPace();
    if (percent < MIN_SPEED_PERCENT) {
      return MIN_SPEED_PERCENT;
    }
    if (percent > MAX_SPEED_PERCENT) {
      return MAX_SPEED_PERCENT;
    }
    return percent;
  }

  /**
   * Spaces a retry after a transient network failure, same shape as the OpenRouter path: an
   * exponential base plus random jitter, waited out on a synthesis-pool worker via a delayed future
   * (no blocking sleep and no thread interrupt, a Hub constraint).
   */
  private void backoffBeforeNetworkRetry(int attempt) {
    long base = networkRetryBaseMillis << (attempt - 1);
    long jitter =
        networkRetryJitterMillis <= 0
            ? 0
            : ThreadLocalRandom.current().nextLong(networkRetryJitterMillis + 1);
    long delayMillis = base + jitter;
    if (delayMillis <= 0) {
      return;
    }
    CompletableFuture.runAsync(
            () -> {}, CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS))
        .join();
  }

  /**
   * Logs why a cloud line was rejected in the standardized {@link CloudSynthTrace} shape (at warn
   * so it surfaces without debug), plus a short UTF-8 snippet of the body (at info, only in debug
   * mode) since a Gemini error is typically a JSON/text body.
   */
  private void logFailure(
      String kind,
      int attempt,
      long elapsedMs,
      int inputLen,
      int code,
      String message,
      String contentType,
      byte[] bytes) {
    log.warn(
        CloudSynthTrace.failure(
            kind,
            attempt,
            MAX_SPEECH_ATTEMPTS,
            elapsedMs,
            inputLen,
            code,
            contentType,
            "",
            bytes.length,
            message));
    if (config.debugMode() && bytes.length > 0) {
      log.info("[TTS cloud] {} body snippet: {}", kind, bodySnippet(bytes));
    }
  }

  private void logNetworkFailure(
      String kind, int attempt, long elapsedMs, int inputLen, Exception e) {
    log.warn(
        CloudSynthTrace.failure(
            kind, attempt, MAX_SPEECH_ATTEMPTS, elapsedMs, inputLen, 0, "", "", 0, e.getMessage()));
  }

  private static String headerOrEmpty(Response response, String name) {
    String value = response.header(name);
    return value == null ? "" : value;
  }

  /** First chunk of a response body as printable UTF-8, for diagnosing a non-audio response. */
  private static String bodySnippet(byte[] bytes) {
    int n = Math.min(bytes.length, BODY_SNIPPET_MAX_BYTES);
    String text =
        new String(bytes, 0, n, StandardCharsets.UTF_8).replaceAll("\\p{Cntrl}+", " ").trim();
    return bytes.length > n ? text + "..." : text;
  }

  private void warnOnce(String message) {
    log.debug(message);
    if (!warned) {
      warned = true;
      notice.accept(message);
    }
  }

  /** Elapsed wall-clock since {@code startNanos}, in whole milliseconds, for a latency trace. */
  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private static boolean isNonBlank(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
