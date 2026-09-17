package com.grahambartley.runelite.voiced.dialogue.integration.followerbuddy;

import com.grahambartley.runelite.voiced.dialogue.VoicedDialogueConfig;
import com.grahambartley.runelite.voiced.dialogue.capture.ChatNoticeManager;
import com.grahambartley.runelite.voiced.dialogue.capture.DialogueTextCleaner;
import com.grahambartley.runelite.voiced.dialogue.speaker.NpcGender;
import com.grahambartley.runelite.voiced.dialogue.speech.SynthesisDispatcher;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;

public final class FollowerBuddyIntegration {

  static final String MIRROR_OFF_NOTICE =
      "Voiced Dialogue voices the Follower Buddy plugin's companion, but Follower Buddy's"
          + " \"Mirror to chat\" setting is off. Turn it on in Follower Buddy to hear your"
          + " companion speak.";

  private final VoicedDialogueConfig config;
  private final ChatNoticeManager notices;
  private final FollowerBuddySettings settings;
  private final FollowerSpeaker speaker;

  private boolean mirrorNoticeChecked;

  public FollowerBuddyIntegration(
      ConfigManager configManager,
      ChatNoticeManager notices,
      DialogueTextCleaner textCleaner,
      SynthesisDispatcher dispatcher,
      VoicedDialogueConfig config) {
    this.config = config;
    this.notices = notices;
    this.settings = new FollowerBuddySettings(configManager);
    this.speaker =
        new FollowerSpeaker(
            textCleaner, dispatcher, config::voiceFollower, settings::followerName, this::gender);
  }

  public void onChatMessage(ChatMessage event) {
    speaker.onChatMessage(event);
  }

  public void onGameTick() {
    if (mirrorNoticeChecked || !config.voiceFollower()) {
      return;
    }
    mirrorNoticeChecked = true;
    if (FollowerBuddyPresence.shouldWarnMirrorOff(
        settings.installedFromHub(), settings.mirrorToChat())) {
      notices.postNotice(MIRROR_OFF_NOTICE);
    }
  }

  public void onConfigChanged(ConfigChanged event) {
    if (FollowerBuddySettings.owns(event.getGroup())) {
      settings.invalidate();
      return;
    }
    if (VoicedDialogueConfig.GROUP.equals(event.getGroup())
        && VoicedDialogueConfig.VOICE_FOLLOWER_KEY.equals(event.getKey())) {
      mirrorNoticeChecked = false;
    }
  }

  private NpcGender gender() {
    return FollowerGenderPolicy.resolve(config.followerVoice(), settings.outfitGender());
  }
}
