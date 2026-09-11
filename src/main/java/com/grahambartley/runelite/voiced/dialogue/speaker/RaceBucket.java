package com.grahambartley.runelite.voiced.dialogue.speaker;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The single race table both text-to-race stages read: the runtime wiki lookup, which turns an
 * infobox race into the bucket name stored in the NPC tables, and {@link NpcDemographicParser},
 * which turns a stored race string into the {@link NpcRace} that picks the voice. Holding the wiki
 * pattern, the stored-text keywords and the voiced race together keeps the two stages from drifting
 * apart, and makes the Gnome bucket riding on the goblin voice an explicit mapping rather than a
 * side effect of an overlapping keyword.
 *
 * <p>The stages deliberately scan in different orders: wiki race text is checked for the
 * distinctive races before the generic human words, while a stored race string reaches human ahead
 * of elf, dwarf and the rest, so a half-blood ("Half Icyene, half human") stays human. The races
 * whose names no human string can contain sit ahead of both.
 *
 * <p>The wiki keywords mirror {@code RACE_BUCKET_RULES} in {@code tools/generate_npc_voices.py} so
 * an NPC learned at runtime buckets the same way as a baked-in one; keep the two in sync.
 */
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

  /**
   * The order wiki race text is scanned in; buckets the wiki never emits are absent. Dog, crab and
   * penguin sit behind undead and demon in both scans, so a risen or demonic one keeps its own
   * bucket.
   */
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

  /** The bucket name stored in the NPC tables and matched against the profile table's races. */
  public String bucketName() {
    return bucketName;
  }

  /** The race this bucket voices as. */
  public NpcRace race() {
    return race;
  }

  /** The bucket a raw wiki race text falls into, or {@code null} when none matches. */
  public static RaceBucket forWikiText(String raceText) {
    String lower = raceText.toLowerCase(Locale.ROOT);
    for (RaceBucket bucket : WIKI_SCAN) {
      if (bucket.wikiPattern.matcher(lower).matches()) {
        return bucket;
      }
    }
    return null;
  }

  /** The bucket a stored race string names exactly, or {@code null} when it names none. */
  static RaceBucket forBucketName(String race) {
    for (RaceBucket bucket : values()) {
      if (bucket.bucketName.equalsIgnoreCase(race)) {
        return bucket;
      }
    }
    return null;
  }

  /** The bucket whose keyword appears in a stored race string, or {@code null} when none does. */
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
