---
name: add-race
description: Add a brand-new first-class race (e.g. Tortugan, Monkey) to the plugin, wiring it through the cloud voice map and the race->accent model, then assign every NPC of that race in overrides. Use when a species needs its own race + dedicated accent, not just a per-NPC or ethnicity tweak.
---

# Add a first-class race

Use this when a species should become a **first-class `NPCRace`** with its own dedicated cloud voice and its own racial accent, the way Elf, Dwarf, Goblin, Monkey, Gorilla and Tortugan are. This is bigger than [[add-npc-profile]] (which only edits race/gender/ethnicity within the existing race set) and bigger than a region batch ([[fill-npc-profiles-batch]]). When you only need to make one NPC sound right, or push an accent via an existing ethnicity, use [[add-npc-profile]] instead.

## Inputs

- **Race name** (TitleCase, e.g. `Tortugan`). Becomes the enum constant, the `VALID_RACES` member, the `byRace` key, and the override `race` value. Keep it identical everywhere (the generator validates overrides against `VALID_RACES`).
- **Accent** the race should carry everywhere, phrased positively (see World rules).
- **Optional reference material**: a wiki race page, a roster, cache prefixes. Used to find every NPC id of the race.

## Resolution model (why each edit exists)

Cloud profile resolution combines layers: `default -> byRace[race] -> byEthnicity[ethnicity] -> byCategory keyword matches -> byId[id]`. A distinctive race's accent lives in `byRace`, so it **wins over region** (`byEthnicity`): the race keeps its accent wherever its members are found. Do **not** set `ethnicity` on these NPCs (it is a no-op for a distinctive race). Voice timbre (the Gemini voice sub-pool) is separate from accent and is keyed purely on race+gender.

## The wiring points (touch every one)

1. **`src/main/java/com/grahambartley/voice/VoiceManager.java`** — add the constant to the `NPCRace` enum, just before `UNKNOWN`.
2. **`src/main/java/com/grahambartley/voice/NpcDemographicParser.java`** — add an `else if (raceLower.contains("..."))` arm to `toRace(...)` so a raw wiki/learned race string still maps to the new `NPCRace` constant (the runtime auto-learn path uses this, not just the generator).
3. **`src/main/java/com/grahambartley/synthesis/GeminiVoiceMap.java`** — add a `put(NPCRace.<RACE>, male(...), female(...))` in the constructor (see Gemini rule). This is the sole voice map and it keys the timbre on race+gender.
4. **`tools/generate_npc_voices.py`** — add the race to `VALID_RACES`; add a `RACE_BUCKET_RULES` regex (ordered so it does not collide with a broader bucket); add a `CATEGORY_RACE_RULES` entry so Infobox-Monster pages (no race field) still bucket by category.
5. **`tools/profiles.json`** — add a `byRace["<RACE>"]` entry: `name`, `accent`, `style`, `pace`. This is where the racial accent lives.
6. **`tools/overrides.json`** — add the race to the `_comment` valid-race list, then one one-line `npcs[id]` entry per NPC id (race + gender, no ethnicity).
7. **Docs** — `README.md` Voices table row + the "spans N races" count **and the NPC counts** (total `_meta.count`, bespoke `profiles.byId` length from the regenerated `npc-voices.json`); `docs/npc-voice-tooling.md` race note; the valid-race list in `.claude/skills/add-npc-profile/SKILL.md`.

## Gemini (cloud) sub-pool rule

Each race anchors to a small, **gender-correct** sub-pool of two Gemini voices per gender, chosen for timbre (gravelly/firm = big imposing; bright/light = small; warm/clear = friendly). `GeminiVoiceMapTest` enforces a hard invariant: **no voice may appear in any male pool and any female pool** (`maleAndFemaleVoicePoolsAreDisjointAcrossEveryRace`). So:
- Reuse only voices already classified for that gender elsewhere in the map, or introduce a voice whose gender you are sure of (the API carries no gender; it is confirmed by ear).
- Every voice must be in the 30-voice catalog hard-coded in `GeminiVoiceMapTest.GEMINI_VOICE_CATALOG`.
- Add the race to that test's `MAPPED_RACES` array.

