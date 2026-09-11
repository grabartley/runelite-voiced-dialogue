package com.grahambartley.runelite.voiced.dialogue.profile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ProfanityFilter {

  private static final String WORDLIST_RESOURCE = "/profanity.txt";

  private static final Pattern TOKEN =
      Pattern.compile("[\\p{L}\\p{N}@$](?:[\\p{L}\\p{N}._*@$-]*[\\p{L}\\p{N}@$])?");

  private static final char[] LEET_FROM = {'0', '1', '3', '4', '5', '7', '@', '$'};

  private static final char[] LEET_TO = {'o', 'i', 'e', 'a', 's', 't', 'a', 's'};

  private static final Set<String> ALLOWLIST =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "scunthorpe",
                  "assassin",
                  "assassinate",
                  "assassination",
                  "assess",
                  "assassins",
                  "sussex",
                  "penistone",
                  "class",
                  "pass",
                  "bass",
                  "grass",
                  "compass",
                  "cockle",
                  "shitake",
                  "dickens")));

  private final Set<String> blocklist;

  public ProfanityFilter() {
    this.blocklist = loadBundled();
  }

  private Set<String> loadBundled() {
    Set<String> words = new HashSet<>();
    try (InputStream stream = getClass().getResourceAsStream(WORDLIST_RESOURCE)) {
      if (stream == null) {
        log.warn(
            "Profanity wordlist {} not found - profanity masking is a no-op this session",
            WORDLIST_RESOURCE);
        return Collections.emptySet();
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String entry = line.trim();
          if (entry.isEmpty() || entry.charAt(0) == '#') {
            continue;
          }
          String normalized = normalize(entry);
          if (!normalized.isEmpty()) {
            words.add(normalized);
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to load profanity wordlist {}: {}", WORDLIST_RESOURCE, e.getMessage());
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(words);
  }

  static String normalize(String text) {
    StringBuilder out = new StringBuilder(text.length());
    String lower = text.toLowerCase(Locale.ROOT);
    for (int i = 0; i < lower.length(); i++) {
      char c = lower.charAt(i);
      char mapped = leet(c);
      if (mapped >= 'a' && mapped <= 'z') {
        out.append(mapped);
      }
    }
    return out.toString();
  }

  private static char leet(char c) {
    for (int i = 0; i < LEET_FROM.length; i++) {
      if (LEET_FROM[i] == c) {
        return LEET_TO[i];
      }
    }
    return c;
  }

  public String mask(String text) {
    if (text == null || text.isEmpty() || blocklist.isEmpty()) {
      return text;
    }
    Matcher matcher = TOKEN.matcher(text);
    StringBuilder out = null;
    int last = 0;
    while (matcher.find()) {
      String token = matcher.group();
      String normalized = normalize(token);
      if (normalized.isEmpty()
          || ALLOWLIST.contains(normalized)
          || !blocklist.contains(normalized)) {
        continue;
      }
      if (out == null) {
        out = new StringBuilder(text.length());
      }
      out.append(text, last, matcher.start());
      for (int i = matcher.start(); i < matcher.end(); i++) {
        out.append('*');
      }
      last = matcher.end();
    }
    if (out == null) {
      return text;
    }
    out.append(text, last, text.length());
    return out.toString();
  }
}
