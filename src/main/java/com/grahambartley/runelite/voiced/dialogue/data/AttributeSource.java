package com.grahambartley.runelite.voiced.dialogue.data;

/**
 * Where a set of {@link NpcAttributes} came from. The value rides on the attributes and is read
 * back by voice resolution, so the tables that produce it and the code that tests for it share
 * these constants rather than repeating the literals.
 */
public final class AttributeSource {

  /** The bundled, precomputed NPC table. */
  public static final String STATIC_TABLE = "StaticTable";

  /** The runtime cache of NPCs learned from the wiki. */
  public static final String LEARNED = "Learned";

  /** A live wiki lookup. */
  public static final String WIKI = "Wiki";

  /** No table knew the NPC, so its attributes were defaulted. */
  public static final String DEFAULT = "Default";

  private AttributeSource() {}
}
