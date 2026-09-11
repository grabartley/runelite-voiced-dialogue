package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmCompleteness;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.audio.StreamingPcmDecoder;
import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiEmotionStyle;
import com.grahambartley.runelite.voiced.dialogue.speech.model.GeminiTtsModel;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
public final class CloudSpeechExecutor {

  public static final int MAX_SPEECH_ATTEMPTS = 2;

  public interface Ops {

    public String apiKey();

    String missingKeyNotice();

    String translate(String text, String language, String apiKey);

    default void profileApplied(CharacterProfile profile) {}

    PreparedSpeech buildRequests(SpokenLine line, SynthesisRequest request);

    Call newCall(Request httpRequest, int inputLen);

    default String generationId(Response response) {
      return "";
    }

    DecodedSpeech decodeBuffered(byte[] bytes, PreparedSpeech prepared);

    StreamDrain newStreamDrain();

    default void recordSpendOnFirstChunk(PreparedSpeech prepared) {}

    String failureNotice(int httpCode, byte[] body);

    default long statedWaitMillis(byte[] body) {
      return 0;
    }

    String emptyBodyNotice();
  }

  public interface StreamDrain {

    void drain(ResponseBody body, ChunkSink chunk) throws IOException;

    default boolean finishedCleanly() {
      return true;
    }

    default void bankSpend(PreparedSpeech prepared) {}
  }

  public interface ChunkSink {
    void accept(byte[] bytes, int len);
  }

  public static final class SpokenLine {
    public final String apiKey;
    public final String input;
    public final boolean translating;
    public final double speedRatio;
    public final int speedPercent;

    public SpokenLine(
        String apiKey, String input, boolean translating, double speedRatio, int speedPercent) {
      this.apiKey = apiKey;
      this.input = input;
      this.translating = translating;
      this.speedRatio = speedRatio;
      this.speedPercent = speedPercent;
    }
  }

  public static final class PreparedSpeech {
    final Request buffered;
    final Request streaming;
    public final double speedRatio;
    public final int inputLen;
    public final boolean prefetch;

    public PreparedSpeech(
        Request buffered, Request streaming, double speedRatio, int inputLen, boolean prefetch) {
      this.buffered = buffered;
      this.streaming = streaming;
      this.speedRatio = speedRatio;
      this.inputLen = inputLen;
      this.prefetch = prefetch;
    }

    public PreparedSpeech(Request request, double speedRatio, int inputLen, boolean prefetch) {
      this(request, request, speedRatio, inputLen, prefetch);
    }
  }

  public static final class DecodedSpeech {

    public static final DecodedSpeech EMPTY = new DecodedSpeech(null, 0, () -> {});

    final Pcm pcm;
    final int audioBytes;

    final Runnable bankSpend;

    public DecodedSpeech(Pcm pcm, int audioBytes, Runnable bankSpend) {
      this.pcm = pcm;
      this.audioBytes = audioBytes;
      this.bankSpend = bankSpend;
    }
  }

  private final VoicedDialogueConfig config;
  private final CloudBackendSupport support;
  private final GeminiTtsModel model;
  private final String providerName;
  private final String cacheModelId;
  private final Ops ops;
  private final RateLimitBackoff backoff = new RateLimitBackoff();

  public CloudSpeechExecutor(
      VoicedDialogueConfig config,
      CloudBackendSupport support,
      GeminiTtsModel model,
      String providerName,
      String cacheModelId,
      Ops ops) {
    this.config = config;
    this.support = support;
    this.model = model;
    this.providerName = providerName;
    this.cacheModelId = cacheModelId;
    this.ops = ops;
  }

  public boolean isThrottled() {
    return backoff.isThrottled();
  }

  public void clearRateLimit() {
    backoff.reset();
  }

  public String cacheVariant(SynthesisRequest request) {
    return CloudCacheKeyBuilder.build(
        cacheModelId,
        model.voiceFor(request.voice()),
        support.speedPercent(),
        request.profile(),
        CloudTtsText.effectiveSpokenLanguage(config, request),
        request.skipTranslation());
  }

  public Pcm synthesize(SynthesisRequest request) {
    PreparedSpeech prepared = prepare(request);
    if (prepared == null) {
      return null;
    }
    return runBuffered(prepared);
  }

