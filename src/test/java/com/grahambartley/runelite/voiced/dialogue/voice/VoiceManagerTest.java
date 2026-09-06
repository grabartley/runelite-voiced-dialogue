package com.grahambartley.runelite.voiced.dialogue.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.data.NpcDemographicAnalyzer;
import com.grahambartley.runelite.voiced.dialogue.data.NpcProfileTable;
import com.grahambartley.runelite.voiced.dialogue.synthesis.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager.PlayerVoice;
import java.util.Collections;
import net.runelite.api.Client;
import org.junit.Test;

/**
 * The {@link VoiceManager} facade: player resolution, the NPC default-voice path, and the character
 * profile gate. Trace formatting, name normalisation, demographic parsing, NPC lookup, identity
 * resolution, and NPC voice resolution have their own tests.
 */
public class VoiceManagerTest {

  /**
   * Minimal config used to drive resolution without the RuneLite client. Only the toggles the
   * mapping reads are overridden; everything else keeps its interface default.
   */
  private static final class TestConfig implements VoicedDialogueConfig {
    private final PlayerVoice playerVoice;
    private final boolean characterProfiles;

    TestConfig(PlayerVoice playerVoice, boolean characterProfiles) {
      this.playerVoice = playerVoice;
      this.characterProfiles = characterProfiles;
    }

    @Override
    public PlayerVoice playerVoice() {
      return playerVoice;
    }

    @Override
    public boolean cloudCharacterProfiles() {
      return characterProfiles;
    }
  }

  private VoiceManager newManager(PlayerVoice playerVoice, boolean characterProfiles) {
    // An empty world list means the NPC is never found, so NPC lookups exercise the default-voice
    // path without needing a live game world.
    Client client = mock(Client.class);
    when(client.getNpcs()).thenReturn(Collections.emptyList());
    NpcProfileTable profileTable = new NpcProfileTable();
    profileTable.initialize();
    return new VoiceManager(
        new TestConfig(playerVoice, characterProfiles),
        client,
        new NpcDemographicAnalyzer(),
        profileTable);
  }

  @Test
  public void playerVoiceTypesFixGender() {
    assertEquals(NpcGender.MALE, PlayerVoice.TYPE_A.getGender());
    assertEquals(NpcGender.FEMALE, PlayerVoice.TYPE_B.getGender());
  }

  // ---- Player resolution ----

  @Test
  public void playerResolvesToPlayerSpecWithConfiguredGender() {
    VoiceSpec spec = newManager(PlayerVoice.TYPE_B, true).resolve("player", null).voice();
    assertTrue("player voice should be a player spec", spec.player());
    assertEquals(NpcGender.FEMALE, spec.gender());
    assertEquals("player:FEMALE", spec.key());
    assertFalse("the player carries no per-NPC variety seed", spec.hasVoiceSeed());
  }

  @Test
  public void playerSpeakerMatchingIsCaseInsensitive() {
    VoiceSpec spec = newManager(PlayerVoice.TYPE_A, true).resolve("PLAYER", null).voice();
    assertTrue(spec.player());
    assertEquals(NpcGender.MALE, spec.gender());
  }

  // ---- NPC default-voice path ----

  @Test
  public void undetectedNpcResolvesToTheDefaultHumanMaleVoice() {
    // The NPC is not in the world, so detection resolves to the default human-male voice.
    VoiceSpec spec = newManager(PlayerVoice.TYPE_A, true).resolve("npc", "Hans").voice();
    assertFalse(spec.player());
    assertEquals(NpcRace.HUMAN, spec.race());
    assertEquals(NpcGender.MALE, spec.gender());
    assertTrue("default-voice NPC still gets a per-NPC variety seed", spec.hasVoiceSeed());
    assertEquals("npc:HUMAN:MALE", spec.key());
  }

  // ---- Character profile gate ----

  @Test
  public void profilesResolveForBothSpeakersWhenEnabled() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A, true);
    assertNotNull(manager.resolve(VoiceManager.SPEAKER_PLAYER, null).profile());
    assertNotNull(manager.resolve(VoiceManager.SPEAKER_NPC, "Hans").profile());
  }

  @Test
  public void noProfileIsResolvedWhenCharacterProfilesAreOff() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A, false);
    assertNull(manager.resolve(VoiceManager.SPEAKER_PLAYER, null).profile());
    assertNull(manager.resolve(VoiceManager.SPEAKER_NPC, "Hans").profile());
    assertNotNull("the voice is still resolved", manager.resolve("npc", "Hans").voice());
  }
}
