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

## The table is a complete documented mapping

The ids in `expression-emotions.json` are **not guessed**. They are the publicly documented
RuneScape chathead expression animation enum, a named set spanning **9760-9862**, cross-referenced
from public sources (the rune-server "508 chat head animation list", the RuneScape Wiki
Chathead/Animations pages, and RuneLibris). The table maps every documented named expression in that
range to the nearest of our five `Emotion` values, so it covers the **standard human dialogue
expressions** out of the box.

These appear to be **global shared expression animations**: one id corresponds to one expression
applied across heads, rather than a per-model scheme. So a single table is expected to cover
standard dialogue. Non-human head classes (trolls, ogres, children, monsters) may emit ids outside
this documented set; those currently fall through to `NEUTRAL`, and the optional dev-client session
below can confirm whether any extension is warranted.

Every value must be a valid `Emotion` enum constant. A unit test enforces this.

### Documented expression ids and their mapped Emotion

| animationId | expression name    | mapped Emotion | note                          |
| ----------- | ------------------ | -------------- | ----------------------------- |
| 9760        | NO_EXPRESSION      | NEUTRAL        | default / idle                |
| 9764        | SAD                | SAD            |                               |
| 9768        | SAD_TWO            | SAD            |                               |
| 9772        | NO_EXPRESSION_TWO  | NEUTRAL        |                               |
| 9776        | WHY                | NEUTRAL        |                               |
| 9780        | SCARED             | SCARED         |                               |
| 9784        | MILDLY_ANGRY       | ANGRY          |                               |
| 9788        | ANGRY              | ANGRY          |                               |
| 9792        | VERY_ANGRY         | ANGRY          |                               |
| 9796        | ANGRY_TWO          | ANGRY          |                               |
| 9800        | MANIC_FACE         | ANGRY          | **ambiguous**                 |
| 9804        | JUST_LISTEN        | NEUTRAL        |                               |
| 9808        | PLAIN_TALKING      | NEUTRAL        |                               |
| 9812        | LOOK_DOWN          | SAD            | **ambiguous**                 |
| 9816        | WHAT_THE           | NEUTRAL        | **ambiguous** (surprise)      |
| 9820        | WHAT_THE_TWO       | NEUTRAL        | **ambiguous** (surprise)      |
| 9824        | EYES_WIDE          | SCARED         | **ambiguous** (shock)         |
| 9828        | CROOKED_HEAD       | NEUTRAL        |                               |
| 9832        | GLANCE_DOWN        | NEUTRAL        |                               |
| 9836        | UNSURE             | NEUTRAL        |                               |
| 9840        | LISTEN_LAUGH       | HAPPY          |                               |
| 9844        | TALK_SWING         | NEUTRAL        |                               |
| 9847        | NORMAL             | NEUTRAL        |                               |
| 9851        | GOOFY_LAUGH        | HAPPY          |                               |
| 9855        | NORMAL_STILL       | NEUTRAL        |                               |
| 9859        | THINKING_STILL     | NEUTRAL        |                               |
| 9862        | LOOKING_UP         | NEUTRAL        |                               |

### Ambiguous ids (the optional session's main job)

A few documented expressions do not map cleanly onto our five emotions, mostly because OSRS has
**surprise / shock / confusion** faces and we have no `SURPRISE` emotion, so those were folded to the
nearest of `NEUTRAL` / `SCARED`. These are the ids worth eyeballing in-game if anyone wants to refine
the table:

- **9800 MANIC_FACE** -> currently `ANGRY` (could read as crazed/excited)
- **9812 LOOK_DOWN** -> currently `SAD` (could read as neutral/thinking)
- **9816 WHAT_THE** -> currently `NEUTRAL` (surprise, no `SURPRISE` emotion)
- **9820 WHAT_THE_TWO** -> currently `NEUTRAL` (surprise, no `SURPRISE` emotion)
- **9824 EYES_WIDE** -> currently `SCARED` (shock, folded to the nearest fearful emotion)

Everything else in the table is a clean, documented mapping.

## Optional refinement: the debug-dump session

The dev-client debug-dump session is **optional verification**, not a prerequisite. The table above
is complete and shippable as-is. Use the session only to (a) confirm these ids match the **current
live OSRS cache**, and (b) settle the ambiguous ids listed above.

The plugin ships a `debugMode`-gated dump that prints the live chat-head animation id every game
tick a dialogue is open:

1. Enable **Debug Mode** in the TTSDialogue plugin config (or launch the dev client with the plugin
   and toggle it on).
2. Open dialogue with NPCs whose lines show emotionally distinct expressions. While a line is on
   screen, watch the client log for lines like:

   ```
   [expression-dump] speaker=<name> headAnimId=<id> text="<line>"
   ```

   `headAnimId=-1` means the head has no expression animation this tick (sprite/objectbox dialogue,
   or the one-tick race before the head animates) -> treated as `NEUTRAL`.
3. Confirm the documented ids above fire for the expected expressions on the live cache, and decide
   the final mapping for the ambiguous ids.
4. Adjust entries in `expression-emotions.json` only if the session contradicts the documented
   mapping, keeping every value a valid `Emotion` name. Re-run `./gradlew test` so
   `ExpressionEmotionTableTest` validates the shape.
5. If a non-human model class is ever observed emitting ids outside this set, those entries can be
   added once a per-class scheme is chosen (out of scope for the documented standard table).
