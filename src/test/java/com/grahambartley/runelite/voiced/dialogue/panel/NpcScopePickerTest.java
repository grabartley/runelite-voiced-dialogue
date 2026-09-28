package com.grahambartley.runelite.voiced.dialogue.panel;

import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.GUARD;
import static com.grahambartley.runelite.voiced.dialogue.panel.PanelFixtures.HANS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceScope;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class NpcScopePickerTest {

  private static final List<Integer> GUARD_IDS =
      Arrays.asList(3094, 11911, 11912, 11913, 11914, 11915, 11916, 11917);

  private final AtomicInteger changes = new AtomicInteger();
  private final NpcScopePicker picker =
      new NpcScopePicker(PanelFixtures.catalog(), changes::incrementAndGet);

  @Test
  public void startsOnThisNpcAlone() {
    picker.show(GUARD, "Guard", GUARD_IDS);

    assertEquals(NpcVoiceScope.THIS_NPC, picker.selected());
    assertEquals(Collections.singletonList(GUARD), picker.selectedIds());
  }

  @Test
  public void eachOptionShowsItsNpcCountBeforeSaving() {
    picker.show(GUARD, "Guard", GUARD_IDS);

    assertEquals("Only this NPC (1)", picker.button(NpcVoiceScope.THIS_NPC).getText());
    assertEquals(
        "This character and its variants (6)",
        picker.button(NpcVoiceScope.THIS_CHARACTER).getText());
    assertEquals("Everyone called \"Guard\" (8)", picker.button(NpcVoiceScope.SAME_NAME).getText());
  }

  @Test
  public void thisCharacterOnAVarrockGuardIsTheSixVariants() {
    picker.show(GUARD, "Guard", GUARD_IDS);
    picker.select(NpcVoiceScope.THIS_CHARACTER);

    assertEquals(Arrays.asList(11911, 11912, 11913, 11914, 11915, 11916), picker.selectedIds());
  }

  @Test
  public void optionsThatWouldOnlyCoverThisNpcAreDisabled() {
    picker.show(HANS, "Hans", Collections.singletonList(HANS));

    assertTrue(picker.button(NpcVoiceScope.THIS_NPC).isEnabled());
    assertFalse(picker.button(NpcVoiceScope.THIS_CHARACTER).isEnabled());
    assertFalse(picker.button(NpcVoiceScope.SAME_NAME).isEnabled());
  }

  @Test
  public void aSelectionThatBecomesDisabledFallsBackToThisNpc() {
    picker.show(GUARD, "Guard", GUARD_IDS);
    picker.select(NpcVoiceScope.SAME_NAME);

    picker.show(HANS, "Hans", Collections.singletonList(HANS));

    assertEquals(NpcVoiceScope.THIS_NPC, picker.selected());
    assertEquals(Collections.singletonList(HANS), picker.selectedIds());
  }

  @Test
  public void pickingAnOptionNotifies() {
    picker.show(GUARD, "Guard", GUARD_IDS);

    picker.button(NpcVoiceScope.SAME_NAME).doClick();

    assertEquals(1, changes.get());
    assertEquals(NpcVoiceScope.SAME_NAME, picker.selected());
  }

  @Test
  public void selectedIdsBeforeShowingAnythingIsEmpty() {
    assertTrue(picker.selectedIds().isEmpty());
  }

  @Test
  public void everyoneCalledCoversExactlyTheIdsTheRowShows() {
    picker.show(GUARD, "Guard", Arrays.asList(GUARD, 424242));
    picker.select(NpcVoiceScope.SAME_NAME);

    assertEquals(Arrays.asList(GUARD, 424242), picker.selectedIds());
    assertEquals("Everyone called \"Guard\" (2)", picker.button(NpcVoiceScope.SAME_NAME).getText());
  }
}
