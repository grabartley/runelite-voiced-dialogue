package com.grahambartley.runelite.voiced.dialogue.dialogue;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.synthesis.BackendProvider;
import com.grahambartley.runelite.voiced.dialogue.synthesis.CharacterProfile;
import com.grahambartley.runelite.voiced.dialogue.synthesis.Emotion;
import com.grahambartley.runelite.voiced.dialogue.synthesis.SynthesisRequest;
import com.grahambartley.runelite.voiced.dialogue.synthesis.VoiceSpec;
import com.grahambartley.runelite.voiced.dialogue.voice.ProfileResolver;
import com.grahambartley.runelite.voiced.dialogue.voice.VoiceManager;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.widgets.Widget;

/**
 * Warms the cache for the dialogue options the player can currently see. Each option's text is the
 * line the player will speak if it is picked, so it is built into the exact same {@link
 * SynthesisRequest} (player voice, player profile, neutral) the dispatcher would produce for that
 * line, marked as speculative so spend tracking can tell warming apart from lines actually heard,
 * and handed to the off-thread prefetcher. The "Select an Option" header and blank rows are
 * skipped. Only touches the client on the game thread; never throws.
 */
public final class DialoguePrefetchCoordinator {

  private static final String OPTION_HEADER = "Select an Option";

  private final VoiceManager voiceManager;
  private final ProfileResolver profileResolver;
  private final DialogueTextCleaner textCleaner;
  private final DialoguePrefetcher prefetcher;
  private final BackendProvider backendProvider;
  private final VoicedDialogueConfig config;

  public DialoguePrefetchCoordinator(
      VoiceManager voiceManager,
      ProfileResolver profileResolver,
      DialogueTextCleaner textCleaner,
      DialoguePrefetcher prefetcher,
      BackendProvider backendProvider,
      VoicedDialogueConfig config) {
    this.voiceManager = voiceManager;
    this.profileResolver = profileResolver;
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
    VoiceSpec voice = voiceManager.resolveVoice(VoiceManager.SPEAKER_PLAYER, null);
    CharacterProfile profile = profileResolver.resolve(VoiceManager.SPEAKER_PLAYER, null);
    List<SynthesisRequest> candidates = new ArrayList<>(children.length);
    for (Widget child : children) {
      if (child == null) {
        continue;
      }
      String raw = child.getText();
      if (raw == null) {
        continue;
      }
      String cleaned = textCleaner.clean(raw);
      if (cleaned.isEmpty() || OPTION_HEADER.equalsIgnoreCase(cleaned)) {
        continue;
      }
      candidates.add(
          new SynthesisRequest(
                  cleaned,
                  voice,
                  Emotion.NEUTRAL,
                  profile,
                  /* skipTranslation= */ false,
                  /* player= */ true)
              .asPrefetch());
    }
    prefetcher.offer(candidates);
  }
}
