package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.ArrayDeque;
import java.util.Deque;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class ExamineSpeakerTest {

  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);
  private final Deque<Runnable> deferred = new ArrayDeque<>();

  private boolean enabled = true;
  private boolean dialogueOpen = false;

  private final ExamineSpeaker speaker =
      new ExamineSpeaker(
          new DialogueTextCleaner(new ProfanityFilter()),
          dispatcher,
          () -> enabled,
          () -> dialogueOpen,
          deferred::add);

  private void tick() {
    while (!deferred.isEmpty()) {
      deferred.poll().run();
    }
  }

  private static ChatMessage message(ChatMessageType type, String text) {
    ChatMessage event = new ChatMessage();
    event.setType(type);
    event.setMessage(text);
    event.setMessageNode(mock(MessageNode.class));
    return event;
  }

  private void stampAfterPublishing(ChatMessageType type, String text) {
    ChatMessage event = message(type, text);
    speaker.onChatMessage(event);
    when(event.getMessageNode().getRuneLiteFormatMessage()).thenReturn(text);
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
    tick();

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
    tick();

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void aPluginsPriceLookupCannotTalkOverTheExamineTextItFollows() {
    speaker.onChatMessage(
        message(ChatMessageType.ITEM_EXAMINE, "4 doses of Prayer restore potion."));
    stampAfterPublishing(ChatMessageType.ITEM_EXAMINE, "Price of Prayer potion(4): 9,500 coins");
    tick();

    verify(dispatcher, times(1)).speakNarration("4 doses of Prayer restore potion.");
    verifyNoMoreInteractions(dispatcher);
  }

  @Test
  public void aLineMarkedOnlyAfterPublishingIsStillRecognisedAsAPluginsOwn() {
    stampAfterPublishing(ChatMessageType.NPC_EXAMINE, "Some plugin's annotation.");
    tick();

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void decidingBeforeTheTickWouldVoiceAPluginsLine() {
    SynthesisDispatcher undeferredDispatcher = mock(SynthesisDispatcher.class);
    ExamineSpeaker undeferred =
        new ExamineSpeaker(
            new DialogueTextCleaner(new ProfanityFilter()),
            undeferredDispatcher,
            () -> true,
            () -> false,
            Runnable::run);

    ChatMessage event = message(ChatMessageType.ITEM_EXAMINE, "Price of something: 1 coin");
    undeferred.onChatMessage(event);
    when(event.getMessageNode().getRuneLiteFormatMessage())
        .thenReturn("Price of something: 1 coin");

    verify(undeferredDispatcher).speakNarration("Price of something: 1 coin");
  }

  @Test
  public void aNodelessEventIsTreatedAsGameAuthored() {
    ChatMessage event = new ChatMessage();
    event.setType(ChatMessageType.OBJECT_EXAMINE);
    event.setMessage("A beautiful old oak.");

    speaker.onChatMessage(event);
    tick();

    verify(dispatcher).speakNarration("A beautiful old oak.");
  }

  @Test
  public void switchedOffNothingIsVoicedAndNothingIsEvenQueued() {
    enabled = false;

    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));

    assertTrue("an ignored examine does not queue work for the next tick", deferred.isEmpty());
    tick();
    verifyNoInteractions(dispatcher);
  }

  @Test
  public void switchingItBackOnTakesEffectOnTheNextExamine() {
    enabled = false;
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));
    enabled = true;
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));
    tick();

    verify(dispatcher, times(1)).speakNarration("It's a bucket of milk.");
  }

  @Test
  public void anOpenDialogueKeepsTheAudioChannel() {
    dialogueOpen = true;

    speaker.onChatMessage(message(ChatMessageType.NPC_EXAMINE, "A gruff dwarf."));
    tick();

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void aDialogueOpeningDuringTheDeferralStillWins() {
    speaker.onChatMessage(message(ChatMessageType.NPC_EXAMINE, "A gruff dwarf."));
    dialogueOpen = true;
    tick();

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void examineResumesOnceTheDialogueCloses() {
    dialogueOpen = true;
    speaker.onChatMessage(message(ChatMessageType.NPC_EXAMINE, "A gruff dwarf."));
    tick();
    dialogueOpen = false;
    speaker.onChatMessage(message(ChatMessageType.NPC_EXAMINE, "A gruff dwarf."));
    tick();

    verify(dispatcher, times(1)).speakNarration("A gruff dwarf.");
  }

  @Test
  public void markupIsStrippedBeforeVoicing() {
    speaker.onChatMessage(
        message(ChatMessageType.OBJECT_EXAMINE, "<col=ffffff>A gnarled old tree.</col>"));
    tick();

    verify(dispatcher).speakNarration("A gnarled old tree.");
  }

  @Test
  public void textThatCleansAwayToNothingIsNotVoiced() {
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, ""));
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "   "));
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "<col=ffffff></col>"));
    tick();

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void aNullMessageIsIgnoredRatherThanThrowing() {
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, null));
    tick();

    verifyNoInteractions(dispatcher);
  }

  @Test
  public void examiningTheSameThingTwiceVoicesItBothTimes() {
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));
    tick();
    speaker.onChatMessage(message(ChatMessageType.ITEM_EXAMINE, "It's a bucket of milk."));
    tick();

    verify(dispatcher, times(2)).speakNarration("It's a bucket of milk.");
  }
}
