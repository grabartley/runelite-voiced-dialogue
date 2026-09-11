# Synthesis architecture

Every dialogue line is voiced through a single pipeline: a cloud speech call implemented by the
`SynthesisBackend` that `BackendProvider` supplies. Two backends exist, one per **Voice Provider**
setting: Google AI Studio (`AiStudioTtsBackend`, the default) and OpenRouter
(`OpenRouterTtsBackend`). `BackendProvider` resolves the configured provider's backend live on
every call, so switching takes effect on the next line with no restart, and also applies the
emotion-downgrade rule (an emotion the model cannot voice is rewritten to Neutral before synthesis).
A line the pipeline cannot voice (for example when the active provider's API key is not set) is
left silent, never routed to the other provider.

Both providers speak through the same model, Google's **Gemini 3.1 Flash TTS**, and receive the
same spoken content, so a line sounds the same whichever provider voices it. The next section
describes how that content is built once for both; each provider section after it covers only the
transport.

## Voice resolution and prompt construction (shared by both providers)

Each NPC gets a gender-correct Gemini voice by race (`GeminiVoiceMap`), and two NPCs of the same
race and gender are spread across a sub-pool by a per-NPC seed that is stable across sessions.
Life stage is a third axis: an NPC marked as a child (a `child` life-stage marker in the bundled
table, or a child keyword like "Child" or "Schoolboy" in the display name) resolves to a dedicated
youthful sub-pool of its gender instead of its adult race anchor, for every race and ethnicity
alike. Which voices sit in each pool, and why those ones, is
[voice-casting.md](voice-casting.md).

Emotion is detected from each speaker's chat-head animation and rides in every request as one of
Happy, Sad, Angry, Scared, or Neutral. It is prepended to the spoken text as an inline Gemini
style tag (`[happy]`, `[sad]`, `[angry]`, `[fearful]`, rendered by `GeminiEmotionStyle`), so
happy, sad, angry, and scared lines are audibly different; Neutral adds no tag.

A per-speaker **character profile** (`CharacterProfile`, resolved by `NpcProfileTable`) is
rendered as a leading `AUDIO PROFILE` direction block setting accent/style/pace, so the profile
sets the character and the emotion tag colours the moment.

Narration is a speaker class of its own. The item, double-item, and message boxes (`NarrationWatcher`,
gated by **Voice Narration**, off by default) are the game telling the story rather than a character
talking, so they resolve to the fixed `VoiceSpec.NARRATOR` and the `narrator` profile layer: one
voice held out of every character pool, always Neutral since a narration box carries no chat head,
and untouched by the Player and NPC Speaking Styles and by the cave echo, which colours the voices
of people standing in the room with you. The spoken language still applies. Being fixed is what
keeps narrated lines on a stable cache key across sessions.

These are the engine's generic dialogs (`objectbox` 193, `objectbox_double` 11, `messagebox` 229),
not content-specific ones, and the game raises `messagebox` for interface prompts as well as story
beats: a world switch warning arrives on the same widget, through the same chat type, in the same
game state as a quest's narration. Nothing in the widget, the chat type, or the game state
separates them, so narration is opt-in rather than filtered by a heuristic that would have to guess
what counts as story.

Examine text (`ExamineSpeaker`, gated by **Voice Examine Text**, off by default) rides the same
narrator path. The client tags examines with their own chat types (`ITEM_EXAMINE`, `NPC_EXAMINE`,
`OBJECT_EXAMINE`), so no other game-channel message can reach it and no string matching is needed.
The types alone are not sufficient, though, because other plugins publish their own lines on them:
RuneLite's Examine plugin appends an item price on `ITEM_EXAMINE` moments after the real examine
text, and since every new line stops the one playing, voicing it would talk over the flavour line the
player asked for. Only lines the game authored are voiced, told apart by the RuneLite format message
the client stamps onto any node published through `ChatMessageManager` with a RuneLite-formatted
message. Both built-in publishers on these types, the Examine and Barrows plugins, supply one.

That test cannot be applied on arrival. `ChatMessageManager.add` publishes the message and stamps the
format message on the following statement, so every subscriber first sees the node unmarked. The
decision is therefore deferred to the next client tick (`ClientThread.invokeLater`, roughly 20ms) by
which point the stamp has landed.

Examine yields the audio channel while a dialogue is open, asking `DialogueWatcher`, the single owner
of that state, rather than reading the dialogue widgets a second time. That gate is checked in the
deferred task, the moment the channel would actually be taken, so a dialogue opening during the
intervening tick still wins it. Like every other voiced line an examine goes through
`DialogueAudioService.speak`, which stops current playback and advances the epoch, so a fresh examine
cuts the one before it exactly as a new dialogue line cuts the line being skipped.

## The OpenRouter speech call

An OpenAI-compatible speech request over HTTPS to `https://openrouter.ai/api/v1/audio/speech`. It
needs an OpenRouter API key; until one is set it logs a one-time notice and its lines stay silent.
Gemini 3.1 Flash TTS is the one OpenRouter speech model with both a voice catalog rich enough to
map every race and gender and full emotion support. The body requests `response_format: "pcm"`, a
headerless 16-bit LE mono stream at 24 kHz decoded to the pipeline's native rate.

Dialogue text leaves your machine and is sent to OpenRouter. A missing key, an API error, or a network
problem fails that line gracefully (it is left unvoiced) and surfaces a one-time notice.

## The Google AI Studio speech call

With **Voice Provider** set to Google AI Studio, `AiStudioTtsBackend` sends the same content
directly to the Gemini API instead: a `generateContent` request to
`https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-tts-preview:generateContent`,
authenticated with a Google AI Studio API key in the `x-goog-api-key` header. It needs its own key
(**Google AI Studio API Key**); until one is set it logs a provider-specific one-time notice and its
lines stay silent. The shared voice resolution is requested as the `prebuiltVoiceConfig` voice.

Only the transport differs from OpenRouter: audio comes back as base64 16-bit LE PCM inside JSON
rather than a raw body, and the Gemini API has no `speed` parameter, so a non-default **Speaking
Pace** is rendered as a leading `SPEAKING PACE` prompt direction instead. The
`streamGenerateContent` variant (`?alt=sse`) backs the streaming path below, delivering audio as
server-sent events whose chunks are decoded and handed to playback as they arrive. Failure handling
mirrors OpenRouter: one retry for a transient empty or truncated line, a backed-off retry for a
network timeout, a rate-limit back-off on 429, and a `cacheVariant` built from the same fields under
the distinct `cloud-google-ai-studio` backend id, so the two providers' cache entries never collide.

On the Gemini API a 429 means quota, so the notice is worded from the `google.rpc.QuotaFailure`
violation the rejection carries: a free-tier ceiling, a paid per-model cap, and a per-minute limit
each read differently, and a body carrying no violation falls back to wording that names no cause.

The paid per-model cap is the one players meet. A billed key on Google's entry usage tier gets 100
requests per day per project for the pinned preview speech model
(`GenerateRequestsPerDayPerProjectPerModel`, `quotaValue: 100`), so 100 uncached synthesis calls
a day.
Enabling billing does not lift it: the allowance is a property of the usage tier, which Google
raises on cumulative spend (around 10,000 requests per day once the account has spent roughly $100),
so in practice it stands for the whole player base. The cap is scoped per model, which is why at the
same moment the speech model rejects with 429 the GA `gemini-3.1-flash-lite` translation model on
the same key still answers 200. The durable fix is the GA model swap, tracked in
[#236](https://github.com/grabartley/runelite-voiced-dialogue/issues/236).

The 100 requests are not 100 lines the player hears. **Prefetch Dialogue** defaults on, and
`DialoguePrefetcher` speculatively synthesizes every visible dialogue option, so options that are
never picked draw on the same allowance. Player-facing copy therefore says *up to* 100 fresh lines
a day and names prefetch as a claim on them, rather than equating requests with heard lines.

Player-facing copy states the 100-a-day figure and that billing does not raise it, and describes the
lift only as one Google grants for heavy long-term use. The spend threshold is deliberately kept out
of the README and the in-game notices: it reads as a paywall on a plugin that costs fractions of a
cent per line.

OpenRouter carries no equivalent ceiling. It serves the same model as a paid model, and paid models
have no platform-level request cap: `GET /api/v1/key` on a credited key reports `is_free_tier:
false` with no daily allowance, and speech requests succeed while an AI Studio key is exhausted.

The translation hop has a direct counterpart too: `AiStudioTranslator` sends the same shared
system prompt to `gemini-3.1-flash-lite` through the Gemini API, so a non-English language or a
speaking style works without an OpenRouter key.

## Cost and latency controls

Because synthesis is billed per character, several guards keep cost bounded and latency low:

- **Cache key.** `cacheVariant` folds in the model, the resolved Gemini voice, and the character
  profile, plus (only when not at their defaults) the speaking pace and a non-English spoken
  language, on top of the shared `(backendId, voiceKey, emotion, text)` identity. Every speaker
  resolves to a profile, so every key carries its content hash. A model, voice, pace, profile, or
  language change therefore never replays the wrong audio, while a plain English line stays on a
  stable key so changing a setting that cannot affect it does not force a needless re-bill. Line
  length is not part of the key: every line is sent whole.
- **In-flight de-duplication.** If two tasks reach the synth step for the same cache key at once, only
  the first issues a cloud call; the second waits on and reuses its result (`synthesizeDeduped`).
- **Session spend readout.** `SpendTracker` counts billable work per provider, recorded inside each
  backend at the point audio is confirmed (decoded, or the first chunk fed to the sink), so every
  cache tier, deduped join, and failed call stays out of the totals. Prefetch synths land in their own
  bucket and translation hops in another, since they bill against a different model. `::voicedspend`
  formats a snapshot through `SpendReport`. Session-scoped and memory-only: a fresh tracker is built
  on plugin start and nothing is written to disk.

  The cost each provider reports differs, so the readout does too. OpenRouter exposes a key's
  all-time credit usage at `/api/v1/key`; `OpenRouterUsageClient` reads it and `OpenRouterCreditMeter`
  subtracts a baseline taken at session start, giving a genuinely billed figure. The meter reports
  unknown rather than zero without a baseline, and a key swap resets it, since usage on another key
  is a different running total. The Gemini API returns no cost at all and its real billing sits
  behind the Cloud Billing API, so AI Studio is costed from the token counts it does report:
  `AiStudioTokenUsage` reads `usageMetadata` (taking the largest reading across a stream's events,
  which report a running total), and `SpendPricing` converts those measured tokens at Google's
  published rate. The readout labels that conversion an estimate and OpenRouter's figure as billed.

  The translation hop is a second billable call against a second model, and each provider accounts
  for it differently. On OpenRouter it bills to the same key, so it is inside the usage delta with
  no extra work. On AI Studio it is a separate `generateContent` call whose tokens are read through
  `AiStudioTokenUsage.forText` and banked in their own counters. The split matters: the hop's output
  is text, and reading it through the speech parser would price it as audio at more than twenty
  times its rate, so the two parse entry points exist precisely to keep that from happening.
  Balance reads run on a dedicated daemon thread, never the game thread, and the finished lines hop
  back to the client thread to be posted.
- **Timeout and stale-drop.** Each provider runs under its own ceiling (`RetryTuning`), sized to how
  it delivers audio: 60 seconds for Google AI Studio, whose longest measured line completes in about
  14 seconds, and 120 seconds for OpenRouter, which spends a long line's whole generation before
  returning anything and narrows that ceiling per line (see below). A hung request therefore cannot
  pin a synthesis-pool worker, and the pipeline's epoch check drops any response that arrives after
  the dialogue has advanced, so stale audio never plays late. The live synthesis pool runs two workers
  sharing one queue, so a line stuck on a slow call or a backed-off retry (left running so its
  result still caches) does not block the next line: the free worker picks it up.
- **Speaking pace.** The **Speaking Pace** setting (Delivery section) is sent as the OpenRouter
  `speed` parameter only when it is not 100%, so the default request body is unchanged; the active
  model may ignore it.
- **Keepalive connection.** The pipeline reuses one long-lived client derived from the injected one
  (an 8-connection 15-minute keepalive pool and a 2s connect budget), so back-to-back lines
  reuse a warm connection instead of re-handshaking. It is pinned to HTTP/1.1: the speech endpoint
  streams raw PCM, and HTTP/2 would multiplex the prefetch pool and the live line onto one
  connection where a concurrent streamed body can return truncated as an empty 200, so each
  concurrent call instead gets its own pooled connection. The same client backs the translation hop.
- **Connection warm-up.** Both cloud backends implement `warmUp()`, run off the game thread at
  session start and whenever an API key or provider changes. OpenRouter warms by GETting its
  unbilled `/api/v1/key` endpoint twice concurrently, leaving two pooled connections, so neither the
  first spoken line nor a live line racing a prefetch pays a TCP/TLS handshake. It is a no-op while
  the pool already holds a connection, so re-warming spends nothing.
- **Empty-200 retry.** A 200 with a zero-byte body is a transient server glitch (the generation id
  is present but no audio came back), so the line is retried once before falling back.
- **Per-line call budget.** Because OpenRouter withholds audio until a line is fully generated, a
  line's wait scales with its length, and a fixed read timeout either killed long lines or gave
  short ones far too long. Each call now gets `CALL_BUDGET_BASE` plus a per-character allowance,
  clamped to the client's ceiling, and the per-read budget matches that ceiling since the whole
  wait arrives as a single read.
- **Timeout retry.** A read/call timeout (a slow generation or a transient network blip) is retried
  once after a short exponential backoff with jitter, rather than dropping the line on the first
  failure. A connect-phase failure (host unreachable) and any non-2xx fail the line without a retry.
- **Fastest-provider routing.** Every request carries a `provider` block with `sort: "throughput"`
  (the `:nitro` equivalent), so OpenRouter routes to the lowest-latency provider for the model.
- **Prompt-cache stabilisation.** The per-speaker character-profile block leads each request and is
  byte-stable (profile fields are trailing-trimmed at construction), so Gemini's implicit prompt
  cache hits on repeats for the same speaker, lowering input cost and time-to-first-byte.
- **Rate-limit back-off.** A `429` opens a back-off window. When the rejection states its own wait,
  through a `Retry-After` header or a `google.rpc.RetryInfo` delay in the body, that wait is the
  window (clamped to an hour) and nothing is sent until it passes, since a call made before the
  stated moment only earns another rejection. Changing an API key or the provider drops the window,
  which is one of the fixes the notice asks for. When it states nothing, the window is a geometric,
  capped guess: user lines still try, but speculative prefetch holds off (`isThrottled`) so the
  plugin never retry-storms a limit.

Beyond per-line guards, two larger levers cut perceived latency and broaden reach:

- **Speculative prefetch.** When dialogue options are visible, `DialoguePrefetcher` builds the exact
  request the player would speak for each option and warms it through `DialogueAudioService.prefetch`,
  which shares the in-flight dedup and both cache tiers but never plays audio or touches the playback
  epoch. A small fixed pool caps it at two requests in flight, a per-conversation cap bounds spend,
  already-cached lines are skipped, and leaving the node cancels still-queued prefetches. Gated by
  **Prefetch Dialogue**.
- **Optional translation.** With **Spoken Language** set to anything but English, the active
  provider's translator (`OpenRouterTranslator` or `AiStudioTranslator`)
  translates each line through the Gemini flash-lite model (a fixed per-language system
  prompt for prompt-cache stability, preserving names and RuneScape terms) before the speech call,
  which then carries a BCP-47 `language_code` derived from the base language. The language (with any
  quirk) is folded into the cache key, so a line is translated and billed at most once per
  language/quirk; a failed translation fails the line gracefully rather than voicing the wrong
  language. **Spoken Language** is a fixed dropdown (the `SpokenLanguage` enum in `VoicedDialogueConfig`,
  one entry per supported language), so every selection carries a known-good BCP-47 `language_code`;
  the enum is the single source of truth for both the options and their codes.
- **Player / NPC Speaking Style.** Two independent settings, one for your own lines (**Player
  Speaking Style**) and one for NPC lines (**NPC Speaking Style**), drawn from the same option set
  (Gen Z slang, pirate speak, formal, Shakespearean, cyberpunk, and so on). Each style
  shifts word choice and phrasing only, never the voice or accent. The style for the line's speaker
  class is appended
  to the spoken language, so the translation hop rewrites that line in that style. It routes through
  the hop even for English, and composes with any language. Each class is selected from the
  `player` flag on `SynthesisRequest`, so the cache key already differs between a player and an NPC
  line of identical text under different styles. Either class can be `None` independently (e.g. a
  roadman player among posh NPCs).

The translation model is invoked only when there is something for it to do. For a given line, with
that speaker class's Speaking Style on `None` and **Spoken Language** English, the effective target
is plain English, so the line bypasses the model entirely and the source text goes straight to
speech: no chat-completions request, no added latency or cost. Setting a non-English language, a
style for that class, or both is what turns the hop on.

A cache-missed live line plays as it downloads: the backend's `synthesizeStreaming` decodes the
response incrementally and feeds each chunk to the player through a `PcmSink`, so audio starts on
the first decoded chunk instead of after the whole body. On Google AI Studio that decodes each SSE
audio event as it arrives, and audio starts after roughly 0.8s whatever the line's length.
OpenRouter reads the raw PCM body per network read, but sends nothing until the whole clip is
generated, so its first chunk only lands once the line is finished and the wait grows with the
line's length: measured at ~1.7s for a 20-character line and ~15s for a 400-character one. Streaming
therefore only shortens time-to-sound on AI Studio. The whole line is still accumulated and cached
on a clean finish, an interrupted or incomplete stream plays what arrived but is never cached, and
debug mode logs `firstChunkMs` (time to first audible chunk) alongside the full elapsed time so the
real streaming gain per provider is measurable. Prefetch and cave-echo lines always buffer.

The primary cost lever remains the persistent disk cache, always present, which keeps any
already-heard line from being billed again across sessions. Its footprint is bounded by the **Cache
Size Limit** (default 1024 MiB) and evicted oldest-first (FIFO) so it never grows past the
configured limit; a read never rescues an old entry, and the just-written clip survives unless it
alone exceeds the cap, in which case the pass clears the directory and that line is billed again
next time. Setting the limit to `0` opts out of eviction entirely, so the cache keeps every clip
for users who would rather spend disk than ever re-bill a line. The limit is read at eviction time
rather than held from start-up, so changing it applies from the next cached line with no restart.

## Cave echo

**Cave Echo** (off by default) adds a decaying echo to lines spoken while the player is
underground, so dialogue in a cave, dungeon, sewer or basement sounds enclosed. Narration is
excluded: the echo is the room colouring a voice inside it, and the narrator is not in the room. Underground is a pure
coordinate test: the player's mirror-corrected world `Y` at or above `Constants.OVERWORLD_MAX_Y`,
since every cave and dungeon is displaced north of the overworld. The echo is local DSP (a damped
feedback comb) applied to a fresh buffer at playback, after both cache tiers. Both tiers still store
the dry line under the unchanged cache key, so toggling the effect never invalidates the cache and the
line is never re-billed for it: it is free, adds no network call, and does not affect billing
or privacy.

## Audio playback

Synthesized audio (a `Pcm` of mono float samples) is played through `javax.sound.sampled.SourceDataLine`
by `StreamingAudioPlayer`, converting to signed 16-bit LE PCM via `PcmAudio`. Nothing is staged to a
temp file, and a generation counter lets a new line interrupt the one currently playing. The Plugin Hub
maintainers accept this `javax.sound` use for the plugin's streaming and interruption needs.
