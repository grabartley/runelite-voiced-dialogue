# NPC voice table tooling

Offline tooling that produces the bundled `src/main/resources/npc-voices.json`
lookup table and the `src/main/resources/voice-regions.json` voice pools. **None of this runs inside the plugin.** At runtime the plugin only
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
  (Gemini) backend (name, accent, accentDetail, style, replaceStyle, pace, pitch, voiceRegion).
  Embedded verbatim into the output under
  a top-level `profiles` key. See [Character voice profiles](#character-voice-profiles-cloud).
- `tools/voice-regions.json` - hand-curated **voice regions**: each names one Gemini Extended
  Voice Library accent, the player-accent keywords that select it, any voices excluded because they
  read as the wrong gender, and optionally `onlyVoices` to narrow a gender to named voices (the
  trolls' deep southern English pool).
- `tools/voice-library.json` - a committed snapshot of the Extended Voice Library, refreshed with
  `GEMINI_API_KEY=... python3 tools/fetch_voice_library.py`. The generator builds each region's
  male and female pools from it into `src/main/resources/voice-regions.json`, so a voice only
  changes when the snapshot or the regions change and are shipped.

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

A field value is read to the end of its line and cut at the first `|`, or at a
link or template close that has nothing open, that sits outside a link or a
template, so a piped link such as `[[Dwarf (race)|Dwarves]]` reaches the bucket
rules whole and a single-line infobox still stops at its next parameter. Race buckets on the link target first and its
display text second, which keeps `[[Dwarf (race)|Dwarves]]` a Dwarf while letting
a target the rules cannot read, such as `[[Dog_(disambiguation)|Dog]]`, resolve
from the display text instead of falling to the `Human` default. The auto-learn
lookup reads a live wiki page by the same rules, so a learned NPC buckets exactly
as a bundled one does.

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

The race rules, the category race rules and the league region to ethnicity map live in
`src/main/resources/wiki-mapping.json`. The generator reads it and so does the plugin's
runtime auto-learn lookup, so a race keyword means the same thing in the bundled table and in a
learned entry. Editing a rule there changes both, and the table needs regenerating to pick it up.

Rule order is load-bearing: the list is scanned top to bottom and the first match wins. Arceuus sits
ahead of the human fallback, dog behind undead and demon, and gorilla ahead of monkey, each for the
reason written up below. Re-sorting the list would change what thousands of NPCs sound like.

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
  Dogs are their own `Dog` race, matched after the undead and demon rules so a
  risen hound keeps its grave, and voiced as vocalisation rather than speech: the
  barks, whines and growls the game writes into their dialogue boxes are made as
  sounds rather than read out as words. Dog-like monsters ride along with them
  (shadow hounds, terror dogs, jackals, wolves), since a growl is a growl and the
  race buckets pick a voice rather than a species. A hellhound is a `Demon`, and a
  skeletal, revenant or reanimated one is `Undead`, because the grave outranks the
  abyss in both scans.
  Crabs and penguins are their own `Crab` and `Penguin` races, matched in the same
  slot behind the undead and demon rules. The crabs carry a bright seaside lilt and
  the penguins the clipped speech of the Motherland, so the Crab Quest cast and the
  Cold War penguins keep their own voice wherever they turn up. The penguins in
  costume are pinned to `Penguin` rather than to whatever they are dressed as.
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

Then build and test, including the generator's own tests, which CI also runs:

```bash
./gradlew test spotlessCheck
python3 -m unittest discover -s tools -p "test_*.py"
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
from its region's youngest native voices (or the prebuilt child pool when its accent has no
region) and takes the `child` profile layer, with its style and child pitch, just as a
keyword-named child does;
generically named children (Child, Schoolboy, Street urchin, ...) are caught by
the `child` keyword category in `profiles.json` instead and need no override. Find
an NPC's id with the RuneLite developer tools, the wiki, or **Debug Logging** in the
plugin (it logs the id and chosen voice/profile per line).

## Character voice profiles (cloud)

Alongside the `npcId -> {race, gender, ethnicity?, lifeStage?}` table, the bundled resource
carries a `profiles` section that steers **how** the cloud (Gemini 3.8) backend
delivers each line: accent, style, and pace. `GeminiSpeechStyle` renders them as a full
character profile in one style string sent in `speech_metadata.style`. It opens by naming the
spoken language and the speaker's gender ("A woman's voice.", "A young boy's voice."; none for
the narrator, whose storyteller voice is fixed), then any `pitch`, and ends with the line's chat-head emotion (and, on Google AI
Studio, a speed direction when **Speaking Pace** is not 100). The language is the **Spoken
Language** setting when the line is translated, and English when it is not:

```
Speaking English. Audio profile: Benny, a character in a medieval fantasy world. Accent:
Strong London English accent, British English pronunciation. Common British English, the
plain, standard speech of Gielinor's central kingdom of Misthalin. Style: An ordinary citizen of
Gielinor. Down-to-earth, sincere, and approachable. Eager street vendor, loud and
pitchy, hawking his newspapers to passers-by. Pace: Steady and conversational.
Sounding happy.
```

The text the model receives is the spoken line alone, since Gemini 3.8 speaks its
input verbatim. By ear, the full profile keeps NPCs that share a voice sounding like
different people, where a short style string flattens them together.

- `accent` is a strong, explicit accent phrase that names the pronunciation:
  "Strong London English accent, British English pronunciation", "Strong Glasgow
  Scottish accent, Scottish English pronunciation", "Strong Italian accent,
  Italian-accented English pronunciation". Gemini 3.8 treats a soft phrase
  ("Plain southern English accent") as optional and falls back to a generic
  default accent, so every accent leads with "Strong" (or "Very strong," where by ear the accent
  needs more push, as for the Tortugans) and names its pronunciation.
  A delivery quirk (slurred, whispered, hissing) is never an accent: it goes in
  `style`, so the character keeps the accent of its race or region.
- `accentDetail` is optional and sits next to an `accent`. It carries the colour of the accent in
  prose ("A fantasy Norse accent, a Scandinavian-flavoured English in the manner of a Viking from
  the old sagas.") and is rendered right after the accent. Like `voiceRegion`, it always comes
  from the layer that set the winning accent. The strong accent phrase keeps 3.8 on the right
  accent, and the detail keeps the character medieval rather than modern.
- `style` and `pace` are descriptive delivery prose: persona, tone, timbre, volume,
  rhythm.
- `replaceStyle: true` on a layer drops the styles of the less specific layers before its own, so
  a region can replace the generic human style: the Wilderness trades "Down-to-earth, sincere, and
  approachable" for its own outlaw style. More specific layers still add to it.
- `name` is sent as the profile's name, so it is part of the cache key.
- `pitch` is optional and leads the profile, right after the spoken language and the speaker's
  gender, ahead of the profile name ("Very high-pitched,
  squeaky, thin little voice, far above a normal adult voice"). Native library voices ignore
  pitch described later in the style, so it leads. The most specific layer that sets it wins. The
  gender before it matters: by ear a bare "very deep" turns a female troll or dwarf into a man, a
  bare "very high" turns a boy into a girl, and a few library voices drift toward the other
  gender unless it is named.
- `voiceRegion` sits next to an `accent` whose accent has native speakers in the voice library
  (`"voiceRegion": "SCOTTISH"`), and the NPC is voiced from that region's pool. The region always
  comes from the same layer as the winning accent, so an accent with no region (Welsh, Nigerian)
  clears any region a less specific layer set. See [voice-casting.md](voice-casting.md).
- No meta-instructions ("word for word", "do not change voice") and no square- or angle-bracket
  tags.

### Repetition is deliberate

The composed style repeats itself: a troll's depth is in its `pitch` and again in its race style,
an `accentDetail` can restate its accent, and every human carries the generic "ordinary citizen"
line under their own description. **Leave it.** Gemini 3.8 treats each mention of a trait as a
push toward it, so repetition works as emphasis. The wording is tuned by ear against `main`. By ear, a
version that says each fact once sounds generic: trolls lose depth, characters flatten toward the
same plain person, and misgendered and modern-sounding voices return.

The cost is small. The style is billed as text input, about $0.0001 a line for a long style
against about $0.001 for the audio, and the README latency figures already include full styles.

So a profile edit follows these rules:

- Keep `main`'s wording and every depth or pitch cue, even where it reads as redundant.
- Make one wording change at a time, render the `compare-voices` cases it touches against the
  current branch, and keep it only if it passes by ear.
- Contradiction is a different problem from repetition: two layers that disagree (a fairy's
  "quick and fluttering" under the Fairy Godfather's "unhurried menace") are fixed on that one
  NPC, and only after hearing the change.

The generator enforces the mechanical part: `validate_profiles` rejects a tag
bracket, a prompt-block marker, or "word for word" in any field, an `accent` that
does not start with "Strong" or "Very strong," and end with its pronunciation, an `accent` over 100
characters, a `voiceRegion` that is not in `tools/voice-regions.json`, a `voiceRegion` or `accentDetail` on a
layer with no `accent`, and a `replaceStyle` with no `style` beside it.

The source of truth is `tools/profiles.json`; the generator embeds it under the
output's `profiles` key. This is a **British** medieval fantasy world: commoners
speak plain, common British, only royalty, knights, nobles and other high society
use posh Received Pronunciation.

### Layers (all matches combine)

An NPC can be several things at once (a Fremennik human, a ghost pirate), so
**every** matching layer contributes. `style` accumulates across all contributing
layers so the persona blends, unless a layer sets `replaceStyle`; `name`, `accent`, `pace`, and
`pitch` are single-valued, so the most specific layer that sets each one wins, and `voiceRegion`
and `accentDetail` always follow the layer that set the winning `accent`. A child marked by the table's `lifeStage` rather than by a child
keyword takes the `child` category layer after the keyword categories and before `byId`.

1. `default` - the global British fallback. **Must be complete** (all four of
   `name`, `accent`, `style`, `pace`). Every other layer is sparse.
2. `byRace[race]` - one per race bucket.
3. `byEthnicity[ethnicity]` - an ethnicity accent. Applied **only to the plain folk**
   (Human / unknown race) so distinctive races keep their racial accent wherever
   they are. Every league region has an accent: the far lands follow the
   real-world cultures they are based on (Desert -> Middle Eastern,
   Sophanem/Menaphos -> Egyptian, Karamja -> West African, Fremennik -> Norse,
   Morytania -> Eastern European, Varlamore -> Italian, Wyrmscraig -> rural Irish),
   the central kingdoms use distinct English regional accents (Misthalin London,
   Asgarnia West Country, Kandarin Scouse, Kourend Northern, the Wilderness rough and harsh on Newcastle voices),
   and Tirannwn is Welsh.
4. `byCategory[]` - an ordered list; **every** entry whose `keywords` word-match
   the display name contributes. This expresses categories the race buckets cannot
   (leprechaun -> Irish, vampyre -> Dracula-esque, gnome, imp, ghost, pirate,
   royalty, knight, noble, wizard, ...). Matching is case-insensitive and bounded
   on word edges, so `imp` matches "Imp" but not "important". A category may also
   carry `"lifeStage": "child"`: besides layering its style, it marks every matching NPC
   as a child so the voice resolver picks from its region's youngest native voices, or
   the prebuilt child pool when it has no region (the `child` category keys on
   child/schoolboy/schoolgirl/urchin).
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
