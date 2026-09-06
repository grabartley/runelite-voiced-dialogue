package com.grahambartley.runelite.voiced.dialogue.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** The runtime learned-NPC cache: in-memory lookup plus atomic persistence across instances. */
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
    assertTrue("a learned id is present", store.contains(123));
    assertFalse("an unlearned id is absent", store.contains(999));
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
