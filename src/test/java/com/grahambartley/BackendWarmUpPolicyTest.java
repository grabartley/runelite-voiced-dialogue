package com.grahambartley;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure decision logic for the runtime backend-switch warm-up trigger (#75): only the plugin group
 * plus a backend-affecting key should warm.
 */
public class BackendWarmUpPolicyTest {

  @Test
  public void recognisesBackendKeys() {
    assertTrue(BackendWarmUpPolicy.affectsBackendWarmUp("ttsDialogue", "voiceBackend"));
    assertTrue(BackendWarmUpPolicy.affectsBackendWarmUp("ttsDialogue", "openRouterApiKey"));
  }

  @Test
  public void ignoresUnrelatedGroupKeyOrNulls() {
    // Right group, key that does not affect backend selection/availability.
    assertFalse(BackendWarmUpPolicy.affectsBackendWarmUp("ttsDialogue", "volume"));
    // A backend key but a different plugin's config group.
    assertFalse(BackendWarmUpPolicy.affectsBackendWarmUp("otherPlugin", "voiceBackend"));
    // Defensive: nulls never throw and never warm.
    assertFalse(BackendWarmUpPolicy.affectsBackendWarmUp(null, "voiceBackend"));
    assertFalse(BackendWarmUpPolicy.affectsBackendWarmUp("ttsDialogue", null));
  }
}
