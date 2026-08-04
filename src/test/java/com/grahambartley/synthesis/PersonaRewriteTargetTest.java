package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import org.junit.Test;

/** Who is eligible for the opt-in mature persona rewrite, and what it asks the model for. */
public class PersonaRewriteTargetTest {

  private static final class TestConfig implements VoicedDialogueConfig {
    boolean mature = true;

    @Override
    public boolean allowMaturePersonaAdlibs() {
      return mature;
    }
  }

  private static final CharacterProfile RUDE =
      new CharacterProfile("Adventurer", "British.", "Rude and foul-mouthed.", "Normal.");

  private static SynthesisRequest playerLine(CharacterProfile profile) {
    return new SynthesisRequest(
        "Move.",
        VoiceSpec.player(NPCGender.MALE),
        Emotion.NEUTRAL,
        profile,
        /* skipTranslation= */ false,
        /* player= */ true);
  }

  @Test
  public void anOptedInPlayerPersonaIsEligible() {
    assertTrue(PersonaRewriteTarget.enabled(new TestConfig(), playerLine(RUDE)));
  }

  @Test
  public void theToggleIsRequired() {
    TestConfig config = new TestConfig();
    config.mature = false;

    assertFalse(PersonaRewriteTarget.enabled(config, playerLine(RUDE)));
  }

  @Test
  public void npcLinesAreNeverEligible() {
    SynthesisRequest npcLine =
        new SynthesisRequest(
            "Move.", VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL, RUDE);

    assertFalse(PersonaRewriteTarget.enabled(new TestConfig(), npcLine));
  }

  @Test
  public void publicChatIsNeverEligible() {
    SynthesisRequest publicChat =
        new SynthesisRequest(
            "Move.",
            VoiceSpec.player(NPCGender.MALE),
            Emotion.NEUTRAL,
            RUDE,
            /* skipTranslation= */ true,
            /* player= */ true);

    assertFalse(PersonaRewriteTarget.enabled(new TestConfig(), publicChat));
  }

  @Test
  public void aPlayerWithNoPersonaIsNotEligible() {
    assertFalse(PersonaRewriteTarget.enabled(new TestConfig(), playerLine(null)));
    CharacterProfile blank = new CharacterProfile("Adventurer", "British.", "   ", "Normal.");
    assertFalse(PersonaRewriteTarget.enabled(new TestConfig(), playerLine(blank)));
  }

  @Test
  public void theTargetCarriesThePersonaAndKeepsTheMeaning() {
    String target = PersonaRewriteTarget.apply(new TestConfig(), playerLine(RUDE), "English");

    assertTrue(target.startsWith("English"));
    assertTrue(target.contains("Rude and foul-mouthed."));
    assertTrue(target.contains("preserve the original meaning, names, and RuneScape terms"));
  }

  @Test
  public void anIneligibleLineKeepsItsPlainTarget() {
    TestConfig config = new TestConfig();
    config.mature = false;

    assertEquals("English", PersonaRewriteTarget.apply(config, playerLine(RUDE), "English"));
  }
}
