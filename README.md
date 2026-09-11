# Voiced Dialogue

<p align="center">
<a href="https://github.com/grabartley/runelite-voiced-dialogue/stargazers"><img src="https://img.shields.io/github/stars/grabartley/runelite-voiced-dialogue?logo=github&label=Stars&color=4078c0" alt="GitHub stars"></a>
<a href="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml"><img src="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml/badge.svg" alt="Release"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-28a745.svg" alt="License: MIT"></a>
<a href="https://ko-fi.com/grahambartley"><img src="https://img.shields.io/badge/Ko--fi-Support-009078?logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
</p>

> Voiced Dialogue leverages a cloud service ([Google AI Studio](https://aistudio.google.com) or [OpenRouter](https://openrouter.ai)) to deliver high quality TTS with advanced features like emotion, accents, and per-NPC personalities. On Google AI Studio, dialogue starts speaking in well under a second. It requires usage credits: you pay only for the audio you generate, and a line of dialogue costs roughly $0.0025 (~€0.0023) on average to voice. See [Get started](#get-started) for setup.

## Gielinor, out loud

Every quest, every shopkeeper, every back-alley stranger: **now they actually talk.** Voiced Dialogue gives NPCs and your own adventurer real AI voices in real time, turning silent text boxes into a living, breathing world you can hear.

Walk up, talk, and listen. That is the whole setup.

## What you get

- **A voice for everyone.** NPCs and the player each get a distinct voice, so a goblin never sounds like a king.
- **Over 14,100 NPCs, already voiced.** The plugin knows exactly who is speaking and instantly picks the right voice, matched by race and gender. Bump into someone added in a future game update? Flip on **Auto-learn** and the plugin works out who they are all by itself.
- **Accents and personalities with real craft.** 18 races and 14 regional origins each map to their own accent: Scottish dwarves, South London trolls, Irish leprechauns, Dracula-esque vampyres, Norse Fremennik raiders, the gothic dread of Morytania, and more. On top of that, **over 6,400 NPCs** get a hand-written personality with its own style and speaking pace, so the icons of Gielinor sound like themselves.
- **Real emotion.** The plugin reads each speaker's chat-head expression and delivers the line happy, sad, angry, scared, or neutral, so a furious dwarf actually sounds furious.
- **You star in it too.** Set your own hero's accent, persona, and pace and play the dashing knight, the gruff mercenary, or the chaos goblin of your dreams.
- **A narrator for the story.** Switch on **Voice Narration** and the message and item boxes quests lean on ("You find a key.", the cutscene beats between conversations) are read aloud by a dedicated narrator voice, so a quest keeps its voice at the moments it is actually about.
- **Examine anything, hear it described.** Switch on **Voice Examine Text** and the narrator reads the flavour line behind every item, NPC, and bit of scenery you examine.
- **Your chat, out loud.** Turn on **Voice My Public Chat** and everything you type in public chat is spoken in your character's voice.
- **Speak any language, any vibe.** Pipe dialogue through another language, or drop a delivery style over it: be a roadman in Gen Z slang among posh nobles, or run the whole realm as a pirate crew.
- **Atmosphere on tap.** Lines spoken underground pick up a cave echo, so dungeons and sewers feel enclosed.
- **Clean by default.** Always-on, offline profanity filtering keeps things friendly with no setup.
- **Fast and out of the way.** Voices play in real time, and skipping a line cuts its audio instantly, so the client stays snappy even when you mash through dialogue.

## Get started

Install from the **RuneLite Plugin Hub**: open RuneLite, click the wrench (Configuration) icon, open the **Plugin Hub**, search for **Voiced Dialogue**, and install.

Voiced Dialogue voices dialogue through the cloud, so it needs an API key from one of two providers. There is no subscription with either: you only pay for the audio you actually generate.

### Choosing your provider

Voiced Dialogue voices dialogue through one of two cloud services. Both use the same Gemini TTS model, so **the voices, accents, emotion, and personalities are identical**. They differ in how quickly a line starts speaking, how many fresh lines you get in a day, and how much setup they ask of you.

|  | Google AI Studio | OpenRouter |
|---|---|---|
| **Pros** | Dialogue starts speaking in well under a second, however long the line is, because audio streams as it is generated. NPCs answer you almost immediately, which is the difference between the plugin feeling alive and feeling like it is buffering. | No daily ceiling, so you can voice as much fresh dialogue as you like in a sitting. The simplest setup too: make an account, add a few euro of credit, paste the key. |
| **Cons** | Slightly longer setup, since you have to enable billing on the Google project behind your key. Even with billing on, Google starts a key at **100 requests per day** while the plugin's speech model is in preview. That is up to 100 brand-new lines a day, and **Prefetch Dialogue** spends some of them on options you never end up picking. Billing does not raise that one, though Google does lift it for accounts with heavy long-term use. | Noticeably higher latency on every line, and it grows with the length of the line, because OpenRouter has no streaming support and sends nothing until the whole clip is generated. A long quest speech can leave you waiting a long time before it starts. |

Measured time until a line starts speaking, same dialogue through both:

| Line length | Google AI Studio | OpenRouter |
|---|---|---|
| Short (20 chars) | **0.7s** | 1.7s |
| Medium (100 chars) | **0.8s** | 6.3s |
| Long (400 chars) | **0.8s** | 19.5s |
| Very long (500+ chars) | **0.8s** | 37.2s |

**Pick Google AI Studio for speed.** The extra setup step is worth it: in real questing, dialogue keeps pace with you instead of making you wait on every line. The catch is the starting daily ceiling above, which lands at roughly a couple of sessions of meeting new characters.

**Pick OpenRouter for volume.** Nothing caps how much fresh dialogue you voice in a day, so a long questing binge keeps talking. You pay for it in waiting: the longer the line, the longer before it starts.

You can switch at any time with the **Voice Provider** setting, and lines you have already heard replay instantly and free from your local cache either way, so neither the cap nor the wait applies twice to the same line.

### Setting up Google AI Studio

1. **Create an API key.** Go to [aistudio.google.com/apikey](https://aistudio.google.com/apikey), sign in with your Google account, create an API key, and copy it.
2. **Enable billing on the key's project.** From the same page, open the project behind your key and turn billing on. **Do not skip this:** the free tier allows only a handful of speech requests per day, so without billing the plugin voices a few lines and then goes quiet. Costs are per character and are listed on Google's [Gemini API pricing page](https://ai.google.dev/pricing). Billing lifts the free-tier limit, not the 100-a-day starting ceiling on the preview speech model.
3. **Select the provider and paste the key.** In RuneLite, open the Voiced Dialogue settings, set **Voice Provider** to **Google AI Studio**, and paste the key into the **Google AI Studio API Key** field under **General**.
4. **Talk to someone.** Walk up to any NPC and start a conversation. Lines should start speaking about a second after the text box appears, however long they are.

### Setting up OpenRouter

1. **Create an account.** Go to [openrouter.ai](https://openrouter.ai), click **Sign Up**, and pick **Sign in with Google** (GitHub or email work too).
2. **Add credits.** Open your [Credits page](https://openrouter.ai/settings/credits) and top up. **€5 is plenty to start.** A line of dialogue costs roughly $0.0025 (~€0.0023) on average to voice, so €5 covers over 2,000 lines.
3. **Create an API key.** Open your [API Keys page](https://openrouter.ai/settings/keys), create a new key (name it anything, like `RuneLite`), and copy it.
4. **Select the provider.** In the Voiced Dialogue settings, set **Voice Provider** to **OpenRouter**.
5. **Paste the key into the plugin.** Paste it into the **OpenRouter API Key** field under **General**.
6. **Talk to someone.** Walk up to any NPC and start a conversation. If they answer out loud, you are done.

Until a key is set for your chosen provider, lines stay silent and a one-time notice points you to the key.

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

</details>

<details>
<summary><b>Session spend on demand</b></summary>

Type `::voicedspend` in chat and the plugin answers with what this session has cost so far: lines voiced, lines pre-voiced by prefetch, and the spend, one chat line per provider you used. Lines replayed from your cache are free and are counted nowhere, so a session spent on dialogue you have heard before reads as zero. Totals are session-only and reset whenever the plugin restarts.

**On OpenRouter the figure is the real one.** OpenRouter reports what your key has spent, so the readout takes a reading when the session starts and quotes the difference. That is the same number your account is billed, not a model of it. It covers everything on that key, so other apps sharing it show up too.

**On Google AI Studio the figure is an estimate, because Google does not expose a real one.** The Gemini API returns no cost, and actual billing lives behind the Cloud Billing API, out of reach for a plugin. What it does return is the audio and text tokens it really metered for every call, so the readout shows those measured counts and converts them at [Google's published rate](https://ai.google.dev/pricing). Only the rate is assumed; the quantities are the API's own.

Setting a non-English **Spoken Language** or a **Speaking Style** adds a translation call per line against a second, much cheaper model, and both providers account for it. On OpenRouter it bills to the same key, so it is already inside the reported spend. On Google AI Studio it is metered and priced at its own model's rate, and shown as its own bucket, so a translated session is never costed as though the hop were free.

</details>

> **Privacy:** only the dialogue text being spoken is sent to your chosen provider (OpenRouter or Google AI Studio) over HTTPS, and lines you have already heard replay from your local cache without going anywhere.

## Configuration

Settings mirror the in-game panel: **General** (provider, keys, playback, caching), **Voices** (who sounds like what), **Delivery** (how each line is spoken), and **Advanced** (niche tuning).

<details>
<summary><b>General</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Voice Provider** | `Google AI Studio` | The cloud service that voices dialogue and bills the calls. The voices sound the same on both: Google AI Studio starts speaking far sooner, OpenRouter voices any number of new lines a day. See [Choosing your provider](#choosing-your-provider). |
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
| **Voice Narration** | `Off` | Reads message and item boxes aloud in a narrator voice. This is the game telling the story rather than a character speaking, so it keeps its own voice and ignores the speaking styles. The game shows these boxes for interface prompts as well as story beats, so a world switch warning is narrated too. |
| **Voice Examine Text** | `Off` | Reads examine text aloud in the narrator voice when you examine an item, an NPC, or scenery. Examine lines are short and repeat heavily, so after the first hearing they replay free from the cache, but exploring somewhere new voices a lot of fresh lines. |
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
| **Cave Echo** | `Off` | Adds a decaying echo to dialogue spoken below the overworld (cave, dungeon, sewer, or basement). The narrator is not in the room with you, so narration stays dry. |

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

```bash
git clone https://github.com/grabartley/runelite-voiced-dialogue.git
cd runelite-voiced-dialogue
./gradlew clean build
```

Setup, tests, and launching the dev client are covered in [docs/development.md](docs/development.md); [docs/architecture.md](docs/architecture.md) explains how the synthesis pipeline works end to end.

**Tech stack:** Java, the Gemini and OpenRouter speech APIs for the cloud voice, and the RuneLite plugin framework.

## Thanks

Voiced Dialogue stands on the shoulders of others: [Google AI Studio](https://aistudio.google.com) and [OpenRouter](https://openrouter.ai) for serving the cloud voice, and the RuneLite devs for making plugin development genuinely fun.

## Contribute

Got ideas or found a bug? [Open an issue](https://github.com/grabartley/runelite-voiced-dialogue/issues) and let's talk.

## License

Released under the [MIT License](LICENSE).
