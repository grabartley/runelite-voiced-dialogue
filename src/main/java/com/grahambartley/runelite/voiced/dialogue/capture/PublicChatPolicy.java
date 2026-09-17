package com.grahambartley.runelite.voiced.dialogue.capture;

import net.runelite.client.util.Text;

public final class PublicChatPolicy {

  private PublicChatPolicy() {}

  public static boolean isSelfPublicChat(String eventName, String localName) {
    return isFrom(eventName, localName);
  }

  public static boolean isFrom(String eventName, String speakerName) {
    if (eventName == null || speakerName == null) {
      return false;
    }
    return Text.sanitize(eventName).equals(Text.sanitize(speakerName));
  }
}
