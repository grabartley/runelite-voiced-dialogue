package com.grahambartley.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.grahambartley.VoicedDialogueConfig;
import com.grahambartley.synthesis.VoiceSpec;
import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import com.grahambartley.voice.VoiceManager.PlayerVoice;
import org.junit.Test;

/**
 * The {@link VoiceManager} facade: player voice resolution and the NPC default-voice path. Trace
 * formatting, name normalisation, demographic parsing, NPC lookup, and NPC voice resolution have
 * their own tests.
 */
public class VoiceManagerTest {

  /**
   * Minimal config used to drive voice resolution without the RuneLite client. Only the toggles the
   * mapping reads are overridden; everything else keeps its interface default.
   */
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

  private VoiceManager newManager(PlayerVoice playerVoice) {
    // A null client means the NPC is never found, so NPC lookups exercise the default-voice path
    // without needing a live game world.
    return new VoiceManager(new TestConfig(playerVoice), null);
  }

  @Test
  public void playerVoiceTypesFixGender() {
    assertEquals(NPCGender.MALE, PlayerVoice.TYPE_A.getGender());
    assertEquals(NPCGender.FEMALE, PlayerVoice.TYPE_B.getGender());
  }

  // ---- Player resolution ----

  @Test
  public void playerResolvesToPlayerSpecWithConfiguredGender() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_B);
    VoiceSpec spec = manager.resolveVoice("player", null);
    assertTrue("player voice should be a player spec", spec.player());
    assertEquals(NPCGender.FEMALE, spec.gender());
    assertEquals("player:FEMALE", spec.key());
    assertFalse("the player carries no per-NPC variety seed", spec.hasVoiceSeed());
  }

  @Test
  public void playerSpeakerMatchingIsCaseInsensitive() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    VoiceSpec spec = manager.resolveVoice("PLAYER", null);
    assertTrue(spec.player());
    assertEquals(NPCGender.MALE, spec.gender());
  }

  // ---- NPC default-voice path ----

  @Test
  public void undetectedNpcResolvesToTheDefaultHumanMaleVoice() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    // No client, so the NPC can't be found and detection resolves to the default human-male voice.
    VoiceSpec spec = manager.resolveVoice("npc", "Hans");
    assertFalse(spec.player());
    assertEquals(NPCRace.HUMAN, spec.race());
    assertEquals(NPCGender.MALE, spec.gender());
    assertTrue("default-voice NPC still gets a per-NPC variety seed", spec.hasVoiceSeed());
    assertEquals("npc:HUMAN:MALE", spec.key());
  }
}
