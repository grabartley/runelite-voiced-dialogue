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
