package com.grahambartley.runelite.voiced.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuEntryAdded;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SetVoiceMenuTest {

  private static final int HANS = 3105;
  private static final int PROFILE_ID = 3106;
  private static final String TARGET = "<col=ffff00>Hans";

  private final Client client = mock(Client.class);
  private final Menu menu = mock(Menu.class);
  private final MenuEntry created = mock(MenuEntry.class, RETURNS_SELF);
  private final Map<NPC, Integer> profileIds = new HashMap<>();
  private final List<Runnable> uiQueue = new ArrayList<>();
  private final List<String> opened = new ArrayList<>();
  private boolean enabled = true;

  private final SetVoiceMenu setVoiceMenu =
      new SetVoiceMenu(
          client,
          () -> enabled,
          profileIds::get,
          uiQueue::add,
          (npcId, name) -> opened.add(npcId + ":" + name));

  public SetVoiceMenuTest() {
    when(client.getMenu()).thenReturn(menu);
    when(menu.createMenuEntry(anyInt())).thenReturn(created);
  }

  private static NPC npc(int id, String name) {
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(id);
    when(npc.getName()).thenReturn(name);
    return npc;
  }

  private static MenuEntryAdded added(MenuAction type, NPC npc) {
    MenuEntry entry = mock(MenuEntry.class);
    when(entry.getType()).thenReturn(type);
    when(entry.getNpc()).thenReturn(npc);
    when(entry.getTarget()).thenReturn(TARGET);
    when(entry.getIdentifier()).thenReturn(7);
    return new MenuEntryAdded(entry);
  }

  @SuppressWarnings("unchecked")
  private void clickCreatedEntry() {
    ArgumentCaptor<Consumer<MenuEntry>> onClick = ArgumentCaptor.forClass(Consumer.class);
    verify(created).onClick(onClick.capture());
    onClick.getValue().accept(created);
  }

  private void drainUi() {
    List<Runnable> queued = new ArrayList<>(uiQueue);
    uiQueue.clear();
    queued.forEach(Runnable::run);
  }

  @Test
  public void examiningAnNpcAddsSetVoiceBesideIt() {
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, npc(HANS, "Hans")));

    verify(menu).createMenuEntry(-1);
    verify(created).setOption("Set voice");
    verify(created).setTarget(TARGET);
    verify(created).setIdentifier(7);
    verify(created).setType(MenuAction.RUNELITE);
  }

  @Test
  public void otherNpcOptionsAddNothingSoEachNpcGetsOneEntry() {
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.NPC_FIRST_OPTION, npc(HANS, "Hans")));
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.NPC_SECOND_OPTION, npc(HANS, "Hans")));

    verify(menu, never()).createMenuEntry(anyInt());
  }

  @Test
  public void nonNpcEntriesAddNothing() {
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_OBJECT, null));
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.WALK, null));

    verify(menu, never()).createMenuEntry(anyInt());
  }

  @Test
  public void anExamineEntryWithNoNpcAddsNothing() {
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, null));

    verify(menu, never()).createMenuEntry(anyInt());
  }

  @Test
  public void turningTheSettingOffHidesTheEntryOnTheNextMenu() {
    enabled = false;
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, npc(HANS, "Hans")));
    verify(menu, never()).createMenuEntry(anyInt());

    enabled = true;
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, npc(HANS, "Hans")));
    verify(menu).createMenuEntry(-1);
  }

  @Test
  public void choosingSetVoiceOpensThatNpcOnTheUiThreadUnderItsProfileId() {
    NPC hans = npc(HANS, "Hans");
    profileIds.put(hans, PROFILE_ID);
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, hans));

    clickCreatedEntry();
    assertTrue("the panel is only touched on the UI thread", opened.isEmpty());
    drainUi();

    assertEquals(1, opened.size());
    assertEquals(PROFILE_ID + ":Hans", opened.get(0));
  }

  @Test
  public void anNpcWithNoResolvableIdOpensNothing() {
    NPC hans = npc(HANS, "Hans");
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, hans));

    clickCreatedEntry();
    drainUi();

    assertTrue(opened.isEmpty());
    assertTrue(uiQueue.isEmpty());
  }

  @Test
  public void aTaggedNameOpensUnderItsPlainName() {
    NPC hans = npc(HANS, "<col=ffff00>Hans</col> ");
    profileIds.put(hans, PROFILE_ID);
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, hans));

    clickCreatedEntry();
    drainUi();

    assertEquals(PROFILE_ID + ":Hans", opened.get(0));
  }

  @Test
  public void anNpcWithNoNameOpensNothing() {
    NPC nameless = npc(HANS, null);
    profileIds.put(nameless, PROFILE_ID);
    setVoiceMenu.onMenuEntryAdded(added(MenuAction.EXAMINE_NPC, nameless));

    clickCreatedEntry();
    drainUi();

    assertTrue(opened.isEmpty());
  }
}
