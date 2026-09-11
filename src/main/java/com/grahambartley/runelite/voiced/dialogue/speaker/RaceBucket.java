package com.grahambartley.runelite.voiced.dialogue.speaker;

import java.util.Locale;
import java.util.regex.Pattern;

public enum RaceBucket {
  ARCEUUS("Arceuus", NpcRace.ARCEUUS, null, "arceuus"),
  ARANEI("Aranei", NpcRace.ARANEI, "\\baranei\\b", "aranei"),
  HUMAN("Human", NpcRace.HUMAN, "\\bhuman\\b|\\bman\\b|\\bwoman\\b", "human", "man", "person"),
  ELF("Elf", NpcRace.ELF, "\\belf\\b|\\belves\\b|elven", "elf", "elven"),
  DWARF("Dwarf", NpcRace.DWARF, "dwarf|dwarven", "dwarf", "dwarven"),
  GNOME("Gnome", NpcRace.GOBLIN, "gnome", "gnome"),
  GOBLIN("Goblin", NpcRace.GOBLIN, "goblin|hobgoblin", "goblin"),
  TROLL(
      "Troll",
      NpcRace.TROLL,
      "troll|\\bgiant\\b|cyclops|ogre|\\bent\\b|\\bgolem\\b",
      "troll",
      "giant"),
  UNDEAD(
      "Undead",
      NpcRace.UNDEAD,
      "vampyre|vampire|\\bvyre\\b|zombie|skeleton|ghost|ghoul|undead|wight|shade|revenant|"
          + "mummy|banshee|spectre|wraith|ankou|lich|reanimat",
      "undead",
      "skeleton",
      "zombie",
      "ghost",
      "revenant",
      "reanimat"),
  DEMON(
      "Demon",
      NpcRace.DEMON,
      "demon|devil|\\bimp\\b|abyssal|dragon|wyvern|wyrm|drake|tzhaar|tztok|tzkal|hellhound",
      "demon",
      "dragon",
      "devil",
      "hellhound"),
  DOG("Dog", NpcRace.DOG, "\\bdogs?\\b", "dog"),
  CRAB("Crab", NpcRace.CRAB, "\\bcrabs?\\b", "crab"),
  PENGUIN("Penguin", NpcRace.PENGUIN, "\\bpenguins?\\b", "penguin"),
  GORILLA("Gorilla", NpcRace.GORILLA, null, "gorilla"),
  MONKEY("Monkey", NpcRace.MONKEY, "monkey|gorilla|primate|baboon|mandril", "monkey", "primate"),
  WIZARD(
      "Wizard",
      NpcRace.WIZARD,
      "wizard|sorcerer|sorceress|necromancer|\\bmage\\b",
      "wizard",
      "mage"),
  TORTUGAN("Tortugan", NpcRace.TORTUGAN, null, "tortugan", "tortuga"),
  ICYENE("Icyene", NpcRace.ICYENE, null, "icyene");

  private static final RaceBucket[] WIKI_SCAN = {
    ARANEI, UNDEAD, DEMON, DOG, CRAB, PENGUIN, GNOME, GOBLIN, MONKEY, DWARF, ELF, TROLL, WIZARD,
    HUMAN
  };

  private final String bucketName;
  private final NpcRace race;
  private final Pattern wikiPattern;
  private final String[] keywords;

  RaceBucket(String bucketName, NpcRace race, String wikiKeywords, String... keywords) {
    this.bucketName = bucketName;
    this.race = race;
    this.wikiPattern = wikiKeywords == null ? null : Pattern.compile(".*(" + wikiKeywords + ").*");
    this.keywords = keywords;
  }

  public String bucketName() {
    return bucketName;
  }

  public NpcRace race() {
    return race;
  }

  public static RaceBucket forWikiText(String raceText) {
    String lower = raceText.toLowerCase(Locale.ROOT);
    for (RaceBucket bucket : WIKI_SCAN) {
      if (bucket.wikiPattern.matcher(lower).matches()) {
        return bucket;
      }
    }
    return null;
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
