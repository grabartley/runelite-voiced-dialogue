"""Real (Zonos-v0.1) and mock synthesizers behind one interface, selected by ``--mock``."""

from __future__ import annotations

import math
import os
import sys
import traceback
from typing import List, Optional, Tuple

from . import emotion as emotion_mod
from . import voices

ZONOS_SAMPLE_RATE = 44100  # Zonos's DAC autoencoder decodes to 44.1 kHz; the plugin never resamples.


class Synthesizer:
    def sample_rate(self) -> int:
        raise NotImplementedError

    def cuda_available(self) -> bool:
        raise NotImplementedError

    def gpu_detail(self) -> str:
        return ""

    def synthesize(
        self,
        text: str,
        player: bool,
        race: Optional[str],
        gender: Optional[str],
        emotion: str,
        speed: float,
        emotion_vector: Optional[List[float]],
        player_reference_clip: Optional[str] = None,
    ) -> Tuple[int, List[float]]:
        raise NotImplementedError

    def close(self) -> None:
        pass


class MockSynthesizer(Synthesizer):
    """Deterministic tone for wire-framing tests only (``--mock``); never real speech."""

    def __init__(self, sample_rate: int = ZONOS_SAMPLE_RATE):
        self._sample_rate = sample_rate

    def sample_rate(self) -> int:
        return self._sample_rate

    def cuda_available(self) -> bool:
        return False

    def gpu_detail(self) -> str:
        return "mock synthesizer (framing only, no GPU, not real speech)"

    def synthesize(
        self, text, player, race, gender, emotion, speed, emotion_vector,
        player_reference_clip=None,
    ):
        voice_id = voices.voice_for(player, race, gender)
        if player and player_reference_clip:
            voice_id = "player_custom:" + str(player_reference_clip)
        vec = emotion_mod.resolve_emotion_vector(emotion_vector, emotion)
        primary_axis = max(range(len(vec)), key=lambda i: vec[i]) if vec else emotion_mod.NEUTRAL
        freq = 180.0 + 20.0 * primary_axis
        speed = speed if speed and speed > 0 else 1.0
        seconds = max(0.25, min(2.0, 0.05 * max(1, len(text)))) / speed
        n = int(self._sample_rate * seconds)
        amp = 0.2 + 0.05 * (sum(ord(c) for c in voice_id) % 5)
        samples = [amp * math.sin(2.0 * math.pi * freq * (i / self._sample_rate)) for i in range(n)]
        return self._sample_rate, samples


