---
name: pr
description: Create a PR for the runelite-voiced-dialogue repo using a git worktree branched from latest main. Use for final branch, commit, push, and PR flow.
---

# PR

Use this skill for branch strategy, final validation, commit, push, and PR creation.
This skill is used directly by humans and also as a handoff step from the build skill.

## Workflow

1. Stage: `git add <files>`
2. Format: `./gradlew spotlessApply`
3. Test: `./gradlew test`
4. Build: `./gradlew clean build`
5. Commit: `git commit -m "<type>: <short lowercase descriptive present tense message>"`
- Types: feat, fix, refactor, test, docs, chore
6. Push: `git push origin <branch-name>`
7. Create PR with a body file to preserve markdown formatting and avoid shell interpolation issues:
	- Write body markdown to a temp file (example: `.claude/tmp/pr-body.md`) and include real newlines.
	- Create PR: `gh pr create --repo grabartley/runelite-voiced-dialogue --base main --head <branch-name> --title "<title>" --body-file .claude/tmp/pr-body.md`
	- If updating an existing PR body, use: `gh pr edit <pr-number> --repo grabartley/runelite-voiced-dialogue --body-file .claude/tmp/pr-body.md`
	- Title: `<type>: <description>` (same style as commit message)
	- Body: lead with concise prose describing the FINAL STATE of the branch as it differs from `main`. Write in present tense as if the change has already landed. Group related behaviour into a few tight paragraphs. Reference specific files inline only when the path is essential context; otherwise leave file enumeration to the diff.
	- Prose is the backbone of the description, but structured elements like tables are encouraged when they explain a complex part of the change more clearly than a paragraph would. Reach for a table to lay out things like a status-transition matrix, config option to behaviour mappings, before-and-after behaviour for several cases, or option-to-default pairs. Keep the prose carrying the narrative and let the table clarify one dense piece.
	- The goal of the next rule is to describe what the merged code does under `main`, not what changed between revisions on this branch. Treat it as a tone test, not a literal word blocklist: these verbs are fine in genuine present-tense final-state copy (for example "the plugin updates the voice cache on dialogue start"). Only rewrite when a sentence describes the journey instead of the destination.
	- Journey phrasing to avoid when it describes how the branch evolved rather than the final state: "no longer", "now does", "restored", "refreshed", "renamed from", "previously", "fixed", "updated", "moved from X to Y", "added a", "the bug where". If a sentence describes how the branch differs from an earlier point on the same branch instead of describing what the merged code does, rewrite or delete it. Anything you addressed mid-development that has no observable effect vs `main` does not belong in the body.
	- Never enumerate file changes, classes being added, or method renames, in prose, lists, or tables. Reviewers see that in the Files Changed tab already. Tables are for explaining behaviour and concepts, not for cataloguing the diff.
	- Never include automated-test breakdowns ("X tests pass", "Y new tests added"). CI runs the suite. At most, one sentence on the *kinds* of tests added (unit tests for a voice manager, etc.) when that's actually useful context.
	- Keep it concise. Do not waffle. If a paragraph can be cut without losing reviewer-relevant signal, cut it.
	- Attach screenshots whenever the change has a visible surface (plugin config panel, in-game dialogue, RuneLite overlay). Capture the relevant state in the dev client or wherever the change is visible, and either drag the file into the GitHub PR description after creation or upload it through the GitHub web UI; reference the resulting `user-attachments` URL in the body next to the paragraph it illustrates. Skip screenshots only when the change has no rendered output.
	- Wrap class names, commands, and identifiers in backticks inside the markdown file, not inline shell args.
	- Always include a closing reference like `Closes #<issue-number>` so the PR Development section is linked to the issue being worked on.
8. After merge, clean up: `cd ../runelite-voiced-dialogue && git worktree remove ./.claude/worktrees/tts-<branch-name>`

## Conventions

- Always branch from latest `main`, never from other branches
- Use a fresh worktree per PR, don't reuse worktrees across branches
- Worktree path: `./.claude/worktrees/tts-<branch-name>` (sibling directory)
- Branch names: kebab-case (e.g. `fix-voice-fallback`, `add-static-npc-map`)
- Commit messages: `<type>: <lowercase description>`, no period at end
- Types: feat, fix, refactor, test, docs, chore
- PR descriptions: prose-led, present-tense description of the final state vs `main`. Tables and other structured elements are encouraged for explaining complex parts more clearly than prose alone. No journey language ("no longer", "now", "restored", "fixed", etc.). Never enumerate file changes in any form. No automated-test pass-counts. Cut anything that doesn't help the reviewer.
- Attach screenshots for any change with a visible surface, drag-uploaded into the GitHub PR description so the body links the `user-attachments` URL beside the paragraph it illustrates.
- PR titles and descriptions must be written as public-facing text, since this repo is public and anyone can view them
- Always link the PR Development section to the active issue via `Closes #<issue-number>` in the PR body
- Pre-commit must complete successfully: format, test, build
- Run `./gradlew spotlessApply` before staging so CI `spotlessCheck` stays green
- No emoji in commit messages or PR titles

## Related Skills

- worktree, used first for fresh branch and isolated directory setup

## Build Skill Integration

- If invoked after build work, confirm the linked issue is in `QA testing` before final handoff.
- Do not move issue to `Done`, that is reserved for human QA completion.
