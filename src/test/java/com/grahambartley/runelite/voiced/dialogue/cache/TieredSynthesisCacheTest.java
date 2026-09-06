package com.grahambartley.runelite.voiced.dialogue.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.cache.TieredSynthesisCache.CacheKey;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TieredSynthesisCacheTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private static final CacheKey KEY =
      new CacheKey("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Echo");

  /** Counts disk-tier reads so tier promotion is observable. */
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

  private Path cacheDir() {
    return tmp.getRoot().toPath().resolve("cache");
  }

  private static Pcm pcm(float sample) {
    return new Pcm(new float[] {sample}, 24_000);
  }

  private static Pcm synthOnce(TieredSynthesisCache cache, CacheKey key, Pcm result) {
    return cache.withInFlight(key, () -> result, () -> {});
  }

  @Test
  public void anUnknownKeyMissesBothTiers() {
    TieredSynthesisCache cache = new TieredSynthesisCache(8, null);
    assertNull(cache.lookup(KEY));
    assertNull(cache.memoryHit(KEY));
  }

  @Test
  public void aSynthIsWrittenThroughToBothTiers() {
    DiskAudioCache disk = new DiskAudioCache(cacheDir());
    Pcm synthed = pcm(0.5f);
    synthOnce(new TieredSynthesisCache(8, disk), KEY, synthed);

    // A brand new cache over the same directory has an empty memory tier, so a hit here can only
    // have come from disk.
    TieredSynthesisCache nextSession = new TieredSynthesisCache(8, new DiskAudioCache(cacheDir()));
    Pcm read = nextSession.lookup(KEY);
    assertNotNull("the line survives into a fresh session", read);
    assertEquals(0.5f, read.getSamples()[0], 0f);
  }

  @Test
  public void aDiskHitIsPromotedIntoMemory() {
    synthOnce(new TieredSynthesisCache(8, new DiskAudioCache(cacheDir())), KEY, pcm(0.5f));

    CountingDiskCache disk = new CountingDiskCache(cacheDir());
    TieredSynthesisCache cache = new TieredSynthesisCache(8, disk);

    assertNotNull("the first lookup falls through to disk", cache.lookup(KEY));
    assertEquals("which cost exactly one disk read", 1, disk.gets.get());
    assertNotNull("the promoted entry is now in the memory tier", cache.memoryHit(KEY));

    assertNotNull(cache.lookup(KEY));
    assertEquals("the replay never touches disk again", 1, disk.gets.get());
  }

  @Test
  public void memoryHitNeverReadsTheDiskTier() {
    synthOnce(new TieredSynthesisCache(8, new DiskAudioCache(cacheDir())), KEY, pcm(0.5f));

    CountingDiskCache disk = new CountingDiskCache(cacheDir());
    assertNull(new TieredSynthesisCache(8, disk).memoryHit(KEY));
    assertEquals("the game-thread-safe lookup must not reach the filesystem", 0, disk.gets.get());
  }

  @Test
  public void aFailedSynthIsNotCached() {
    TieredSynthesisCache cache = new TieredSynthesisCache(8, new DiskAudioCache(cacheDir()));

    assertNull(cache.withInFlight(KEY, () -> null, () -> {}));
    assertNull("nothing was written through", cache.lookup(KEY));
  }

  @Test
  public void aCacheWithNoDiskTierStillServesFromMemory() {
    TieredSynthesisCache cache = new TieredSynthesisCache(8, null);
    Pcm synthed = pcm(0.25f);

    assertSame(synthed, synthOnce(cache, KEY, synthed));
    assertSame(synthed, cache.lookup(KEY));
  }

  @Test
  public void concurrentIdenticalSynthsIssueExactlyOneBackendCall() throws Exception {
    // Two callers reach the synth step for the same key at once (a real cloud call is slow). The
    // first must be the only one billed; the second waits on and reuses its result.
    TieredSynthesisCache cache = new TieredSynthesisCache(8, null);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    AtomicBoolean waiterWasDeduped = new AtomicBoolean();
    Pcm canned = pcm(0.2f);

    Supplier<Pcm> slowSynth =
        () -> {
          calls.incrementAndGet();
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return canned;
        };

    AtomicReference<Pcm> first = new AtomicReference<>();
    AtomicReference<Pcm> second = new AtomicReference<>();
    AtomicBoolean ownerWasDeduped = new AtomicBoolean();
    Thread owner =
        new Thread(
            () -> first.set(cache.withInFlight(KEY, slowSynth, () -> ownerWasDeduped.set(true))));
    owner.start();
    assertTrue("owner reached the synth", entered.await(2, TimeUnit.SECONDS));

    Thread waiter =
        new Thread(
            () -> second.set(cache.withInFlight(KEY, slowSynth, () -> waiterWasDeduped.set(true))));
    waiter.start();
    // Wait until the waiter is parked inside the in-flight future, so releasing the owner cannot
    // race ahead and let the waiter register itself as a second owner.
    while (waiter.getState() != Thread.State.WAITING) {
      Thread.onSpinWait();
    }
    release.countDown();
    owner.join(2_000);
    waiter.join(2_000);

    assertEquals("two simultaneous identical requests issue exactly one synth", 1, calls.get());
    assertNotNull("the owner produced audio", first.get());
    assertSame("the waiter reuses the owner's audio", first.get(), second.get());
    assertFalse("the owner ran the synth itself", ownerWasDeduped.get());
    assertTrue("the waiter was told it reused an in-flight synth", waiterWasDeduped.get());
  }

  @Test
  public void aKeyIsSynthesizableAgainOnceTheFirstSynthFinishes() {
    // The in-flight registration must be cleared in a finally, or a failed synth would wedge the
    // key forever.
    TieredSynthesisCache cache = new TieredSynthesisCache(8, null);

    try {
      cache.withInFlight(
          KEY,
          () -> {
            throw new IllegalStateException("backend blew up");
          },
          () -> {});
    } catch (IllegalStateException expected) {
      // The caller sees the failure; the registry must not keep the key.
    }

    Pcm retry = pcm(0.9f);
    assertSame("a retry after a failed synth is not blocked", retry, synthOnce(cache, KEY, retry));
    assertSame("and its result is cached", retry, cache.lookup(KEY));
  }
}
