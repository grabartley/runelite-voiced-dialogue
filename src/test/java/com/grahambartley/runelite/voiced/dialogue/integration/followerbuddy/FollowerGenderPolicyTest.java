package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.junit.Assert.assertEquals;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class FollowerGenderPolicyTest {

  @Test
  @Parameters(method = "cases")
  public void theSettingOutranksTheOutfit(
      FollowerVoice configured, NpcGender fromOutfit, NpcGender expected) {
    assertEquals(expected, FollowerGenderPolicy.resolve(configured, fromOutfit));
  }

  private Object[] cases() {
    return new Object[] {
      new Object[] {FollowerVoice.TYPE_A, NpcGender.FEMALE, NpcGender.MALE},
      new Object[] {FollowerVoice.TYPE_B, NpcGender.MALE, NpcGender.FEMALE},
      new Object[] {FollowerVoice.AUTO, NpcGender.FEMALE, NpcGender.FEMALE},
      new Object[] {FollowerVoice.AUTO, NpcGender.MALE, NpcGender.MALE},
      new Object[] {FollowerVoice.AUTO, NpcGender.UNKNOWN, FollowerGenderPolicy.FALLBACK},
      new Object[] {FollowerVoice.AUTO, null, FollowerGenderPolicy.FALLBACK},
      new Object[] {null, NpcGender.FEMALE, NpcGender.FEMALE},
      new Object[] {null, NpcGender.UNKNOWN, FollowerGenderPolicy.FALLBACK},
    };
  }

  @Test
  public void autoCarriesNoGenderOfItsOwn() {
    assertEquals(NpcGender.UNKNOWN, FollowerVoice.AUTO.getGender());
    assertEquals(NpcGender.MALE, FollowerVoice.TYPE_A.getGender());
    assertEquals(NpcGender.FEMALE, FollowerVoice.TYPE_B.getGender());
  }

  @Test
  public void theSettingLabelsReadAsVoiceTypes() {
    assertEquals("Auto", FollowerVoice.AUTO.toString());
    assertEquals("Type A", FollowerVoice.TYPE_A.toString());
    assertEquals("Type B", FollowerVoice.TYPE_B.toString());
  }
}
