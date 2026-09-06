# Development

How to build, test, and run the plugin locally.

## Requirements

- **Java 17** and Gradle (wrapper included).
- The plugin's `src/main` sources compile at **release 11** for Plugin Hub compatibility, so
  keep them free of Java 12+ syntax and APIs (records, pattern-matching `instanceof`,
  `Stream.toList()`, and so on); tests use Java 17.

## Build

```bash
git clone https://github.com/grabartley/runelite-voiced-dialogue.git
cd runelite-voiced-dialogue
./gradlew clean build
```

`build` also runs `spotlessCheck`; fix formatting with `./gradlew spotlessApply`.

## Run the tests

```bash
./gradlew test
```

## Package layout

Packages under `com.grahambartley.runelite.voiced.dialogue` follow the lifecycle of a single
dialogue line, and the dependency graph runs one way only.

| Package | Holds |
|---|---|
| (root) | `VoicedDialoguePlugin` and `VoicedDialogueConfig`, pinned here by `runelite-plugin.properties` |
| `capture` | Reading a line off the game widgets: watching, widget reads, text cleaning, public chat, prefetch |
| `speech` | Turning a line into audio: the shared cloud call flow, both provider backends, the off-thread pipeline |
| `speech.spend` | Session cost accounting behind `::voicedspend` |
| `cache` | The memory and disk tiers that keep a line from being billed twice |
| `profile` | Resolving who is speaking into how they sound: voice spec, character profile, emotion |
| `speaker` | Who the speaker is: NPC lookup, demographics, races, the wiki auto-learn path |
| `audio` | PCM decoding and playback, plus the cave echo effect |

Dependencies point from the top of that table toward the bottom, and the graph has no cycles:

```
capture  -> profile, speech
speech   -> audio, cache, profile, speaker, speech.spend
cache    -> audio, profile
profile  -> speaker
speaker, audio, speech.spend  -> nothing else in the plugin
```

`audio`, `speaker` and `speech.spend` are leaves; nothing outside the plugin root depends on
`capture`. An import that runs against this direction means a class is in the wrong package,
not that the rule needs an exception.

## Launch the dev client

Run the `com.grahambartley.runelite.voiced.dialogue.VoicedDialoguePluginRunner` class (in the
test sources) with VM options `-ea --add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED`,
either from your IDE or wired into `build.gradle`.

## Where to go next

- [architecture.md](architecture.md): how the synthesis pipeline works end to end.
- [npc-voice-tooling.md](npc-voice-tooling.md): the offline tooling that generates the bundled
  NPC voice and profile table.
- [emotion-detection.md](emotion-detection.md): how chat-head expressions map to emotions.
- [hub-submission.md](hub-submission.md) and
  [hub-compliance-checklist.md](hub-compliance-checklist.md): how the Plugin Hub listing works
  and what it requires.
