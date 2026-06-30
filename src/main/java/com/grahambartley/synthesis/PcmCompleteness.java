package com.grahambartley.synthesis;

import com.grahambartley.tts.Pcm;

/**
 * Detects a line whose audio stops mid-utterance.
 *
 * <p>The cloud speech response is transport-complete: it arrives as a chunked HTTP/1.1 body, so a
 * connection cut mid-stream throws and the line is dropped, never decoded. The truncation this
 * guards against is above the transport: the model intermittently returns a 200 whose PCM payload
 * ends before the line finishes. No length field describes the intended audio, so the only tell is
 * the audio itself, and {@link RawPcmDecoder} accepts any whole-sample buffer.
 *
 * <p>A complete utterance from this model releases into trailing silence; a truncated one ends mid
 * signal at full amplitude. A line that does not end on at least {@link #MIN_TRAILING_SILENCE_MS}
 * of quiet is therefore treated as truncated so the caller can re-fetch it. Clips shorter than
 * {@link #MIN_JUDGED_MS} are left alone: they are too short to carry a reliable trailing-silence
 * tell, and real dialogue lines are far longer.
 */
final class PcmCompleteness {

  /** Below this absolute sample amplitude counts as silence for the trailing-quiet run. */
  private static final float SILENCE_FLOOR = 0.02f;

  /** A complete line ends on at least this much trailing quiet; the corpus median is ~360 ms. */
  private static final int MIN_TRAILING_SILENCE_MS = 120;

  /** Clips shorter than this are not assessed, so a one-word reply is never mistaken for a cut. */
  private static final int MIN_JUDGED_MS = 500;

  private PcmCompleteness() {}

  static boolean isTruncated(Pcm pcm) {
    int rate = pcm.getSampleRate();
    if (rate <= 0) {
      return false;
    }
    float[] samples = pcm.getSamples();
    if (samples.length < msToSamples(MIN_JUDGED_MS, rate)) {
      return false;
    }
    int needed = msToSamples(MIN_TRAILING_SILENCE_MS, rate);
    int quiet = 0;
    for (int i = samples.length - 1; i >= 0; i--) {
      if (Math.abs(samples[i]) >= SILENCE_FLOOR) {
        break;
      }
      if (++quiet >= needed) {
        return false;
      }
    }
    return true;
  }

  private static int msToSamples(int ms, int rate) {
    return Math.round(ms / 1000f * rate);
  }
}
