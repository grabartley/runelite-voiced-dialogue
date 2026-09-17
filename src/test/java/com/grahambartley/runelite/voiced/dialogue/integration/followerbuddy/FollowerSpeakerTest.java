package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.grahambartley.runelite.voiced.dialogue.capture.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

public class FollowerSpeakerTest {

  private static final String FOLLOWER_NAME = "Barkley";

  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);

  private boolean enabled = true;
  private boolean conversationOnScreen;
  private String followerName = FOLLOWER_NAME;
  private NpcGender gender = NpcGender.MALE;
  private int nameReads;

  private final FollowerSpeaker speaker =
      new FollowerSpeaker(
          new DialogueTextCleaner(new ProfanityFilter()),
          dispatcher,
          () -> enabled,
          () -> conversationOnScreen,
          () -> {
            nameReads++;
            return followerName;
          },
          () -> gender);

  @Test
  public void theFollowersMirroredLineIsSpokenCleaned() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "<col=ff0000>Woof!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.MALE);
  }

  @Test
  public void theResolvedGenderReachesTheDispatcher() {
    gender = NpcGender.FEMALE;

    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.FEMALE);
  }

  @Test
  public void anIconOnTheChatNameStillMatchesTheFollower() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "<img=2>" + FOLLOWER_NAME, "Woof!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.MALE);
  }

  @Test
  public void anotherPlayersPublicChatIsIgnored() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Zezima", "Hi there!"));

    verify(dispatcher, never()).speakFollower(anyString(), any());
  }

  @Test
  public void otherChatTypesAreIgnored() {
    speaker.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, FOLLOWER_NAME, "Woof!"));

    verify(dispatcher, never()).speakFollower(anyString(), any());
  }

  @Test
  public void aRenamedFollowerIsMatchedOnTheNextMessage() {
    followerName = "Rufus";

    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Rufus", "Woof!"));

    verify(dispatcher).speakFollower("Woof!", NpcGender.MALE);
  }

  @Test
  public void aMessageThatCleansAwayToNothingIsNotSpoken() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "<img=2>"));

    verify(dispatcher, never()).speakFollower(anyString(), any());
  }

  @Test
  public void theToggleIsReadLiveSoTurningItOffSilencesTheNextMessage() {
    enabled = false;

    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    verify(dispatcher, never()).speakFollower(anyString(), any());
  }

  @Test
  public void aDisabledFeatureNeverLooksUpTheFollowerName() {
    enabled = false;

    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    org.junit.Assert.assertEquals(0, nameReads);
  }

  @Test
  public void aNonPublicMessageNeverLooksUpTheFollowerName() {
    speaker.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, FOLLOWER_NAME, "Woof!"));

    org.junit.Assert.assertEquals(0, nameReads);
  }

  @Test
  public void theCompanionStaysQuietWhileAConversationIsOnScreen() {
    conversationOnScreen = true;

    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    verify(dispatcher, never()).speakFollower(anyString(), any());
    org.junit.Assert.assertEquals(
        "a line we will not speak costs no cross-plugin read", 0, nameReads);
  }

  @Test
  public void theCompanionSpeaksAgainOnceTheConversationCloses() {
    conversationOnScreen = true;
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof!"));

    conversationOnScreen = false;
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, FOLLOWER_NAME, "Woof again!"));

    verify(dispatcher, never()).speakFollower("Woof!", NpcGender.MALE);
    verify(dispatcher).speakFollower("Woof again!", NpcGender.MALE);
  }

  private static ChatMessage chat(ChatMessageType type, String name, String message) {
    ChatMessage event = new ChatMessage();
    event.setType(type);
    event.setName(name);
    event.setMessage(message);
    return event;
  }
}
