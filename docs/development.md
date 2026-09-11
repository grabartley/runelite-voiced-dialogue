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

## Comments

**The source carries no comments at all.** Not explanatory ones, not javadoc, not "why" ones. Names
and structure carry the meaning, and anything that seems to need explaining is either a sign the
code should be reshaped until it does not, or reasoning that belongs in a document under `docs/`.

The rule is absolute because every softer version is a judgement call. A bar like "only when the
why is surprising" needs someone to rule on surprising, line by line, in every review, and what
clears that bar drifts towards commentary that restates the code beside it.

This covers `src/main/java` and `src/test/java` equally. Build scripts, workflow files, property
files, and the Python tooling under `tools/` keep their comments: `#` appears inside ordinary
values, and a Python docstring is an expression rather than a comment, so removing one can change
behaviour. Markdown is content rather than commentary and is unaffected.

Two further rules apply to names, log messages, notices, and every markdown file in the repo:

- **No transient language.** Write the final state, always. Banned framing: "as before",
  "previously", "legacy", "new", "now supports", "backward-compatible", migration or rollout
  narration, and anything describing how the code got here rather than what it is.
- **No bare issue or PR references.** In markdown, link them (`[#123](url)`), never `#123`.

## Class size

**Hard limit: 700 lines per class**, main and test alike. A class approaching it holds more than
one responsibility: extract a collaborator rather than trimming whitespace. Test classes reach it
when fixtures are copy-pasted between methods; extract a shared fixture helper instead.

## Package layout

Packages under `com.grahambartley.runelite.voiced.dialogue` follow the lifecycle of a single
dialogue line, and the dependency graph runs one way only.

Each package sits at a tier. A package may import a strictly lower tier, never a higher one and
never a peer.

| Tier | Package | Holds |
|---|---|---|
| 7 | (root) | `VoicedDialoguePlugin` and `VoicedDialogueConfig`, pinned here by `runelite-plugin.properties`, and the wiring that constructs both provider backends |
| 6 | `capture` | Reading a line off the game widgets: watching, widget reads, text cleaning, narration, public chat, examine, prefetch |
| 6 | `speech.openrouter` | The OpenRouter transport: payload shape, credit metering, usage reads |
| 6 | `speech.aistudio` | The Google AI Studio transport: `generateContent`, SSE streaming, token usage |
| 5 | `speech` | The provider-neutral call flow: retry and back-off, HTTP helpers, the backend contract, the off-thread pipeline |
| 4 | `speech.model` | The Gemini speech and translation models both providers serve: model ids, voice catalog, emotion tags |
| 4 | `cache` | The memory and disk tiers that keep a line from being billed twice |
| 3 | `profile` | Resolving who is speaking into how they sound: voice spec, character profile, emotion |
| 2 | `speaker` | Who the speaker is: NPC lookup, demographics, races, the wiki auto-learn path |
| 1 | `audio` | PCM decoding and playback, plus the cave echo effect |
| 1 | `speech.spend` | Session cost accounting behind `::voicedspend` |

The two provider packages share tier 6 on purpose. Peers cannot import each other, so neither
transport can reach into the other; anything both need belongs in `speech` or `speech.model`. Only
the plugin root, which wires them, names both.

`speech` holds no provider-specific code, so the shared call flow cannot quietly grow a dependency
on one provider's quirks. An import that runs against a tier means a class is in the wrong package,
not that the rule needs an exception. `PackageDependencyTest` enforces this.

## Launch the dev client

Run the `com.grahambartley.runelite.voiced.dialogue.VoicedDialoguePluginRunner` class (in the
test sources) with VM options `-ea --add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED`,
either from your IDE or wired into `build.gradle`.

## Where to go next

- [architecture.md](architecture.md): how the synthesis pipeline works end to end.
- [npc-voice-tooling.md](npc-voice-tooling.md): the offline tooling that generates the bundled
  NPC voice and profile table.
- [voice-casting.md](voice-casting.md): which Gemini voice each race, child, player, and the
  narrator gets, and why that one.
- [emotion-detection.md](emotion-detection.md): how chat-head expressions map to emotions.
- [hub-submission.md](hub-submission.md) and
  [hub-compliance-checklist.md](hub-compliance-checklist.md): how the Plugin Hub listing works
  and what it requires.
