# Plugin Hub listing

How **Voiced Dialogue** (internal name `voiced-dialogue`) is listed on the official
[RuneLite Plugin Hub](https://github.com/runelite/plugin-hub), and how a new version
reaches users. This is maintainer reference; none of it is part of the normal plugin build.

## How the Hub consumes this repo

The Hub does not host the plugin jar. It hosts a one-file *commit descriptor* per plugin
(`plugins/voiced-dialogue` in `runelite/plugin-hub`) containing a `repository=` pointing at
this repo and a `commit=` pinning the release commit to build. The Hub packager clones this
repo at that exact commit, builds the jar from `src/main` per the `build=standard` build
type, reads `runelite-plugin.properties` for the display metadata (`displayName`, `author`,
`support`, `description`, `tags`, `version`), and reads the descriptor for the listing
`warning=`/`authors=`.

The split matters:

| Field | Lives in | Why |
|-------|----------|-----|
| `displayName`, `author`, `support`, `description`, `tags`, `version`, `build` | `runelite-plugin.properties` (this repo) | Read from the repo at the pinned commit. `build=standard` tells the packager to build `src/main` with its own Gradle setup. |
| `repository`, `commit` | `plugins/voiced-dialogue` descriptor (runelite/plugin-hub) | The only required descriptor fields. |
| `warning`, `authors`, `jarSizeLimitMiB` | `plugins/voiced-dialogue` descriptor (runelite/plugin-hub) | The packager reads these from the descriptor, not from the properties file. A `warning=` in `runelite-plugin.properties` is an unused prop and never reaches the user. |

## The off-machine-data disclosure

The descriptor's `warning=` line carries the disclosure that dialogue text leaves the
machine, covering both providers. This is the canonical copy of that text:

> This plugin sends the NPC and player dialogue text it voices to your chosen provider,
> Google AI Studio or OpenRouter (third-party services not controlled or verified by the
> RuneLite developers), over HTTPS, using your API key, to synthesize speech.

How the repo satisfies the Hub's rules behind that disclosure (injected HTTP client, key
handling, no bundled binaries, and so on) is recorded in
[`hub-compliance-checklist.md`](hub-compliance-checklist.md).

## Shipping a new version

The Hub serves whatever commit the descriptor pins, so an update is two steps:

1. **Cut the release.** Dispatch the `Release` workflow (Actions tab -> "Run workflow")
   with the `bump` (`patch`/`minor`/`major`) and the `release_type`. It computes the next
   version from the latest `v*` tag, writes it into `runelite-plugin.properties`, tags the
   commit `v<version>`, and publishes the GitHub Release with the plugin jars. Version
   numbers are never edited by hand. Then copy the tag's commit sha:

   ```bash
   git fetch --tags
   git rev-parse v<version>   # copy the full 40-char sha
   ```

2. **Repoint the descriptor.** On a fresh branch off `upstream/master` in a fork of
   `runelite/plugin-hub`, update only `commit=` in `plugins/voiced-dialogue` to that sha and
   open a small PR against `runelite/plugin-hub`.

The PR runs the Hub's build plus a `RuneLite Plugin Hub Checks` automated audit. A green
check on both means it built and passed. If `Hub Checks` requests changes, fix them in this
repo, cut a fresh release, update `commit=` on the same PR, and push again (keep everything
in one PR).

## What the listing depends on

These must stay true for the Hub build to keep working:

- The repository is public and `LICENSE` exists at the repo root (MIT).
- `runelite-plugin.properties` declares `build=standard` and carries real, non-placeholder
  metadata. Its `version` is written by the `Release` workflow onto the tagged commit.
- The jar stays clean and small: no native libraries, no model, well under the Hub's 10 MiB
  limit (the built jar is ~362 KiB, mostly the bundled `npc-voices.json` table).
- `src/main` compiles under **Java 11**. `build=standard` replaces this repo's
  `build.gradle` with the Hub's, which hard-sets `options.release=11`, so any Java 12+
  syntax or API in main sources fails the Hub build. Our own `compileJava` pins
  `options.release=11` to catch this locally; keep it that way. Tests are unaffected (the
  Hub never builds them).
