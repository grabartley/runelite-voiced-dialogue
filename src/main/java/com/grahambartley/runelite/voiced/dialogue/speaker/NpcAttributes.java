package com.grahambartley.runelite.voiced.dialogue.speaker;

import lombok.Data;

/** The demographic attributes voice resolution reads for one NPC. */
@Data
public class NpcAttributes {

  private String race;

  private String gender;

  /** The id these attributes were matched under, which for a multiloc NPC may be its base id. */
  private int npcId;

  /** Which table the attributes came from; one of the {@link AttributeSource} values. */
  private String source;

  /** The NPC's ethnicity accent key (e.g. "kharidian"), or null when not ethnically distinct. */
  private String ethnicity;

  /** The NPC's {@link LifeStage} marker, or null for an adult. */
  private String lifeStage;

  public NpcAttributes(String race, String gender, String source) {
    this.race = race;
    this.gender = gender;
    this.source = source;
  }

  /** Whether this NPC is marked as a child (voiced from the youthful sub-pool). */
  public boolean isChild() {
    return LifeStage.CHILD.equalsIgnoreCase(lifeStage);
  }
}
