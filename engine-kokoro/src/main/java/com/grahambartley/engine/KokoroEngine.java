package com.grahambartley.engine;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import java.nio.file.Path;

/**
 * Standalone Kokoro TTS engine backed by sherpa-onnx.
 *
 * <p>This is the external-process counterpart of the plugin's in-JVM {@code KokoroTtsEngine}: same
 * {@code kokoro-multi-lang-v1_0} model, same sherpa-onnx configuration, same CPU provider. The
 * model is loaded once on first synthesis and reused for the life of the process. The engine has no
 * knowledge of the {@code --stdio} framing; {@link KokoroEngineMain} owns that.
 */
final class KokoroEngine {

  private static final int NUM_THREADS = 2;
  private static final String PROVIDER = "cpu";
  private static final float DEFAULT_SPEED = 1.0f;
  private static final String ESPEAK_DATA_DIR = "espeak-ng-data";
  private static final String LEXICON_FILE = "lexicon-us-en.txt";

  private OfflineTts tts;

  /** Loads the model now so the first request does not pay the cold-start cost mid-stream. */
  void load() {
    ensureLoaded();
  }

  /** Synthesizes {@code text} for {@code speakerId} at {@code speed}, returning mono float PCM. */
  synchronized Pcm synthesize(String text, int speakerId, float speed) {
    OfflineTts engine = ensureLoaded();
    float effectiveSpeed = speed > 0 ? speed : DEFAULT_SPEED;
    GeneratedAudio audio = engine.generate(text, speakerId, effectiveSpeed);
    return new Pcm(audio.getSamples(), audio.getSampleRate());
  }

  void close() {
    if (tts != null) {
      try {
        tts.release();
      } catch (Throwable ignored) {
        // best-effort native cleanup
      }
      tts = null;
    }
  }

  private synchronized OfflineTts ensureLoaded() {
    if (tts != null) {
      return tts;
    }
    Path modelDir = ModelLocator.resolve();

    OfflineTtsKokoroModelConfig kokoro =
        OfflineTtsKokoroModelConfig.builder()
            .setModel(modelDir.resolve(ModelLocator.MODEL_FILE).toString())
            .setVoices(modelDir.resolve(ModelLocator.VOICES_FILE).toString())
            .setTokens(modelDir.resolve(ModelLocator.TOKENS_FILE).toString())
            .setDataDir(modelDir.resolve(ESPEAK_DATA_DIR).toString())
            .setLexicon(modelDir.resolve(LEXICON_FILE).toString())
            .build();

    OfflineTtsModelConfig modelConfig =
        OfflineTtsModelConfig.builder()
            .setKokoro(kokoro)
            .setNumThreads(NUM_THREADS)
            .setDebug(false)
            .setProvider(PROVIDER)
            .build();

    OfflineTtsConfig config = OfflineTtsConfig.builder().setModel(modelConfig).build();
    tts = new OfflineTts(config);
    return tts;
  }

  /** Mono PCM: float samples in [-1, 1] at the engine-reported sample rate (24 kHz for Kokoro). */
  static final class Pcm {
    final float[] samples;
    final int sampleRate;

    Pcm(float[] samples, int sampleRate) {
      this.samples = samples;
      this.sampleRate = sampleRate;
    }
  }
}
