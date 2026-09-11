package com.grahambartley.runelite.voiced.dialogue.capture;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.profile.Emotion;
import com.grahambartley.runelite.voiced.dialogue.profile.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.profile.Speaker;
import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import com.grahambartley.runelite.voiced.dialogue.speech.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.widgets.Widget;

public final class DialoguePrefetchCoordinator {

  private static final String OPTION_HEADER = "Select an Option";

  private final VoiceManager voiceManager;
  private final DialogueTextCleaner textCleaner;
  private final DialoguePrefetcher prefetcher;
  private final BackendProvider backendProvider;
  private final VoicedDialogueConfig config;

  private List<String> lastRawOptions = Collections.emptyList();

  public DialoguePrefetchCoordinator(
      VoiceManager voiceManager,
      DialogueTextCleaner textCleaner,
      DialoguePrefetcher prefetcher,
      BackendProvider backendProvider,
      VoicedDialogueConfig config) {
    this.voiceManager = voiceManager;
    this.textCleaner = textCleaner;
    this.prefetcher = prefetcher;
    this.backendProvider = backendProvider;
    this.config = config;
  }

  void prefetchOptions(Widget options) {
    if (!config.prefetch() || !backendProvider.active().isAvailable()) {
      return;
    }
    Widget[] children = options.getDynamicChildren();
    if (children == null || children.length == 0) {
      return;
    }
    List<String> raw = new ArrayList<>(children.length);
    for (Widget child : children) {
      raw.add(child == null ? null : child.getText());
    }
    if (raw.equals(lastRawOptions)) {
      return;
    }
    lastRawOptions = raw;

    ResolvedSpeaker resolved = voiceManager.resolve(Speaker.PLAYER, null);
    List<SynthesisRequest> candidates = new ArrayList<>(raw.size());
    for (String text : raw) {
      if (text == null) {
        continue;
      }
      String cleaned = textCleaner.clean(text);
      if (cleaned.isEmpty() || OPTION_HEADER.equalsIgnoreCase(cleaned)) {
        continue;
      }
      candidates.add(
          new SynthesisRequest(
                  cleaned, resolved.voice(), Emotion.NEUTRAL, resolved.profile(), false, true)
              .asPrefetch());
    }
    prefetcher.offer(candidates);
  }

  void reset() {
    lastRawOptions = Collections.emptyList();
    prefetcher.reset();
  }
}
