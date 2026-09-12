package com.grahambartley.runelite.voiced.dialogue.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager.PlayerVoice;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import org.junit.Test;

public class VoiceManagerTest {

  private static final class TestConfig implements VoicedDialogueConfig {
    private final PlayerVoice playerVoice;

    TestConfig(PlayerVoice playerVoice) {
      this.playerVoice = playerVoice;
    }

    @Override
    public PlayerVoice playerVoice() {
      return playerVoice;
    }
  }

  private static final int DWARF_ID = 290;

  private VoiceManager newManager(PlayerVoice playerVoice) {
    Client client = mock(Client.class);
    when(client.getNpcs()).thenReturn(Collections.emptyList());
    NpcProfileTable profileTable = new NpcProfileTable();
    profileTable.initialize();
    return new VoiceManager(
        new TestConfig(playerVoice), client, new NpcDemographicAnalyzer(), profileTable);
  }

  private VoiceManager newManager(Client client, PlayerVoice playerVoice) {
    NpcDemographicAnalyzer demographicAnalyzer = new NpcDemographicAnalyzer();
    demographicAnalyzer.initialize();
    NpcProfileTable profileTable = new NpcProfileTable();
    profileTable.initialize();
    return new VoiceManager(new TestConfig(playerVoice), client, demographicAnalyzer, profileTable);
  }

  @Test
  public void playerVoiceTypesFixGender() {
    assertEquals(NpcGender.MALE, PlayerVoice.TYPE_A.getGender());
    assertEquals(NpcGender.FEMALE, PlayerVoice.TYPE_B.getGender());
  }

  @Test
  public void playerResolvesToPlayerSpecWithConfiguredGender() {
    VoiceSpec spec = newManager(PlayerVoice.TYPE_B).resolve(Speaker.PLAYER, null).voice();
    assertTrue("player voice should be a player spec", spec.player());
    assertEquals(NpcGender.FEMALE, spec.gender());
    assertEquals("player:FEMALE", spec.key());
    assertFalse("the player carries no per-NPC variety seed", spec.hasVoiceSeed());
  }

  @Test
  public void undetectedNpcResolvesToTheDefaultHumanMaleVoice() {
    VoiceSpec spec = newManager(PlayerVoice.TYPE_A).resolve(Speaker.NPC, "Hans").voice();
    assertFalse(spec.player());
    assertEquals(NpcRace.HUMAN, spec.race());
    assertEquals(NpcGender.MALE, spec.gender());
    assertTrue("default-voice NPC still gets a per-NPC variety seed", spec.hasVoiceSeed());
    assertEquals("npc:HUMAN:MALE", spec.key());
  }

  @Test
  public void narratorResolvesToTheFixedNarratorSpec() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_B);

    VoiceSpec spec = manager.resolveNarrator().voice();
    assertTrue("the narrator is its own speaker class", spec.narrator());
    assertEquals("narrator", spec.key());
    assertEquals(
        "the player voice setting must not move the narrator",
        spec,
        newManager(PlayerVoice.TYPE_A).resolveNarrator().voice());
  }

  @Test
  public void everySpeakerAlwaysResolvesToACharacterProfile() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    assertNotNull(
        "the player always carries a profile", manager.resolve(Speaker.PLAYER, null).profile());
    assertNotNull(
        "an NPC always carries a profile", manager.resolve(Speaker.NPC, "Hans").profile());
    assertNotNull("the narrator always carries a profile", manager.resolveNarrator().profile());
  }

  @Test
  public void anUndetectedNpcStillResolvesToTheDefaultProfileRatherThanNone() {
    CharacterProfile profile =
        newManager(PlayerVoice.TYPE_A).resolve(Speaker.NPC, "Hans").profile();
    assertNotNull(profile);
    assertFalse("the default profile still names a voice", profile.name().trim().isEmpty());
    assertFalse("the default profile still carries an accent", profile.accent().trim().isEmpty());
  }

  @Test
  public void anNpcHandedInDirectlyResolvesFromItsOwnIdWithoutAWorldScan() {
    Client client = mock(Client.class);
    when(client.getNpcs()).thenReturn(Collections.emptyList());
    VoiceManager manager = newManager(client, PlayerVoice.TYPE_A);

    VoiceSpec spec = manager.resolveNpc(worldNpc(DWARF_ID, "Dwarf")).voice();

    assertEquals(NpcRace.DWARF, spec.race());
    assertEquals(NpcGender.MALE, spec.gender());
    verify(client, never()).getNpcs();
  }

  @Test
  public void theDirectNpcPathResolvesExactlyAsTheNameLookupDoes() {
    NPC dwarf = worldNpc(DWARF_ID, "Dwarf");
    Client client = mock(Client.class);
    when(client.getNpcs()).thenReturn(Collections.singletonList(dwarf));
    VoiceManager manager = newManager(client, PlayerVoice.TYPE_A);

    assertEquals(manager.resolve(Speaker.NPC, "Dwarf"), manager.resolveNpc(dwarf));
  }

  private static NPC worldNpc(int npcId, String name) {
    NPCComposition composition = mock(NPCComposition.class);
    when(composition.getId()).thenReturn(npcId);
    when(composition.getName()).thenReturn(name);
    NPC npc = mock(NPC.class);
    when(npc.getId()).thenReturn(npcId);
    when(npc.getName()).thenReturn(name);
    when(npc.getComposition()).thenReturn(composition);
    return npc;
  }
}
