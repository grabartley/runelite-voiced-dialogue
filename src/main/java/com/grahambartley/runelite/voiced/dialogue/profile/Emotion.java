package com.grahambartley.runelite.voiced.dialogue.profile;

/**
 * The emotional colour carried end-to-end through synthesis.
 *
 * <p>Detection reads the speaker's chat-head animation and threads one of these into every {@code
 * SynthesisRequest}; each {@code SynthesisBackend} renders it however its engine allows. Backends
 * advertise the subset they can voice via {@code SynthesisBackend#supportedEmotions()}, and {@code
 * BackendProvider} downgrades anything unsupported to {@link #NEUTRAL} before synthesis so backends
 * never special-case emotions they cannot produce.
 */
public enum Emotion {
  NEUTRAL,
  HAPPY,
  SAD,
  ANGRY,
  SCARED
}
