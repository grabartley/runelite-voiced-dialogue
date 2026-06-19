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

## The table is a documented mapping

The ids in `expression-emotions.json` are derived from the publicly documented RuneScape chathead
expression animation enum, a named set spanning **9760-9862**, not from live observation. The table
maps every documented named expression in that range to the nearest of our five `Emotion` values, so
it covers the **standard human dialogue expressions** out of the box.

Non-human head classes (trolls, ogres, children, monsters) may emit ids outside this documented set;
those fall through to `NEUTRAL`.

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

### Ambiguous ids

A few documented expressions do not map cleanly onto our five emotions, mostly because OSRS has
**surprise / shock / confusion** faces and we have no `SURPRISE` emotion, so those were folded to the
nearest of `NEUTRAL` / `SCARED`:

- **9800 MANIC_FACE** -> `ANGRY` (could read as crazed/excited)
- **9812 LOOK_DOWN** -> `SAD` (could read as neutral/thinking)
- **9816 WHAT_THE** -> `NEUTRAL` (surprise, no `SURPRISE` emotion)
- **9820 WHAT_THE_TWO** -> `NEUTRAL` (surprise, no `SURPRISE` emotion)
- **9824 EYES_WIDE** -> `SCARED` (shock, folded to the nearest fearful emotion)

Everything else in the table is a clean, documented mapping.