class ZonosSynthesizer(Synthesizer):
    """The real Zonos-v0.1 backend. torch and Zonos are imported lazily on first load."""

    def __init__(self, bundle_root: str, model_id: str = "Zyphra/Zonos-v0.1-transformer"):
        self._bundle_root = bundle_root
        self._model_id = model_id
        self._model = None
        self._torch = None
        self._device = None
        self._speaker_cache = {}
        self._sample_rate = ZONOS_SAMPLE_RATE
        self._cuda = None
        self._gpu_detail = ""

    def _import_torch(self):
        # Import torch once per process and reuse the cached module: re-importing it under a second
        # identity makes the frozen build raise "cannot load module more than once per process" (#77).
        if self._torch is None:
            import torch

            self._torch = torch
        return self._torch

    def cuda_available(self) -> bool:
        if self._cuda is not None:
            return self._cuda
        try:
            torch = self._import_torch()
            available = bool(torch.cuda.is_available()) and torch.cuda.device_count() > 0
            if available:
                name = torch.cuda.get_device_name(0)
                self._gpu_detail = "CUDA GPU: {} (torch {})".format(name, torch.__version__)
            else:
                self._gpu_detail = "no usable CUDA device (torch {})".format(torch.__version__)
            self._cuda = available
        except Exception as exc:  # noqa: BLE001 - any failure means "no usable GPU"
            self._gpu_detail = "torch/CUDA unavailable: {}".format(exc)
            self._cuda = False
            print("=== ZONOS GPU PROBE FAILED (full traceback) ===", file=sys.stderr)
            traceback.print_exc()
            sys.stderr.flush()
        return self._cuda

    def gpu_detail(self) -> str:
        if self._cuda is None:
            self.cuda_available()
        return self._gpu_detail

    def sample_rate(self) -> int:
        return self._sample_rate

    def load(self) -> None:
        if self._model is not None:
            return
        # ZONOS_DEVICE is a dev/local override (e.g. "cpu"/"mps") to run the real synthesis path off a
        # GPU. The shipped plugin never sets it, so production stays CUDA-gated and falls back to Kokoro.
        device_override = os.environ.get("ZONOS_DEVICE", "").strip()
        if device_override:
            self._import_torch()
            self._device = device_override
        else:
            if not self.cuda_available():
                raise RuntimeError(
                    "Zonos requires a usable CUDA GPU; none detected ({})".format(self.gpu_detail())
                )
            self._import_torch()
            self._device = "cuda"
        from zonos.model import Zonos

        self._model = Zonos.from_pretrained(self._model_id, device=self._device)
        rate = getattr(self._model, "sampling_rate", None) or getattr(
            getattr(self._model, "autoencoder", None), "sampling_rate", None
        )
        if isinstance(rate, int) and rate > 0:
            self._sample_rate = rate

    def _speaker_embedding(self, voice_id: str):
        if voice_id in self._speaker_cache:
            return self._speaker_cache[voice_id]
        path = voices.embedding_path_for(self._bundle_root, voice_id)
        if not os.path.isfile(path):
            path = voices.embedding_path_for(self._bundle_root, voices.DEFAULT_VOICE)
        embedding = self._embed_clip(path)
        self._speaker_cache[voice_id] = embedding
        return embedding

    def _custom_player_embedding(self, clip_path: str, fallback_voice_id: str):
        # A bad user clip (issue #50) must degrade to the bundled player voice, never error the line.
        cache_key = "custom:" + os.path.abspath(clip_path)
        if cache_key in self._speaker_cache:
            return self._speaker_cache[cache_key]
        try:
            if not os.path.isfile(clip_path):
                raise FileNotFoundError(clip_path)
            embedding = self._embed_clip(clip_path)
        except Exception as exc:  # noqa: BLE001 - any decode failure falls back to the default
            print(
                "Custom player reference clip unusable ({}); using bundled player voice".format(exc),
                file=sys.stderr,
            )
            embedding = self._speaker_embedding(fallback_voice_id)
        self._speaker_cache[cache_key] = embedding
        return embedding

    def _embed_clip(self, path: str):
        import torchaudio

        wav, sr = torchaudio.load(path)
        return self._model.make_speaker_embedding(wav, sr)

    def synthesize(
        self, text, player, race, gender, emotion, speed, emotion_vector,
        player_reference_clip=None,
    ):
        self.load()
        torch = self._torch
        from zonos.conditioning import make_cond_dict

        voice_id = voices.voice_for(player, race, gender)
        if player and player_reference_clip:
            speaker = self._custom_player_embedding(player_reference_clip, voice_id)
        else:
            speaker = self._speaker_embedding(voice_id)
        vec = emotion_mod.resolve_emotion_vector(emotion_vector, emotion)
        emotion_tensor = torch.tensor([vec], device=self._device, dtype=torch.float32)

        cond_dict = make_cond_dict(text=text, speaker=speaker, language="en-us", emotion=emotion_tensor)
        conditioning = self._model.prepare_conditioning(cond_dict)
        codes = self._model.generate(conditioning)
        wavs = self._model.autoencoder.decode(codes).cpu()
        audio = wavs[0]
        if audio.dim() > 1:
            audio = audio.mean(dim=0)
        samples = audio.detach().to("cpu").float().flatten().tolist()
        return self._sample_rate, samples

    def close(self) -> None:
        self._model = None
        self._speaker_cache.clear()
        try:
            if self._torch is not None and self._cuda:
                self._torch.cuda.empty_cache()
        except Exception:  # noqa: BLE001 - best-effort cleanup
            pass


def build_synthesizer(bundle_root: str, mock: bool) -> Synthesizer:
    return MockSynthesizer() if mock else ZonosSynthesizer(bundle_root)
