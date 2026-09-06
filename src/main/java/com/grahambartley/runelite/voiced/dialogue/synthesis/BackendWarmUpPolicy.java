package com.grahambartley.runelite.voiced.dialogue.synthesis;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.util.Set;

/**
 * Pure decision for the runtime warm-up trigger: whether a {@link
 * net.runelite.client.events.ConfigChanged} should re-run the active backend's off-thread warm-up.
 * Factored out of the plugin so it is testable without RuneLite injection.
 */
public final class BackendWarmUpPolicy {

  /**
   * Config keys that change which backend is active or whether it can become available: entering
   * either provider's API key can make a previously-unavailable backend available, and switching
   * provider makes a different (possibly cold) backend the active one.
   */
  private static final Set<String> WARM_TRIGGER_KEYS =
      Set.of(
          VoicedDialogueConfig.OPENROUTER_API_KEY,
          VoicedDialogueConfig.GOOGLE_AI_STUDIO_API_KEY,
          VoicedDialogueConfig.PROVIDER_KEY);

  private BackendWarmUpPolicy() {}

  /**
   * Returns {@code true} only when a changed config entry belongs to this plugin's group and its
   * key affects backend availability. Never throws; tolerates {@code null} group/key.
   */
  public static boolean affectsBackendWarmUp(String group, String key) {
    // key != null first: WARM_TRIGGER_KEYS is an immutable Set.of(...), whose contains(null)
    // throws.
    return VoicedDialogueConfig.GROUP.equals(group)
        && key != null
        && WARM_TRIGGER_KEYS.contains(key);
  }
}
