---
name: publish-version
description: Publish a new plugin version end to end. Dispatches the release workflow in this repo, waits for it, resolves the released tag's commit SHA, then opens the follow-up PR in runelite/plugin-hub that repoints the Hub at the new release commit. Use when asked to release, publish, ship, or cut a new version of the plugin.
---

# Publish Version

Use this skill to release a new plugin version. It runs two steps as one guided flow:

1. Dispatch `.github/workflows/release.yml` in `grabartley/runelite-voiced-dialogue`, which computes the next semver, pins it into `runelite-plugin.properties` on a detached release commit, pushes the `v<version>` tag, and publishes a GitHub Release.
2. Open a PR against `runelite/plugin-hub` that updates only the `commit=` line in `plugins/voiced-dialogue` to the new release tag's SHA, so the Hub builds the new version.

This skill only opens the Hub PR. Merging it is done by the Plugin Hub maintainers.

## Critical Rules

1. Never guess `bump` or `release_type`. Both are required release inputs; ask the user and refuse to proceed until you have both.
2. Do not edit `.github/workflows/release.yml`. The skill dispatches it unchanged.
3. The Hub must be pinned to the tag's commit, not a branch HEAD. The release commit is detached and reachable only through the `v<version>` tag; resolving `main` gives the wrong SHA.
4. The Hub PR diff must touch only the `commit=` line of `plugins/voiced-dialogue`. Leave `repository=`, `authors=`, and `warning=` untouched.
5. If the release run fails mid-way, a dangling `v<version>` tag may already exist. Delete it before re-dispatching (see Recovery), or the re-run's tag push collides.
6. Everything you write here is public (release notes, Hub PR title and body). No local paths, private notes, or machine-specific details.

## Repo Constants

- This repo: `grabartley/runelite-voiced-dialogue`
- Release workflow: `.github/workflows/release.yml` (`workflow_dispatch`, inputs `bump` and `release_type`)
- Hub upstream: `runelite/plugin-hub`, base branch `master`
- Hub fork: `grabartley/plugin-hub`
- Hub manifest: `plugins/voiced-dialogue` (the `commit=` line is the only field this skill changes)
- Hub PR branch: `voiced-dialogue-v<version>`
- Hub PR title: `update voiced-dialogue`

## Workflow

### 1. Collect release metadata

Ask the user for, and do not proceed without:

- `bump`: one of `patch`, `minor`, `major`.
- `release_type`: one of `alpha`, `beta`, `stable` (anything other than `stable` publishes as a prerelease).

Also ask what, if anything, they want highlighted in the Hub PR body beyond the auto-generated release notes (a headline feature, a time-sensitive reason to merge, etc.).

Optionally preview the version the workflow will compute so the user can sanity-check it. It bumps the highest existing `v*` tag by semver:

```bash
gh api repos/grabartley/runelite-voiced-dialogue/tags --jq '.[].name' \
  | grep '^v' | sort -V | tail -n1
```

The workflow, not this skill, is the source of truth for the final version.

### 2. Dispatch the release and wait

```bash
gh workflow run release.yml \
  --repo grabartley/runelite-voiced-dialogue \
  -f bump=<bump> -f release_type=<release_type>
```

The dispatch returns no run id, so resolve the run just started, then watch it:

```bash
# give GitHub a moment to register the run, then grab the newest release-workflow run id
RUN_ID=$(gh run list --repo grabartley/runelite-voiced-dialogue \
  --workflow release.yml --limit 1 --json databaseId --jq '.[0].databaseId')

gh run watch "$RUN_ID" --repo grabartley/runelite-voiced-dialogue --exit-status
```

`--exit-status` makes the command fail if the run fails. On failure, surface the failing step:

```bash
gh run view "$RUN_ID" --repo grabartley/runelite-voiced-dialogue --log-failed
```

Report the failing step, follow Recovery if a tag was pushed, and stop. Do not open a Hub PR for a failed release.

### 3. Resolve the released SHA

On success, determine the new version from the run (or the highest tag now present), then resolve the commit the tag points at, not `main`:

