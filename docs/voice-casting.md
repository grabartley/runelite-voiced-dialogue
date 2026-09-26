# Voice casting

Which Gemini voice each speaker gets, and why that one.
[architecture.md](architecture.md) owns how a speaker is resolved into a voice spec; this owns
which voice that spec lands on.

## Native accent voices come first

Gemini's 30 prebuilt voices are all tagged General American in Google's own voice list, so on
their own they can only put an accent on. Gemini 3.8 renders that as an American speaker doing an
accent. Google's Extended Voice Library holds about two thousand more voices, many of them native
speakers tagged by accent: 80 Dublin, 49 Winchester (southern English), 16 each for Glasgow,
Manchester and Newcastle, 14 Bristol, 6 Liverpool, and native Italian, Egyptian Arabic, Polish,
Japanese, Australian and New Zealand speakers, each of whom reads English text in English.

So an NPC whose accent has native speakers is voiced from them. Each profile layer that sets an
`accent` can name a `voiceRegion` next to it (see
[npc-voice-tooling.md](npc-voice-tooling.md)), and the region always comes from the same layer as
the winning accent, so the two can never disagree. `GeminiVoiceMap` then resolves a speaker in
this order:

1. The narrator takes its fixed voice.
2. The player takes a native voice from the first region whose keyword their typed accent names,
   or the player pool when it names none.
3. A child with a voice region takes that region's child pool: the three youngest native voices
   of its gender. A child with no region takes the prebuilt child pool.
4. An NPC with a voice region takes a voice from that region's pool for its gender.
5. Anything else, including a region with no voices for the NPC's gender, takes its race pool.

| Region | Library accent | Male | Female | Voices |
|---|---|---|---|---|
| `SOUTHERN_ENGLISH` | Winchester | 14 | 34 | commoners, Misthalin, Received Pronunciation, Cockney, most British creatures |
| `WEST_COUNTRY` | Bristol | 10 | 3 | Asgarnia, pirates, crabs |
| `SCOUSE` | Liverpool | 4 | 2 | Kandarin |
| `MANCUNIAN` | Manchester | 5 | 11 | Kourend, Yorkshire barbarians, Northern bespoke NPCs |
| `GEORDIE` | Newcastle | 5 | 11 | the Wilderness |
| `SCOTTISH` | Glasgow | 9 | 7 | dwarves |
| `IRISH` | Dublin | 37 | 39 | gnomes, leprechauns, Wyrmscraig, Irish bespoke NPCs |
| `ITALIAN` | Italian | 25 | 27 | Varlamore |
| `EGYPTIAN_ARABIC` | Egyptian Arabic | 46 | 44 | the Kharidian desert, Menaphos and Sophanem |
| `POLISH` | Polish | 31 | 31 | Morytania, vampyres, Romani bespoke NPCs |
| `JAPANESE` | Tokyo Japanese | 31 | 39 | the Eastern Lands |
| `AUSTRALIAN`, `NEW_ZEALAND` | Sydney, Auckland | | | bespoke NPCs |

