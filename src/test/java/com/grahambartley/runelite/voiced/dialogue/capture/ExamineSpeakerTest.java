package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The examine policy: only the client's three examine chat types are voiced, always through the
 * narrator, never while a dialogue holds the audio channel, and never at all while the toggle is
 * off.
 */
@RunWith(JUnitParamsRunner.class)
public class ExamineSpeakerTest {

  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);

  private boolean enabled = true;
  private boolean dialogueOpen = false;

  private final ExamineSpeaker speaker =
      new ExamineSpeaker(
          new DialogueTextCleaner(new ProfanityFilter()),
          dispatcher,
          () -> enabled,
          () -> dialogueOpen);

  private static ChatMessage message(ChatMessageType type, String text) {
    return message(type, text, null);
  }

  /**
   * {@code runeLiteFormat} non-null marks the node as authored or reformatted by a plugin, which is
   * how the client distinguishes those from the game's own text.
   */
  private static ChatMessage message(ChatMessageType type, String text, String runeLiteFormat) {
    MessageNode node = mock(MessageNode.class);
    when(node.getRuneLiteFormatMessage()).thenReturn(runeLiteFormat);
    ChatMessage event = new ChatMessage();
    event.setType(type);
    event.setMessage(text);
    event.setMessageNode(node);
    return event;
  }

  private Object[] examineTypes() {
    return new Object[] {
      ChatMessageType.ITEM_EXAMINE, ChatMessageType.NPC_EXAMINE, ChatMessageType.OBJECT_EXAMINE
    };
  }

  @Test
  @Parameters(method = "examineTypes")
  public void everyExamineTypeIsVoicedThroughTheNarrator(ChatMessageType type) {
    speaker.onChatMessage(message(type, "It's a bucket of milk."));

    verify(dispatcher).speakNarration("It's a bucket of milk.");
  }

  private Object[] nonExamineTypes() {
    return new Object[] {
      ChatMessageType.GAMEMESSAGE,
      ChatMessageType.PUBLICCHAT,
      ChatMessageType.MESBOX,
      ChatMessageType.DIALOG,
      ChatMessageType.NPC_SAY,
      ChatMessageType.SPAM,
      ChatMessageType.PRIVATECHAT,
      ChatMessageType.CLAN_CHAT,
      ChatMessageType.FRIENDSCHAT,
      ChatMessageType.WELCOME,
      ChatMessageType.CONSOLE,
      ChatMessageType.BROADCAST,
      ChatMessageType.TRADE,
      ChatMessageType.ENGINE,
      ChatMessageType.UNKNOWN
    };
  }

  @Test
  @Parameters(method = "nonExamineTypes")
  public void noOtherChatTypeIsEverVoiced(ChatMessageType type) {
    speaker.onChatMessage(message(type, "You feel something weird."));

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void switchedOffNothingIsVoiced() {
    enabled = false;

    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void switchingItBackOnTakesEffectOnTheNextExamine() {
    enabled = false;
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));
    enabled = true;
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));

    verify(dispatcher, times(1)).speakNarration("It's a bucket of milk.");
  }

  @Test
  public void anOpenDialogueKeepsTheAudioChannel() {
    dialogueOpen = true;

    speaker.onChatMessage(message(ChatMessageType.NPC_EXAMINE, "A gruff dwarf."));

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void examineResumesOnceTheDialogueCloses() {
    dialogueOpen = true;
    speaker.onChatMessage(message(ChatMessageType.NPC_EXAMINE, "A gruff dwarf."));
    dialogueOpen = false;
    speaker.onChatMessage(message(ChatMessageType.NPC_EXAMINE, "A gruff dwarf."));

    verify(dispatcher, times(1)).speakNarration("A gruff dwarf.");
  }

  @Test
  public void markupIsStrippedBeforeVoicing() {
    speaker.onChatMessage(
        message(ChatMessageType.OBJECT_EXAMINE, "<col=ffffff>A gnarled old tree.</col>"));

    verify(dispatcher).speakNarration("A gnarled old tree.");
  }

  @Test
  public void textThatCleansAwayToNothingIsNotVoiced() {
    // Not parameterized: JUnitParams trims each row, so a whitespace-only case would arrive here as
    // the empty string and quietly re-test the case above it.
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, ""));
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "   "));
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "<col=ffffff></col>"));

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void aNullMessageIsIgnoredRatherThanThrowing() {
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, null));

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void aPluginsOwnLineOnAnExamineTypeIsNotVoiced() {
    speaker.onChatMessage(
        message(
            ChatMessageType.ITEM_EXAMINE,
            "Price of Prayer potion(4): 9,500 coins",
            "Price of <col=ff0000>Prayer potion(4)</col>: 9,500 coins"));

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void aPluginsPriceLookupCannotTalkOverTheExamineTextItFollows() {
    // RuneLite's own Examine plugin appends a price on ITEM_EXAMINE in the same tick as the real
    // examine text. Every new line stops the one playing, so voicing the price would cut off the
    // flavour line the player actually asked for.
    speaker.onChatMessage(
        message(ChatMessageType.ITEM_EXAMINE, "4 doses of Prayer restore potion."));
    speaker.onChatMessage(
        message(
            ChatMessageType.ITEM_EXAMINE,
            "Price of Prayer potion(4): 9,500 coins",
            "Price of ..."));

    verify(dispatcher, times(1)).speakNarration("4 doses of Prayer restore potion.");
    verifyNoMoreInteractions(dispatcher);
  }

  @Test
  public void aNodelessEventIsTreatedAsGameAuthored() {
    ChatMessage event = new ChatMessage();
    event.setType(ChatMessageType.OBJECT_EXAMINE);
    event.setMessage("A beautiful old oak.");

    speaker.onChatMessage(event);

    verify(dispatcher).speakNarration("A beautiful old oak.");
  }

  @Test
  public void examiningTheSameThingTwiceVoicesItBothTimes() {
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));

    // The player clicked twice and the second replay is a cache hit, so there is nothing to dedup.
    verify(dispatcher, times(2)).speakNarration("It's a bucket of milk.");
  }
}
