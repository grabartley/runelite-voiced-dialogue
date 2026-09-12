package com.grahambartley.runelite.voiced.dialogue;

import com.grahambartley.runelite.voiced.dialogue.profile.VoiceManager;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(VoicedDialogueConfig.GROUP)
public interface VoicedDialogueConfig extends Config {

  String GROUP = "voicedDialogue";

  String PROVIDER_KEY = "ttsProvider";

  String OPENROUTER_API_KEY = "openRouterApiKey";

  String GOOGLE_AI_STUDIO_API_KEY = "googleAiStudioApiKey";

  @ConfigSection(
      name = "General",
      description = "Provider, API keys, volume, chat, and prefetch.",
      position = 0)
  String generalSection = "general";

  @ConfigSection(
      name = "Voices",
      description = "Who speaks and how they sound: you, nearby NPCs, and the narrator.",
      position = 1)
  String voicesSection = "voices";

  @ConfigSection(
      name = "Delivery",
      description = "Emotion, language, style, pace, and effects per line.",
      position = 2)
  String deliverySection = "delivery";

  @ConfigSection(
      name = "Advanced",
      description = "Niche tuning and diagnostics most players won't need.",
      position = 3,
      closedByDefault = true)
  String advancedSection = "advanced";

  enum TtsProvider {
    OPENROUTER("OpenRouter"),
    GOOGLE_AI_STUDIO("Google AI Studio");

    private final String label;

