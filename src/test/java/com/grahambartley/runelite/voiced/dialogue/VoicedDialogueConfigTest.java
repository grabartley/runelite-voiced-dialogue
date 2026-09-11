package com.grahambartley.runelite.voiced.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.SpokenLanguage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

/**
 * Invariants of the {@link SpokenLanguage} dropdown, the single source of truth for languages, of
 * the provider tooltip that has to state Google AI Studio's daily ceiling, and of the config panel
 * itself: how long a description may run, and which stored keys may never move.
 */
public class VoicedDialogueConfigTest {

  /** What a tooltip a player reads at a glance can hold. */
  private static final int MAX_DESCRIPTION_CHARS = 120;

  /**
   * Voice Provider is the one tooltip allowed to run long: it carries the daily-ceiling disclosure
   * that {@code docs/architecture.md} requires player-facing copy to state.
   */
  private static final int DISCLOSURE_CHARS = 200;

  @Test
  public void theProviderTooltipStatesTheCeilingAndTheUncappedAlternative() throws Exception {
    String description =
        VoicedDialogueConfig.class
            .getMethod("ttsProvider")
            .getAnnotation(ConfigItem.class)
            .description();

    assertTrue(
        "a player choosing in the panel is told the fast provider's ceiling: " + description,
        description.contains("100 fresh lines a day"));
    assertTrue(
        "and that the other one has none: " + description,
        description.contains("OpenRouter has no daily cap"));
  }

  @Test
  public void englishIsTheDefaultNoTranslationLanguage() {
    assertEquals("en-GB", SpokenLanguage.ENGLISH.code());
    assertTrue("English is the no-translation default", SpokenLanguage.ENGLISH.isEnglish());
    for (SpokenLanguage language : SpokenLanguage.values()) {
      assertEquals(
          "English is the only language treated as no-translation",
          language == SpokenLanguage.ENGLISH,
          language.isEnglish());
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
    assertEquals("English", SpokenLanguage.ENGLISH.toString());
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

  @Test
  public void noSettingDescriptionRunsLongerThanATooltipComfortablyShows() {
    List<String> tooLong = new ArrayList<>();
    for (Method method : VoicedDialogueConfig.class.getMethods()) {
      ConfigItem item = method.getAnnotation(ConfigItem.class);
      if (item == null) {
        continue;
      }
      int limit = "ttsProvider".equals(item.keyName()) ? DISCLOSURE_CHARS : MAX_DESCRIPTION_CHARS;
      if (item.description().length() > limit) {
        tooLong.add(item.name() + " (" + item.description().length() + " > " + limit + ")");
      }
    }
    assertTrue("descriptions longer than a tooltip shows: " + tooLong, tooLong.isEmpty());
  }

  @Test
  public void everySettingCarriesANameAndADescription() {
    for (Method method : VoicedDialogueConfig.class.getMethods()) {
      ConfigItem item = method.getAnnotation(ConfigItem.class);
      if (item == null) {
        continue;
      }
      assertFalse("a setting with no name: " + method.getName(), item.name().trim().isEmpty());
      assertFalse(
          "a setting with no description: " + item.name(), item.description().trim().isEmpty());
    }
  }

  /**
   * A stored key is live user state on every existing install: renaming one silently resets that
   * setting, and for a key folded into a character profile it also re-keys cached audio, which
   * re-bills the player for lines they have already paid to synthesize.
   */
  @Test
  public void profileSteeringKeysKeepTheirStoredNames() throws Exception {
    assertEquals(
        "playerAccent",
        VoicedDialogueConfig.class
            .getMethod("playerAccent")
            .getAnnotation(ConfigItem.class)
            .keyName());
    assertEquals(
        "playerPersona",
        VoicedDialogueConfig.class
            .getMethod("playerPersona")
            .getAnnotation(ConfigItem.class)
            .keyName());
    assertEquals(
        "playerPace",
        VoicedDialogueConfig.class
            .getMethod("playerPace")
            .getAnnotation(ConfigItem.class)
            .keyName());
  }

  @Test
  public void yourDeliveryPaceIsLabelledApartFromTheSpeakingPaceSpeedDial() throws Exception {
    ConfigItem direction =
        VoicedDialogueConfig.class.getMethod("playerPace").getAnnotation(ConfigItem.class);
    ConfigItem speed =
        VoicedDialogueConfig.class.getMethod("speakingPace").getAnnotation(ConfigItem.class);
    assertEquals("Your Delivery Pace", direction.name());
    assertEquals("Speaking Pace", speed.name());
    assertFalse(
        "the two pace settings must not share a label", direction.name().equals(speed.name()));
  }
}
