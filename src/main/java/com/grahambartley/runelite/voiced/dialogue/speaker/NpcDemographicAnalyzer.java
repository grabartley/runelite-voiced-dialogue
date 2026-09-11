package com.grahambartley.runelite.voiced.dialogue.speaker;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;

@Slf4j
public class NpcDemographicAnalyzer {

  private static final String TABLE_RESOURCE = "/npc-voices.json";
  private static final String DEFAULT_RACE = "Unknown";
  private static final String DEFAULT_GENDER = "Male";

  private static final Pattern FEMALE_NAME_HINT =
      Pattern.compile(
          "\\b(woman|women|girl|lady|queen|princess|duchess|countess|baroness|empress|witch"
              + "|sorceress|priestess|huntress|banshee|hag|crone|mother|sister|nun|maiden|barmaid"
              + "|waitress|seamstress|goddess|mistress|madam|damsel|wife|maid)\\b",
          Pattern.CASE_INSENSITIVE);

  private Map<Integer, NpcAttributes> voiceTable = Collections.emptyMap();

  private LearnedNpcStore learnedStore;

  public void initialize() {
    voiceTable = loadVoiceTable();
    log.info("NPC voice table loaded with {} entries from {}", voiceTable.size(), TABLE_RESOURCE);
  }

  public void setLearnedStore(LearnedNpcStore learnedStore) {
    this.learnedStore = learnedStore;
  }

  public NpcAttributes analyzeNPC(NPC npc) {
    if (npc == null) {
      return null;
    }
    NPCComposition composition = npc.getComposition();
    if (composition == null) {
      return null;
    }
    int activeId = npc.getId();
    int baseId = composition.getId();
    NpcAttributes known = lookupKnown(activeId);
    if (known == null && baseId != activeId) {
      known = lookupKnown(baseId);
    }
    return known != null ? known : defaultAttributes(activeId, composition.getName());
  }

  private NpcAttributes lookupKnown(int npcId) {
    NpcAttributes attributes = voiceTable.get(npcId);
    if (attributes != null) {
      return attributes;
    }
    return learnedStore != null ? learnedStore.get(npcId) : null;
  }

  public int getTableSize() {
    return voiceTable.size();
  }

  private NpcAttributes defaultAttributes(int npcId, String npcName) {
    String gender =
        npcName != null && FEMALE_NAME_HINT.matcher(npcName).find() ? "Female" : DEFAULT_GENDER;
    NpcAttributes defaultData = new NpcAttributes(DEFAULT_RACE, gender, AttributeSource.DEFAULT);
    defaultData.setNpcId(npcId);
    return defaultData;
  }

  private Map<Integer, NpcAttributes> loadVoiceTable() {
    try (InputStream stream = getClass().getResourceAsStream(TABLE_RESOURCE)) {
      if (stream == null) {
        log.warn(
            "NPC voice table {} not found - every NPC will use the default voice", TABLE_RESOURCE);
        return Collections.emptyMap();
      }

      Map<Integer, NpcAttributes> table =
          NpcEntriesReader.read(
              new InputStreamReader(stream, StandardCharsets.UTF_8),
              AttributeSource.STATIC_TABLE,
              (key, e) ->
                  log.warn("Skipping malformed NPC voice entry {}: {}", key, e.getMessage()));
      if (table == null) {
        log.warn("NPC voice table {} has no 'npcs' object - using default voice", TABLE_RESOURCE);
        return Collections.emptyMap();
      }
      return Collections.unmodifiableMap(table);
    } catch (Exception e) {
      log.error("Failed to load NPC voice table {}: {}", TABLE_RESOURCE, e.getMessage());
      return Collections.emptyMap();
    }
  }
}
