package com.grahambartley.runelite.voiced.dialogue.speaker;

import lombok.Data;

@Data
public class NpcAttributes {

  private String race;

  private String gender;

  private int npcId;

  private String source;

  private String ethnicity;

  private String lifeStage;

  public NpcAttributes(String race, String gender, String source) {
    this.race = race;
    this.gender = gender;
    this.source = source;
  }

  public boolean isChild() {
    return LifeStage.CHILD.equalsIgnoreCase(lifeStage);
  }
}
