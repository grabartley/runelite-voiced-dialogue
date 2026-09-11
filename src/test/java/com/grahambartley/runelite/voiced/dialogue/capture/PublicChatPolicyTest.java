package com.grahambartley.runelite.voiced.dialogue.capture;

import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class PublicChatPolicyTest {

  private Object[] selfPublicChatCases() {
    return new Object[] {
      new Object[] {"Zezima", "Zezima", true},
      new Object[] {"<img=2>Zezima", "Zezima", true},
      new Object[] {"Big Bird", "Big Bird", true},
      new Object[] {"<img=5>Big Bird", "Big Bird", true},
      new Object[] {"Woox", "Zezima", false},
      new Object[] {"<img=2>Woox", "Zezima", false},
      new Object[] {null, "Zezima", false},
      new Object[] {"Zezima", null, false},
    };
  }

  @Test
  @Parameters(method = "selfPublicChatCases")
  public void selfFilterKeepsOnlyTheLocalPlayerIgnoringRankIconsAndNbsp(
      String eventName, String localName, boolean expected) {
    assertEquals(expected, PublicChatPolicy.isSelfPublicChat(eventName, localName));
  }
}
