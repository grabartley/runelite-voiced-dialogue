package com.grahambartley.runelite.voiced.dialogue.speaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class LearnedNpcStoreTest {

  private final Gson gson = new Gson();

  @Test
  public void learnsAndReadsBackWithEthnicity() throws Exception {
    Path file = Files.createTempDirectory("learned").resolve("learned-npcs.json");
    LearnedNpcStore store = new LearnedNpcStore(file, gson);

    store.learn(123, "Dwarf", "Female", "kharidian");

    NpcAttributes a = store.get(123);
    assertEquals("Dwarf", a.getRace());
    assertEquals("Female", a.getGender());
    assertEquals("kharidian", a.getEthnicity());
    assertEquals(AttributeSource.LEARNED, a.getSource());
    assertEquals(123, a.getNpcId());
    assertNull("an unlearned id is absent", store.get(999));
  }

  @Test
  public void persistsAcrossInstances() throws Exception {
    Path file = Files.createTempDirectory("learned").resolve("learned-npcs.json");
    new LearnedNpcStore(file, gson).learn(456, "Human", "Male", null);

    LearnedNpcStore reloaded = new LearnedNpcStore(file, gson);
    NpcAttributes a = reloaded.get(456);
    assertEquals("Human", a.getRace());
    assertEquals("Male", a.getGender());
    assertNull("a null ethnicity is not persisted", a.getEthnicity());
    assertEquals(1, reloaded.size());
  }

  @Test
  public void aMissIsRememberedUntilItsRetryWindowPasses() throws Exception {
    Path file = Files.createTempDirectory("learned").resolve("learned-npcs.json");
    LearnedNpcStore store = new LearnedNpcStore(file, gson);
    long now = System.currentTimeMillis();

    assertTrue("an unseen id is worth looking up", store.isWorthLooking(11, now));

    store.missed(11, now);
    assertFalse("a fresh miss is not repeated", store.isWorthLooking(11, now));
    assertFalse(
        "a fresh miss survives a restart", new LearnedNpcStore(file, gson).isWorthLooking(11, now));
    assertTrue(
        "a stale miss is retried", store.isWorthLooking(11, now + TimeUnit.DAYS.toMillis(31)));
  }

  @Test
  public void learningAnIdClearsItsMiss() throws Exception {
    Path file = Files.createTempDirectory("learned").resolve("learned-npcs.json");
    LearnedNpcStore store = new LearnedNpcStore(file, gson);
    long now = System.currentTimeMillis();

    store.missed(22, now);
    store.learn(22, "Human", "Female", "kandarin");

    assertFalse("a learned id is never looked up again", store.isWorthLooking(22, now));
    LearnedNpcStore reloaded = new LearnedNpcStore(file, gson);
    assertEquals("Female", reloaded.get(22).getGender());
    assertFalse(reloaded.isWorthLooking(22, now + TimeUnit.DAYS.toMillis(365)));
  }

  @Test
  public void missingFileStartsEmpty() throws Exception {
    Path file = Files.createTempDirectory("learned").resolve("does-not-exist.json");
    assertEquals(0, new LearnedNpcStore(file, gson).size());
  }

  @Test
  public void repeatedWritesReplaceTheStoreCleanlyAndLeaveNoTempFile() throws Exception {
    Path dir = Files.createTempDirectory("learned");
    Path file = dir.resolve("learned-npcs.json");
    LearnedNpcStore store = new LearnedNpcStore(file, gson);

    store.learn(1, "Human", "Male", null);
    store.learn(2, "Dwarf", "Female", null);
    store.learn(3, "Gnome", "Male", null);

    assertEquals(3, new LearnedNpcStore(file, gson).size());
    assertEquals(
        "the temp file is consumed by the move, never left behind",
        0,
        Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".tmp")).count());
  }
}
