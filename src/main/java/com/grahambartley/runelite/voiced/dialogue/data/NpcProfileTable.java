package com.grahambartley.runelite.voiced.dialogue.data;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.data.NpcProfileLayers.CategoryRule;
import com.grahambartley.runelite.voiced.dialogue.data.NpcProfileLayers.Layer;
import com.grahambartley.runelite.voiced.dialogue.synthesis.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.synthesis.DirectionSanitizer;
import com.grahambartley.runelite.voiced.dialogue.synthesis.ProfanityFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the character voice profiles bundled in {@code /npc-voices.json} under the top-level
 * {@code profiles} key (produced offline by {@code tools/generate_npc_voices.py} from {@code
 * tools/profiles.json}), which {@link NpcProfileParser} loads.
 *
 * <p>Resolution <em>combines</em> every matching layer: {@code default} (always complete), {@code
 * byRace[race]}, {@code byEthnicity[ethnicity]}, <em>every</em> {@code byCategory} entry whose
 * keyword word-matches the display name, and {@code byId[npcId]}. An NPC can be several things at
 * once (a Fremennik human, a ghost pirate), so all matches contribute: {@code style} accumulates
 * across the layers, while {@code name}, {@code accent}, and {@code pace} take the most specific
 * layer that sets them. A bespoke per-NPC entry therefore need only carry what is unique (usually a
 * {@code name} and a {@code style}); the rest falls through to the category, race, or British
 * default. Every NPC resolves to a complete {@link CharacterProfile}, whether or not it has a
 * bespoke entry.
 *
 * <p>The player is resolved separately: the {@code player} layer over the default, with the three
 * configured player fields (accent/style/pace) overriding when non-blank.
 */
@Slf4j
public final class NpcProfileTable {

  private static final String TABLE_RESOURCE = "/npc-voices.json";

  /** The resolved profile plus the layer that won, for debug logging. */
  @Value
  @Accessors(fluent = true)
  public static class Resolution {
    CharacterProfile profile;
    String source;
  }

  /**
   * The keyword categories one display name matches, resolved once per line and read by both the
   * voice (life stage) and the profile (layers).
   */
  public static final class NameMatch {

    static final NameMatch NONE = new NameMatch(Collections.emptyList());

    private final List<CategoryRule> rules;

    NameMatch(List<CategoryRule> rules) {
      this.rules = rules;
    }

    /**
     * Whether the name matches a child keyword category ({@code "lifeStage": "child"}), so
     * generically named children (Child, Schoolboy, Street urchin, ...) voice from the youthful
     * sub-pool without a per-id table entry.
     */
    public boolean child() {
      for (CategoryRule rule : rules) {
        if (rule.child()) {
          return true;
        }
      }
      return false;
    }
  }

  /** One contributing layer and the label naming it in the debug trace. */
  private static final class MatchedLayer {
    private final Layer layer;
    private final String source;

    MatchedLayer(Layer layer, String source) {
      this.layer = layer;
      this.source = source;
    }
  }

  /**
   * Neutralizes the three free-text player direction fields (injection break-out + profanity)
   * before they are baked into the player {@link CharacterProfile}. Unconditional, no toggle.
   */
  private final DirectionSanitizer directionSanitizer;

  private NpcProfileLayers layers = NpcProfileLayers.EMPTY;
  private boolean loaded = false;

  public NpcProfileTable() {
    this(new DirectionSanitizer(new ProfanityFilter()));
  }

  public NpcProfileTable(DirectionSanitizer directionSanitizer) {
    this.directionSanitizer = directionSanitizer;
  }

  /** Loads the {@code profiles} section from the bundled resource. */
  public void initialize() {
    NpcProfileLayers parsed = NpcProfileParser.loadResource(TABLE_RESOURCE);
    if (parsed == null) {
      return;
    }
    this.layers = parsed;
    this.loaded = true;
    log.info(
        "NPC profiles loaded: {} race, {} ethnicity, {} keyword categories, {} bespoke NPC"
            + " overrides",
        layers.byRace().size(),
        layers.byEthnicity().size(),
        layers.byCategory().size(),
        layers.byId().size());
  }

  /** Test seam: build a table directly from a parsed {@code profiles} object. */
  static NpcProfileTable fromProfilesJson(JsonObject profiles) {
    NpcProfileTable table = new NpcProfileTable();
    table.layers = NpcProfileParser.parse(profiles);
    table.loaded = true;
    return table;
  }

  /** Every category whose keyword is in the display name, in declaration order (may be empty). */
  public NameMatch matchName(String npcName) {
    if (npcName == null || npcName.isEmpty()) {
      return NameMatch.NONE;
    }
    String lower = npcName.toLowerCase(Locale.ROOT);
    List<CategoryRule> matches = new ArrayList<>();
    for (CategoryRule rule : layers.byCategory()) {
      for (String keyword : rule.keywords()) {
        if (wordContains(lower, keyword)) {
          matches.add(rule);
          break;
        }
      }
    }
    return new NameMatch(matches);
  }

