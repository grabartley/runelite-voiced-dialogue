# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for the FULL-GRAPH frozen-import smoke test (smoke_import.py).

WHY THIS IS A FULL-GRAPH SMOKE (issue #103)
-------------------------------------------
The earlier smoke (#99) froze only ``smoke_import.py`` + torch + numpy and deliberately EXCLUDED
zonos / phonemizer / the engine package. That validated a *different* bundle than the one shipped:
green smoke, broken release. The residual untested risk -- does ``collect_all("zonos")`` /
``collect_all("phonemizer")`` re-collecting numpy transitively re-trigger the import-time double-init
even with an explicit ``collect_all("numpy")``? -- is exactly what a stripped graph cannot answer.

This spec freezes the SAME module graph as the real engine (``zonos-engine.spec``) by calling the
SAME shared recipe, ``_collect.collect_engine_deps()`` (collect_all over zonos/phonemizer/numpy +
torch/torchaudio data-only). The smoke and the shipped bundle are therefore physically incapable of
diverging on the collection graph: any recipe change lives in ``_collect.py`` and both specs consume
it. The only difference from the real spec is the entry point (the cheap probe ``smoke_import.py``
instead of the full ``zonos_engine_cli.py``); the frozen dependency bundle is identical.

The torch-data-only handling (never torch's binaries -- the hook owns those, issue #77) lives in
``_collect.py`` and is inherited here unchanged, so the frozen import path the smoke exercises matches
production. Run on a plain CPU runner: the double-init surfaces at ``import torch`` BEFORE any CUDA
call, so no GPU is needed to reproduce (or clear) the entire bug class with full fidelity.
"""

import os
import sys

block_cipher = None

# Invoked from the engine-zonos dir (same as the real build), so the engine package + entry resolve
# relative to cwd exactly as zonos-engine.spec does.
HERE = os.path.abspath(os.getcwd())
ENTRY = os.path.join(HERE, "packaging", "smoke_import.py")

# Make packaging/_collect.py importable. SPECPATH is this spec's directory (packaging/), injected by
# PyInstaller; fall back to HERE/packaging for plain linting without PyInstaller.
_PACKAGING_DIR = globals().get("SPECPATH") or os.path.join(HERE, "packaging")
if _PACKAGING_DIR not in sys.path:
    sys.path.insert(0, _PACKAGING_DIR)

from _collect import collect_engine_deps  # noqa: E402 - import after sys.path is primed above

# Freeze the engine's full dependency graph + the engine package itself, so the smoke imports zonos
# and calls the real cuda_available() against the same frozen bundle the release ships.
hiddenimports = [
    "zonos_engine",
    "zonos_engine.synthesizer",
    "zonos",
    "zonos.model",
    "zonos.conditioning",
    "phonemizer",
]

# THE shared recipe -- identical call to zonos-engine.spec. This is what makes the smoke faithful.
datas, binaries, _hidden = collect_engine_deps()
hiddenimports += _hidden


a = Analysis(
    [ENTRY],
    pathex=[HERE],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    runtime_hooks=[],
    excludes=["tkinter", "matplotlib"],
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="smoke-import",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    name="smoke-import",
)
