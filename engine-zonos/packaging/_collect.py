# -*- coding: utf-8 -*-
"""Single source of truth for the Zonos engine's PyInstaller dependency-collection recipe.

WHY THIS MODULE EXISTS
----------------------
The frozen Zonos engine (``zonos-engine.spec``) and the frozen-import smoke test
(``smoke-import.spec``) MUST freeze the same module graph, or the smoke validates a *different*
bundle than the one shipped: green smoke, broken release. The earlier smoke (#99) hand-rolled a
stripped recipe (torch + numpy only, no zonos/phonemizer) and so could not see bugs that only the
full graph triggers. By funnelling both specs through this one function, the real bundle and the
smoke become **physically incapable of diverging** on the collection graph: any recipe change is a
change to this module and is exercised by both.

THE RECIPE
----------
* ``collect_all`` over zonos + phonemizer + numpy. These packages have NO dedicated PyInstaller hook
  that would duplicate their contents, so collecting them whole (code, data, binaries) is both safe
  and necessary for self-containment. numpy is collected explicitly because the frozen bundle needs
  numpy complete (``numpy._core`` and friends); relying on it arriving only transitively has left the
  frozen numpy incomplete in the past.

* torch + torchaudio get ONLY their non-binary data files + dist metadata, NEVER their binaries
  (issue #77). PyInstaller ships dedicated hooks for torch and torchaudio that already collect their
  compiled C-extensions (.pyd/.so) and the bundled CUDA runtime libs exactly once. Listing torch in
  hiddenimports AND running ``collect_all("torch")`` on top of those hooks bundles torch's native
  extension under two resolvable paths; at runtime CPython then raises "cannot load module more than
  once per process" the second time the extension's module-init runs, which made the GPU probe report
  no usable GPU on a real NVIDIA box. torch/torchaudio still travel with the exe -- pulled in
  transitively by zonos and the synthesizer, so their hooks fire and their binaries ship exactly once.

Outside a PyInstaller run ``collect_all`` is unavailable; the function then returns empty lists so the
specs (and this module) stay importable for linting/CI without a PyInstaller install.
"""

from __future__ import annotations

# Collected whole (code + data + binaries) via collect_all. No dedicated hook owns these, so
# collecting them in full is non-duplicating and required for a self-contained frozen bundle.
COLLECT_ALL_PACKAGES = ("zonos", "phonemizer", "numpy")

# Data + metadata ONLY -- never binaries. PyInstaller's built-in hooks own these packages' compiled
# extensions + CUDA runtime; duplicating those binaries is exactly what triggered the double-load
# (issue #77).
DATA_ONLY_PACKAGES = ("torch", "torchaudio")


def collect_engine_deps():
    """Return ``(datas, binaries, hiddenimports)`` for the Zonos engine's frozen module graph.

    Both ``zonos-engine.spec`` and ``smoke-import.spec`` call this so the smoke freezes the same
    graph as the shipped engine by construction. See the module docstring for the recipe rationale
    (notably the torch/torchaudio data-only handling that fixes issue #77).
    """
    datas = []
    binaries = []
    hiddenimports = []

    try:
        from PyInstaller.utils.hooks import (
            collect_all,
            collect_data_files,
            copy_metadata,
        )
    except Exception:  # pragma: no cover - only importable inside a PyInstaller run
        # collect_all is unavailable outside a PyInstaller build; return empties so the specs stay
        # importable for linting and the smoke spec can be parsed without PyInstaller installed.
        return datas, binaries, hiddenimports

    # zonos + phonemizer + numpy: collect everything (code, data, binaries). No dedicated hook would
    # duplicate their contents, so collect_all is safe and needed for self-containment.
    for pkg in COLLECT_ALL_PACKAGES:
        d, b, h = collect_all(pkg)
        datas += d
        binaries += b
        hiddenimports += h

    # torch + torchaudio: ONLY non-binary data files + dist metadata, never their binaries. The
    # built-in torch/torchaudio hooks own the compiled extensions + CUDA runtime; duplicating those
    # is exactly what triggered the double-load (issue #77). collect_data_files(...) returns data
    # (version files, configs) without the .pyd/.so the hook already collects.
    for pkg in DATA_ONLY_PACKAGES:
        datas += collect_data_files(pkg)
        try:
            datas += copy_metadata(pkg)
        except Exception:  # pragma: no cover - metadata is optional
            pass

    return datas, binaries, hiddenimports
