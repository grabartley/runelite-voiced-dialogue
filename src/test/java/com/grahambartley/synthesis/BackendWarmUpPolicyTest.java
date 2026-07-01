package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Pure decision logic for the runtime warm-up trigger (#75): only the plugin group plus a
 * backend-affecting key should warm.
 */
@RunWith(JUnitParamsRunner.class)
public class BackendWarmUpPolicyTest {

  private Object[] warmUpCases() {
    return new Object[] {
      // Plugin group with a backend-affecting key warms.
      new Object[] {"voicedDialogue", "openRouterApiKey", true},
      // Right group, key that does not affect backend availability.
      new Object[] {"voicedDialogue", "volume", false},
      // A backend key but a different plugin's config group.
      new Object[] {"otherPlugin", "openRouterApiKey", false},
      // Defensive: nulls never throw and never warm.
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
