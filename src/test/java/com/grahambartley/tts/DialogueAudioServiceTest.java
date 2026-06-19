package com.grahambartley.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;

public class DialogueAudioServiceTest {

  /** Records synth requests and hands back canned PCM so cache behavior is observable. */
  private static final class FakeSynth implements Synthesizer {
    final List<String> requests = new ArrayList<>();

    @Override
    public Pcm synthesize(String text, int speakerId) {
      requests.add(speakerId + "|" + text);
      return new Pcm(new float[] {0.1f, -0.1f}, 24_000);
    }
  }

  /** Records playback and interruption so the pipeline's decisions are observable. */
  private static final class FakeOutput implements AudioOutput {
    int streamCalls;
    int stopCalls;
    int lastVolume = -1;

    @Override
    public void stream(float[] samples, int sampleRate, int volumePercent) {
      streamCalls++;
      lastVolume = volumePercent;
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

  private static DialogueAudioService service(
      Synthesizer synth, AudioOutput output, Executor executor, int cacheSize, int volume) {
    return new DialogueAudioService(synth, output, executor, cacheSize, () -> volume);
  }

  @Test
  public void repeatedLineIsServedFromCacheWithoutResynth() {
    FakeSynth synth = new FakeSynth();
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(synth, output, executor, 8, 100);

    svc.speak("Hello adventurer", 5);
    executor.runAll();
    svc.speak("Hello adventurer", 5);
    executor.runAll();

    assertEquals("second identical line should hit the cache", 1, synth.requests.size());
    assertEquals("both lines should still play", 2, output.streamCalls);
  }

  @Test
  public void sameTextDifferentVoiceIsSynthesizedSeparately() {
    FakeSynth synth = new FakeSynth();
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(synth, output, executor, 8, 100);

    svc.speak("Greetings", 1);
    executor.runAll();
    svc.speak("Greetings", 2);
    executor.runAll();

    assertEquals("different voices are distinct cache keys", 2, synth.requests.size());
  }

  @Test
  public void staleLineIsDroppedWhenSupersededBeforeItRuns() {
    FakeSynth synth = new FakeSynth();
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(synth, output, executor, 8, 100);

    // Two lines enqueued before either runs: advancing dialogue supersedes the first.
    svc.speak("First line", 1);
    svc.speak("Second line", 1);
    executor.runAll();

    assertEquals("stale first line should never synthesize", 1, synth.requests.size());
    assertEquals("1|Second line", synth.requests.get(0));
    assertEquals("only the live line should play", 1, output.streamCalls);
  }

  @Test
  public void everyNewLineStopsCurrentPlayback() {
    FakeSynth synth = new FakeSynth();
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(synth, output, executor, 8, 100);

    svc.speak("a", 1);
    svc.speak("b", 1);

    assertEquals("each speak interrupts whatever is playing", 2, output.stopCalls);
  }

  @Test
  public void interruptStopsPlaybackAndDropsQueuedLine() {
    FakeSynth synth = new FakeSynth();
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(synth, output, executor, 8, 100);

    svc.speak("Queued line", 1);
    svc.interrupt();
    executor.runAll();

    assertTrue("interrupt should stop current audio", output.stopCalls >= 1);
    assertEquals("queued stale line should not synthesize", 0, synth.requests.size());
    assertEquals("queued stale line should not play", 0, output.streamCalls);
  }

  @Test
  public void failedSynthIsNotCachedOrPlayed() {
    Synthesizer failing = (text, speakerId) -> null;
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(failing, output, executor, 8, 100);

    svc.speak("anything", 1);
    executor.runAll();

    assertEquals("null synth result should not play", 0, output.streamCalls);
  }

  @Test
  public void emptyTextIsIgnored() {
    FakeSynth synth = new FakeSynth();
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(synth, output, executor, 8, 100);

    svc.speak("", 1);
    svc.speak(null, 1);
    executor.runAll();

    assertEquals(0, synth.requests.size());
    assertEquals(0, output.streamCalls);
    assertEquals("empty lines do not even interrupt", 0, output.stopCalls);
  }

  @Test
  public void currentVolumeIsForwardedToPlayback() {
    FakeSynth synth = new FakeSynth();
    FakeOutput output = new FakeOutput();
    DeferredExecutor executor = new DeferredExecutor();
    DialogueAudioService svc = service(synth, output, executor, 8, 42);

    svc.speak("line", 1);
    executor.runAll();

    assertEquals(42, output.lastVolume);
  }
}
