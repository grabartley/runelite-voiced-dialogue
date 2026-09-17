package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.junit.Assert.assertEquals;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class FollowerOutfitTest {

  @Test
  @Parameters(method = "outfitCases")
  public void theOutfitStringDecidesTheGender(String outfit, NpcGender expected) {
    assertEquals(expected, FollowerOutfit.genderOf(outfit));
  }

  private Object[] outfitCases() {
    return new Object[] {
      new Object[] {
        "HAIR=kit:128,HANDS=kit:69,BOOTS=kit:79,JAW=kit:296,gender=female", NpcGender.FEMALE
      },
      new Object[] {"HAIR=kit:0,gender=male", NpcGender.MALE},
      new Object[] {"gender=male", NpcGender.MALE},
      new Object[] {"gender=FEMALE", NpcGender.FEMALE},
      new Object[] {"gender = female", NpcGender.FEMALE},
      new Object[] {"HAIR=kit:128,BOOTS=kit:79", NpcGender.UNKNOWN},
      new Object[] {"HAIR=kit:0\ngender=male", NpcGender.MALE},
      new Object[] {"HAIR=kit:0\r\ngender=female", NpcGender.FEMALE},
      new Object[] {"gender=f", NpcGender.FEMALE},
      new Object[] {"gender=1", NpcGender.FEMALE},
      new Object[] {"gender=0", NpcGender.MALE},
      new Object[] {"gender=other", NpcGender.MALE},
      new Object[] {"gender=", NpcGender.UNKNOWN},
      new Object[] {"", NpcGender.UNKNOWN},
      new Object[] {null, NpcGender.UNKNOWN},
    };
  }

  @Test
  public void aKitNamedAfterGenderDoesNotMasqueradeAsTheGenderField() {
    assertEquals(NpcGender.UNKNOWN, FollowerOutfit.genderOf("ANDROGYNDER=kit:12"));
  }

  @Test
  public void theOutfitFormFollowerBuddyActuallyWritesIsRead() {
    assertEquals(
        NpcGender.FEMALE,
        FollowerOutfit.genderOf(
            "HAIR=kit:128,HANDS=kit:69,BOOTS=kit:79,JAW=kit:296,gender=female"));
    assertEquals(
        "the male outfit omits the token entirely",
        NpcGender.UNKNOWN,
        FollowerOutfit.genderOf("HAIR=kit:128,HANDS=kit:69,BOOTS=kit:79,JAW=kit:296"));
  }
}
