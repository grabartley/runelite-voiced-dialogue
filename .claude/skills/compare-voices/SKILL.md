---
name: compare-voices
description: Render the same real in-game lines through a baseline ref and the current branch, for a representative male and female speaker of every voice outcome, and hand the developer a side-by-side go / no-go QA sheet. Use before shipping any change that affects how voices sound as a whole: the TTS model, voice pools or regions, the style or prompt layout, profile layers, pitch, pacing, emotion, or provider payloads.
---

# Compare voices before and after a change

A manual regression check by ear. It is an extra QA step on top of the unit tests, not a
replacement for them: the tests prove the mechanics, this proves the result still sounds right.

## What it does

`run.sh` checks out the baseline ref in a temporary worktree, then runs every case in
[cases.json](cases.json) through the plugin's own code in both checkouts. It resolves each speaker
with `VoiceManager`, which gives the same profile, voice region and voice that the client would
pick, and then calls the real `OpenRouterTtsBackend` or `AiStudioTtsBackend` against the live API.
No part of the request is rebuilt by hand, so both sides are exactly what a player would hear on
that version.

[ClipHarnessTest.java](ClipHarnessTest.java) does the rendering. `run.sh` copies it into
`src/test` for the run only and removes it afterwards, together with the baseline worktree, even
when the run fails. It never lands in the build or CI.

The result is `voice-qa-sheet.html` in the output directory. Each card has the real line, the
Before and After clips, and the voice id, region and accent each side used, plus Go / No go
buttons. **Copy results** puts the verdicts on the clipboard to paste back into the session.

## Run it

```bash
.claude/skills/compare-voices/run.sh origin/main <out-dir>
```

- The third argument picks the provider: `openrouter` (default) or `aistudio`. Run both when the
  change touches provider payloads.
- Keys come from `VOICE_QA_KEY_FILE`, else the first RuneLite profile in
  `~/.runelite/profiles2/` that holds an OpenRouter key. Keys are read by the harness and never
  printed.
- The player speaks with the config's default accent. Set `PLAYER_ACCENT` to test a typed accent.
- Set `VOICE_QA_ONLY` to a regular expression to render only the cases whose key or group matches,
  for example `VOICE_QA_ONLY='^More races$|narrator'`.
- Use a Java 17 shell, the same one used for `./gradlew build`. The sheet builder compresses clips
  with macOS `afconvert`.
- Put `<out-dir>` in a scratch location, never inside the repo.

A full run is about 80 short lines, so it costs a few cents and takes a few minutes.

Send the developer the sheet with `SendUserFile` (display `render`) and wait for their verdicts. A
No go blocks the change until it is fixed or the developer accepts it.

## Coverage

[cases.json](cases.json) holds one male and one female speaker for each voice outcome. Each has a
real line taken from the OSRS wiki transcript named in `source`.

| Group | What it covers |
|-------|----------------|
| Regional accents | every `voiceRegion` in `tools/voice-regions.json`, reached the way real NPCs reach it (ethnicity, race or `byId`) |
| No voice region | speakers whose accent layer sets no region, so the race pool picks the voice (Monkey) |
| Pitch | Goblins (high) and Trolls (low) |
| Children | a table child, a child picked by name keyword, and a child from another region |
| Player and narrator | both player voice types and a narration box |
| More races | every other race with its own profile: elves, gorillas, undead, demons, wizards, Tortugans, Icyene, Arceuus, Aranei, dogs, crabs, penguins |
| More origins | Welsh (Tirannwn), Nigerian (Karamja), Wyrmscraig and Menaphite humans |
| Categories and roles | name-keyword layers: leprechauns, vampyres, TzHaar, imps, fairies, ogres, ghosts, pirates, barbarians, wizards, monks, royalty, knights and nobles |

A slot that no real NPC can fill carries `missing` with the reason and is shown greyed out, for
example the female Japanese, Australian, New Zealand, TzHaar and ogre slots.

## Keep the cases current

- Adding a voice region, a race with its own pool, or any layer that changes how voices are
  picked also adds a male and a female case for it here.
- Choose well-known NPCs whose display name does not hit a `byCategory` keyword, unless the slot
  is about that category. An NPC with its own `byId` accent tests only itself, so avoid those for
  broad slots.
- Non-human races ignore the ethnicity layer, so their region comes from the race.
- Lines are 8 to 30 words of real dialogue with wiki markup stripped. Emotion is `NEUTRAL` unless
  the line clearly carries one of `HAPPY`, `SAD`, `ANGRY` or `SCARED`.
- The harness uses `VoiceManager.resolveNpc`, `resolveNarrator` and `resolve(Speaker.PLAYER, …)`
  and the backend constructors. If any of those signatures change, update the harness in the
  same PR. The baseline ref must also compile against it, so compare against a ref recent enough
  to share those entry points.
