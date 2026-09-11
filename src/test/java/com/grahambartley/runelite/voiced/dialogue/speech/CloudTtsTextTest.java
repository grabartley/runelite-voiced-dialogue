package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The provider-neutral text rules: the translation-target decision, language/quirk combination, and
 * the shared translator system prompt.
 */
@RunWith(JUnitParamsRunner.class)
public class CloudTtsTextTest {

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
  public void narrationTakesNeitherSpeakingStyleButKeepsTheSpokenLanguage() {
    MutableTestConfig config = new MutableTestConfig();
    config.language = VoicedDialogueConfig.SpokenLanguage.FRENCH;
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.PIRATE;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;

    assertEquals(
        "narration is the game's own voice, so no character register is layered on",
        "French",
        CloudTtsText.effectiveSpokenLanguage(config, narrationRequest()));
  }

  @Test
  @Parameters(method = "speakerClassStyleCases")
  public void eachSpeakerClassPicksItsOwnStyle(SynthesisRequest request, String expected) {
    MutableTestConfig config = new MutableTestConfig();
    config.playerQuirk = VoicedDialogueConfig.SpeakingStyle.PIRATE;
    config.npcQuirk = VoicedDialogueConfig.SpeakingStyle.GEN_Z;

    assertEquals(expected, CloudTtsText.effectiveSpokenLanguage(config, request));
  }

  private Object[] speakerClassStyleCases() {
    return new Object[] {
      new Object[] {characterRequest(true), "English pirate speak"},
      new Object[] {characterRequest(false), "English Gen Z slang"},
      new Object[] {narrationRequest(), "English"},
    };
  }

  private static SynthesisRequest narrationRequest() {
    return new SynthesisRequest(
        "You find a key.", VoiceSpec.NARRATOR, Emotion.NEUTRAL, null, false, false);
  }

  private static SynthesisRequest characterRequest(boolean player) {
    VoiceSpec voice =
        player ? VoiceSpec.player(NpcGender.MALE) : VoiceSpec.npc(NpcRace.HUMAN, NpcGender.FEMALE);
    return new SynthesisRequest("Hello.", voice, Emotion.NEUTRAL, null, false, player);
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
