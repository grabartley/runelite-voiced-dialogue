package com.grahambartley.runelite.voiced.dialogue;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class VoicedDialoguePluginRunner {
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(VoicedDialoguePlugin.class);
    RuneLite.main(args);
  }
}
