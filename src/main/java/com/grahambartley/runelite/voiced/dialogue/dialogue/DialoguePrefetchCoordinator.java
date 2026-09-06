package com.grahambartley.runelite.voiced.dialogue.dialogue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.synthesis.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.synthesis.Emotion;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.voice.ResolvedSpeaker;
import com.grahambartley.runelite.voiced.dialogue.voice.Speaker;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.widgets.Widget;

/**
 * Warms the cache for the dialogue options the player can currently see. Each option's text is the
 * line the player will speak if it is picked, so it is built into the exact same {@link
 * SynthesisRequest} (player voice, player profile, neutral) the dispatcher would produce for that
 * line, marked as speculative so spend tracking can tell warming apart from lines actually heard,
 * and handed to the off-thread prefetcher. The "Select an Option" header and blank rows are
 * skipped. Only touches the client on the game thread; never throws.
 *
 * <p>This is the sole owner of the prefetch config gate, read live so toggling it takes effect
 * immediately.
 */
public final class DialoguePrefetchCoordinator {

  private static final String OPTION_HEADER = "Select an Option";

  private final VoiceManager voiceManager;
  private final DialogueTextCleaner textCleaner;
  private final DialoguePrefetcher prefetcher;
  private final BackendProvider backendProvider;
  private final VoicedDialogueConfig config;

  /**
   * The raw option texts last built into requests, so an unchanged menu re-resolves nothing. Only
   * the prefetcher's own dedup would absorb the rebuild, and the option list is re-read on every
   * tick the player spends reading it.
   */
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
                  cleaned,
                  resolved.voice(),
                  Emotion.NEUTRAL,
                  resolved.profile(),
                  /* skipTranslation= */ false,
                  /* player= */ true)
              .asPrefetch());
    }
    prefetcher.offer(candidates);
  }

  /**
   * Ends the dialogue session: the prefetcher starts a fresh cap and the remembered option texts
   * are dropped, so re-opening the same menu warms again rather than being mistaken for the menu
   * still on screen.
   */
  void reset() {
    lastRawOptions = Collections.emptyList();
    prefetcher.reset();
  }
}
