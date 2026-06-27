package com.grahambartley;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("ttsDialogue")
public interface TTSDialogueConfig extends Config {

  @ConfigSection(name = "General Settings", description = "General TTS settings", position = 0)
  String generalSection = "general";

  @ConfigSection(
      name = "Voice Settings",
      description = "Configure player and default voices",
      position = 1)
  String voiceSection = "voices";

  @ConfigSection(
      name = "Synthesis",
      description = "Choose the synthesis backend and emotion behaviour",
      position = 2)
  String synthesisSection = "synthesis";

  @ConfigSection(
      name = "Cloud (OpenRouter)",
      description =
          "OpenRouter cloud TTS settings. Used only when Voice Backend is Cloud. Dialogue text leaves"
              + " your machine and is sent to OpenRouter when this backend is active.",
      position = 4)
  String cloudOpenRouterSection = "cloudOpenRouter";

  /**
   * Which synthesis backend dialogue routes through. {@code CLOUD} is the OpenRouter cloud backend
   * (default, cloud-first): it falls back to the local engine and warns once when no API key is
   * set. {@code LOCAL} is the offline, neutral-only Kokoro engine.
   */
  enum VoiceBackend {
    LOCAL,
    CLOUD
  }

  @ConfigItem(
      keyName = "voiceBackend",
      name = "Voice Backend",
      description =
          "Which synthesis engine to use. Cloud is the OpenRouter cloud backend (default): it needs"
              + " an API key and falls back to the local voice with a one-time notice until you add"
              + " one. Local is the offline, neutral-only Kokoro voice.",
      position = 0,
      section = synthesisSection)
  default VoiceBackend voiceBackend() {
    return VoiceBackend.CLOUD;
  }

  @ConfigItem(
      keyName = "enableEmotion",
      name = "Enable Emotion",
      description =
          "Carry the emotion detected from the speaker's chat-head animation through to synthesis."
              + " The Cloud voice renders happy, sad, angry, and scared delivery; the Local voice is"
              + " neutral-only, so its lines stay neutral. Turn this off to voice every line as"
              + " Neutral.",
      position = 1,
      section = synthesisSection)
  default boolean enableEmotion() {
    return true;
  }

  @ConfigItem(
      keyName = "openRouterApiKey",
      name = "OpenRouter API Key",
      description =
          "Your OpenRouter API key. Required for the Cloud voice backend. Stored locally and never"
              + " bundled with the plugin.",
      position = 0,
      secret = true,
      section = cloudOpenRouterSection)
  default String openRouterApiKey() {
    return "";
  }

  @ConfigItem(
      keyName = "maxCloudCharsPerLine",
      name = "Max Cloud Characters",
      description =
          "Hard cap on how many characters of a single dialogue line are sent to the cloud backend."
              + " Cloud TTS is billed per character, so an unusually long line is truncated at a"
              + " sentence or word boundary before sending. OSRS lines are short, so this only bites"
              + " pathological cases. Set to 0 to disable the cap.",
      position = 1,
      section = cloudOpenRouterSection)
  @Range(min = 0, max = 5000)
  default int maxCloudCharsPerLine() {
    return 600;
  }

  @ConfigItem(
      keyName = "cloudSpeedPercent",
      name = "Cloud Speaking Pace",
      description =
          "Speaking pace for the cloud backend, as a percentage of normal (100 = normal). Sent as"
              + " the OpenRouter speed parameter only when not 100; the active model may ignore it."
              + " Has no effect on the local backend.",
      position = 2,
      section = cloudOpenRouterSection)
  @Range(min = 50, max = 200)
  default int cloudSpeedPercent() {
    return 100;
  }

  @ConfigItem(
      keyName = "volume",
      name = "Dialogue Volume",
      description = "Volume of the spoken dialogue (0–100)",
      position = 0,
      section = generalSection)
  @Range(min = 0, max = 100)
  default int volume() {
    return 100;
  }

  @ConfigItem(
      keyName = "enableRaceBasedVoices",
      name = "Enable Automatic NPC Voices",
      description =
          "Automatically pick a Kokoro voice per NPC from the bundled race and gender voice table."
              + " When off, every NPC uses the default voice.",
      position = 1,
      section = generalSection)
  default boolean enableRaceBasedVoices() {
    return true;
  }

  @ConfigItem(
      keyName = "playerVoice",
      name = "Player Voice",
      description = "Voice used for player dialogue",
      position = 0,
      section = voiceSection)
  default VoiceManager.VoiceProfile playerVoice() {
    return VoiceManager.VoiceProfile.PLAYER_MALE;
  }

  @ConfigItem(
      keyName = "enableFallbacks",
      name = "Enable Voice Fallbacks",
      description =
          "When an NPC's race isn't in the voice table, fall back to a gender-appropriate human"
              + " voice. When off, those NPCs use the single default voice.",
      position = 3,
      section = generalSection)
  default boolean enableFallbacks() {
    return true;
  }

  @ConfigItem(
      keyName = "persistentCache",
      name = "Persistent Audio Cache",
      description =
          "Save synthesized dialogue to disk so repeated lines play instantly across sessions and"
              + " cloud backends are not re-billed for audio you have already heard. Cache lives in"
              + " ~/.runelite/tts-dialogue/cache and is size-bounded.",
      position = 5,
      section = generalSection)
  default boolean persistentCache() {
    return true;
  }

  @ConfigItem(
      keyName = "diskCacheMaxMiB",
      name = "Cache Size Limit (MiB)",
      description =
          "Maximum size of the on-disk audio cache in MiB. When a new clip would push the cache over"
              + " this limit, the oldest clips are deleted first (FIFO) to make room, so the cache"
              + " never grows past it. Only applies when Persistent Audio Cache is on.",
      position = 6,
      section = generalSection)
  @Range(min = 16, max = 4096)
  default int diskCacheMaxMiB() {
    return 256;
  }

  @ConfigItem(
      keyName = "debugMode",
      name = "Debug Mode",
      description = "Show detailed NPC race/gender resolution info in logs",
      position = 4,
      section = generalSection)
  default boolean debugMode() {
    return false;
  }
}
