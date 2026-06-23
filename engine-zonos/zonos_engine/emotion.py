"""Emotion-vector handling for the Zonos engine.

The plugin resolves an emotion to an 8-dim vector and sends it on the request, so the engine just
consumes it. The presets here only cover the bare-request / ``--selftest`` path. Dimension order and
preset shape mirror the plugin's ``ZonosEmotionVectors`` so the two never drift.
"""

from __future__ import annotations

from typing import List, Optional

DIMENSIONS = 8

# Zonos-v0.1's fixed dimension order: [happiness, sadness, anger, fear, surprise, disgust, neutral, other].
HAPPINESS = 0
SADNESS = 1
ANGER = 2
FEAR = 3
SURPRISE = 4
DISGUST = 5
NEUTRAL = 6
OTHER = 7

_PRIMARY = 0.80
_NEUTRAL_FLOOR = 0.20


def _neutral() -> List[float]:
    v = [0.0] * DIMENSIONS
    v[NEUTRAL] = 1.0
    return v


def _expressive(primary_dim: int) -> List[float]:
    v = [0.0] * DIMENSIONS
    v[primary_dim] = _PRIMARY
    v[NEUTRAL] = _NEUTRAL_FLOOR
    return v


_PRESETS = {
    "NEUTRAL": _neutral(),
    "HAPPY": _expressive(HAPPINESS),
    "SAD": _expressive(SADNESS),
    "ANGRY": _expressive(ANGER),
    "SCARED": _expressive(FEAR),
}


def preset_for(emotion: Optional[str]) -> List[float]:
    key = emotion.upper() if isinstance(emotion, str) else "NEUTRAL"
    return list(_PRESETS.get(key, _PRESETS["NEUTRAL"]))


def resolve_emotion_vector(
    emotion_vector: Optional[List[float]], emotion: Optional[str]
) -> List[float]:
    if emotion_vector:
        vec = [float(v) for v in emotion_vector[:DIMENSIONS]]
        if len(vec) < DIMENSIONS:
            vec += [0.0] * (DIMENSIONS - len(vec))
        return vec
    return preset_for(emotion)
