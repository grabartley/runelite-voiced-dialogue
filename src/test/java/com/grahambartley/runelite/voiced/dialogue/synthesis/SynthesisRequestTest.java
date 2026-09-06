package com.grahambartley.runelite.voiced.dialogue.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager.NPCGender;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager.NPCRace;
import org.junit.Test;

/**
 * The short-constructor defaults and the routing flags carried alongside the spoken line: a bare
 * request must produce a translating, non-speculative NPC line, and {@code withEmotion} must carry
 * every flag through so a re-emotioned copy is not silently re-translated, re-classed, or
 * re-counted as a line the player heard.
 */
public class SynthesisRequestTest {

  private static final VoiceSpec VOICE = VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE);

  @Test
  public void shortConstructorDefaultsToTranslating() {
    assertFalse(
        "the 3-arg form leaves translation enabled",
        new SynthesisRequest("hi", VOICE, Emotion.NEUTRAL).skipTranslation());
  }

  @Test
  public void withEmotionPreservesSkipTranslation() {
    SynthesisRequest publicChat =
        new SynthesisRequest("hi", VOICE, Emotion.NEUTRAL, null, true, false);
    assertTrue(
        "a re-emotioned copy keeps skip-translation, so a downgrade never re-enables translation",
        publicChat.withEmotion(Emotion.HAPPY).skipTranslation());

    SynthesisRequest dialogue =
        new SynthesisRequest("hi", VOICE, Emotion.HAPPY, null, false, false);
    assertFalse(
        "a normal line stays translating after a downgrade",
        dialogue.withEmotion(Emotion.NEUTRAL).skipTranslation());
  }

  @Test
  public void shortConstructorDefaultsToNpcSpeakerClass() {
    assertFalse(
        "the 3-arg form voices as an NPC line",
        new SynthesisRequest("hi", VOICE, Emotion.NEUTRAL).player());
  }

  @Test
  public void withEmotionPreservesPlayerClass() {
    SynthesisRequest playerLine =
        new SynthesisRequest("hi", VOICE, Emotion.NEUTRAL, null, false, true);
    assertTrue(
        "a re-emotioned player line stays a player line, so it keeps the player Speaking Style",
        playerLine.withEmotion(Emotion.HAPPY).player());

    SynthesisRequest npcLine = new SynthesisRequest("hi", VOICE, Emotion.HAPPY, null, false, false);
    assertFalse(
        "a re-emotioned NPC line stays an NPC line", npcLine.withEmotion(Emotion.NEUTRAL).player());
  }

  @Test
  public void everyConstructorDefaultsToALiveLineRatherThanAPrefetch() {
    assertFalse(new SynthesisRequest("hi", VOICE, Emotion.NEUTRAL).prefetch());
    assertFalse(new SynthesisRequest("hi", VOICE, Emotion.NEUTRAL, null, true, true).prefetch());
  }

  @Test
  public void asPrefetchMarksTheLineAndChangesNothingElse() {
    SynthesisRequest live = new SynthesisRequest("hi", VOICE, Emotion.HAPPY, null, true, true);
    SynthesisRequest warmed = live.asPrefetch();

    assertTrue("the copy is marked speculative", warmed.prefetch());
    assertEquals("the spoken text is untouched", live.text(), warmed.text());
    assertEquals("the voice is untouched", live.voice(), warmed.voice());
    assertEquals("the emotion is untouched", live.emotion(), warmed.emotion());
    assertEquals(
        "translation behaviour is untouched", live.skipTranslation(), warmed.skipTranslation());
    assertEquals("the speaker class is untouched", live.player(), warmed.player());
    assertSame("marking an already-speculative line is a no-op", warmed, warmed.asPrefetch());
  }

  @Test
  public void withEmotionPreservesThePrefetchMark() {
    SynthesisRequest warmed =
        new SynthesisRequest("hi", VOICE, Emotion.HAPPY, null, false, true).asPrefetch();
    assertTrue(
        "an emotion downgrade must not turn speculative warming into a voiced line",
        warmed.withEmotion(Emotion.NEUTRAL).prefetch());
  }
}
