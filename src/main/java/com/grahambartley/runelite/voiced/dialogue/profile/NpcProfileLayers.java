package com.grahambartley.runelite.voiced.dialogue.profile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * The parsed {@code profiles} section: the complete default plus the sparse layers resolution
 * combines on top of it.
 */
@Value
@Accessors(fluent = true)
class NpcProfileLayers {

  /**
   * Last-resort British default used only when the bundled {@code profiles.default} is missing or
   * incomplete, so resolution never returns {@code null} or an NPE even on a malformed resource.
   */
  static final CharacterProfile BUILTIN_DEFAULT =
      new CharacterProfile(
          "Gielinor Commoner",
          "British English, Received Pronunciation, as heard in southern England.",
          "A grounded medieval fantasy townsperson; plain, sincere, and natural.",
          "Steady and conversational.");

  /** What a table that never loaded resolves against: the built-in default and nothing else. */
  static final NpcProfileLayers EMPTY =
      new NpcProfileLayers(
          BUILTIN_DEFAULT,
          null,
          Collections.emptyMap(),
          Collections.emptyMap(),
          Collections.emptyList(),
          Collections.emptyMap());

  /**
   * A sparse profile layer: any field may be {@code null}, meaning "inherit from the layer below".
   */
  @Value
  @Accessors(fluent = true)
  static class Layer {
    String name;
    String accent;
    String style;
    String pace;
  }

  /**
   * An ordered keyword rule: the layer applies when any keyword word-matches the display name. A
   * rule carrying {@code "lifeStage": "child"} additionally marks matching NPCs as children.
   */
  @Value
  @Accessors(fluent = true)
  static class CategoryRule {
    String id;
    List<String> keywords;
    Layer layer;
    boolean child;
  }

  CharacterProfile defaultProfile;
  Layer playerLayer;
  Map<String, Layer> byRace;
  Map<String, Layer> byEthnicity;
  List<CategoryRule> byCategory;
  Map<Integer, Layer> byId;
}
