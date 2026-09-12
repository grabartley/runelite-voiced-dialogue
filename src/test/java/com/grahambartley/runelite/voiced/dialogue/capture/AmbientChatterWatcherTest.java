package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.profile.ProfanityFilter;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
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

  private boolean enabled = true;
  private boolean conversationOnScreen = false;
  private int earshotTiles = 20;

  private final AmbientChatterWatcher watcher =
      new AmbientChatterWatcher(
          client,
          new DialogueTextCleaner(new ProfanityFilter()),
          dispatcher,
          () -> enabled,
          () -> conversationOnScreen,
          () -> earshotTiles);

  @Before
  public void setUp() {
    Player local = mock(Player.class);
    when(local.getWorldLocation()).thenReturn(PLAYER_TILE);
    when(client.getLocalPlayer()).thenReturn(local);
  }

  @Test
  public void aNearbyNpcsOverheadLineIsSpokenInItsOwnVoice() {
    NPC hans = npc(3);

    watcher.onOverheadTextChanged(overhead(hans, "<col=ff0000>Lovely day!"));

    verify(dispatcher).speakAmbient("Lovely day!", hans);
  }

  @Test
  public void everyNpcInAChatteringCrowdIsVoiced() {
    for (int tilesAway = 1; tilesAway <= 12; tilesAway++) {
      watcher.onOverheadTextChanged(overhead(npc(tilesAway), "Line " + tilesAway));
    }

    verify(dispatcher, times(12)).speakAmbient(anyString(), any(NPC.class));
  }

  @Test
  public void oneNpcRepeatingItselfIsVoicedEveryTime() {
    NPC crier = npc(2);

    watcher.onOverheadTextChanged(overhead(crier, "Hear ye!"));
    watcher.onOverheadTextChanged(overhead(crier, "Hear ye!"));
    watcher.onOverheadTextChanged(overhead(crier, "Come one, come all!"));

    verify(dispatcher, times(2)).speakAmbient(eq("Hear ye!"), eq(crier));
    verify(dispatcher).speakAmbient("Come one, come all!", crier);
  }

  @Test
  public void anotherPlayersOverheadTextIsNeverSpoken() {
    Player other = mock(Player.class);
    when(other.getWorldLocation()).thenReturn(PLAYER_TILE);

    watcher.onOverheadTextChanged(overhead(other, "Selling whips"));

    verifyNothingSpoken();
  }

  @Test
  public void anNpcBeyondTheConfiguredRangeIsIgnored() {
    watcher.onOverheadTextChanged(overhead(npc(earshotTiles + 1), "Too far away"));

    verifyNothingSpoken();
  }

  @Test
  public void anNpcExactlyAtTheConfiguredRangeIsSpoken() {
    watcher.onOverheadTextChanged(overhead(npc(earshotTiles), "Just in range"));

    verify(dispatcher).speakAmbient(eq("Just in range"), any(NPC.class));
  }

  @Test
  public void theRangeIsReadLiveSoWideningItReachesFurtherAtOnce() {
    NPC distant = npc(35);

    watcher.onOverheadTextChanged(overhead(distant, "Faint shout"));
    earshotTiles = 50;
    watcher.onOverheadTextChanged(overhead(distant, "Faint shout"));

    verify(dispatcher, times(1)).speakAmbient(anyString(), any(NPC.class));
  }

  @Test
  public void anNpcOnAnotherPlaneIsIgnored() {
    NPC upstairs = mock(NPC.class);
    when(upstairs.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 1));

    watcher.onOverheadTextChanged(overhead(upstairs, "Up here"));

    verifyNothingSpoken();
  }

  @Test
  public void nothingIsSpokenWhileAConversationIsOnScreen() {
    conversationOnScreen = true;

    watcher.onOverheadTextChanged(overhead(npc(1), "Lovely day!"));

    verifyNothingSpoken();
  }

  @Test
  public void theToggleIsReadLiveSoTurningItOffSilencesTheNextLine() {
    enabled = false;

    watcher.onOverheadTextChanged(overhead(npc(1), "Lovely day!"));

    verifyNothingSpoken();
  }

  @Test
  public void anOverheadLineArrivingWhileLoggedOutIsIgnored() {
    when(client.getLocalPlayer()).thenReturn(null);

    watcher.onOverheadTextChanged(overhead(npc(1), "Lovely day!"));

    verifyNothingSpoken();
  }

  @Test
  public void aLineThatCleansAwayToNothingIsNotSpoken() {
    watcher.onOverheadTextChanged(overhead(npc(1), "<col=ff0000>"));

    verifyNothingSpoken();
  }

  @Test
  public void aNullOverheadLineIsIgnored() {
    watcher.onOverheadTextChanged(overhead(npc(1), null));

    verifyNothingSpoken();
  }

  private void verifyNothingSpoken() {
    verify(dispatcher, never()).speakAmbient(anyString(), any(NPC.class));
  }

  private static NPC npc(int tilesAway) {
    NPC npc = mock(NPC.class);
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
