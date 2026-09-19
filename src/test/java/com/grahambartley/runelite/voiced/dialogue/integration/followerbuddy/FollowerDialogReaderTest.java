package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;

public class FollowerDialogReaderTest {

  private final OverlayManager overlayManager = mock(OverlayManager.class);
  private final List<Overlay> registered = new ArrayList<>();

  private final FollowerDialogReader reader =
      new FollowerDialogReader(overlayManager, StubDialog.class.getName());

  @Before
  public void setUp() {
    when(overlayManager.anyMatch(any()))
        .thenAnswer(
            call -> {
              Predicate<Overlay> predicate = call.getArgument(0);
              for (Overlay overlay : registered) {
                if (predicate.test(overlay)) {
                  return true;
                }
              }
              return false;
            });
  }

  @Test
  public void aClosedDialogHasNoLine() {
    register(new StubDialog());

    assertNull(reader.currentLine());
  }

  @Test
  public void theOpenPageIsRead() {
    StubDialog dialog = new StubDialog();
    dialog.openAt(new String[] {"Hello there.", "Fine weather."}, 1, false);
    register(dialog);

    FollowerDialogLine line = reader.currentLine();

    assertNotNull(line);
    assertEquals("Fine weather.", line.text());
    assertEquals(1, line.page());
    assertFalse("the follower is speaking this node", line.playerSpeaking());
  }

  @Test
  public void aPlayerNodeIsReportedAsThePlayerSpeaking() {
    StubDialog dialog = new StubDialog();
    dialog.openAt(new String[] {"How are you?"}, 0, true);
    register(dialog);

    assertTrue(reader.currentLine().playerSpeaking());
  }

  @Test
  public void aPageBeyondTheLastIsTheOptionsScreenAndCarriesNoLine() {
    StubDialog dialog = new StubDialog();
    dialog.openAt(new String[] {"Pick one."}, 1, false);
    register(dialog);

    assertNull(reader.currentLine());
  }

  @Test
  public void anEmptyOrMissingPageIsSkipped() {
    StubDialog dialog = new StubDialog();
    dialog.openAt(new String[] {""}, 0, false);
    register(dialog);
    assertNull(reader.currentLine());

    dialog.openAt(new String[] {null}, 0, false);
    assertNull(reader.currentLine());
  }

  @Test
  public void anAbsentDialogIsNotRescannedOnEveryTick() {
    for (int tick = 0; tick < FollowerDialogReader.RESCAN_TICKS; tick++) {
      assertNull(reader.currentLine());
    }

    verify(overlayManager, times(1)).anyMatch(any());
  }

  @Test
  public void aFollowerBuddyInstalledMidSessionIsPickedUpOnTheNextRescan() {
    assertNull(reader.currentLine());

    StubDialog dialog = new StubDialog();
    dialog.openAt(new String[] {"Hello there."}, 0, false);
    register(dialog);
    for (int tick = 0; tick < FollowerDialogReader.RESCAN_TICKS; tick++) {
      reader.currentLine();
    }

    assertNotNull(reader.currentLine());
  }

  @Test
  public void anOverlayWhoseLayoutHasMovedSilencesTheDialogForGood() {
    FollowerDialogReader moved =
        new FollowerDialogReader(overlayManager, MovedDialog.class.getName());
    register(new MovedDialog());

    assertNull(moved.currentLine());

    for (int tick = 0; tick <= FollowerDialogReader.RESCAN_TICKS; tick++) {
      assertNull("a reader that gave up never reads again", moved.currentLine());
    }
    verify(overlayManager, times(1)).anyMatch(any());
  }

  @Test
  public void theProductionReaderLooksForFollowerBuddysOwnDialogClass() {
    assertEquals("com.follower.speech.FollowerDialog", FollowerDialogReader.DIALOG_CLASS);
  }

  @Test
  public void anUnrelatedOverlayIsNeverMistakenForTheDialog() {
    register(new UnrelatedOverlay());

    assertNull(reader.currentLine());
  }

  private void register(Overlay overlay) {
    registered.add(overlay);
  }

  private static final class UnrelatedOverlay extends Overlay {
    @Override
    public Dimension render(Graphics2D graphics) {
      return null;
    }
  }

  private static final class StubNode {
    private final boolean playerSpeaking;

    StubNode(boolean playerSpeaking) {
      this.playerSpeaking = playerSpeaking;
    }
  }

  private static final class StubDialog extends Overlay {
    private boolean open;
    private String[] pages = new String[0];
    private int page;
    private StubNode node;

    void openAt(String[] lines, int index, boolean playerSpeaking) {
      this.open = true;
      this.pages = lines;
      this.page = index;
      this.node = new StubNode(playerSpeaking);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
      return null;
    }
  }

  private static final class MovedDialog extends Overlay {
    private boolean shown;

    @Override
    public Dimension render(Graphics2D graphics) {
      return shown ? null : null;
    }
  }
}
