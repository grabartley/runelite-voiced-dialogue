package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.capture.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.capture.PublicChatPolicy;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;

public final class FollowerSpeaker {

  private final DialogueTextCleaner textCleaner;
  private final SynthesisDispatcher dispatcher;
  private final BooleanSupplier enabled;
  private final BooleanSupplier conversationOnScreen;
  private final Supplier<String> followerName;
  private final Supplier<NpcGender> gender;

  public FollowerSpeaker(
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      BooleanSupplier enabled,
      BooleanSupplier conversationOnScreen,
      Supplier<String> followerName,
      Supplier<NpcGender> gender) {
    this.textCleaner = textCleaner;
    this.dispatcher = dispatcher;
    this.enabled = enabled;
    this.conversationOnScreen = conversationOnScreen;
    this.followerName = followerName;
    this.gender = gender;
  }

  public void onChatMessage(ChatMessage event) {
    if (event.getType() != ChatMessageType.PUBLICCHAT
        || !enabled.getAsBoolean()
        || conversationOnScreen.getAsBoolean()) {
      return;
    }
    if (!PublicChatPolicy.isFrom(event.getName(), followerName.get())) {
      return;
    }
    String cleaned = textCleaner.clean(event.getMessage());
    if (cleaned.isEmpty()) {
      return;
    }
    dispatcher.speakFollower(cleaned, gender.get());
  }
}
