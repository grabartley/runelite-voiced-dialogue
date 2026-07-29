package com.grahambartley.voice;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.grahambartley.data.NpcProfileTable;
import com.grahambartley.synthesis.CharacterProfile;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

/**
 * Golden snapshot over <em>every</em> NPC in the bundled table: for each of the ~13.7k ids in
 * {@code /npc-voices.json}, it re-runs the real {@link NpcProfileTable} resolver and asserts the
 * complete final resolved state (demographics + resolved {@link CharacterProfile} + {@code source}
 * + resolved child flag) matches the committed {@code /npc-resolved-golden.json} row for that id.
 *
 * <p>The golden is the human-reviewable record of what every NPC sounds like. Any edit to a mapping
 * file ({@code tools/overrides.json} or {@code tools/profiles.json}, then regenerating {@code
 * npc-voices.json}) moves the resolved output; if the golden is not regenerated with it the
 * affected rows fail here, so the golden diff is exactly the "which NPCs' final voices changed"
 * artifact.
 *
 * <p>Regenerate after an intentional mapping change and review the diff:
 *
 * <pre>{@code
 * ./gradlew test --tests '*ResolvedNpcVoiceGoldenTest' -Dgolden.regenerate=true --rerun-tasks
 * }</pre>
 *
 * Regeneration writes the golden from this same Java resolver, so the golden can never drift from
 * the layering logic. Normal runs only compare.
 *
 * <p>Scope: this is the data-driven resolution surface only (resolve-by-id from the committed
 * table). The live world-scan ({@code NpcFinder}) and multiloc composition-id transform steps need
 * a running client and stay covered by their own unit tests; they are lookup/transform plumbing,
 * not mapping data, so a mapping edit never moves them.
 *
 * <p>Form: a single aggregating test rather than ~13.7k discrete cases. The issue's stated
 * preference is discrete parameterized cases, but at this volume they bog down the runner and
 * report (the issue explicitly sanctions this fallback); every mismatch still carries its {@code
 * id}, name, and the field that drifted, so a failure reads like a per-NPC case.
 */
public class ResolvedNpcVoiceGoldenTest {

  private static final String NPCS_RESOURCE = "/npc-voices.json";
  private static final String NAMES_RESOURCE = "/npc-names.json";
  private static final String GOLDEN_RESOURCE = "/npc-resolved-golden.json";
  private static final Path GOLDEN_SOURCE_PATH =
      Paths.get("src", "test", "resources", "npc-resolved-golden.json");

  // serializeNulls so an absent ethnicity/lifeStage is written as an explicit `null` rather than
  // dropped: the golden then records every field for every NPC, and a field flipping from set to
  // null (e.g. an override clearing a wrong ethnicity) surfaces as a diff instead of vanishing.
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

  /** Cap the mismatch lines rendered in a failure so the message stays readable on a wide drift. */
  private static final int MAX_REPORTED = 100;

  @Test
  public void everyNpcResolvesToItsCommittedGoldenProfile() throws IOException {
    JsonObject npcs = readResource(NPCS_RESOURCE).getAsJsonObject("npcs");
    JsonObject names = readResource(NAMES_RESOURCE);

    NpcProfileTable table = new NpcProfileTable();
    table.initialize();
    assertTrue("the bundled profile table loaded", table.isLoaded());

    // Numeric id order so the golden file and any diff read predictably (1, 2, 10, not 1, 10, 2).
    TreeMap<Integer, String> ids = new TreeMap<>();
    for (String key : npcs.keySet()) {
      ids.put(Integer.parseInt(key), key);
    }

    if (Boolean.getBoolean("golden.regenerate")) {
      JsonObject regenerated = new JsonObject();
      for (Map.Entry<Integer, String> e : ids.entrySet()) {
        regenerated.add(e.getValue(), resolvedRow(e.getKey(), npcs, names, table));
      }
      Files.write(
          GOLDEN_SOURCE_PATH, (GSON.toJson(regenerated) + "\n").getBytes(StandardCharsets.UTF_8));
      return;
    }

    JsonObject golden = readResource(GOLDEN_RESOURCE);
    List<String> mismatches = new ArrayList<>();
    for (Map.Entry<Integer, String> e : ids.entrySet()) {
      int id = e.getKey();
      String key = e.getValue();
      String name = optString(names, key);
      JsonElement expected = golden.get(key);
      if (expected == null || !expected.isJsonObject()) {
        mismatches.add(id + " " + label(name) + " -> no golden row (regenerate the golden)");
        continue;
      }
      diffRow(
          id, name, expected.getAsJsonObject(), resolvedRow(id, npcs, names, table), mismatches);
    }
    for (String key : golden.keySet()) {
      if (!npcs.has(key)) {
        mismatches.add(
            key + " -> in the golden but no longer in the bundled table (regenerate the golden)");
      }
    }

    if (!mismatches.isEmpty()) {
      fail(failureMessage(mismatches));
    }
  }

