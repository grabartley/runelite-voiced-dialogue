package com.grahambartley;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev-client launcher: runs RuneLite with this plugin loaded for manual testing. Not a unit test.
 */
public class TTSDialoguePluginRunner {
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(TTSDialoguePlugin.class);
    RuneLite.main(args);
  }
}
