# Voiced Dialogue

<p align="center">
<a href="https://github.com/grabartley/runelite-voiced-dialogue/stargazers"><img src="https://img.shields.io/github/stars/grabartley/runelite-voiced-dialogue?logo=github&label=Stars&color=4078c0" alt="GitHub stars"></a>
<a href="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml"><img src="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml/badge.svg" alt="Release"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-28a745.svg" alt="License: MIT"></a>
<a href="https://ko-fi.com/grahambartley"><img src="https://img.shields.io/badge/Ko--fi-Support-009078?logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
</p>

> Voiced Dialogue leverages a cloud service ([Google AI Studio](https://aistudio.google.com), recommended, or [OpenRouter](https://openrouter.ai)) to deliver high quality TTS with advanced features like emotion, accents, and per-NPC personalities. Both voice through the same Gemini model, so they sound identical, but Google AI Studio starts speaking in well under a second regardless of how long the line is. It requires usage credits: you pay only for the audio you generate, and a line of dialogue costs roughly $0.0025 (~€0.0023) on average to voice. See [Get started](#get-started) for setup.

## Gielinor, out loud

Every quest, every shopkeeper, every back-alley stranger: **now they actually talk.** Voiced Dialogue gives NPCs and your own adventurer real AI voices in real time, turning silent text boxes into a living, breathing world you can hear.

Walk up, talk, and listen. That is the whole setup.

<!--
## Hear it

Template for embedded demo clips; uncomment once clips exist. Each slot is a short
video or audio link with a one-line caption. Suggested slots: a quest conversation
with distinct NPC voices, an angry chat-head delivering a furious line, and the same
NPC in pirate speak or another language.
-->

## What you get

- **A voice for everyone.** NPCs and the player each get a distinct voice, so a goblin never sounds like a king.
- **Over 13,700 NPCs, already voiced.** The plugin knows exactly who is speaking and instantly picks the right voice, matched by race and gender. Bump into someone added in a future game update? Flip on **Auto-learn** and the plugin works out who they are all by itself.
- **Accents and personalities with real craft.** 13 races and 14 regional origins each map to their own accent: Scottish dwarves, South London trolls, Irish leprechauns, Dracula-esque vampyres, Norse Fremennik raiders, the gothic dread of Morytania, and more. On top of that, **over 6,400 NPCs** get a hand-written personality with its own style and speaking pace, so the icons of Gielinor sound like themselves.
- **Real emotion.** The plugin reads each speaker's chat-head expression and delivers the line happy, sad, angry, scared, or neutral, so a furious dwarf actually sounds furious.
- **You star in it too.** Set your own hero's accent, persona, and pace and play the dashing knight, the gruff mercenary, or the chaos goblin of your dreams.
- **Your chat, out loud.** Turn on **Voice My Public Chat** and everything you type in public chat is spoken in your character's voice.
- **Speak any language, any vibe.** Pipe dialogue through another language, or drop a delivery style over it: be a roadman in Gen Z slang among posh nobles, or run the whole realm as a pirate crew.
- **Atmosphere on tap.** Lines spoken underground pick up a cave echo, so dungeons and sewers feel enclosed.
- **Clean by default.** Always-on, offline profanity filtering keeps things friendly with no setup.
- **Fast and out of the way.** Voices play in real time, and skipping a line cuts its audio instantly, so the client stays snappy even when you mash through dialogue.

## Get started

Install from the **RuneLite Plugin Hub**: open RuneLite, click the wrench (Configuration) icon, open the **Plugin Hub**, search for **Voiced Dialogue**, and install.

Voiced Dialogue voices dialogue through the cloud, so it needs an API key from one of two providers. There is no subscription with either: you only pay for the audio you actually generate.

### Choosing your provider

Voiced Dialogue voices dialogue through one of two cloud services. Both use the same Gemini TTS model, so **the voices, accents, emotion, and personalities are identical**. They differ in how quickly a line starts speaking, and in how much setup they ask of you.

|  | Google AI Studio (recommended) | OpenRouter |
|---|---|---|
| **Pros** | Dialogue starts speaking in well under a second, however long the line is, because audio streams as it is generated. NPCs answer you almost immediately, which is the difference between the plugin feeling alive and feeling like it is buffering. | The simplest setup: make an account, add a few euro of credit, paste the key. |
| **Cons** | Slightly longer setup, since you have to enable billing on the Google project behind your key. | Noticeably higher latency on every line, and it grows with the length of the line, because OpenRouter has no streaming support and sends nothing until the whole clip is generated. A long quest speech can leave you waiting a long time before it starts. |

Measured time until a line starts speaking, same dialogue through both:

| Line length | Google AI Studio | OpenRouter |
|---|---|---|
| Short (20 chars) | **0.7s** | 1.7s |
| Medium (100 chars) | **0.8s** | 6.3s |
| Long (400 chars) | **0.8s** | 19.5s |
| Very long (500+ chars) | **0.8s** | 37.2s |

**Google AI Studio is the recommended way to use the plugin.** The extra setup step is worth it: in real questing, dialogue keeps pace with you instead of making you wait on every line.

You can switch at any time with the **Voice Provider** setting, and lines you have already heard replay instantly from your local cache either way.

### Setting up Google AI Studio (recommended)

1. **Create an API key.** Go to [aistudio.google.com/apikey](https://aistudio.google.com/apikey), sign in with your Google account, create an API key, and copy it.
2. **Enable billing on the key's project.** From the same page, open the project behind your key and turn billing on. **Do not skip this:** the free tier allows only a handful of speech requests per day, so without billing the plugin voices a few lines and then goes quiet. Costs are per character and are listed on Google's [Gemini API pricing page](https://ai.google.dev/pricing).
3. **Paste the key into the plugin.** In RuneLite, open the Voiced Dialogue settings and paste it into the **Google AI Studio API Key** field under **General**. **Voice Provider** is already set to Google AI Studio unless you have used OpenRouter before, in which case set it now.
4. **Talk to someone.** Walk up to any NPC and start a conversation. Lines should start speaking about a second after the text box appears, however long they are.

### Setting up OpenRouter

1. **Create an account.** Go to [openrouter.ai](https://openrouter.ai), click **Sign Up**, and pick **Sign in with Google** (GitHub or email work too).
2. **Add credits.** Open your [Credits page](https://openrouter.ai/settings/credits) and top up. **€5 is plenty to start.** A line of dialogue costs roughly $0.0025 (~€0.0023) on average to voice, so €5 covers over 2,000 lines.
3. **Create an API key.** Open your [API Keys page](https://openrouter.ai/settings/keys), create a new key (name it anything, like `RuneLite`), and copy it.
4. **Select the provider.** In the Voiced Dialogue settings, set **Voice Provider** to **OpenRouter**.
5. **Paste the key into the plugin.** Paste it into the **OpenRouter API Key** field under **General**.
6. **Talk to someone.** Walk up to any NPC and start a conversation. If they answer out loud, you are done.

Until a key is set for your chosen provider, lines stay silent and a one-time notice points you to the key. If you were already voicing dialogue through OpenRouter, the plugin keeps you there, so nothing changes for you until you switch it yourself.

## The features, up close

<details>
<summary><b>Emotion from expressions</b></summary>

The plugin watches the speaker's chat-head as they talk (the NPC's head for their lines, yours for your own) and matches the voice to the expression: one of Neutral, Happy, Sad, Angry, or Scared. A cheerful greeting sounds bright, a threat sounds menacing, a plea sounds desperate. Controlled by the **Emotional Delivery** toggle; turn it off to voice everything neutral.

</details>

<details>
<summary><b>Character profiles and accents</b></summary>

Every speaker gets a **character profile** that steers an accent, a persona, and a pace. This is a British medieval fantasy world at heart: commoners speak plain common British while royalty, knights, and high society get posh Received Pronunciation, with lore-driven exceptions for the races and peoples of Gielinor.

Accents also follow where a character is **from**, taking the real-world cultures the lands are based on: desert nomads, island chiefs, Norse raiders, and Mediterranean nobles all sound like home even when they are standing somewhere else entirely.

Profiles stack in layers, so an unknown NPC still gets a sensible voice while iconic characters get a bespoke one on top. Your own hero is fully editable (**Your Accent**, **Your Persona**, **Your Pace**), defaulting to a friendly, plucky adventurer with a Cambridge British accent. Gated by **Character Voices**.

</details>

<details>
<summary><b>Languages and speaking styles</b></summary>

Set **Spoken Language** to anything other than English and every line is spoken in that language, with names and RuneScape terms kept intact. Layer a **Speaking Style** on top (Gen Z slang, pirate speak, Shakespearean, cyberpunk, and more), set separately for your own lines and for NPCs, and mix them however you like: every combination of language and style works together.

</details>

<details>
<summary><b>Fast, cheap, and out of the way</b></summary>

Everything runs off the game thread, so the client never stutters and skipping a line cuts its audio instantly. Every line you have heard is kept in a local cache and replays instantly and free, even across sessions. Turn on **Prefetch Dialogue** and the plugin pre-voices the dialogue options on your screen, so the line you pick next starts playing the moment you click it.

On Google AI Studio a line starts speaking after about a second and keeps generating while you listen, so length costs you almost nothing up front. OpenRouter has no streaming support and withholds a line until it is fully generated, so its wait grows with the length of the line.

</details>

> **Privacy:** only the dialogue text being spoken is sent to your chosen provider (OpenRouter or Google AI Studio) over HTTPS, and lines you have already heard replay from your local cache without going anywhere.

## Configuration

Settings mirror the in-game panel: **General** (provider, keys, playback, caching), **Voices** (who sounds like what), **Delivery** (how each line is spoken), and **Advanced** (niche tuning).

<details>
<summary><b>General</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Voice Provider** | `Google AI Studio` | The cloud service that voices dialogue and bills the calls. The voices sound the same on both; Google AI Studio is recommended because dialogue starts speaking far sooner. See [Choosing your provider](#choosing-your-provider). |
| **OpenRouter API Key** | empty | Your OpenRouter API key, used by the OpenRouter provider; stored locally, never bundled with the plugin. |
| **Google AI Studio API Key** | empty | Your Gemini API key, used by the Google AI Studio provider; stored locally, never bundled with the plugin. |
| **Dialogue Volume** | `20` | Loudness of the spoken dialogue, from `0` (muted) to `100`. |
| **Voice My Public Chat** | `Off` | Speaks your own public chat aloud in your player voice, exactly as typed. |
| **Prefetch Dialogue** | `On` | Pre-voices the dialogue options you can see so your pick plays instantly; can spend credit on branches you never choose. |
| **Save Audio To Disk** | `On` | Keeps synthesized audio on disk so repeated lines replay instantly and free across sessions. |
| **Stream Playback** | `On` | Starts speaking a line as its audio arrives instead of waiting for the whole clip, so dialogue begins sooner. Only Google AI Studio delivers audio early enough for this to help; OpenRouter sends nothing until a line is fully generated. Cached lines always play instantly either way. |

</details>

<details>
<summary><b>Voices</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Player Voice** | `Type A` | The voice used for your own character's dialogue and public chat. |
| **Your Accent** | British (Cambridge) | Accent for your character's voice. |
| **Your Persona** | friendly and plucky | Persona and delivery style for your character's voice. |
| **Your Pace** | Normal | Speaking pace for your character's voice. |
| **Character Voices** | `On` | Gives each speaker a distinct accent, style, and pace instead of one shared voice. Off gives the plainest, cheapest delivery. |
| **Auto-learn New NPCs** | `Off` | For an NPC the plugin does not recognise, looks its race, gender, and origin up on the OSRS Wiki once and remembers it. The first line still uses the default voice while the lookup runs. |

</details>

<details>
<summary><b>Delivery</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Emotional Delivery** | `On` | Matches the voice to the emotion on the speaker's chat-head. Off voices every line as Neutral. |
| **Spoken Language** | `English` | Speaks dialogue in another language, keeping names, places, and item terms intact. Translation adds a little latency per line. |
| **Player Speaking Style** | `None` | A delivery register layered onto your own lines (Gen Z slang, pirate speak, formal, and so on). |
| **NPC Speaking Style** | `None` | The same set of styles, applied to NPC lines instead; composes with any Spoken Language. |
| **Speaking Pace** | `100` | How fast dialogue is spoken, as a percent of normal. |
| **Cave Echo** | `Off` | Adds a decaying echo to dialogue spoken below the overworld (cave, dungeon, sewer, or basement). |

</details>

<details>
<summary><b>Advanced</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Cache Size Limit (MiB)** | `1024` | Maximum size of the on-disk audio cache; the oldest clips are deleted first. Set to `0` for no limit. |
| **Max Characters Per Line** | `0` | Caps how many characters of a line are voiced, to bound worst-case per-line cost. `0` voices the whole line. |
| **Debug Logging** | `Off` | Writes detailed per-line voice decisions and timing logs, for troubleshooting. |

</details>

## For developers

**Requirements:** Java 17 and Gradle (wrapper included). The plugin's `src/main` sources compile at release 11 for Plugin Hub compatibility, so keep them free of Java 12+ syntax and APIs; tests use Java 17.

```bash
git clone https://github.com/grabartley/runelite-voiced-dialogue.git
cd runelite-voiced-dialogue
./gradlew build
```

Run the `com.grahambartley.VoicedDialoguePluginRunner` class with VM options `-ea --add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED`, either from your IDE or wired into `build.gradle`. See [docs/architecture.md](docs/architecture.md) for how the synthesis pipeline works end to end.

**Tech stack:** Java, the Gemini and OpenRouter speech APIs for the cloud voice, and the RuneLite plugin framework.

## Thanks

Voiced Dialogue stands on the shoulders of others: [Google AI Studio](https://aistudio.google.com) and [OpenRouter](https://openrouter.ai) for serving the cloud voice, and the RuneLite devs for making plugin development genuinely fun.

## Contribute

Got ideas or found a bug? [Open an issue](https://github.com/grabartley/runelite-voiced-dialogue/issues) and let's talk.

## License

Released under the [MIT License](LICENSE).
