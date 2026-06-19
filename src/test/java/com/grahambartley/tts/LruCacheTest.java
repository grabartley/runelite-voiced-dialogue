package com.grahambartley.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LruCacheTest {

  @Test
  public void evictsLeastRecentlyUsedBeyondCapacity() {
    LruCache<String, Integer> cache = new LruCache<>(2);
    cache.put("a", 1);
    cache.put("b", 2);
    cache.put("c", 3);

    assertEquals(2, cache.size());
    assertFalse("oldest entry should be evicted", cache.containsKey("a"));
    assertTrue(cache.containsKey("b"));
    assertTrue(cache.containsKey("c"));
  }

  @Test
  public void readingAnEntryMarksItMostRecentlyUsed() {
    LruCache<String, Integer> cache = new LruCache<>(2);
    cache.put("a", 1);
    cache.put("b", 2);

    // Touch "a" so "b" becomes the least-recently used.
    assertEquals(Integer.valueOf(1), cache.get("a"));
    cache.put("c", 3);

    assertTrue(cache.containsKey("a"));
    assertFalse("untouched entry should be evicted", cache.containsKey("b"));
    assertTrue(cache.containsKey("c"));
  }

  @Test
  public void putReplacesExistingValueWithoutGrowing() {
    LruCache<String, Integer> cache = new LruCache<>(2);
    cache.put("a", 1);
    cache.put("a", 2);

    assertEquals(1, cache.size());
    assertEquals(Integer.valueOf(2), cache.get("a"));
  }

  @Test
  public void missingKeyReturnsNull() {
    LruCache<String, Integer> cache = new LruCache<>(2);
    assertNull(cache.get("nope"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonPositiveCapacity() {
    new LruCache<String, Integer>(0);
  }
}
