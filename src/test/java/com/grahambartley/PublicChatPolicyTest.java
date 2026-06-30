package com.grahambartley;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The local-player name filter for the public-chat feature (#138): keeps only the player's own
 * public chat. Pure and null-safe, so it verifies without a live client.
 */
public class PublicChatPolicyTest {

  @Test
  public void selfFilterMatchesTheLocalPlayerIgnoringRankIconsAndNbsp() {
    assertTrue(
        "plain identical names match", PublicChatPolicy.isSelfPublicChat("Zezima", "Zezima"));
    assertTrue(
        "a clan/friend rank <img> icon prefix on the chat name is stripped before comparing",
        PublicChatPolicy.isSelfPublicChat("<img=2>Zezima", "Zezima"));
    assertTrue(
        "a non-breaking space in the chat name is normalised to a regular space",
        PublicChatPolicy.isSelfPublicChat("Big Bird", "Big Bird"));
    assertTrue(
        "rank icon and nbsp together still match",
        PublicChatPolicy.isSelfPublicChat("<img=5>Big Bird", "Big Bird"));
  }

  @Test
  public void selfFilterDropsOtherPlayersAndNulls() {
    assertFalse(
        "a different player is dropped", PublicChatPolicy.isSelfPublicChat("Woox", "Zezima"));
    assertFalse(
        "a different player behind a rank icon is still dropped",
        PublicChatPolicy.isSelfPublicChat("<img=2>Woox", "Zezima"));
    assertFalse(
        "a null chat name never matches", PublicChatPolicy.isSelfPublicChat(null, "Zezima"));
    assertFalse(
        "a null local name (not logged in) never matches",
        PublicChatPolicy.isSelfPublicChat("Zezima", null));
  }
}
