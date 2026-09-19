package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class FollowerDialogLine {

  String text;

  int page;

  boolean playerSpeaking;
}
