package com.grahambartley.runelite.voiced.dialogue.panel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class NpcListRowTest {

  private final ChatheadImages chatheads = PanelFixtures.offlineChatheads();

  @Test
  public void showsTheNameAndHowManyNpcsShareIt() {
    NpcListEntry entry = new NpcListEntry("Guard", Arrays.asList(1, 2, 3), false, false, 1);

    NpcListRow row = new NpcListRow(entry, chatheads, e -> {});

    assertEquals("Guard", row.nameText());
    assertEquals("3 NPCs", row.detailLabelText());
    assertEquals("", row.badgeText());
  }

  @Test
  public void aHeardEditedNpcSaysSo() {
    NpcListEntry entry =
        new NpcListEntry("Hans", Collections.singletonList(3105), true, true, 3105);

    NpcListRow row = new NpcListRow(entry, chatheads, e -> {});

    assertEquals("1 NPC · heard", row.detailLabelText());
    assertEquals("Edited", row.badgeText());
  }

  @Test
  public void showsAChatheadImmediately() {
    NpcListRow row = new NpcListRow(PanelFixtures.single("Hans", 3105), chatheads, e -> {});

    assertNotNull(row.chatheadLabel().getIcon());
    assertEquals(NpcListRow.CHATHEAD_SIZE, row.chatheadLabel().getIcon().getIconWidth());
  }

  @Test
  public void clickingOpensTheEntry() {
    NpcListEntry entry = PanelFixtures.single("Hans", 3105);
    List<NpcListEntry> opened = new ArrayList<>();
    NpcListRow row = new NpcListRow(entry, chatheads, opened::add);

    row.dispatchEvent(
        new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1));

    assertEquals(1, opened.size());
    assertSame(entry, opened.get(0));
  }

  @Test
  public void hoveringHighlightsTheRow() {
    NpcListRow row = new NpcListRow(PanelFixtures.single("Hans", 3105), chatheads, e -> {});
    Color resting = row.getBackground();

    row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_ENTERED, 0, 0, 5, 5, 0, false));
    Color hovered = row.getBackground();
    row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_EXITED, 0, 0, 5, 5, 0, false));

    assertNotEquals(resting, hovered);
    assertEquals(resting, row.getBackground());
  }
}
