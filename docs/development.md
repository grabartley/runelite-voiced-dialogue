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

`./gradlew check` runs that suite and the Checkstyle no-comments gate over both source sets.

## Comments

**The source carries no comments at all.** Not explanatory ones, not javadoc, not "why" ones. Names
and structure carry the meaning, and anything that seems to need explaining is either a sign the
code should be reshaped until it does not, or reasoning that belongs in a document under `docs/`.

The rule is absolute because every softer version is a judgement call. A bar like "only when the
why is surprising" needs someone to rule on surprising, line by line, in every review, and what
clears that bar drifts towards commentary that restates the code beside it.

This covers `src/main/java` and `src/test/java` equally. Build scripts, workflow files, property files, and the Python tooling under
`tools/` keep their comments: `#` appears inside ordinary values, and a Python docstring is an
expression rather than a comment, so removing one can change behaviour. Markdown is content rather
than commentary. So are the `_comment` keys in `src/main/resources/npc-voices.json`, which are data
the profile table is authored against.

### The build enforces it

`config/checkstyle/checkstyle.xml` carries exactly one rule, a `MatchXpath` query over the parsed
comment nodes:

```xml
<property name="query" value="//SINGLE_LINE_COMMENT | //BLOCK_COMMENT_BEGIN"/>
```

The query runs against Checkstyle's Java AST, so a string literal is never mistaken for a comment
and no regular expression is involved. `SINGLE_LINE_COMMENT` covers `//`, and `BLOCK_COMMENT_BEGIN`
covers both `/* */` and `/** */`, so javadoc is not a special case and gets no exemption.

A comment fails `./gradlew build` locally and in CI, at `file:line:column`, tagged `[NoComments]`.
The Gradle Checkstyle plugin attaches a task per source set and `check` picks both of them up, so
main and test sources are covered alike.

Text matching is not an alternative. A `grep` for lines beginning `//`, `/*`, or `*` cannot tell an
operator from a comment marker, so a wrapped multiplication reports as a comment.

### Why a linter and not Spotless

Spotless already runs on every build, which makes it the obvious place and the wrong one.

Spotless has no assertion primitive. Every step is a rewriter, and `spotlessCheck` means nothing
more than "run the rewriter and diff against disk". Its only comment-aware behaviour points the
other way: `googleJavaFormat` preserves and reflows javadoc rather than removing it. Enforcing
there would mean shipping a literal-aware comment lexer and running it unattended on every build,
forever. The moment a tool is allowed to rewrite source unattended, a bug in it becomes a silent
code deletion.

So the build fails instead of fixing itself. There is no `spotlessApply` equivalent that deletes
the comment for you, and that is the point.

### The gate tests itself

The failure worth guarding against is a gate that silently stops gating: a mistyped query matches
nothing, reports zero violations, and reads exactly like a clean tree.

`NoCommentsRuleTest` runs Checkstyle against the real configuration file over a fixture at
`src/test/resources/comment-gate/CommentGateFixture.java.txt`. The fixture is a resource rather
than a source file so the gate cannot audit its own test data. It asserts that each comment form is
reported, that no comment-like literal is, and that the total is exact, so a query matching too
much fails as loudly as one matching too little. The fixture covers a URL holding `//`, a string
holding `/* */`, a `'"'` char literal, an escaped quote followed by slashes, a text block holding
all three markers, and a bare `//` carrying no text.

The configuration file is declared an input of the `test` task. Without that, editing the query
leaves the task up to date and Gradle skips it, which is precisely the silent failure being
guarded against.

The test proves the **query**, not the **wiring**. Repointing `configFile`, setting
`ignoreFailures`, or adding a source set with no task attached all leave the suite green while the
gate stops covering code. The query is the part that fails silently and subtly; the wiring fails
visibly, in the build file, where review catches it.

### What the gate does not catch

A `.java` file whose entire content is a comment, with no `package` statement and no type, audits
clean: an empty compilation unit gives `MatchXpath` no tree to walk. Anything carrying a `package`
line is caught, so this is unreachable in this source tree, but it is a hole rather than a
guarantee.

Checkstyle carries one rule and is not a general style gate. Growing it into one is a separate
decision, and it should be resisted while Spotless and `googleJavaFormat` own formatting.

### Related rules

These apply to names, log messages, notices, and every markdown file in the repo:

- **No transient language.** Write the final state, always. Banned framing: "as before",
  "previously", "legacy", "new", "now supports", "backward-compatible", migration or rollout
  narration, and anything describing how the code got here rather than what it is.
- **No bare issue or PR references.** In markdown, link them (`[#123](url)`), never `#123`.

## Class size

**Hard limit: 700 lines per class**, main and test alike. A class approaching it holds more than
one responsibility: extract a collaborator rather than trimming whitespace. Test classes reach it
when fixtures are copy-pasted between methods; extract a shared fixture helper instead.

Two test classes sit above the limit, `OpenRouterTtsBackendTest` and `DialogueAudioServiceTest`.
They are the only two, and they are the exceptions rather than the precedent.

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
