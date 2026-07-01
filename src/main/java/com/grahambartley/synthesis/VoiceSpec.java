package com.grahambartley.synthesis;

import com.grahambartley.voice.VoiceManager;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Backend-neutral description of <em>who</em> is speaking: the player, or an NPC of a given race
 * and gender.
 *
 * <p>This carries the resolved race/gender categories rather than any engine-specific voice id, so
 * the cloud backend can map the same spec to its own voice bank. {@link #key()} produces a stable,
 * human-readable fragment used in the synthesis cache key.
 *
 * <p>For per-NPC voice variety (issue #78) an NPC spec may additionally carry a {@link #voiceSeed}:
 * a stable, backend-neutral integer derived from the NPC's identity, so two NPCs of the same
 * race+gender can be spread across a gender-appropriate sub-pool and sound different (but stable)
 * from each other. {@link #UNSPECIFIED_SEED} ({@code -1}) means "no explicit choice" so the backend
 * anchors the spec to the first voice of its race/gender pool.
 */
@Value
@Accessors(fluent = true)
public class VoiceSpec {

  /** No per-NPC variety seed: the backend anchors the spec to its race/gender pool. */
  public static final int UNSPECIFIED_SEED = -1;

  boolean player;
  VoiceManager.NPCRace race;
  VoiceManager.NPCGender gender;
  int voiceSeed;

  /** A player voice of the given gender. Race is not meaningful for the player. */
  public static VoiceSpec player(VoiceManager.NPCGender gender) {
    return new VoiceSpec(true, VoiceManager.NPCRace.HUMAN, gender, UNSPECIFIED_SEED);
  }

  /** An NPC voice for the given race and gender, with no per-NPC variety seed. */
  public static VoiceSpec npc(VoiceManager.NPCRace race, VoiceManager.NPCGender gender) {
    return new VoiceSpec(false, race, gender, UNSPECIFIED_SEED);
  }

  /**
   * An NPC voice for the given race and gender carrying a stable per-NPC variety seed (issue #78),
   * spreading same-race/gender NPCs across a gender sub-pool. A negative seed is normalised to
   * {@link #UNSPECIFIED_SEED} so it is treated as absent.
   */
  public static VoiceSpec npc(VoiceManager.NPCRace race, VoiceManager.NPCGender gender, int seed) {
    return new VoiceSpec(false, race, gender, seed < 0 ? UNSPECIFIED_SEED : seed);
  }

  /** Whether this spec carries a per-NPC variety seed. */
  public boolean hasVoiceSeed() {
    return voiceSeed >= 0;
  }

  /**
   * Stable cache-key fragment, e.g. {@code "npc:ELF:FEMALE"} or {@code "player:MALE"}. Two specs
   * that resolve to the same voice produce the same key. The per-NPC variety seed is deliberately
   * not folded in here: the cloud backend already reflects the concrete resolved voice in its own
   * cache variant, so two NPCs that map to different voices never share a cached frame anyway.
   */
  public String key() {
    return player ? "player:" + gender : "npc:" + race + ":" + gender;
  }

  @Override
  public String toString() {
    return key();
  }
}
