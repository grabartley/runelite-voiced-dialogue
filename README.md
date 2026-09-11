# Voiced Dialogue

<p align="center">
<a href="https://github.com/grabartley/runelite-voiced-dialogue/stargazers"><img src="https://img.shields.io/github/stars/grabartley/runelite-voiced-dialogue?logo=github&label=Stars&color=4078c0" alt="GitHub stars"></a>
<a href="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml"><img src="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml/badge.svg" alt="Release"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-28a745.svg" alt="License: MIT"></a>
<a href="https://ko-fi.com/grahambartley"><img src="https://img.shields.io/badge/Ko--fi-Support-009078?logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
</p>

**Gielinor talks back.** Voiced Dialogue speaks every dialogue box aloud with cloud AI voices, in real time, as you play.

Bring your own API key. You pay only for the audio you generate, about **$0.0025 a line**, and every line you have already heard replays free from your cache.

## What it does

- **14,103 NPCs already voiced**, matched by race and gender. For anyone a game update adds later, turn on **Auto-learn New NPCs** and the plugin works them out from the wiki.
- **Accents with lore behind them.** 18 races and 14 regional origins each get their own: Scottish dwarves, South London trolls, Norse Fremennik, Kharidian desert nomads, Varlamoran nobles, the gothic dread of Morytania.
- **6,447 hand-written character profiles**, so the names you know sound like themselves rather than like their species.
- **Emotion off the chat-head.** The plugin reads the speaker's expression and delivers the line happy, sad, angry, scared, or neutral.
- **You get a voice too.** Set your accent, persona, and pace, and optionally speak your public chat aloud.
- **A narrator for the quest beats.** Turn on **Voice Narration** for the message and item boxes quests lean on, and **Voice Examine Text** to hear anything you examine.
- **Any language, any register.** Speak the whole game in Spanish, or run Gielinor as a pirate crew, Gen Z roadmen, or Shakespearean nobles.
- **Cave echo underground**, so dungeons and sewers sound enclosed.
- **Fast and out of the way.** Synthesis is off the game thread, skipping a line cuts its audio instantly, and repeats replay from disk.

Offline profanity filtering is always on. What leaves your client is the line being spoken and the character direction steering it, over HTTPS to your chosen provider; a line you have heard before replays from your local cache without going anywhere. **Auto-learn New NPCs**, off unless you turn it on, also looks up an unrecognised NPC's name on the OSRS Wiki.

## Install

Open RuneLite, click the wrench (Configuration), open the **Plugin Hub**, search **Voiced Dialogue**, install.

Then pick a provider and paste in a key. Until you do, dialogue stays silent and a one-time chat notice tells you how to set one up.

## Pick a provider

Both providers run the same Gemini TTS model, so **the voices, accents, and emotion are identical**. The difference is speed and volume.

| Line length | Google AI Studio | OpenRouter |
|---|---|---|
| Short (20 chars) | **0.7s** | 1.7s |
| Medium (100 chars) | **0.8s** | 6.3s |
| Long (400 chars) | **0.8s** | 19.5s |
| Very long (500+ chars) | **0.8s** | 37.2s |

**Google AI Studio** streams audio as it is generated, so a line starts in under a second no matter how long it is. While the speech model is in preview, a billed key begins at **up to 100 fresh lines a day**, and **Prefetch Dialogue** draws on the same allowance by pre-voicing options you may never pick. Enabling billing does not lift that ceiling, though Google does raise it for accounts with heavy long-term use.

**OpenRouter** has no daily cap, so a long questing binge keeps talking, but it sends nothing until the whole clip is finished. A quest speech can leave you waiting the better part of a minute.

Switch any time with **Voice Provider**. Cached lines are instant and free on both.

### Google AI Studio

