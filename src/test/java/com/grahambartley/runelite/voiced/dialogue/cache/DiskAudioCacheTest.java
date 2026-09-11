package com.grahambartley.runelite.voiced.dialogue.cache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DiskAudioCacheTest {

  private static final long MTIME_BASE = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private Path cacheDir() {
    return tmp.getRoot().toPath().resolve("cache");
  }

  private static Pcm pcm(int sampleRate, float... samples) {
    return new Pcm(samples, sampleRate);
  }

  @Test
  public void missWhenNothingStored() {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    assertNull(cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Hello"));
  }

  @Test
  public void roundTripsPcmAndPreservesSampleRate() {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    Pcm stored = pcm(48_000, 0.5f, -0.25f, 1.0f, -1.0f, 0.0f);
    cache.put("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Hello", stored);

    Pcm read = cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Hello");
    assertNotNull(read);
    assertEquals("sample rate must survive disk round-trip", 48_000, read.getSampleRate());
    assertArrayEquals(stored.getSamples(), read.getSamples(), 0.0f);
  }

  @Test
  public void survivesAcrossFreshCacheInstance() {
    Pcm stored = pcm(24_000, 0.1f, -0.1f);
    new DiskAudioCache(cacheDir())
        .put("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Persist me", stored);

    Pcm read =
        new DiskAudioCache(cacheDir())
            .get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Persist me");
    assertNotNull("a fresh session should still find the line on disk", read);
    assertArrayEquals(stored.getSamples(), read.getSamples(), 0.0f);
  }

  @Test
  public void differentEmotionDoesNotCollide() {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    cache.put("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Halt", pcm(24_000, 0.1f));
    cache.put("cloud-openrouter", "npc:HUMAN:MALE", Emotion.ANGRY, "Halt", pcm(24_000, 0.9f));

    Pcm neutral = cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Halt");
    Pcm angry = cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.ANGRY, "Halt");
    assertArrayEquals(new float[] {0.1f}, neutral.getSamples(), 0.0f);
    assertArrayEquals(new float[] {0.9f}, angry.getSamples(), 0.0f);
  }

  @Test
  public void differentBackendDoesNotCollide() {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    cache.put(
        "cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Greetings", pcm(24_000, 0.2f));
    cache.put("other-backend", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Greetings", pcm(24_000, 0.8f));

    assertArrayEquals(
        new float[] {0.2f},
        cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Greetings").getSamples(),
        0.0f);
    assertArrayEquals(
        new float[] {0.8f},
        cache.get("other-backend", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Greetings").getSamples(),
        0.0f);
  }

  @Test
  public void corruptFileIsTreatedAsMissAndDeleted() throws IOException {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    cache.put(
        "cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Boom", pcm(24_000, 0.3f, 0.4f));

    Path entry = onlyEntry(cacheDir());
    Files.write(entry, "not a valid cache file".getBytes(StandardCharsets.UTF_8));

    assertNull(
        "a corrupt file must read as a miss, not crash",
        cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Boom"));
    assertTrue("the corrupt file should be deleted on miss", Files.notExists(entry));

    cache.put(
        "cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Boom", pcm(24_000, 0.3f, 0.4f));
    assertNotNull(cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Boom"));
  }

  @Test
  public void truncatedFileIsTreatedAsMiss() throws IOException {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    cache.put(
        "cloud-openrouter",
        "npc:HUMAN:MALE",
        Emotion.NEUTRAL,
        "Cut",
        pcm(24_000, 0.3f, 0.4f, 0.5f));

    Path entry = onlyEntry(cacheDir());
    byte[] full = Files.readAllBytes(entry);
    Files.write(entry, java.util.Arrays.copyOf(full, full.length - 4));

    assertNull(cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Cut"));
  }

  @Test
  public void evictionKeepsUsageBoundedAndDropsOldest() throws Exception {
    long cap = 600;
    DiskAudioCache cache = new DiskAudioCache(cacheDir(), cap);

    float[] samples = new float[26];
    for (int i = 0; i < 12; i++) {
      putStamped(cache, "npc:HUMAN:MALE", "line-" + i, new Pcm(samples, 24_000), i);
    }

    assertTrue("total cache size must stay under the cap", dirSize(cacheDir()) <= cap);
    assertNull(
        "oldest line should be evicted",
        cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "line-0"));
    assertNotNull(
        "newest line should remain",
        cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "line-11"));
  }

  @Test
  public void unlimitedCapNeverEvicts() throws Exception {
    DiskAudioCache cache = new DiskAudioCache(cacheDir(), DiskAudioCache.UNLIMITED);

    float[] samples = new float[26];
    for (int i = 0; i < 12; i++) {
      cache.put(
          "cloud-openrouter",
          "npc:HUMAN:MALE",
          Emotion.NEUTRAL,
          "line-" + i,
          new Pcm(samples, 24_000));
    }

    assertNotNull(
        "the oldest line survives under an unlimited cache",
        cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "line-0"));
    assertNotNull(
        "the newest line survives too",
        cache.get("cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "line-11"));
  }

  @Test
  public void readDoesNotRescueAnEntryFromFifoEviction() throws Exception {
    long cap = 600;
    DiskAudioCache cache = new DiskAudioCache(cacheDir(), cap);
    float[] samples = new float[26];

    putStamped(cache, "v", "first", new Pcm(samples, 24_000), 0);
    assertNotNull(
        "the oldest entry is present before the cache fills",
        cache.get("cloud-openrouter", "v", Emotion.NEUTRAL, "first"));

    for (int i = 0; i < 5; i++) {
      putStamped(cache, "v", "fill-" + i, new Pcm(samples, 24_000), i + 1);
    }

    assertTrue("total cache size must stay under the cap", dirSize(cacheDir()) <= cap);
    assertNull(
        "the oldest entry is evicted FIFO even though it was read",
        cache.get("cloud-openrouter", "v", Emotion.NEUTRAL, "first"));
    assertNotNull(
        "the newest entry survives", cache.get("cloud-openrouter", "v", Emotion.NEUTRAL, "fill-4"));
  }

  @Test
  public void readFailureDoesNotThrowWhenDirIsUnreadable() {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    assertNull(cache.get("x", "y", Emotion.NEUTRAL, "z"));
    cache.put("x", "y", Emotion.NEUTRAL, "z", new Pcm(new float[] {0f}, 24_000));
  }

  @Test
  public void storedBytesAreLittleEndianFloat32AfterTheHeader() throws IOException {
    DiskAudioCache cache = new DiskAudioCache(cacheDir());
    cache.put(
        "cloud-openrouter", "npc:HUMAN:MALE", Emotion.NEUTRAL, "Bytes", pcm(24_000, 0.5f, -0.25f));

    byte[] stored = Files.readAllBytes(onlyEntry(cacheDir()));
    assertEquals("header(16) + 2 float32 samples", 16 + 2 * 4, stored.length);
    ByteBuffer buf = ByteBuffer.wrap(stored).order(ByteOrder.LITTLE_ENDIAN);
    assertEquals("magic \"TDC1\"", 0x54_44_43_31, buf.getInt());
    assertEquals("sample rate", 24_000, buf.getInt());
    assertEquals("sample count", 2, buf.getInt());
    assertEquals("reserved word", 0, buf.getInt());
    assertEquals(0.5f, buf.getFloat(), 0f);
    assertEquals(-0.25f, buf.getFloat(), 0f);
  }

  private void putStamped(DiskAudioCache cache, String voiceKey, String text, Pcm pcm, int order)
      throws IOException {
    Set<Path> before = entries(cacheDir());
    cache.put("cloud-openrouter", voiceKey, Emotion.NEUTRAL, text, pcm);
    for (Path p : entries(cacheDir())) {
      if (!before.contains(p)) {
        Files.setLastModifiedTime(p, FileTime.fromMillis(MTIME_BASE + order * 1_000L));
      }
    }
  }

  private static Set<Path> entries(Path dir) throws IOException {
    Set<Path> paths = new HashSet<>();
    if (!Files.isDirectory(dir)) {
      return paths;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tdc")) {
      for (Path p : stream) {
        paths.add(p);
      }
    }
    return paths;
  }

  private static Path onlyEntry(Path dir) throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tdc")) {
      for (Path p : stream) {
        return p;
      }
    }
    throw new IllegalStateException("no cache entry found in " + dir);
  }

  private static long dirSize(Path dir) throws IOException {
    long total = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.tdc")) {
      for (Path p : stream) {
        total += Files.size(p);
      }
    }
    return total;
  }
}
