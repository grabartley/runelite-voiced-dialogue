package com.grahambartley;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure decision logic for the public-chat feature (issue #138): the local-player name filter that
 * keeps only the player's own public chat, and the edge-triggered close interrupt that no longer
 * truncates public-chat audio played while idle. Both are static, so they verify without a live
 * client.
 */
public class TTSDialoguePluginPublicChatTest {

  @Test
  public void selfFilterMatchesTheLocalPlayerIgnoringRankIconsAndNbsp() {
    assertTrue(
        "plain identical names match", TTSDialoguePlugin.isSelfPublicChat("Zezima", "Zezima"));
    assertTrue(
        "a clan/friend rank <img> icon prefix on the chat name is stripped before comparing",
        TTSDialoguePlugin.isSelfPublicChat("<img=2>Zezima", "Zezima"));
    assertTrue(
        "a non-breaking space in the chat name is normalised to a regular space",
        TTSDialoguePlugin.isSelfPublicChat("Big Bird", "Big Bird"));
    assertTrue(
        "rank icon and nbsp together still match",
        TTSDialoguePlugin.isSelfPublicChat("<img=5>Big Bird", "Big Bird"));
  }

  @Test
  public void selfFilterDropsOtherPlayersAndNulls() {
    assertFalse(
        "a different player is dropped", TTSDialoguePlugin.isSelfPublicChat("Woox", "Zezima"));
    assertFalse(
        "a different player behind a rank icon is still dropped",
        TTSDialoguePlugin.isSelfPublicChat("<img=2>Woox", "Zezima"));
    assertFalse(
        "a null chat name never matches", TTSDialoguePlugin.isSelfPublicChat(null, "Zezima"));
    assertFalse(
        "a null local name (not logged in) never matches",
        TTSDialoguePlugin.isSelfPublicChat("Zezima", null));
  }

  @Test
  public void closeInterruptFiresOnlyOnTheOpenToClosedTransition() {
    assertTrue(
        "dialogue just closed -> cut its audio once",
        TTSDialoguePlugin.shouldInterruptOnClose(false, true));
    assertFalse(
        "still idle (was closed, still closed) -> never interrupt, so public chat plays on",
        TTSDialoguePlugin.shouldInterruptOnClose(false, false));
    assertFalse(
        "dialogue still open -> nothing to interrupt",
        TTSDialoguePlugin.shouldInterruptOnClose(true, true));
    assertFalse(
        "dialogue just opened -> nothing to interrupt",
        TTSDialoguePlugin.shouldInterruptOnClose(true, false));
  }
}
