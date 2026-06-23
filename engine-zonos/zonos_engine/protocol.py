"""Encode/decode for the ``--stdio`` wire protocol. Torch-free so it can be unit-tested anywhere.

The framing must match the plugin (``ExternalEngineClient``) and the Kokoro engine's Java
``StdioProtocol`` byte-for-byte:

* Request (stdin, one JSON line)::
      {"text", "voice": {"player", "race", "gender"}, "emotion", "speed",
       "emotionVector": [8 floats], "playerReferenceClip": "/abs/path.wav"}
  ``race``/``gender`` are uppercase enum names (e.g. ``HUMAN``, ``MALE``). ``emotionVector`` and
  ``playerReferenceClip`` (issue #50, player lines only) are optional; a bare request still decodes.
* Synthesis reply (stdout): one header line ``{"sampleRate","samples","format":"f32le"}`` followed by
  exactly ``samples * 4`` little-endian float32 bytes.
* Failure: a single ``{"error": "..."}`` line and no PCM frame, so the pipe never hangs.
* Health: ``{"op":"health"}`` -> ``{"ok","gpu","detail"}``; the plugin gates availability on ``gpu``.

Header keys are emitted compact and in order (sampleRate, samples, format) to match Gson's output.
"""

from __future__ import annotations

import json
import struct
from dataclasses import dataclass, field
from typing import List, Optional

FORMAT_F32LE = "f32le"
_COMPACT = (",", ":")


@dataclass
class Request:
    text: str = ""
    player: bool = False
    race: Optional[str] = None
    gender: Optional[str] = None
    emotion: str = "NEUTRAL"
    speed: float = 1.0
    emotion_vector: Optional[List[float]] = None
    player_reference_clip: Optional[str] = None
    op: Optional[str] = None
    raw: dict = field(default_factory=dict)

    @property
    def is_health(self) -> bool:
        return self.op == "health"


def decode_request(line: str) -> Request:
    root = json.loads(line) if line else {}
    if not isinstance(root, dict):
        root = {}

    op = root.get("op")
    if op is not None and not isinstance(op, str):
        op = str(op)

    voice = root.get("voice") if isinstance(root.get("voice"), dict) else {}
    speed_val = root.get("speed")
    try:
        speed = float(speed_val) if speed_val is not None else 1.0
    except (TypeError, ValueError):
        speed = 1.0

    emotion_vector = None
    vec = root.get("emotionVector")
    if isinstance(vec, list):
        try:
            emotion_vector = [float(v) for v in vec]
        except (TypeError, ValueError):
            emotion_vector = None

    clip = root.get("playerReferenceClip")

    return Request(
        text=_as_str(root.get("text"), ""),
        player=bool(voice.get("player", False)),
        race=_as_str(voice.get("race"), None),
        gender=_as_str(voice.get("gender"), None),
        emotion=_as_str(root.get("emotion"), "NEUTRAL"),
        speed=speed,
        emotion_vector=emotion_vector,
        player_reference_clip=clip if isinstance(clip, str) and clip.strip() else None,
        op=op,
        raw=root,
    )


def encode_samples(samples) -> bytes:
    seq = list(samples)
    return struct.pack("<%df" % len(seq), *seq)


def header_line(sample_rate: int, samples: int) -> str:
    return json.dumps(
        {"sampleRate": int(sample_rate), "samples": int(samples), "format": FORMAT_F32LE},
        separators=_COMPACT,
    )


def error_line(message: Optional[str]) -> str:
    return json.dumps({"error": "" if message is None else str(message)}, separators=_COMPACT)


def health_line(ok: bool, gpu: bool, detail: str = "") -> str:
    return json.dumps({"ok": bool(ok), "gpu": bool(gpu), "detail": detail or ""}, separators=_COMPACT)


def write_response(out_binary, sample_rate: int, samples) -> None:
    pcm = encode_samples(samples)
    out_binary.write((header_line(sample_rate, len(pcm) // 4) + "\n").encode("utf-8"))
    out_binary.write(pcm)
    out_binary.flush()


def write_line(out_binary, line: str) -> None:
    out_binary.write((line + "\n").encode("utf-8"))
    out_binary.flush()


def _as_str(value, fallback):
    if value is None:
        return fallback
    return value if isinstance(value, str) else str(value)
