# Voice casting

Which Gemini voice each speaker gets, and why that one.
[architecture.md](architecture.md) owns how a speaker is resolved into a voice spec; this owns
which voice that spec lands on.

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
- **Placement within a pool is stable.** A per-NPC seed spreads same-race, same-gender NPCs across
  their sub-pool, and the same NPC lands on the same voice in every session. Adult pools hold two
  voices, so the spread is variety rather than a guarantee that any two NPCs differ. A spec
  carrying no seed anchors to index 0.
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

The player, children, and the narrator resolve outside the race table:

| Speaker | Male | Female |
|---|---|---|
| Player | Achird, Iapetus | Aoede, Autonoe |
| Child | Puck | Leda, Zephyr |
| Narrator | Callirrhoe | Callirrhoe |

`NpcRace` is the key, and several in-game species bucket into one of these before the map is
consulted: gnomes are voiced from the goblin pool, giants and cyclopes from the troll pool, and
dragons and TzHaar from the demon pool. [npc-voice-tooling.md](npc-voice-tooling.md) owns that
bucketing.

## Depth is the organising axis

Depth comes from the catalog's character adjectives, confirmed by ear. The male voices below are
the spine of the axis rather than the full roster, since they are where the adjectives divide most
cleanly: gravelly (Algenib), firm (Alnilam, Orus), even (Schedar) and breathy (Enceladus) are the
deep end; upbeat (Puck) and casual (Zubenelgenubi) are the bright end; informative (Charon,
Rasalgethi) and knowledgeable (Sadaltager) sit between them, reading measured rather than low and
carrying weight through delivery rather than pitch, which is why Charon can anchor the plain human
pool and still sit in the wizard one. The female pools follow the same ordering by ear.

Big, imposing races take the deep end so they sound large rather than high-pitched. Small ones
stay bright, so a scuttling crab never reads as something standing over you. The undead male
anchor is the breathy voice, which reads hollow rather than merely low.

## Where a pool is borrowed

Most races have a pool assembled for them. These are cast by reference to a pool that already
exists instead, and the reference is itself the casting decision:

- **Tortugans** take the player male pool unchanged and the wizard female pool unchanged. Both
  read relaxed and mid-depth rather than characterful, which suits warm island folk who are
  otherwise plain people.
- **Citizens of Arceuus** take the elf pool unchanged, and the elf pool is itself cast off the
  human one: it keeps Iapetus and Erinome, drops the human anchors Charon and Despina, and adds
  Rasalgethi and Vindemiatrix, which read more refined and carry an incorporeal delivery better.
- **Aranei** keep one undead voice per gender, Enceladus and Achernar, and pair each with a clear
  one. Enceladus is the breathy voice and Achernar the soft one, which is what carries their
  hushed, telepathic delivery; the clear half keeps them sounding like a living species.
- **Dogs** anchor on the monkey pool's excitable and forward voices and pair each with one from
  the deep end, so a bark lands as a sound with an animal behind it rather than as a word read
  aloud.
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

The childlike timbre dominates what a player hears. Race and accent still colour the delivery
through the character profile's directive text, so a troll child sounds young rather than large.

The male child pool holds one voice, deliberately. It is the only male voice that reads as a young
boy, and a second that merely reads high is worse than the repetition. The female pool holds two,
both of which read young and hold the directed British accent.

## The player

The **Player Voice** setting picks a gender pool rather than a voice, and the player always takes
index 0 of it: there is one player, so nothing needs spreading on a seed. The two options are
labelled Type A and Type B rather than by gender: the voices are
recognisably male and female, and the labelling follows the modern convention so the setting does
not ask a player to pick a gender.

## The narrator

The narrator is a speaker class of its own rather than a character, so it resolves to one fixed
voice in every session.

That voice is held out of every race pool, every child pool and the player pool, so the game's own
narration is never mistaken for an NPC standing next to you. It was picked by ear from the three
voices no character pool claimed: it holds the directed British accent and reads as a storyteller
rather than as someone in the room. An explicit high-fantasy redraft of its profile direction was
auditioned against it and rejected.

## What the cache key does and does not see

A voice spec's cache-key fragment carries the speaker class, race, and gender: `npc:ELF:FEMALE`,
`player:MALE`, `narrator`.

The per-NPC seed and the child flag are deliberately absent from it. The backend already folds the
concrete resolved voice into its own cache variant, so two NPCs that map to different voices never
share a cached frame, and duplicating the seed in both places would only widen the key.

Cache keys are live user state. Players hold thousands of cached clips on disk, and a key change
silently re-bills every one of them, so the fragment above is fixed rather than tidy.
