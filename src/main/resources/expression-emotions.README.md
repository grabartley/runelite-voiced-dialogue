# Chat-head expression -> Emotion table

`expression-emotions.json` maps an OSRS dialogue chat-head **expression animation id** to one of the
five canonical `com.grahambartley.synthesis.Emotion` values (`NEUTRAL`, `HAPPY`, `SAD`, `ANGRY`,
`SCARED`). Emotion detection (#26) reads the animation id off the open chat-head widget and looks it
up here; the resolved emotion rides in every `SynthesisRequest` and each backend renders it as far
as it can (Kokoro neutral, Azure SSML styles, Zonos emotion vectors).

## The default contract

**Any animation id not present in this map, and `-1` (no animation / stale head), resolves to
`NEUTRAL`.** This is the single safe default the whole pipeline depends on: it means an unseen
expression, a non-human head, or a one-tick race where the head animation lags the text never
crashes and never blocks the game thread. #26 owns the runtime `EmotionResolver` that implements
this; `ExpressionEmotionTable` in this repo loads the JSON and encodes the same default so the
contract is testable in isolation.

## Important: this seed is UNVERIFIED and model-relative

The ids in `expression-emotions.json` are a **best-effort seed**, not observed data:

- They are seeded from the widely-cited human "talk" range (~9760-9859) and have **not** been
  confirmed against today's OSRS cache.
- They are **model-relative**: they describe the **human / standard** dialogue head only.
  Non-human heads (trolls, ogres, children, monsters, etc.) emit different ids for the same
  apparent expression and currently fall through to `NEUTRAL`. The table is extended per model
  class later, once those ids are observed.
- Every value must be a valid `Emotion` enum constant. A unit test enforces this.

Seed entries (confirm or replace each via the dump below):

| animationId | seeded Emotion | observed expression (to verify)        |
| ----------- | -------------- | -------------------------------------- |
| 9760        | NEUTRAL        | default / idle talk                    |
| 9764        | SAD            | downcast, frowning                     |
| 9780        | SCARED         | shocked / afraid                       |
| 9788        | ANGRY          | shouting / furious                     |
| 9808        | NEUTRAL        | plain talking                          |
| 9851        | HAPPY          | laughing / smiling                     |

## How to verify and extend the table (debug-dump procedure)

The plugin ships a `debugMode`-gated dump that prints the live chat-head animation id every game
tick a dialogue is open. Use it to turn the seed above into observed data:

1. Enable **Debug Mode** in the TTSDialogue plugin config (or launch the dev client with the plugin
   and toggle it on).
2. Open dialogue with NPCs whose lines show emotionally distinct expressions. While a line is on
   screen, watch the client log for lines like:

   ```
   [expression-dump] speaker=<name> headAnimId=<id> text="<line>"
   ```

   `headAnimId=-1` means the head has no expression animation this tick (sprite/objectbox dialogue,
   or the one-tick race before the head animates) -> treat as `NEUTRAL`.
3. Record which id fires for each observed expression (happy, sad, angry, scared, neutral) on the
   **human** head. Confirm the seeded ids above or replace them with the real observed values.
4. Add or correct entries in `expression-emotions.json`, keeping every value a valid `Emotion`
   name. Re-run `./gradlew test` so `ExpressionEmotionTableTest` validates the shape.
5. To extend to a non-human model class, repeat against an NPC of that class and note that its ids
   differ; those entries can be added once a per-class scheme is chosen (out of scope for the human
   seed).
