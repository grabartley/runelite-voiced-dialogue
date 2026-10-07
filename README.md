# Voiced Dialogue

<p align="center">
<a href="https://github.com/grabartley/runelite-voiced-dialogue/stargazers"><img src="https://img.shields.io/github/stars/grabartley/runelite-voiced-dialogue?logo=github&label=Stars&color=4078c0" alt="GitHub stars"></a>
<a href="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml"><img src="https://github.com/grabartley/runelite-voiced-dialogue/actions/workflows/release.yml/badge.svg" alt="Release"></a>
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-28a745.svg" alt="License: MIT"></a>
<a href="https://ko-fi.com/grahambartley"><img src="https://img.shields.io/badge/Ko--fi-Support-009078?logo=ko-fi&logoColor=white" alt="Ko-fi"></a>
</p>

Voiced Dialogue reads dialogue boxes out loud as you play, using AI voices from Google's Gemini text-to-speech.

You bring your own API key and pay only for the audio you generate, about **$0.001 a line**. Lines you have already heard are saved on your computer and replay for free.

## What it does

- **14,103 NPCs have voices already**, using about 700 different voices. Most are native speakers from Google's library of over 2,000. Each NPC's voice is picked to fit its race, gender, age and accent, children get child voices, and an NPC keeps the same voice every time you talk to it. For NPCs added in future game updates, turn on **Auto-learn New NPCs** and the plugin looks them up on the wiki.
- **Accents that fit the lore**, using native speakers where Google has them. 18 races and 14 regions each have their own: Scottish dwarves, South London trolls, Norse Fremennik, Kharidian desert nomads, Varlamoran nobles, and a gothic Morytania.
- **6,451 named characters have their own voice profile**, so well-known characters sound like themselves and not just like the rest of their race.
- **Names said the Jagex way.** When the voices speak English, Neitiznot, Ardougne, Saradomin and over 100 other names are said the way Jagex says them.
- **Emotion from the chat-head.** The line is spoken happy, sad, angry, scared or neutral, based on the speaker's face.
- **Change anyone's voice.** In the **NPC Voices** side panel you can search for any NPC and set its accent, style, pace or voice type. The change can apply to one NPC, one character, or everyone with that name.
- **Your character speaks too.** Set your own accent, personality and pace. You can also have your public chat read out.
- **A narrator.** Turn on **Voice Narration** to hear the message and item boxes that quests use, and **Voice Examine Text** to hear examine text.
- **Background chatter.** Turn on **Voice Ambient Chatter** to hear the text NPCs say over their heads, like market sellers and cutscene lines. Several NPCs can talk at once, and they get quieter as you walk away.
- **Other languages and styles.** Play in Spanish or another language, or have everyone talk like pirates, Gen Z, or Shakespeare.
- **Cave echo** in dungeons, caves and sewers.
- **Doesn't slow the game down.** Voices are made in the background, skipping a line stops it straight away, and every line is played at the same loudness.

A built-in offline filter blocks swear words. The only things sent from your computer are the line being spoken and a short description of how the character should sound. These go over HTTPS to the provider you pick. Lines you have heard before play from your computer and are not sent anywhere. If you turn on **Auto-learn New NPCs**, the plugin also searches the OSRS Wiki for NPCs it doesn't know. The **NPC Voices** panel loads NPC chat-head pictures from the OSRS Wiki.

## Install

Open RuneLite, click the wrench (Configuration), open the **Plugin Hub**, search **Voiced Dialogue**, install.

Then pick a provider and paste in a key. Until you do, dialogue stays silent and a one-time chat notice tells you how to set one up.

## Pick a provider

Both providers use the same Gemini model, so **the voices, accents and emotion sound the same**. The difference is speed and how many lines you can make a day.

| Line length | Google AI Studio | OpenRouter |
|---|---|---|
| Short (20 chars) | **1.2s** | 1.9s |
| Medium (100 chars) | **1.4s** | 3.6s |
| Long (400 chars) | **1.3s** | 10.1s |
| Very long (500+ chars) | **1.2s** | 12.6s |

**Google AI Studio** starts playing while the audio is still being made, so a line starts in just over a second however long it is. Google limits how many new lines a key can make each day, and **Prefetch Dialogue** uses up that same limit by voicing options you might not pick. Turning on billing doesn't raise the limit, but Google raises it over time as you spend more and move up AI Studio's usage tiers. You can see your key's current limit on its AI Studio rate-limit page.

**OpenRouter** has no daily limit, so long quest sessions never go quiet. But it only plays a line once the whole clip is finished, so a long quest speech can take over ten seconds to start.

You can switch at any time with **Voice Provider**. Both use the same saved lines, so anything you have heard on one plays instantly and for free on the other.

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

Click the Voiced Dialogue icon in RuneLite's sidebar to open **NPC Voices**. It lists the NPCs you have heard this session and any you have edited. Search by name to find anyone else. You can also right-click an NPC that talks and choose **Set-voice** to go straight to it.

Open an NPC to set its **Voice type** (Type A or Type B, like **Player Voice**), **Accent**, **Style** and **Pace**. Leave a field blank to keep the plugin's choice. Under **Apply to**, pick **Only this NPC**, **This character and its variants** (for example every Varrock guard), or **Everyone called** that name. Each option shows how many NPCs it affects. **Clear override** puts the plugin's voice back.

