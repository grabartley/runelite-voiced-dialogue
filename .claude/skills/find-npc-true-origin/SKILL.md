---
name: find-npc-true-origin
description: Research an NPC's true origin ethnicity from the OSRS Wiki and pin it explicitly in tools/overrides.json. Use when an NPC gains or has a wrong ethnicity accent, when auditing overrides after a merge/logic change, or any time you need to decide an NPC's real regional accent rather than trusting the wiki leagueRegion proxy.
---

# Find an NPC's true origin ethnicity

The generator defaults an NPC's `ethnicity` (accent) from the wiki `leagueRegion`,
i.e. **where they are found**. That is only a proxy for **where they are from**.
A pirate found in the Morytania region is not gothic Eastern-European; an
archaeologist working in the desert is not Kharidian. This skill is the process
for determining an NPC's *true* origin and pinning it in `tools/overrides.json`.

**Golden rule: any NPC you spend time researching gets its origin set explicitly
in `overrides.json`, even when it matches the wiki region.** Never leave a
researched NPC to fall back to the default. Fallback (`ethnicity: null`) is a
last resort for characters who genuinely have no regional accent, not a dumping
ground for "didn't bother deciding". See `add-npc-profile` for the file mechanics
and `regenerate-npc-voices` for the generation/commit flow.

## The accent each ethnicity maps to (from tools/profiles.json byEthnicity)

Getting the accent right means knowing what each key *sounds* like. British-family
keys are low-contrast; the foreign ones are jarring if mis-assigned:

| key | accent |
|-----|--------|
| `misthalin` | Common British (plain, standard, ≈ the default) |
| `asgarnia` | West Country British burr (also the classic **pirate** "arr" sound) |
| `kandarin` | Liverpudlian / Scouse |
| `kourend` | Broad rustic British |
| `wilderness` | Rough, harsh British (outlaws) |
| `tirannwn` | Welsh |
| `fremennik` | Norse / Viking |
| `morytania` | Eastern European gothic |
| `kharidian` | Middle Eastern / Arabic |
| `menaphite` | Egyptian |
| `karamja` | West African / Nigerian |
| `varlamore` | Italian |
| `easternlands` | Japanese |

`null` (no ethnicity) → the `default` profile: "Common British, plain earthy
peasant", essentially the same sound as `misthalin`.

## Process, per NPC

1. **Pull the wiki background, not just the infobox region.** Read the page lead /
   background prose (via the osrs MCP `osrs_wiki_parse_page`, or fetch the section-0
   wikitext). The opening sentences almost always state the real origin: "*a pirate
   from Mos Le'Harmless*", "*a Saradominist priest*", "*a traveller brought to
   Kourend*", "*a knight of Camelot*". Note `leagueRegion` and `location` too.
2. **Decide origin from lore, not location:**
   - **Native/resident of a region** (lives/works there, no foreign indicator) →
     that region's ethnicity. Presume permanent residents are locals.
   - **Explicit foreigner / traveller** ("brought to…", "came to…", "travelled
     from…") → their *stated home* region if it maps to a key, else `null`.
   - **Generic British / English character** with no specific home (Arthurian
     knights, Robin Hood, wandering adventurers, quest cultists) → `null`
     (default British). Posh characters get their poshness from the knight/noble
     **role style layer**, not an accent, so `null` is correct for them.
   - **Pirates / no home kingdom** (Mos Le'Harmless, Braindeath, Lady Zay crews,
     random-event pirates) → `asgarnia` (West Country = the stock pirate accent).
   - **Non-human mislabelled `race: Human`** (penguins, frogs, lizardmen, aviansie,
     Mahjarrat, araxytes, leprechauns, merfolk, fairies) → **fix the race** to the
     right bucket (aviansie/Mahjarrat/lizardman/araxyte → `Demon`; small comic
     creatures → `Goblin`; leprechaun → `Gnome` for the Irish-flavoured voice) and
     set `ethnicity: null` (the racial accent wins for non-Human anyway). Truly
     unmappable species (merfolk, centaur, sentient object) keep `Human` + `null`.
3. **Pin it in `overrides.json`.** Set `"ethnicity": "<region>"` or
   `"ethnicity": null` on every id for that character. Add `race`/`gender` if you
   corrected them. Keep one-line objects so the diff stays small.
4. **Verify against the wiki before committing** — see the mandatory checklist in
   `regenerate-npc-voices`. Every changed NPC in the final `npc-voices.json` must
   be hand-confirmed against wiki data, and a change table presented.

## Bulk audit (e.g. after a generator/merge change)

1. Regenerate to a scratch file and diff the `npcs` map against the committed one
   to get the set of NPCs whose `race`/`gender`/`ethnicity` changed.
2. Dedupe by character name (variant ids share a character) to get the real work
   list, then fetch each character's background in batches (section-0 wikitext for
   `leagueRegion` + `location` + first prose sentences is compact and authoritative).
3. Classify each with the rules above, write the decisions into `overrides.json`,
   and regenerate with `--base <committed npc-voices.json>` so the diff is exactly
   your intended changes with **no wiki-freshness drift** (new NPCs, unrelated
   reclassifications). Drift belongs in a dedicated table-refresh, not an
   origin-audit PR.
4. Present the change table (see `regenerate-npc-voices`).

## Sanity references

- Karim (2877) / Ellis (3231) — Al-Kharid natives, `kharidian`.
- Ak-Haranu (2989) — `easternlands` (explicit non-region accent).
- Pirate Pete / Bill Teach — Mos Le'Harmless pirates → `asgarnia`.
- Sir Percival — Camelot knight → `null` (posh via role, not an accent).
- Kree'arra — aviansie → `race: Demon`, `ethnicity: null`.
