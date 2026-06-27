package com.grahambartley.synthesis;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Renders an {@link Emotion} as a Gemini 3.1 Flash TTS inline style tag prepended to the spoken
 * {@code input}.
 *
 * <p>Gemini voices a delivery style from a bracketed tag at the head of the text (e.g. {@code
 * "[happy] Well met, traveller."}); the tag steers tone without being read aloud. The mapping here
 * is deliberately conservative: only the four non-neutral {@link Emotion}s detected from chat-head
 * animations (#25) are tagged, each to the closest tag in Gemini's documented vocabulary ({@code
 * SCARED} -> {@code fearful}). {@link Emotion#NEUTRAL} adds no tag at all, so a neutral line is
 * byte-for-byte the plain text. Tuning the exact tag wording or adding intensity is a follow-up
 * (out of scope for #109); this is the single place to change it.
 *
 * <p>{@link #SUPPORTED} is what {@link OpenRouterTtsBackend} advertises, so {@link BackendProvider}
 * only ever passes an emotion this map can tag.
 */
final class GeminiEmotionStyle {

  /** Emotions the Gemini backend can voice; everything else is downgraded to neutral upstream. */
  static final EnumSet<Emotion> SUPPORTED =
      EnumSet.of(Emotion.NEUTRAL, Emotion.HAPPY, Emotion.SAD, Emotion.ANGRY, Emotion.SCARED);

  private static final Map<Emotion, String> TAGS = new EnumMap<>(Emotion.class);

  static {
    TAGS.put(Emotion.HAPPY, "happy");
    TAGS.put(Emotion.SAD, "sad");
    TAGS.put(Emotion.ANGRY, "angry");
    TAGS.put(Emotion.SCARED, "fearful");
  }

  private GeminiEmotionStyle() {}

  /**
   * The bare tag word for an emotion (e.g. {@code "happy"}), or {@code null} for neutral/unmapped.
   */
  static String tagFor(Emotion emotion) {
    return emotion == null ? null : TAGS.get(emotion);
  }

  /**
   * Prefixes {@code input} with the emotion's inline style tag, or returns {@code input} unchanged
   * for {@link Emotion#NEUTRAL} (and any unmapped emotion) so neutral lines carry no markup.
   */
  static String apply(String input, Emotion emotion) {
    String tag = tagFor(emotion);
    if (tag == null) {
      return input;
    }
    return "[" + tag + "] " + input;
  }
}