Accents with no native speakers in the library (Welsh, Norse, Nigerian, Bajan and Caribbean, the
Russian penguins, the West Midlands ogres) keep their race pool and carry the accent through the
profile's style alone. The library has no countryside Irish voices, so gnomes and leprechauns take
Dublin voices and the style's "rural Irish" accent pulls them toward the country. Pitch is not
filtered, since the library's male voices are overwhelmingly low and a pitch filter would empty
most pools. A creature's depth or squeak rides in the profile's `pitch` field instead, which opens
the style string ("Very high-pitched, squeaky, thin little voice, far above a normal adult
voice."): by ear, a native voice ignores pitch described later in the style but follows it when it
leads. Goblins, gnomes, monkeys, crabs, imps and fairies are high; trolls, gorillas, demons,
dragons, TzHaar, ogres and dwarves are low.

The pools are bundled in `src/main/resources/voice-regions.json`, built from a committed snapshot
of the library, so a voice never changes because Google's list changed; it changes only when the
pools are regenerated and shipped.

## The catalog adjectives are not the casting

Gemini exposes 30 prebuilt voices identified by a name and a one-word vibe adjective: Charon is
"Informative", Algenib is "Gravelly", Puck is "Upbeat". The API carries no gender metadata and no
age metadata, and the adjectives describe delivery rather than timbre.

Every pool below was therefore confirmed by ear from a generated sample pack, rendered through the
plugin's own prompt so the sample matches what a player hears. Where the adjective and the ear
disagreed, the ear won. Sadachbia is "Lively" and Fenrir is "Excitable", and both read as
adults, so neither is a child voice. Laomedeia reads young but drifts off the directed British
accent, so it is a goblin, a crab, and a penguin rather than a child.

A candidate is auditioned by rendering it to audio through the plugin's own prompt and listening
to it. A voice does not enter a pool on the strength of its catalog entry.

## The invariants

- **Gender is structural.** A male spec resolves to a voice from a male sub-pool and a female spec
  to one from a female sub-pool. The 14 male and 13 female voices in use are disjoint sets, so no
  race maps two genders onto the same voice.
- **Each NPC keeps one voice.** A per-NPC seed spreads same-race, same-gender NPCs across their
  pool, and the same NPC lands on the same voice on every line and in every session. The seed is
  the NPC's base composition id, which a transforming NPC keeps when its active id changes, so a
  quest character does not change voice mid-quest. Every pool picks by rendezvous hashing, so
  adding or removing a voice moves only the NPCs on that voice. Race pools hold two voices, so
  their spread is variety rather than a guarantee that any two NPCs differ. A spec carrying no
  seed anchors to index 0 and never takes a region voice.
- **Every spec resolves.** An unknown gender is voiced as male. Four further fallbacks exist and
  none is reachable today, so they are defence in depth rather than live behaviour: a null spec
  and an empty adult pool both resolve to Charon, an empty child pool to Puck, both regardless of
  the spec's gender; and an unmapped race goes to the player pool, though the resolver rewrites an
  unknown race to human before the map is consulted.

## The pools

Voices are shared heavily: 22 of the 30 appear in more than one race pool, and two pairs of races
draw identical pools. The table is the casting, and rows that share a pool share a line rather than
being described twice.

| Race | Male | Female |
|---|---|---|
| Human | Charon, Iapetus | Despina, Erinome |
| Elf and Citizen of Arceuus | Iapetus, Rasalgethi | Vindemiatrix, Erinome |
| Dwarf | Algenib, Alnilam | Gacrux, Kore |
| Goblin | Puck, Zubenelgenubi | Leda, Laomedeia |
| Monkey | Fenrir, Sadachbia | Zephyr, Pulcherrima |
| Gorilla and Troll | Algenib, Orus | Gacrux, Kore |
| Undead | Enceladus, Schedar | Achernar, Sulafat |
| Demon | Algenib, Rasalgethi | Gacrux, Despina |
| Wizard | Sadaltager, Charon | Sulafat, Vindemiatrix |
| Tortugan | Achird, Iapetus | Sulafat, Vindemiatrix |
| Icyene | Alnilam, Schedar | Kore, Despina |
| Aranei | Enceladus, Iapetus | Achernar, Erinome |
| Dog | Fenrir, Orus | Pulcherrima, Gacrux |
| Crab | Zubenelgenubi, Sadachbia | Pulcherrima, Laomedeia |
| Penguin | Puck, Zubenelgenubi | Zephyr, Laomedeia |

The player, children, and the narrator resolve outside the race table. A child with a voice region
and a player whose accent names one take native voices instead (see above):

| Speaker | Male | Female |
|---|---|---|
| Player | Achird, Iapetus | Aoede, Autonoe |
| Child with no voice region | Puck | Leda, Zephyr |
| Narrator | `en-gb-storyteller-2` | `en-gb-storyteller-2` |

`NpcRace` is the key, and several in-game species bucket into one of these before the map is
consulted: gnomes are voiced from the goblin pool, giants and cyclopes from the troll pool, and
dragons and TzHaar from the demon pool. [npc-voice-tooling.md](npc-voice-tooling.md) owns that
bucketing.

## Depth is the organising axis

Depth comes from the catalog's character adjectives, confirmed by ear. The male voices below are
the spine of the axis rather than the full roster, since they are where the adjectives divide most
cleanly: gravelly (Algenib), firm (Alnilam, Orus), even (Schedar) and breathy (Enceladus) are the
deep end; upbeat (Puck) and casual (Zubenelgenubi) are the bright end; informative (Charon,
Rasalgethi, Sadaltager) sits between them, reading measured rather than low and carrying weight
through delivery rather than pitch, which is why Charon can anchor the plain human pool and still
sit in the wizard one. The female pools follow the same ordering by ear.

Big, imposing races take the deep end so they sound large rather than high-pitched. Small ones
stay bright, so a scuttling crab never reads as something standing over you. The undead male
anchor is the breathy voice, which reads hollow rather than merely low.

## Where a pool is borrowed

Most races have a pool assembled for them. These are cast by reference to a pool that already
exists instead, and the reference is itself the casting decision:

- **Tortugans** take the player male pool unchanged and the wizard female pool unchanged:
  friendly and clear in one, warm and gentle in the other, which is the relaxed mid-depth that
  suits warm island folk.
- **Citizens of Arceuus** take the elf pool unchanged, and the elf pool is itself cast off the
  human one: it keeps Iapetus and Erinome, drops the human anchors Charon and Despina, and adds
  Rasalgethi and Vindemiatrix. The catalog groups Charon and Rasalgethi together, so this split
  is one the ear made and the adjectives do not: the elf pair reads more refined, and carries an
  incorporeal delivery better.
- **Aranei** keep one undead voice per gender, Enceladus and Achernar, and pair each with a clear
  one. Enceladus is the breathy voice and Achernar the soft one, which is what carries their
  hushed, telepathic delivery; the clear half keeps them sounding like a living species.
- **Dogs** anchor on the monkey pool's excitable, bright voices and pair each with one from the
  deep end, so a bark lands as a sound with an animal behind it rather than a word read aloud.
- **Crabs** take one goblin voice and one monkey voice in each gender, which keeps them small and
  quick without making them sound like goblins outright.
- **Penguins** take the goblin male pool unchanged, where the upbeat anchor carries the waddling
  comedy and the casual one the flat spy deadpan. The female pool pairs a monkey voice with a
  goblin one and is bright rather than deadpan.

## Children

Life stage is a third resolution axis alongside race and gender, and
[architecture.md](architecture.md) covers how a speaker is marked as a child. Every child voice is
drawn from the gender pool it already belongs to, so the gender invariant holds with children
included.

The library holds no child voices; its youngest speakers are in their early twenties. By ear, the
youngest native voices told to sound like a child beat the prebuilt child voices, which read young
but carry the General American base. So a child whose accent has a voice region takes one of the
three youngest native voices of its gender in that region, and the generator builds those child
pools from the ages the library states. Every child, whether marked by a child keyword in its name
or by the `child` life-stage marker in the bundled table, takes the `child` profile layer, whose
`pitch` ("Very high-pitched, light young child's voice, far above an adult voice") opens the style
and outranks the race's pitch, so a troll child sounds young rather than large.

A child whose accent has no native voices keeps the prebuilt child pool. Its male pool holds one
voice, deliberately: it is the only prebuilt male voice that reads as a young boy, and a second
that merely reads high is worse than the repetition. The female pool holds two.

## The player

The **Player Voice** setting picks a gender. When the typed **Your Accent** names a region's
keyword ("Irish", "Glasgow", "southern", and so on, listed in `tools/voice-regions.json`), the
player takes a fixed native voice from that region for the gender; regions are tried in file order
and the broad southern English region comes last, so a more specific accent always wins. Otherwise
the player takes index 0 of the player pool: there is one player, so nothing needs spreading on a
seed. The two options are
labelled Type A and Type B rather than by gender: the voices are
recognisably male and female, and the labelling follows the modern convention so the setting does
not ask a player to pick a gender.

## The narrator

The narrator is a speaker class of its own rather than a character, so it resolves to one fixed
voice in every session.

That voice is `en-gb-storyteller-2`, a native southern English voice the library tags as a
storyteller and narrator, picked by ear from the native storyteller candidates. It is named once,
as `narratorVoice` in `tools/voice-regions.json`, and the generator removes it from every region
and child pool; no race, child or player pool holds it either. So the game's own narration is never
mistaken for an NPC standing next to you. If the bundled table cannot load, the narrator falls back
to the prebuilt Callirrhoe. An explicit high-fantasy redraft of its profile direction was
auditioned and rejected.

## What the cache key does and does not see

A voice spec's cache-key fragment carries the speaker class, race, and gender: `npc:ELF:FEMALE`,
`player:MALE`, `narrator`.

The per-NPC seed and the child flag are deliberately absent from it. The backend already folds the
concrete resolved voice into its own cache variant, so two NPCs that map to different voices never
share a cached frame, and duplicating the seed in both places would only widen the key.

Cache keys are live user state. Players hold thousands of cached clips on disk, and a key change
silently re-bills every one of them, so the fragment above is fixed rather than tidy.
