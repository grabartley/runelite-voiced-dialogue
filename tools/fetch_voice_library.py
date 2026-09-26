#!/usr/bin/env python3
"""Snapshot the Gemini Extended Voice Library into tools/voice-library.json.

Offline tooling, not part of the plugin runtime. Lists every voice from
GET /v1beta/voices with a Google AI Studio key taken from the GEMINI_API_KEY
environment variable, keeps only the fields the generator reads, and writes them
sorted by id so a refresh produces a minimal diff. tools/generate_npc_voices.py
builds the bundled region voice pools from this snapshot.

Usage
-----
  GEMINI_API_KEY=... python3 tools/fetch_voice_library.py
"""

import json
import os
import sys
import urllib.parse
import urllib.request

ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/voices"
PAGE_SIZE = 1000
KEPT_FIELDS = ("id", "accent", "language_code", "region_code", "gender", "pitch")
DEFAULT_OUT = os.path.join("tools", "voice-library.json")


def fetch_all(api_key):
    voices = []
    token = ""
    while True:
        url = f"{ENDPOINT}?pageSize={PAGE_SIZE}"
        if token:
            url += "&pageToken=" + urllib.parse.quote(token)
        request = urllib.request.Request(url, headers={"x-goog-api-key": api_key})
        with urllib.request.urlopen(request, timeout=60) as response:
            page = json.load(response)
        voices.extend(page.get("voices", []))
        token = page.get("next_page_token") or page.get("nextPageToken") or ""
        if not token:
            return voices


def main():
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        sys.exit("Set GEMINI_API_KEY to a Google AI Studio key.")
    voices = fetch_all(api_key)
    trimmed = sorted(({k: v[k] for k in KEPT_FIELDS if k in v} for v in voices),
                     key=lambda v: v["id"])
    with open(DEFAULT_OUT, "w", encoding="utf-8") as fh:
        json.dump({"voices": trimmed}, fh, indent=1, ensure_ascii=False)
        fh.write("\n")
    print(f"Wrote {len(trimmed)} voices to {DEFAULT_OUT}", file=sys.stderr)


if __name__ == "__main__":
    main()
