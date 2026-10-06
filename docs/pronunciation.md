# Pronunciation

`src/main/resources/pronunciations.json` lists RuneScape names the cloud voices tend to say wrong,
with how each one should be said. `PronunciationGuide` swaps each listed name in a line for its
respelling before the line is sent, so "Welcome to Neitiznot" is spoken as "Welcome to
Nay-tiz-not". What the player reads on screen does not change.

## The table

Each row has a `word`, written as it appears in game, and a `say`, the respelling with the stressed
syllable in capitals:

```json
{ "word": "Neitiznot", "say": "NAY-tiz-not" }
```

The capitals are for the person reading the table. The plugin sends the respelling in lower case,
with a capital first letter when the name was capitalised in the line. By ear, a hyphenated lower
case respelling in the line itself is said more reliably than a pronunciation note added to the
style direction. Both were auditioned against the same lines for
[#379](https://github.com/grabartley/runelite-voiced-dialogue/issues/379).

Matching ignores case, treats a curly apostrophe as a straight one, allows any run of spaces inside
a multi-word name, and never matches inside a longer word, so "Seren" leaves "Serenity" alone. A
possessive keeps its ending: "Saradomin's" becomes "Sa-ra-dome-in's". When two listed names
overlap, the longer one wins.

## Sources

Most rows come from Jagex's official Pronunciation Guide, archived on the RuneScape Wiki as
[Transcript:Pronunciation guide](https://runescape.wiki/w/Transcript:Pronunciation_guide). The
rest come from the pronunciation stated on that name's OSRS Wiki page, for example
[Arceuus](https://oldschool.runescape.wiki/w/Arceuus), [Lovakengj](https://oldschool.runescape.wiki/w/Lovakengj)
and [Slepe](https://oldschool.runescape.wiki/w/Slepe).

Names from the guide that only exist in RuneScape 3 are left out, and so are names an English
voice already says correctly, like Lumbridge or Falador. A respelling can only make those worse.

## Adding a name

1. Find a source: the Jagex guide first, then the OSRS Wiki page for the name.
2. Add a row with the in-game spelling and the respelling. A name listed twice, in any case, fails
   the load.
3. Render a real line that uses the name with the `compare-voices` skill and check it by ear before
   shipping. A respelling that reads well on paper can still be said oddly.

Changing the table never changes a cache key, so lines already cached keep the old pronunciation
until the cache is cleared.
