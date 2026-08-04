package com.grahambartley;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.VoicedDialogueConfig.SpokenLanguage;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Invariants of the {@link SpokenLanguage} dropdown: the single source of truth for languages. */
public class VoicedDialogueConfigTest {

  @Test
  public void englishIsTheDefaultNoTranslationLanguage() {
    assertEquals("en-GB", SpokenLanguage.ENGLISH.code());
    assertTrue("English is the no-translation default", SpokenLanguage.ENGLISH.isEnglish());
    for (SpokenLanguage language : SpokenLanguage.values()) {
      assertEquals(
          "all and only the English variants are treated as no-translation",
          language.code().startsWith("en-"),
          language.isEnglish());
    }
  }

  @Test
  public void onlyTheNonDefaultEnglishVariantsCarryAPronunciationDirection() {
    assertEquals(
        "the default keeps its existing request shape",
        "",
        SpokenLanguage.ENGLISH.pronunciationDirection());
    assertTrue(
        SpokenLanguage.AMERICAN_ENGLISH.pronunciationDirection().contains("American English"));
    assertTrue(
        SpokenLanguage.AUSTRALIAN_ENGLISH.pronunciationDirection().contains("Australian English"));
  }

  @Test
  public void everyTranslatedLanguageAsksForNativePronunciation() {
    for (SpokenLanguage language : SpokenLanguage.values()) {
      if (language.isEnglish()) {
        continue;
      }
      String direction = language.pronunciationDirection();
      assertTrue(direction, direction.contains("native " + language.label() + " pronunciation"));
      assertTrue(direction, direction.contains("Do not use an English accent"));
    }
  }

  @Test
  public void accentStylesCarryAVoiceDirectionAndKeepTheLineInEnglish() {
    for (VoicedDialogueConfig.SpeakingStyle style :
        new VoicedDialogueConfig.SpeakingStyle[] {
          VoicedDialogueConfig.SpeakingStyle.AUS_SLANG,
          VoicedDialogueConfig.SpeakingStyle.BOSTON,
          VoicedDialogueConfig.SpeakingStyle.FRENCH_ACCENT
        }) {
      assertFalse(style.toString(), style.voiceDirection().isEmpty());
      assertTrue(style.toString(), style.forcesEnglish());
    }
  }

  @Test
  public void registerOnlyStylesStayLanguageAgnostic() {
    for (VoicedDialogueConfig.SpeakingStyle style : VoicedDialogueConfig.SpeakingStyle.values()) {
      if (style.voiceDirection().isEmpty()) {
        assertFalse(style.toString(), style.forcesEnglish());
      }
    }
  }

  @Test
  public void everyLanguageCarriesANameAndWellFormedBcp47Code() {
    for (SpokenLanguage language : SpokenLanguage.values()) {
      assertFalse(
          "a language name is fed to the prompt, so it is never blank", language.label().isEmpty());
      assertTrue(
          "the code is a BCP-47 tag: " + language.code(),
          language.code().matches("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*"));
    }
  }

  @Test
  public void theDropdownIsDeAliasedSoEachCodeAppearsOnce() {
    Set<String> codes = new HashSet<>();
    for (SpokenLanguage language : SpokenLanguage.values()) {
      assertTrue(
          "a duplicate code means an alias slipped in: " + language.code(),
          codes.add(language.code()));
    }
  }

  @Test
  public void toStringShowsTheDisplayNameWithoutTheCode() {
    assertEquals("English (UK)", SpokenLanguage.ENGLISH.toString());
    assertEquals("English (US)", SpokenLanguage.AMERICAN_ENGLISH.toString());
    assertEquals("English (AU)", SpokenLanguage.AUSTRALIAN_ENGLISH.toString());
    assertEquals("Spanish", SpokenLanguage.SPANISH.toString());
    for (SpokenLanguage language : SpokenLanguage.values()) {
      assertFalse(
          "the dropdown never exposes the BCP-47 code: " + language,
          language.toString().contains(language.code()));
    }
  }

  @Test
  public void regionalVariantsShortenTheDropdownTextButKeepTheFullTranslationName() {
    assertEquals("Portuguese (BR)", SpokenLanguage.BRAZILIAN_PORTUGUESE.toString());
    assertEquals("Brazilian Portuguese", SpokenLanguage.BRAZILIAN_PORTUGUESE.label());
    assertEquals("Spanish (LatAm)", SpokenLanguage.LATIN_AMERICAN_SPANISH.toString());
    assertEquals("Latin American Spanish", SpokenLanguage.LATIN_AMERICAN_SPANISH.label());
  }
}