1. Go to [aistudio.google.com/apikey](https://aistudio.google.com/apikey), sign in, create a key, copy it.
2. From the same page, open the key's project and **turn billing on**. Without it you get a handful of lines a day and then silence. Rates are on the [Gemini API pricing page](https://ai.google.dev/pricing).
3. In RuneLite, set **Voice Provider** to **Google AI Studio** and paste the key into **Google AI Studio API Key**.
4. Talk to any NPC.

### OpenRouter

1. Sign up at [openrouter.ai](https://openrouter.ai).
2. Top up on the [Credits page](https://openrouter.ai/settings/credits). **€5 covers over 2,000 lines.**
3. Create a key on the [API Keys page](https://openrouter.ai/settings/keys) and copy it.
4. In RuneLite, set **Voice Provider** to **OpenRouter** and paste the key into **OpenRouter API Key**.
5. Talk to any NPC.

## Track your spend

Type `::voicedspend` in chat for this session's lines voiced, lines prefetched, and cost, one line per provider. Cached replays are free and counted nowhere. Totals reset when the plugin restarts.

On OpenRouter the figure is the real billed amount read from your key. On Google AI Studio it is an estimate: Google returns the tokens it metered but no cost, so the plugin prices those measured counts at Google's published rate. A non-English language or a speaking style adds a cheap translation call per line, accounted for separately.

## Settings

<details>
<summary><b>General</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Voice Provider** | `Google AI Studio` | Which cloud service voices dialogue and bills the calls. See [Pick a provider](#pick-a-provider). |
| **OpenRouter API Key** | empty | Your OpenRouter key; stored locally, never bundled with the plugin. |
| **Google AI Studio API Key** | empty | Your Gemini key; stored locally, never bundled with the plugin. |
| **Dialogue Volume** | `20` | Loudness of spoken dialogue, `0` (muted) to `100`. |
| **Voice My Public Chat** | `Off` | Speaks your public chat aloud in your player voice, exactly as typed. |
| **Prefetch Dialogue** | `On` | Pre-voices the dialogue options on screen so your pick plays instantly; can spend credit on branches you never choose. |
| **Save Audio To Disk** | `On` | Keeps audio on disk so repeated lines replay instantly and free across sessions. |
| **Stream Playback** | `On` | Starts speaking as audio arrives instead of waiting for the full clip. Only Google AI Studio delivers audio early enough for this to help. |

</details>

<details>
<summary><b>Voices</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Player Voice** | `Type A` | The voice for your character's dialogue and public chat. |
| **Your Accent** | British (Cambridge) | Accent for your character's voice. Needs **Character Voices** on. |
| **Your Persona** | friendly and plucky | Persona and delivery style for your character. Needs **Character Voices** on. |
| **Your Pace** | Normal | Speaking pace for your character. Needs **Character Voices** on. |
| **Character Voices** | `On` | Gives each speaker their own accent, persona, and pace. Off gives one plain shared delivery, the cheapest option. |
| **Voice Narration** | `Off` | Reads message and item boxes in the narrator voice. The game uses these boxes for interface prompts too, so a world switch warning gets narrated. |
| **Voice Examine Text** | `Off` | Narrates examine text for items, NPCs, and scenery. Short and heavily repeated, so mostly cached after the first hearing. |
| **Auto-learn New NPCs** | `Off` | Looks an unrecognised NPC's race, gender, and origin up on the OSRS Wiki once and remembers it. Their first line still uses the default voice. |

</details>

<details>
<summary><b>Delivery</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Emotional Delivery** | `On` | Matches the voice to the emotion on the speaker's chat-head. Off voices everything neutral. |
| **Spoken Language** | `English` | Speaks dialogue in another language, keeping names, places, and item terms intact. Adds a little latency per line. |
| **Player Speaking Style** | `None` | A register layered onto your own lines: Gen Z slang, pirate speak, formal, and more. |
| **NPC Speaking Style** | `None` | The same styles applied to NPC lines. Composes with any Spoken Language. |
| **Speaking Pace** | `100` | Speech speed as a percent of normal. |
| **Cave Echo** | `Off` | Adds a decaying echo below the overworld (cave, dungeon, sewer, basement). Narration stays dry. |

</details>

<details>
<summary><b>Advanced</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Cache Size Limit (MiB)** | `1024` | Maximum on-disk cache size; oldest clips are deleted first. `0` for no limit. |
| **Max Characters Per Line** | `0` | Caps how much of a line is voiced, to bound worst-case cost. `0` voices the whole line. |
| **Debug Logging** | `Off` | Writes per-line voice decisions and timing logs for troubleshooting. |

</details>

## For developers

```bash
git clone https://github.com/grabartley/runelite-voiced-dialogue.git
cd runelite-voiced-dialogue
./gradlew clean build
```

[docs/development.md](docs/development.md) covers setup, tests, and the dev client. [docs/architecture.md](docs/architecture.md) explains the synthesis pipeline end to end.

Built on Java, the RuneLite plugin framework, and the Gemini and OpenRouter speech APIs. Thanks to the RuneLite devs for making plugin development genuinely fun.

Got ideas or found a bug? [Open an issue](https://github.com/grabartley/runelite-voiced-dialogue/issues).

Released under the [MIT License](LICENSE).
