package com.grahambartley.runelite.voiced.dialogue.speech;

import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class BackendWarmUpPolicyTest {

  private Object[] warmUpCases() {
    return new Object[] {
      new Object[] {"voicedDialogue", "openRouterApiKey", true},
      new Object[] {"voicedDialogue", "googleAiStudioApiKey", true},
      new Object[] {"voicedDialogue", "ttsProvider", true},
      new Object[] {"voicedDialogue", "volume", false},
      new Object[] {"otherPlugin", "openRouterApiKey", false},
      new Object[] {null, "openRouterApiKey", false},
      new Object[] {"voicedDialogue", null, false},
    };
  }

  @Test
  @Parameters(method = "warmUpCases")
  public void affectsBackendWarmUp(String group, String key, boolean expected) {
    assertEquals(expected, BackendWarmUpPolicy.affectsBackendWarmUp(group, key));
  }
}
