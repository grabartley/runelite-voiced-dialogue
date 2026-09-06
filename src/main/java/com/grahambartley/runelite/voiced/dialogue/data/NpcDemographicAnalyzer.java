package com.grahambartley.runelite.voiced.dialogue.data;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;

/**
 * Resolves an NPC's race/gender from a static, precomputed lookup table baked into the plugin as a
 * bundled resource ({@code /npc-voices.json}).
 *
 * <p>At runtime this is a single in-memory map lookup keyed by NPC id: no network requests, no
 * large downloads, no model-id guessing. The table is produced offline by {@code
 * tools/generate_npc_voices.py}; see the README for how to regenerate and expand it.
 *
 * <p>Ids missing from the table resolve to race {@code Unknown}, which voices with the single
 * default voice. A last-resort name check still reports a best-guess gender for such entries (an
 * explicit female title or word in the name reads as female, everything else male) so the attribute
 * is populated, but it does not change that default voice. This is the lone runtime name check and
 * never affects race or table-backed NPCs.
 */
@Slf4j
public class NpcDemographicAnalyzer {

  private static final String TABLE_RESOURCE = "/npc-voices.json";
  private static final String DEFAULT_RACE = "Unknown";
  private static final String DEFAULT_GENDER = "Male";

  /**
   * Explicit female titles/words used only as a best-guess gender for NPCs missing from the table.
   * Word-aware so it can't match inside larger words. Mirrors the offline generator's female
   * keywords; keep the two in sync.
   */
  private static final Pattern FEMALE_NAME_HINT =
      Pattern.compile(
          "\\b(woman|women|girl|lady|queen|princess|duchess|countess|baroness|empress|witch"
              + "|sorceress|priestess|huntress|banshee|hag|crone|mother|sister|nun|maiden|barmaid"
              + "|waitress|seamstress|goddess|mistress|madam|damsel|wife|maid)\\b",
          Pattern.CASE_INSENSITIVE);

  /** Immutable npcId -> {race, gender} table loaded once from the bundled resource. */
  private Map<Integer, NpcAttributes> voiceTable = Collections.emptyMap();

  /**
   * Optional writable cache of NPCs learned at runtime via the wiki fallback, consulted after the
   * bundled table and before the default so a once-learned NPC voices correctly thereafter.
   */
  private LearnedNpcStore learnedStore;

  /** Loads the static lookup table from the bundled resource. */
  public void initialize() {
    voiceTable = loadVoiceTable();
    log.info("NPC voice table loaded with {} entries from {}", voiceTable.size(), TABLE_RESOURCE);
  }

  /** Wires in the runtime learned-NPC cache (the wiki fallback). */
  public void setLearnedStore(LearnedNpcStore learnedStore) {
    this.learnedStore = learnedStore;
  }

  /**
   * Resolves race/gender for an NPC by a single lookup in the static table, keyed by NPC id.
   * Returns a deterministic default for ids missing from the table; returns {@code null} only when
   * the NPC (or its composition) is itself null.
   */
  public NpcAttributes analyzeNPC(NPC npc) {
    if (npc == null) {
      return null;
    }
    NPCComposition composition = npc.getComposition();
    if (composition == null) {
      return null;
    }
    // Prefer the NPC's active id, which matches the wiki/cache ids the table is keyed by. A
    // transformed multiloc NPC (many Karamja, quest and morphing NPCs) reports a different
    // composition (base) id than its active id, and only the active id is in the table; falling
    // back to the base id keeps the simple, non-transformed NPCs working unchanged.
    int activeId = npc.getId();
    int baseId = composition.getId();
    NpcAttributes known = lookupKnown(activeId);
    if (known == null && baseId != activeId) {
      known = lookupKnown(baseId);
    }
    return known != null ? known : defaultAttributes(activeId, composition.getName());
  }

  /** A bundled-table or learned hit for an id, or {@code null} when neither knows it. */
  private NpcAttributes lookupKnown(int npcId) {
    NpcAttributes attributes = voiceTable.get(npcId);
    if (attributes != null) {
      return attributes;
    }
    return learnedStore != null ? learnedStore.get(npcId) : null;
  }

  /** Number of entries in the loaded table (for logging and tests). */
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
