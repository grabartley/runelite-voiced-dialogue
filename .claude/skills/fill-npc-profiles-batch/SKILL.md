---
name: fill-npc-profiles-batch
description: Bulk-author bespoke per-NPC voice profiles (and race/gender/ethnicity corrections) for a whole league region, researching each from the OSRS Wiki and osrs MCP. Use for the region-by-region rollout (umbrella issue #122; batches #123-125...) or when asked to "fill in NPC profiles" for an area.
---

# Fill in NPC profiles for a league region

Bulk-add bespoke `byId` profiles and corrections for every talkable NPC in a
**league region** at a time, themed by region to avoid overlap. The engine and
per-NPC editing are covered by `add-npc-profile`; this skill is the **batch
research + authoring** loop. Work in a fresh worktree (see the `worktree` skill)
tied to the batch issue, and do every edit, regen, and commit **inside that
worktree** (verify `pwd` and `git branch --show-current`; the shell can land you
in the primary checkout on `main`).

There is **no fixed NPC cap**. Cover the whole region. Splitting a region only
makes sense if a reviewer asks for it.

## 1. Build the candidate list by LEAGUE REGION, not by town

Filter on the wiki `leagueRegion` field for the whole region. **Do not filter by
town/location names.** Town filtering silently drops everything between the
towns: the Grand Exchange, Dorgesh-Kaan and other underground cities, the mines,
the guilds, monasteries, islands (Entrana, Fossil Island), and quest areas. A
huge share of talked-to NPCs live outside the named towns.

The generator already enumerates and parses every NPC page; reuse its functions
rather than re-deriving. Enumerate `Template:Infobox NPC` pages, then per page
read `leagueRegion`, `location`, the `options` line, and the `id` groups:

```python
import sys; sys.path.insert(0, "tools"); import generate_npc_voices as g
titles = g.enumerate_npc_pages()                 # ~5-6k pages, slow; cache it
for title, wikitext, cats in g.fetch_infoboxes(titles):
    lr = (g.first_field(wikitext, "leagueRegion") or "").lower()
    if "," in lr or "&" in lr:        # multi-region NPCs carry no single ethnicity
        continue                       #   (they keep the British default; skip)
    if lr not in {"misthalin", "asgarnia"}:   # <- your region(s)
        continue
    ids = sorted({i for grp in g.parse_id_groups(wikitext) for i in grp})
    # talkable test: the Infobox options line must contain "Talk-to"
```

Then:
- **Talkable filter:** keep only NPCs whose `|options...=` line contains
  `Talk-to` (case-insensitive). This drops museum displays, livestock, pets,
  furniture, fake-player NPCs, and pure-combat monsters. A few genuine quest NPCs
  with a blank options field fall out too; that is acceptable.
- **Subtract the already-bespoke:** skip any NPC whose ids are already in
  `tools/profiles.json` `byId`.
- **Cache** the enumeration/fetch to a temp file. The full wiki crawl takes
  several minutes; you will re-run analysis many times.

## 2. Research in parallel (the fan-out)

Authoritative data is per-page, so fan the research out across **subagents** (the
Agent tool, `general-purpose`), ~15 NPCs each, run concurrently
(`run_in_background: true`). Key results by **exact NPC title (name)**, never by
id, so you control id mapping locally (one NPC can have many cache ids). Write a
shared instructions file and point each agent at its chunk file + an output file.
Per NPC the agent returns: `name`, `style` (<=200 chars, delivery only),
`race`, `gender`, `race_corrected`/`gender_corrected` flags, `ethnicity_origin`,
optional `accent`/`pace`, and a confidence.

**Origin is the part to get right.** `ethnicity_origin` is where the character is
truly FROM, as a `byEthnicity` key, or `none` if no regional accent fits. The
common case is a local (origin == the found region), but the whole point of the
region-wide pass is catching the **foreigners**: visitors, immigrants, quest
travellers. Tell the agents explicitly NOT to default everyone to the found
region. Watch especially for hubs that gather people from across Gielinor: the
**Grand Exchange specialist clerks** are recruited from different kingdoms (Farid
Morrisane is Kharidian, Relobo Blinyo is Karamjan), barbarians are Fremennik,
desert/eastern traders carry their own accents, and so on.

## 3. Author the edits (the merge)

Merge the result files locally, then write into the two source files. **Never
hand-edit `src/main/resources/npc-voices.json`** (it is generated).

- **Bespoke personality** -> `tools/profiles.json` `byId`: sparse, `name` +
  `style` (delivery/temperament, no quest lore, no emotion words). Set
  `accent`/`pace` only for a real per-character quirk. **Attach the profile to
  every one of the NPC's cache ids** so variants resolve identically.
- **Origin / accent** -> `tools/overrides.json` `ethnicity`, NOT a repeated
  `byId` accent. An override **replaces** the whole table entry, so it must carry
  `race` + `gender` too. Set `ethnicity` to the true `byEthnicity` key to give a
  foreigner their accent; **omit** `ethnicity` to clear a wrong one (foreigner
  from a region with no accent key) so they fall back to the British default.
- **Race / gender corrections** -> `tools/overrides.json` (mis-gendered NPCs,
  talkable monsters with no wiki race, switch-infobox gaps).

Decide an override only by diffing the researched value against what the
generator would produce (`g.bucket_for_race(rawRace)`, `g.normalise_gender`,
ethnicity defaulted from the found region). Add an override only when something
differs; most locals need none.

## 4. Gotchas (learned the hard way)

- **Ethnicity applies only to plain folk.** The resolver
  (`NpcProfileTable.resolveNpc`) layers `byEthnicity` only when race is
  `Human`/`Unknown`. For a Dwarf/Goblin/Gnome/Troll/etc. the ethnicity field is a
  **no-op**, the racial accent always wins. Do not bother setting/clearing
  ethnicity on non-human NPCs; just fix their race.
- **Normalise non-standard wiki races.** Race must be one of Human, Elf, Dwarf,
  Goblin, Gnome, Troll, Undead, Demon, Wizard. The generator buckets most
  substrings ("Imcando dwarf" -> Dwarf, "goblin (race)" -> Goblin), but odd ones
  fall through to Human: map them in the merge (`Dorgeshuun`/`Cave goblin` ->
  Goblin, `Vampyre`/`Ghost` -> Undead, `Imp`/`Nihil`/`Hellhound` -> Demon,
  `Fairy`/`Dryad` -> Elf, `Ork`/`Giant` -> Troll). Truly unmappable races
  (Serpent, Dragonkin, Merfolk, Centaur) get a `byId` **style only**, no override.
- **Skip the non-voiced.** Cats (catspeak meows would get a silly human voice),
  boss pets, and overhead-only critters survive the Talk-to filter sometimes;
  drop them.
- **File formatting is hand-curated.** `profiles.json` `byId` and
  `overrides.json` `npcs` use **one-line entry objects**; other layers are
  multi-line. A blind `json.dump(indent=2)` reformats every existing entry and
  produces a giant noisy diff. **Insert append-only** (text insertion before the
  container's closing brace, one line per entry, comma the previous last line),
  never reorder existing entries.
- **Regenerate matches source order.** After editing the source files, rerun the
  generator so `npc-voices.json` reflects them; the embedded `byId` is verbatim.
- **Multi-region NPCs** (wiki `leagueRegion` listing several, comma/`&`
  separated) carry no single ethnicity and keep the British default. They are out
  of scope for a single-region batch by design, not "missed".

## 5. World rules

British medieval fantasy: commoners common British; royalty/knights/nobles posh
via **style register**, not a new accent; distinctive races and lore creatures
keep their racial accents (handled by the race layer, do not restate them in a
`byId` accent); `ethnicity` = origin, following the real-world cultures the OSRS
locations are based on. Phrase accents positively, never mention America. No
transient comments. Gemini renders British/European accents reliably, foreign
ones inconsistently, do not over-invest where the model cannot deliver.

## 6. Regenerate, verify, ship

```bash
python3 tools/generate_npc_voices.py
./gradlew test spotlessCheck
```

Spot-check resolutions with `run-game-client` + Debug Mode (`diagnose-npc-voice`),
checking a local commoner, a flagged foreigner, a corrected non-human, and a
quirk NPC. Then open the region PR linked to the batch issue. See
`add-npc-profile`, `regenerate-npc-voices`, and `create-issue` for the
surrounding flow.
