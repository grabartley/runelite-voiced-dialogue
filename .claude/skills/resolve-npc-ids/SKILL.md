---
name: resolve-npc-ids
description: Resolve live NPC ids (and names, genders, dialogue flags) for newly released content by decoding the archived OSRS game cache, when the wiki infoboxes aren't filled in yet and the osrs MCP / MOID dumps are stale. Use when adding voice coverage for a fresh update and you need real cache ids for tools/overrides.json but cannot walk the area in-game.
---

# Resolve NPC ids from the game cache

`tools/overrides.json` is keyed by **live NPC id** (`NPCComposition#getId`). For a
brand-new update those ids are hard to get:

- the **OSRS Wiki** only lists an id once an editor fills the `Advanced data -> NPC ID`
  infobox row, which lags launch by hours to days and often never happens for
  minor NPCs (village stubs, Slayer masters with a bespoke infobox);
- the **osrs MCP** `npctypes.txt` dump and the weirdgloop **MOID** `npcsmin.js`
  both lag a release by days (they end well below the new id block);
- **walking the area in-game** may be gated behind a quest/skill you can't meet.

The reliable source is the **OpenRS2 archive** (<https://archive.openrs2.org>),
which snapshots the live cache within hours of every update. This skill decodes
index 2 / group 9 (npc configs) out of it to give `id -> {name, options}` for the
whole game, including the just-released block.

## Usage

`decode_npc_ids.py` (next to this file) is self-contained (Python stdlib only).

```bash
cd .claude/skills/resolve-npc-ids

# find named characters (substring match) on the newest archived cache
python3 decode_npc_ids.py --names "Ffion,Cormac,Mortimer,Mad Angel"

# dump a whole id block, flagging who is a dialogue (Talk-to) NPC
python3 decode_npc_ids.py --range 16294-16337 --ops

# everything at or above an id (handy: new content is always the highest ids)
python3 decode_npc_ids.py --min-id 16294 --ops

# pin an exact cache id instead of auto-selecting the newest
python3 decode_npc_ids.py --cache 2644 --range 16294-16337 --ops
```

`--cache latest` (the default) scans `caches.json`, sorts oldschool caches by
timestamp, and picks the newest one that actually serves the npc config group.
A same-day cache is often a *partial* dump (few groups) but still contains the
changed npc group, so it works. Every run prints the cache id + timestamp to
stderr so you record exactly what you read.

## How to find the new content

New NPCs are always assigned the **highest ids**, contiguous in one block. Find
the block's floor from an id you already know (one filled wiki infobox, or the
previous stale dump's max id), then `--min-id <that>` and read down the list.
`--ops` separates the dialogue NPCs (voice these) from combat monsters, pets,
goats, golems and fishing spots (skip these).

## Trust but verify

- The decoder **self-validates** on every run: it checks three stable ids
  (Tool Leprechaun 0, Hafuba 5254, Commander Zilyana 2205) decode to their known
  names. If Jagex ships a new npc opcode that shifts byte alignment, that check
  prints `ALIGN FAIL` to stderr and you must extend `decode_npc()` (see below).
- **Gender is not in the npc config.** The cache gives you the id + name + menu
  options, not gender. Get gender from the wiki infobox where it exists, and
  otherwise infer from the character and **flag it for confirmation** — names lie
  (Wyrmscraig's `Ffion` is a male NPC despite the name). Never set a gender you
  can't back up without marking it as needing a check.
- Cross-check any id against the wiki infobox when one exists; the two should
  agree exactly (they did for the whole Wyrmscraig cast).

## Extending the opcode table

`decode_npc()` models every opcode that appears **before** the name/options in a
def, which is all it needs. If a future update adds an opcode Jagex writes before
the name, alignment breaks and the self-check fails, printing the unknown opcode
number. Add a branch for it (read the right number of bytes) using the RuneLite
`NpcType`/`NpcLoader` definitions as reference, then re-run until the self-check
passes. A single oddball def that misaligns is caught per-npc and skipped, so it
never kills the run - only a target you actually need matters.

## Related skills

- `add-npc-profile`, `add-race`, `fill-npc-profiles-batch` - consume the ids this
  skill resolves.
- `regenerate-npc-voices` - rebuilds the bundled table after overrides land.
