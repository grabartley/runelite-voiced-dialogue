package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class FollowerBuddyPresenceTest {

  @Test
  @Parameters(method = "hubCases")
  public void theHubListDecidesInstallation(String externalPlugins, boolean expected) {
    assertEquals(expected, FollowerBuddyPresence.installedFromHub(externalPlugins));
  }

  private Object[] hubCases() {
    return new Object[] {
      new Object[] {"follower-buddy", true},
      new Object[] {"voiced-dialogue,follower-buddy,menu-entry-swapper", true},
      new Object[] {"voiced-dialogue, follower-buddy", true},
      new Object[] {"follower-buddy-extras", false},
      new Object[] {"my-follower-buddy", false},
      new Object[] {"voiced-dialogue", false},
      new Object[] {"", false},
      new Object[] {null, false},
    };
  }

  @Test
  public void theNoticeOnlyFiresForAnInstalledPluginWithMirroringOff() {
    assertTrue(FollowerBuddyPresence.shouldWarnMirrorOff(true, false));
    assertFalse(FollowerBuddyPresence.shouldWarnMirrorOff(true, true));
    assertFalse(
        "stale config alone never earns a notice",
        FollowerBuddyPresence.shouldWarnMirrorOff(false, false));
    assertFalse(FollowerBuddyPresence.shouldWarnMirrorOff(false, true));
  }
}
