package com.grahambartley.runelite.voiced.dialogue.capture;

import net.runelite.client.util.Text;

public final class PublicChatPolicy {

  private PublicChatPolicy() {}

  public static boolean isSelfPublicChat(String eventName, String localName) {
    if (eventName == null || localName == null) {
      return false;
    }
    return Text.sanitize(eventName).equals(Text.sanitize(localName));
  }
}
