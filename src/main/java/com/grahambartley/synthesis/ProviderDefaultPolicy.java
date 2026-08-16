package com.grahambartley.synthesis;

/**
 * Pure decision for honouring a player's existing provider when the shipped default changes.
 *
 * <p>The default provider is now {@link
 * com.grahambartley.VoicedDialogueConfig.TtsProvider#GOOGLE_AI_STUDIO}, because it streams audio
 * while OpenRouter withholds it until a line is fully generated. A player who was already voicing
 * dialogue through OpenRouter must not be moved onto a provider they have no key for, though, and
 * RuneLite stores nothing for a setting left untouched, so an established player and a brand new
 * one look identical in config alone.
 *
 * <p>Their OpenRouter key is what tells them apart: it is only ever present because the player put
 * it there. So a player with a key and no explicit provider choice is pinned to OpenRouter once,
 * making the previous default explicit; everyone else gets the new one.
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
