package com.grahambartley.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class WikiTranscriptTest {

  private static final String HANS =
      "* '''Hans:''' Hello. What are you doing here?\n"
          + "* {{tselect|Select an option}}\n"
          + "* {{topt|I'm looking for whoever is in charge of this place.}}\n"
          + "** '''Player:''' I'm looking for whoever is in charge of this place.\n"
          + "** '''Hans:''' Who, the Duke? He's in his study.\n"
          + "** {{tact|end}}\n"
          + "* {{topt|I don't know. I'm lost. Where am I?}}\n"
          + "** '''Player:''' I don't know. I'm lost. Where am I?\n"
          + "** '''Hans:''' You are in Lumbridge Castle.\n"
          + "** {{tact|end}}\n"
          + "* {{topt|Can you tell me how long I've been here?}}\n"
          + "** '''Player:''' Can you tell me how long I've been here?\n"
          + "** '''Hans:''' You've spent [amount] days here.\n";

  @Test
  public void initialNpcLinePredictsAtMostTwoOptions() {
    List<WikiTranscript.Line> next =
        WikiTranscript.parse(HANS, "Hans").successors("Hello. What are you doing here?");

    assertEquals(2, next.size());
    assertEquals(WikiTranscript.Speaker.PLAYER, next.get(0).speaker());
    assertEquals("I'm looking for whoever is in charge of this place.", next.get(0).text());
    assertEquals("I don't know. I'm lost. Where am I?", next.get(1).text());
  }

  @Test
  public void selectedOptionPredictsItsNpcResponse() {
    List<WikiTranscript.Line> next =
        WikiTranscript.parse(HANS, "Hans")
            .successors("I'm looking for whoever is in charge of this place.");

    assertEquals(1, next.size());
    assertEquals(WikiTranscript.Speaker.NPC, next.get(0).speaker());
    assertEquals("Who, the Duke? He's in his study.", next.get(0).text());
  }

  @Test
  public void dynamicPlaceholderAndOtherSpeakerStopPrediction() {
    assertTrue(
        WikiTranscript.parse(HANS, "Hans")
            .successors("Can you tell me how long I've been here?")
            .isEmpty());
    assertTrue(
        WikiTranscript.parse(
                "* '''Hans:''' Hello.\n* '''Duke Horacio:''' Welcome.\n* '''Player:''' Thanks.",
                "Hans")
            .successors("Hello.")
            .isEmpty());
  }

  @Test
  public void ambiguousDuplicateAndConditionedBranchesAreRejected() {
    String duplicate =
        "==First==\n* '''Hans:''' Hello.\n* '''Player:''' First answer.\n"
            + "==Second==\n* '''Hans:''' Hello.\n* '''Player:''' Different answer.\n";
    assertTrue(WikiTranscript.parse(duplicate, "Hans").successors("Hello.").isEmpty());
    assertTrue(
        WikiTranscript.parse(
                "* '''Hans:''' Hello.\n* {{tcond|After completing a quest:}}\n** '''Hans:''' Secret.",
                "Hans")
            .successors("Hello.")
            .isEmpty());
  }
}
