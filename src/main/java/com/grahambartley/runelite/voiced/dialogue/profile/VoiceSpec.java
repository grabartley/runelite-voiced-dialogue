package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
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
 * <p>For per-NPC voice variety an NPC spec may additionally carry a {@link #voiceSeed}: a stable,
 * backend-neutral integer derived from the NPC's identity, so two NPCs of the same race+gender can
 * be spread across a gender-appropriate sub-pool and sound different (but stable) from each other.
 * {@link #UNSPECIFIED_SEED} ({@code -1}) means "no explicit choice" so the backend anchors the spec
 * to the first voice of its race/gender pool.
 *
 * <p>A spec flagged {@link #child} resolves to a youthful, gender-correct sub-pool instead of its
 * adult race anchor; the race still colours the delivery through the character-profile text, and
 * the player is never a child.
 */
@Value
@Accessors(fluent = true)
public class VoiceSpec {

  /** No per-NPC variety seed: the backend anchors the spec to its race/gender pool. */
  public static final int UNSPECIFIED_SEED = -1;

  boolean player;
  NpcRace race;
  NpcGender gender;
  int voiceSeed;
  boolean child;

  /** A player voice of the given gender. Race is not meaningful for the player. */
  public static VoiceSpec player(NpcGender gender) {
    return new VoiceSpec(true, NpcRace.HUMAN, gender, UNSPECIFIED_SEED, false);
  }

  /** An NPC voice for the given race and gender, with no per-NPC variety seed. */
  public static VoiceSpec npc(NpcRace race, NpcGender gender) {
    return new VoiceSpec(false, race, gender, UNSPECIFIED_SEED, false);
  }

  /**
   * An NPC voice for the given race and gender carrying a stable per-NPC variety seed, spreading
   * same-race/gender NPCs across a gender sub-pool. A negative seed is normalised to {@link
   * #UNSPECIFIED_SEED} so it is treated as absent.
   */
  public static VoiceSpec npc(NpcRace race, NpcGender gender, int seed) {
    return new VoiceSpec(false, race, gender, seed < 0 ? UNSPECIFIED_SEED : seed, false);
  }

  /**
   * An NPC voice additionally carrying the child flag, so a young NPC resolves to the youthful
   * voice sub-pool of its gender rather than its adult race anchor.
   */
  public static VoiceSpec npc(NpcRace race, NpcGender gender, int seed, boolean child) {
    return new VoiceSpec(false, race, gender, seed < 0 ? UNSPECIFIED_SEED : seed, child);
  }

  /** Whether this spec carries a per-NPC variety seed. */
  public boolean hasVoiceSeed() {
    return voiceSeed >= 0;
  }

  /**
   * Stable cache-key fragment, e.g. {@code "npc:ELF:FEMALE"} or {@code "player:MALE"}. Two specs
   * that resolve to the same voice produce the same key. The per-NPC variety seed and the child
   * flag are deliberately not folded in here: the cloud backend already reflects the concrete
   * resolved voice in its own cache variant, so two NPCs that map to different voices never share a
   * cached frame anyway.
   */
  public String key() {
    return player ? "player:" + gender : "npc:" + race + ":" + gender;
  }

  @Override
  public String toString() {
    return key();
  }
}
