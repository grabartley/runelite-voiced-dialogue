package com.grahambartley.synthesis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.grahambartley.voice.VoiceManager.NPCGender;
import com.grahambartley.voice.VoiceManager.NPCRace;
import org.junit.Test;

/** When a short player reply carries the question it is answering. */
public class SynthesisRequestContextTest {

  private static SynthesisRequest playerReply(String text) {
    return new SynthesisRequest(
        text,
        VoiceSpec.player(NPCGender.MALE),
        Emotion.NEUTRAL,
        null,
        /* skipTranslation= */ false,
        /* player= */ true);
  }

  private static SynthesisRequest npcLine(String text) {
    return new SynthesisRequest(
        text, VoiceSpec.npc(NPCRace.HUMAN, NPCGender.MALE), Emotion.NEUTRAL);
  }

  @Test
  public void aShortPlayerReplyKeepsTheQuestionItAnswers() {
    SynthesisRequest reply = playerReply("Yes.").withContext("Will you help me fight the dragon?");

    assertEquals("Will you help me fight the dragon?", reply.context());
    assertEquals("the spoken text is untouched", "Yes.", reply.text());
  }

  @Test
  public void anNpcLineNeverCarriesContext() {
    assertNull(npcLine("Well met, traveller.").withContext("Anything at all").context());
  }

  @Test
  public void publicChatIsAlwaysVoicedAsTyped() {
    SynthesisRequest publicChat =
        new SynthesisRequest(
            "Yes.",
            VoiceSpec.player(NPCGender.MALE),
            Emotion.NEUTRAL,
            null,
            /* skipTranslation= */ true,
            /* player= */ true);

    assertNull(publicChat.withContext("Will you help me?").context());
  }

  @Test
  public void aLongReplyCarriesItsOwnMeaningSoNeedsNoContext() {
    String longReply =
        "Yes, I will gladly help you fight the dragon, but first I must return to Varrock and"
            + " gather supplies for the journey ahead.";

    assertNull(playerReply(longReply).withContext("Will you help me?").context());
  }

  @Test
  public void blankOrMissingContextIsIgnored() {
    assertNull(playerReply("Yes.").withContext(null).context());
    assertNull(playerReply("Yes.").withContext("   ").context());
  }

  @Test
  public void oversizedContextIsTrimmedToABoundedWindow() {
    String longQuestion = repeat("a very long question ", 40);

    String carried = playerReply("Yes.").withContext(longQuestion).context();

    assertEquals(240, carried.length());
  }

  @Test
  public void anUnchangedContextReturnsTheSameInstance() {
    SynthesisRequest reply = playerReply("Yes.");

    assertSame(reply, reply.withContext(null));
  }

  @Test
  public void contextSurvivesAnEmotionDowngrade() {
    SynthesisRequest reply =
        playerReply("Yes.").withContext("Will you help me?").withEmotion(Emotion.HAPPY);

    assertEquals("Will you help me?", reply.context());
  }

  private static String repeat(String value, int times) {
    StringBuilder builder = new StringBuilder(value.length() * times);
    for (int i = 0; i < times; i++) {
      builder.append(value);
    }
    return builder.toString();
  }
}
