package com.grahambartley.runelite.voiced.dialogue.speaker;

import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class NpcGenderTest {

  @Test
  @Parameters(method = "cases")
  public void everythingThatIsNotFemaleSpeaksAsMale(NpcGender gender, NpcGender expected) {
    assertEquals(expected, NpcGender.orDefault(gender));
  }

  private Object[] cases() {
    return new Object[] {
      new Object[] {NpcGender.FEMALE, NpcGender.FEMALE},
      new Object[] {NpcGender.MALE, NpcGender.MALE},
      new Object[] {NpcGender.UNKNOWN, NpcGender.MALE},
      new Object[] {null, NpcGender.MALE},
    };
  }

  @Test
  public void theAnswerIsAlwaysOneOfTheTwoSpokenGenders() {
    for (NpcGender gender : NpcGender.values()) {
      NpcGender resolved = NpcGender.orDefault(gender);
      assertEquals(
          "a voice pool is only ever keyed by male or female",
          true,
          resolved == NpcGender.MALE || resolved == NpcGender.FEMALE);
    }
  }
}
