package com.grahambartley.runelite.voiced.dialogue.speaker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * A small, writable on-disk cache of NPC race/gender/ethnicity the plugin has <em>learned</em> at
 * runtime (via the wiki fallback) for NPCs missing from the bundled table, e.g. NPCs added to the
 * game since the last plugin update. It is a peer of the bundled table: {@link
 * NpcDemographicAnalyzer} consults it after the baked-in resource and before the default, so a
 * once-learned NPC voices correctly for the rest of that session and every future one.
 *
 * <p>It lives outside the jar (the bundled resource is read-only) under the plugin's RuneLite
 * directory and is written through a temp file, moved atomically where the filesystem supports it,
 * so a crash mid-write cannot corrupt it. Reads and writes are thread-safe: lookups happen on the
 * dialogue pipeline thread while a wiki lookup may be writing on a background thread.
 */
@Slf4j
public final class LearnedNpcStore {

  private final Path file;
  private final Gson gson;
  private final Map<Integer, NpcAttributes> learned = new ConcurrentHashMap<>();

  public LearnedNpcStore(Path file, Gson gson) {
    this.file = file;
    this.gson = gson;
    load();
  }

  /** Whether an id has already been learned. */
  public boolean contains(int npcId) {
    return learned.containsKey(npcId);
  }

  /** A learned entry for an id, or {@code null}. The returned attributes are a fresh copy. */
  public NpcAttributes get(int npcId) {
    NpcAttributes stored = learned.get(npcId);
    if (stored == null) {
      return null;
    }
    return learnedAttributes(npcId, stored.getRace(), stored.getGender(), stored.getEthnicity());
  }

  /** Records race/gender/ethnicity for an id and persists the whole store. */
  public synchronized void learn(int npcId, String race, String gender, String ethnicity) {
    learned.put(npcId, learnedAttributes(npcId, race, gender, ethnicity));
    persist();
  }

  /** Number of learned entries (for logging and tests). */
  public int size() {
    return learned.size();
  }

  private static NpcAttributes learnedAttributes(
      int npcId, String race, String gender, String ethnicity) {
    NpcAttributes attributes = new NpcAttributes(race, gender, AttributeSource.LEARNED);
    attributes.setNpcId(npcId);
    attributes.setEthnicity(ethnicity);
    return attributes;
  }

  private void load() {
    if (file == null || !Files.exists(file)) {
      return;
    }
    try (Reader reader =
        new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
      Map<Integer, NpcAttributes> entries =
          NpcEntriesReader.read(
              reader,
              AttributeSource.LEARNED,
              (key, e) ->
                  log.debug("Skipping malformed learned NPC entry {}: {}", key, e.getMessage()));
      if (entries == null) {
        return;
      }
      learned.putAll(entries);
      log.info("Loaded {} learned NPC entries from {}", learned.size(), file);
    } catch (Exception e) {
      log.debug("Could not read learned NPC store {}: {}", file, e.getMessage());
    }
  }

  private void persist() {
    if (file == null) {
      return;
    }
    try {
      Files.createDirectories(file.getParent());
      JsonObject npcs = new JsonObject();
      for (Map.Entry<Integer, NpcAttributes> e : learned.entrySet()) {
        JsonObject entry = new JsonObject();
        entry.addProperty("race", e.getValue().getRace());
        entry.addProperty("gender", e.getValue().getGender());
        if (e.getValue().getEthnicity() != null) {
          entry.addProperty("ethnicity", e.getValue().getEthnicity());
        }
        npcs.add(String.valueOf(e.getKey()), entry);
      }
      JsonObject root = new JsonObject();
      root.add("npcs", npcs);
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.write(tmp, gson.toJson(root).getBytes(StandardCharsets.UTF_8));
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException atomicUnsupported) {
        // Some filesystems reject ATOMIC_MOVE; fall back to a plain replace, which is still far
        // better than writing the destination in place. Mirrors DiskAudioCache.
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      log.debug("Could not write learned NPC store {}: {}", file, e.getMessage());
    }
  }
}
