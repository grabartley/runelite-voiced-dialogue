package com.grahambartley.runelite.voiced.dialogue.speech.model;

import com.grahambartley.runelite.voiced.dialogue.profile.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcRace;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public final class GeminiVoiceMap {

  static final String DEFAULT_VOICE = "Charon";

  static final String DEFAULT_CHILD_VOICE = "Puck";

  static final String FALLBACK_NARRATOR_VOICE = "Callirrhoe";

  static final int PLAYER_SEED = 0;

  static final int FOLLOWER_SEED = 1;

  private final Map<NpcRace, Map<NpcGender, String[]>> npcVoices;
  private final Map<NpcGender, String[]> playerVoices;
  private final Map<NpcGender, String[]> followerVoices;
  private final Map<NpcGender, String[]> childVoices;
  private final GeminiVoiceRegions regions;

  public GeminiVoiceMap() {
    this(GeminiVoiceRegions.bundled());
  }

  GeminiVoiceMap(GeminiVoiceRegions regions) {
    this.regions = regions;
    playerVoices = new EnumMap<>(NpcGender.class);
    playerVoices.put(NpcGender.MALE, new String[] {"Achird", "Iapetus"});
    playerVoices.put(NpcGender.FEMALE, new String[] {"Aoede", "Autonoe"});

    followerVoices = new EnumMap<>(NpcGender.class);
    followerVoices.put(NpcGender.MALE, new String[] {"Iapetus"});
    followerVoices.put(NpcGender.FEMALE, new String[] {"Laomedeia"});

    childVoices = new EnumMap<>(NpcGender.class);
    childVoices.put(NpcGender.MALE, new String[] {"Puck"});
    childVoices.put(NpcGender.FEMALE, new String[] {"Leda", "Zephyr"});

    npcVoices = new EnumMap<>(NpcRace.class);
    put(NpcRace.HUMAN, male("Charon", "Iapetus"), female("Despina", "Erinome"));
    put(NpcRace.ELF, male("Iapetus", "Rasalgethi"), female("Vindemiatrix", "Erinome"));
    put(NpcRace.DWARF, male("Algenib", "Alnilam"), female("Gacrux", "Kore"));
    put(NpcRace.GOBLIN, male("Puck", "Zubenelgenubi"), female("Leda", "Laomedeia"));
    put(NpcRace.MONKEY, male("Fenrir", "Sadachbia"), female("Zephyr", "Pulcherrima"));
    put(NpcRace.GORILLA, male("Algenib", "Orus"), female("Gacrux", "Kore"));
    put(NpcRace.TROLL, male("Algenib", "Orus"), female("Gacrux", "Kore"));
    put(NpcRace.UNDEAD, male("Enceladus", "Schedar"), female("Achernar", "Sulafat"));
    put(NpcRace.DEMON, male("Algenib", "Rasalgethi"), female("Gacrux", "Despina"));
    put(NpcRace.WIZARD, male("Sadaltager", "Charon"), female("Sulafat", "Vindemiatrix"));
    put(NpcRace.TORTUGAN, male("Achird"), female("Sulafat", "Vindemiatrix"));

    put(NpcRace.ICYENE, male("Alnilam", "Schedar"), female("Kore", "Despina"));
    put(NpcRace.ARCEUUS, male("Iapetus", "Rasalgethi"), female("Vindemiatrix", "Erinome"));
    put(NpcRace.ARANEI, male("Enceladus", "Iapetus"), female("Achernar", "Erinome"));
    put(NpcRace.DOG, male("Fenrir", "Orus"), female("Pulcherrima", "Gacrux"));
    put(NpcRace.CRAB, male("Zubenelgenubi", "Sadachbia"), female("Pulcherrima", "Laomedeia"));
    put(NpcRace.PENGUIN, male("Puck", "Zubenelgenubi"), female("Zephyr", "Laomedeia"));
  }

  private static String[] male(String... voices) {
    return voices;
  }

  private static String[] female(String... voices) {
    return voices;
  }

  private void put(NpcRace race, String[] maleVoices, String[] femaleVoices) {
    Map<NpcGender, String[]> byGender = new EnumMap<>(NpcGender.class);
    byGender.put(NpcGender.MALE, maleVoices);
    byGender.put(NpcGender.FEMALE, femaleVoices);
    npcVoices.put(race, byGender);
  }

  public String voiceFor(VoiceSpec spec, CharacterProfile profile) {
    if (spec == null) {
      return DEFAULT_VOICE;
    }
    if (spec.narrator()) {
      String narrator = regions.narratorVoice();
      return narrator != null ? narrator : FALLBACK_NARRATOR_VOICE;
    }
    NpcGender gender = NpcGender.orDefault(spec.gender());
    String region = regionFor(spec, profile);
    if (spec.player()) {
      String regional = regions.voiceFor(region, gender, PLAYER_SEED);
      return regional != null ? regional : anchor(playerVoices.get(gender));
    }
    if (spec.follower()) {
      String ownersVoice = regions.voiceFor(region, gender, PLAYER_SEED);
      String regional = regions.voiceExcluding(region, gender, FOLLOWER_SEED, ownersVoice);
      return regional != null ? regional : anchor(followerVoices.get(gender));
    }
    if (spec.child()) {
      String regional = regions.childVoiceFor(region, gender, spec.voiceSeed());
      if (regional != null) {
        return regional;
      }
      String[] pool = childVoices.get(gender);
      return (pool == null || pool.length == 0) ? DEFAULT_CHILD_VOICE : pick(pool, spec);
    }
    String regional = regions.voiceFor(region, gender, spec.voiceSeed());
    if (regional != null) {
      return regional;
    }
    Map<NpcGender, String[]> byGender = npcVoices.get(spec.race());
    if (byGender == null) {
      return pick(playerVoices.get(gender), spec);
    }
    return pick(byGender.get(gender), spec);
  }

  public String regionFor(VoiceSpec spec, CharacterProfile profile) {
    if (spec == null || spec.narrator()) {
      return null;
    }
    if (spec.player() || spec.follower()) {
      return regions.regionForAccent(profile == null ? null : profile.accent());
    }
    return npcRegion(spec, profile);
  }

  private String npcRegion(VoiceSpec spec, CharacterProfile profile) {
    if (profile == null || !spec.hasVoiceSeed()) {
      return null;
    }
    if (profile.accentOverridden()) {
      String matched = regions.regionForAccent(profile.accent());
      if (matched != null) {
        return matched;
      }
    }
    return profile.voiceRegion();
  }

  private static String anchor(String[] pool) {
    return (pool == null || pool.length == 0) ? DEFAULT_VOICE : pool[0];
  }

  private static String pick(String[] pool, VoiceSpec spec) {
    if (pool == null || pool.length == 0) {
      return DEFAULT_VOICE;
    }
    if (!spec.hasVoiceSeed()) {
      return pool[0];
    }
    return GeminiVoiceRegions.pick(Arrays.asList(pool), spec.voiceSeed());
  }
}
