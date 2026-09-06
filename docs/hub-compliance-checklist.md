# Plugin Hub compliance record

What the [RuneLite Plugin Hub](https://github.com/runelite/plugin-hub) requires of
**Voiced Dialogue** (internal name `voiced-dialogue`) and how this repo satisfies it,
focused on the requirements the Hub review is strictest about: outbound network access,
third-party API usage, user-provided secrets, bundled binaries, and user consent. The
compliance story is **Cloud-only, no subprocess, no bundled binaries**: the plugin voices
dialogue solely through outbound HTTPS to the configured provider
(`generativelanguage.googleapis.com` for Google AI Studio, or `openrouter.ai`), spawns no
external process, and ships no engine, native library, or model.

This is the verification record. How the Hub listing itself works lives in
[`hub-submission.md`](hub-submission.md).

## Requirements

### Outbound HTTP uses the injected client only

**Verified.** No `new OkHttpClient()` / `new OkHttpClient.Builder()` anywhere in the cloud
path (`grep -rn "new OkHttpClient" src/` returns nothing). Every component receives
RuneLite's injected `OkHttpClient` and, where it needs different timeouts or a keepalive
pool, derives from it via `newBuilder()` (allowed):

- `VoicedDialoguePlugin.java`: `@Inject private OkHttpClient okHttpClient;`, passed into every
  network component.
- `OpenRouterTtsBackend.java`: derives a keepalive HTTP/1.1 client via
  `httpClient.newBuilder()...build()`; `OpenRouterTranslator` and the warm-up path share that
  derived client.
- `AiStudioTtsBackend.java`: derives its own keepalive client via
  `httpClient.newBuilder()...build()`; `AiStudioTranslator` shares that derived client.
- `OpenRouterUsageClient.java`: the `::voicedspend` balance read, using the injected client
  as-is.
- `speaker/WikiNpcClient.java`: optional NPC auto-learn lookups, also through the injected
  client.

### All network and synthesis stays off the game thread

**Verified.** `DialogueAudioService` runs synthesis on dedicated daemon executors (a 2-thread
bounded synthesis pool, a warm-up thread for the cloud connection handshake, and a 2-thread
prefetch pool); the cloud HTTP calls for both providers execute on those threads. Disk cache
I/O also stays on those pool threads: the prefetch fast-path checks only the in-memory tier,
so the game thread never reads the on-disk cache. NPC auto-learn lookups run on their own
`tts-wiki-learn` daemon thread, and the `::voicedspend` balance read runs on a dedicated
spend executor thread. User-facing notices are hopped back to the client thread via
`clientThread.invokeLater(...)` in `ChatNoticeManager`. The game thread never makes a network
call, reads the disk cache, or blocks on synthesis.

### No subprocess, no `Thread.sleep`, no thread interrupt

**Verified.** `src/main` spawns no external process (`grep -rn "ProcessBuilder\|Runtime.*exec"
src/main` returns nothing), calls no `Thread.sleep` (`grep -rn "Thread.sleep(" src/main`
returns nothing), and never interrupts a thread (`grep -rn "Thread.currentThread\|\.interrupt("
src/main` returns nothing but the unrelated `DialogueAudioService.interrupt()` playback method).
The cloud retry backoff waits on a delayed `CompletableFuture` joined to completion rather than
a sleeping pool thread, and blocking waits use `CompletableFuture.join()` (which needs no
`InterruptedException` handling and never re-raises the interrupt flag).

### API keys are secrets, never logged, sent only to their own provider

**Verified.**

- Stored via the RuneLite config items `openRouterApiKey` and `googleAiStudioApiKey`, both
  declared `secret = true` (`VoicedDialogueConfig.java`), so RuneLite masks them in the UI and
  config store.
- Each key is read only to authenticate its own provider: the `Authorization: Bearer <key>`
  header in `OpenRouterTtsBackend`, `OpenRouterTranslator`, and `OpenRouterUsageClient`, and
  the `x-goog-api-key` header in `AiStudioTtsBackend` and `AiStudioTranslator`.
  Neither key is ever sent to the other provider's host.
- Never logged: error logs record HTTP status, content-type, generation id, and a body
  snippet, never the key or the `Authorization` header. No `log.*` statement references the
  key.
- Never bundled: the key only ever exists in the user's local RuneLite config.

### In-plugin consent / privacy notice for cloud

**Verified.** That dialogue text leaves the machine is disclosed in multiple places:

- First-run onboarding chat notice (`ChatNoticeManager`): "...Your dialogue text is then sent
  to that provider to be voiced. Until a key is set, lines stay silent."
- The **Voice Provider** setting names the service that receives the text and bills the calls.
- The Hub listing itself carries the off-machine-data `warning=` in the descriptor; the
  canonical disclosure text lives in [`hub-submission.md`](hub-submission.md).

### Graceful behaviour when outbound network is blocked or the key is invalid

**Verified by code audit** (re-verify by hand per below). Each backend
gates on `isAvailable()` (false when no key) and wraps every request in try/catch: non-2xx
responses, empty/undecodable/truncated audio, `IOException`, and unexpected
`RuntimeException` all return `null` after a one-time chat notice. A `null` synthesis result
leaves the single line unvoiced; nothing is thrown to the game thread. With no key set, the
line stays silent with a one-time "add your API key" notice naming the selected provider.

### No secrets or large binaries in the built jar

**Verified by inspecting `build/libs` after `./gradlew jar`.** The jar is ~362 KiB
(well under the Hub's 10 MiB limit) and contains only compiled classes plus three data
resources, all loaded via `getResourceAsStream`:

| Resource | Size | What it is |
|----------|------|------------|
| `npc-voices.json` | ~2.0 MiB uncompressed | Precomputed NPC race/gender/ethnicity + voice profile table |
| `expression-emotions.json` | ~1.5 KiB | Chat-head animation → emotion map |
| `profanity.txt` | ~1.0 KiB | Offline profanity blocklist |

No API keys, no model files, no native libraries, and no synthesis engine of any kind: the
plugin is Cloud-only and voices dialogue over HTTPS, so there is nothing to bundle and nothing
to download at runtime.

### Main sources compile under the Hub's Java 11 standard build

**Verified.** `build=standard` discards our `build.gradle` and compiles `src/main` at
`options.release=11`. Our own `compileJava` pins the same release level so a Java 12+ syntax or
API in main sources fails locally before it reaches the Hub. `src/main` carries no records,
pattern-matching `instanceof`, or other Java 12+ constructs; tests stay on release 17 and are
never built by the Hub.

## Hub listing text

From `runelite-plugin.properties` (the `version` is written by the `Release` workflow onto
each tagged release commit):

- **displayName:** Voiced Dialogue
- **author:** Graham Bartley
- **support:** https://github.com/grabartley/runelite-voiced-dialogue
- **description:** Voices NPC and player dialogue using cloud text-to-speech (Google AI Studio or OpenRouter).
- **tags:** tts, voice, dialogue, audio, immersion, accessibility, npc, speech, talk, text-to-speech
- **build:** standard

The descriptor's `warning=` disclosure text is kept in
[`hub-submission.md`](hub-submission.md).

## Re-verifying by hand

Code review confirms the above; the network-blocked path and jar contents are re-confirmed
by hand on the target machine (per `run-game-client`) whenever this record needs
re-validating:

1. Run with the network disabled or an invalid key and confirm dialogue stays silent with a
   single chat notice, no crash, and no game-thread exception in the logs.
2. Inspect `build/libs/*.jar` contents and confirm no API key, no model binary, no
   disallowed dependency.
3. Confirm the logs never print the API key.
