#!/usr/bin/env bash
# Builds an origin review sheet for every voiced NPC in one area. Usage:
#   run.sh <area name> <ethnicity key> <cache symbol regex> <out-dir> [excluded names...]
set -euo pipefail

AREA=${1:?area name, e.g. Karamja}
ETHNICITY=${2:?byEthnicity key, e.g. karamja}
SYMBOLS=${3:-}
OUT=$(mkdir -p "${4:?output directory}" && cd "$4" && pwd)
shift 4 || true
SKILL=$(cd "$(dirname "$0")" && pwd)
REPO=$(git -C "$SKILL" rev-parse --show-toplevel)
HARNESS=src/test/java/com/grahambartley/runelite/voiced/dialogue/ClipHarnessTest.java

KEY_FILE=${VOICE_QA_KEY_FILE:-}
if [ -z "$KEY_FILE" ]; then
  KEY_FILE=$(grep -l "^voicedDialogue.openRouterApiKey=" "$HOME"/.runelite/profiles2/*.properties | head -1 || true)
fi
[ -f "$KEY_FILE" ] || { echo "No key file found; set VOICE_QA_KEY_FILE" >&2; exit 1; }

cleanup() {
  rm -f "$REPO/$HARNESS" "$REPO/clip-harness.json"
}
trap cleanup EXIT

cd "$REPO"
python3 "$SKILL/area.py" collect --ethnicity "$ETHNICITY" --symbols "$SYMBOLS" --out "$OUT" --exclude "$@"
cp "$REPO/.claude/skills/compare-voices/ClipHarnessTest.java" "$REPO/$HARNESS"
python3 - "$OUT" "$KEY_FILE" > "$REPO/clip-harness.json" <<'PY'
import json, sys
out, keys = sys.argv[1:]
cases = json.load(open(f"{out}/probe.json"))["cases"]
print(json.dumps({"mode": "resolve", "outDir": f"{out}/resolved", "keyFile": keys,
                  "provider": "openrouter", "cases": cases}))
PY
./gradlew test --rerun --tests '*ClipHarnessTest' -q
python3 "$SKILL/area.py" build --area "$AREA" --out "$OUT"
echo "$OUT/site/origins.html"
