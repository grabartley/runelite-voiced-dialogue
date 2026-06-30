# Engine Pipeline

How the plugin jar and the external TTS engine relate, how each is built and shipped, and the runbook for cutting a release. The `README.md` covers what the plugin does for a player; this doc covers the build/release plumbing for maintainers and contributors.

## Two artifacts, two channels

The project produces two artifacts. They are built and released **together** under one version tag, but reach users through two channels.

| Artifact | What it is | Built/tested by | Published by |
|----------|------------|-----------------|--------------|
| **Plugin jar** | Pure-JVM RuneLite plugin. Ships with no engine binary and no voice model. The Hub builds the thin jar from source; the deploy also builds a standalone `*-all.jar` shadow jar (runnable via `TTSDialoguePluginRunner`). | `.github/workflows/cicd.yml` on every PR (thin jar + shadow jar) | The **RuneLite Plugin Hub** builds the thin jar from source at a tagged commit. The shadow jar is attached to the GitHub Release. |
| **Engine bundles** | Self-contained per-OS engine processes (jlink runtime + native libs + model + licenses). | `.github/workflows/cicd.yml` matrix on the manual deploy | This repo's **GitHub Releases**, in the same release as the shadow jar. |

The thin jar is what a user installs from the Hub. The engine bundle is what that jar downloads at runtime. The shadow jar and the engine bundles ride one GitHub Release under one `v<semver>` tag, and the manifest below is the contract that binds a jar build to the engine bundles in that release.

The local backend is served by a single engine family:

| Engine | Backend | Runtime | Bundle contents | Built by | Manifest |
|--------|---------|---------|-----------------|----------|----------|
| **Kokoro** (CPU) | `local-kokoro` (default `LOCAL`) | jlink JVM + sherpa-onnx native libs (ONNX) | engine jars, native libs, jlink runtime, Kokoro model, licenses | `cicd.yml` deploy (`engine-kokoro/` Gradle module) | `engine-manifest.json` |

The engine speaks the `--stdio` wire protocol (`ExternalEngineClient` drives it). Kokoro is a JVM engine in the `engine-kokoro/` Gradle subproject. (The emotional Cloud backend is a separate HTTP path with no external engine bundle.)

## CI vs deploy

`cicd.yml` is the only plugin workflow. It runs in two modes off the trigger.

### Pull requests — the build/test gate

- Trigger: `on: pull_request` (to `main`).
- Validates the Gradle wrapper, runs `./gradlew spotlessCheck`, then `./gradlew build`, which compiles the plugin **and** the `:engine-kokoro` module and runs the full test suite. That includes the engine's `--stdio` framing/manifest conformance tests (`EngineConformanceTest`).
- Publishes the JUnit report, then builds the standalone shadow jar (`./gradlew shadowJar`) and uploads it as a downloadable CI artifact.
- It deliberately **never** builds the cross-platform engine bundles, signs, notarizes, tags, or publishes anything.

### `workflow_dispatch` — the manual deploy (one tagged release)

- Trigger: `on: workflow_dispatch` **only**. There is intentionally no `push`/tag trigger. Inputs are a `bump` (`patch`/`minor`/`major`, default `patch`) and a `release_type` (`alpha`/`beta`/`stable`, default `alpha`). `alpha`/`beta` publish a prerelease; `stable` publishes a full release.
- The `plugin` job computes the next semver from the latest `v*` tag and the `bump` input, runs the same Spotless + test gate, builds the release shadow jar (`./gradlew shadowJar -PreleaseVersion=<semver>`), and exposes the version to the rest of the run. A cheap test failure here fast-fails before the expensive engine matrix runs.
- The `engine` matrix job produces the three Kokoro bundles, each on a runner matching its OS/arch so the jlink runtime and native libs are native to the target, all versioned to the same `v<semver>`:

  | Target | Runner | Archive |
  |--------|--------|---------|
  | `linux-x64` | `ubuntu-latest` | `.tar.gz` |
  | `win-x64` | `windows-latest` | `.zip` |
  | `osx-aarch64` | `macos-14` | `.tar.gz` |

  Each bundle carries the engine application jar, the per-target sherpa-onnx native libraries, a self-contained `jlink` Java runtime (so end users need no JDK), the Kokoro model (`kokoro-multi-lang-v1_0`, downloaded from the sherpa-onnx model release and normalized to a `model/` dir), and the Apache-2.0 attribution files under `licenses/`.
- Per-target validation runs before any signing: the `--stdio` conformance test runs on the Linux bundle (`EngineConformanceTest`, asserting the full frame round-trips on a real built engine), and every target runs a native `--selftest` (synthesize a fixed phrase, report sample rate + sample count). Self-test runs before signing so a self-test failure fast-fails cheaply and a signed bundle always implies "passed self-test".
- Optional, secret-gated code-signing/notarization (see the table below). With no secrets the bundles ship **unsigned** and the deploy still completes.
- Each bundle is sha256'd. The `publish` job gathers the shadow jar and every engine target, regenerates `engine-manifest.json` via `engine-kokoro/scripts/generate_manifest.py`, generates a changelog from the GitHub compare notes, creates the `v<semver>` tag, publishes a single GitHub Release carrying the shadow jar plus the bundles + their `.sha256` files, and opens an auto-PR to commit the refreshed manifest into the plugin resources.
- The publish step is re-runnable: re-running for an existing tag refreshes that release's assets.

