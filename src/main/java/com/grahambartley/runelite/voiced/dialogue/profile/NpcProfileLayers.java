package com.grahambartley.runelite.voiced.dialogue.profile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
class NpcProfileLayers {

  static final CharacterProfile BUILTIN_DEFAULT =
      new CharacterProfile(
          "Gielinor Commoner",
          "British English, Received Pronunciation, as heard in southern England.",
          "A grounded medieval fantasy townsperson; plain, sincere, and natural.",
          "Steady and conversational.");

  static final NpcProfileLayers EMPTY =
      new NpcProfileLayers(
          BUILTIN_DEFAULT,
          null,
          null,
          Collections.emptyMap(),
          Collections.emptyMap(),
          Collections.emptyList(),
          Collections.emptyMap());

  @Value
  @Accessors(fluent = true)
  static class Layer {
    String name;
    String accent;
    String style;
    String pace;
  }

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
  Layer narratorLayer;
  Map<String, Layer> byRace;
  Map<String, Layer> byEthnicity;
  List<CategoryRule> byCategory;
  Map<Integer, Layer> byId;
}
