package com.grahambartley.runelite.voiced.dialogue.audio;

public final class CaveEcho {
  private CaveEcho() {}

  static final int DELAY_MS = 260;
  static final float FEEDBACK = 0.35f;
  static final float DAMPING = 0.40f;
  static final int MAX_TAIL_MS = 2000;

  static final double TAIL_FLOOR = 1e-3;

  public static Pcm apply(Pcm dry) {
    float[] in = dry.getSamples();
    int rate = dry.getSampleRate();
    int d = Math.max(1, Math.round(DELAY_MS * rate / 1000f));

    int hops = (int) Math.ceil(Math.log(TAIL_FLOOR) / Math.log(FEEDBACK));
    int tail = Math.min(hops * d, MAX_TAIL_MS * rate / 1000);
    float[] out = new float[in.length + tail];

    float lp = 0f;
    for (int i = 0; i < out.length; i++) {
      float dryS = i < in.length ? in[i] : 0f;
      float delayed = i >= d ? out[i - d] : 0f;
      lp = DAMPING * lp + (1f - DAMPING) * delayed;
      float s = dryS + FEEDBACK * lp;
      out[i] = s < -1f ? -1f : (s > 1f ? 1f : s);
    }
    return new Pcm(out, rate);
  }
}