  public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
    PreparedSpeech prepared = prepare(request);
    if (prepared == null) {
      return null;
    }
    return runStreaming(prepared, sink);
  }

  private PreparedSpeech prepare(SynthesisRequest request) {
    if (backoff.isRefusing()) {
      log.debug("[TTS cloud] {} asked to be left alone; this line was not voiced", providerName);
      return null;
    }
    String rawKey = ops.apiKey();
    if (!CloudHttp.isNonBlank(rawKey)) {
      support.noticeMissingKey(ops.missingKeyNotice());
      return null;
    }
    String apiKey = rawKey.trim();
    String text = request.text();
    String language = CloudTtsText.effectiveSpokenLanguage(config, request);
    boolean translating =
        CloudTtsText.needsTranslation(language) && !request.skipTranslation() && !text.isEmpty();
    String spokenText = text;
    if (translating) {
      String translated = ops.translate(text, language.trim(), apiKey);
      if (translated == null) {
        support.warnOnce(
            providerName
                + " translation to "
                + language.trim()
                + " failed; this line was not voiced.");
        return null;
      }
      spokenText = translated;
    }
    String styledInput = model.styleInput(spokenText, request.emotion());
    CharacterProfile profile = request.profile();
    String input = profile.renderPromptBlock() + styledInput;
    ops.profileApplied(profile);
    int speed = support.speedPercent();
    double speedRatio = speed / (double) CloudBackendSupport.DEFAULT_SPEED_PERCENT;

    if (config.debugMode()) {
      String tag = GeminiEmotionStyle.tagFor(request.emotion());
      log.info(
          "[TTS voice] cloud emotion {} -> {}",
          request.emotion(),
          tag == null ? "no tag (neutral input)" : "inline tag [" + tag + "]");
      log.info(
          "[TTS cloud] character profile '{}' accent='{}' (cacheKey={})",
          profile.name(),
          profile.accent(),
          profile.cacheKey());
      if (speed != CloudBackendSupport.DEFAULT_SPEED_PERCENT) {
        log.info("[TTS cloud] speed {}", speedRatio);
      }
    }

    return ops.buildRequests(
        new SpokenLine(apiKey, input, translating, speedRatio, speed), request);
  }

  private Pcm runBuffered(PreparedSpeech prepared) {
    for (int attempt = 1; attempt <= MAX_SPEECH_ATTEMPTS; attempt++) {
      long attemptStart = System.nanoTime();
      long backoffGeneration = backoff.generation();
      try (Response response = ops.newCall(prepared.buffered, prepared.inputLen).execute()) {
        ResponseBody body = response.body();
        byte[] bytes = body == null ? CloudHttp.EMPTY_BODY : body.bytes();
        String contentType = CloudHttp.headerOrEmpty(response, "Content-Type");
        String generationId = ops.generationId(response);
        long elapsedMs = CloudHttp.elapsedMs(attemptStart);

        if (!response.isSuccessful()) {
          if (response.code() == CloudHttp.HTTP_TOO_MANY_REQUESTS) {
            backoff.recordRateLimited(statedWait(response, bytes), backoffGeneration);
          }
          support.warnOnce(ops.failureNotice(response.code(), bytes));
          support.logFailure(
              "non-2xx",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              generationId,
              bytes);
          return null;
        }
        backoff.recordSuccess();
        DecodedSpeech decoded = ops.decodeBuffered(bytes, prepared);
        if (decoded.audioBytes == 0) {
          support.logFailure(
              "empty-body",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              generationId,
              bytes);
          if (attempt < MAX_SPEECH_ATTEMPTS) {
            log.debug(CloudSynthTrace.retry("empty-body", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
            continue;
          }
          support.warnOnce(ops.emptyBodyNotice());
          return null;
        }
        if (decoded.pcm == null) {
          support.warnOnce(
              providerName
                  + " TTS returned audio that could not be decoded; this line was not voiced.");
          support.logFailure(
              "undecodable",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              generationId,
              bytes);
          return null;
        }
        if (PcmCompleteness.isTruncated(decoded.pcm, prepared.speedRatio)) {
          if (attempt < MAX_SPEECH_ATTEMPTS) {
            log.debug(CloudSynthTrace.retry("truncated", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
            continue;
          }
          support.warnOnce(
              providerName + " TTS returned a truncated line; this line was not voiced.");
          support.logFailure(
              "truncated",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              generationId,
              bytes);
          return null;
        }
        if (config.debugMode()) {
          log.info(
              CloudSynthTrace.success(
                  attempt,
                  MAX_SPEECH_ATTEMPTS,
                  elapsedMs,
                  prepared.inputLen,
                  decoded.audioBytes,
                  generationId));
        }
        decoded.bankSpend.run();
        return decoded.pcm;
      } catch (ConnectException e) {
        support.warnOnce(networkNotice());
        support.logNetworkFailure(
            "connect", attempt, CloudHttp.elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      } catch (IOException e) {
        long elapsedMs = CloudHttp.elapsedMs(attemptStart);
        if (attempt < MAX_SPEECH_ATTEMPTS) {
          log.debug(CloudSynthTrace.retry("network", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
          support.backoffBeforeNetworkRetry(attempt);
          continue;
        }
        support.warnOnce(networkNotice());
        support.logNetworkFailure("network", attempt, elapsedMs, prepared.inputLen, e);
        return null;
      } catch (RuntimeException e) {
        support.warnOnce(
            providerName + " TTS request failed unexpectedly; this line was not voiced.");
        support.logNetworkFailure(
            "unexpected", attempt, CloudHttp.elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      }
    }
    return null;
  }

  private Pcm runStreaming(PreparedSpeech prepared, PcmSink sink) {
    int rate = model.sampleRate();
    for (int attempt = 1; attempt <= MAX_SPEECH_ATTEMPTS; attempt++) {
      long attemptStart = System.nanoTime();
      StreamDrain drain = ops.newStreamDrain();
      Accumulator acc = new Accumulator(prepared, sink, rate, attemptStart);
      long backoffGeneration = backoff.generation();
      try (Response response = ops.newCall(prepared.streaming, prepared.inputLen).execute()) {
        String contentType = CloudHttp.headerOrEmpty(response, "Content-Type");
        String generationId = ops.generationId(response);
        if (!response.isSuccessful()) {
          byte[] bytes = CloudHttp.errorBody(response);
          if (response.code() == CloudHttp.HTTP_TOO_MANY_REQUESTS) {
            backoff.recordRateLimited(statedWait(response, bytes), backoffGeneration);
          }
          support.warnOnce(ops.failureNotice(response.code(), bytes));
          support.logFailure(
              "non-2xx",
              attempt,
              CloudHttp.elapsedMs(attemptStart),
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              generationId,
              bytes);
          return null;
        }
        backoff.recordSuccess();
        ResponseBody body = response.body();
        if (body != null) {
          drain.drain(body, acc);
        }
        long elapsedMs = CloudHttp.elapsedMs(attemptStart);
        if (acc.totalBytes == 0) {
          support.logFailure(
              "empty-body",
              attempt,
              elapsedMs,
              prepared.inputLen,
              response.code(),
              response.message(),
              contentType,
              generationId,
              CloudHttp.EMPTY_BODY);
          if (attempt < MAX_SPEECH_ATTEMPTS) {
            log.debug(CloudSynthTrace.retry("empty-body", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
            continue;
          }
          support.warnOnce(ops.emptyBodyNotice());
          return null;
        }
        drain.bankSpend(prepared);
        if (config.debugMode()) {
          log.info(
              "{} firstChunkMs={}",
              CloudSynthTrace.success(
                  attempt,
                  MAX_SPEECH_ATTEMPTS,
                  elapsedMs,
                  prepared.inputLen,
                  (int) acc.totalBytes,
                  generationId),
              acc.firstChunkMs);
        }
        Pcm pcm = new Pcm(flatten(acc.chunks, acc.sampleCount), rate);
        if (!drain.finishedCleanly()
            || acc.decoder.hasPendingByte()
            || PcmCompleteness.isTruncated(pcm, prepared.speedRatio)) {
          log.debug("[TTS cloud] streamed line played but not cached (incomplete tail)");
          return null;
        }
        return pcm;
      } catch (ConnectException e) {
        support.warnOnce(networkNotice());
        support.logNetworkFailure(
            "connect", attempt, CloudHttp.elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      } catch (IOException e) {
        long elapsedMs = CloudHttp.elapsedMs(attemptStart);
        if (!acc.fedSink && attempt < MAX_SPEECH_ATTEMPTS) {
          log.debug(CloudSynthTrace.retry("network", attempt, MAX_SPEECH_ATTEMPTS, elapsedMs));
          support.backoffBeforeNetworkRetry(attempt);
          continue;
        }
        support.warnOnce(networkNotice());
        support.logNetworkFailure("network", attempt, elapsedMs, prepared.inputLen, e);
        return null;
      } catch (RuntimeException e) {
        support.warnOnce(
            providerName + " TTS request failed unexpectedly; this line was not voiced.");
        support.logNetworkFailure(
            "unexpected", attempt, CloudHttp.elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      }
    }
    return null;
  }

  private long statedWait(Response response, byte[] body) {
    return Math.max(CloudHttp.retryAfterMillis(response), ops.statedWaitMillis(body));
  }

  private String networkNotice() {
    return providerName + " TTS request could not reach the network; this line was not voiced.";
  }

  private final class Accumulator implements ChunkSink {
    final StreamingPcmDecoder decoder = new StreamingPcmDecoder();
    final List<float[]> chunks = new ArrayList<>();
    int sampleCount;
    long totalBytes;
    boolean fedSink;
    long firstChunkMs = -1;

    private final PreparedSpeech prepared;
    private final PcmSink sink;
    private final int rate;
    private final long attemptStart;

    Accumulator(PreparedSpeech prepared, PcmSink sink, int rate, long attemptStart) {
      this.prepared = prepared;
      this.sink = sink;
      this.rate = rate;
      this.attemptStart = attemptStart;
    }

    @Override
    public void accept(byte[] bytes, int len) {
      totalBytes += len;
      float[] chunk = decoder.decode(bytes, len);
      if (chunk.length > 0) {
        sink.accept(chunk, rate);
        if (!fedSink) {
          firstChunkMs = CloudHttp.elapsedMs(attemptStart);
          ops.recordSpendOnFirstChunk(prepared);
        }
        fedSink = true;
        chunks.add(chunk);
        sampleCount += chunk.length;
      }
    }
  }

  private static float[] flatten(List<float[]> chunks, int totalSamples) {
    float[] out = new float[totalSamples];
    int pos = 0;
    for (float[] chunk : chunks) {
      System.arraycopy(chunk, 0, out, pos, chunk.length);
      pos += chunk.length;
    }
    return out;
  }
}