```bash
VERSION=$(gh api repos/grabartley/runelite-voiced-dialogue/tags --jq '.[].name' \
  | grep '^v' | sort -V | tail -n1 | sed 's/^v//')

# The tag SHA: the detached release commit carrying the pinned runelite-plugin.properties.
RELEASE_SHA=$(gh api repos/grabartley/runelite-voiced-dialogue/git/refs/tags/v$VERSION \
  --jq '.object.sha')

# Annotated tags resolve one level deeper; if the above is a tag object, dereference it:
OBJ_TYPE=$(gh api repos/grabartley/runelite-voiced-dialogue/git/refs/tags/v$VERSION --jq '.object.type')
if [ "$OBJ_TYPE" = "tag" ]; then
  RELEASE_SHA=$(gh api repos/grabartley/runelite-voiced-dialogue/git/tags/$RELEASE_SHA --jq '.object.sha')
fi
```

Verify the GitHub Release exists for the tag before continuing:

```bash
gh release view "v$VERSION" --repo grabartley/runelite-voiced-dialogue \
  --json tagName,isPrerelease,url
```

Sanity-check that `RELEASE_SHA` is the release commit and not `main`'s HEAD (they differ whenever the version bump produced a commit):

```bash
gh api repos/grabartley/runelite-voiced-dialogue/commits/$RELEASE_SHA \
  --jq '.commit.message'   # expect "chore: release v<version>"
```

### 4. Open the plugin-hub PR

Sync the fork's `master` with upstream so the branch is cut from current upstream:

```bash
gh repo sync grabartley/plugin-hub --source runelite/plugin-hub --branch master
```

Work in a throwaway clone of the fork (use the session scratchpad, never the plugin repo's worktree):

```bash
git clone https://github.com/grabartley/plugin-hub.git "$SCRATCH/plugin-hub"
cd "$SCRATCH/plugin-hub"
git checkout -b "voiced-dialogue-v$VERSION" origin/master
```

Edit only the `commit=` line, then confirm the diff is exactly that one line:

```bash
sed -i -E "s/^commit=.*/commit=$RELEASE_SHA/" plugins/voiced-dialogue
git diff plugins/voiced-dialogue    # must show only the commit= line changing
```

If `git diff` shows anything other than the single `commit=` line, stop and fix it. `repository=`, `authors=`, and `warning=` must be byte-for-byte unchanged.

Commit, push, and open the PR against upstream `master`:

```bash
git commit -am "update voiced-dialogue"
git push origin "voiced-dialogue-v$VERSION"

gh pr create \
  --repo runelite/plugin-hub \
  --base master \
  --head "grabartley:voiced-dialogue-v$VERSION" \
  --title "update voiced-dialogue" \
  --body-file "$SCRATCH/hub-pr-body.md"
```

### 5. Hub PR body

Write the body to a file first (real newlines, public-facing). Model it on the prior update PRs
([runelite/plugin-hub#13363](https://github.com/runelite/plugin-hub/pull/13363),
[runelite/plugin-hub#13209](https://github.com/runelite/plugin-hub/pull/13209)):

- Opening line linking the release tag and stating the pin, e.g.:
  `Updates voiced-dialogue to [v<version>](https://github.com/grabartley/runelite-voiced-dialogue/releases/tag/v<version>). The pinned commit is the ` `` `v<version>` `` ` release tag commit.`
- If the user flagged a time-sensitive reason to merge, a short bold `**Time-sensitive:**` paragraph.
- A `Highlights since v<previous>:` list of a few player-facing bullets, derived from the generated release notes plus anything the user supplied. Keep them user-focused, not a changelog dump. Pull the notes with:

```bash
gh release view "v$VERSION" --repo grabartley/runelite-voiced-dialogue --json body --jq '.body'
```

Keep the body concise and in the same voice as the prior PRs. Do not enumerate raw commit subjects; summarize into player-visible highlights.

### 6. Report back

Return to the user:

- New version and release type (prerelease or stable)
- Release URL
- Pinned `RELEASE_SHA`
- Hub PR URL

End here. The maintainers merge the Hub PR.

## Recovery

If the release run failed after the tag was pushed (any failure at or after the "Push release tag" step), the `v<version>` tag exists but the release may be incomplete. Delete it before re-dispatching:

```bash
gh api -X DELETE repos/grabartley/runelite-voiced-dialogue/git/refs/tags/v<version>
```

Then re-run from step 2. If the failure was before the tag push (formatting, build, or test gate), no tag exists; fix the underlying cause on `main` first, then re-dispatch.

## Related Skills

- `pr`, for the branch, commit, and PR conventions this skill follows.
- `worktree`, for isolated branch setup when the release itself needs code changes first (out of scope here; this skill assumes `main` is release-ready).
