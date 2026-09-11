# Voice casting

Which Gemini voice each speaker gets, and why that one.
[architecture.md](architecture.md) owns the resolution mechanism and this owns the casting, so
neither repeats the other.

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

- **Gender is structural.** A male spec can only resolve to a voice from a male sub-pool, a female
  spec only from a female one. No race maps two genders onto the same voice.
- **Every spec resolves.** An unknown gender is voiced as male, an empty adult pool falls back to
  the neutral default and an empty child pool to the child anchor, so no spec can reach a backend
  without a voice. `GeminiVoiceMap` also sends an unknown race to the player pool, though nothing
  reaches it with one: the resolver rewrites an unknown race to human before the map is consulted,
  so that branch is defence in depth.
- **Placement within a pool is stable.** A per-NPC seed spreads same-race, same-gender NPCs across
  their sub-pool, and the same NPC lands on the same voice in every session. Pools hold two voices,
  so the spread is variety rather than a guarantee that any two NPCs differ. A spec carrying no
  seed anchors to index 0 of its pool.

## Depth is the organising axis

Voice depth is inferred from the catalog's character adjectives and then confirmed by ear.
Gravelly (Algenib), firm (Alnilam, Orus), even (Schedar), breathy (Enceladus), informative
(Charon, Rasalgethi) and knowledgeable (Sadaltager) are the deep, mature end. Upbeat (Puck) and
casual (Zubenelgenubi) are the bright, light end.

Big, imposing races anchor to the deep end so they sound large rather than high-pitched. Small
races stay deliberately bright, so a scuttling crab never reads as something standing over you.

| Race | Casting |
|---|---|
| Human | Clear, neutral, mid-depth. The most common case by far |
| Elf | Refined and clear |
| Dwarf | Gravelly and firm, deep |
| Goblin | Bright and light, deliberately high and small |
| Monkey | Bright, energetic, playful |
| Gorilla | Gravelly and firm, the deepest anchors |
| Troll and ogre | Gravelly and firm, the deepest male timbres |
| Undead | Breathy and even, deep and cold |
| Demon | Gravelly and informative, the deepest |
| Wizard | Knowledgeable and informative, weighty |
| Tortugan | Friendly and warm, relaxed mid-depth |
| Icyene | Firm and even |
| Citizen of Arceuus | The elf pool's refined, clear timbres, which carry an incorporeal delivery better than the earthier human voices |
| Aranei | Breathy paired with clear, so they carry the undead pool's softness without its cold and stay a living species by ear |
| Dog | The monkey pool's excitable timbres paired with the deepest anchors, so a bark lands as a sound with an animal behind it rather than as a word read aloud |
| Crab | The bright, light end alongside the goblins |
| Penguin | Bright and light, paired so the upbeat anchor carries the waddling comedy and the casual one the flat spy deadpan |

## Children

Life stage is a third resolution axis alongside race and gender, and
[architecture.md](architecture.md) covers how a speaker is marked as a child. Every child voice is
drawn from the gender pool it already belongs to, so the gender invariant holds with children
included.

The childlike timbre dominates what a player hears. Race and accent still colour the delivery
through the character profile's directive text, so a troll child sounds young rather than large.

Boys share a single voice deliberately. It is the only male voice that reads as a young boy, and a
second one that merely reads high is worse than the repetition. Girls have two, both of which read
young and hold the directed British accent.

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