## Build the roster (find every id)

The generator/runtime keys on the live `NPCComposition#getId`, and a race's members are scattered across variants and locations. Be exhaustive:

1. **Cache sweep.** Grep the local cache dump for every plausible internal-name prefix:
   `/Users/gbartley/.npm/_npx/*/node_modules/@jayarrowz/mcp-osrs/dist/data/npctypes.txt`
   Format is `id<TAB>internal_name`; the id is the first column (NOT the grep line number, which is off by one). Use `awk -F'\t' '$2 ~ /prefix/ {print $1"\t"$2}'`.
2. **Wiki verify.** Cross-check the race's wiki page ("Known <race>s"), the location navbox, and each NPC's Infobox `id`. **Gotcha:** cache internal names can be role-based and misleading (a Tortugan elder was named `slayer_gryphon_guardian`; a Tortugan gardener was `farming_gardener_calquat_3`). Trust the **wiki Infobox `Race` + `id`** as the source of truth, not the internal name. When the internal name and the wiki disagree, list the id and flag it for in-game Debug Mode verification in the QA checklist.
3. **Include all variants.** Combat/`_vis`/`_locked`/`_1op` variant ids of the same NPC all get the entry. Off-location members count too (verify nothing is missed outside the home region).
4. **Gender.** Take the wiki Infobox gender when stated; when genuinely indeterminate default to **Male** (unknown gender routes to the male sub-pool, so Male is the consistent safe default). Confirm ambiguous ones via dialogue pronouns in Debug Mode where it matters.
5. **Exclude** non-members that share a prefix (creatures, humans, quest NPCs) explicitly.

## Regenerate + validate

```
cp src/main/resources/npc-voices.json /tmp/before.json
python3 tools/generate_npc_voices.py --base /tmp/before.json   # offline, deterministic, no wiki drift
```
Use `--base` (see [[regenerate-npc-voices]]) for an overrides/profiles-only change: it re-applies overrides + the embedded `byRace` profile onto the existing table with a minimal diff and no network. Then confirm **only** your ids changed (diff each entry against the base) and that `byRace["<RACE>"]` is embedded. A full network run is only needed if you also want the wiki to auto-classify members by their Infobox race.

**Every NPC whose entry changes in the regenerated `npc-voices.json` must be 100% hand verified against the wiki before commit, and a change table presented to the developer** (one row per change: id(s), name, race old→new, ethnicity old→new, why). See [[regenerate-npc-voices]] and [[find-npc-true-origin]]. The burden of verification is on you.

Then:
```
./gradlew spotlessApply && ./gradlew test spotlessCheck
```
Tests to extend: `GeminiVoiceMapTest` (`MAPPED_RACES`, `GEMINI_VOICE_CATALOG` if you introduce a new voice), `NpcProfilesResourceTest` (`everyRaceBucketResolvesToItsOwnLayer` list + a stated-accent assertion).

## World rules (non-negotiable prose constraints)

- **Phrase accents positively.** Name the wanted accent ("the lilting Bajan English of Barbados"); never describe by negation. Gemini renders British/European accents reliably but foreign accents only partially, so make `style` carry the character so the line still reads well if the accent lands only halfway.
- **Keep all prose timeless.** No rollout/batch/PR/date/"for now" references in code, JSON, or comments.
- **British medieval-fantasy default.** Everything is British unless lore or trope says otherwise.

## QA handoff

In the manual QA checklist, call out: a same-race male and female NPC voice gender-correct; a line logging `source=byRace` with the new accent; and any id whose cache internal name disagreed with the wiki, for direct Debug Mode confirmation. To debug a wrong result, see [[diagnose-npc-voice]].
