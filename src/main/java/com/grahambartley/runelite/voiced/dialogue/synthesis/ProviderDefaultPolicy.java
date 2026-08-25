package com.grahambartley.runelite.voiced.dialogue.synthesis;

/**
 * Pure decision for keeping a player on the provider they hold a key for.
 *
 * <p>The shipped provider is {@link
 * com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig.TtsProvider#GOOGLE_AI_STUDIO},
 * which needs a Gemini API key. A player carrying only an OpenRouter key would be voiced through a
 * provider they cannot authenticate against, and RuneLite stores nothing for a setting left
 * untouched, so that player and one who has never configured anything look identical in config
 * alone.
 *
 * <p>The OpenRouter key is what tells them apart: it is only ever present because the player put it
 * there. So a player holding one with no explicit provider choice is pinned to OpenRouter, and
 * everyone else keeps the shipped provider.
 */
public final class ProviderDefaultPolicy {

  private ProviderDefaultPolicy() {}

  /**
   * Whether the player's provider should be pinned to OpenRouter to preserve how the plugin behaved
   * for them before the default changed.
   *
   * @param storedProvider the provider explicitly saved in config, or {@code null} when the player
   *     has never chosen one
   * @param openRouterApiKey the configured OpenRouter key, possibly blank or {@code null}
   */
  public static boolean shouldPinToOpenRouter(String storedProvider, String openRouterApiKey) {
    return storedProvider == null && openRouterApiKey != null && !openRouterApiKey.trim().isEmpty();
  }
}