  /**
   * Resolves the complete profile for an NPC by <em>combining</em> every matching layer: the race
   * bucket, the ethnicity accent, every keyword category the display name matched, and the per-NPC
   * override. An NPC can be more than one thing at once (a Fremennik human, a ghost pirate), so all
   * matches contribute. {@code style} accumulates across every contributing layer so the persona
   * blends; {@code name}, {@code accent}, and {@code pace} are single-valued, so the most specific
   * layer that sets each one wins (per-NPC override, then the last matching category, then race,
   * then the default), which keeps a coherent accent and pace rather than stacking contradictory
   * directions. Never returns {@code null}.
   *
   * @param npcId the live NPC id, or {@code null} when unknown (no bespoke override is applied)
   * @param nameMatch the categories the display name matched, from {@link #matchName(String)}
   * @param race the resolved race bucket (e.g. {@code "Troll"}), may be {@code null}
   * @param ethnicity the NPC's ethnicity accent key (e.g. {@code "kharidian"}), may be {@code null}
   */
  public Resolution resolveNpc(Integer npcId, NameMatch nameMatch, String race, String ethnicity) {
    return mergeLayers(collectLayers(npcId, nameMatch, race, ethnicity));
  }

  /** The contributing layers, least specific first, so a later layer wins single-valued fields. */
  private List<MatchedLayer> collectLayers(
      Integer npcId, NameMatch nameMatch, String race, String ethnicity) {
    List<MatchedLayer> matched = new ArrayList<>();

    Layer raceLayer = race == null ? null : layers.byRace().get(race.toLowerCase(Locale.ROOT));
    if (raceLayer != null) {
      matched.add(new MatchedLayer(raceLayer, "race:" + race));
    }
    // Ethnicity tints only the plain folk (Human / unknown race); a distinctive race keeps its own
    // accent wherever it is found, so a dwarf stays gruff and Scottish even in the desert.
    boolean plainRace =
        race == null || race.equalsIgnoreCase("Human") || race.equalsIgnoreCase("Unknown");
    Layer ethnicityLayer =
        (ethnicity == null || !plainRace)
            ? null
            : layers.byEthnicity().get(ethnicity.toLowerCase(Locale.ROOT));
    if (ethnicityLayer != null) {
      matched.add(new MatchedLayer(ethnicityLayer, "ethnicity:" + ethnicity));
    }
    for (CategoryRule rule : nameMatch.rules) {
      matched.add(new MatchedLayer(rule.layer(), "keyword:" + rule.id()));
    }
    Layer idLayer = npcId == null ? null : layers.byId().get(npcId);
    if (idLayer != null) {
      matched.add(new MatchedLayer(idLayer, "id:" + npcId));
    }
    return matched;
  }

  private Resolution mergeLayers(List<MatchedLayer> matched) {
    CharacterProfile defaultProfile = layers.defaultProfile();
    String name = defaultProfile.name();
    String accent = defaultProfile.accent();
    String pace = defaultProfile.pace();
    List<String> styleParts = new ArrayList<>();
    List<String> sources = new ArrayList<>();
    for (MatchedLayer entry : matched) {
      Layer layer = entry.layer;
      if (layer.name() != null) {
        name = layer.name();
      }
      if (layer.accent() != null) {
        accent = layer.accent();
      }
      if (layer.pace() != null) {
        pace = layer.pace();
      }
      if (layer.style() != null) {
        styleParts.add(layer.style());
      }
      sources.add(entry.source);
    }
    String style = styleParts.isEmpty() ? defaultProfile.style() : String.join(" ", styleParts);
    String source = sources.isEmpty() ? "default" : String.join("+", sources);

    return new Resolution(new CharacterProfile(name, accent, style, pace), source);
  }

  /**
   * Resolves the player's profile: the {@code player} layer over the default, then the three
   * configured fields overriding when non-blank. Each configured field is run through {@link
   * DirectionSanitizer} first, so newline/marker injection and profanity can never reach the
   * prompt; a field that sanitizes down to blank falls back to the layer default rather than
   * emitting an empty direction. The player's name label is never overridden by config.
   */
  public CharacterProfile resolvePlayer(String accent, String style, String pace) {
    CharacterProfile base = apply(layers.defaultProfile(), layers.playerLayer());
    return new CharacterProfile(
        base.name(),
        sanitizedOr(accent, base.accent()),
        sanitizedOr(style, base.style()),
        sanitizedOr(pace, base.pace()));
  }

  private String sanitizedOr(String configured, String fallback) {
    if (isBlank(configured)) {
      return fallback;
    }
    String sanitized = directionSanitizer.sanitize(configured);
    return isBlank(sanitized) ? fallback : sanitized;
  }

  /** Whether the bundled {@code profiles} section loaded successfully. */
  public boolean isLoaded() {
    return loaded;
  }

  /**
   * Whether {@code haystack} contains {@code needle} bounded by non-letters on both sides, so
   * {@code "imp"} matches "Imp" and "Imp Catcher" but not "important", and a hyphen counts as a
   * boundary. Both arguments are expected already lower-cased.
   */
  static boolean wordContains(String haystack, String needle) {
    if (needle.isEmpty()) {
      return false;
    }
    int idx = haystack.indexOf(needle);
    while (idx >= 0) {
      boolean leftOk = idx == 0 || !Character.isLetter(haystack.charAt(idx - 1));
      int end = idx + needle.length();
      boolean rightOk = end == haystack.length() || !Character.isLetter(haystack.charAt(end));
      if (leftOk && rightOk) {
        return true;
      }
      idx = haystack.indexOf(needle, idx + 1);
    }
    return false;
  }

  private static CharacterProfile apply(CharacterProfile base, Layer layer) {
    if (layer == null) {
      return base;
    }
    return new CharacterProfile(
        layer.name() != null ? layer.name() : base.name(),
        layer.accent() != null ? layer.accent() : base.accent(),
        layer.style() != null ? layer.style() : base.style(),
        layer.pace() != null ? layer.pace() : base.pace());
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
