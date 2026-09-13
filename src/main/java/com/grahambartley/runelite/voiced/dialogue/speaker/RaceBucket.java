package com.grahambartley.runelite.voiced.dialogue.speaker;

public enum RaceBucket {
  ARCEUUS("Arceuus", NpcRace.ARCEUUS, "arceuus"),
  ARANEI("Aranei", NpcRace.ARANEI, "aranei"),
  HUMAN("Human", NpcRace.HUMAN, "human", "man", "person"),
  ELF("Elf", NpcRace.ELF, "elf", "elven"),
  DWARF("Dwarf", NpcRace.DWARF, "dwarf", "dwarven"),
  GNOME("Gnome", NpcRace.GOBLIN, "gnome"),
  GOBLIN("Goblin", NpcRace.GOBLIN, "goblin"),
  TROLL("Troll", NpcRace.TROLL, "troll", "giant"),
  UNDEAD("Undead", NpcRace.UNDEAD, "undead", "skeleton", "zombie", "ghost", "revenant", "reanimat"),
  DEMON("Demon", NpcRace.DEMON, "demon", "dragon", "devil", "hellhound"),
  DOG("Dog", NpcRace.DOG, "dog"),
  CRAB("Crab", NpcRace.CRAB, "crab"),
  PENGUIN("Penguin", NpcRace.PENGUIN, "penguin"),
  GORILLA("Gorilla", NpcRace.GORILLA, "gorilla"),
  MONKEY("Monkey", NpcRace.MONKEY, "monkey", "primate"),
  WIZARD("Wizard", NpcRace.WIZARD, "wizard", "mage"),
  TORTUGAN("Tortugan", NpcRace.TORTUGAN, "tortugan", "tortuga"),
  ICYENE("Icyene", NpcRace.ICYENE, "icyene");

  private final String bucketName;
  private final NpcRace race;
  private final String[] keywords;

  RaceBucket(String bucketName, NpcRace race, String... keywords) {
    this.bucketName = bucketName;
    this.race = race;
    this.keywords = keywords;
  }

  public String bucketName() {
    return bucketName;
  }

  public NpcRace race() {
    return race;
  }

  static RaceBucket forBucketName(String race) {
    for (RaceBucket bucket : values()) {
      if (bucket.bucketName.equalsIgnoreCase(race)) {
        return bucket;
      }
    }
    return null;
  }

  static RaceBucket forKeyword(String race) {
    String lower = race.toLowerCase();
    for (RaceBucket bucket : values()) {
      for (String keyword : bucket.keywords) {
        if (lower.contains(keyword)) {
          return bucket;
        }
      }
    }
    return null;
  }
}
