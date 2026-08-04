package com.grahambartley.synthesis;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * One line to synthesize: the text, the resolved {@link VoiceSpec}, the desired {@link Emotion},
 * and an optional {@link CharacterProfile} steering delivery.
 *
 * <p>This is the single unit that flows from the dialogue pipeline into a {@link SynthesisBackend}.
 * The emotion is the <em>requested</em> emotion; {@link BackendProvider} may downgrade it to {@link
 * Emotion#NEUTRAL} for backends that cannot voice it before {@link SynthesisBackend#synthesize} is
 * called.
 *
 * <p>The profile is the resolved per-speaker delivery template ({@link CharacterProfile}); it is
 * {@code null} when character profiles are off or no profile applies. The cloud backend renders it
 * as a leading AUDIO PROFILE block. A {@code null} profile leaves the request body byte-for-byte as
 * before, so existing cache entries stay valid.
 *
 * <p>{@code skipTranslation} forces the line to be voiced verbatim even when a non-English spoken
 * language or a global quirk is configured: the cloud backend skips the translation hop and the
 * {@code |l<language>} cache segment for such a request. It is {@code true} only for the player's
 * own public chat (voiced as typed); every dialogue line leaves it {@code false}, so the request
 * body and cache key stay byte-for-byte as before.
 *
 * <p>{@code player} marks the line as the player's own speech rather than an NPC's, so the cloud
 * backend can pick the per-speaker-class Speaking Style (Player vs NPC). It is {@code true} for
 * player dialogue, public chat, and prefetched options (all lines the player speaks) and {@code
 * false} for NPC lines. The legacy constructors default it {@code false}, so an unmarked request
 * voices as an NPC line as before.
 *
 * <p>{@code context} is the preceding NPC line, carried only for a short player reply so a rewrite
 * can tell what the reply is answering. It is never spoken and never part of the text sent to TTS.
 * It is {@code null} for every other request, so those keep their existing request body and cache
 * key.
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
@AllArgsConstructor
public final class SynthesisRequest {

  /**
   * Only a short reply is ambiguous enough to need the question it answers. A longer line already
   * carries its own meaning, so it is not worth the extra prompt tokens.
   */
  private static final int MAX_CONTEXTUAL_REPLY_CHARS = 80;

  /** Upper bound on the carried context, so a long NPC speech cannot dominate the prompt. */
  private static final int MAX_CONTEXT_CHARS = 240;

  private final String text;
  private final VoiceSpec voice;
  private final Emotion emotion;
  private final CharacterProfile profile;
  private final boolean skipTranslation;
  private final boolean player;
  private final String context;

  /** A request with no character profile (backward-compatible 3-arg form). */
  public SynthesisRequest(String text, VoiceSpec voice, Emotion emotion) {
    this(text, voice, emotion, null, false, false, null);
  }

  /** A translating request with a character profile (backward-compatible 4-arg form). */
  public SynthesisRequest(String text, VoiceSpec voice, Emotion emotion, CharacterProfile profile) {
    this(text, voice, emotion, profile, false, false, null);
  }

  /** A request with explicit translation behaviour but no speaker-class flag (5-arg form). */
  public SynthesisRequest(
      String text,
      VoiceSpec voice,
      Emotion emotion,
      CharacterProfile profile,
      boolean skipTranslation) {
    this(text, voice, emotion, profile, skipTranslation, false, null);
  }

  /** A request with explicit translation and speaker-class behaviour (6-arg form). */
  public SynthesisRequest(
      String text,
      VoiceSpec voice,
      Emotion emotion,
      CharacterProfile profile,
      boolean skipTranslation,
      boolean player) {
    this(text, voice, emotion, profile, skipTranslation, player, null);
  }

  /**
   * Returns a copy of this request with a different emotion, leaving text, voice, profile, context,
   * and the translation and speaker-class behaviour intact.
   */
  public SynthesisRequest withEmotion(Emotion newEmotion) {
    if (newEmotion == emotion) {
      return this;
    }
    return new SynthesisRequest(text, voice, newEmotion, profile, skipTranslation, player, context);
  }

  /**
   * Returns a copy carrying the preceding NPC line as non-spoken rewrite context, or this request
   * unchanged when the context cannot help: anything that is not a short player reply, and public
   * chat, which is always voiced exactly as typed. Longer context is trimmed to a bounded window.
   */
  public SynthesisRequest withContext(String dialogueContext) {
    String bounded = dialogueContext == null ? null : dialogueContext.trim();
    if (!player
        || skipTranslation
        || text == null
        || text.length() > MAX_CONTEXTUAL_REPLY_CHARS
        || bounded == null
        || bounded.isEmpty()) {
      bounded = null;
    } else if (bounded.length() > MAX_CONTEXT_CHARS) {
      bounded = bounded.substring(0, MAX_CONTEXT_CHARS).trim();
    }
    if (bounded == null ? context == null : bounded.equals(context)) {
      return this;
    }
    return new SynthesisRequest(text, voice, emotion, profile, skipTranslation, player, bounded);
  }
}
