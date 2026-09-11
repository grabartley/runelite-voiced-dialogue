package com.grahambartley.runelite.voiced.dialogue.speech;

import com.grahambartley.runelite.voiced.dialogue.audio.Pcm;
import com.grahambartley.runelite.voiced.dialogue.audio.PcmSink;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import java.util.EnumSet;

public interface SynthesisBackend {

  String id();

  boolean isAvailable();

  default String missingKeyNotice() {
    return "Add your API key in the Voiced Dialogue settings to hear dialogue; without a key,"
        + " lines are not voiced.";
  }

  EnumSet<Emotion> supportedEmotions();

  Pcm synthesize(SynthesisRequest request);

  default Pcm synthesizeStreaming(SynthesisRequest request, PcmSink sink) {
    Pcm pcm = synthesize(request);
    if (pcm != null) {
      sink.accept(pcm.getSamples(), pcm.getSampleRate());
    }
    return pcm;
  }

  default String cacheVariant(SynthesisRequest request) {
    return "";
  }

  default boolean isThrottled() {
    return false;
  }

  default void clearRateLimit() {}

  default void warmUp() {}

  default void close() {}
}
