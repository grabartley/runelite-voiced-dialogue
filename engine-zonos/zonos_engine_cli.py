"""Frozen-engine entry point.

PyInstaller runs the frozen script as ``__main__`` with no parent package, so it targets this thin
runner rather than ``zonos_engine/engine.py`` directly, keeping ``zonos_engine`` an importable package
(its relative imports resolve in the bundle as under ``python -m zonos_engine``).
"""

import multiprocessing
import os

# torch.compile/inductor spawns a subprocess compile pool; in a frozen exe those children re-launch the
# exe and crash before Zonos's suppress_errors fallback can act. Force eager so no workers spawn.
os.environ.setdefault("TORCHDYNAMO_DISABLE", "1")
os.environ.setdefault("TORCHINDUCTOR_COMPILE_THREADS", "1")

from zonos_engine.engine import main  # noqa: E402 - after the env guards above

if __name__ == "__main__":
    # A frozen multiprocessing child re-launches this exe; freeze_support dispatches it to the worker
    # instead of re-parsing CLI args. Must run before main().
    multiprocessing.freeze_support()
    raise SystemExit(main())
