# Voiced Dialogue

<p align="center">
<a href="https://github.com/grabartley/runelite-voiced-dialogue/stargazers"><img src="https://img.shields.io/github/stars/grabartley/runelite-voiced-dialogue?logo=github&label=Stars&color=4078c0" alt="GitHub stars"></a>
<a href="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml"><img src="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml/badge.svg" alt="Release"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-28a745.svg" alt="License: MIT"></a>
<a href="https://ko-fi.com/grahambartley"><img src="https://img.shields.io/badge/Ko--fi-Support-009078?logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
</p>

**Gielinor talks back.** Voiced Dialogue speaks every dialogue box aloud with cloud AI voices, in real time, as you play.

Bring your own API key. You pay only for the audio you generate, about **$0.001 a line**, and every line you have already heard replays free from your cache.

## What it does

- **14,103 NPCs already voiced by nearly 600 unique voices**, most of them native speakers from Google's library of over 2,000, matched by race, gender and accent, and each NPC keeps its voice for life. For anyone a game update adds later, turn on **Auto-learn New NPCs** and the plugin works them out from the wiki.
- **Accents with lore behind them, voiced by native speakers where they exist.** 18 races and 14 regional origins each get their own: Scottish dwarves, South London trolls, Norse Fremennik, Kharidian desert nomads, Varlamoran nobles, the gothic dread of Morytania.
- **6,451 hand-written character profiles**, so the names you know sound like themselves rather than like their species.
- **Emotion off the chat-head.** The plugin reads the speaker's expression and delivers the line happy, sad, angry, scared, or neutral.
- **Recast anyone.** Open the **NPC Voices** side panel, find any NPC by name, and give them your own accent, style, pace, or voice type, for one NPC, one character, or everyone sharing a name.
- **You get a voice too.** Set your accent, persona, and pace, and optionally speak your public chat aloud.
- **A narrator for the quest beats.** Turn on **Voice Narration** for the message and item boxes quests lean on, and **Voice Examine Text** to hear anything you examine.
- **The world talking around you.** Turn on **Voice Ambient Chatter** and the lines NPCs say over their heads, market criers, farm animals, cutscene asides, come through in their own voices, overlapping the way a crowd actually does and fading away as you walk off.
- **Any language, any register.** Speak the whole game in Spanish, or run Gielinor as a pirate crew, Gen Z roadmen, or Shakespearean nobles.
- **Cave echo underground**, so dungeons and sewers sound enclosed.
- **Fast and out of the way.** Synthesis is off the game thread, skipping a line cuts its audio instantly, and repeats replay from disk.

Offline profanity filtering is always on. What leaves your client is the line being spoken and the character direction steering it, over HTTPS to your chosen provider; a line you have heard before replays from your local cache without going anywhere. **Auto-learn New NPCs**, off unless you turn it on, also looks up an unrecognised NPC's name on the OSRS Wiki. The **NPC Voices** panel loads each listed NPC's chat-head picture from the OSRS Wiki by name.

## Install

Open RuneLite, click the wrench (Configuration), open the **Plugin Hub**, search **Voiced Dialogue**, install.

Then pick a provider and paste in a key. Until you do, dialogue stays silent and a one-time chat notice tells you how to set one up.

## Pick a provider

Both providers run the same Gemini TTS model, so **the voices, accents, and emotion are identical**. The difference is speed and volume.

| Line length | Google AI Studio | OpenRouter |
|---|---|---|
| Short (20 chars) | **1.2s** | 1.9s |
| Medium (100 chars) | **1.4s** | 3.6s |
| Long (400 chars) | **1.3s** | 10.1s |
| Very long (500+ chars) | **1.2s** | 12.6s |

**Google AI Studio** streams audio as it is generated, so a line starts in just over a second no matter how long it is. Google caps how many fresh lines a key can voice each day, and **Prefetch Dialogue** draws on the same allowance by pre-voicing options you may never pick. Enabling billing does not lift that ceiling, though Google does raise it for accounts with heavy long-term use; your key's current limit is on its AI Studio rate-limit page.

**OpenRouter** has no daily cap, so a long questing binge keeps talking, but it sends nothing until the whole clip is finished. A long quest speech can leave you waiting over ten seconds.

Switch any time with **Voice Provider**. Cached lines are instant and free on both.

### Google AI Studio

