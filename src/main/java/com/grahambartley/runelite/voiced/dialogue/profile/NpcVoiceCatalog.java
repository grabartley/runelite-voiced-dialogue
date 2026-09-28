package com.grahambartley.runelite.voiced.dialogue.profile;

import com.grahambartley.runelite.voiced.dialogue.profile.NpcProfileLayers.Layer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class NpcVoiceCatalog {

  private static final Pattern VARIANT_SUFFIX = Pattern.compile("_VARIANT\\d+");
  private static final Pattern SEX_SUFFIX = Pattern.compile("_[FM](?=_|$)");
  private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");

  private final Map<Integer, String> bundledNames;
  private final Map<Integer, String> symbols;
  private final Map<String, TreeSet<Integer>> bundledIdsByName = new HashMap<>();
  private final Map<String, String> bundledDisplayNames = new HashMap<>();
  private final Map<String, TreeSet<Integer>> idsByCharacter = new HashMap<>();
  private final Map<Integer, String> sessionNames = new ConcurrentHashMap<>();

  public NpcVoiceCatalog(Map<Integer, String> bundledNames, Map<Integer, String> symbols) {
    this.bundledNames = Collections.unmodifiableMap(new HashMap<>(bundledNames));
    this.symbols = Collections.unmodifiableMap(new HashMap<>(symbols));
    for (Map.Entry<Integer, String> entry : this.bundledNames.entrySet()) {
      String key = key(entry.getValue());
      bundledIdsByName.computeIfAbsent(key, k -> new TreeSet<>()).add(entry.getKey());
      bundledDisplayNames.merge(key, entry.getValue(), NpcVoiceCatalog::firstAlphabetically);
    }
    for (Map.Entry<Integer, String> entry : this.symbols.entrySet()) {
      idsByCharacter
          .computeIfAbsent(characterKey(entry.getValue()), k -> new TreeSet<>())
          .add(entry.getKey());
    }
  }

  static NpcVoiceCatalog from(NpcProfileLayers layers) {
    Map<Integer, String> names = new HashMap<>();
    for (Map.Entry<Integer, Layer> entry : layers.byId().entrySet()) {
      String name = entry.getValue().name();
      if (name != null && !name.trim().isEmpty()) {
        names.put(entry.getKey(), name.trim());
      }
    }
    return new NpcVoiceCatalog(names, layers.symbols());
  }

  public void remember(int npcId, String name) {
    if (name != null && !name.trim().isEmpty() && !bundledNames.containsKey(npcId)) {
      sessionNames.put(npcId, name.trim());
    }
  }

  public String nameOf(int npcId) {
    String bundled = bundledNames.get(npcId);
    return bundled != null ? bundled : sessionNames.get(npcId);
  }

  public String displayName(String name) {
    String bundled = bundledDisplayNames.get(key(name));
    return bundled != null ? bundled : name.trim();
  }

  public List<Integer> idsNamed(String name) {
    String key = key(name);
    TreeSet<Integer> ids = new TreeSet<>(bundledIdsByName.getOrDefault(key, new TreeSet<>()));
    for (Map.Entry<Integer, String> entry : sessionNames.entrySet()) {
      if (key(entry.getValue()).equals(key)) {
        ids.add(entry.getKey());
      }
    }
    return new ArrayList<>(ids);
  }

  public List<String> namesMatching(String query) {
    String needle = key(query);
    TreeSet<String> keys = new TreeSet<>();
    for (String key : bundledIdsByName.keySet()) {
      if (key.contains(needle)) {
        keys.add(key);
      }
    }
    for (String name : sessionNames.values()) {
      if (key(name).contains(needle)) {
        keys.add(key(name));
      }
    }
    List<String> names = new ArrayList<>();
    for (String key : keys) {
      names.add(displayNameForKey(key));
    }
    return names;
  }

  public List<Integer> scopeIds(int npcId, NpcVoiceScope scope) {
    switch (scope) {
      case THIS_CHARACTER:
        String symbol = symbols.get(npcId);
        return symbol == null
            ? Collections.singletonList(npcId)
            : new ArrayList<>(idsByCharacter.get(characterKey(symbol)));
      case SAME_NAME:
        String name = nameOf(npcId);
        if (name == null) {
          return Collections.singletonList(npcId);
        }
        TreeSet<Integer> named = new TreeSet<>(idsNamed(name));
        named.add(npcId);
        return new ArrayList<>(named);
      case THIS_NPC:
      default:
        return Collections.singletonList(npcId);
    }
  }

  static String characterKey(String symbol) {
    String stripped = VARIANT_SUFFIX.matcher(symbol).replaceAll("");
    stripped = SEX_SUFFIX.matcher(stripped).replaceAll("");
    return DIGIT_RUN.matcher(stripped).replaceAll("");
  }

  private String displayNameForKey(String key) {
    String bundled = bundledDisplayNames.get(key);
    if (bundled != null) {
      return bundled;
    }
    for (String name : sessionNames.values()) {
      if (key(name).equals(key)) {
        return name;
      }
    }
    return key;
  }

  private static String firstAlphabetically(String a, String b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  private static String key(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }
}
