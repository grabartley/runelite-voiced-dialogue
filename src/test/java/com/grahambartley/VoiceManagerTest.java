package com.grahambartley;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.grahambartley.VoiceManager.KokoroVoice;
import com.grahambartley.VoiceManager.NPCGender;
import com.grahambartley.VoiceManager.NPCRace;
import com.grahambartley.VoiceManager.PlayerVoice;
import com.grahambartley.synthesis.VoiceSpec;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class VoiceManagerTest {

  /**
   * Minimal config used to drive voice resolution without the RuneLite client. Only the toggles the
   * mapping reads are overridden; everything else keeps its interface default.
   */
  private static final class TestConfig implements TTSDialogueConfig {
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
    // A null client means findNPCByName returns null, so NPC lookups exercise the default-voice
    // path without needing a live game world.
    return new VoiceManager(new TestConfig(playerVoice), null);
  }

  // ---- British voice bank ----

  @Test
  public void bankIsBritishOnlyWithDistinctInRangeIds() {
    Set<Integer> ids = new HashSet<>();
    for (KokoroVoice voice : KokoroVoice.values()) {
      assertTrue(
          voice + " must be a British (bm_/bf_) voice", voice.getVoiceName().startsWith("b"));
      // The kokoro-multi-lang-v1_0 English voices occupy speaker ids 0-27.
      int id = voice.getSpeakerId();
      assertTrue(voice + " id out of range: " + id, id >= 0 && id <= 27);
      assertTrue(voice + " has a duplicate speaker id", ids.add(id));
    }
  }

  @Test
  public void everyVoiceNamesItsKokoroVoice() {
    for (KokoroVoice voice : KokoroVoice.values()) {
      assertTrue(voice + " missing kokoro voice name", voice.getVoiceName().length() > 0);
    }
    assertEquals("bm_george", KokoroVoice.BM_GEORGE.getVoiceName());
    assertEquals("bf_emma", KokoroVoice.BF_EMMA.getVoiceName());
  }

  @Test
  public void genderPoolsAreNonEmptyDisjointBritishAndGenderCorrect() {
    assertTrue("male pool should not be empty", VoiceManager.MALE_SPEAKER_POOL.length > 0);
    assertTrue("female pool should not be empty", VoiceManager.FEMALE_SPEAKER_POOL.length > 0);
    for (int id : VoiceManager.MALE_SPEAKER_POOL) {
      assertEquals("male pool id " + id + " must be a male voice", NPCGender.MALE, genderOf(id));
      assertTrue("male pool id " + id + " must be British", isBritish(id));
    }
    for (int id : VoiceManager.FEMALE_SPEAKER_POOL) {
      assertEquals(
          "female pool id " + id + " must be a female voice", NPCGender.FEMALE, genderOf(id));
      assertTrue("female pool id " + id + " must be British", isBritish(id));
    }
    for (int m : VoiceManager.MALE_SPEAKER_POOL) {
      assertFalse("pools must be disjoint", contains(VoiceManager.FEMALE_SPEAKER_POOL, m));
    }
  }

  // ---- Player ----

  @Test
  public void playerResolvesToPlayerSpecWithConfiguredGender() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_B);
    VoiceSpec spec = manager.resolveVoice("player", null);
    assertTrue("player voice should be a player spec", spec.player());
    assertEquals(NPCGender.FEMALE, spec.gender());
    // The player carries an explicit British speaker, so the cache key includes it.
    assertEquals("player:FEMALE#" + VoiceManager.playerSpeaker(NPCGender.FEMALE), spec.key());
  }

  @Test
  public void playerResolvesToABritishSpeakerByGender() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_B);
    VoiceSpec spec = manager.resolveVoice("player", null);
    assertTrue("player carries a British Local speaker", spec.hasExplicitKokoroSpeakerId());
    assertEquals(VoiceManager.playerSpeaker(NPCGender.FEMALE), manager.kokoroSpeakerId(spec));
    assertTrue("player speaker must be British", isBritish(manager.kokoroSpeakerId(spec)));
  }

  @Test
  public void playerSpeakerMatchingIsCaseInsensitive() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    VoiceSpec spec = manager.resolveVoice("PLAYER", null);
    assertTrue(spec.player());
    assertEquals(VoiceManager.playerSpeaker(NPCGender.MALE), manager.kokoroSpeakerId(spec));
  }

  @Test
  public void playerVoiceTypesFixGender() {
    assertEquals(NPCGender.MALE, PlayerVoice.TYPE_A.getGender());
    assertEquals(NPCGender.FEMALE, PlayerVoice.TYPE_B.getGender());
  }

  // ---- NPC selection ----

  @Test
  public void undetectedNpcResolvesToTheDefaultHumanMaleVoice() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    // No client, so the NPC can't be found and detection resolves to the default human-male voice.
    VoiceSpec spec = manager.resolveVoice("npc", "Hans");
    assertEquals(NPCRace.HUMAN, spec.race());
    assertEquals(NPCGender.MALE, spec.gender());
    assertTrue("default-voice NPC still gets a per-NPC speaker", spec.hasExplicitKokoroSpeakerId());
    assertTrue(
        "chosen speaker must be from the male British pool",
        contains(VoiceManager.MALE_SPEAKER_POOL, manager.kokoroSpeakerId(spec)));
    assertTrue("cache key carries the chosen speaker", spec.key().startsWith("npc:HUMAN:MALE#"));
  }

  @Test
  public void bareSpecsLandInTheGenderCorrectBritishPool() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    int male = manager.kokoroSpeakerId(VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE));
    int female = manager.kokoroSpeakerId(VoiceSpec.npc(NPCRace.ELF, NPCGender.FEMALE));
    assertTrue(contains(VoiceManager.MALE_SPEAKER_POOL, male));
    assertTrue(contains(VoiceManager.FEMALE_SPEAKER_POOL, female));
  }

  @Test
  public void selectionIsByGenderNotRace() {
    // Two NPCs of different races but the same gender and key hash to the same British voice: race
    // does not influence the Local voice any more.
    int human = VoiceManager.pickNpcSpeakerId(NPCGender.MALE, 4242, "Guard");
    int dwarf = VoiceManager.pickNpcSpeakerId(NPCGender.MALE, 4242, "Guard");
    assertEquals(human, dwarf);
  }

  @Test
  public void explicitSpeakerOnSpecWins() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    VoiceSpec spec = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE, 27);
    assertEquals(27, manager.kokoroSpeakerId(spec));
  }

  @Test
  public void unknownGenderBareSpecLandsInTheMaleBritishPool() {
    VoiceManager manager = newManager(PlayerVoice.TYPE_A);
    int chosen = manager.kokoroSpeakerId(VoiceSpec.npc(NPCRace.HUMAN, NPCGender.UNKNOWN));
    assertTrue("unknown gender must still resolve British", isBritish(chosen));
    assertTrue(contains(VoiceManager.MALE_SPEAKER_POOL, chosen));
  }

  // ---- Per-NPC variety (issue #78) ----

  @Test
  public void differentNpcIdsOfSameGenderGetDifferentSpeakers() {
    int poolSize = VoiceManager.MALE_SPEAKER_POOL.length;
    int a = VoiceManager.pickNpcSpeakerId(NPCGender.MALE, 1001, "Guard");
    int b = VoiceManager.pickNpcSpeakerId(NPCGender.MALE, 1002, "Guard");
    assertTrue("pool must offer variety", poolSize > 1);
    assertNotEquals("adjacent ids must land in different slots", a, b);
    assertEquals(NPCGender.MALE, genderOf(a));
    assertEquals(NPCGender.MALE, genderOf(b));
  }

  @Test
  public void perNpcSpeakerIsStableAcrossCalls() {
    int first = VoiceManager.pickNpcSpeakerId(NPCGender.MALE, 4242, "Guard");
    for (int i = 0; i < 5; i++) {
      assertEquals(first, VoiceManager.pickNpcSpeakerId(NPCGender.MALE, 4242, "Guard"));
    }
  }

  @Test
  public void perNpcSelectionKeysOnNameWhenNoCompositionId() {
    int hans = VoiceManager.pickNpcSpeakerId(NPCGender.MALE, null, "Hans");
    int hansAgain = VoiceManager.pickNpcSpeakerId(NPCGender.MALE, null, "<col=ff0000>Hans</col>");
    assertEquals("normalised name keys identically", hans, hansAgain);
    assertEquals(NPCGender.MALE, genderOf(hans));
  }

  @Test
  public void femaleNpcOnlyEverGetsFemaleSpeaker() {
    for (int id = 0; id < 200; id++) {
      int chosen = VoiceManager.pickNpcSpeakerId(NPCGender.FEMALE, id, "Woman");
      assertEquals(
          "female NPC id " + id + " must map to a female voice",
          NPCGender.FEMALE,
          genderOf(chosen));
    }
  }

  // ---- Traces ----

  @Test
  public void buildNpcTraceShowsWorldHitSourceAndChosenSpeaker() {
    String trace =
        VoiceManager.buildNpcTrace("Goblin", 101, NPCRace.GOBLIN, NPCGender.MALE, "table-hit", 24);
    assertTrue(trace, trace.contains("npc='Goblin'"));
    assertTrue(trace, trace.contains("world=HIT(id=101)"));
    assertTrue(trace, trace.contains("race=GOBLIN"));
    assertTrue(trace, trace.contains("source=table-hit"));
    assertTrue(trace, trace.contains("speakerId=24"));
    assertTrue(trace, trace.contains("bm_daniel"));
  }

  @Test
  public void buildNpcTraceShowsWorldMissForUntabledNpc() {
    String trace =
        VoiceManager.buildNpcTrace(
            "Hans", null, NPCRace.UNKNOWN, NPCGender.UNKNOWN, "not-in-world", 26);
    assertTrue(trace, trace.contains("world=MISS"));
    assertTrue(trace, trace.contains("race=UNKNOWN"));
    assertTrue(trace, trace.contains("bm_george"));
  }

  @Test
  public void buildPlayerTraceNamesTheSpeaker() {
    String trace = VoiceManager.buildPlayerTrace(NPCGender.FEMALE);
    assertTrue(trace, trace.contains("player ->"));
    assertTrue(trace, trace.contains("gender=FEMALE"));
    assertTrue(trace, trace.contains("speakerId=" + VoiceManager.playerSpeaker(NPCGender.FEMALE)));
  }

  // ---- Name normalisation ----

  @Test
  public void normalizeNameStripsColorTagsTrimsAndNormalisesNbsp() {
    assertEquals("Hans", VoiceManager.normalizeName("<col=0000ff>Hans</col>"));
    assertEquals("Hans", VoiceManager.normalizeName("  Hans  "));
    assertEquals("Father Aereck", VoiceManager.normalizeName("Father Aereck"));
    assertEquals("", VoiceManager.normalizeName(null));
    assertEquals("", VoiceManager.normalizeName("<col=ff0000></col>"));
  }

  private static NPCGender genderOf(int speakerId) {
    for (KokoroVoice voice : KokoroVoice.values()) {
      if (voice.getSpeakerId() == speakerId) {
        return voice.getGender();
      }
    }
    return NPCGender.UNKNOWN;
  }

  private static boolean isBritish(int speakerId) {
    return VoiceManager.kokoroVoiceName(speakerId).startsWith("b");
  }

  private static boolean contains(int[] pool, int value) {
    for (int v : pool) {
      if (v == value) {
        return true;
      }
    }
    return false;
  }
}