Changes work from the next line, with no restart. Lines from an edited NPC are made again, and paid for again, the first time you hear them after the change. Your edits are saved in your RuneLite profile.

To share your voices with a friend or move them to another RuneLite profile, use **Export** under the search bar. It copies your edits to the clipboard or saves them as a `.json` file. **Import** takes pasted text or a file and shows you what will change first. Pick **Merge** to add them to your own edits, or **Replace all** to keep only the imported ones. Only your own edits are exported.

## Track your spend

Type `::voicedspend` in chat to see how many lines you have voiced and prefetched this session, and what they cost, for each provider. Saved lines are free and aren't counted. The totals reset when the plugin restarts.

On OpenRouter this is the real amount you were charged. On Google AI Studio it is an estimate, because Google reports how much was used but not the price, so the plugin works it out from Google's published rates. Using another language or a speaking style adds a small extra charge per line to rewrite the text, and this is shown separately.

## Settings

<details>
<summary><b>General</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Voice Provider** | `Google AI Studio` | Which service makes the voices and charges you. See [Pick a provider](#pick-a-provider). |
| **OpenRouter API Key** | empty | Your OpenRouter key. Saved only on your computer. |
| **Google AI Studio API Key** | empty | Your Google AI Studio key. Saved only on your computer. |
| **Dialogue Volume** | `20` | How loud voices are, from `0` (muted) to `100`. Every line plays at this loudness, whoever is speaking. |
| **Voice My Public Chat** | `Off` | Reads your public chat out loud in your character's voice, exactly as you typed it. |
| **Prefetch Dialogue** | `On` | Voices the dialogue options on screen ahead of time so your choice plays straight away. This can cost money for options you never pick. |

</details>

<details>
<summary><b>Voices</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Player Voice** | `Type A` | The voice for your character's dialogue and public chat. |
| **Your Accent** | Strong friendly, down-to-earth southern English | Your character's accent. Write it strongly and name the pronunciation, for example "Strong Dublin Irish accent, Irish English pronunciation". If you name a place Google has native voices for (Irish, Scottish, southern English, West Country, Scouse, Geordie, Italian, Indian and more), you get a native speaker from there. |
| **Your Persona** | Plucky, peppy and upbeat, a cheerful, eager adventurer brimming with warmth and enthusiasm. | Your character's personality and way of speaking. |
| **Your Delivery Pace** | Lively and bouncy, with an upbeat, energetic rhythm. | How fast or slow your character talks. |
| **Voice Narration** | `Off` | Reads message and item boxes in a narrator voice. The game also uses these boxes for things like the world switch warning, so those get read too. |
| **Voice Examine Text** | `Off` | Reads examine text for items, NPCs and scenery. These lines are short and come up often, so most are free after the first time. |
| **Voice Ambient Chatter** | `Off` | Reads the text nearby NPCs say over their heads, each in their own voice. Several NPCs can talk at once. Voices get quieter with distance and stop when the NPC is out of range. Animal noises aren't voiced. These play without you clicking anything, so a busy area costs money the first time you visit it, but the lines repeat a lot, so most are free after that. Chatter stays quiet while you are in a conversation. |
| **Auto-learn New NPCs** | `Off` | Looks up an unknown NPC's race, gender and origin on the OSRS Wiki once and remembers it. The lookup starts when you click **Talk-to**, so it is usually done before the NPC speaks. |
| **Set Voice Menu Option** | `On` | Adds **Set-voice** to the right-click menu of NPCs that talk, which opens them in the **NPC Voices** panel. Monsters and bosses that don't talk don't get it. |

</details>

<details>
<summary><b>Delivery</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Emotional Delivery** | `On` | Matches the voice to the emotion on the speaker's chat-head. When off, every line is spoken neutrally. |
| **Spoken Language** | `English` | Speaks dialogue in another language, keeping names, places and items as they are. Each line takes a little longer to start. |
| **Player Speaking Style** | `None` | Rewrites your character's lines in a style, like Gen Z slang, pirate speak, posh and more. |
| **NPC Speaking Style** | `None` | The same styles for NPC lines. Works with any Spoken Language. |
| **Speaking Pace** | `100` | How fast voices talk, as a percent of normal speed. |
| **Cave Echo** | `Off` | Adds an echo when you are underground, like in caves, dungeons, sewers and basements. The narrator has no echo. |

</details>

<details>
<summary><b>Advanced</b></summary>

| Setting | Default | What it does |
|---------|---------|--------------|
| **Cache Size Limit (MiB)** | `1024` | How much disk space saved lines can use. The oldest are deleted first. `0` means no limit. Saved lines can be [converted to WAV](docs/cache-files.md). |
| **Debug Logging** | `Off` | Writes extra logs about each line's voice and timing, to help find problems. |

</details>

## For developers

```bash
git clone https://github.com/grabartley/runelite-voiced-dialogue.git
cd runelite-voiced-dialogue
./gradlew clean build
```

[docs/development.md](docs/development.md) covers setup, tests and the dev client. [docs/architecture.md](docs/architecture.md) explains how the plugin works.

Built on Java, the RuneLite plugin framework, and the Gemini and OpenRouter speech APIs. Thanks to the RuneLite devs for making plugins fun to build.

Got ideas or found a bug? [Open an issue](https://github.com/grabartley/runelite-voiced-dialogue/issues).

Released under the [MIT License](LICENSE).
