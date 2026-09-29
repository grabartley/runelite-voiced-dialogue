---
name: review-area-origins
description: Build a visual review sheet of every voiced NPC in one area (a region, island or city), showing each one's in-game model, location, examine text and how it resolves today, with an origin selector per human, so the developer picks the right origin for each and the picks land in tools/overrides.json. Use when an area's NPCs sound like the wrong nationality, when a region's visitors (pirates, sailors, traders, tourists) wrongly take the local accent, or when asked to review or audit an area's accents.
---

# Review an area's NPC origins

The wiki's league region records where an NPC stands, not where it comes from, so everyone found
in an area defaults to that area's accent, visitors included. This skill puts every voiced NPC in
the area in front of the developer, on one page they can use from a phone, and turns their picks
into overrides. The developer decides every origin; the sheet only gives them what they need to see.

## 1. Choose the area

- **Origin key**: the `byEthnicity` key in `tools/profiles.json` for the area, for example
  `karamja`.
- **Cache symbol pattern**: a case-insensitive regular expression over the game cache names of the
  area's places and people, for example
  `karamja|brimhaven|shilo|musa|tai_?bwo|cairn|kharazi|ship_?yard`. It catches NPCs standing in the
  area whose origin is already something else. Grep the osrs MCP `npctypes.txt` dump (see
  `resolve-npc-ids`) for the area's place names to build it.
- **Exclusions**: names the pattern catches by accident (Shilop in Varrock matches `shilo`).

The sheet lists every NPC with the area's origin plus every NPC whose cache name matches, keeping
only those with a bespoke `byId` profile, which is the set that talks.

## 2. Build the sheet

```bash
.claude/skills/review-area-origins/run.sh <Area> <origin key> '<cache symbol pattern>' <out-dir> [excluded names...]
```

It resolves every NPC through `VoiceManager` with the compare-voices harness in resolve-only mode,
so the accent, voice and region shown are exactly what the client picks, and no audio is rendered
and nothing is billed. It then looks each id up on the wiki (`Special:Lookup`) for its model picture,
location and examine text, and writes `<out-dir>/site/origins.html`. Put `<out-dir>` in scratch,
never in the repo. Use a Java 17 shell.

Each card shows the model picture per version, ids, race, gender, age, location, examine text,
current origin, accent, voice and region, style and a wiki link. Every human gets an **Origin**
selector with the current origin preselected, including "None: British default". Non-humans get
no selector, because their racial accent always wins over origin; list any that look wrong in the
reply instead. Changed cards turn yellow, **Show changed only** filters to them, picks survive a
reload, and **Copy results** copies one line per change:

```text
Zembo [13655]: karamja -> kandarin
```

On a phone over plain http the clipboard is blocked, so it opens a box to copy from instead.

## 3. Host it and wait

Host `<out-dir>/site` on the home network exactly as in the `compare-voices` skill ("Host the sheet
on the home network"), give the developer `http://<ip>:8765/origins.html`, and wait for their pasted
results. Say how the list was gathered and that an NPC standing in the area under an unrelated cache
name may be missing, so they can name any they spot.

**Mandatory: take it down.** When the results arrive, stop the server and prove nothing is
listening, as the compare-voices skill requires. No process may be left running.

## 4. Apply the picks

Save the pasted results to a scratch file, then:

```bash
python3 .claude/skills/review-area-origins/area.py apply --picks <picks file> --out <out-dir>
python3 tools/generate_npc_voices.py --base src/main/resources/npc-voices.json
```

`apply` patches `ethnicity` on an id's existing one-line override, or adds a new one-line entry
named after the id's wiki page, and rejects an origin that is not a `byEthnicity` key. `none` pins
`ethnicity: null`, which clears the wiki's guess and gives the British default. When the developer
gives a pick in words instead (for example "the man on the left is Misthalin"), map it to the id
from the card, whose pictures are in id order, and add a line for it.

Then prove the regenerated table differs from `main` in exactly the picked ids and only in their
origin, and show that table of ids, names, from and to in the issue and the PR.

## 5. Ship it

Track the change with an issue and a pull request through the `build` flow. Before handing off,
render the changed characters with a focused `compare-voices` run: one case per character, a real
transcript line each, and a `why` naming who they are and the origin change, then host that sheet
the same way and take it down when the developer is done. Characters with no transcript line get no
case; list them in the reply.
