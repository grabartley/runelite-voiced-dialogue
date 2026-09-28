package com.grahambartley.runelite.voiced.dialogue.panel;

import com.grahambartley.runelite.voiced.dialogue.profile.HeardNpc;
import com.grahambartley.runelite.voiced.dialogue.profile.NpcVoiceCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class NpcListEntries {

  static final int MATCH_LIMIT = 50;

  private final NpcVoiceCatalog catalog;

  NpcListEntries(NpcVoiceCatalog catalog) {
    this.catalog = catalog;
  }

  NpcListing build(String query, List<HeardNpc> heard, Set<Integer> editedIds) {
    Map<String, Integer> heardIdByKey = new LinkedHashMap<>();
    for (HeardNpc npc : heard) {
      heardIdByKey.putIfAbsent(key(npc.name()), npc.id());
    }
    Map<String, String> editedNameByKey = new TreeMap<>();
    Set<Integer> unnamed = new HashSet<>();
    for (int id : editedIds) {
      String name = catalog.nameOf(id);
      if (name == null) {
        unnamed.add(id);
      } else {
        editedNameByKey.putIfAbsent(key(name), name);
      }
    }
    Context context = new Context(heardIdByKey, editedIds);

    String needle = query == null ? "" : query.trim();
    if (needle.isEmpty()) {
      List<NpcListEntry> heardEntries = new ArrayList<>();
      for (HeardNpc npc : heard) {
        if (heardEntries.stream().noneMatch(e -> key(e.name()).equals(key(npc.name())))) {
          heardEntries.add(entry(npc.name(), context));
        }
      }
      List<NpcListEntry> editedEntries = new ArrayList<>();
      for (Map.Entry<String, String> edited : editedNameByKey.entrySet()) {
        if (!heardIdByKey.containsKey(edited.getKey())) {
          editedEntries.add(entry(edited.getValue(), context));
        }
      }
      return new NpcListing(heardEntries, editedEntries, null, false, unnamed);
    }

    String lowerNeedle = key(needle);
    List<NpcListEntry> matches = new ArrayList<>();
    for (String name : catalog.namesMatching(needle)) {
      matches.add(entry(name, context));
    }
    matches.sort(
        Comparator.comparingInt(NpcListEntries::rank)
            .thenComparing(e -> key(e.name()).startsWith(lowerNeedle) ? 0 : 1)
            .thenComparing(e -> key(e.name())));
    boolean truncated = matches.size() > MATCH_LIMIT;
    List<NpcListEntry> shown =
        truncated ? new ArrayList<>(matches.subList(0, MATCH_LIMIT)) : matches;
    return new NpcListing(null, null, shown, truncated, unnamed);
  }

  private NpcListEntry entry(String name, Context context) {
    String display = catalog.displayName(name);
    String key = key(display);
    TreeSet<Integer> ids = new TreeSet<>(catalog.idsNamed(display));
    Integer heardId = context.heardIdByKey.get(key);
    if (heardId != null) {
      ids.add(heardId);
    }
    Integer firstEdited = null;
    for (int id : ids) {
      if (context.editedIds.contains(id)) {
        firstEdited = id;
        break;
      }
    }
    int preferred = heardId != null ? heardId : firstEdited != null ? firstEdited : ids.first();
    return new NpcListEntry(
        display, new ArrayList<>(ids), heardId != null, firstEdited != null, preferred);
  }

  private static int rank(NpcListEntry entry) {
    return entry.heard() ? 0 : entry.edited() ? 1 : 2;
  }

  private static String key(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }

  private static final class Context {
    private final Map<String, Integer> heardIdByKey;
    private final Set<Integer> editedIds;

    private Context(Map<String, Integer> heardIdByKey, Set<Integer> editedIds) {
      this.heardIdByKey = heardIdByKey;
      this.editedIds = editedIds;
    }
  }
}
