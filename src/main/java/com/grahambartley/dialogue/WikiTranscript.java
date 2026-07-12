package com.grahambartley.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A conservatively parsed OSRS Wiki transcript that predicts immediate dialogue successors. */
public final class WikiTranscript {

  public enum Speaker {
    NPC,
    PLAYER
  }

  public static final class Line {
    private final Speaker speaker;
    private final String text;

    Line(Speaker speaker, String text) {
      this.speaker = speaker;
      this.text = text;
    }

    public Speaker speaker() {
      return speaker;
    }

    public String text() {
      return text;
    }
  }

  private enum Kind {
    SPEECH,
    OPTION,
    END,
    BARRIER
  }

  private static final Pattern SPEECH = Pattern.compile("^(\\*+)\\s*'''([^']+):'''\\s*(.+?)\\s*$");
  private static final Pattern OPTION =
      Pattern.compile("^(\\*+)\\s*\\{\\{topt\\|(.+?)}}\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern END =
      Pattern.compile("^(\\*+)\\s*\\{\\{tact\\|end(?:\\|[^}]*)?}}", Pattern.CASE_INSENSITIVE);
  private static final Pattern CONDITION =
      Pattern.compile("^(\\*+)\\s*\\{\\{tcond\\|", Pattern.CASE_INSENSITIVE);
  private static final Pattern ACTION =
      Pattern.compile("^(\\*+)\\s*\\{\\{tact\\|", Pattern.CASE_INSENSITIVE);
  private static final Pattern HEADING = Pattern.compile("^=+.+?=+\\s*$");
  private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[(?:[^]|]+\\|)?([^]]+)]]");
  private static final Pattern DYNAMIC =
      Pattern.compile("\\[[^]]+]|\\{\\{[^}]+}}", Pattern.CASE_INSENSITIVE);
  private static final int MAX_SUCCESSORS = 2;

  private static final class Entry {
    final Kind kind;
    final int depth;
    final Speaker speaker;
    final String text;

    Entry(Kind kind, int depth, Speaker speaker, String text) {
      this.kind = kind;
      this.depth = depth;
      this.speaker = speaker;
      this.text = text;
    }
  }

  private final List<Entry> entries;
  private final Map<String, List<Integer>> speechIndexes;

  private WikiTranscript(List<Entry> entries) {
    this.entries = entries;
    this.speechIndexes = indexSpeech(entries);
  }

  public static WikiTranscript parse(String wikitext, String npcName) {
    if (wikitext == null || wikitext.isEmpty()) {
      return new WikiTranscript(Collections.emptyList());
    }
    List<Entry> entries = new ArrayList<>();
    for (String raw : wikitext.split("\\r?\\n")) {
      if (HEADING.matcher(raw).matches()) {
        entries.add(new Entry(Kind.BARRIER, 0, null, null));
        continue;
      }
      Matcher end = END.matcher(raw);
      if (end.find()) {
        entries.add(new Entry(Kind.END, end.group(1).length(), null, null));
        continue;
      }
      Matcher condition = CONDITION.matcher(raw);
      if (condition.find()) {
        entries.add(new Entry(Kind.BARRIER, condition.group(1).length(), null, null));
        continue;
      }
      Matcher action = ACTION.matcher(raw);
      if (action.find()) {
        entries.add(new Entry(Kind.BARRIER, action.group(1).length(), null, null));
        continue;
      }
      Matcher option = OPTION.matcher(raw);
      if (option.matches()) {
        String text = clean(option.group(2));
        if (usable(text)) {
          entries.add(new Entry(Kind.OPTION, option.group(1).length(), Speaker.PLAYER, text));
        }
        continue;
      }
      Matcher speech = SPEECH.matcher(raw);
      if (!speech.matches()) {
        continue;
      }
      String text = clean(speech.group(3));
      if (!usable(text)) {
        continue;
      }
      String speakerName = clean(speech.group(2));
      Speaker speaker;
      if ("player".equalsIgnoreCase(speakerName)) {
        speaker = Speaker.PLAYER;
      } else if (npcName != null && normalize(speakerName).equals(normalize(npcName))) {
        speaker = Speaker.NPC;
      } else {
        entries.add(new Entry(Kind.BARRIER, speech.group(1).length(), null, null));
        continue;
      }
      entries.add(new Entry(Kind.SPEECH, speech.group(1).length(), speaker, text));
    }
    return new WikiTranscript(entries);
  }

  /** Returns up to two distinct immediate successors for an exact normalized line match. */
  public List<Line> successors(String currentText) {
    String wanted = normalize(currentText);
    if (wanted.isEmpty()) {
      return Collections.emptyList();
    }
    List<Line> result = null;
    for (int i : speechIndexes.getOrDefault(wanted, Collections.emptyList())) {
      Entry current = entries.get(i);
      List<Line> candidate = new ArrayList<>();
      collectSuccessors(i, current, new LinkedHashSet<>(), candidate);
      if (result == null) {
        result = candidate;
      } else if (!sameLines(result, candidate)) {
        return Collections.emptyList();
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private static Map<String, List<Integer>> indexSpeech(List<Entry> entries) {
    Map<String, List<Integer>> indexes = new HashMap<>();
    for (int i = 0; i < entries.size(); i++) {
      Entry entry = entries.get(i);
      if (entry.text != null) {
        indexes.computeIfAbsent(normalize(entry.text), ignored -> new ArrayList<>()).add(i);
      }
    }
    return indexes;
  }

  private void collectSuccessors(int index, Entry current, Set<String> seen, List<Line> result) {
    for (int i = index + 1; i < entries.size() && result.size() < MAX_SUCCESSORS; i++) {
      Entry next = entries.get(i);
      if (next.kind == Kind.BARRIER) {
        return;
      }
      if (next.depth < current.depth) {
        return;
      }
      if (next.kind == Kind.END && next.depth <= Math.max(1, current.depth)) {
        return;
      }
      if (next.text == null) {
        continue;
      }
      if (normalize(next.text).equals(normalize(current.text))) {
        continue;
      }
      if (next.kind == Kind.OPTION) {
        int optionDepth = next.depth;
        add(next, seen, result);
        for (int j = i + 1; j < entries.size() && result.size() < MAX_SUCCESSORS; j++) {
          Entry candidate = entries.get(j);
          if (candidate.depth < optionDepth) {
            return;
          }
          if (candidate.kind == Kind.BARRIER || candidate.kind == Kind.END) {
            if (candidate.depth <= optionDepth) {
              return;
            }
            continue;
          }
          if (candidate.kind == Kind.OPTION && candidate.depth == optionDepth) {
            add(candidate, seen, result);
          }
        }
        return;
      }
      if (next.kind == Kind.SPEECH) {
        add(next, seen, result);
        return;
      }
    }
  }

  private static void add(Entry entry, Set<String> seen, List<Line> result) {
    String key = entry.speaker + "\u0001" + normalize(entry.text);
    if (seen.add(key)) {
      result.add(new Line(entry.speaker, entry.text));
    }
  }

  private static boolean sameLines(List<Line> left, List<Line> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int i = 0; i < left.size(); i++) {
      Line a = left.get(i);
      Line b = right.get(i);
      if (a.speaker != b.speaker || !normalize(a.text).equals(normalize(b.text))) {
        return false;
      }
    }
    return true;
  }

  private static boolean usable(String text) {
    return text != null && !text.isEmpty() && !DYNAMIC.matcher(text).find() && text.length() <= 500;
  }

  private static String clean(String text) {
    if (text == null) {
      return "";
    }
    Matcher links = WIKI_LINK.matcher(text);
    StringBuffer plain = new StringBuffer();
    while (links.find()) {
      links.appendReplacement(plain, Matcher.quoteReplacement(links.group(1)));
    }
    links.appendTail(plain);
    return plain
        .toString()
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replaceAll("<[^>]+>", "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static String normalize(String text) {
    return clean(text).toLowerCase(Locale.ROOT);
  }
}
