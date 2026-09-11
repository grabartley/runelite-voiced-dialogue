package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.audio.AudioOutput;
import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.cache.DiskAudioCache;
import com.grahambartley.runelite.voiced.dialogue.cache.TieredSynthesisCache;
import com.grahambartley.runelite.voiced.dialogue.cache.TieredSynthesisCache.CacheKey;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import com.grahambartley.runelite.voiced.dialogue.speech.spend.SpendTracker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DialogueAudioServiceTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private abstract static class TestBackend implements SynthesisBackend {
    private final String id;
    private final EnumSet<Emotion> supported;

    TestBackend() {
      this("cloud-openrouter", EnumSet.of(Emotion.NEUTRAL));
    }

    TestBackend(String id, EnumSet<Emotion> supported) {
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
    public EnumSet<Emotion> supportedEmotions() {
      return supported;
    }
  }

  private static class FakeBackend extends TestBackend {
    final List<String> requests = new ArrayList<>();
    volatile boolean throttled;

    FakeBackend(EnumSet<Emotion> supported) {
      this("cloud-openrouter", supported);
    }

    FakeBackend(String id, EnumSet<Emotion> supported) {
      super(id, supported);
    }

    @Override
    public boolean isThrottled() {
      return throttled;
    }

    @Override
    public Pcm synthesize(SynthesisRequest request) {
      requests.add(request.voice().key() + "|" + request.emotion() + "|" + request.text());
      return new Pcm(new float[] {0.1f, -0.1f}, 24_000);
    }
  }

  private static final class FakeOutput implements AudioOutput {
    volatile int streamCalls;
    volatile int stopCalls;
    volatile int lastVolume = -1;
    volatile float[] lastSamples;

    volatile int beginStreamCalls;
    volatile int endStreamCalls;
    volatile int lastStreamVolume = -1;
    final List<float[]> streamedChunks = Collections.synchronizedList(new ArrayList<>());

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

  private static final class DeferredExecutor implements Executor {
    final List<Runnable> tasks = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    void runAll() {
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
      BackendProvider provider,
      AudioOutput output,
      DiskAudioCache diskCache,
      Executor executor,
      int cacheSize,
      int volume) {
    return new DialogueAudioService(
        provider,
        output,
        new TieredSynthesisCache(cacheSize, diskCache),
        executor,
        executor,
        executor,
        () -> volume);
  }

  private static DialogueAudioService service(
      BackendProvider provider, AudioOutput output, Executor executor, int cacheSize, int volume) {
    return service(provider, output, null, executor, cacheSize, volume);
  }

  private static DialogueAudioService diskService(
      BackendProvider provider, AudioOutput output, DiskAudioCache disk, Executor executor) {
    return service(provider, output, disk, executor, 8, 100);
  }

  private static SynthesisRequest req(String text, NpcRace race, NpcGender gender) {
    return new SynthesisRequest(
        text, VoiceSpec.npc(race, gender), Emotion.NEUTRAL, TestFixtures.TROLL_PROFILE);
  }

  private static SynthesisRequest req(String text, NpcRace race, NpcGender gender, Emotion e) {
    return new SynthesisRequest(text, VoiceSpec.npc(race, gender), e, TestFixtures.TROLL_PROFILE);
  }

  @Test
  public void repeatedLineIsServedFromCacheWithoutResynth() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Hello adventurer", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    svc.speak(req("Hello adventurer", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("second identical line should hit the cache", 1, backend.requests.size());
    assertEquals("the live miss streamed", 1, output.beginStreamCalls);
    assertEquals("the cached repeat played buffered", 1, output.streamCalls);
  }

  @Test
  public void aLiveMissStreams() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Hello", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("a live miss opens a stream", 1, output.beginStreamCalls);
    assertEquals("and never uses the buffered path", 0, output.streamCalls);
    assertEquals("the stream is ended", 1, output.endStreamCalls);
    assertEquals("the line is synthesized once", 1, backend.requests.size());
    assertTrue("audio was handed to the stream", output.streamedChunks.size() >= 1);
  }

  @Test
  public void aStreamedLineIsCachedSoTheRepeatPlaysFromCacheBuffered() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Encore", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    svc.speak(req("Encore", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("the streamed line was cached, so only one synth", 1, backend.requests.size());
    assertEquals("the first line streamed", 1, output.beginStreamCalls);
    assertEquals("the repeat played from cache, buffered", 1, output.streamCalls);
  }

  @Test
  public void aCaveEchoLineBuffers() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Boo", NpcRace.HUMAN, NpcGender.MALE), true);
    executor.runAll();

    assertEquals(
        "an echo line never streams (echo needs the whole clip)", 0, output.beginStreamCalls);
    assertEquals("it uses the buffered path", 1, output.streamCalls);
  }

  @Test
  public void prefetchNeverStreams() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.prefetch(req("Later", NpcRace.HUMAN, NpcGender.MALE));
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
    SynthesisBackend streaming =
        new TestBackend() {
          @Override
          public Pcm synthesize(SynthesisRequest request) {
            synthed.add(request.text());
            return new Pcm(new float[] {0.1f, 0.2f}, 24_000);
          }

          @Override
          public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
            synthed.add(request.text());
            sink.accept(new float[] {0.1f}, 24_000);
            skipHook[0].run();
            sink.accept(new float[] {0.2f}, 24_000);
            return new Pcm(new float[] {0.1f, 0.2f}, 24_000);
          }
        };
    DialogueAudioService svc = service(provider(streaming), output, executor, 8, 100);
    skipHook[0] = svc::interrupt;

    svc.speak(req("Skipme", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("the line was synthesized once", 1, synthed.size());
    assertEquals(
        "the stream is released even though it was skipped mid-line", 1, output.endStreamCalls);

    svc.speak(req("Skipme", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("the skipped line was cached, so the repeat does not re-synth", 1, synthed.size());
    assertEquals("the repeat plays from cache, buffered", 1, output.streamCalls);
  }

  @Test
  public void aStreamedLineThatIsIncompleteIsPlayedButNotCached() {
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    List<String> synthed = new ArrayList<>();
    SynthesisBackend streaming =
        new TestBackend() {
          @Override
          public Pcm synthesize(SynthesisRequest request) {
            synthed.add(request.text());
            return null;
          }

          @Override
          public Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
            synthed.add(request.text());
            sink.accept(new float[] {0.1f, 0.2f}, 24_000);
            return null;
          }
        };
    DialogueAudioService svc = service(provider(streaming), output, executor, 8, 100);

    svc.speak(req("Clipped", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("it still played through the stream", 1, output.streamedChunks.size());
    assertEquals("the stream is released even on a null return", 1, output.endStreamCalls);

    svc.speak(req("Clipped", NpcRace.HUMAN, NpcGender.MALE));
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
        new TestBackend() {
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
    DialogueAudioService svc = service(provider(streaming), output, executor, 8, 100);

    svc.speak(req("ThreeParts", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("every chunk was forwarded to the player", 3, output.streamedChunks.size());
    assertArrayEquals("chunk 1", new float[] {0.1f}, output.streamedChunks.get(0), 0f);
    assertArrayEquals("chunk 2", new float[] {0.2f}, output.streamedChunks.get(1), 0f);
    assertArrayEquals("chunk 3", new float[] {0.3f}, output.streamedChunks.get(2), 0f);
    assertEquals("streamed, not buffered", 0, output.streamCalls);
    assertEquals("stream ended once", 1, output.endStreamCalls);

    svc.speak(req("ThreeParts", NpcRace.HUMAN, NpcGender.MALE));
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

    svc.speak(req("Greetings", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    svc.speak(req("Greetings", NpcRace.ELF, NpcGender.FEMALE));
    executor.runAll();

    assertEquals("different voices are distinct cache keys", 2, backend.requests.size());
  }

  @Test
  public void sameTextAndVoiceDifferentEmotionIsSynthesizedSeparately() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL, Emotion.ANGRY));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Halt", NpcRace.HUMAN, NpcGender.MALE, Emotion.NEUTRAL));
    executor.runAll();
    svc.speak(req("Halt", NpcRace.HUMAN, NpcGender.MALE, Emotion.ANGRY));
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

    svc.speak(req("First line", NpcRace.HUMAN, NpcGender.MALE));
    svc.speak(req("Second line", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("stale first line should never synthesize", 1, backend.requests.size());
    assertTrue(backend.requests.get(0).endsWith("|Second line"));
    assertEquals("only the live line should play, and it streams", 1, output.beginStreamCalls);
    assertEquals("nothing plays buffered", 0, output.streamCalls);
  }

  @Test
  public void everyNewLineStopsCurrentPlayback() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("a", NpcRace.HUMAN, NpcGender.MALE));
    svc.speak(req("b", NpcRace.HUMAN, NpcGender.MALE));

    assertEquals("each speak interrupts whatever is playing", 2, output.stopCalls);
  }

  @Test
  public void interruptStopsPlaybackAndDropsQueuedLine() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Queued line", NpcRace.HUMAN, NpcGender.MALE));
    svc.interrupt();
    executor.runAll();

    assertTrue("interrupt should stop current audio", output.stopCalls >= 1);
    assertEquals("queued stale line should not synthesize", 0, backend.requests.size());
    assertEquals("queued stale line should not play", 0, output.streamCalls);
  }

  @Test
  public void failedSynthIsNotCachedOrPlayed() {
    SynthesisBackend failing =
        new TestBackend() {
          @Override
          public Pcm synthesize(SynthesisRequest request) {
            return null;
          }
        };
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(failing), output, executor, 8, 100);

    svc.speak(req("anything", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("null synth result should not play", 0, output.streamCalls);
  }

  @Test
  public void emptyTextIsIgnored() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("", NpcRace.HUMAN, NpcGender.MALE));
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

    svc.speak(req("line", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    assertEquals(
        "the streamed live miss opens its line at the configured volume",
        42,
        output.lastStreamVolume);

    svc.speak(req("line", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    assertEquals("and the buffered cache hit plays at it too", 42, output.lastVolume);
  }

  @Test
  public void unsupportedEmotionDowngradesToNeutralAndSharesCacheEntry() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Be gone", NpcRace.HUMAN, NpcGender.MALE, Emotion.NEUTRAL));
    executor.runAll();
    svc.speak(req("Be gone", NpcRace.HUMAN, NpcGender.MALE, Emotion.ANGRY));
    executor.runAll();

    assertEquals("downgraded emotion reuses the neutral cache entry", 1, backend.requests.size());
    assertTrue(backend.requests.get(0).contains("|NEUTRAL|"));
    assertEquals("the first line streamed", 1, output.beginStreamCalls);
    assertEquals("the downgraded repeat played buffered from cache", 1, output.streamCalls);
  }

  @Test
  public void lineSynthesizedInOneSessionIsServedFromDiskInTheNext() {
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    SynthesisRequest line = req("Welcome to Lumbridge", NpcRace.HUMAN, NpcGender.FEMALE);

    FakeBackend backend1 = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor exec1 = new DeferredExecutor();
    DialogueAudioService session1 =
        diskService(provider(backend1), new FakeOutput(), new DiskAudioCache(cacheDir), exec1);
    session1.speak(line);
    exec1.runAll();
    assertEquals("first session synthesizes the line", 1, backend1.requests.size());

    FakeBackend backend2 = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output2 = new FakeOutput();
    DeferredExecutor exec2 = new DeferredExecutor();
    DialogueAudioService session2 =
        diskService(provider(backend2), output2, new DiskAudioCache(cacheDir), exec2);
    session2.speak(line);
    exec2.runAll();

    assertEquals(
        "second session must be served from disk, not re-synthesized", 0, backend2.requests.size());
    assertEquals("the line still plays in the new session", 1, output2.streamCalls);
  }

  @Test
  public void aStreamingLineAwaitsAnInFlightSynthAndPlaysItBuffered() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    Pcm canned = new Pcm(new float[] {0.3f, -0.3f}, 24_000);
    SynthesisBackend blocking =
        new TestBackend() {
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
    DialogueAudioService svc = service(provider(blocking), output, new DeferredExecutor(), 8, 100);
    CacheKey key = new CacheKey("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Echo");
    SynthesisRequest request = req("Echo", NpcRace.HUMAN, NpcGender.MALE);

    Thread owner = new Thread(() -> svc.runStreaming(0, blocking, request, key));
    owner.start();
    assertTrue("owner reached the backend stream", entered.await(2, TimeUnit.SECONDS));
    Thread waiter = new Thread(() -> svc.runStreaming(0, blocking, request, key));
    waiter.start();
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
    CountDownLatch blockerEntered = new CountDownLatch(1);
    CountDownLatch releaseBlocker = new CountDownLatch(1);
    Pcm canned = new Pcm(new float[] {0.3f, -0.3f}, 24_000);
    SynthesisBackend backend =
        new TestBackend() {
          @Override
          public Pcm synthesize(SynthesisRequest request) {
            if ("Blocker".equals(request.text())) {
              blockerEntered.countDown();
              try {
                releaseBlocker.await();
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
      svc.speak(req("Blocker", NpcRace.HUMAN, NpcGender.MALE));
      assertTrue("the blocker occupied a worker", blockerEntered.await(2, TimeUnit.SECONDS));

      svc.speak(req("Second", NpcRace.HUMAN, NpcGender.MALE));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (output.endStreamCalls == 0 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertEquals(
          "the second line streamed to completion on the free worker while the first was blocked",
          1,
          output.endStreamCalls);
      assertEquals("and both lines opened a stream", 2, output.beginStreamCalls);
    } finally {
      releaseBlocker.countDown();
      pool.shutdownNow();
      pool.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test
  public void lateResponseIsDroppedWhenEpochAdvancesDuringSynth() {
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    final DialogueAudioService[] holder = new DialogueAudioService[1];
    SynthesisBackend slow =
        new TestBackend() {
          @Override
          public Pcm synthesize(SynthesisRequest request) {
            holder[0].interrupt();
            return new Pcm(new float[] {0.1f, -0.1f}, 24_000);
          }
        };
    DialogueAudioService svc = service(provider(slow), output, executor, 8, 100);
    holder[0] = svc;

    svc.speak(req("Stale cloud line", NpcRace.HUMAN, NpcGender.MALE));
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

    svc.prefetch(req("Yes, I'll help.", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("prefetch synthesizes the line", 1, backend.requests.size());
    assertEquals("prefetch never plays audio", 0, output.streamCalls);
    assertEquals("prefetch never interrupts playback", 0, output.stopCalls);

    svc.speak(req("Yes, I'll help.", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("the spoken line is served from the prefetched cache", 1, backend.requests.size());
    assertEquals("the spoken line plays once", 1, output.streamCalls);
  }

  @Test
  public void prefetchOfAnAlreadyCachedLineIsANoOp() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), new FakeOutput(), executor, 8, 100);

    svc.speak(req("Already heard", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    svc.prefetch(req("Already heard", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("prefetch never re-synthesizes a cached line", 1, backend.requests.size());
  }

  @Test
  public void prefetchSkipsWhenTheBackendIsThrottled() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    backend.throttled = true;
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), new FakeOutput(), executor, 8, 100);

    svc.prefetch(req("Don't pile on the 429", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();

    assertEquals("a rate-limited backend is never prefetched", 0, backend.requests.size());
  }

  @Test
  public void cancelPrefetchDropsStillQueuedPrefetches() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), new FakeOutput(), executor, 8, 100);

    svc.prefetch(req("Branch the player left", NpcRace.HUMAN, NpcGender.MALE));
    svc.cancelPrefetch();
    executor.runAll();

    assertEquals("a cancelled prefetch never reaches the backend", 0, backend.requests.size());
  }

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
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    SynthesisRequest line = req("Only on disk", NpcRace.HUMAN, NpcGender.MALE);
    FakeBackend seedBackend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor seedExec = new DeferredExecutor();
    DialogueAudioService seeder =
        diskService(
            provider(seedBackend), new FakeOutput(), new DiskAudioCache(cacheDir), seedExec);
    seeder.speak(line);
    seedExec.runAll();
    assertEquals("seeding session wrote the line", 1, seedBackend.requests.size());

    CountingDiskCache disk = new CountingDiskCache(cacheDir);
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = diskService(provider(backend), new FakeOutput(), disk, executor);

    svc.prefetch(line);

    assertEquals(
        "the prefetch call must not read the disk tier on its own thread", 0, disk.gets.get());

    executor.runAll();

    assertTrue("the queued task reads the disk tier on the pool", disk.gets.get() >= 1);
    assertEquals("the disk hit means the backend is never billed", 0, backend.requests.size());
  }

  @Test
  public void prefetchedLineIsNotRebilledWhenSpokenAcrossTiers() {
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    FakeBackend warm = new FakeBackend("cloud-openrouter", EnumSet.allOf(Emotion.class));
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc =
        diskService(provider(warm), new FakeOutput(), new DiskAudioCache(cacheDir), executor);

    svc.prefetch(req("Tell me about the quest.", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    svc.speak(req("Tell me about the quest.", NpcRace.HUMAN, NpcGender.MALE));
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

    svc.speak(req("Echoing cave line", NpcRace.HUMAN, NpcGender.MALE), true);
    executor.runAll();

    assertEquals("the echoed line still plays once", 1, output.streamCalls);
    assertTrue("the echoed buffer is longer than the dry synth", output.lastSamples.length > 2);
  }

  @Test
  public void echoIsRenderOnlyAndTheCacheStaysDry() {
    FakeBackend backend = new FakeBackend(EnumSet.of(Emotion.NEUTRAL));
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Same line", NpcRace.HUMAN, NpcGender.MALE), true);
    executor.runAll();
    int echoedLength = output.lastSamples.length;

    svc.speak(req("Same line", NpcRace.HUMAN, NpcGender.MALE), false);
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
    Path cacheDir = tmp.getRoot().toPath().resolve("cache");
    FakeBackend cloud = new FakeBackend("cloud-openrouter", EnumSet.allOf(Emotion.class));
    SynthesisRequest line =
        req("Have you any quests?", NpcRace.HUMAN, NpcGender.MALE, Emotion.HAPPY);

    DeferredExecutor exec1 = new DeferredExecutor();
    DialogueAudioService session1 =
        diskService(provider(cloud), new FakeOutput(), new DiskAudioCache(cacheDir), exec1);
    session1.speak(line);
    exec1.runAll();
    int afterFirstSession = cloud.requests.size();
    assertEquals("first hearing of the line costs exactly one API call", 1, afterFirstSession);

    DeferredExecutor exec2 = new DeferredExecutor();
    DialogueAudioService session2 =
        diskService(provider(cloud), new FakeOutput(), new DiskAudioCache(cacheDir), exec2);
    session2.speak(line);
    exec2.runAll();

    assertEquals(
        "a repeated line across sessions must not re-bill the cloud backend",
        afterFirstSession,
        cloud.requests.size());
  }

  @Test
  public void cachedReplaysAndPrefetchHitsNeverAddToTheSpendReadout() {
    SpendTracker spend = new SpendTracker();
    SynthesisBackend backend =
        new FakeBackend(EnumSet.of(Emotion.NEUTRAL)) {
          @Override
          public Pcm synthesize(SynthesisRequest request) {
            spend.recordSpeech(
                VoicedDialogueConfig.TtsProvider.OPENROUTER,
                request.text().length(),
                request.prefetch());
            return super.synthesize(request);
          }
        };
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(provider(backend), output, executor, 8, 100);

    svc.speak(req("Hello adventurer", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    SpendTracker.ProviderSpend afterFirst = spend.snapshot().get(0);
    assertEquals("the first hearing is a real backend call", 1, afterFirst.voicedLines());

    svc.speak(req("Hello adventurer", NpcRace.HUMAN, NpcGender.MALE));
    executor.runAll();
    svc.prefetch(req("Hello adventurer", NpcRace.HUMAN, NpcGender.MALE).asPrefetch());
    executor.runAll();

    SpendTracker.ProviderSpend after = spend.snapshot().get(0);
    assertEquals("a replayed line costs nothing", 1, after.voicedLines());
    assertEquals("a prefetch of a cached line costs nothing", 0, after.prefetchedLines());
    assertEquals(
        "no extra characters were sent", afterFirst.speechCharacters(), after.speechCharacters());
  }
}