## The manifest is the glue

The plugin jar ships tiny: no engine binary, no voice model. `src/main/resources/engine-manifest.json` is the small JSON resource that binds the jar to a published engine release. Its shape is flat and stable: a `version`, an `engine` name, and an `artifacts` map keyed by platform id (`osx-aarch64`, `linux-x64`, `win-x64`), each entry carrying `url`, `sha256`, `size`, `signed`, and `launcher`.

A platform entry carries `url`, `sha256`, `size`, `signed`, and `launcher`.

At runtime `EngineInstaller` (`src/main/java/com/grahambartley/synthesis/engine/EngineInstaller.java`) reads the bundled manifest via `getResourceAsStream` and resolves the entry for the current OS/arch. It downloads the bundle from its `url`, verifies the `sha256`, and extracts it under `~/.runelite/tts-dialogue/engines/<engine>-<version>/`. On macOS it then clears the `com.apple.quarantine` extended attribute on the extracted files so Gatekeeper does not block an unsigned engine. The plugin runs the extracted launcher as the external `--stdio` process and reuses it on later runs (the install is idempotent: an already-extracted launcher is reused without re-downloading). Any download failure or hash mismatch fails cleanly to "backend unavailable" with no partial install — never a crash, never a game-thread block.

If the manifest entry has an empty `url`/`sha256` (the dev placeholder), the installer treats it as "no engine published yet" — not an error, just nothing to install. So merging the auto-generated manifest after a release is what flips the plugin from "no engine" to "downloads the real bundle".

## Release runbook (correct order)

Releases are **never automatic**. Cut one in this order:

1. **Dispatch `CI/CD`** from the Actions tab ("CI/CD" -> "Run workflow"), choosing the `bump` (`patch`/`minor`/`major`) and `release_type` (`alpha`/`beta`/`stable`). One run computes the next `v<semver>`, builds and validates the shadow jar and the three Kokoro bundles, signs the bundles if secrets are present, tags the release, publishes a single GitHub Release carrying the shadow jar + the bundles, regenerates `engine-manifest.json`, and opens an auto-PR with the updated manifest.
2. **Merge the manifest auto-PR** so the bundled manifest in the plugin points at the real, published engine bundles (real `url` + `sha256` per platform) instead of the dev placeholders.
3. **Submit/update the plugin in `runelite/plugin-hub`** at the tagged commit (see issue #31) so the Hub builds the thin jar from source at that commit and serves it. The Hub jar is published by the Hub, not by this repo; the shadow jar on the Release is the standalone/sideload artifact.
4. **Users install from the Hub.** On first use of the local voice, the jar reads the merged manifest, downloads the per-OS engine bundle from the Release, verifies its sha256, and runs it.

### Signing secrets

Signing is optional. Absent the secrets, the pipeline still completes and publishes **unsigned** bundles, and the macOS first-run fallback below covers Gatekeeper. Configure these repository secrets to enable signing/notarization:

| Secret(s) | Enables |
|-----------|---------|
| `APPLE_ID`, `APPLE_TEAM_ID`, `APPLE_APP_PASSWORD`, `MACOS_CERT_P12`, `MACOS_CERT_PASSWORD` | macOS code-signing + notarization. Codesign is gated on `APPLE_TEAM_ID` + `MACOS_CERT_P12`; notarization additionally needs `APPLE_ID` + `APPLE_APP_PASSWORD`. A bundle is only marked `"signed": true` in the manifest once both codesign and notarize succeed. |
| `WINDOWS_CERT_PFX`, `WINDOWS_CERT_PASSWORD` | Windows Authenticode signing (`signtool`). |

### macOS first-run fallback (unsigned bundles)

If a macOS bundle is unsigned and un-notarized, Gatekeeper blocks it on first launch. Two paths recover it:

- The plugin's `EngineInstaller` clears the `com.apple.quarantine` extended attribute on the extracted engine after download, which covers the common case automatically.
- If a launch is still blocked, **right-click the `kokoro-engine` launcher in Finder and choose Open**, then confirm. macOS remembers the approval for later runs.

Windows SmartScreen may warn on an unsigned bundle; choose **More info -> Run anyway**.

## Versioning

The plugin and engine ship under **one version**. A single `v<semver>` tag carries both the shadow jar and the engine bundles, and the bundled manifest in that commit points at the engine bundles in the same release. To cut a new version, dispatch the `CI/CD` deploy with the desired `bump`; it rebuilds and republishes both the jar and the engine bundles together, then opens the manifest auto-PR. Re-tag the plugin commit submitted to the Hub at that release.
