package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileLayers.CategoryRule;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileLayers.Layer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NpcProfileTable {

  private static final String TABLE_RESOURCE = "/npc-voices.json";

  @Value
  @Accessors(fluent = true)
  public static class Resolution {
    CharacterProfile profile;
    String source;
  }

  public static final class NameMatch {

    static final NameMatch NONE = new NameMatch(Collections.emptyList());

    private final List<CategoryRule> rules;

    NameMatch(List<CategoryRule> rules) {
      this.rules = rules;
    }

    public boolean child() {
      for (CategoryRule rule : rules) {
        if (rule.child()) {
          return true;
        }
      }
      return false;
    }
  }

  private static final class MatchedLayer {
    private final Layer layer;
    private final String source;

    MatchedLayer(Layer layer, String source) {
      this.layer = layer;
      this.source = source;
    }
  }

  private final DirectionSanitizer directionSanitizer;

  private NpcProfileLayers layers = NpcProfileLayers.EMPTY;
  private boolean loaded = false;

  public NpcProfileTable() {
    this(new DirectionSanitizer(new ProfanityFilter()));
  }

  public NpcProfileTable(DirectionSanitizer directionSanitizer) {
    this.directionSanitizer = directionSanitizer;
  }

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

  static NpcProfileTable fromProfilesJson(JsonObject profiles) {
    NpcProfileTable table = new NpcProfileTable();
    table.layers = NpcProfileParser.parse(profiles);
    table.loaded = true;
    return table;
  }

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

  public Resolution resolveNpc(Integer npcId, NameMatch nameMatch, String race, String ethnicity) {
    return mergeLayers(collectLayers(npcId, nameMatch, race, ethnicity));
  }

  private List<MatchedLayer> collectLayers(
      Integer npcId, NameMatch nameMatch, String race, String ethnicity) {
    List<MatchedLayer> matched = new ArrayList<>();

    Layer raceLayer = race == null ? null : layers.byRace().get(race.toLowerCase(Locale.ROOT));
    if (raceLayer != null) {
      matched.add(new MatchedLayer(raceLayer, "race:" + race));
    }
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

  public CharacterProfile resolvePlayer(String accent, String style, String pace) {
    CharacterProfile base = apply(layers.defaultProfile(), layers.playerLayer());
    return new CharacterProfile(
        base.name(),
        sanitizedOr(accent, base.accent()),
        sanitizedOr(style, base.style()),
        sanitizedOr(pace, base.pace()));
  }

  public CharacterProfile resolveNarrator() {
    return apply(layers.defaultProfile(), layers.narratorLayer());
  }

  private String sanitizedOr(String configured, String fallback) {
    if (isBlank(configured)) {
      return fallback;
    }
    String sanitized = directionSanitizer.sanitize(configured);
    return isBlank(sanitized) ? fallback : sanitized;
  }

  public boolean isLoaded() {
    return loaded;
  }

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
