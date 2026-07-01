---
name: regenerate-npc-voices
description: Regenerate the bundled NPC voice + profile table (src/main/resources/npc-voices.json) from the OSRS Wiki. Use when refreshing NPC coverage (newly released NPCs), after editing tools/overrides.json or tools/profiles.json, or after changing the generator's mapping rules.
---

# Regenerate the NPC voice table

`src/main/resources/npc-voices.json` is **generated**, never hand-edited. It holds
`_meta`, the `profiles` section (embedded from `tools/profiles.json`), and the
`npcs` table (`id -> {race, gender, ethnicity?}`).

## Command

```bash
python3 tools/generate_npc_voices.py        # needs network access to the wiki
./gradlew test spotlessCheck
```

Quick partial run for testing: `--limit 500`. Commit the regenerated
`npc-voices.json` alongside any `overrides.json` / `profiles.json` edits.

For an overrides/profiles-only change, prefer `--base src/main/resources/npc-voices.json`
(offline, no wiki fetch) so the diff is exactly your intended edits with **no
wiki-freshness drift** (newly released NPCs, unrelated reclassifications). A full
online run is only for a deliberate table/coverage refresh.

After regenerating, update the NPC counts in `README.md` to match the new
table: the total entry count (`_meta.count`) and the bespoke-personality count
(`profiles.byId` length). Read both from the regenerated `npc-voices.json` and
edit the README figures in the same commit so they never drift.

## Mandatory: verify every changed NPC before committing

Any NPC whose entry changes in the regenerated `npc-voices.json` — via override
edits, wiki drift, a summary refresh, or a logic change — must be **100% hand
verified against the OSRS Wiki** before commit. The burden of verification is on
you; do not rubber-stamp the diff.

1. Diff the new `npcs` map against the committed one (`git show HEAD:...`) to list
   every changed id, and dedupe by character to get the real work list.
2. For each changed character, confirm `race`/`gender`/`ethnicity` against the wiki
   using the process in `find-npc-true-origin` (true origin, not just found-region).
   Fix anything wrong in `overrides.json` and regenerate.
3. **Present a change table to the developer** before committing, one row per
   change, so they can see exactly what was done:

   | id(s) | name | race (old → new) | ethnicity (old → new) | why |
   |-------|------|------------------|-----------------------|-----|

   Include drift-introduced changes too; if a full online run pulled in new NPCs
   or reclassifications you did not intend, either verify them or switch to
   `--base` to exclude them.

## What the generator does

1. Enumerates every main-namespace page transcluding `Template:Infobox NPC` **or** `Template:Infobox Monster`.
2. Per page: race from the infobox `race` field, or, for Monster pages that lack it, from the page **categories** (e.g. `Category:Trolls` -> Troll); gender paired **per version** (switch infoboxes list a gender per id group); ethnicity from `leagueRegion` (Desert splits into `kharidian`/`menaphite` via location or `Category:Menaphites`/`Sophanem`).
3. Cross-references a full `id -> name` dump (`--summary`) by name to cover variant ids the wiki pages don't list.
4. Applies `tools/overrides.json` last (authoritative: race/gender/ethnicity).
5. Embeds `tools/profiles.json` under `profiles`.

## Profile architecture (for reference)

- Layers combine: `default -> byRace -> byEthnicity (plain folk only) -> byCategory (every keyword match) -> byId`. `style` accumulates; `name`/`accent`/`pace` take the most specific layer.
- `tools/profiles.json` = delivery prose (accent/style/pace). `tools/overrides.json` = classification (race/gender/ethnicity), which also feeds the offline Local backend's voice.
- British world: commoners common British, royalty/knights/nobles posh via style register; distinctive races and lore creatures keep their accents; ethnicity = origin (corrected in overrides for foreigners); never by negation; no transient comments.

## Notes / known limits

- The full run scrapes a few thousand pages (minutes). Race/gender/ethnicity come from the wiki, so accuracy tracks the wiki.
- Coverage gaps that fall to overrides or the runtime wiki fallback: variant ids in neither the wiki id-lists nor the id dump; talkable monsters whose category gives no race; disambiguation-named NPCs ("Citizen", "Priest") with no page data.
- To find which NPCs still **need** a bespoke profile, the authoritative talkable signal is the existence of a `Transcript:<name>` page (wiki namespace 120), not the infobox `Talk-to` options line, which is blank on many genuine talkers (monster-infobox bosses, the Dorgesh-Kaan cave goblins). See `fill-npc-profiles-batch` section 1 for the batch query.
- See `add-npc-profile` to make a change and `diagnose-npc-voice` to investigate one.
