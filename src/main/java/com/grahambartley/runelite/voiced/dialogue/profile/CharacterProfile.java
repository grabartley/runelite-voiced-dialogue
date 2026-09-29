package com.grahambartley.runelite.voiced.dialogue.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
public final class CharacterProfile {

  private static final int CACHE_KEY_BYTES = 8;

  private static final Pattern TRAILING_SEPARATORS = Pattern.compile("[\\s.;,:]+$");

  private final String name;
  private final String accent;
  private final String accentDetail;
  private final String style;
  private final String pace;
  private final String pitch;
  private final String voiceRegion;
  private final boolean accentOverridden;
  private final Integer age;

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
    this(name, accent, accentDetail, style, pace, pitch, voiceRegion, accentOverridden, null);
  }

  public CharacterProfile(
      String name,
      String accent,
      String accentDetail,
      String style,
      String pace,
      String pitch,
      String voiceRegion,
      boolean accentOverridden,
      Integer age) {
    this.name = stripTrailingOrNull(name);
    this.accent = stripTrailingOrNull(accent);
    this.accentDetail = stripTrailingOrNull(accentDetail);
    this.style = stripTrailingOrNull(style);
    this.pace = stripTrailingOrNull(pace);
    this.pitch = stripTrailingOrNull(pitch);
    this.voiceRegion = voiceRegion;
    this.accentOverridden = accentOverridden;
    this.age = age;
  }

  private static String stripTrailingOrNull(String field) {
    return field == null ? null : field.stripTrailing();
  }

  public static String spoken(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = TRAILING_SEPARATORS.matcher(value.trim()).replaceAll("");
    return trimmed.isEmpty() ? null : trimmed;
  }

  public String spokenAccentDetail() {
    return spoken(accent) == null ? null : spoken(accentDetail);
  }

  public String cacheKey() {
    String joined =
        spoken(name)
            + '\u0001'
            + spoken(accent)
            + '\u0001'
            + spoken(style)
            + '\u0001'
            + spoken(pace);
    String spokenPitch = spoken(pitch);
    if (spokenPitch != null) {
      joined += '\u0001' + spokenPitch;
    }
    String detail = spokenAccentDetail();
    if (detail != null) {
      joined += '\u0002' + detail;
    }
    if (age != null) {
      joined += '\u0003' + age.toString();
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(CACHE_KEY_BYTES * 2);
      for (int i = 0; i < CACHE_KEY_BYTES; i++) {
        hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
        hex.append(Character.forDigit(digest[i] & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