  /** The complete final resolved state for one NPC: demographics + resolved profile + source. */
  private static JsonObject resolvedRow(
      int id, JsonObject npcs, JsonObject names, NpcProfileTable table) {
    JsonObject demo = npcs.getAsJsonObject(String.valueOf(id));
    String name = optString(names, String.valueOf(id));
    String race = optString(demo, "race");
    String gender = optString(demo, "gender");
    String ethnicity = optString(demo, "ethnicity");
    String lifeStage = optString(demo, "lifeStage");

    NpcProfileTable.Resolution resolution = table.resolveNpc(id, name, race, ethnicity);
    CharacterProfile p = resolution.profile();
    boolean child = "child".equals(lifeStage) || table.isChildName(name);

    JsonObject profile = new JsonObject();
    profile.addProperty("name", p.name());
    profile.addProperty("accent", p.accent());
    profile.addProperty("style", p.style());
    profile.addProperty("pace", p.pace());

    JsonObject row = new JsonObject();
    row.addProperty("id", id);
    row.addProperty("name", name);
    row.addProperty("race", race);
    row.addProperty("gender", gender);
    row.add("ethnicity", nullable(ethnicity));
    row.add("lifeStage", nullable(lifeStage));
    row.addProperty("child", child);
    row.add("profile", profile);
    row.addProperty("source", resolution.source());
    return row;
  }

  /**
   * Appends one message per drifting field so a failure names exactly what changed for this NPC.
   */
  private static void diffRow(
      int id, String name, JsonObject expected, JsonObject actual, List<String> mismatches) {
    diffField(id, name, "", expected, actual, mismatches);
  }

  private static void diffField(
      int id,
      String name,
      String prefix,
      JsonObject expected,
      JsonObject actual,
      List<String> mismatches) {
    // Union of both sides so a field present in only one (a stale golden key, or a newly added
    // one) is still compared rather than skipped.
    LinkedHashSet<String> fields = new LinkedHashSet<>(actual.keySet());
    fields.addAll(expected.keySet());
    for (String field : fields) {
      String path = prefix.isEmpty() ? field : prefix + "." + field;
      JsonElement exp = expected.get(field);
      JsonElement act = actual.get(field);
      if (exp != null && exp.isJsonObject() && act != null && act.isJsonObject()) {
        diffField(id, name, path, exp.getAsJsonObject(), act.getAsJsonObject(), mismatches);
      } else if (exp == null || act == null || !exp.equals(act)) {
        mismatches.add(
            id
                + " "
                + label(name)
                + " -> "
                + path
                + ": expected «"
                + render(exp)
                + "», got «"
                + render(act)
                + "»");
      }
    }
  }

  private static String failureMessage(List<String> mismatches) {
    StringBuilder sb = new StringBuilder();
    sb.append(mismatches.size())
        .append(" resolved voice attribute(s) drifted from the committed golden.\n")
        .append("Regenerate and review the diff after an intentional mapping change:\n")
        .append("  ./gradlew test --tests '*ResolvedNpcVoiceGoldenTest' -Dgolden.regenerate=true")
        .append(" --rerun-tasks\n\n");
    int shown = Math.min(mismatches.size(), MAX_REPORTED);
    for (int i = 0; i < shown; i++) {
      sb.append("  ").append(mismatches.get(i)).append('\n');
    }
    if (mismatches.size() > shown) {
      sb.append("  ... and ").append(mismatches.size() - shown).append(" more\n");
    }
    return sb.toString();
  }

  private static JsonObject readResource(String resource) throws IOException {
    try (InputStream stream = ResolvedNpcVoiceGoldenTest.class.getResourceAsStream(resource)) {
      if (stream == null) {
        throw new IOException("required test resource not found on the classpath: " + resource);
      }
      Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
      return new JsonParser().parse(reader).getAsJsonObject();
    }
  }

  private static JsonElement nullable(String value) {
    return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
  }

  private static String render(JsonElement el) {
    return el == null || el.isJsonNull() ? "null" : el.getAsString();
  }

  private static String optString(JsonObject obj, String key) {
    if (!obj.has(key) || obj.get(key).isJsonNull()) {
      return null;
    }
    String value = obj.get(key).getAsString();
    return value.isEmpty() ? null : value;
  }

  private static String label(String name) {
    return name == null || name.isEmpty() ? "(unnamed)" : name;
  }
}
