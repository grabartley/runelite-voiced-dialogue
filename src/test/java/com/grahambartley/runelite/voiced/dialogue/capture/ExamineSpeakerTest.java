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

/**
 * The examine policy: only the client's three examine chat types are voiced, only when the game
 * rather than a plugin wrote the line, never while a dialogue holds the audio channel, and never at
 * all while the toggle is off.
 *
 * <p>The decision is deferred by a client tick, so these tests drive that tick explicitly with
 * {@link #tick()}. That is not ceremony: the client stamps a plugin's line <em>after</em>
 * publishing it, so a test that stamped the node up front would be modelling a client that does not
 * exist. {@link #stampAfterPublishing} reproduces the real order.
 */
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

  /** Runs whatever the speaker deferred, standing in for the next client tick. */
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

  /**
   * Publishes {@code text} and only then marks the node as plugin-authored, which is the order
   * {@code ChatMessageManager} uses: it adds the message, and stamps the format message on the
   * following statement.
   */
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
    // The real sequence: the game prints the examine text, RuneLite's Examine plugin publishes a
    // price on the same chat type moments later, and only then is that line marked as its own.
    // Voicing the price would stop the flavour line mid-sentence.
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
    // Pins the reason for the delay by showing what happens without it: an otherwise identical
    // speaker that decides on arrival, before the client has marked the line, speaks it.
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
    // The gate is checked when the audio channel would be taken, not when the line arrived.
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
    // Not parameterized: JUnitParams trims each row, so a whitespace-only case would arrive here as
    // the empty string and quietly re-test the case beside it.
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

    // The player clicked twice and the second replay is a cache hit, so there is nothing to dedup.
    verify(dispatcher, times(2)).speakNarration("It's a bucket of milk.");
  }
}
