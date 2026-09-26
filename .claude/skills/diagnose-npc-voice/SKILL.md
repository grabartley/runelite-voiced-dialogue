---
name: diagnose-npc-voice
description: Investigate why an NPC's spoken voice, accent, gender, or delivery is wrong. Use when an NPC "sounds wrong", has the wrong accent/gender, sounds British when it shouldn't, is silent, or otherwise voices incorrectly, to find the root cause from the debug logs before fixing.
---

# Diagnose an NPC voice issue

Find the root cause from the runtime logs, then fix at the source via the
`add-npc-profile` skill. Do not guess, read what actually resolved.

## Get logs

Launch with the `run-game-client` skill (it runs with `--debug` and logs to
`/tmp/tts-client.log`), turn **Debug Mode** on in the plugin config, set an
API key for the selected provider so dialogue is voiced, and reproduce the line. Then read the
trace lines the plugin emits per line:

```
[TTS voice]   npc='X' world=HIT(id=<activeId>) race=R gender=G source=table-hit -> seed=<n>
[TTS profile] npc='X' id=<id> race=R ethnicity=E -> '<profile name>' (source=..., accent='...', voiceRegion=REGION)
```

- `[TTS voice]` = the **timbre** pick: a `voiceRegion` on the profile selects that region's native voice pool, otherwise race/gender select a Gemini voice sub-pool, and `seed` spreads same-race/gender NPCs across it. `[TTS cloud] ...` lines show the actual request/response.
- `[TTS voice] cloud emotion X -> style '...'` (Debug Mode) = the exact style string sent in `speech_metadata.style`: the full profile plus the emotion. The most direct evidence for a wrong accent.
- `[TTS profile]` = the **delivery** (accent/style/pace). `source=` lists every layer that combined, e.g. `race:Human+ethnicity:karamja+keyword:vampyre+id:123`. For single-valued fields the **last** layer that sets them wins.

## Common root causes

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `race=Unknown` / generic default | id not in the bundled table: a **variant id** the wiki doesn't list, an `Infobox Monster` creature, or a brand-new NPC | add to `overrides.json`; the runtime wiki auto-learn covers brand-new ones if enabled |
| Sounds British when it should be regional | `ethnicity` wrong: `leagueRegion` is **where found, not where from** (a foreigner in a region), or the NPC is a disambiguation-named page with no data | set/clear `ethnicity` in `overrides.json` |
| Right ethnicity but accent ignored | a social-role category once clobbered it; confirm `source` and that role categories (royalty/knight/noble/monk/wizard) are **style-only** | ensure ethnicity sets the accent; roles add register via style |
| Wrong gender | wiki has no gender, a mixed-gender **switch infobox** (per-version), or a wiki error | `overrides.json` `gender`; the generator pairs gender per version, a wiki gap still needs an override |
| Two different ids for one NPC across the two trace lines | transformed multiloc NPC: **active id** (`NPC#getId`) vs base composition id; the table is keyed by the active id | n/a (expected); use the active id when pinning |
| Accent weak or missing (sounds generic, not the intended accent) | the resolved `accent` is a soft phrase, or a bespoke `accent` holds a delivery quirk instead of an accent; Gemini 3.8 falls back to a generic default on both | rewrite it as "Strong <X> accent, <variety> pronunciation" and move any quirk into `style` ([#332](https://github.com/grabartley/runelite-voiced-dialogue/issues/332) found any prebuilt voice holds a strongly phrased accent) |
| Line is silent | cloud returned a non-PCM body (rate-limit/quota/error) | check `[TTS cloud] ... response: HTTP ... contentType=... body snippet`; it's the cloud robustness path. On Google AI Studio an HTTP 429 usually means the speech model's per-day request cap for the key's usage tier, which billing does not lift; the notice names which quota ran out |

## Key facts to remember

- `npc-voices.json` is generated, never hand-edited; fixes go in `tools/overrides.json` (race/gender/ethnicity) or `tools/profiles.json` (accent/style/pace).
- Resolution: `default -> byRace -> byEthnicity (plain folk only) -> byCategory (all keyword matches) -> byId`; style accumulates, name/accent/pace most-specific-wins.
- Hand off the fix to the `add-npc-profile` skill.
