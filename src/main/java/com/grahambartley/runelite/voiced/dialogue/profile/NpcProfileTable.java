package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.JsonObject;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileLayers.CategoryRule;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileLayers.Layer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class NpcProfileTable {

  private static final String TABLE_RESOURCE = "/npc-voices.json";

  static final String MALE_VOICING = "The speaker is a man, and sounds like one.";

  static final String FEMALE_VOICING = "The speaker is a woman, and sounds like one.";

  private static final Pattern ENDS_SENTENCE = Pattern.compile("[.!?]$");

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
            + " overrides, {} cache symbols",
        layers.byRace().size(),
        layers.byEthnicity().size(),
        layers.byCategory().size(),
        layers.byId().size(),
        layers.symbols().size());
  }

  static NpcProfileTable fromProfilesJson(JsonObject profiles) {
    NpcProfileTable table = new NpcProfileTable();
    table.layers = NpcProfileParser.parse(profiles);
    table.loaded = true;
    return table;
  }

  public boolean hasBespokeProfile(int npcId) {
    return layers.byId().containsKey(npcId);
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

  public Resolution resolveNpc(
      Integer npcId,
      NameMatch nameMatch,
      String race,
      String ethnicity,
      boolean child,
      NpcVoiceOverride override) {
    return mergeLayers(collectLayers(npcId, nameMatch, race, ethnicity, child), override);
  }

  private List<MatchedLayer> collectLayers(
      Integer npcId, NameMatch nameMatch, String race, String ethnicity, boolean child) {
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
    if (child && !nameMatch.child()) {
      for (CategoryRule rule : layers.byCategory()) {
        if (rule.child()) {
          matched.add(new MatchedLayer(rule.layer(), "lifeStage:" + rule.id()));
        }
      }
    }
    Layer idLayer = npcId == null ? null : layers.byId().get(npcId);
    if (idLayer != null) {
      matched.add(new MatchedLayer(idLayer, "id:" + npcId));
    }
    return matched;
  }

  private Resolution mergeLayers(List<MatchedLayer> matched, NpcVoiceOverride override) {
    CharacterProfile defaultProfile = layers.defaultProfile();
    String name = defaultProfile.name();
    String accent = defaultProfile.accent();
    String accentDetail = defaultProfile.accentDetail();
    String voiceRegion = defaultProfile.voiceRegion();
    String pace = defaultProfile.pace();
    String pitch = defaultProfile.pitch();
    Integer age = defaultProfile.age();
    List<String> styleParts = new ArrayList<>();
    boolean accentOverridden = false;
    List<String> sources = new ArrayList<>();
    for (MatchedLayer entry : matched) {
      Layer layer = entry.layer;
      if (layer.name() != null) {
        name = layer.name();
      }
      if (layer.accent() != null) {
        accent = layer.accent();
        accentDetail = layer.accentDetail();
        voiceRegion = layer.voiceRegion();
      }
      if (layer.pace() != null) {
        pace = layer.pace();
      }
      if (layer.pitch() != null) {
        pitch = layer.pitch();
      }
      if (layer.age() != null) {
        age = layer.age();
      }
      if (layer.style() != null) {
        if (layer.replaceStyle()) {
          styleParts.clear();
        }
        styleParts.add(asSentence(layer.style()));
      }
      sources.add(entry.source);
    }
    if (override != null && override.hasProfileFields()) {
      if (override.name() != null) {
        name = override.name();
      }
      if (override.accent() != null) {
        accent = override.accent();
        accentDetail = null;
        accentOverridden = true;
      }
      if (override.style() != null) {
        styleParts.clear();
        styleParts.add(asSentence(override.style()));
      }
      if (override.pace() != null) {
        pace = override.pace();
      }
      sources.add("override");
    }
    String style = styleParts.isEmpty() ? defaultProfile.style() : String.join(" ", styleParts);
    String source = sources.isEmpty() ? "default" : String.join("+", sources);

    return new Resolution(
        new CharacterProfile(
            name, accent, accentDetail, style, pace, pitch, voiceRegion, accentOverridden, age),
        source);
  }

  private static String asSentence(String style) {
    String trimmed = style.trim();
    return ENDS_SENTENCE.matcher(trimmed).find() ? trimmed : trimmed + ".";
  }

  public CharacterProfile resolvePlayer(String accent, String style, String pace) {
    return configured(layers.playerLayer(), accent, style, pace);
  }

  public CharacterProfile resolveFollower(
      String accent, String style, String pace, NpcGender gender) {
    CharacterProfile base = configured(layers.followerLayer(), accent, style, pace);
    return new CharacterProfile(
        base.name(), base.accent(), base.style() + " " + voicingFor(gender), base.pace());
  }

  static String voicingFor(NpcGender gender) {
    return NpcGender.orDefault(gender) == NpcGender.FEMALE ? FEMALE_VOICING : MALE_VOICING;
  }

  private CharacterProfile configured(Layer layer, String accent, String style, String pace) {
    CharacterProfile base = apply(layers.defaultProfile(), layer);
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

  public NpcVoiceCatalog buildCatalog() {
    return NpcVoiceCatalog.from(layers);
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
    boolean ownAccent = layer.accent() != null;
    return new CharacterProfile(
        layer.name() != null ? layer.name() : base.name(),
        ownAccent ? layer.accent() : base.accent(),
        ownAccent ? layer.accentDetail() : base.accentDetail(),
        layer.style() != null ? layer.style() : base.style(),
        layer.pace() != null ? layer.pace() : base.pace(),
        null,
        null);
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
