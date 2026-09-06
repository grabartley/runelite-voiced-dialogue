package com.grahambartley.runelite.voiced.dialogue.data;

/**
 * Life-stage markers carried by the bundled tables. Only children are marked; an NPC without a
 * marker is an adult.
 */
public final class LifeStage {

  /** Voiced from the youthful sub-pool of the NPC's gender. */
  public static final String CHILD = "child";

  private LifeStage() {}
}
