package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.OverheadTextChanged;
import org.junit.Before;
import org.junit.Test;

public class AmbientChatterWatcherTest {

  private static final WorldPoint PLAYER_TILE = new WorldPoint(3200, 3200, 0);

  private final Client client = mock(Client.class);
  private final SynthesisDispatcher dispatcher = mock(SynthesisDispatcher.class);
  private final List<Runnable> completions = new ArrayList<>();

  private boolean enabled = true;
  private boolean conversationOnScreen = false;
  private long now = 1_000_000L;

  private final AmbientChatterWatcher watcher =
      new AmbientChatterWatcher(
          client,
          new DialogueTextCleaner(new ProfanityFilter()),
          dispatcher,
          () -> enabled,
          () -> conversationOnScreen,
          () -> now);

  @Before
  public void setUp() {
    Player local = mock(Player.class);
    when(local.getWorldLocation()).thenReturn(PLAYER_TILE);
    when(client.getLocalPlayer()).thenReturn(local);
    doAnswer(
            invocation -> {
              completions.add(invocation.getArgument(2));
              return null;
            })
        .when(dispatcher)
        .speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void aNearbyNpcsOverheadLineIsSpokenInItsOwnVoice() {
    NPC hans = npc(1, 3);

    watcher.onOverheadTextChanged(overhead(hans, "<col=ff0000>Lovely day!"));

    verify(dispatcher).speakAmbient(eq("Lovely day!"), eq(hans), any(Runnable.class));
  }

  @Test
  public void anotherPlayersOverheadTextIsNeverSpoken() {
    Player other = mock(Player.class);
    when(other.getWorldLocation()).thenReturn(PLAYER_TILE);

    watcher.onOverheadTextChanged(new OverheadTextChanged(other, "Selling whips"));

    verifyNothingSpoken();
  }

  @Test
  public void anNpcBeyondEarshotIsIgnored() {
    watcher.onOverheadTextChanged(
        overhead(npc(1, AmbientChatterWatcher.EARSHOT_TILES + 1), "Too far away"));

    verifyNothingSpoken();
  }

  @Test
  public void anNpcExactlyAtTheEarshotEdgeIsSpoken() {
    watcher.onOverheadTextChanged(
        overhead(npc(1, AmbientChatterWatcher.EARSHOT_TILES), "Just in range"));

    verify(dispatcher).speakAmbient(eq("Just in range"), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void anNpcOnAnotherPlaneIsIgnored() {
    NPC upstairs = mock(NPC.class);
    when(upstairs.getIndex()).thenReturn(1);
    when(upstairs.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 1));

    watcher.onOverheadTextChanged(overhead(upstairs, "Up here"));

    verifyNothingSpoken();
  }

  @Test
  public void nothingIsSpokenWhileAConversationIsOnScreen() {
    conversationOnScreen = true;

    watcher.onOverheadTextChanged(overhead(npc(1, 1), "Lovely day!"));

    verifyNothingSpoken();
  }

  @Test
  public void theToggleIsReadLiveSoTurningItOffSilencesTheNextLine() {
    enabled = false;

    watcher.onOverheadTextChanged(overhead(npc(1, 1), "Lovely day!"));

    verifyNothingSpoken();
  }

  @Test
  public void anOverheadLineArrivingWhileLoggedOutIsIgnored() {
    when(client.getLocalPlayer()).thenReturn(null);

    watcher.onOverheadTextChanged(overhead(npc(1, 1), "Lovely day!"));

    verifyNothingSpoken();
  }

  @Test
  public void aLineThatCleansAwayToNothingIsNotSpoken() {
    watcher.onOverheadTextChanged(overhead(npc(1, 1), "<col=ff0000>"));

    verifyNothingSpoken();
  }

  @Test
  public void aNullOverheadLineIsIgnored() {
    watcher.onOverheadTextChanged(overhead(npc(1, 1), null));

    verifyNothingSpoken();
  }

  @Test
  public void theSameNpcRepeatingWithinTheCooldownIsSpokenOnce() {
    NPC hans = npc(1, 1);

    watcher.onOverheadTextChanged(overhead(hans, "Lovely day!"));
    finishSpeaking();
    now += AmbientChatterWatcher.NPC_COOLDOWN_NANOS - 1;
    watcher.onOverheadTextChanged(overhead(hans, "Still a lovely day!"));

    verify(dispatcher, times(1)).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void theSameNpcSpeaksAgainOnceItsCooldownElapses() {
    NPC hans = npc(1, 1);

    watcher.onOverheadTextChanged(overhead(hans, "Lovely day!"));
    finishSpeaking();
    now += AmbientChatterWatcher.NPC_COOLDOWN_NANOS;
    watcher.onOverheadTextChanged(overhead(hans, "Still a lovely day!"));

    verify(dispatcher, times(2)).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void theCooldownIsTrackedPerNpcRatherThanGlobally() {
    watcher.onOverheadTextChanged(overhead(npc(1, 1), "Lovely day!"));
    finishSpeaking();
    watcher.onOverheadTextChanged(overhead(npc(2, 1), "Buying gold"));

    verify(dispatcher, times(2)).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void aCrowdOfChatteringNpcsYieldsOneLineUntilItFinishes() {
    for (int index = 1; index <= 12; index++) {
      watcher.onOverheadTextChanged(overhead(npc(index, 1), "Line " + index));
    }

    verify(dispatcher, times(1)).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void theNextNpcSpeaksAsSoonAsTheLineBeforeItFinishes() {
    watcher.onOverheadTextChanged(overhead(npc(1, 1), "Lovely day!"));
    watcher.onOverheadTextChanged(overhead(npc(2, 1), "Buying gold"));
    finishSpeaking();
    watcher.onOverheadTextChanged(overhead(npc(3, 1), "Fresh bread"));

    verify(dispatcher, times(2)).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void aLineThatNeverReportsFinishingReleasesTheSlotAtTheCeiling() {
    watcher.onOverheadTextChanged(overhead(npc(1, 1), "Lovely day!"));
    completions.clear();
    now += AmbientChatterWatcher.MAX_SLOT_HOLD_NANOS;
    watcher.onOverheadTextChanged(overhead(npc(2, 1), "Buying gold"));

    verify(dispatcher, times(2)).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  @Test
  public void aStaleCompletionDoesNotFreeTheSlotHeldByALaterLine() {
    watcher.onOverheadTextChanged(overhead(npc(1, 1), "Lovely day!"));
    Runnable stale = completions.remove(0);
    now += AmbientChatterWatcher.MAX_SLOT_HOLD_NANOS;
    watcher.onOverheadTextChanged(overhead(npc(2, 1), "Buying gold"));
    stale.run();
    watcher.onOverheadTextChanged(overhead(npc(3, 1), "Fresh bread"));

    verify(dispatcher, times(2)).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  private void finishSpeaking() {
    List<Runnable> pending = new ArrayList<>(completions);
    completions.clear();
    for (Runnable completion : pending) {
      completion.run();
    }
  }

  private void verifyNothingSpoken() {
    verify(dispatcher, never()).speakAmbient(anyString(), any(NPC.class), any(Runnable.class));
  }

  private static NPC npc(int index, int tilesAway) {
    NPC npc = mock(NPC.class);
    when(npc.getIndex()).thenReturn(index);
    when(npc.getWorldLocation())
        .thenReturn(
            new WorldPoint(
                PLAYER_TILE.getX() + tilesAway, PLAYER_TILE.getY(), PLAYER_TILE.getPlane()));
    return npc;
  }

  private static OverheadTextChanged overhead(Actor actor, String text) {
    return new OverheadTextChanged(actor, text);
  }
}
