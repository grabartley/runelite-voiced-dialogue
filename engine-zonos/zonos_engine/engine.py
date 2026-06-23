"""Entry point for the standalone Zonos GPU TTS engine.

Speaks the same ``--stdio`` line protocol and ``{ok, gpu}`` health handshake the plugin's
``ExternalEngineClient`` / ``LocalZonosBackend`` drive. stdout is a binary channel (header line + PCM,
or one JSON line for health/error); everything human-readable goes to stderr.
"""

from __future__ import annotations

import argparse
import os
import struct
import sys
import traceback
import wave
from typing import Optional

from . import protocol
from .synthesizer import Synthesizer, build_synthesizer


def _bundle_root() -> str:
    frozen = getattr(sys, "_MEIPASS", None)
    if frozen:
        return frozen
    return os.environ.get("ZONOS_BUNDLE_ROOT") or os.path.dirname(
        os.path.dirname(os.path.abspath(__file__))
    )


def main(argv: Optional[list] = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    parser = argparse.ArgumentParser(prog="zonos-engine", add_help=True)
    parser.add_argument("--stdio", action="store_true", help="run the stdin/stdout line protocol")
    parser.add_argument("--selftest", action="store_true", help="synthesize a fixed phrase")
    parser.add_argument("--mock", action="store_true", help="mock tone synthesizer (framing only)")
    parser.add_argument("--wav", default=None, help="(--selftest) write the audio to this wav path")
    parser.add_argument(
        "--check-imports", action="store_true", help="import the synthesis modules and exit"
    )
    args = parser.parse_args(argv)

    if args.check_imports:
        return _run_check_imports()

    synth = build_synthesizer(_bundle_root(), mock=args.mock)
    if args.stdio:
        return _run_stdio(synth)
    if args.selftest:
        return _run_selftest(synth, args.wav)

    parser.print_help(sys.stderr)
    return 2


def _run_check_imports() -> int:
    # Build/packaging guard. Importing zonos.model pulls in zonos.backbone and transformers' lazily
    # resolved submodules; frozen, this fails if the bundle dropped either (which lets the engine warm
    # up but never synthesize). No weights, no GPU, so it runs on any runner.
    try:
        from zonos.model import Zonos  # noqa: F401
        from zonos.conditioning import make_cond_dict  # noqa: F401
    except BaseException:  # noqa: BLE001 - any import failure is a packaging failure
        print("FAILURE: synthesis-path imports did not resolve:", file=sys.stderr)
        traceback.print_exc()
        return 1
    print("check-imports OK: zonos.model + zonos.conditioning resolved.")
    return 0


def _run_stdio(synth: Synthesizer) -> int:
    out = sys.stdout.buffer
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            req = protocol.decode_request(line)
        except Exception as exc:  # noqa: BLE001 - malformed JSON: surface as an error line
            protocol.write_line(out, protocol.error_line("bad request: {}".format(exc)))
            print("Bad request line: {}".format(exc), file=sys.stderr)
            continue

        if req.is_health:
            _answer_health(out, synth)
            continue

        try:
            sample_rate, samples = synth.synthesize(
                req.text,
                req.player,
                req.race,
                req.gender,
                req.emotion,
                req.speed,
                req.emotion_vector,
                req.player_reference_clip,
            )
            protocol.write_response(out, sample_rate, samples)
        except Exception as exc:  # noqa: BLE001 - keep the process alive across one failure
            protocol.write_line(out, protocol.error_line(str(exc)))
            print("Synthesis failed: {}".format(exc), file=sys.stderr)

    synth.close()
    return 0


def _answer_health(out, synth: Synthesizer) -> None:
    try:
        protocol.write_line(out, protocol.health_line(True, synth.cuda_available(), synth.gpu_detail()))
    except Exception as exc:  # noqa: BLE001 - never crash the handshake
        protocol.write_line(
            out, protocol.health_line(False, False, "health probe failed: {}".format(exc))
        )


def _run_selftest(synth: Synthesizer, wav_path: Optional[str]) -> int:
    sample_rate, samples = synth.synthesize(
        "Zonos self test. The emotional voice engine is alive.",
        player=False,
        race="HUMAN",
        gender="MALE",
        emotion="HAPPY",
        speed=1.0,
        emotion_vector=None,
    )
    print("gpu={}".format(synth.cuda_available()))
    print("detail={}".format(synth.gpu_detail()))
    print("sampleRate={} samples={}".format(sample_rate, len(samples)))
    if not samples or sample_rate <= 0:
        print("Self-test produced empty audio", file=sys.stderr)
        return 1
    if wav_path:
        _write_wav(wav_path, sample_rate, samples)
        print("wrote {}".format(wav_path))
    synth.close()
    return 0


def _write_wav(path: str, sample_rate: int, samples) -> None:
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(int(sample_rate))
        frames = bytearray()
        for s in samples:
            clamped = max(-1.0, min(1.0, float(s)))
            frames += struct.pack("<h", int(clamped * 32767))
        w.writeframes(bytes(frames))


if __name__ == "__main__":
    raise SystemExit(main())