    TtsProvider(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum SpeakingStyle {
    NONE("None", ""),
    GEN_Z("Gen Z Slang", "Gen Z slang"),
    MILLENNIAL("Millennial Slang", "millennial slang"),
    NINETIES_STREET("90s Street", "with 90s hip-hop street slang"),
    STREET("Street Slang", "casual street slang"),
    US_SLANG("US Slang", "with casual American slang"),
    UK_SLANG("UK Slang", "with London Roadman Slang"),
    IRISH_SLANG("Irish Slang", "with Dublin Slang"),
    SURFER("Surfer", "with laid-back surfer slang"),
    VALLEY_GIRL("Valley Girl", "with Valley Girl slang"),
    FORMAL("Formal & Posh", "very formal and posh"),
    VICTORIAN("Victorian", "in formal Victorian English"),
    SHAKESPEAREAN("Shakespearean", "in Shakespearean Early Modern English"),
    DRAMATIC("Over-Dramatic", "wildly over-dramatic and theatrical"),
    CUTESY("Cutesy & Bubbly", "cutesy, bubbly and over-enthusiastic"),
    PIRATE("Pirate Speak", "pirate speak"),
    COWBOY("Cowboy", "with Wild West cowboy slang"),
    CYBERPUNK("Cyberpunk", "with gritty cyberpunk netrunner slang"),
    RHYMING("Rhyming", "as rhyming verse");

    private final String label;
    private final String phrase;

    SpeakingStyle(String label, String phrase) {
      this.label = label;
      this.phrase = phrase;
    }

    public boolean isNone() {
      return this == NONE;
    }

    public String phrase() {
      return phrase;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum SpokenLanguage {
    ENGLISH("English", "en-GB"),
    SPANISH("Spanish", "es-ES"),
    LATIN_AMERICAN_SPANISH("Latin American Spanish", "es-419", "Spanish (LatAm)"),
    MEXICAN_SPANISH("Mexican Spanish", "es-MX", "Spanish (MX)"),
    FRENCH("French", "fr-FR"),
    CANADIAN_FRENCH("Canadian French", "fr-CA", "French (CA)"),
    GERMAN("German", "de-DE"),
    ITALIAN("Italian", "it-IT"),
    PORTUGUESE("Portuguese", "pt-PT"),
    BRAZILIAN_PORTUGUESE("Brazilian Portuguese", "pt-BR", "Portuguese (BR)"),
    DUTCH("Dutch", "nl-NL"),
    POLISH("Polish", "pl-PL"),
    RUSSIAN("Russian", "ru-RU"),
    UKRAINIAN("Ukrainian", "uk-UA"),
    JAPANESE("Japanese", "ja-JP"),
    KOREAN("Korean", "ko-KR"),
    CHINESE("Chinese", "zh-CN"),
    TRADITIONAL_CHINESE("Traditional Chinese", "zh-TW", "Chinese (Trad.)"),
    CANTONESE("Cantonese", "yue-HK"),
    ARABIC("Arabic", "ar-XA"),
    HINDI("Hindi", "hi-IN"),
    BENGALI("Bengali", "bn-IN"),
    TAMIL("Tamil", "ta-IN"),
    TURKISH("Turkish", "tr-TR"),
    SWEDISH("Swedish", "sv-SE"),
    NORWEGIAN("Norwegian", "nb-NO"),
    DANISH("Danish", "da-DK"),
    FINNISH("Finnish", "fi-FI"),
    ICELANDIC("Icelandic", "is-IS"),
    GREEK("Greek", "el-GR"),
    CZECH("Czech", "cs-CZ"),
    SLOVAK("Slovak", "sk-SK"),
    ROMANIAN("Romanian", "ro-RO"),
    HUNGARIAN("Hungarian", "hu-HU"),
    BULGARIAN("Bulgarian", "bg-BG"),
    CROATIAN("Croatian", "hr-HR"),
    SERBIAN("Serbian", "sr-RS"),
    CATALAN("Catalan", "ca-ES"),
    HEBREW("Hebrew", "he-IL"),
    PERSIAN("Persian", "fa-IR"),
    VIETNAMESE("Vietnamese", "vi-VN"),
    THAI("Thai", "th-TH"),
    INDONESIAN("Indonesian", "id-ID"),
    MALAY("Malay", "ms-MY"),
    FILIPINO("Filipino", "fil-PH"),
    WELSH("Welsh", "cy-GB"),
    IRISH("Irish", "ga-IE"),
    LATIN("Latin", "la"),
    AFRIKAANS("Afrikaans", "af-ZA"),
    SWAHILI("Swahili", "sw-KE");

    private final String label;
    private final String code;
    private final String displayName;

    SpokenLanguage(String label, String code) {
      this(label, code, label);
    }

    SpokenLanguage(String label, String code, String displayName) {
      this.label = label;
      this.code = code;
      this.displayName = displayName;
    }

    public boolean isEnglish() {
      return this == ENGLISH;
    }

    public String label() {
      return label;
    }

    public String code() {
      return code;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  @ConfigItem(
      keyName = PROVIDER_KEY,
      name = "Voice Provider",
      description =
          "Cloud service that voices dialogue and bills the calls. Google AI Studio is fastest but"
              + " begins at 100 fresh lines a day, prefetched options included; OpenRouter has no"
              + " daily cap.",
      position = 0,
      section = generalSection)
  default TtsProvider ttsProvider() {
    return TtsProvider.GOOGLE_AI_STUDIO;
  }

  @ConfigItem(
      keyName = OPENROUTER_API_KEY,
      name = "OpenRouter API Key",
      description = "Used by the OpenRouter provider. Key at openrouter.ai.",
      position = 1,
      secret = true,
      section = generalSection)
  default String openRouterApiKey() {
    return "";
  }

  @ConfigItem(
      keyName = GOOGLE_AI_STUDIO_API_KEY,
      name = "Google AI Studio API Key",
      description = "Used by the Google AI Studio provider. Key at aistudio.google.com.",
      position = 2,
      secret = true,
      section = generalSection)
  default String googleAiStudioApiKey() {
    return "";
  }

  @ConfigItem(
      keyName = "volume",
      name = "Dialogue Volume",
      description = "Loudness of spoken dialogue, 0 (muted) to 100.",
      position = 3,
      section = generalSection)
  @Range(min = 0, max = 100)
  default int volume() {
    return 20;
  }

  @ConfigItem(
      keyName = "voicePublicChat",
      name = "Voice My Public Chat",
      description = "Speak your own public chat in your player voice.",
      position = 4,
      section = generalSection)
  default boolean voicePublicChat() {
    return false;
  }

  @ConfigItem(
      keyName = "prefetch",
      name = "Prefetch Dialogue",
      description = "Preload visible dialogue options; may raise spend.",
      position = 5,
      section = generalSection)
  default boolean prefetch() {
    return true;
  }

  @ConfigItem(
      keyName = "playerVoice",
      name = "Player Voice",
      description = "Voice for your character's dialogue and public chat.",
      position = 0,
      section = voicesSection)
  default VoiceManager.PlayerVoice playerVoice() {
    return VoiceManager.PlayerVoice.TYPE_A;
  }

  @ConfigItem(
      keyName = "playerAccent",
      name = "Your Accent",
      description = "Your voice's accent.",
      position = 1,
      section = voicesSection)
  default String playerAccent() {
    return "British English, as spoken in Cambridge, England.";
  }

  @ConfigItem(
      keyName = "playerPersona",
      name = "Your Persona",
      description = "Who your adventurer is.",
      position = 2,
      section = voicesSection)
  default String playerPersona() {
    return "Friendly, plucky, warm, and enthusiastic.";
  }

  @ConfigItem(
      keyName = "playerPace",
      name = "Your Delivery Pace",
      description = "How your character paces their words.",
      position = 3,
      section = voicesSection)
  default String playerPace() {
    return "Normal.";
  }

  @ConfigItem(
      keyName = "voiceNarration",
      name = "Voice Narration",
      description = "Speak message and item boxes in a narrator voice.",
      position = 4,
      section = voicesSection)
  default boolean voiceNarration() {
    return false;
  }

  @ConfigItem(
      keyName = "voiceExamineText",
      name = "Voice Examine Text",
      description = "Read examine text aloud in the narrator voice. Repeats replay free.",
      position = 5,
      section = voicesSection)
  default boolean voiceExamineText() {
    return false;
  }

  @ConfigItem(
      keyName = "voiceAmbientChatter",
      name = "Voice Ambient Chatter",
      description =
          "Speak nearby NPCs' overhead lines in their own voices. Spends on its own; repeats"
              + " replay free.",
      position = 6,
      section = voicesSection)
  default boolean voiceAmbientChatter() {
    return false;
  }

  @ConfigItem(
      keyName = "ambientChatterRadius",
      name = "Ambient Chatter Range",
      description = "How far away, in tiles, an NPC can be and still be heard.",
      position = 7,
      section = voicesSection)
  @Range(min = 1, max = 50)
  default int ambientChatterRadius() {
    return 20;
  }

  @ConfigItem(
      keyName = "autoLearnNewNpcs",
      name = "Auto-learn New NPCs",
      description = "Look up unknown NPCs on the wiki once, then cache.",
      position = 8,
      section = voicesSection)
  default boolean autoLearnNewNpcs() {
    return false;
  }

  @ConfigItem(
      keyName = "cloudEmotion",
      name = "Emotional Delivery",
      description = "Deliver lines with the speaker's on-screen emotion.",
      position = 0,
      section = deliverySection)
  default boolean cloudEmotion() {
    return true;
  }

  @ConfigItem(
      keyName = "cloudLanguage",
      name = "Spoken Language",
      description = "Language spoken in; non-English translates each line.",
      position = 1,
      section = deliverySection)
  default SpokenLanguage cloudLanguage() {
    return SpokenLanguage.ENGLISH;
  }

  @ConfigItem(
      keyName = "cloudPlayerSpeakingStyle",
      name = "Player Speaking Style",
      description = "Rewrite your lines in a style (Gen Z, pirate, etc.).",
      position = 2,
      section = deliverySection)
  default SpeakingStyle cloudPlayerSpeakingStyle() {
    return SpeakingStyle.NONE;
  }

  @ConfigItem(
      keyName = "cloudNpcSpeakingStyle",
      name = "NPC Speaking Style",
      description = "Rewrite NPC lines in a style (Gen Z, pirate, etc.).",
      position = 3,
      section = deliverySection)
  default SpeakingStyle cloudNpcSpeakingStyle() {
    return SpeakingStyle.NONE;
  }

  @ConfigItem(
      keyName = "speakingPace",
      name = "Speaking Pace",
      description = "Speech speed as % of normal (100 = normal).",
      position = 4,
      section = deliverySection)
  @Range(min = 50, max = 200)
  default int speakingPace() {
    return 100;
  }

  @ConfigItem(
      keyName = "cloudCaveEcho",
      name = "Cave Echo",
      description = "Add a cave echo to dialogue spoken underground.",
      position = 5,
      section = deliverySection)
  default boolean cloudCaveEcho() {
    return false;
  }

  @ConfigItem(
      keyName = "cacheSizeLimitMiB",
      name = "Cache Size Limit (MiB)",
      description = "Max disk cache in MiB; oldest go first. 0 = no limit.",
      position = 0,
      section = advancedSection)
  @Range(min = 0, max = 4096)
  default int cacheSizeLimitMiB() {
    return 1024;
  }

  @ConfigItem(
      keyName = "debugMode",
      name = "Debug Logging",
      description = "Log NPC race/gender resolution to the client logs.",
      position = 1,
      section = advancedSection)
  default boolean debugMode() {
    return false;
  }
}
