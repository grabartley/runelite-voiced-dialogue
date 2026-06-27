# NPC voice table tooling

Offline tooling that produces the bundled `src/main/resources/npc-voices.json`
lookup table. **None of this runs inside the plugin.** At runtime the plugin only
reads the generated JSON and does a single in-memory map lookup keyed by NPC id,
so there are no network calls or large downloads when choosing a voice.

## Files

- `tools/generate_npc_voices.py` - the generator. Builds the table from a **full
  NPC dump** plus the curated overrides, then writes the bundled resource.
- `tools/overrides.json` - hand-curated, **authoritative** `npcId -> {race, gender}`
  entries. These always win over anything the generator infers, and they pin
  high-traffic named dialogue NPCs (Hans, the Cook, quest givers) whose gender
  cannot be read off the name.
- `tools/profiles.json` - hand-curated **character voice profiles** for the cloud
  (Gemini) backend (accent, style, pace). The generator embeds this file verbatim
  into the output under a top-level `profiles` key, so regenerating the table
  never wipes it. See [Character voice profiles](#character-voice-profiles-cloud).

## Data sources

- **Full NPC summary (required).** [`npcs-summary.json`][summary] from the
  community `osrsreboxed-db` fork (the now-defunct OSRSBox DB's successor). This
  is the coverage backbone: it publishes `id -> name` for **every** NPC
  definition, including the peaceful, dialogue NPCs players actually talk to. Its
  ids are the real RuneLite/OSRS cache ids, so they match what the live client
  reports via `NPCComposition#getId` (verified against the OSRS cache, e.g.
  Hans = 3105, Cook = 225, Thurgo = 4733).
- **Monster dump (optional).** OSRSBox `monsters-complete.json` is still consulted
  when reachable, only for the richer `examine` text it carries on attackable
  NPCs, which sharpens race/gender on monsters. The generator runs fine without
  it.

> Earlier versions sourced **only** from `monsters-complete.json` (attackable
> monsters), so peaceful dialogue NPCs were absent and collapsed to the
> human-male default, and the ids did not even match the live client's id space.
> That is the bug this tooling now fixes.

[summary]: https://raw.githubusercontent.com/0xNeffarion/osrsreboxed-db/master/docs/npcs-summary.json

## Regenerate the table

From the repo root:

```bash
# Downloads the full NPC summary (and, if reachable, the monster dump) and
# writes the resource:
python3 tools/generate_npc_voices.py
```

Or point it at local dumps and/or a custom output path:

```bash
python3 tools/generate_npc_voices.py \
    --npcs /path/to/npcs-summary.json \
    --monsters /path/to/monsters-complete.json \
    --overrides tools/overrides.json \
    --out src/main/resources/npc-voices.json
```

Then build and test:

```bash
./gradlew test spotlessCheck
```

Commit the regenerated `src/main/resources/npc-voices.json` alongside any
overrides changes.

## How classification works

1. **Full NPC source.** Every named NPC from `npcs-summary.json` is processed and
   **kept** (including plain Human/Male townsfolk), so the correct gender is
   always pinned. The optional monster dump adds `examine` text for attackable
   NPCs.
2. **Deterministic classifier.** A conservative, word-aware keyword classifier
   assigns a race (Human, Elf, Dwarf, Goblin, Troll, Undead, Demon, Wizard) and
   gender (Male, Female) from the name/examine text:
   - **Race:** distinctive creature keywords map to the closest voice bucket;
     anything human-looking defaults to `Human`.
   - **Gender:** gendered titles/words (e.g. `woman`/`lady`/`sister` vs
     `man`/`sir`/`father`) win first. For human-looking NPCs with no title, a
     small **curated female first-name allowlist** (e.g. Gertrude, Cassie) fires
     on the first name token so common female townsfolk are not defaulted to
     male. Everything else defaults to `Male`.
3. **No dropping.** Unlike the old generator, entries that resolve to the
   Human/Male default are **kept**, so townsfolk get an explicit, correct gender
   instead of falling through to the runtime default.
4. **Merge overrides.** `overrides.json` is applied last and always wins.

## Fixing a wrong voice

The classifier is intentionally simple, so it will get some NPCs wrong. **Do not
hand-edit `npc-voices.json`** (it gets overwritten on regeneration). Instead add
or correct the entry in `overrides.json`, then regenerate. Example:

```json
{
  "npcs": {
    "10681": { "name": "Aubury", "race": "Wizard", "gender": "Male" }
  }
}
```

The optional `name` field is documentation only and is ignored by the generator.
You can find an NPC's id with the RuneLite developer tools, the OSRS Wiki, or by
enabling **Debug Mode** in the plugin (it logs the id and chosen voice per NPC).

## Character voice profiles (cloud)

Alongside the `npcId -> {race, gender}` table used to pick a Kokoro/Gemini voice,
the bundled resource carries a `profiles` section that steers **how** the cloud
(Gemini) backend delivers each line: accent, style, and pace, rendered into a
Gemini `AUDIO PROFILE` / `DIRECTOR'S NOTES` block prepended to the spoken text.
Chat-head emotion is layered on top as a separate inline tag, so the two compose.
The local Kokoro backend ignores profiles (it takes no prompt).

The source of truth is `tools/profiles.json`; the generator embeds it under the
output's `profiles` key. This is a **British** medieval fantasy world: everything
defaults to a British accent and nothing sounds American.

### Layers (all matches combine)

An NPC can be several things at once (a Fremennik human, a ghost pirate), so
**every** matching layer contributes. `style` accumulates across all contributing
layers so the persona blends; `name`, `accent`, and `pace` are single-valued, so
the most specific layer that sets each one wins (per-NPC, then the last matching
category, then race, then the default), which keeps a coherent accent and pace
instead of stacking contradictory directions.

1. `default` - the global British fallback. **Must be complete** (all four of
   `name`, `accent`, `style`, `pace`). Every other layer is sparse and contributes
   only the fields it sets.
2. `byRace[race]` - one per race bucket (Human, Elf, Dwarf, Goblin, Troll, Undead,
   Demon, Wizard), keyed by the same race the table assigns the NPC.
3. `byCategory[]` - an **ordered** list; **every** entry whose `keywords`
   word-match the NPC's display name contributes (in declaration order). This
   expresses categories the 8 race buckets cannot (leprechaun -> Irish, vampyre ->
   Dracula-esque, Fremennik -> Scandinavian, gnome, imp, ghost, pirate, royalty,
   and so on). Keyword matching is case-insensitive and bounded on word edges, so
   `imp` matches "Imp" but not "important".
4. `byId[npcId]` - per-NPC **bespoke** overrides keyed by the live NPC id
   (`NPCComposition#getId`). Sparse: carry only what is unique to the character
   (usually `name` + `style`); its style is added on top of the blend, and accent
   and pace inherit from the category/race/default layer unless it sets them.

A bespoke entry therefore needs only its unique fields:

```json
"byId": {
  "3105": { "name": "Hans", "style": "An eccentric, doddery old groundskeeper ..." }
}
```

The `byId` map is grown over successive batches (250 NPCs per PR). Until an NPC
has a bespoke entry it still gets a solid profile from the category/race/default
layers, so coverage is universal from day one. Find an NPC's id with the RuneLite
developer tools, the OSRS Wiki, the OSRS cache `npctypes` dump, or **Debug Mode**
(it logs the resolved profile and which layer won per line).

Player lines use the `player` layer over the default; the three player fields in
the plugin config (accent/style/pace) override it at runtime when non-blank.

## Expanding coverage

The table can be crowdsourced and grown over time. Three ways to add coverage:

- Add authoritative entries to `overrides.json` (best for named dialogue NPCs and
  corrections).
- Add an unambiguous female first name to the curated allowlist in
  `generate_npc_voices.py`, then regenerate (best for whole townsfolk classes).
- Improve the race keyword rules in `generate_npc_voices.py` for whole classes of
  creatures, then regenerate.
