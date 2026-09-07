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

/**
 * The provider-neutral control flow of a cloud speech call, shared by every cloud TTS backend: the
 * availability check and optional translation hop that prepare the spoken input, the buffered and
 * streaming retry loops with their one-time notices and {@link CloudSynthTrace} logging, the
 * rate-limit back-off, the completeness gate that keeps a clipped line out of the cache, and the
 * cache-variant key. A backend supplies one {@link Ops} implementation carrying only its provider
 * quirks: payload shape, transport, body decoding, and notice wording.
 */
@Slf4j
public final class CloudSpeechExecutor {

  /**
   * One speech call plus a single retry, for a transient empty, truncated, or timed-out line (the
   * retry after a timeout is spaced by a backoff, the others are immediate).
   */
  public static final int MAX_SPEECH_ATTEMPTS = 2;

  /** The provider-specific half of a speech call. */
  public interface Ops {

    /** The provider's configured API key, untrimmed; blank means the backend is unavailable. */
    public String apiKey();

    /** The user-facing notice surfaced on every line attempted without an API key. */
    String missingKeyNotice();

    /**
     * Translates the capped line into the target language, recording the hop's spend on success.
     * Returns {@code null} on failure, which fails the whole line.
     */
    String translate(String text, String language, String apiKey);

    /** Called when a profile block leads the input, before the debug trace is emitted. */
    default void profileApplied(CharacterProfile profile) {}

    /** Builds the provider's request payload and HTTP request(s) for the prepared spoken line. */
    PreparedSpeech buildRequests(SpokenLine line, SynthesisRequest request);

    /** Issues one HTTP attempt (e.g. under a per-line call budget). */
    Call newCall(Request httpRequest, int inputLen);

    /** The response's generation id, or {@code ""} for a provider whose responses carry none. */
    default String generationId(Response response) {
      return "";
    }

    /** Decodes a whole buffered response body into audio. */
    DecodedSpeech decodeBuffered(byte[] bytes, PreparedSpeech prepared);

    /** A fresh per-attempt reader of the provider's streaming response body. */
    StreamDrain newStreamDrain();

    /**
     * Records a streamed line's spend as its first audio reaches the sink, for a provider billed
     * per call rather than per metered token.
     */
    default void recordSpendOnFirstChunk(PreparedSpeech prepared) {}

    /**
     * The one-time user notice for a non-2xx speech response. {@code body} is the rejection's own
     * bytes, which is where a provider states the cause it should be worded from.
     */
    String failureNotice(int httpCode, byte[] body);

    /**
     * How long a 429's body says to wait, in milliseconds, or 0 when it says nothing. Read from the
     * body because the shape is the provider's own; the {@code Retry-After} header is read for
     * every provider alike.
     */
    default long statedWaitMillis(byte[] body) {
      return 0;
    }

    /** The one-time user notice for a 2xx response that carried no audio at all. */
    String emptyBodyNotice();
  }

  /** Reads one provider's streaming body, handing raw audio chunks to the shared accumulator. */
  public interface StreamDrain {

    /** Reads the whole response body, passing each raw audio chunk to {@code chunk}. */
    void drain(ResponseBody body, ChunkSink chunk) throws IOException;

    /** Whether the provider's own end-of-stream signal marked the line complete. */
    default boolean finishedCleanly() {
      return true;
    }

    /**
     * Records the drained line's spend, for a provider whose token totals only complete as the
     * stream drains.
     */
    default void bankSpend(PreparedSpeech prepared) {}
  }

  /** One raw audio chunk read off a streaming body. */
  public interface ChunkSink {
    void accept(byte[] bytes, int len);
  }

  /** The shared preparation of a line: trimmed key, final spoken input, and pace. */
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

  /** The built speech request(s) plus the values both response loops need. */
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

