package com.grahambartley.runelite.voiced.dialogue;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

public class PackageDependencyTest {

  private static final String BASE = "com.grahambartley.runelite.voiced.dialogue";
  private static final Path MAIN = Paths.get("src/main/java", BASE.replace('.', '/'));

  private static final Map<String, Integer> TIER = new HashMap<>();

  static {
    TIER.put("", 7);
    TIER.put("capture", 6);
    TIER.put("speech.openrouter", 6);
    TIER.put("speech.aistudio", 6);
    TIER.put("speech", 5);
    TIER.put("cache", 4);
    TIER.put("speech.model", 4);
    TIER.put("profile", 3);
    TIER.put("speaker", 2);
    TIER.put("audio", 1);
    TIER.put("speech.spend", 1);
  }

  private static final Set<String> ROOT_TYPES_ANY_TIER =
      Collections.singleton("VoicedDialogueConfig");

  private static final Pattern IMPORT =
      Pattern.compile("^import (?:static )?" + Pattern.quote(BASE) + "\\.([\\w.]+);");

  private static String packageOfImport(String path) {
    StringBuilder pkg = new StringBuilder();
    for (String segment : path.split("\\.")) {
      if (!Character.isLowerCase(segment.charAt(0))) {
        break;
      }
      pkg.append(pkg.length() == 0 ? "" : ".").append(segment);
    }
    return pkg.toString();
  }

  @Test
  public void everyPackageIsPlacedInTheLayering() throws IOException {
    for (Path dir : walk(Files::isDirectory)) {
      assertTrue(
          "package '"
              + packageOf(dir)
              + "' is missing from the layering; place it here and in"
              + " docs/development.md",
          TIER.containsKey(packageOf(dir)));
    }
  }

  @Test
  public void noImportRunsAgainstTheLayering() throws IOException {
    List<String> violations = new ArrayList<>();
    for (Path source : sources()) {
      String from = packageOf(source.getParent());
      for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
        Matcher matcher = IMPORT.matcher(line);
        if (!matcher.find()) {
          continue;
        }
        String to = packageOfImport(matcher.group(1));
        if (from.equals(to)) {
          continue;
        }
        if (to.isEmpty()) {
          String type = matcher.group(1).split("\\.")[0];
          if (!ROOT_TYPES_ANY_TIER.contains(type)) {
            violations.add(
                String.format(
                    "%s imports root type %s in %s", label(from), type, source.getFileName()));
          }
          continue;
        }
        if (!TIER.containsKey(to)) {
          violations.add(
              String.format(
                  "%s imports unplaced package %s in %s",
                  label(from), label(to), source.getFileName()));
          continue;
        }
        if (TIER.get(from) > TIER.get(to)) {
          continue;
        }
        violations.add(
            String.format(
                "%s (tier %d) imports %s (tier %d) in %s",
                label(from), TIER.get(from), to, TIER.get(to), source.getFileName()));
      }
    }
    if (!violations.isEmpty()) {
      fail("imports run against the package layering:\n  " + String.join("\n  ", violations));
    }
  }

  @Test
  public void everySourceFileSitsInTheDirectoryItsPackageNames() throws IOException {
    for (Path source : sources()) {
      String pkg = packageOf(source.getParent());
      String expected = pkg.isEmpty() ? BASE : BASE + "." + pkg;
      assertEquals("misplaced source file " + source, expected, declaredPackage(source));
    }
  }

  private static String label(String pkg) {
    return pkg.isEmpty() ? "<root>" : pkg;
  }

  private static String declaredPackage(Path source) throws IOException {
    for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
      if (line.startsWith("package ")) {
        return line.substring("package ".length()).replace(";", "").trim();
      }
    }
    throw new AssertionError("no package declaration in " + source);
  }

  private static String packageOf(Path dir) {
    return MAIN.relativize(dir).toString().replace('/', '.');
  }

  private static List<Path> sources() throws IOException {
    return walk(path -> path.toString().endsWith(".java"));
  }

  private static List<Path> walk(java.util.function.Predicate<Path> keep) throws IOException {
    try (Stream<Path> walk = Files.walk(MAIN)) {
      return walk.filter(keep).sorted().collect(toList());
    }
  }
}