1. Go to [aistudio.google.com/apikey](https://aistudio.google.com/apikey), sign in, create a key, copy it.
2. From the same page, open the key's project and **turn billing on**. Without it you get a handful of lines a day and then silence. Rates are on the [Gemini API pricing page](https://ai.google.dev/pricing).
3. In RuneLite, set **Voice Provider** to **Google AI Studio** and paste the key into **Google AI Studio API Key**.
4. Talk to any NPC.

### OpenRouter

1. Sign up at [openrouter.ai](https://openrouter.ai).
2. Top up on the [Credits page](https://openrouter.ai/settings/credits). **€5 covers over 4,000 lines.**
3. Create a key on the [API Keys page](https://openrouter.ai/settings/keys) and copy it.
4. In RuneLite, set **Voice Provider** to **OpenRouter** and paste the key into **OpenRouter API Key**.
5. Talk to any NPC.

## Change how an NPC sounds

Click the Voiced Dialogue icon in RuneLite's sidebar to open **NPC Voices**. NPCs you hear this session and NPCs you have edited are listed with their chat-heads; search to find anyone else by name. You can also right-click an NPC that speaks and choose **Set-voice** to open the panel straight to them.

Open an NPC to set its **Voice type** (Type A or Type B, the same choice as **Player Voice**), **Accent**, **Style**, and **Pace**. Leave a field blank to keep the plugin's voice for it. Under **Apply to**, choose **Only this NPC**, **This character and its variants** (such as every Varrock guard variant), or **Everyone called** that name; each option shows how many NPCs it covers. **Clear override** puts the plugin's voice back.

Edits take effect on the next line, with no restart. An edited NPC is re-voiced, and billed again, the next time you hear each of its lines. Edits are stored in your RuneLite profile.

To share a set of voices with a friend or copy them to another RuneLite profile, use **Export** under the search bar to copy your edits to the clipboard or save them as a `.json` file. **Import** takes pasted text or a file and shows what it will change first: choose **Merge** to add the imported NPCs to your own edits, or **Replace all** to keep only the imported ones. Only your own edits are exported.

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

</details>

<details>
<summary><b>Voices</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Player Voice** | `Type A` | The voice for your character's dialogue and public chat. |
| **Your Accent** | Strong educated southern English | Accent for your character's voice. Name it strongly, with its pronunciation, e.g. "Strong Dublin Irish accent, Irish English pronunciation". Naming a region with native voices (Irish, Scottish, southern English, West Country, Scouse, Geordie, Italian, and more) voices you with a native speaker from it. |
| **Your Persona** | Plucky, peppy and upbeat, a cheerful, eager adventurer brimming with warmth and enthusiasm. | Persona and delivery style for your character. |
| **Your Delivery Pace** | Lively and bouncy, with an upbeat, energetic rhythm. | How your character paces their words. |
| **Voice Narration** | `Off` | Reads message and item boxes in the narrator voice. The game uses these boxes for interface prompts too, so a world switch warning gets narrated. |
| **Voice Examine Text** | `Off` | Narrates examine text for items, NPCs, and scenery. Short and heavily repeated, so mostly cached after the first hearing. |
| **Voice Ambient Chatter** | `Off` | Speaks the overhead lines nearby NPCs say, each in their own voice. Everyone within earshot is voiced, and they overlap, so a market square sounds like a market square. Chatter fades with distance and cuts out once the speaker walks out of range. One NPC's lines queue behind each other rather than overlapping themselves, and animal noises are left unvoiced. These play without you clicking a dialogue, so a busy area costs real calls the first time you stand in it; the lines are short and repeat heavily, so most replay free afterwards. Silent while you are in a conversation. |
| **Auto-learn New NPCs** | `Off` | Looks an unrecognised NPC's race, gender, and origin up on the OSRS Wiki once and remembers it. Clicking **Talk-to** starts the lookup, usually before the dialogue opens, so their first line lands in the right voice. |
| **Set Voice Menu Option** | `On` | Adds **Set-voice** to the right-click menu of NPCs that speak, opening that NPC in the **NPC Voices** panel. Monsters and bosses with nothing to say don't get it. Turn it off to keep NPC menus unchanged. |

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
| **Cache Size Limit (MiB)** | `1024` | Maximum on-disk cache size; oldest clips are deleted first. `0` for no limit. Clips can be [converted to WAV](docs/cache-files.md). |
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