    /** For a provider whose buffered and streaming paths POST the same request. */
    public PreparedSpeech(Request request, double speedRatio, int inputLen, boolean prefetch) {
      this(request, request, speedRatio, inputLen, prefetch);
    }
  }

  /** A buffered body decoded: how much audio it carried, and the PCM if it was decodable. */
  public static final class DecodedSpeech {

    /** A response that carried no audio at all (retried once, like an empty body). */
    public static final DecodedSpeech EMPTY = new DecodedSpeech(null, 0, () -> {});

    final Pcm pcm;
    final int audioBytes;

    /** Records this call's spend; run only once the line is confirmed complete. */
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

  /** Drops a rate-limit window earned under credentials or a provider that have since changed. */
  public void clearRateLimit() {
    backoff.reset();
  }

  public String cacheVariant(SynthesisRequest request) {
    return CloudCacheKeyBuilder.build(
        cacheModelId,
        model.voiceFor(request.voice()),
        support.speedPercent(),
        request.text(),
        config.cloudMaxChars(),
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

  /**
   * Builds the speech call shared by the buffered and streaming paths: availability check, optional
   * translation hop, emotion styling, character-profile block, and pace. Returns {@code null} when
   * the line cannot be voiced at all: the provider is inside a wait it stated, there is no API key,
   * or the translation failed. The first of those is silent, since the notice that opened the
   * window already said what happened; the other two surface their one-time notice.
   */
  private PreparedSpeech prepare(SynthesisRequest request) {
    // The provider named the moment it will serve again, so a call made before then is a rejection
    // already: it earns another 429, another log line, and another notice, and voices nothing.
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
    String cappedText = CloudTtsText.capLength(request.text(), config.cloudMaxChars());
    // A non-English target language (or a global quirk) routes the capped line through the
    // translation model before it is voiced, so the spoken transcript is the transformed text. A
    // failed translation fails the line rather than voicing the wrong language or caching a
    // mistranslation under the language key. A skip-translation request (public chat) is voiced
    // exactly as typed, so it bypasses the hop.
    String language = CloudTtsText.effectiveSpokenLanguage(config, request);
    boolean translating =
        CloudTtsText.needsTranslation(language)
            && !request.skipTranslation()
            && !cappedText.isEmpty();
    String spokenText = cappedText;
    if (translating) {
      String translated = ops.translate(cappedText, language.trim(), apiKey);
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
    // The profile block sets the tone (accent/style/pace) and the emotion tag colours the moment;
    // they compose, so the block leads and the emotion-tagged transcript follows the divider.
    CharacterProfile profile = request.profile();
    String input = profile == null ? styledInput : profile.renderPromptBlock() + styledInput;
    if (profile != null) {
      ops.profileApplied(profile);
    }
    int speed = support.speedPercent();
    double speedRatio = speed / (double) CloudBackendSupport.DEFAULT_SPEED_PERCENT;

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
      if (speed != CloudBackendSupport.DEFAULT_SPEED_PERCENT) {
        log.info("[TTS cloud] speed {}", speedRatio);
      }
    }

    return ops.buildRequests(
        new SpokenLine(apiKey, input, translating, speedRatio, speed), request);
  }

  /**
   * The buffered path: one speech call, the whole body read and decoded at once. A transient empty
   * or truncated line gets one immediate retry, a network timeout gets one backed-off retry, and
   * every failure returns {@code null} after surfacing the one-time notice.
   */
  private Pcm runBuffered(PreparedSpeech prepared) {
    for (int attempt = 1; attempt <= MAX_SPEECH_ATTEMPTS; attempt++) {
      long attemptStart = System.nanoTime();
      try (Response response = ops.newCall(prepared.buffered, prepared.inputLen).execute()) {
        ResponseBody body = response.body();
        // Read the bytes once; on any failure they are the diagnostic payload (a provider error is
        // usually returned as a JSON/text body, sometimes even with HTTP 200), so capturing them is
        // the only way to see why a line was rejected rather than guessing.
        byte[] bytes = body == null ? CloudHttp.EMPTY_BODY : body.bytes();
        String contentType = CloudHttp.headerOrEmpty(response, "Content-Type");
        String generationId = ops.generationId(response);
        long elapsedMs = CloudHttp.elapsedMs(attemptStart);

        if (!response.isSuccessful()) {
          if (response.code() == CloudHttp.HTTP_TOO_MANY_REQUESTS) {
            backoff.recordRateLimited(statedWait(response, bytes));
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
        // The response is transport-complete, but the model occasionally returns a line whose
        // audio stops mid-utterance. A complete line releases into trailing silence; one that does
        // not is rejected so a clipped clip is never cached or voiced. One retry recovers the
        // common transient case.
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
        // The host is unreachable (connection refused / no route), almost certainly an offline
        // client. Retrying only delays the failure, so the line fails fast.
        support.warnOnce(networkNotice());
        support.logNetworkFailure(
            "connect", attempt, CloudHttp.elapsedMs(attemptStart), prepared.inputLen, e);
        return null;
      } catch (IOException e) {
        // A read/call timeout or a transient blip: a slow generation deserves a backed-off retry
        // rather than being dropped on the first failure. The backoff waits on a synthesis-pool
        // worker, never the game thread, and a second worker keeps serving the next line while
        // this one waits.
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

  /**
   * The streaming path: the provider's drain reads the body incrementally, decoded chunks reach
   * {@code sink} for immediate playback, and the whole line accumulates for caching. An empty body
   * (nothing handed over yet) is retried like the buffered path; once any chunk has reached the
   * sink the line is committed, so a mid-stream failure plays what arrived and is not retried. A
   * line whose accumulated audio is incomplete still played but returns {@code null} so it is not
   * cached, and re-fetches next time.
   */
  private Pcm runStreaming(PreparedSpeech prepared, PcmSink sink) {
    int rate = model.sampleRate();
    for (int attempt = 1; attempt <= MAX_SPEECH_ATTEMPTS; attempt++) {
      long attemptStart = System.nanoTime();
      StreamDrain drain = ops.newStreamDrain();
      Accumulator acc = new Accumulator(prepared, sink, rate, attemptStart);
      try (Response response = ops.newCall(prepared.streaming, prepared.inputLen).execute()) {
        String contentType = CloudHttp.headerOrEmpty(response, "Content-Type");
        String generationId = ops.generationId(response);
        if (!response.isSuccessful()) {
          // Read once: the body is a one-shot stream, and the wait hint, the notice, and the
          // trace all need it.
          byte[] bytes = CloudHttp.errorBody(response);
          if (response.code() == CloudHttp.HTTP_TOO_MANY_REQUESTS) {
            backoff.recordRateLimited(statedWait(response, bytes));
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
          // firstChunkMs is the streamed line's real time-to-first-sound; elapsedMs is the full
          // body. A first chunk that lands nearly at elapsedMs means the provider sent the audio
          // in one burst and streaming playback could not start any earlier.
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
        // The audio already played through the sink; only return it for caching when it is a
        // whole, complete line: the provider signalled a clean finish, no half sample is pending,
        // and the tail releases into silence. There is no retry here since replaying would double
        // it.
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
        // Retry only while no audio has played; once a chunk reached the sink, replaying the line
        // would double it, so a mid-stream cut plays what arrived and fails without a retry.
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

  /**
   * The wait the rejection asked for: the {@code Retry-After} header, or the provider's own hint in
   * the body. The longer of the two, so a response carrying both is honoured by whichever is
   * further out rather than by whichever happened to be read first.
   */
  private long statedWait(Response response, byte[] body) {
    return Math.max(CloudHttp.retryAfterMillis(response), ops.statedWaitMillis(body));
  }

  private String networkNotice() {
    return providerName + " TTS request could not reach the network; this line was not voiced.";
  }

  /** Decodes, plays, and accumulates one streaming attempt's raw audio chunks. */
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
        // Feed playback first so it starts on the earliest samples, then keep the chunk for the
        // cache. After a skip the sink drops the chunk cheaply, so the loop keeps draining the
        // body to completion and the finished line is still cached, never re-billed on a later
        // hearing.
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
}
