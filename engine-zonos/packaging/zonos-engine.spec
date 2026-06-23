# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller spec for the self-contained Zonos GPU engine executable.

PyInstaller freezes the engine entry point plus its full dependency graph (the embedded Python
interpreter, the PyTorch CUDA wheels with their bundled CUDA runtime, the Zonos package, and the
phonemizer backend) into a single ``zonos-engine`` / ``zonos-engine.exe`` under ``dist/``. The
release workflow then lays this out under ``runtime/`` next to the launcher script, alongside the
``voices/`` reference bank, ``model/`` weights, and ``licenses/``, and zips the whole tree.

Run via ``pyinstaller packaging/zonos-engine.spec`` from the ``engine-zonos`` directory (the build
script ``packaging/build_bundle.py`` does this). ``--onedir`` (the default for a spec) is used, not
``--onefile``, so the large CUDA runtime libraries are not unpacked to a temp dir on every launch:
the plugin already extracts the bundle to a stable directory.

DEPENDENCY-COLLECTION RECIPE: this spec does NOT hand-roll its ``collect_all`` / torch-data-only
recipe. It imports ``packaging/_collect.py`` and calls ``collect_engine_deps()``. The frozen-import
smoke (``smoke-import.spec``) imports the SAME function, so the smoke and the shipped bundle freeze
the same module graph by construction and cannot drift apart. Change the recipe in ``_collect.py``
once and both are updated; the smoke then re-proves it on a CPU runner before any release build runs.
"""

import os
import sys

block_cipher = None

# Repo paths are relative to the engine-zonos dir PyInstaller is invoked from.
HERE = os.path.abspath(os.getcwd())

# Make packaging/_collect.py importable. SPECPATH is the directory of this spec (packaging/), which
# PyInstaller injects into the spec's namespace; fall back to HERE/packaging for plain linting.
_PACKAGING_DIR = globals().get("SPECPATH") or os.path.join(HERE, "packaging")
if _PACKAGING_DIR not in sys.path:
    sys.path.insert(0, _PACKAGING_DIR)
# Freeze the top-level runner, NOT zonos_engine/engine.py directly: PyInstaller runs the entry as
# __main__ with no package context, so freezing engine.py directly breaks its package-relative
# imports ("attempted relative import with no known parent package"). zonos_engine_cli.py imports
# the package by absolute name, so the package's internal relative imports resolve in the bundle.
ENTRY = os.path.join(HERE, "zonos_engine_cli.py")

# The engine package's own hidden imports. The torch + zonos + phonemizer + numpy collection comes
# from the SHARED recipe (_collect.collect_engine_deps) below, so this spec and the smoke spec freeze
# the same module graph by construction. See _collect.py for the torch-data-only rationale (issue #77).
hiddenimports = [
    "zonos_engine",
    "zonos_engine.protocol",
    "zonos_engine.voices",
    "zonos_engine.emotion",
    "zonos_engine.synthesizer",
    "zonos",
    "zonos.model",
    "zonos.conditioning",
    "phonemizer",
]

from _collect import collect_engine_deps  # noqa: E402 - import after sys.path is primed above

# Shared collection recipe: collect_all over zonos/phonemizer/numpy + torch/torchaudio data-only.
# Identical call in smoke-import.spec, so the smoke cannot validate a different bundle than ships.
_datas, _binaries, _hidden = collect_engine_deps()
datas = _datas
binaries = _binaries
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
    name="zonos-engine",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=True,  # stdout/stderr are the engine's protocol + log channels.
)

coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=False,
    name="zonos-engine",
)
