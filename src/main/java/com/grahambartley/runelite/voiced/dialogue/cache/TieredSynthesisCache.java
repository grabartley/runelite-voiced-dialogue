package com.grahambartley.runelite.voiced.dialogue.cache;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TieredSynthesisCache {

  private static final int LOG_TEXT_PREVIEW_LENGTH = 40;

  @Value
  @Accessors(fluent = true)
  public static class CacheKey {
    String backendId;
    String voiceKey;
    Emotion emotion;
    String text;

    public String textPreview() {
      return text.length() <= LOG_TEXT_PREVIEW_LENGTH
          ? text
          : text.substring(0, LOG_TEXT_PREVIEW_LENGTH) + "...";
    }
  }

  public final LruCache<CacheKey, Pcm> memory;
  private final DiskAudioCache disk;
  public final ConcurrentHashMap<CacheKey, CompletableFuture<Pcm>> inFlight =
      new ConcurrentHashMap<>();

  public TieredSynthesisCache(int maxEntries, DiskAudioCache disk) {
    this.memory = new LruCache<>(maxEntries);
    this.disk = disk;
  }

  public Pcm memoryHit(CacheKey key) {
    return memory.get(key);
  }

  public Pcm lookup(CacheKey key) {
    long start = System.nanoTime();
    Pcm pcm = memory.get(key);
    if (pcm != null) {
      log.debug(
          "[TTS cache] hit tier=memory lookupMs={} ({}/{}) \"{}\"",
          elapsedMs(start),
          key.backendId(),
          key.voiceKey(),
          key.textPreview());
      return pcm;
    }
    if (disk != null) {
      pcm = disk.get(key.backendId(), key.voiceKey(), key.emotion(), key.text());
      if (pcm != null) {
        memory.put(key, pcm);
        log.debug(
            "[TTS cache] hit tier=disk lookupMs={} ({}/{}) \"{}\"",
            elapsedMs(start),
            key.backendId(),
            key.voiceKey(),
            key.textPreview());
      }
    }
    return pcm;
  }

  public Pcm withInFlight(CacheKey key, Supplier<Pcm> synth, Runnable onDeduped) {
    CompletableFuture<Pcm> own = new CompletableFuture<>();
    CompletableFuture<Pcm> running = inFlight.putIfAbsent(key, own);
    if (running != null) {
      onDeduped.run();
      return await(running);
    }
    Pcm pcm = null;
    try {
      pcm = synth.get();
      if (pcm != null) {
        writeThrough(key, pcm);
      }
    } finally {
      own.complete(pcm);
      inFlight.remove(key, own);
    }
    return pcm;
  }

  private void writeThrough(CacheKey key, Pcm pcm) {
    memory.put(key, pcm);
    if (disk != null) {
      disk.put(key.backendId(), key.voiceKey(), key.emotion(), key.text(), pcm);
    }
  }

  private static Pcm await(CompletableFuture<Pcm> future) {
    try {
      return future.join();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static long elapsedMs(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }
}
