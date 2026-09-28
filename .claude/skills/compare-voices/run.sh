#!/usr/bin/env bash
# Renders every case in cases.json through a baseline ref and through the current
# working tree, then builds a side-by-side QA sheet. Usage:
#   run.sh <baseline-ref> <out-dir> [openrouter|aistudio]
# Set VOICE_QA_ONLY to a regular expression to render only the cases whose key or group matches.
# Set VOICE_QA_CASES to a cases file outside the repo to render a focused set instead of cases.json.
# Keys come from $VOICE_QA_KEY_FILE, else the first RuneLite profile holding an OpenRouter key.
set -euo pipefail

BASE_REF=${1:?baseline ref, e.g. origin/main}
OUT=$(mkdir -p "${2:?output directory}" && cd "$2" && pwd)
PROVIDER=${3:-openrouter}
SKILL=$(cd "$(dirname "$0")" && pwd)
CASES=$(cd "$(dirname "${VOICE_QA_CASES:-$SKILL/cases.json}")" && pwd)/$(basename "${VOICE_QA_CASES:-cases.json}")
REPO=$(git -C "$SKILL" rev-parse --show-toplevel)
HARNESS=src/test/java/com/grahambartley/runelite/voiced/dialogue/ClipHarnessTest.java
BASELINE="$OUT/baseline"

KEY_FILE=${VOICE_QA_KEY_FILE:-}
if [ -z "$KEY_FILE" ]; then
  KEY_FILE=$(grep -l "^voicedDialogue.openRouterApiKey=" "$HOME"/.runelite/profiles2/*.properties | head -1 || true)
fi
[ -f "$KEY_FILE" ] || { echo "No key file found; set VOICE_QA_KEY_FILE" >&2; exit 1; }

cleanup() {
  rm -f "$REPO/$HARNESS" "$REPO/clip-harness.json"
  if [ -d "$BASELINE" ]; then
    git -C "$REPO" worktree remove --force "$BASELINE" || true
  fi
}
trap cleanup EXIT

git -C "$REPO" fetch -q origin || true
git -C "$REPO" worktree add -q --detach "$BASELINE" "$BASE_REF"

render() {
  local checkout=$1 label=$2
  cp "$SKILL/ClipHarnessTest.java" "$checkout/$HARNESS"
  python3 - "$CASES" "$OUT/$label" "$KEY_FILE" "$PROVIDER" "${PLAYER_ACCENT:-}" "${VOICE_QA_ONLY:-}" > "$checkout/clip-harness.json" <<'PY'
import json, re, sys
cases, out, keys, provider, accent, only = sys.argv[1:]
selected = [c for c in json.load(open(cases))["cases"]
            if not only or re.search(only, c["key"]) or re.search(only, c["group"])]
job = {"mode": "render", "outDir": out, "keyFile": keys, "provider": provider,
       "cases": selected}
if accent:
    job["playerAccent"] = accent
print(json.dumps(job))
PY
  (cd "$checkout" && ./gradlew test --rerun --tests '*ClipHarnessTest' -q)
  rm -f "$checkout/$HARNESS" "$checkout/clip-harness.json"
}

render "$BASELINE" before
render "$REPO" after
python3 "$SKILL/sheet.py" "$CASES" "$OUT" "$BASE_REF" "$(git -C "$REPO" rev-parse --abbrev-ref HEAD) (working tree)" "$PROVIDER"
