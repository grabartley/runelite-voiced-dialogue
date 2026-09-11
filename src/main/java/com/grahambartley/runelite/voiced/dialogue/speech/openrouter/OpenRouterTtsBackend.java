package com.grahambartley.runelite.voiced.dialogue.speech.openrouter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudBackendSupport;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudHttp;
import com.grahambartley.runelite.voiced.dialogue.speech.CloudSpeechExecutor;
import com.grahambartley.runelite.voiced.dialogue.speech.RetryTuning;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisBackend;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel;
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

@Slf4j
public final class OpenRouterTtsBackend implements SynthesisBackend {

  public static final String ID = "cloud-openrouter";

  private static final String API_BASE = "https://openrouter.ai/api/v1";
  private static final String SPEECH_PATH = "/audio/speech";
  private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

  private static final String KEY_PATH = "/key";

  private static final String PRODUCTION_ENDPOINT = API_BASE + SPEECH_PATH;

  private static final Duration CALL_BUDGET_BASE = Duration.ofSeconds(15);

  private static final long CALL_BUDGET_MILLIS_PER_CHAR = 75;

  private static final Duration KEEP_ALIVE = Duration.ofMinutes(15);

  static final int WARM_UP_CONNECTIONS = 2;

  private static final int STREAM_READ_BUFFER = 16_384;

  public static final String NO_KEY_NOTICE =
      "Add your OpenRouter API key in the Voiced Dialogue settings to hear dialogue; without a key,"
          + " lines are not voiced.";

  static final String OUT_OF_CREDITS_NOTICE =
      "Your OpenRouter account is out of credits, so dialogue cannot be voiced. Top up at"
          + " openrouter.ai/settings/credits.";

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

  private final Map<String, Integer> prefixHashes = new ConcurrentHashMap<>();

  private final Duration callTimeout;

  private final CloudBackendSupport support;

  private final CloudSpeechExecutor executor;

  public OpenRouterTtsBackend(OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson) {
    this(httpClient, config, gson, PRODUCTION_ENDPOINT);
  }

  OpenRouterTtsBackend(
      OkHttpClient httpClient, VoicedDialogueConfig config, Gson gson, String endpoint) {
    this(httpClient, config, gson, endpoint, RetryTuning.openRouter());
  }

  OpenRouterTtsBackend(
      OkHttpClient httpClient,
      VoicedDialogueConfig config,
      Gson gson,
      String endpoint,
      RetryTuning tuning) {
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
    this.translator =
        new OpenRouterTranslator(
            this.httpClient, config, gson, siblingEndpoint(endpoint, CHAT_COMPLETIONS_PATH));
    this.executor =
        new CloudSpeechExecutor(config, support, model, "OpenRouter", model.modelId(), new Ops());
  }

  private static String siblingEndpoint(String endpoint, String path) {
    return endpoint.contains(SPEECH_PATH) ? endpoint.replace(SPEECH_PATH, path) : API_BASE + path;
  }

  public void setNotice(Consumer<String> notice) {
    support.setNotice(notice);
  }

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

  int pooledConnectionCount() {
    return httpClient.connectionPool().connectionCount();
  }

  int idlePooledConnectionCount() {
    return httpClient.connectionPool().idleConnectionCount();
  }

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

  static String failureNotice(int httpCode) {
    if (httpCode == HttpURLConnection.HTTP_PAYMENT_REQUIRED) {
      return OUT_OF_CREDITS_NOTICE;
    }
    return "OpenRouter TTS request failed (HTTP "
        + httpCode
        + "); check your API key. This line was not voiced.";
  }

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
        payload.addProperty("speed", line.speedRatio);
      }
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
