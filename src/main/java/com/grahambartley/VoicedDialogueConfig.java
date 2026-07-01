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
      description =
          "Your OpenRouter API key plus playback and caching. Dialogue text is sent to OpenRouter"
              + " over HTTPS with your key to be voiced, so it leaves your machine.",
      position = 0)
  String generalSection = "general";

  @ConfigSection(
      name = "Voices",
      description = "Who speaks and how they sound: your own character and the NPCs around you.",
      position = 1)
  String voicesSection = "voices";

  @ConfigSection(
      name = "Delivery",
      description =
          "How each line is delivered: emotion, spoken language, speaking style, pace, and effects.",
      position = 2)
  String deliverySection = "delivery";

  @ConfigSection(
      name = "Advanced",
      description = "Niche tuning and diagnostics most players never need to touch",
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
      description =
          "Your OpenRouter API key, required to voice dialogue. Create a free key at openrouter.ai"
              + " and paste it here. Stored locally and never bundled with the plugin. Without a"
              + " key, lines stay silent with a one-time notice.",
      position = 0,
      secret = true,
      section = generalSection)
  default String openRouterApiKey() {
    return "";
  }

  @ConfigItem(
      keyName = "volume",
      name = "Dialogue Volume",
      description = "Loudness of the spoken dialogue, from 0 (muted) to 100.",
      position = 1,
      section = generalSection)
  @Range(min = 0, max = 100)
  default int volume() {
    return 20;
  }

  @ConfigItem(
      keyName = "voicePublicChat",
      name = "Voice My Public Chat",
      description =
          "Speak your own public chat messages aloud using your player voice. Voiced exactly as"
              + " typed: spoken language and speaking style are never applied to public chat.",
      position = 2,
      section = generalSection)
  default boolean voicePublicChat() {
    return false;
  }

  @ConfigItem(
      keyName = "prefetch",
      name = "Prefetch Dialogue",
      description =
          "Warm the audio cache for the dialogue options you can see, so the line you pick next"
              + " plays instantly. It can raise OpenRouter spend on branches you never choose.",
      position = 3,
      section = generalSection)
  default boolean prefetch() {
    return true;
  }

  @ConfigItem(
      keyName = "persistentCache",
      name = "Save Audio To Disk",
      description =
          "Save synthesized dialogue to disk so repeated lines play instantly across sessions and"
              + " OpenRouter is not re-billed for audio you have already heard. The cache lives in"
              + " ~/.runelite/voiced-dialogue/cache and is size-bounded.",
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
      description = "The voice used for your own character's dialogue and public chat.",
      position = 0,
      section = voicesSection)
  default VoiceManager.PlayerVoice playerVoice() {
    return VoiceManager.PlayerVoice.TYPE_A;
  }

  @ConfigItem(
      keyName = "playerAccent",
      name = "Your Accent",
      description =
          "Accent for your character's voice. British by default; this is a British medieval"
              + " fantasy world. Used only when Character Voices are on.",
      position = 1,
      section = voicesSection)
  default String playerAccent() {
    return "British English, as spoken in Cambridge, England.";
  }

  @ConfigItem(
      keyName = "playerPersona",
      name = "Your Persona",
      description =
          "Persona and delivery style for your character's voice. Describe who your adventurer is."
              + " Used only when Character Voices are on.",
      position = 2,
      section = voicesSection)
  default String playerPersona() {
    return "Friendly, plucky, warm, and enthusiastic.";
  }

  @ConfigItem(
      keyName = "playerPace",
      name = "Your Pace",
      description =
          "Speaking pace for your character's voice. Used only when Character Voices are on.",
      position = 3,
      section = voicesSection)
  default String playerPace() {
    return "Normal.";
  }

  @ConfigItem(
      keyName = "cloudCharacterProfiles",
      name = "Character Voices",
      description =
          "Give each speaker a distinct voice (accent, style, pace) drawn from the bundled character"
              + " table, instead of one shared voice for everyone. Adds a little to each request;"
              + " turn off for the cheapest, plainest delivery.",
      position = 4,
      section = voicesSection)
  default boolean cloudCharacterProfiles() {
    return true;
  }

  @ConfigItem(
      keyName = "autoLearnNewNpcs",
      name = "Auto-learn New NPCs",
      description =
          "When an NPC isn't in the bundled voice table (e.g. one added to the game since the last"
              + " plugin update), look its race, gender and ethnicity up on the Old School RuneScape"
              + " Wiki once, then cache the result locally so it voices correctly from then on. The"
              + " first line for such an NPC still uses the default voice while the lookup runs. Off"
              + " by default; when on it makes a network request (the NPC's name) to the wiki.",
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
      description =
          "Carry the emotion read from the speaker's chat-head animation through to the voice, so"
              + " lines are delivered happy, sad, angry, or scared. Turn this off to voice every"
              + " line neutrally.",
      position = 0,
      section = deliverySection)
  default boolean cloudEmotion() {
    return true;
  }

  @ConfigItem(
      keyName = "cloudLanguage",
      name = "Spoken Language",
      description =
          "Language dialogue is spoken in. English (default) speaks the original line directly. Any"
              + " other language routes each line through a translation model first, preserving"
              + " names, places, and item terms, then voices the translation. Adds a translation"
              + " request per new line.",
      position = 1,
      section = deliverySection)
  default SpokenLanguage cloudLanguage() {
    return SpokenLanguage.ENGLISH;
  }

  @ConfigItem(
      keyName = "cloudPlayerSpeakingStyle",
      name = "Player Speaking Style",
      description =
          "Optional delivery register layered onto your own dialogue lines, on top of the Spoken"
              + " Language. None (default) changes nothing; any other value rewrites your lines in"
              + " that style (Gen Z slang, pirate speak, and so on) via the translation model, so"
              + " they route through that hop even for English. Leave this on None with English to"
              + " skip the translation model entirely for your lines.",
      position = 2,
      section = deliverySection)
  default SpeakingStyle cloudPlayerSpeakingStyle() {
    return SpeakingStyle.NONE;
  }

  @ConfigItem(
      keyName = "cloudNpcSpeakingStyle",
      name = "NPC Speaking Style",
      description =
          "Optional delivery register layered onto NPC dialogue lines, on top of the Spoken"
              + " Language. None (default) changes nothing; any other value rewrites NPC lines in"
              + " that style (Gen Z slang, pirate speak, and so on) via the translation model, so"
              + " they route through that hop even for English. Leave this on None with English to"
              + " skip the translation model entirely for NPC lines.",
      position = 3,
      section = deliverySection)
  default SpeakingStyle cloudNpcSpeakingStyle() {
    return SpeakingStyle.NONE;
  }

  @ConfigItem(
      keyName = "speakingPace",
      name = "Speaking Pace",
      description =
          "How fast dialogue is spoken, as a percentage of normal (100 = normal). Sent to"
              + " OpenRouter only when not 100 (the active model may ignore it).",
      position = 4,
      section = deliverySection)
  @Range(min = 50, max = 200)
  default int speakingPace() {
    return 100;
  }

  @ConfigItem(
      keyName = "cloudCaveEcho",
      name = "Cave Echo",
      description =
          "Add a cave echo to dialogue spoken underground. When you are below the overworld (a cave,"
              + " dungeon, sewer or basement), spoken lines get a decaying echo so they sound"
              + " enclosed. Off by default. The echo is added at playback, so cached audio is"
              + " unchanged and nothing is re-billed.",
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
      description =
          "Maximum size of the on-disk audio cache in MiB. When a new clip would push the cache over"
              + " this limit, the oldest clips are deleted first (FIFO) to make room, so the cache"
              + " never grows past it. Set to 0 for no limit. Only applies when Save Audio To Disk"
              + " is on.",
      position = 0,
      section = advancedSection)
  @Range(min = 0, max = 4096)
  default int cacheSizeLimitMiB() {
    return 1024;
  }

  @ConfigItem(
      keyName = "cloudMaxChars",
      name = "Max Characters Per Line",
      description =
          "Hard cap on how many characters of a single dialogue line are sent to OpenRouter, which"
              + " bills per character, so a positive cap truncates an unusually long line at a"
              + " sentence or word boundary before sending. 0 (default) sends the whole line"
              + " uncapped; OSRS lines are short, so set a cap only to bound pathological cases.",
      position = 1,
      section = advancedSection)
  @Range(min = 0, max = 5000)
  default int cloudMaxChars() {
    return 0;
  }

  @ConfigItem(
      keyName = "debugMode",
      name = "Debug Logging",
      description = "Show detailed NPC race/gender resolution info in the client logs.",
      position = 2,
      section = advancedSection)
  default boolean debugMode() {
    return false;
  }
}
