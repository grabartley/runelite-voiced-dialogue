package com.grahambartley.runelite.voiced.dialogue.profile;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class CharacterProfile {

  private final String name;
  private final String accent;
  private final String accentDetail;
  private final String style;
  private final String pace;
  private final String pitch;
  private final String voiceRegion;
  private final boolean accentOverridden;

  public CharacterProfile(String name, String accent, String style, String pace) {
    this(name, accent, null, style, pace, null, null);
  }

  public CharacterProfile(
      String name,
      String accent,
      String accentDetail,
      String style,
      String pace,
      String pitch,
      String voiceRegion) {
    this(name, accent, accentDetail, style, pace, pitch, voiceRegion, false);
  }

  public CharacterProfile(
      String name,
      String accent,
      String accentDetail,
      String style,
      String pace,
      String pitch,
      String voiceRegion,
      boolean accentOverridden) {
    this.name = stripTrailingOrNull(name);
    this.accent = stripTrailingOrNull(accent);
    this.accentDetail = stripTrailingOrNull(accentDetail);
    this.style = stripTrailingOrNull(style);
    this.pace = stripTrailingOrNull(pace);
    this.pitch = stripTrailingOrNull(pitch);
    this.voiceRegion = voiceRegion;
    this.accentOverridden = accentOverridden;
  }

  private static String stripTrailingOrNull(String field) {
    return field == null ? null : field.stripTrailing();
  }

  public String cacheKey() {
    String joined = name + '' + accent + '' + style + '' + pace;
    if (pitch != null) {
      joined += '\u0001' + pitch;
    }
    if (accentDetail != null) {
      joined += '\u0002' + accentDetail;
    }
    return Integer.toHexString(joined.hashCode());
  }
}
