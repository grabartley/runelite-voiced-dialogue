package com.grahambartley.runelite.voiced.dialogue.speaker;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LearnedNpcStore {

  private static final long MISS_RETRY_MILLIS = TimeUnit.DAYS.toMillis(30);

  private final Path file;
  private final Gson gson;
  private final Map<Integer, NpcAttributes> learned = new ConcurrentHashMap<>();
  private final Map<Integer, Long> misses = new ConcurrentHashMap<>();

  public LearnedNpcStore(Path file, Gson gson) {
    this.file = file;
    this.gson = gson;
    load();
  }

  public boolean contains(int npcId) {
    return learned.containsKey(npcId);
  }

  public NpcAttributes get(int npcId) {
    NpcAttributes stored = learned.get(npcId);
    if (stored == null) {
      return null;
    }
    return learnedAttributes(npcId, stored.getRace(), stored.getGender(), stored.getEthnicity());
  }

  public synchronized void learn(int npcId, String race, String gender, String ethnicity) {
    learned.put(npcId, learnedAttributes(npcId, race, gender, ethnicity));
    misses.remove(npcId);
    persist();
  }

  public synchronized void missed(int npcId, long atMillis) {
    misses.put(npcId, atMillis);
    persist();
  }

  public boolean isPastMissWindow(int npcId, long nowMillis) {
    Long missedAt = misses.get(npcId);
    return missedAt == null || nowMillis - missedAt >= MISS_RETRY_MILLIS;
  }

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
    JsonObject root;
    try {
      root =
          new JsonParser()
              .parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
              .getAsJsonObject();
    } catch (Exception e) {
      log.debug("Could not read learned NPC store {}: {}", file, e.getMessage());
      return;
    }
    loadLearned(root);
    loadMisses(root);
  }

  private void loadLearned(JsonObject root) {
    Map<Integer, NpcAttributes> entries =
        NpcEntriesReader.read(
            root,
            AttributeSource.LEARNED,
            (key, e) ->
                log.debug("Skipping malformed learned NPC entry {}: {}", key, e.getMessage()));
    if (entries == null) {
      return;
    }
    learned.putAll(entries);
    log.info("Loaded {} learned NPC entries from {}", learned.size(), file);
  }

  private void loadMisses(JsonObject root) {
    if (!root.has("misses") || !root.get("misses").isJsonObject()) {
      return;
    }
    JsonObject stored = root.getAsJsonObject("misses");
    long now = System.currentTimeMillis();
    for (String key : stored.keySet()) {
      try {
        long missedAt = stored.get(key).getAsLong();
        if (now - missedAt < MISS_RETRY_MILLIS) {
          misses.put(Integer.valueOf(key), missedAt);
        }
      } catch (RuntimeException e) {
        log.debug("Skipping malformed learned NPC miss {}: {}", key, e.getMessage());
      }
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
      long now = System.currentTimeMillis();
      misses.values().removeIf(missedAt -> now - missedAt >= MISS_RETRY_MILLIS);
      JsonObject storedMisses = new JsonObject();
      for (Map.Entry<Integer, Long> e : misses.entrySet()) {
        storedMisses.addProperty(String.valueOf(e.getKey()), e.getValue());
      }
      JsonObject root = new JsonObject();
      root.add("npcs", npcs);
      root.add("misses", storedMisses);
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.write(tmp, gson.toJson(root).getBytes(StandardCharsets.UTF_8));
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException atomicUnsupported) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      log.debug("Could not write learned NPC store {}: {}", file, e.getMessage());
    }
  }
}
