package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.audio.RawPcmDecoder;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speech.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudBackendSupport;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudSpeechExecutor;
import com.grahambartley.runelite.voiced.dialogue.speech.RetryTuning;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiEmotionStyle;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiVoiceMap;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Cloud synthesis through OpenRouter's OpenAI-compatible speech endpoint.
 *
 * <p>It POSTs a JSON body ({@code model}, {@code input}, {@code voice}, {@code response_format}) to
 * {@code https://openrouter.ai/api/v1/audio/speech} over the injected {@link OkHttpClient} (never a
 * fresh client), authenticating with a user-supplied {@code Bearer} key. With {@code
 * response_format: "pcm"} the response is a raw, headerless stream of signed 16-bit little-endian
 * mono samples at 24 kHz, decoded by {@link RawPcmDecoder} into {@link Pcm} at its true rate so
 * playback is not pitch-shifted.
 *
 * <p>The model is fixed to Gemini 3.1 Flash TTS, the one OpenRouter speech model with both a voice
 * catalog rich enough to map every race/gender and full emotion support. {@link GeminiVoiceMap}
 * resolves each {@link VoiceSpec} to a gender-correct Gemini voice, spread per NPC. Emotion is
 * rendered through {@link GeminiEmotionStyle}, which prepends an inline style tag to the spoken
 * {@code input} (e.g. {@code "[angry] ..."}); the backend advertises {@link
 * GeminiEmotionStyle#SUPPORTED} and {@link BackendProvider} downgrades anything outside it to
 * {@link Emotion#NEUTRAL}, which carries no tag.
 *
 * <p>The backend reports {@link #isAvailable()} only when an API key is set. Every failure path
 * (missing key, non-2xx, network error, empty/undecodable body) returns {@code null} and surfaces a
 * one-time notice rather than throwing, so the line is left unvoiced without crashing or blocking
 * the game thread.
 */
@Slf4j
public final class OpenRouterTtsBackend implements SynthesisBackend {

  /** Stable backend id, folded into the synthesis cache key. */
  public static final String ID = "cloud-openrouter";

  private static final String API_BASE = "https://openrouter.ai/api/v1";
  private static final String SPEECH_PATH = "/audio/speech";
  private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

  /**
   * Cheapest authenticated endpoint OpenRouter exposes: a warm-up costs a key lookup, not audio.
   */
  private static final String KEY_PATH = "/key";

  private static final String PRODUCTION_ENDPOINT = API_BASE + SPEECH_PATH;

  /**
   * Floor of a line's call budget, covering connect, the model's own start-up cost, and a short
   * line's generation.
   */
  private static final Duration CALL_BUDGET_BASE = Duration.ofSeconds(15);

  /**
   * Extra budget per character of input. OpenRouter delivers nothing until generation finishes, so
   * a line's wait grows with its length: measured at roughly 37ms per character, doubled here so a
   * slow-but-valid generation still lands. A line stays bounded by the provider's ceiling in {@link
   * RetryTuning}, so an uncapped {@code cloudMaxChars} cannot hold a worker forever.
   */
  private static final long CALL_BUDGET_MILLIS_PER_CHAR = 75;

  /**
   * How long an idle connection is kept. Long enough to span the travel between quest steps, so a
   * conversation that follows a quiet stretch still starts warm; OpenRouter holds its side of an
   * idle connection well past this, so the client is the binding constraint.
   */
  private static final Duration KEEP_ALIVE = Duration.ofMinutes(15);

  /**
   * Connections opened by a warm-up. Two, because HTTP/1.1 cannot multiplex: a live line dispatched
   * while a prefetch is in flight needs a second pooled connection or it pays its own handshake.
   */
  static final int WARM_UP_CONNECTIONS = 2;

  /** Read granularity for the streaming path: bytes are decoded and played per network read. */
  private static final int STREAM_READ_BUFFER = 16_384;

  /**
   * User-facing notice shown when no API key is set. Shared with the plugin's startup check so the
   * two paths never drift.
   */
  public static final String NO_KEY_NOTICE =
      "Add your OpenRouter API key in the Voiced Dialogue settings to hear dialogue; without a key,"
          + " lines are not voiced.";

  /**
   * User-facing notice for HTTP 402, OpenRouter's insufficient-credits rejection: the key is valid
   * and the account balance is the problem, so the fix is a top-up rather than a key check.
   */
  static final String OUT_OF_CREDITS_NOTICE =
      "Your OpenRouter account is out of credits, so dialogue cannot be voiced. Top up at"
          + " openrouter.ai/settings/credits.";

  /**
   * Drains and closes a warm-up response so its connection returns to the pool rather than being
   * discarded, which is the entire point of the call. Failures are not the player's problem: the
   * next line simply pays its own handshake.
   */
  private static final Callback WARM_UP_CALLBACK =
      new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
          log.debug("[TTS cloud] OpenRouter warm-up failed: {}", e.getMessage());
        }

        @Override
        public void onResponse(Call call, Response response) {
          try (ResponseBody body = response.body()) {
            if (body != null) {
              body.bytes();
            }
            log.debug("[TTS cloud] OpenRouter warm-up HTTP {}", response.code());
          } catch (IOException e) {
            log.debug("[TTS cloud] OpenRouter warm-up body read failed: {}", e.getMessage());
          }
        }
      };

  private final OkHttpClient httpClient;
  private final VoicedDialogueConfig config;
  private final Gson gson;
  private final String endpoint;
  private final String warmUpEndpoint;
  private final GeminiTtsModel model = new GeminiTtsModel();
  private final OpenRouterTranslator translator;

  /**
   * Per-profile digest of the stable cacheable prefix, used only in debug mode to assert the prefix
   * a given profile renders is byte-identical across the process lifetime (so Gemini's prompt cache
   * can hit). A mismatch means a non-stable field leaked into the prefix and is logged once.
   */
  private final Map<String, Integer> prefixHashes = new ConcurrentHashMap<>();

  /** Ceiling every per-line call budget is clamped to; overridable in tests. */
  private final Duration callTimeout;

  /** The shared notice/logging/backoff plumbing, parameterized by this provider's budgets. */
  private final CloudBackendSupport support;

  /** The shared prepare/retry/decode control flow, parameterized by this provider's quirks. */
  private final CloudSpeechExecutor executor;

  public OpenRouterTtsBackend(OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson) {
    this(httpClient, config, gson, PRODUCTION_ENDPOINT);
  }

  /**
   * Test seam: lets a test point the request at a mock web server instead of the live OpenRouter
   * host while exercising the real header, JSON body, decode, and error handling.
   */
  OpenRouterTtsBackend(
      OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson, String endpoint) {
    this(httpClient, config, gson, endpoint, RetryTuning.openRouter());
  }

  /**
   * Test seam: also lets a test shrink the timeout and retry-backoff budget so the network-timeout
   * retry path runs in milliseconds instead of the multi-second production budget.
   */
  OpenRouterTtsBackend(
      OkHttpClient httpClient,
      VoicedDialogueConfig config,
      Gson gson,
      String endpoint,
      RetryTuning tuning) {
    // Pinned to HTTP/1.1 deliberately: the speech endpoint streams raw PCM, and HTTP/2 multiplexes
    // concurrent calls (the prefetch pool plus the live line) onto one connection, where a
    // concurrent streamed body can come back truncated as an empty 200. HTTP/1.1 gives each
    // concurrent call its own pooled connection, so they never contend; sequential lines still
    // reuse a warm connection.
    this.httpClient = CloudHttp.deriveClient(httpClient, tuning, KEEP_ALIVE, true);
    this.callTimeout = tuning.callTimeout;
    this.support =
        new CloudBackendSupport(
            config,
            VoicedDialogueConfig.TtsProvider.OPENROUTER,
            CloudSpeechExecutor.MAX_SPEECH_ATTEMPTS,
            tuning);
    this.config = config;
    this.gson = gson;
    this.endpoint = endpoint;
    this.warmUpEndpoint = siblingEndpoint(endpoint, KEY_PATH);
    // The translation hop shares the same keepalive client. Its chat-completions endpoint is the
    // sibling of the speech endpoint, so a test pointing speech at a mock server points translation
    // at the same server without extra wiring.
    this.translator =
        new OpenRouterTranslator(
            this.httpClient, config, gson, siblingEndpoint(endpoint, CHAT_COMPLETIONS_PATH));
    this.executor =
        new CloudSpeechExecutor(config, support, model, "OpenRouter", model.modelId(), new Ops());
  }

  /**
   * The endpoint sharing this one's host and API root, so a test pointing speech at a mock server
   * reaches the mock's siblings too, while production resolves against the live API base.
   */
  private static String siblingEndpoint(String endpoint, String path) {
    return endpoint.contains(SPEECH_PATH) ? endpoint.replace(SPEECH_PATH, path) : API_BASE + path;
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
    return CloudHttp.isNonBlank(config.openRouterApiKey());
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
   * Opens {@link #WARM_UP_CONNECTIONS} pooled connections against the key endpoint, so a spoken
   * line does not pay the TCP/TLS handshake itself. A no-op while the pool already holds a
   * connection, which makes it safe to call whenever a conversation starts: it only spends when the
   * pool is genuinely cold, either at session start or after the keep-alive evicted it. The calls
   * are issued concurrently because sequential ones would reuse the first connection and leave the
   * second cold, and asynchronously so no caller waits on the handshake.
   */
  @Override
  public void warmUp() {
    if (!isAvailable() || pooledConnectionCount() > 0) {
      return;
    }
    Request request =
        new Request.Builder()
            .url(warmUpEndpoint)
            .addHeader("Authorization", "Bearer " + config.openRouterApiKey().trim())
            .addHeader("User-Agent", CloudHttp.USER_AGENT)
            .get()
            .build();
    for (int i = 0; i < WARM_UP_CONNECTIONS; i++) {
      httpClient.newCall(request).enqueue(WARM_UP_CALLBACK);
    }
  }

  /** Connections this backend currently holds, so a test can await an asynchronous warm-up. */
  int pooledConnectionCount() {
    return httpClient.connectionPool().connectionCount();
  }

  /**
   * Connections free for the next line to reuse. A warm-up connection is counted by {@link
   * #pooledConnectionCount()} from the moment it is established, but only becomes reusable once its
   * response body has been drained and released, so a test that needs a genuinely warm pool waits
   * on this instead.
   */
  int idlePooledConnectionCount() {
    return httpClient.connectionPool().idleConnectionCount();
  }

  /**
   * How long this line is allowed to take, growing with its length because OpenRouter withholds the
   * audio until the whole clip exists. Clamped to the client's ceiling so a short line still fails
   * fast rather than inheriting the budget a very long one would need.
   */
  Duration callBudgetFor(int inputLength) {
    Duration scaled =
        CALL_BUDGET_BASE.plusMillis(Math.max(0, inputLength) * CALL_BUDGET_MILLIS_PER_CHAR);
    return scaled.compareTo(callTimeout) > 0 ? callTimeout : scaled;
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
   * The one-time user notice for a non-2xx speech response. A 402 is OpenRouter's
   * insufficient-credits rejection and gets the dedicated top-up notice; anything else keeps the
   * generic check-your-key message with the code for context.
   */
  static String failureNotice(int httpCode) {
    if (httpCode == HttpURLConnection.HTTP_PAYMENT_REQUIRED) {
      return OUT_OF_CREDITS_NOTICE;
    }
    return "OpenRouter TTS request failed (HTTP "
        + httpCode
        + "); check your API key. This line was not voiced.";
  }

  /**
   * Debug-only guard: records the digest of a profile's stable cacheable prefix on first sight and
   * warns once if a later call for the same profile renders a different prefix, which would
   * silently defeat Gemini's implicit prompt cache. A no-op outside debug mode.
   */
  private void assertStablePrefix(CharacterProfile profile) {
    if (!config.debugMode()) {
      return;
    }
    int hash = profile.renderPromptBlock().hashCode();
    Integer seen = prefixHashes.putIfAbsent(profile.cacheKey(), hash);
    if (seen != null && seen != hash) {
      log.warn(
          "[TTS cloud] cacheable prefix for profile '{}' changed across calls (prompt cache will"
              + " miss); cacheKey={}",
          profile.name(),
          profile.cacheKey());
    }
  }

  /** The OpenRouter-specific half of the shared speech call: payload, transport, and notices. */
  private final class Ops implements CloudSpeechExecutor.Ops {

    @Override
    public String apiKey() {
      return config.openRouterApiKey();
    }

    @Override
    public String missingKeyNotice() {
      return NO_KEY_NOTICE;
    }

    @Override
    public String translate(String text, String language, String apiKey) {
      String translated = translator.translate(text, language, apiKey);
      if (translated != null) {
        support.recordTranslationSpend(text.length());
      }
      return translated;
    }

    @Override
    public void profileApplied(CharacterProfile profile) {
      assertStablePrefix(profile);
    }

    @Override
    public CloudSpeechExecutor.PreparedSpeech buildRequests(
        CloudSpeechExecutor.SpokenLine line, SynthesisRequest request) {
      JsonObject payload = new JsonObject();
      payload.addProperty("model", model.modelId());
      payload.addProperty("input", line.input);
      payload.addProperty("voice", model.voiceFor(request.voice()));
      payload.addProperty("response_format", model.responseFormat());
      if (line.speedPercent != CloudBackendSupport.DEFAULT_SPEED_PERCENT) {
        // The model may ignore speed; sending it only when non-default avoids paying for a param
        // the model might not honour on the common default-pace line.
        payload.addProperty("speed", line.speedRatio);
      }
      // A translated line gets a BCP-47 language_code from the base language (not the quirk), so
      // the voice pronounces the text natively rather than mis-reading it with an English phoneme
      // set.
      if (line.translating) {
        payload.addProperty("language_code", config.cloudLanguage().code());
      }
      OpenRouterProvider.apply(payload);

      Request httpRequest =
          OpenRouterProvider.attributedRequest(endpoint, line.apiKey)
              .post(
                  RequestBody.create(
                      CloudHttp.JSON_MEDIA_TYPE,
                      gson.toJson(payload).getBytes(StandardCharsets.UTF_8)))
              .build();
      return new CloudSpeechExecutor.PreparedSpeech(
          httpRequest, line.speedRatio, line.input.length(), request.prefetch());
    }

    /** Issues the call under this line's own budget rather than the client-wide ceiling. */
    @Override
    public Call newCall(Request httpRequest, int inputLength) {
      Call call = httpClient.newCall(httpRequest);
      call.timeout().timeout(callBudgetFor(inputLength).toMillis(), TimeUnit.MILLISECONDS);
      return call;
    }

    @Override
    public String generationId(Response response) {
      return CloudHttp.headerOrEmpty(response, "X-Generation-Id");
    }

    @Override
    public CloudSpeechExecutor.DecodedSpeech decodeBuffered(
        byte[] bytes, CloudSpeechExecutor.PreparedSpeech prepared) {
      if (bytes.length == 0) {
        return CloudSpeechExecutor.DecodedSpeech.EMPTY;
      }
      return new CloudSpeechExecutor.DecodedSpeech(
          model.decodeResponse(bytes),
          bytes.length,
          () -> support.recordSpeechSpend(prepared.inputLen, prepared.prefetch));
    }

    @Override
    public CloudSpeechExecutor.StreamDrain newStreamDrain() {
      return OpenRouterTtsBackend::drainRawBody;
    }

    @Override
    public void recordSpendOnFirstChunk(CloudSpeechExecutor.PreparedSpeech prepared) {
      support.recordSpeechSpend(prepared.inputLen, prepared.prefetch);
    }

    @Override
    public String failureNotice(int httpCode, byte[] body) {
      return OpenRouterTtsBackend.failureNotice(httpCode);
    }

    @Override
    public String emptyBodyNotice() {
      return "OpenRouter TTS returned an empty response; this line was not voiced.";
    }
  }

  /** Reads the raw PCM body in network-read-sized chunks so playback starts on the first read. */
  private static void drainRawBody(ResponseBody body, CloudSpeechExecutor.ChunkSink chunk)
      throws IOException {
    InputStream in = body.byteStream();
    byte[] buffer = new byte[STREAM_READ_BUFFER];
    int read;
    while ((read = in.read(buffer)) != -1) {
      if (read == 0) {
        continue;
      }
      chunk.accept(buffer, read);
    }
  }
}
