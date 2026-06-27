# Synthesis backends

The plugin routes every dialogue line through one `SynthesisBackend`, chosen by the **Voice Backend**
config. `BackendProvider` resolves the active backend on every line, applies the emotion-downgrade
rule, and falls back to the local Kokoro voice (with a one-time notice) whenever the selected backend
is unavailable.

| Backend | Config value | Engine location | Emotion | Offline | Setup cost |
|---------|--------------|-----------------|---------|---------|------------|
| OpenRouter (Gemini) | `Cloud` (default) | OpenRouter speech API over HTTPS | Happy, Sad, Angry, Scared, Neutral | No | your own OpenRouter API key |
| Kokoro | `Local` | external CPU `--stdio` engine | Neutral only | Yes | one-time engine + model download |

Emotion is detected from each speaker's chat-head animation and rides in every request. The cloud
backend renders it as an inline Gemini style tag on the spoken text (`[happy]`, `[sad]`, `[angry]`,
`[fearful]`), so happy, sad, angry, and scared lines are audibly different; Neutral carries no tag. The
local Kokoro voice is neutral-only, so `BackendProvider` downgrades every line to Neutral for it.

## Cloud (OpenRouter) backend

The default (cloud-first). An OpenAI-compatible speech request over HTTPS to
`https://openrouter.ai/api/v1/audio/speech`, selected when **Voice Backend** is `Cloud`. It needs an
OpenRouter API key; until one is set it logs a one-time notice and falls back to the local voice. The
model is fixed to Google's **Gemini 3.1 Flash TTS**, the one OpenRouter speech model with both a voice
catalog rich enough to map every race and gender and full emotion support. Each NPC gets a
gender-correct Gemini voice by race, and two NPCs of the same race and gender are spread across a
sub-pool so they sound distinct but stable. The detected emotion is prepended to `input` as an inline
style tag (`GeminiEmotionStyle`); Neutral adds none. The body requests `response_format: "pcm"`, a
headerless 16-bit LE mono stream at 24 kHz decoded to the pipeline's native rate.

With this backend active, dialogue text leaves your machine and is sent to OpenRouter. A missing key,
an API error, or a network problem fails that line gracefully and falls back to the local voice with a
one-time notice. The cache key folds in the resolved voice so two NPCs never replay each other's audio.

## Local (Kokoro) backend

An external CPU engine reached over a process transport (one JSON request line per synthesis, one
header line plus a raw float PCM frame back), spawned lazily off the game thread and kept alive across
lines. Neutral-only by deliberate design so the local voice stays clean neural output. It is the
universal fallback whenever the cloud backend is unavailable (no key, error, or offline).
