package com.grahambartley.runelite.voiced.dialogue.audio;

public interface AudioOutput {

  void stream(float[] samples, int sampleRate, int volumePercent);

  AudioStream beginStream(int volumePercent);

  void stop();

  void close();

  interface AudioStream {

    void write(float[] samples, int sampleRate);

    void end();
  }
}
