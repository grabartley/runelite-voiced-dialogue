package com.grahambartley.runelite.voiced.dialogue.audio;

@FunctionalInterface
public interface PcmSink {

  void accept(float[] samples, int sampleRate);
}
