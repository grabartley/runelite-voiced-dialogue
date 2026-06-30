package com.grahambartley;

import net.runelite.client.util.Text;

/**
 * Pure decision for the player public-chat feature (#138): whether a public-chat event came from
 * the local player. Both names are run through {@link Text#sanitize} first because {@code
 * event.getName()} can carry clan/friend rank {@code <img=...>} icons and non-breaking spaces that
 * the local player's raw name does not. Null-safe (a null name never matches).
 */
final class PublicChatPolicy {

  private PublicChatPolicy() {}

  static boolean isSelfPublicChat(String eventName, String localName) {
    if (eventName == null || localName == null) {
      return false;
    }
    return Text.sanitize(eventName).equals(Text.sanitize(localName));
  }
}
