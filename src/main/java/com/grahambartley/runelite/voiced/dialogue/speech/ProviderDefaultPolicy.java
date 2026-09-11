package com.grahambartley.runelite.voiced.dialogue.speech;

public final class ProviderDefaultPolicy {

  private ProviderDefaultPolicy() {}

  public static boolean shouldPinToOpenRouter(String storedProvider, String openRouterApiKey) {
    return storedProvider == null && openRouterApiKey != null && !openRouterApiKey.trim().isEmpty();
  }
}
