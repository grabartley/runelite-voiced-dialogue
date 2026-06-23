#!/usr/bin/env python3
"""Full-graph frozen-import probe for the Zonos engine (issue #103).

WHY THIS EXISTS
---------------
The recurring Zonos GPU failure ``CPU dispatcher tracer already initlized`` /
``cannot load module more than once per process`` is a numpy import-time double-init, NOT a CUDA
runtime error. It fires the instant the PyInstaller-frozen ``import torch`` re-initializes numpy's C
extension a second time in one process, BEFORE any GPU call. Two consequences:

1. No GPU is required to reproduce it. A plain CPU CI runner that freezes the real module graph and
   runs ``import torch`` reproduces (or clears) this whole bug class with full fidelity.
2. The earlier smoke (#99) froze only this script + torch + numpy and excluded zonos/phonemizer/the
   engine package, so it validated a different bundle than ships. This entry, frozen via
   ``smoke-import.spec`` (which uses the SAME shared collection recipe as ``zonos-engine.spec``),
   exercises the engine's ACTUAL import + probe path against the SAME module graph the release ships.

WHAT IT DOES
------------
Reproduces the engine's real import + probe path: ``import torch``, ``import zonos``, then calls the
engine's own ``zonos_engine.synthesizer.cuda_available()`` (via a ZonosSynthesizer instance) rather
than re-implementing the probe. The GPU result is IGNORED: ``cuda_available()`` returning False on a
GPU-less runner is EXPECTED and fine, because the double-init happens at IMPORT, before any CUDA call.
We fail ONLY if an import/probe raises, or if the captured output contains the double-load markers.
"""

from __future__ import annotations

import io
import sys
import traceback
from contextlib import redirect_stderr, redirect_stdout

# The frozen-bundle markers of the import-time double-init this gate exists to catch.
DOUBLE_LOAD_MARKERS = (
    "cannot load module more than once per process",
    "CPU dispatcher tracer already initlized",
)


def main() -> int:
    # Capture everything the probe path prints so we can scan it for the double-load markers even
    # when nothing raises (the engine's cuda_available() swallows probe failures and only prints a
    # traceback to stderr, so a clean exit code alone is NOT sufficient evidence).
    buf = io.StringIO()

    try:
        with redirect_stdout(buf), redirect_stderr(buf):
            # Report numpy first only for diagnostics; the real test is the torch import below, which
            # is where the frozen bootstrap's numpy re-init (the double-init) actually fires.
            import numpy

            print("numpy version: {}".format(numpy.__version__), flush=True)

            # THE import: torch exactly as the engine does (synthesizer._import_torch -> import torch).
            # In the frozen build this is where the double-load surfaced.
            import torch

            print("torch version: {}".format(torch.__version__), flush=True)

            # Full-graph fidelity: import zonos too, since the real bundle's collect_all("zonos")
            # pulls numpy/torch transitively and we want THAT path frozen and exercised.
            import zonos  # noqa: F401 - imported for its frozen-graph side effects

            print("zonos imported", flush=True)

            # Run the engine's REAL probe rather than re-implementing it. bundle_root is irrelevant to
            # the import/probe path (it is only used later for weights/voices), so a placeholder is
            # fine. cuda_available() obtains torch via the engine's single cached import and runs the
            # same torch.cuda probe; False on a GPU-less runner is expected and IGNORED here.
            from zonos_engine.synthesizer import ZonosSynthesizer

            synth = ZonosSynthesizer(bundle_root=".")
            available = bool(synth.cuda_available())
            print("cuda_available(): {}".format(available), flush=True)
    except Exception:  # noqa: BLE001 - any raise from the frozen import/probe path is a FAILURE
        out = buf.getvalue()
        sys.stdout.write(out)
        print("FAILURE: frozen import/probe path raised:", flush=True)
        traceback.print_exc()
        sys.stdout.flush()
        return 1

    out = buf.getvalue()
    # Surface the captured output so CI logs show exactly what the frozen probe did.
    sys.stdout.write(out)

    # Even without a raise, fail loudly if the double-init markers appear anywhere in the output: the
    # engine's cuda_available() catches probe exceptions internally and prints the full traceback to
    # stderr, so the markers can be present on a non-raising, otherwise-"clean" exit.
    for marker in DOUBLE_LOAD_MARKERS:
        if marker in out:
            print(
                "FAILURE: frozen output contains the double-load marker: {!r}".format(marker),
                flush=True,
            )
            return 1

    print(
        "SMOKE OK: frozen import torch + import zonos + cuda_available() completed without a "
        "double-load.",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
