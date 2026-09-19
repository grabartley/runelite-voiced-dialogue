package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.capture.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import org.junit.Test;

public class FollowerDialogWatcherTest {

  private final FollowerDialogReader reader = mock(FollowerDialogReader.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);

  private boolean enabled = true;
  private NpcGender gender = NpcGender.MALE;

  private final FollowerDialogWatcher watcher =
      new FollowerDialogWatcher(
          reader,
          new DialogueTextCleaner(new ProfanityFilter()),
          dispatcher,
          () -> enabled,
          () -> gender);

  @Test
  public void aFollowerPageIsSpokenInTheCompanionVoice() {
    showing(new FollowerDialogLine("<col=0000ff>Hello there.", 0, false));

    watcher.tick();

    verify(dispatcher).speakFollowerDialogue("Hello there.", NpcGender.MALE);
  }

  @Test
  public void aPlayerPageIsSpokenInThePlayerVoice() {
    showing(new FollowerDialogLine("How are you?", 0, true));

    watcher.tick();

    verify(dispatcher).speakPlayerDialogue("How are you?");
    verify(dispatcher, never()).speakFollowerDialogue(anyString(), any());
  }

  @Test
  public void aPageStillOnScreenIsNotSpokenAgainEveryTick() {
    showing(new FollowerDialogLine("Hello there.", 0, false));

    watcher.tick();
    watcher.tick();
    watcher.tick();

    verify(dispatcher, times(1)).speakFollowerDialogue("Hello there.", NpcGender.MALE);
  }

  @Test
  public void turningThePageSpeaksTheNextLine() {
    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();

    showing(new FollowerDialogLine("Fine weather.", 1, false));
    watcher.tick();

    verify(dispatcher).speakFollowerDialogue("Hello there.", NpcGender.MALE);
    verify(dispatcher).speakFollowerDialogue("Fine weather.", NpcGender.MALE);
  }

  @Test
  public void thePageThatOpensTheNextNodeIsSpokenEvenThoughItsIndexRepeats() {
    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();

    showing(new FollowerDialogLine("A new subject.", 0, false));
    watcher.tick();

    verify(dispatcher).speakFollowerDialogue("A new subject.", NpcGender.MALE);
  }

  @Test
  public void reopeningOnTheSameLineSpeaksItAgain() {
    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();

    showing(null);
    watcher.tick();

    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();

    verify(dispatcher, times(2)).speakFollowerDialogue("Hello there.", NpcGender.MALE);
  }

  @Test
  public void withTheFeatureOffTheDialogIsNeverRead() {
    enabled = false;

    watcher.tick();

    verifyNoInteractions(reader);
    verifyNoInteractions(dispatcher);
  }

  @Test
  public void switchingTheFeatureOffMidConversationSpeaksTheLineAgainWhenItComesBack() {
    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();

    enabled = false;
    watcher.tick();

    enabled = true;
    watcher.tick();

    verify(dispatcher, times(2)).speakFollowerDialogue("Hello there.", NpcGender.MALE);
  }

  @Test
  public void aPageThatCleansAwayToNothingIsNotSpoken() {
    showing(new FollowerDialogLine("<img=2>", 0, false));

    watcher.tick();

    verify(dispatcher, never()).speakFollowerDialogue(anyString(), any());
  }

  @Test
  public void theResolvedGenderReachesTheDispatcher() {
    gender = NpcGender.FEMALE;
    showing(new FollowerDialogLine("Hello there.", 0, false));

    watcher.tick();

    verify(dispatcher).speakFollowerDialogue("Hello there.", NpcGender.FEMALE);
  }

  @Test
  public void anOpenDialogIsReportedOnScreenSoOverheadChatterHoldsBack() {
    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();
    assertTrue(watcher.isOnScreen());

    showing(null);
    watcher.tick();
    assertFalse(watcher.isOnScreen());
  }

  @Test
  public void theOnScreenAnswerIsTheTicksOwnReadRatherThanAFreshReflection() {
    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();

    watcher.isOnScreen();
    watcher.isOnScreen();
    watcher.isOnScreen();

    verify(reader, times(1)).currentLine();
  }

  @Test
  public void switchingTheFeatureOffClearsTheOnScreenAnswer() {
    showing(new FollowerDialogLine("Hello there.", 0, false));
    watcher.tick();

    enabled = false;
    watcher.tick();

    assertFalse(watcher.isOnScreen());
  }

  private void showing(FollowerDialogLine line) {
    when(reader.currentLine()).thenReturn(line);
  }
}
