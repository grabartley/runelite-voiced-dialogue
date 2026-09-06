package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class VoiceSpecTest {

  @Test
  @Parameters(method = "playerKeyCases")
  public void playerKeyOmitsRace(NpcGender gender, String expected) {
    assertEquals(expected, VoiceSpec.player(gender).key());
  }

  private Object[] playerKeyCases() {
    return new Object[] {
      new Object[] {NpcGender.MALE, "player:MALE"},
      new Object[] {NpcGender.FEMALE, "player:FEMALE"},
    };
  }

  @Test
  @Parameters(method = "npcKeyCases")
  public void npcKeyCarriesRaceAndGender(NpcRace race, NpcGender gender, String expected) {
    assertEquals(expected, VoiceSpec.npc(race, gender).key());
  }

  private Object[] npcKeyCases() {
    return new Object[] {
      new Object[] {NpcRace.ELF, NpcGender.FEMALE, "npc:ELF:FEMALE"},
      new Object[] {NpcRace.DEMON, NpcGender.MALE, "npc:DEMON:MALE"},
    };
  }

  @Test
  @Parameters(method = "playerFlagCases")
  public void playerSpecIsFlaggedAsPlayer(VoiceSpec spec, boolean expected) {
    assertEquals(expected, spec.player());
  }

  private Object[] playerFlagCases() {
    return new Object[] {
      new Object[] {VoiceSpec.player(NpcGender.MALE), true},
      new Object[] {VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE), false},
    };
  }

  @Test
  public void equalSpecsShareKeyAndEquality() {
    VoiceSpec a = VoiceSpec.npc(NpcRace.GOBLIN, NpcGender.FEMALE);
    VoiceSpec b = VoiceSpec.npc(NpcRace.GOBLIN, NpcGender.FEMALE);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertEquals(a.key(), b.key());
  }

  @Test
  public void playerAndNpcOfSameGenderAreDistinct() {
    VoiceSpec player = VoiceSpec.player(NpcGender.MALE);
    VoiceSpec npc = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);
    assertNotEquals(player, npc);
    assertNotEquals(player.key(), npc.key());
  }

  @Test
  public void bareNpcSpecHasNoVoiceSeed() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE);
    assertFalse(spec.hasVoiceSeed());
    assertEquals(VoiceSpec.UNSPECIFIED_SEED, spec.voiceSeed());
    assertEquals("npc:HUMAN:MALE", spec.key());
  }

  @Test
  public void voiceSeedIsCarriedButNotFoldedIntoKey() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 17);
    assertTrue(spec.hasVoiceSeed());
    assertEquals(17, spec.voiceSeed());
    // The seed drives per-NPC voice variety but is not part of the key: the cloud backend already
    // folds the concrete resolved voice into its own cache variant.
    assertEquals("npc:HUMAN:MALE", spec.key());
  }

  @Test
  public void sameRaceGenderDifferentSeedShareKeyButDifferOnEquality() {
    VoiceSpec a = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 14);
    VoiceSpec b = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 17);
    assertEquals(a.key(), b.key());
    assertNotEquals(a, b);
  }

  @Test
  public void negativeSeedIsNormalisedToUnspecified() {
    VoiceSpec spec = VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, -5);
    assertFalse(spec.hasVoiceSeed());
    assertEquals(VoiceSpec.UNSPECIFIED_SEED, spec.voiceSeed());
    assertEquals("npc:HUMAN:MALE", spec.key());
  }

  @Test
  public void specsDefaultToAdult() {
    assertFalse(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE).child());
    assertFalse(VoiceSpec.npc(NpcRace.HUMAN, NpcGender.MALE, 7).child());
    assertFalse("the player is never a child", VoiceSpec.player(NpcGender.FEMALE).child());
  }

  @Test
  public void childFlagIsCarriedButNotFoldedIntoKey() {
    VoiceSpec child = VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE, 7, true);
    assertTrue(child.child());
    // Like the seed, the child flag drives the concrete voice, which the cloud backend already
    // folds into its own cache variant.
    assertEquals("npc:TROLL:MALE", child.key());
    assertNotEquals(child, VoiceSpec.npc(NpcRace.TROLL, NpcGender.MALE, 7));
  }
}
