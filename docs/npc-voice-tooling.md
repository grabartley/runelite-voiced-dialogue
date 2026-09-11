# NPC voice table tooling

Offline tooling that produces the bundled `src/main/resources/npc-voices.json`
lookup table. **None of this runs inside the plugin.** At runtime the plugin only
reads the generated JSON and does in-memory map lookups keyed by NPC id, so there
are no network calls or large downloads when choosing a voice.

## Files

- `tools/generate_npc_voices.py` - the generator. Pulls every NPC's race, gender
  and ethnicity from the Old School RuneScape Wiki, merges the curated
  overrides, embeds the voice profiles, and writes the bundled resource.
- `tools/overrides.json` - hand-curated, **authoritative** `npcId -> {race,
  gender, ethnicity?, lifeStage?}` entries. These always win over the wiki, for pinning the
  rare NPC the wiki gets wrong or does not cover, and for marking named children.
- `tools/profiles.json` - hand-curated **character voice profiles** for the cloud
  (Gemini) backend (accent, style, pace). Embedded verbatim into the output under
  a top-level `profiles` key. See [Character voice profiles](#character-voice-profiles-cloud).

## Data source

The [Old School RuneScape Wiki](https://oldschool.runescape.wiki) is the
authoritative, current source. Talkable NPCs transclude `Template:Infobox NPC`,
which exposes `race`, `gender`, `leagueRegion`, `location` and one or more cache
`id`s; talkable creatures transclude `Template:Infobox Monster`, which carries
none of those, so their race comes from the page's categories. The generator:

1. Enumerates every main-namespace page transcluding either infobox template
   (via the MediaWiki `embeddedin` API).
2. Fetches each page's lead wikitext and categories in batches and parses every
   infobox.
3. Maps each cache id to `{race, gender, ethnicity}` (plus a curated `lifeStage` child
   marker from the overrides), deriving race from the
   page categories when the infobox does not carry it.

Because race and gender come straight from the wiki, townsfolk get the correct
gender (e.g. Cecilia is Female) and newly released NPCs (Varlamore, etc.) are
covered as soon as the wiki documents them. The ids are real cache ids the live
client reports.

The wiki does not always list every cache id an NPC uses (variants, multiloc
versions). To close that gap the generator also cross-references a full
`id -> name` NPC dump against the wiki data **by name**, so a variant id whose
name still resolves to a documented NPC is covered too.

> **Coverage notes.** The live client reports a transformed/multiloc NPC's
> *active* id, which can differ from its base composition id; the runtime resolves
> by the active id first (then the base id) to match the wiki. Combat creatures use
> a separate `Infobox Monster` that carries **no race/gender/ethnicity**, so their
> race is derived from the page's categories (e.g. TzHaar-Mej); `overrides.json`
> covers only the cases the categories get wrong. Anything still unknown (a
> brand-new NPC) is left to the runtime auto-learn fallback.

## Mapping rules

- **Race.** The wiki race text is mapped onto a voice bucket. Buckets are
  voice-categorical, not lore-accurate: ogre/cyclops -> Troll, vampyre -> Undead,
  dragon/TzHaar -> Demon. Gnomes are kept as their own `Gnome` race (so they can
  sound country Irish) even though they share the small/high goblin voice timbre.
  Gorillas are their own `Gorilla` race (deep and booming, matched before monkey)
  so apes are kept off the chattery island-monkey voice; the explicitly demonic
  Monkey Madness 2 gorillas stay `Demon` via overrides. Tortugans (the turtle-like
  folk of the Great Conch) are their own `Tortugan` race, carrying a warm Bajan
  accent everywhere they are found. Icyene (the winged Saradominist beings) are
  their own `Icyene` race with an ethereal, hallowed delivery; a "Half Icyene"
  (Safalaan) is excluded by the rule and pinned `Human` in overrides. The Citizens
  of Arceuus (ascended humans whose souls were rehoused in incorporeal bodies at
  the Dark Altar) are their own `Arceuus` race, matched ahead of the human
  fallback and carrying a cool, faintly echoing delivery wherever they are found;
  the mortals who declined immortality stay `Human`.
  The aranei (the hooded, telepathic servants bound to House Shadum) are their own
  `Aranei` race, carrying a soft, breathy delivery wherever they are found,
  including Sarei, the Mysterious Stranger, wherever she appears. The aranei who
  takes her post at the Theatre of Blood after she is killed is a different
  person, and is pinned separately.
- **Gender.** Taken verbatim (`Male`/`Female`); defaults to `Male` only when the
  wiki has none.
- **Ethnicity.** The wiki `leagueRegion` (where the NPC is found) is the default
  proxy for ethnicity (where they are from) and maps to an ethnicity accent key.
  `Desert` splits into `kharidian` (Middle Eastern) and `menaphite` (Egyptian, for
  the Sophanem/Menaphos cities). An NPC documented across several league regions
  has no single ethnicity, so it keeps the British default. Ethnicity is an
  **origin** signal, not where the NPC is standing, so a Varrock guard exploring
  Karamja still sounds Misthalin; a foreigner is corrected in `overrides.json`.
  A place with no `leagueRegion` of its own (e.g. the Wyrmscraig island ->
  `wyrmscraig`) gets no auto-assignment; its ethnicity is pinned per-NPC in
  `overrides.json`.

## Regenerate the table

From the repo root (needs network access to the wiki):

```bash
python3 tools/generate_npc_voices.py
# or a quick partial run for testing:
python3 tools/generate_npc_voices.py --limit 500
```

For an **overrides- or profiles-only** change (no new wiki coverage needed), use
the offline `--base` mode instead. It re-applies `overrides.json` and re-embeds
`profiles.json` onto the existing table without the live wiki scrape, so the diff
is minimal and deterministic (only the changed ids, the embedded profiles, and
the `_meta` counts) with no wiki drift:

```bash
python3 tools/generate_npc_voices.py --base src/main/resources/npc-voices.json
```

Then build and test:

```bash
./gradlew test spotlessCheck
```

Commit the regenerated `src/main/resources/npc-voices.json` alongside any
overrides or profile changes.

## Fixing a wrong voice

**Do not hand-edit `npc-voices.json`** (it gets overwritten on regeneration).
First, fix it at the source: the wiki itself, if its infobox is wrong. For a
local-only correction, or to pin a talkable monster the wiki splits into
`Infobox Monster`, add the entry to `overrides.json` and regenerate:

```json
{
  "npcs": {
    "2154": { "name": "TzHaar-Mej", "race": "Demon", "gender": "Male" }
  }
}
```

The optional `name` field is documentation only. `ethnicity` is also optional (set a byEthnicity key, or omit to clear a wrong one). The optional
`lifeStage` field marks a named child (`"lifeStage": "child"` is the only value) so it voices
from the youthful cloud voice sub-pool instead of its adult race anchor;
generically named children (Child, Schoolboy, Street urchin, ...) are caught by
the `child` keyword category in `profiles.json` instead and need no override. Find
an NPC's id with the RuneLite developer tools, the wiki, or **Debug Logging** in the
plugin (it logs the id and chosen voice/profile per line).

## Character voice profiles (cloud)

Alongside the `npcId -> {race, gender, ethnicity?, lifeStage?}` table, the bundled resource
carries a `profiles` section that steers **how** the cloud (Gemini) backend
delivers each line: accent, style, and pace, rendered into a Gemini `AUDIO
PROFILE` / `DIRECTOR'S NOTES` block prepended to the spoken text. Chat-head
emotion is layered on top as a separate inline tag, so the two compose.

The source of truth is `tools/profiles.json`; the generator embeds it under the
output's `profiles` key. This is a **British** medieval fantasy world: commoners
speak plain, common British, only royalty, knights, nobles and other high society
use posh Received Pronunciation.

### Layers (all matches combine)

An NPC can be several things at once (a Fremennik human, a ghost pirate), so
**every** matching layer contributes. `style` accumulates across all contributing
layers so the persona blends; `name`, `accent`, and `pace` are single-valued, so
the most specific layer that sets each one wins.

1. `default` - the global British fallback. **Must be complete** (all four of
   `name`, `accent`, `style`, `pace`). Every other layer is sparse.
2. `byRace[race]` - one per race bucket.
3. `byEthnicity[ethnicity]` - an ethnicity accent. Applied **only to the plain folk**
   (Human / unknown race) so distinctive races keep their racial accent wherever
   they are. Every league region has an accent: the far lands follow the
   real-world cultures they are based on (Desert -> Middle Eastern,
   Sophanem/Menaphos -> Egyptian, Karamja -> West African, Fremennik -> Norse,
   Morytania -> Eastern European gothic, Varlamore -> Mediterranean), the central
   kingdoms use distinct English regional accents (Misthalin, Asgarnia West
   Country, Kandarin Liverpudlian, Kourend, Wilderness), and Tirannwn is Welsh.
4. `byCategory[]` - an ordered list; **every** entry whose `keywords` word-match
   the display name contributes. This expresses categories the race buckets cannot
   (leprechaun -> Irish, vampyre -> Dracula-esque, gnome, imp, ghost, pirate,
   royalty, knight, noble, wizard, ...). Matching is case-insensitive and bounded
   on word edges, so `imp` matches "Imp" but not "important". A category may also
   carry `"lifeStage": "child"`: besides layering its style, it marks every matching NPC
   as a child so the voice resolver picks from the youthful voice sub-pool (the
   `child` category keys on child/schoolboy/schoolgirl/urchin).
5. `byId[npcId]` - per-NPC **bespoke** overrides keyed by the live NPC id. Sparse:
   carry only what is unique to the character (usually `name` + `style`); its
   style is added on top of the blend, and accent and pace inherit unless it sets
   them. This is the highest-precedence layer, so it can pin any character's
   delivery regardless of ethnicity.

Player lines use the `player` layer over the default; the three player fields in
the plugin config (accent/style/pace) override it at runtime when non-blank.

Narration boxes use the `narrator` layer over the default, with no config fields
over it, so the narrator sounds the same in every session and its lines keep a
stable cache key.
