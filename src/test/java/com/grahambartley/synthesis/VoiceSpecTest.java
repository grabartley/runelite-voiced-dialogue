package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class VoiceSpecTest {

  @Test
  @Parameters(method = "playerKeyCases")
  public void playerKeyOmitsRace(NPCGender gender, String expected) {
    assertEquals(expected, VoiceSpec.player(gender).key());
  }

  private Object[] playerKeyCases() {
    return new Object[] {
      new Object[] {NPCGender.MALE, "player:MALE"},
      new Object[] {NPCGender.FEMALE, "player:FEMALE"},
    };
  }

  @Test
  @Parameters(method = "npcKeyCases")
  public void npcKeyCarriesRaceAndGender(NPCRace race, NPCGender gender, String expected) {
    assertEquals(expected, VoiceSpec.npc(race, gender).key());
  }

  private Object[] npcKeyCases() {
    return new Object[] {
      new Object[] {NPCRace.ELF, NPCGender.FEMALE, "npc:ELF:FEMALE"},
      new Object[] {NPCRace.DEMON, NPCGender.MALE, "npc:DEMON:MALE"},
    };
  }

  @Test
  @Parameters(method = "playerFlagCases")
  public void playerSpecIsFlaggedAsPlayer(VoiceSpec spec, boolean expected) {
    assertEquals(expected, spec.player());
  }

  private Object[] playerFlagCases() {
    return new Object[] {
      new Object[] {VoiceSpec.player(NPCGender.MALE), true},
      new Object[] {VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), false},
    };
  }

  @Test
  public void equalSpecsShareKeyAndEquality() {
    VoiceSpec a = VoiceSpec.npc(NPCRace.GOBLIN, NPCGender.FEMALE);
    VoiceSpec b = VoiceSpec.npc(NPCRace.GOBLIN, NPCGender.FEMALE);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a.key(), b.key());
  }

  @Test
  public void playerAndNpcOfSameGenderAreDistinct() {
    VoiceSpec player = VoiceSpec.player(NPCGender.MALE);
    VoiceSpec npc = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE);
    assertNotEquals(player, npc);
    assertNotEquals(player.key(), npc.key());
  }

  @Test
  public void bareNpcSpecHasNoVoiceSeed() {
    VoiceSpec spec = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE);
    assertFalse(spec.hasVoiceSeed());
    assertEquals(VoiceSpec.UNSPECIFIED_SEED, spec.voiceSeed());
    assertEquals("npc:HUMAN:MALE", spec.key());
  }

  @Test
  public void voiceSeedIsCarriedButNotFoldedIntoKey() {
    VoiceSpec spec = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE, 17);
    assertTrue(spec.hasVoiceSeed());
    assertEquals(17, spec.voiceSeed());
    // The seed drives per-NPC voice variety but is not part of the key: the cloud backend already
    // folds the concrete resolved voice into its own cache variant.
    assertEquals("npc:HUMAN:MALE", spec.key());
  }

  @Test
  public void sameRaceGenderDifferentSeedShareKeyButDifferOnEquality() {
    VoiceSpec a = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE, 14);
    VoiceSpec b = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE, 17);
    assertEquals(a.key(), b.key());
    assertNotEquals(a, b);
  }

  @Test
  public void negativeSeedIsNormalisedToUnspecified() {
    VoiceSpec spec = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE, -5);
    assertFalse(spec.hasVoiceSeed());
    assertEquals(VoiceSpec.UNSPECIFIED_SEED, spec.voiceSeed());
    assertEquals("npc:HUMAN:MALE", spec.key());
  }
}
