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
      new Object[] {"gender=other", NpcGender.UNKNOWN},
      new Object[] {"", NpcGender.UNKNOWN},
      new Object[] {null, NpcGender.UNKNOWN},
    };
  }

  @Test
  public void aKitNamedAfterGenderDoesNotMasqueradeAsTheGenderField() {
    assertEquals(NpcGender.UNKNOWN, FollowerOutfit.genderOf("ANDROGYNDER=kit:12"));
  }
}
