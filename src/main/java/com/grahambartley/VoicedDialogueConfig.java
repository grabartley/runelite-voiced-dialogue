package com.grahambartley;

import com.grahambartley.voice.VoiceManager;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(VoicedDialogueConfig.GROUP)
public interface VoicedDialogueConfig extends Config {

  /** The {@code @ConfigGroup} value, shared so config reads/writes never restate the literal. */
  String GROUP = "voicedDialogue";

  @ConfigSection(
      name = "General",
      description = "API key, playback, cache. Text is sent to OpenRouter.",
      position = 0)
  String generalSection = "general";

  @ConfigSection(
      name = "Voices",
      description = "Who speaks and how they sound: you and nearby NPCs.",
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

  /**
   * An optional delivery quirk layered onto a spoken line, selected per speaker class (Player vs
   * NPC). {@link #NONE} (the default) changes nothing; any other value appends its {@link
   * #phrase()} to the configured spoken language, so the line is routed through the translation
   * model and rewritten in that register (for example "English" plus Gen Z slang behaves like a
   * "English Gen Z slang" target). Every value is a register or tone, not a dialect, so it stays
   * language-agnostic and composes with any spoken language ("French pirate speak", "Japanese Gen Z
   * slang").
   */
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

    /** Whether this is the no-op default. */
    public boolean isNone() {
      return this == NONE;
    }

    /** The style descriptor appended to the spoken language for the translation model. */
    public String phrase() {
      return phrase;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /**
   * The finite set of languages dialogue can be spoken in: the single source of truth for both the
   * dropdown options and the BCP-47 {@code language_code} sent to the TTS model. Each constant
   * carries a natural language name (fed verbatim to the translation model as the target language),
   * its BCP-47 code (sent so a translated line is pronounced natively rather than mis-read with an
   * English phoneme set), and a display name shown in the dropdown. The display name defaults to
   * the natural name but is shortened for regional variants (e.g. {@code Spanish (LatAm)}) so the
   * combo box does not crowd out the setting label. {@link #ENGLISH} (the default) speaks the
   * original line directly; every other value routes the line through the translation hop first.
   */
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

    /** Whether this is English, the no-translation default. */
    public boolean isEnglish() {
      return this == ENGLISH;
    }

    /** The natural language name fed to the translation model as the target language. */
    public String label() {
      return label;
    }

    /** The BCP-47 code sent as {@code language_code} so the line is pronounced natively. */
    public String code() {
      return code;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  // ---------------------------------------------------------------------------
  // General
  // ---------------------------------------------------------------------------

  @ConfigItem(
      keyName = "openRouterApiKey",
      name = "OpenRouter API Key",
      description = "Required to voice dialogue. Free key at openrouter.ai.",
      position = 0,
      secret = true,
      section = generalSection)
  default String openRouterApiKey() {
    return "";
  }

  @ConfigItem(
      keyName = "volume",
      name = "Dialogue Volume",
      description = "Loudness of spoken dialogue, 0 (muted) to 100.",
      position = 1,
      section = generalSection)
  @Range(min = 0, max = 100)
  default int volume() {
    return 20;
  }

  @ConfigItem(
      keyName = "voicePublicChat",
      name = "Voice My Public Chat",
      description = "Speak your own public chat in your player voice.",
      position = 2,
      section = generalSection)
  default boolean voicePublicChat() {
    return false;
  }

  @ConfigItem(
      keyName = "prefetch",
      name = "Prefetch Dialogue",
      description = "Preload visible dialogue options; may raise spend.",
      position = 3,
      section = generalSection)
  default boolean prefetch() {
    return true;
  }

  @ConfigItem(
      keyName = "persistentCache",
      name = "Save Audio To Disk",
      description = "Save audio to disk so repeat lines replay for free.",
      position = 4,
      section = generalSection)
  default boolean persistentCache() {
    return true;
  }

  // ---------------------------------------------------------------------------
  // Voices
  // ---------------------------------------------------------------------------

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
      description = "Your voice's accent. Needs Character Voices on.",
      position = 1,
      section = voicesSection)
  default String playerAccent() {
    return "British English, as spoken in Cambridge, England.";
  }

  @ConfigItem(
      keyName = "playerPersona",
      name = "Your Persona",
      description = "Who your adventurer is. Needs Character Voices on.",
      position = 2,
      section = voicesSection)
  default String playerPersona() {
    return "Friendly, plucky, warm, and enthusiastic.";
  }

  @ConfigItem(
      keyName = "playerPace",
      name = "Your Pace",
      description = "Your speaking pace. Needs Character Voices on.",
      position = 3,
      section = voicesSection)
  default String playerPace() {
    return "Normal.";
  }

  @ConfigItem(
      keyName = "cloudCharacterProfiles",
      name = "Character Voices",
      description = "Give each speaker a distinct voice from the table.",
      position = 4,
      section = voicesSection)
  default boolean cloudCharacterProfiles() {
    return true;
  }

  @ConfigItem(
      keyName = "autoLearnNewNpcs",
      name = "Auto-learn New NPCs",
      description = "Look up unknown NPCs on the wiki once, then cache.",
      position = 5,
      section = voicesSection)
  default boolean autoLearnNewNpcs() {
    return false;
  }

  // ---------------------------------------------------------------------------
  // Delivery
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // Advanced
  // ---------------------------------------------------------------------------

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
      keyName = "cloudMaxChars",
      name = "Max Characters Per Line",
      description = "Cap characters sent per line. 0 = whole line, uncapped.",
      position = 1,
      section = advancedSection)
  @Range(min = 0, max = 5000)
  default int cloudMaxChars() {
    return 0;
  }

  @ConfigItem(
      keyName = "debugMode",
      name = "Debug Logging",
      description = "Log NPC race/gender resolution to the client logs.",
      position = 2,
      section = advancedSection)
  default boolean debugMode() {
    return false;
  }
}
