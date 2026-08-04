package com.grahambartley.tts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.grahambartley.synthesis.BackendProvider;
import com.grahambartley.synthesis.Emotion;
import com.grahambartley.synthesis.PcmSink;
import com.grahambartley.synthesis.SynthesisBackend;
import com.grahambartley.synthesis.SynthesisRequest;
import com.grahambartley.synthesis.VoiceSpec;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DialogueAudioServiceTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  /** Records synth requests and hands back canned PCM so cache behavior is observable. */
  private static final class FakeBackend implements SynthesisBackend {
    final List<String> requests = new ArrayList<>();
    private final String id;
    private final EnumSet<Emotion> supported;
    volatile boolean throttled;

    FakeBackend(EnumSet<Emotion> supported) {
      this("cloud-openrouter", supported);
    }

    FakeBackend(String id, EnumSet<Emotion> supported) {
      this.id = id;
      this.supported = supported;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public boolean isThrottled() {
      return throttled;
    }

    @Override
    public EnumSet<Emotion> supportedEmotions() {
      return supported;
    }

    @Override
    public Pcm synthesize(SynthesisRequest request) {
      requests.add(request.voice().key() + "|" + request.emotion() + "|" + request.text());
      return new Pcm(new float[] {0.1f, -0.1f}, 24_000);
    }
  }

  /** Records playback and interruption so the pipeline's decisions are observable. */
  private static final class FakeOutput implements AudioOutput {
    int streamCalls;
    int stopCalls;
    int lastVolume = -1;
    float[] lastSamples;

    int beginStreamCalls;
    int endStreamCalls;
    int lastStreamVolume = -1;
    final List<float[]> streamedChunks = new ArrayList<>();

    @Override
    public void stream(float[] samples, int sampleRate, int volumePercent) {
      streamCalls++;
      lastVolume = volumePercent;
      lastSamples = samples;
    }

    @Override
    public AudioStream beginStream(int volumePercent) {
      beginStreamCalls++;
      lastStreamVolume = volumePercent;
      return new AudioStream() {
        @Override
        public void write(float[] samples, int sampleRate) {
          streamedChunks.add(samples);
        }

        @Override
        public void end() {
          endStreamCalls++;
        }
      };
    }

    @Override
    public void stop() {
      stopCalls++;
    }

    @Override
    public void close() {}
  }

  /** Executor that defers tasks until explicitly drained, to simulate a real queue. */
  private static final class DeferredExecutor implements Executor {
    final List<Runnable> tasks = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    void runAll() {
      // Copy because running a task may enqueue more.
      List<Runnable> snapshot = new ArrayList<>(tasks);
      tasks.clear();
      for (Runnable r : snapshot) {
        r.run();
      }
    }
  }

  private static BackendProvider provider(SynthesisBackend backend) {
    return new BackendProvider(backend);
  }

  private static DialogueAudioService service(
      BackendProvider provider, AudioOutput output, Executor executor, int cacheSize, int volume) {
    // Buffered playback (streaming off, via the six-arg seam); the streaming path has its own tests
    // below. No disk layer.
    return new DialogueAudioService(provider, output, null, executor, cacheSize, () -> volume);
  }

  private static DialogueAudioService streamingService(
      BackendProvider provider, AudioOutput output, Executor executor, int cacheSize, int volume) {
    return new DialogueAudioService(
        provider, output, null, executor, cacheSize, () -> volume, () -> true);
  }

  private static SynthesisRequest req(String text, NPCRace race, NPCGender gender) {
    return new SynthesisRequest(text, VoiceSpec.npc(race, gender), Emotion.NEUTRAL);
  }

  private static SynthesisRequest req(String text, NPCRace race, NPCGender gender, Emotion e) {
    return new SynthesisRequest(text, VoiceSpec.npc(race, gender), e);
  }

  @Test
  public void repeatedLineIsServedFromCacheWithoutResynth() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Hello adventurer", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();
    svc.speak(req("Hello adventurer", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("second identical line should hit the cache", 1, backend.requests.size());
    assertEquals("both lines should still play", 2, output.streamCalls);
  }

  @Test
  public void aLiveMissStreamsWhenStreamPlaybackIsOn() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = streamingService(provider(backend), output, executor, 8, 100);

    svc.speak(req("Hello", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("a live miss opens a stream", 1, output.beginStreamCalls);
    assertEquals("and never uses the buffered path", 0, output.streamCalls);
    assertEquals("the stream is ended", 1, output.endStreamCalls);
    assertEquals("the line is synthesized once", 1, backend.requests.size());
    assertTrue("audio was handed to the stream", output.streamedChunks.size() >= 1);
  }

  @Test
  public void aLiveMissBuffersWhenStreamPlaybackIsOff() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Hello", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("the toggle off keeps the buffered path", 1, output.streamCalls);
    assertEquals("no stream is opened", 0, output.beginStreamCalls);
  }

  @Test
  public void aStreamedLineIsCachedSoTheRepeatPlaysFromCacheBuffered() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = streamingService(provider(backend), output, executor, 8, 100);

    svc.speak(req("Encore", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();
    svc.speak(req("Encore", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("the streamed line was cached, so only one synth", 1, backend.requests.size());
    assertEquals("the first line streamed", 1, output.beginStreamCalls);
    assertEquals("the repeat played from cache, buffered", 1, output.streamCalls);
  }

  @Test
  public void aCaveEchoLineBuffersEvenWhenStreamingIsOn() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = streamingService(provider(backend), output, executor, 8, 100);

    svc.speak(req("Boo", NPCRace.HUMAN, NPCGender.MALE), /* applyEcho= */ true);
    executor.runAll();

    assertEquals(
        "an echo line never streams (echo needs the whole clip)", 0, output.beginStreamCalls);
    assertEquals("it uses the buffered path", 1, output.streamCalls);
  }

  @Test
  public void prefetchNeverStreamsEvenWhenStreamingIsOn() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = streamingService(provider(backend), output, executor, 8, 100);

    svc.prefetch(req("Later", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("prefetch opens no stream", 0, output.beginStreamCalls);
    assertEquals("and never plays", 0, output.streamCalls);
    assertEquals("but it did synthesize and cache", 1, backend.requests.size());
  }

  @Test
  public void aStreamSkippedMidLineStillFinishesAndCaches() {
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    List<String> synthed = new ArrayList<>();
    Runnable[] skipHook = {() -> {}};
    // A backend that keeps draining the body to the complete line even though a skip lands mid-way
    // (the real backend does the same). The service must still cache that complete line.
    // The player is what drops the post-skip chunk from the speakers; that is covered by
    // StreamingAudioPlayerTest, so here we assert the service-level caching and stream release.
    SynthesisBackend streaming =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            synthed.add(request.text());
            return new Pcm(new float[] {0.1f, 0.2f}, 24_000);
          }

          @Override
          public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
            synthed.add(request.text());
            sink.accept(new float[] {0.1f}, 24_000);
            skipHook[0].run(); // the player skips mid-line here
            sink.accept(new float[] {0.2f}, 24_000);
            return new Pcm(new float[] {0.1f, 0.2f}, 24_000);
          }
        };
    DialogueAudioService svc = streamingService(provider(streaming), output, executor, 8, 100);
    skipHook[0] = svc::interrupt; // interrupting advances the epoch, like a Continue-click

    svc.speak(req("Skipme", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("the line was synthesized once", 1, synthed.size());
    assertEquals(
        "the stream is released even though it was skipped mid-line", 1, output.endStreamCalls);

    // The whole line was still cached despite the skip: a repeat is a cache hit.
    svc.speak(req("Skipme", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("the skipped line was cached, so the repeat does not re-synth", 1, synthed.size());
    assertEquals("the repeat plays from cache, buffered", 1, output.streamCalls);
  }

  @Test
  public void aStreamedLineThatIsIncompleteIsPlayedButNotCached() {
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    List<String> synthed = new ArrayList<>();
    // A backend that plays a chunk but returns null (a truncated/failed stream: heard once, not
    // cacheable). The service must release the stream and cache nothing (INV2).
    SynthesisBackend streaming =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            synthed.add(request.text());
            return null;
          }

          @Override
          public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
            synthed.add(request.text());
            sink.accept(new float[] {0.1f, 0.2f}, 24_000);
            return null; // played, but too incomplete to cache
          }
        };
    DialogueAudioService svc = streamingService(provider(streaming), output, executor, 8, 100);

    svc.speak(req("Clipped", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("it still played through the stream", 1, output.streamedChunks.size());
    assertEquals("the stream is released even on a null return", 1, output.endStreamCalls);

    // Nothing was cached, so a repeat drives the backend again (never persisted clipped).
    svc.speak(req("Clipped", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals(
        "an incomplete streamed line is not cached, so the repeat re-synths", 2, synthed.size());
  }

  @Test
  public void aMultiChunkStreamForwardsEveryChunkInOrderThenCaches() {
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    int[] synths = {0};
    SynthesisBackend streaming =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            synths[0]++;
            return new Pcm(new float[] {0.1f, 0.2f, 0.3f}, 24_000);
          }

          @Override
          public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
            synths[0]++;
            sink.accept(new float[] {0.1f}, 24_000);
            sink.accept(new float[] {0.2f}, 24_000);
            sink.accept(new float[] {0.3f}, 24_000);
            return new Pcm(new float[] {0.1f, 0.2f, 0.3f}, 24_000);
          }
        };
    DialogueAudioService svc = streamingService(provider(streaming), output, executor, 8, 100);

    svc.speak(req("ThreeParts", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("every chunk was forwarded to the player", 3, output.streamedChunks.size());
    assertArrayEquals("chunk 1", new float[] {0.1f}, output.streamedChunks.get(0), 0f);
    assertArrayEquals("chunk 2", new float[] {0.2f}, output.streamedChunks.get(1), 0f);
    assertArrayEquals("chunk 3", new float[] {0.3f}, output.streamedChunks.get(2), 0f);
    assertEquals("streamed, not buffered", 0, output.streamCalls);
    assertEquals("stream ended once", 1, output.endStreamCalls);

    // The complete line is cached: the repeat is a buffered cache hit, no second synth.
    svc.speak(req("ThreeParts", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();
    assertEquals("the repeat is served from cache", 1, synths[0]);
    assertEquals("and plays buffered", 1, output.streamCalls);
  }

  @Test
  public void sameTextDifferentVoiceIsSynthesizedSeparately() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Greetings", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();
    svc.speak(req("Greetings", NPCRace.ELF, NPCGender.FEMALE));
    executor.runAll();

    assertEquals("different voices are distinct cache keys", 2, backend.requests.size());
  }

  @Test
  public void sameTextAndVoiceDifferentEmotionIsSynthesizedSeparately() {
    // A backend that supports two emotions so neither downgrades to NEUTRAL.
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL, Emotion.ANGRY));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Halt", NPCRace.HUMAN, NPCGender.MALE, Emotion.NEUTRAL));
    executor.runAll();
    svc.speak(req("Halt", NPCRace.HUMAN, NPCGender.MALE, Emotion.ANGRY));
    executor.runAll();

    assertEquals("emotion is part of the cache key, no collision", 2, backend.requests.size());
    assertEquals("npc:HUMAN:MALE|NEUTRAL|Halt", backend.requests.get(0));
    assertEquals("npc:HUMAN:MALE|ANGRY|Halt", backend.requests.get(1));
  }

  @Test
  public void staleLineIsDroppedWhenSupersededBeforeItRuns() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    // Two lines enqueued before either runs: advancing dialogue supersedes the first.
    svc.speak(req("First line", NPCRace.HUMAN, NPCGender.MALE));
    svc.speak(req("Second line", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("stale first line should never synthesize", 1, backend.requests.size());
    assertTrue(backend.requests.get(0).endsWith("|Second line"));
    assertEquals("only the live line should play", 1, output.streamCalls);
  }

  @Test
  public void everyNewLineStopsCurrentPlayback() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("a", NPCRace.HUMAN, NPCGender.MALE));
    svc.speak(req("b", NPCRace.HUMAN, NPCGender.MALE));

    assertEquals("each speak interrupts whatever is playing", 2, output.stopCalls);
  }

  @Test
  public void interruptStopsPlaybackAndDropsQueuedLine() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Queued line", NPCRace.HUMAN, NPCGender.MALE));
    svc.interrupt();
    executor.runAll();

    assertTrue("interrupt should stop current audio", output.stopCalls >= 1);
    assertEquals("queued stale line should not synthesize", 0, backend.requests.size());
    assertEquals("queued stale line should not play", 0, output.streamCalls);
  }

  @Test
  public void failedSynthIsNotCachedOrPlayed() {
    SynthesisBackend failing =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            return null;
          }
        };
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(failing), output, executor, 8, 100);

    svc.speak(req("anything", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("null synth result should not play", 0, output.streamCalls);
  }

  @Test
  public void emptyTextIsIgnored() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("", NPCRace.HUMAN, NPCGender.MALE));
    svc.speak(null);
    executor.runAll();

    assertEquals(0, backend.requests.size());
    assertEquals(0, output.streamCalls);
    assertEquals("empty lines do not even interrupt", 0, output.stopCalls);
  }

  @Test
  public void currentVolumeIsForwardedToPlayback() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 42);

    svc.speak(req("line", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals(42, output.lastVolume);
  }

  @Test
  public void unsupportedEmotionDowngradesToNeutralAndSharesCacheEntry() {
    // A NEUTRAL-only backend. An ANGRY line downgrades and collides with the NEUTRAL
    // one, proving the downgrade happens before the cache key is built.
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Be gone", NPCRace.HUMAN, NPCGender.MALE, Emotion.NEUTRAL));
    executor.runAll();
    svc.speak(req("Be gone", NPCRace.HUMAN, NPCGender.MALE, Emotion.ANGRY));
    executor.runAll();

    assertEquals("downgraded emotion reuses the neutral cache entry", 1, backend.requests.size());
    assertTrue(backend.requests.get(0).contains("|NEUTRAL|"));
    assertEquals("both lines still play", 2, output.streamCalls);
  }

  @Test
  public void lineSynthesizedInOneSessionIsServedFromDiskInTheNext() {
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    SynthesisRequest line = req("Welcome to Lumbridge", NPCRace.HUMAN, NPCGender.FEMALE);

    // Session 1: fresh service with a fresh in-memory cache, real disk cache. Synthesizes once.
    FakeBackend backend1 = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor exec1 = new DeferredExecutor();
    DialogueAudioService session1 =
        new DialogueAudioService(
            provider(backend1),
            new FakeOutput(),
            new DiskAudioCache(cacheDir),
            exec1,
            8,
            () -> 100);
    session1.speak(line);
    exec1.runAll();
    assertEquals("first session synthesizes the line", 1, backend1.requests.size());

    // Session 2: brand new service and a brand new in-memory cache, same disk dir. The in-memory
    // tier is empty, so without disk this would re-synthesize.
    FakeBackend backend2 = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output2 = new FakeOutput();
    DeferredExecutor exec2 = new DeferredExecutor();
    DialogueAudioService session2 =
        new DialogueAudioService(
            provider(backend2), output2, new DiskAudioCache(cacheDir), exec2, 8, () -> 100);
    session2.speak(line);
    exec2.runAll();

    assertEquals(
        "second session must be served from disk, not re-synthesized", 0, backend2.requests.size());
    assertEquals("the line still plays in the new session", 1, output2.streamCalls);
  }

  @Test
  public void concurrentIdenticalSynthsIssueExactlyOneBackendCall() throws Exception {
    // Two tasks reach the synth step for the same key at once (a real cloud call is slow). The
    // first must be the only one billed; the second waits on and reuses its result.
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    Pcm canned = new Pcm(new float[] {0.2f, -0.2f}, 24_000);
    SynthesisBackend blocking =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            calls.incrementAndGet();
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return canned;
          }
        };
    DialogueAudioService svc =
        service(provider(blocking), new FakeOutput(), new DeferredExecutor(), 8, 100);
    DialogueAudioService.CacheKey key =
        new DialogueAudioService.CacheKey(
            "cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Echo");
    SynthesisRequest request = req("Echo", NPCRace.HUMAN, NPCGender.MALE);

    AtomicReference<Pcm> first = new AtomicReference<>();
    AtomicReference<Pcm> second = new AtomicReference<>();
    Thread owner = new Thread(() -> first.set(svc.synthesizeDeduped(blocking, request, key)));
    owner.start();
    assertTrue("owner reached the backend", entered.await(2, TimeUnit.SECONDS));
    Thread waiter = new Thread(() -> second.set(svc.synthesizeDeduped(blocking, request, key)));
    waiter.start();
    // Wait until the waiter is parked inside the in-flight future's get(), so releasing the owner
    // cannot race ahead and let the waiter register itself as a second owner.
    while (waiter.getState() != Thread.State.WAITING) {
      Thread.onSpinWait();
    }
    release.countDown();
    owner.join(2_000);
    waiter.join(2_000);

    assertEquals(
        "two simultaneous identical requests issue exactly one backend call", 1, calls.get());
    assertNotNull("the owner produced audio", first.get());
    assertSame("the waiter reuses the owner's audio", first.get(), second.get());
  }

  @Test
  public void aStreamingLineAwaitsAnInFlightSynthAndPlaysItBuffered() throws Exception {
    // The streaming analog of the above: when a synth for this key is already in flight (e.g. a
    // prefetch), a streaming line must await it and play it BUFFERED, issuing no second backend
    // call
    // and opening no stream (INV5).
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    Pcm canned = new Pcm(new float[] {0.3f, -0.3f}, 24_000);
    SynthesisBackend blocking =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            return canned;
          }

          @Override
          public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
            calls.incrementAndGet();
            entered.countDown();
            try {
              release.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            sink.accept(canned.getSamples(), canned.getSampleRate());
            return canned;
          }
        };
    FakeOutput output = new FakeOutput();
    DialogueAudioService svc =
        streamingService(provider(blocking), output, new DeferredExecutor(), 8, 100);
    DialogueAudioService.CacheKey key =
        new DialogueAudioService.CacheKey(
            "cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Echo");
    SynthesisRequest request = req("Echo", NPCRace.HUMAN, NPCGender.MALE);

    // Epoch is 0 on a fresh service (no speak yet), so runStreaming(0, ...) passes its epoch guard.
    Thread owner = new Thread(() -> svc.runStreaming(0, blocking, request, key));
    owner.start();
    assertTrue("owner reached the backend stream", entered.await(2, TimeUnit.SECONDS));
    Thread waiter = new Thread(() -> svc.runStreaming(0, blocking, request, key));
    waiter.start();
    // Wait until the waiter parks inside the in-flight future, so releasing cannot race it into
    // registering as a second owner.
    while (waiter.getState() != Thread.State.WAITING) {
      Thread.onSpinWait();
    }
    release.countDown();
    owner.join(2_000);
    waiter.join(2_000);

    assertEquals("exactly one backend stream call despite two streaming lines", 1, calls.get());
    assertEquals("only the owner opened a stream", 1, output.beginStreamCalls);
    assertEquals("the waiter played the deduped result buffered", 1, output.streamCalls);
  }

  @Test
  public void aBlockedLineDoesNotStallTheNextLineOnTheSynthesisPool() throws Exception {
    // A slow/blocked synth (e.g. a backed-off cloud retry that is left running so its result still
    // caches) holds one pool worker; a second, different line must still synthesize and play on the
    // free worker rather than queueing behind it (#196).
    CountDownLatch blockerEntered = new CountDownLatch(1);
    CountDownLatch releaseBlocker = new CountDownLatch(1);
    Pcm canned = new Pcm(new float[] {0.3f, -0.3f}, 24_000);
    SynthesisBackend backend =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            if ("Blocker".equals(request.text())) {
              blockerEntered.countDown();
              try {
                releaseBlocker.await(2, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            return canned;
          }
        };
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8));
    FakeOutput output = new FakeOutput();
    DialogueAudioService svc = service(provider(backend), output, pool, 8, 100);
    try {
      svc.speak(req("Blocker", NPCRace.HUMAN, NPCGender.MALE));
      assertTrue("the blocker occupied a worker", blockerEntered.await(2, TimeUnit.SECONDS));

      svc.speak(req("Second", NPCRace.HUMAN, NPCGender.MALE));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (output.streamCalls == 0 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(
          "the second line played on the free worker while the first was still blocked",
          1,
          output.streamCalls);
    } finally {
      releaseBlocker.countDown();
      pool.shutdownNow();
      pool.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test
  public void lateResponseIsDroppedWhenEpochAdvancesDuringSynth() {
    // Simulates a slow cloud response that lands after the player skipped ahead: the epoch bumps
    // mid-synth, so the now-stale audio must never play even though synthesis succeeded.
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    final DialogueAudioService[] holder = new DialogueAudioService[1];
    SynthesisBackend slow =
        new SynthesisBackend() {
          @Override
          public String id() {
            return "cloud-openrouter";
          }

          @Override
          public boolean isAvailable() {
            return true;
          }

          @Override
          public EnumSet<Emotion> supportedEmotions() {
            return EnumSet.of(Emotion.NEUTRAL);
          }

          @Override
          public Pcm synthesize(SynthesisRequest request) {
            holder[0].interrupt();
            return new Pcm(new float[] {0.1f, -0.1f}, 24_000);
          }
        };
    DialogueAudioService svc = service(provider(slow), output, executor, 8, 100);
    holder[0] = svc;

    svc.speak(req("Stale cloud line", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals(
        "a response that lands after the epoch advanced must not play", 0, output.streamCalls);
  }

  @Test
  public void prefetchWarmsCacheWithoutPlayingOrInterrupting() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.prefetch(req("Yes, I'll help.", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("prefetch synthesizes the line", 1, backend.requests.size());
    assertEquals("prefetch never plays audio", 0, output.streamCalls);
    assertEquals("prefetch never interrupts playback", 0, output.stopCalls);

    // The real line now arrives: it must come from the warmed cache, not a second synth.
    svc.speak(req("Yes, I'll help.", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("the spoken line is served from the prefetched cache", 1, backend.requests.size());
    assertEquals("the spoken line plays once", 1, output.streamCalls);
  }

  @Test
  public void prefetchOfAnAlreadyCachedLineIsANoOp() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), new FakeOutput(), executor, 8, 100);

    svc.speak(req("Already heard", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();
    svc.prefetch(req("Already heard", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("prefetch never re-synthesizes a cached line", 1, backend.requests.size());
  }

  @Test
  public void prefetchSkipsWhenTheBackendIsThrottled() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    backend.throttled = true;
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), new FakeOutput(), executor, 8, 100);

    svc.prefetch(req("Don't pile on the 429", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals("a rate-limited backend is never prefetched", 0, backend.requests.size());
  }

  @Test
  public void cancelPrefetchDropsStillQueuedPrefetches() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), new FakeOutput(), executor, 8, 100);

    // Queued but not yet run: leaving the node cancels it before it can spend.
    svc.prefetch(req("Branch the player left", NPCRace.HUMAN, NPCGender.MALE));
    svc.cancelPrefetch();
    executor.runAll();

    assertEquals("a cancelled prefetch never reaches the backend", 0, backend.requests.size());
  }

  /** Counts disk-tier reads so a lookup on the wrong thread is observable. */
  private static final class CountingDiskCache extends DiskAudioCache {
    final AtomicInteger gets = new AtomicInteger();

    CountingDiskCache(Path dir) {
      super(dir);
    }

    @Override
    public Pcm get(String backendId, String voiceKey, Emotion emotion, String text) {
      gets.incrementAndGet();
      return super.get(backendId, voiceKey, emotion, text);
    }
  }

  @Test
  public void prefetchEarlyOutNeverReadsTheDiskCacheOnTheCallingThread() {
    // prefetch(...) is driven from the game thread, so its pre-submit early-out must stay
    // memory-tier only. Seed the disk tier in one session, then prefetch in a fresh session whose
    // memory tier is empty: everything before the executor drains runs on the caller's thread, so
    // any disk read counted there is a game-thread disk read.
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    SynthesisRequest line = req("Only on disk", NPCRace.HUMAN, NPCGender.MALE);
    FakeBackend seedBackend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor seedExec = new DeferredExecutor();
    DialogueAudioService seeder =
        new DialogueAudioService(
            provider(seedBackend),
            new FakeOutput(),
            new DiskAudioCache(cacheDir),
            seedExec,
            8,
            () -> 100);
    seeder.speak(line);
    seedExec.runAll();
    assertEquals("seeding session wrote the line", 1, seedBackend.requests.size());

    CountingDiskCache disk = new CountingDiskCache(cacheDir);
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc =
        new DialogueAudioService(provider(backend), new FakeOutput(), disk, executor, 8, () -> 100);

    svc.prefetch(line);

    assertEquals(
        "the prefetch call must not read the disk tier on its own thread", 0, disk.gets.get());

    executor.runAll();

    assertTrue("the queued task reads the disk tier on the pool", disk.gets.get() >= 1);
    assertEquals("the disk hit means the backend is never billed", 0, backend.requests.size());
  }

  @Test
  public void prefetchedLineIsNotRebilledWhenSpokenAcrossTiers() {
    // Prefetch writes through to disk too, so even a fresh in-memory tier serves the spoken line.
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    FakeBackend warm = new FakeBackend("cloud-openrouter", EnumSet.allOf(Emotion.class));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc =
        new DialogueAudioService(
            new BackendProvider(warm),
            new FakeOutput(),
            new DiskAudioCache(cacheDir),
            executor,
            8,
            () -> 100);

    svc.prefetch(req("Tell me about the quest.", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();
    svc.speak(req("Tell me about the quest.", NPCRace.HUMAN, NPCGender.MALE));
    executor.runAll();

    assertEquals(
        "prefetch then speak bills the cloud backend exactly once", 1, warm.requests.size());
  }

  @Test
  public void echoPlaysALongerDifferentBufferThanTheDrySynth() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Echoing cave line", NPCRace.HUMAN, NPCGender.MALE), true);
    executor.runAll();

    assertEquals("the echoed line still plays once", 1, output.streamCalls);
    assertTrue("the echoed buffer is longer than the dry synth", output.lastSamples.length > 2);
  }

  @Test
  public void echoIsRenderOnlyAndTheCacheStaysDry() {
    // Speaking with echo, then again without, for the same key must reuse the cached DRY audio:
    // one synth call, and the dry replay streams the original short buffer.
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Same line", NPCRace.HUMAN, NPCGender.MALE), true);
    executor.runAll();
    int echoedLength = output.lastSamples.length;

    svc.speak(req("Same line", NPCRace.HUMAN, NPCGender.MALE), false);
    executor.runAll();

    assertEquals("echo never triggers a second synth", 1, backend.requests.size());
    assertEquals(
        "the dry replay streams the original two-sample buffer", 2, output.lastSamples.length);
    assertTrue("the echoed buffer was longer than the dry one", echoedLength > 2);
    assertArrayEquals(
        "the cached audio stayed dry", new float[] {0.1f, -0.1f}, output.lastSamples, 0f);
  }

  @Test
  public void crossSessionRepeatCostsTheBackendZeroAdditionalSynthCalls() {
    // The headline cloud-cost guarantee (#37): a repeated (backendId, voiceKey, emotion, text)
    // across sessions must never bill the backend again. A fake cloud backend counts synth calls.
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    FakeBackend cloud = new FakeBackend("cloud-openrouter", EnumSet.allOf(Emotion.class));
    SynthesisRequest line =
        req("Have you any quests?", NPCRace.HUMAN, NPCGender.MALE, Emotion.HAPPY);

    // Session 1 on the cloud backend: one paid synth call.
    DeferredExecutor exec1 = new DeferredExecutor();
    DialogueAudioService session1 =
        new DialogueAudioService(
            new BackendProvider(cloud),
            new FakeOutput(),
            new DiskAudioCache(cacheDir),
            exec1,
            8,
            () -> 100);
    session1.speak(line);
    exec1.runAll();
    int afterFirstSession = cloud.requests.size();
    assertEquals("first hearing of the line costs exactly one API call", 1, afterFirstSession);

    // Session 2: fresh in-memory cache, same disk dir, same line. Must cost ZERO additional calls.
    DeferredExecutor exec2 = new DeferredExecutor();
    DialogueAudioService session2 =
        new DialogueAudioService(
            new BackendProvider(cloud),
            new FakeOutput(),
            new DiskAudioCache(cacheDir),
            exec2,
            8,
            () -> 100);
    session2.speak(line);
    exec2.runAll();

    assertEquals(
        "a repeated line across sessions must not re-bill the cloud backend",
        afterFirstSession,
        cloud.requests.size());
  }
}
