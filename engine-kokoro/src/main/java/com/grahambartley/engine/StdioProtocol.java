package com.grahambartley.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Pure encode/decode helpers for the {@code --stdio} wire protocol, kept separate from the I/O loop
 * and the native engine so they can be unit-tested directly.
 *
 * <p>Protocol: the plugin writes one JSON request line on stdin
 *
 * <pre>{"text","voice":{race,gender,player},"emotion","speed","speakerId"}</pre>
 *
 * and reads back one JSON header line
 *
 * <pre>{"sampleRate":24000,"samples":N,"format":"f32le"}</pre>
 *
 * immediately followed by {@code N*4} little-endian float32 bytes. The engine renders the explicit
 * {@code speakerId} the plugin resolves; the {@code voice} object and {@code emotion} are accepted
 * and ignored (the plugin owns voice selection and Kokoro is neutral-only by design); {@code speed}
 * defaults to 1.0 when absent.
 */
final class StdioProtocol {

  static final String FORMAT = "f32le";

  /** Speed used when a request omits the {@code speed} field. */
  static final float DEFAULT_SPEED = 1.0f;

  /**
   * Sentinel for an absent/unspecified explicit speaker id: fall back to {@link #DEFAULT_SPEAKER}.
   */
  static final int NO_SPEAKER_ID = -1;

  /**
   * British voice (bm_george) used when a request carries no explicit speaker id. The plugin always
   * sends one now, so this is only a safety net for a malformed or legacy request.
   */
  static final int DEFAULT_SPEAKER = 26;

  private static final Gson GSON = new Gson();

  private StdioProtocol() {}

  /** Encodes mono float samples to little-endian float32 bytes, the protocol PCM frame. */
  static byte[] encodeSamples(float[] samples) {
    ByteBuffer buf =
        ByteBuffer.allocate(samples.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
    for (float s : samples) {
      buf.putFloat(s);
    }
    return buf.array();
  }

  /**
   * Decodes a request line into the fields the engine needs: the text, the speed, and the explicit
   * Kokoro speaker id the plugin chose. The {@code voice} object (player/race/gender) the plugin
   * also sends is ignored here: the plugin owns voice selection and sends the resolved speaker id,
   * so the engine never maps race/gender to a voice itself.
   */
  static Request decodeRequest(String line) {
    JsonObject root = line == null ? new JsonObject() : GSON.fromJson(line, JsonObject.class);
    if (root == null) {
      root = new JsonObject();
    }
    String text = asString(root.get("text"), "");
    float speed =
        root.has("speed") && !root.get("speed").isJsonNull()
            ? root.get("speed").getAsFloat()
            : DEFAULT_SPEED;
    int explicitSpeakerId =
        root.has("speakerId") && !root.get("speakerId").isJsonNull()
            ? root.get("speakerId").getAsInt()
            : NO_SPEAKER_ID;
    return new Request(text, speed, explicitSpeakerId);
  }

  /** Writes the header line then the raw PCM frame to {@code out}, flushing once complete. */
  static void writeResponse(OutputStream out, int sampleRate, byte[] pcm) throws IOException {
    String header = header(sampleRate, pcm.length / Float.BYTES) + System.lineSeparator();
    out.write(header.getBytes(StandardCharsets.UTF_8));
    out.write(pcm);
    out.flush();
  }

  /**
   * The response header object the plugin reads before the PCM frame. Field insertion order
   * (sampleRate, samples, format) is preserved by Gson's compact serializer, so the emitted bytes
   * stay {@code {"sampleRate":24000,"samples":N,"format":"f32le"}} exactly as the #32 contract and
   * the conformance test expect.
   */
  static String header(int sampleRate, int samples) {
    JsonObject obj = new JsonObject();
    obj.addProperty("sampleRate", sampleRate);
    obj.addProperty("samples", samples);
    obj.addProperty("format", FORMAT);
    return GSON.toJson(obj);
  }

  /** An error object so a failed request still yields a parseable line on stdout. */
  static String error(String message) {
    JsonObject obj = new JsonObject();
    obj.addProperty("error", message == null ? "" : message);
    return GSON.toJson(obj);
  }

  private static String asString(com.google.gson.JsonElement el, String fallback) {
    return el == null || el.isJsonNull() ? fallback : el.getAsString();
  }

  /**
   * A decoded synthesis request. {@code emotion} and the {@code voice} object are intentionally
   * absent: Kokoro is neutral-only and the plugin resolves the voice to an explicit speaker id.
   */
  static final class Request {
    final String text;
    final float speed;

    /** Explicit per-NPC speaker id from the wire, or {@link #NO_SPEAKER_ID} when not specified. */
    final int explicitSpeakerId;

    Request(String text, float speed) {
      this(text, speed, NO_SPEAKER_ID);
    }

    Request(String text, float speed, int explicitSpeakerId) {
      this.text = text;
      this.speed = speed;
      this.explicitSpeakerId = explicitSpeakerId;
    }

    /**
     * The Kokoro speaker id to synthesize with: the plugin's explicit choice when present,
     * otherwise the default British voice. The plugin owns voice selection, so the engine simply
     * renders the id it is given.
     */
    int speakerId() {
      return explicitSpeakerId >= 0 ? explicitSpeakerId : DEFAULT_SPEAKER;
    }
  }
}
