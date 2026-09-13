package com.grahambartley.runelite.voiced.dialogue.speaker.wiki;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcAttributes;

public final class WikiLookup {

  private static final WikiLookup UNDOCUMENTED = new WikiLookup(null, true);
  private static final WikiLookup UNREACHABLE = new WikiLookup(null, false);

  private final NpcAttributes attributes;
  private final boolean answered;

  private WikiLookup(NpcAttributes attributes, boolean answered) {
    this.attributes = attributes;
    this.answered = answered;
  }

  static WikiLookup of(NpcAttributes attributes) {
    return new WikiLookup(attributes, true);
  }

  static WikiLookup undocumented() {
    return UNDOCUMENTED;
  }

  static WikiLookup unreachable() {
    return UNREACHABLE;
  }

  public NpcAttributes attributes() {
    return attributes;
  }

  public boolean isUndocumented() {
    return answered && attributes == null;
  }

  public boolean isUnreachable() {
    return !answered;
  }
}
