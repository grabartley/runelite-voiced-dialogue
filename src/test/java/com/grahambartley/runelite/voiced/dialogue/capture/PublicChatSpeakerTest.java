package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import org.junit.Before;
import org.junit.Test;

public class PublicChatSpeakerTest {

  private static final String LOCAL_NAME = "Zezima";

  private final Client client = mock(Client.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);
  private boolean enabled = true;

  private final PublicChatSpeaker speaker =
      new PublicChatSpeaker(
          client, new DialogueTextCleaner(new ProfanityFilter()), dispatcher, () -> enabled);

  @Before
  public void setUp() {
    Player local = mock(Player.class);
    when(local.getName()).thenReturn(LOCAL_NAME);
    when(client.getLocalPlayer()).thenReturn(local);
  }

  @Test
  public void theLocalPlayersOwnPublicChatIsSpokenCleaned() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, LOCAL_NAME, "<col=ff0000>Hi there!"));

    verify(dispatcher).speakPublicChat("Hi there!");
  }

  @Test
  public void aRankIconOnTheChatNameStillMatchesTheLocalPlayer() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "<img=2>" + LOCAL_NAME, "Hi there!"));

    verify(dispatcher).speakPublicChat("Hi there!");
  }

  @Test
  public void anotherPlayersPublicChatIsIgnored() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, "Woox", "Hi there!"));

    verify(dispatcher, never()).speakPublicChat(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  public void otherChatTypesAreIgnored() {
    speaker.onChatMessage(chat(ChatMessageType.GAMEMESSAGE, LOCAL_NAME, "Hi there!"));

    verify(dispatcher, never()).speakPublicChat(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  public void theToggleIsReadLiveSoTurningItOffSilencesTheNextMessage() {
    enabled = false;

    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, LOCAL_NAME, "Hi there!"));

    verify(dispatcher, never()).speakPublicChat(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  public void aMessageThatCleansAwayToNothingIsNotSpoken() {
    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, LOCAL_NAME, "<img=2>"));

    verify(dispatcher, never()).speakPublicChat(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  public void aMessageArrivingWhileLoggedOutIsIgnored() {
    when(client.getLocalPlayer()).thenReturn(null);

    speaker.onChatMessage(chat(ChatMessageType.PUBLICCHAT, LOCAL_NAME, "Hi there!"));

    verify(dispatcher, never()).speakPublicChat(org.mockito.ArgumentMatchers.anyString());
  }

  private static ChatMessage chat(ChatMessageType type, String name, String message) {
    ChatMessage event = new ChatMessage();
    event.setType(type);
    event.setName(name);
    event.setMessage(message);
    return event;
  }
}
