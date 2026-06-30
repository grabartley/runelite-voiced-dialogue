package com.grahambartley;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev-client launcher: runs RuneLite with this plugin loaded for manual testing. Not a unit test.
 */
public class VoicedDialoguePluginRunner {
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(VoicedDialoguePlugin.class);
    RuneLite.main(args);
  }
}
