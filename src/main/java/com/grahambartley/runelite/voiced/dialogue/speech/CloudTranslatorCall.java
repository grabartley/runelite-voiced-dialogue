package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The provider-neutral control flow of one translation-hop call: execute, read, extract, and log
 * every outcome in the shared {@code [TTS cloud] translate} shape. A translator supplies the
 * request payload and the response-shape extraction through {@link Ops}; every failure path returns
 * {@code null} so the backend fails the line gracefully rather than voicing the wrong language or
 * caching a mistranslation.
 */
@Slf4j
public final class CloudTranslatorCall {

  /** The provider-specific half of a translation call. */
  public interface Ops {

    /** Builds the provider's translation request for one line. */
    Request buildRequest(String text, String language, String apiKey);

    /** Extracts the translated text from a raw response body, or {@code null} when it has none. */
    String extractText(String raw);
  }

  /** A completed translation plus the raw body it was extracted from. */
  public static final class Outcome {
    public final String text;
    public final String raw;

    public Outcome(String text, String raw) {
      this.text = text;
      this.raw = raw;
    }
  }

  private CloudTranslatorCall() {}

  /**
   * Runs one translation call, returning the extracted text and raw body, or {@code null} on any
   * failure (non-2xx, network error, empty/unparseable body).
   */
  public static Outcome run(
      OkHttpClient httpClient,
      VoicedDialogueConfig config,
      Ops ops,
      String text,
      String language,
      String apiKey) {
    Request httpRequest = ops.buildRequest(text, language, apiKey);
    long start = System.nanoTime();
    try (Response response = httpClient.newCall(httpRequest).execute()) {
      ResponseBody body = response.body();
      String raw = body == null ? "" : body.string();
      long elapsedMs = CloudHttp.elapsedMs(start);
      if (!response.isSuccessful()) {
        log.warn(
            "[TTS cloud] translate fail reason=non-2xx http={} elapsedMs={} inLen={} detail={}",
            response.code(),
            elapsedMs,
            text.length(),
            response.message());
        return null;
      }
      String translated = ops.extractText(raw);
      if (translated == null || translated.isEmpty()) {
        log.warn(
            "[TTS cloud] translate fail reason=no-content http={} elapsedMs={} inLen={}",
            response.code(),
            elapsedMs,
            text.length());
        return null;
      }
      if (config.debugMode()) {
        log.info(
            "[TTS cloud] translate ok lang={} elapsedMs={} inLen={} outLen={} -> \"{}\"",
            language,
            elapsedMs,
            text.length(),
            translated.length(),
            translated);
      }
      return new Outcome(translated, raw);
    } catch (IOException | RuntimeException e) {
      log.warn(
          "[TTS cloud] translate fail reason=error elapsedMs={} inLen={} detail={}",
          CloudHttp.elapsedMs(start),
          text.length(),
          e.getMessage());
      return null;
    }
  }
}
