package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.util.Set;

public final class BackendWarmUpPolicy {

  private static final Set<String> WARM_TRIGGER_KEYS =
      Set.of(
          VoicedDialogueConfig.OPENROUTER_API_KEY,
          VoicedDialogueConfig.GOOGLE_AI_STUDIO_API_KEY,
          VoicedDialogueConfig.PROVIDER_KEY);

  private BackendWarmUpPolicy() {}

  public static boolean affectsBackendWarmUp(String group, String key) {
    return VoicedDialogueConfig.GROUP.equals(group)
        && key != null
        && WARM_TRIGGER_KEYS.contains(key);
  }
}
