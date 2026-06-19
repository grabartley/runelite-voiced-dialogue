package com.grahambartley.tts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A tiny bounded LRU cache.
 *
 * <p>Backed by an access-ordered {@link LinkedHashMap}, so reading an entry marks it most-recently
 * used and the least-recently used entry is evicted once {@code maxSize} is exceeded. All access is
 * synchronized because the dialogue pipeline reads and writes it from a background thread.
 */
public final class LruCache<K, V> {

  private final int maxSize;
  private final LinkedHashMap<K, V> map;

  public LruCache(int maxSize) {
    if (maxSize < 1) {
      throw new IllegalArgumentException("maxSize must be at least 1");
    }
    this.maxSize = maxSize;
    this.map =
        new LinkedHashMap<K, V>(16, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > LruCache.this.maxSize;
          }
        };
  }

  /** Returns the cached value (marking it most-recently used) or {@code null} if absent. */
  public synchronized V get(K key) {
    return map.get(key);
  }

  public synchronized void put(K key, V value) {
    map.put(key, value);
  }

  public synchronized boolean containsKey(K key) {
    return map.containsKey(key);
  }

  public synchronized int size() {
    return map.size();
  }
}
