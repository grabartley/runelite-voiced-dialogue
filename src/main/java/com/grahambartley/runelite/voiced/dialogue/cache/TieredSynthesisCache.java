package com.grahambartley.runelite.voiced.dialogue.cache;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * The two-tier synthesis cache behind {@code DialogueAudioService}: an in-memory {@link LruCache}
 * in front of an optional persistent {@link DiskAudioCache}, plus the in-flight registry that
 * de-duplicates concurrent synthesis.
 *
 * <p>Lookup order is memory → disk → the caller's synth, promoting a disk hit into memory and
 * writing a fresh synth through to both tiers, so lines survive across sessions and cloud backends
 * are not re-billed for audio the user has already heard. If two callers reach the synth step for
 * the same key at once, only the first calls the backend and the second waits on its result, so a
 * billable cloud line is never paid for twice in parallel.
 */
@Slf4j
public final class TieredSynthesisCache {

  private static final int LOG_TEXT_PREVIEW_LENGTH = 40;

  /**
   * Identifies a synthesized line. The active backend, the resolved voice, the (possibly
   * downgraded) emotion, and the text are all part of the identity, so the same words spoken with a
   * different backend, voice, or emotion are distinct cache entries.
   */
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

  /**
   * The in-memory tier alone. The only lookup that is safe from the game thread, where a
   * synchronous disk read would stall the client.
   */
  public Pcm memoryHit(CacheKey key) {
    return memory.get(key);
  }

  /**
   * Memory then disk lookup; a disk hit is promoted into memory. {@code null} when both miss. Every
   * hit notes its tier and the lookup cost, so a slow disk serve is visible; a miss is left to the
   * synth trace that follows, keeping speculative prefetch misses out of the log.
   */
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
      // Reaches the filesystem, so this must run on the pipeline thread, never the game thread.
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

  /**
   * Runs {@code synth} for {@code key} with at most one backend call per key in flight. The first
   * caller registers a pending result, synthesizes, writes a non-null result through to both tiers,
   * and publishes it; a caller that finds a synth already running for the same key runs {@code
   * onDeduped} and waits on that result instead of issuing a second (billable) call. Returns {@code
   * null} when the synth produced nothing.
   */
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
      // Publish before deregistering so a waiter that already grabbed this future is never left
      // blocked, and a fresh request right after sees a populated cache rather than re-synthing.
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
    // join() rather than get() so there is no InterruptedException to catch and no need to re-raise
    // the thread interrupt flag (a Hub constraint); a failed synth surfaces as an unchecked
    // CompletionException, which drops the line.
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
