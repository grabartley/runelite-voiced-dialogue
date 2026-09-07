package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.util.Set;

/**
 * Pure decision for the runtime credentials trigger: whether a {@link
 * net.runelite.client.events.ConfigChanged} changed which backend is active or what it
 * authenticates with. Two things hang off the answer, the off-thread warm-up and dropping a
 * rate-limit window the old credentials earned, so narrowing the key set for one narrows it for the
 * other. Factored out of the plugin so it is testable without RuneLite injection.
 */
public final class BackendWarmUpPolicy {

  /**
   * Config keys that change which backend is active or whether it can become available: entering
   * either provider's API key can make a previously-unavailable backend available, and switching
   * provider makes a different (possibly cold) backend the active one. Either also invalidates a
   * rate-limit window, which the old key or provider earned and the new one has not.
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
