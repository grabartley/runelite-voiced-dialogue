package com.grahambartley.runelite.voiced.dialogue.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExpressionEmotionTable {

  private static final String DOC_KEY_PREFIX = "_";

  static final String TABLE_RESOURCE = "/expression-emotions.json";

  private final Map<Integer, Emotion> table;

  private ExpressionEmotionTable(Map<Integer, Emotion> table) {
    this.table = table;
  }

  public static ExpressionEmotionTable load() {
    try (InputStream stream = ExpressionEmotionTable.class.getResourceAsStream(TABLE_RESOURCE)) {
      if (stream == null) {
        log.warn(
            "Expression-emotion table {} not found - all expressions resolve to NEUTRAL",
            TABLE_RESOURCE);
        return new ExpressionEmotionTable(Collections.emptyMap());
      }
      JsonObject root =
          new JsonParser()
              .parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      return new ExpressionEmotionTable(parse(root));
    } catch (Exception e) {
      log.warn(
          "Failed to load expression-emotion table {} - all expressions resolve to NEUTRAL: {}",
          TABLE_RESOURCE,
          e.getMessage());
      return new ExpressionEmotionTable(Collections.emptyMap());
    }
  }

  static Map<Integer, Emotion> parse(JsonObject root) {
    Map<Integer, Emotion> parsed = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(DOC_KEY_PREFIX)) {
        continue;
      }
      int animationId;
      try {
        animationId = Integer.parseInt(key);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "expression-emotions.json key is not an integer animation id: " + key, e);
      }
      String emotionName = entry.getValue().getAsString();
      Emotion emotion;
      try {
        emotion = Emotion.valueOf(emotionName);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "expression-emotions.json value for id "
                + animationId
                + " is not a valid Emotion: "
                + emotionName,
            e);
      }
      parsed.put(animationId, emotion);
    }
    return Collections.unmodifiableMap(parsed);
  }

  public Emotion resolve(int animationId) {
    if (animationId < 0) {
      return Emotion.NEUTRAL;
    }
    return table.getOrDefault(animationId, Emotion.NEUTRAL);
  }
}
