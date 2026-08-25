package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The provider-neutral text rules: line capping, the translation-target decision, language/quirk
 * combination, and the shared translator system prompt.
 */
@RunWith(JUnitParamsRunner.class)
public class CloudTtsTextTest {

  @Test
  @Parameters(method = "capLengthUnchangedCases")
  public void capLengthLeavesShortLinesAndDisabledCapUntouched(
      String text, int cap, String expected) {
    assertEquals(expected, CloudTtsText.capLength(text, cap));
  }

  private Object[] capLengthUnchangedCases() {
    return new Object[] {
      new Object[] {"Hello there", 600, "Hello there"},
      new Object[] {"long", 0, "long"},
    };
  }

  @Test
  public void capLengthTruncatesAtSentenceBoundary() {
    String text = "First sentence is here. Second sentence runs on and on and on.";
    String capped = CloudTtsText.capLength(text, 40);
    assertTrue("stays within the cap", capped.length() <= 40);
    assertEquals("cuts at the sentence boundary", "First sentence is here.", capped);
  }

  @Test
  public void capLengthFallsBackToWordBoundaryWhenNoSentenceEnd() {
    String text = "one two three four five six seven eight nine ten";
    String capped = CloudTtsText.capLength(text, 20);
    assertTrue("stays within the cap", capped.length() <= 20);
    assertFalse("does not end on a dangling space", capped.endsWith(" "));
    assertTrue("cuts at a word boundary, not mid-word", text.startsWith(capped));
  }

  @Test
  public void capLengthHardCutsWhenThereIsNoBoundary() {
    String capped = CloudTtsText.capLength("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 10);
    assertEquals("a single huge token is hard-cut to the cap", 10, capped.length());
  }

  @Test
  @Parameters(method = "needsTranslationCases")
  public void needsTranslationTreatsBlankAndEnglishAsNoTranslation(
      String language, boolean expected) {
    assertEquals(expected, CloudTtsText.needsTranslation(language));
  }

  private Object[] needsTranslationCases() {
    return new Object[] {
      new Object[] {"English", false},
      new Object[] {"  english  ", false},
      new Object[] {"", false},
      new Object[] {null, false},
      new Object[] {"French", true},
    };
  }

  @Test
  @Parameters(method = "combineLanguageCases")
  public void combineLanguageAppendsTheQuirkOnlyWhenSet(
      String language, VoicedDialogueConfig.SpeakingStyle style, String expected) {
    assertEquals(expected, CloudTtsText.combineLanguage(language, style));
  }

  private Object[] combineLanguageCases() {
    return new Object[] {
      new Object[] {"English", VoicedDialogueConfig.SpeakingStyle.NONE, "English"},
      new Object[] {"English", VoicedDialogueConfig.SpeakingStyle.GEN_Z, "English Gen Z slang"},
      new Object[] {"French", VoicedDialogueConfig.SpeakingStyle.PIRATE, "French pirate speak"},
      new Object[] {"  ", VoicedDialogueConfig.SpeakingStyle.GEN_Z, "English Gen Z slang"},
      new Object[] {
        "English", VoicedDialogueConfig.SpeakingStyle.UK_SLANG, "English with London Roadman Slang"
      },
      new Object[] {
        "English", VoicedDialogueConfig.SpeakingStyle.IRISH_SLANG, "English with Dublin Slang"
      },
      new Object[] {
        "English", VoicedDialogueConfig.SpeakingStyle.RHYMING, "English as rhyming verse"
      },
      new Object[] {
        "French", VoicedDialogueConfig.SpeakingStyle.SURFER, "French with laid-back surfer slang"
      },
    };
  }

  @Test
  public void translatorSystemPromptIsStablePerLanguageAndNamesTheTarget() {
    assertEquals(
        "the same target yields a byte-identical prompt so the model's cache hits",
        CloudTtsText.translatorSystemPrompt("French"),
        CloudTtsText.translatorSystemPrompt("French"));
    assertNotEquals(
        CloudTtsText.translatorSystemPrompt("French"),
        CloudTtsText.translatorSystemPrompt("German"));
    assertTrue(CloudTtsText.translatorSystemPrompt("French").contains("French"));
  }
}
