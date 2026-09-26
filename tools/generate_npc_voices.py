#!/usr/bin/env python3
"""Offline generator for the bundled NPC voice + profile lookup table.

This is one-time offline tooling. It is NOT part of the plugin's runtime path:
the plugin only reads the generated JSON resource and does in-memory map lookups
keyed by NPC id. Regenerate whenever you want to refresh coverage, then commit
the updated resource.

Data source: the Old School RuneScape Wiki (https://oldschool.runescape.wiki),
which is authoritative and current. Talkable NPCs transclude ``Template:Infobox
NPC`` (carrying ``race``, ``gender``, ``leagueRegion``, ``location`` and cache
``id``s); talkable creatures transclude ``Template:Infobox Monster`` (which
carries none of those, so their race comes from the page's categories). We map
each id -> {race, gender, ethnicity}. Race and gender come straight from the
wiki, so townsfolk get the correct gender (e.g. Cecilia is Female) and newly
released NPCs are covered as soon as the wiki documents them.

Pipeline
--------
  1. Enumerate every main-namespace page transcluding an NPC or Monster infobox.
  2. Fetch each page's lead wikitext and categories in batches.
  3. Parse every infobox: cache ids (grouped per version), gender (per version),
     race, leagueRegion and location.
  4. Map race onto a voice bucket (from the infobox race, else the page's
     categories), normalise gender (paired per version), and map leagueRegion
     (+ location/categories for the Menaphite cities) onto an ethnicity key.
  5. Cross-reference a full id -> name dump by name to cover variant ids the wiki
     pages do not list.
  6. Merge the hand-curated overrides on top (authoritative, always win).
  7. Embed tools/profiles.json under the ``profiles`` key and emit
     src/main/resources/npc-voices.json.

Usage
-----
  python3 tools/generate_npc_voices.py
  python3 tools/generate_npc_voices.py --limit 500   # quick partial run for testing
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

WIKI_API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "runelite-voiced-dialogue NPC table generator (contact: grabartley@gmail.com)"


MAPPING_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main",
                            "resources", "wiki-mapping.json")

with open(MAPPING_PATH, encoding="utf-8") as _mapping_file:
    MAPPING = json.load(_mapping_file)

# Talkable NPCs use Infobox NPC (carries race/gender/leagueRegion); talkable creatures use Infobox
# Monster (carries none of those), so for Monster pages race comes from the page's categories.
INFOBOX_TEMPLATES = MAPPING["infoboxTemplates"]

# Wiki page-category substring -> voice bucket, checked in order, first match wins. This is how
# Infobox Monster NPCs (trolls like Kob, ghosts, TzHaar, ...) get a race the infobox does not carry.
CATEGORY_RACE_RULES = [(r["keyword"], r["race"]) for r in MAPPING["categoryRaceRules"]]

DEFAULT_OUT = os.path.join("src", "main", "resources", "npc-voices.json")
DEFAULT_OVERRIDES = os.path.join("tools", "overrides.json")
DEFAULT_PROFILES = os.path.join("tools", "profiles.json")
# Full NPC id -> name dump, used only to cross-reference ids the wiki pages do not
# list (variants) onto wiki data by name. The wiki remains the source of truth.
DEFAULT_SUMMARY_URL = (
    "https://raw.githubusercontent.com/0xNeffarion/osrsreboxed-db/master/docs/npcs-summary.json"
)

VALID_RACES = set(MAPPING["races"])
VALID_GENDERS = {"Male", "Female"}
VALID_LIFE_STAGES = {"child"}
PROFILE_FIELDS = {"name", "accent", "style", "pace"}

# Gemini 3.8 speaks its input verbatim and takes delivery from a short speech_metadata.style
# string, so profile fields stay short phrases and carry no tags or prompt-block markers.
MAX_DIRECTION_LENGTH = {"accent": 80, "pace": 60}
FORBIDDEN_DIRECTION = re.compile(
    r"[\[\]<>]|audio\s*profile|director'?s\s*notes|transcript|word\s+for\s+word", re.IGNORECASE)

# Wiki race text -> the voice buckets (VoiceProfile), ordered, first hit wins. The rules, the
# category rules above and the league region map below are shared with the plugin's auto-learn
# lookup, which reads the same resource.
RACE_BUCKET_RULES = [(re.compile(r["pattern"], re.IGNORECASE), r["race"])
                     for r in MAPPING["raceRules"]]

DESERT = MAPPING["desert"]
MENAPHITE_HINT = re.compile(DESERT["hint"], re.IGNORECASE)
SINGLE_ETHNICITY = MAPPING["leagueRegionEthnicity"]


def ethnicity_key(league_region, location, categories=None):
    if not league_region:
        return None
    lr = league_region.strip()
    if "," in lr or "&" in lr:
        return None  # documented in several regions -> no single ethnicity
    key = lr.lower()
    if key == DESERT["leagueRegion"]:
        # Split the Egyptian Menaphite cities (Sophanem/Menaphos) out of the desert, by the NPC's
        # location text or its wiki categories (e.g. Category:Menaphites, Category:Sophanem).
        hint = " ".join([location or ""] + (categories or []))
        return DESERT["hinted"] if MENAPHITE_HINT.search(hint) else DESERT["default"]
    return SINGLE_ETHNICITY.get(key)


def bucket_for_race(race_text):
    """Bucket a raw infobox race value. A piped link is tried target first, then display text,
    so "[[Dwarf (race)|Dwarves]]" keeps bucketing on the target while a target the rules cannot
    read, such as "[[Dog_(disambiguation)|Dog]]", falls through to the display text."""
    if not race_text:
        return None  # no infobox race field; caller falls back to categories
    readings = link_readings(race_text)
    if not readings:
        return None  # the field cleaned away to nothing; caller falls back to categories
    for reading in readings:
        for regex, bucket in RACE_BUCKET_RULES:
            if regex.search(reading):
                return bucket
    return MAPPING["defaultRace"]


def normalise_gender(gender_text):
    if gender_text:
        g = gender_text.strip().lower()
        if g.startswith("f"):
            return MAPPING["femaleGender"]
        if g.startswith("m"):
            return MAPPING["defaultGender"]
    return MAPPING["defaultGender"]


def api_get(params):
    params = dict(params)
    params["format"] = "json"
    params["formatversion"] = "2"
    url = WIKI_API + "?" + urllib.parse.urlencode(params)
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as exc:  # noqa: BLE001 - tooling, retry then surface
            wait = 2 * (attempt + 1)
            print(f"  (api retry {attempt + 1} after {exc}; sleeping {wait}s)", file=sys.stderr)
            time.sleep(wait)
    raise RuntimeError(f"wiki API failed after retries: {url}")


def enumerate_npc_pages(limit=None):
    """All main-namespace page titles transcluding an NPC or Monster infobox, deduped."""
    seen = set()
    titles = []
    for template in INFOBOX_TEMPLATES:
        cont = None
        while True:
            params = {
                "action": "query",
                "list": "embeddedin",
                "eititle": template,
                "einamespace": "0",
                "eilimit": "500",
                "eifilterredir": "nonredirects",
            }
            if cont:
                params["eicontinue"] = cont
            data = api_get(params)
            for entry in data.get("query", {}).get("embeddedin", []):
                title = entry["title"]
                if title not in seen:
                    seen.add(title)
                    titles.append(title)
            if limit and len(titles) >= limit:
                return titles[:limit]
            cont = data.get("continue", {}).get("eicontinue")
            if not cont:
                break
            print(f"  enumerated {len(titles)} pages ...", file=sys.stderr)
    return titles


def bucket_from_categories(categories):
    """Voice bucket inferred from a page's wiki categories, or None. Lets Infobox Monster NPCs
    (which carry no race field) still get a race, e.g. a page in Category:Trolls -> Troll."""
    joined = " ".join(categories).lower()
    for needle, bucket in CATEGORY_RACE_RULES:
        if needle in joined:
            return bucket
    return None


FIELD_RE = {
    "race": re.compile(r"\|\s*race\d*\s*=\s*([^\n]+)", re.IGNORECASE),
    "gender": re.compile(r"\|\s*gender\d*\s*=\s*([^\n]+)", re.IGNORECASE),
    "leagueRegion": re.compile(r"\|\s*leagueRegion\s*=\s*([^\n]+)", re.IGNORECASE),
    "location": re.compile(r"\|\s*location\s*=\s*([^\n]+)", re.IGNORECASE),
}
ID_RE = re.compile(r"\|\s*id\d*\s*=\s*([^\n]+)", re.IGNORECASE)
LINK_RE = re.compile(r"\[\[([^\[\]]*)\]\]")


def field_text(value):
    """The one field out of a captured line. FIELD_RE captures to end of line so a piped link
    survives, so the next parameter on a single-line infobox is cut here instead, at the first
    "|", or at a link or template close that has nothing open, outside a link or a nested
    template."""
    depth = 0
    i = 0
    while i < len(value):
        token = value[i:i + 2]
        if token in ("[[", "{{"):
            depth += 1
            i += 2
        elif token in ("]]", "}}"):
            if depth == 0:
                return value[:i]
            depth -= 1
            i += 2
        elif value[i] == "|" and depth == 0:
            return value[:i]
        else:
            i += 1
    return value


def link_target(link_body):
    return link_body.split("|")[0]


def link_display(link_body):
    return link_body.split("|")[-1]


def clean_value(value, link=link_target):
    # Strip wiki markup, refs and templates so "[[Human]]" -> "Human", resolving each link to
    # the side the caller asks for.
    value = re.sub(r"<ref[^>]*>.*?</ref>", "", value, flags=re.IGNORECASE | re.DOTALL)
    value = re.sub(r"<[^>]+>", "", value)
    value = LINK_RE.sub(lambda m: link(m.group(1)), value)
    value = value.replace("[[", "").replace("]]", "")
    value = re.sub(r"\{\{[^}]*\}\}", "", value)
    return value.strip()


def link_readings(value):
    """The readings of a value in the order they should be tried: link targets, then display
    text when the two differ. Empty readings drop out, so a value that cleans away entirely
    leaves nothing to match."""
    target = clean_value(value)
    display = clean_value(value, link=link_display)
    readings = [target] if display == target else [target, display]
    return [reading for reading in readings if reading]


def field_texts(wikitext, pattern):
    """Every value of one field, in page order. The scan resumes at the end of the cut value
    rather than the end of the line, so a single-line infobox still yields every version."""
    values = []
    pos = 0
    while True:
        match = pattern.search(wikitext, pos)
        if not match:
            return values
        text = field_text(match.group(1))
        values.append(text)
        pos = match.start(1) + len(text)


def raw_field(wikitext, key):
    texts = field_texts(wikitext, FIELD_RE[key])
    return texts[0] if texts else None


def field_value(wikitext, key):
    raw = raw_field(wikitext, key)
    return clean_value(raw) if raw is not None else None


def parse_id_groups(wikitext):
    """One group of ids per |idN= line, preserving order. A switch-infobox page lists ids and
    genders as parallel per-version lines, so the i-th id group pairs with the i-th gender."""
    groups = []
    for raw in field_texts(wikitext, ID_RE):
        ids = [int(t) for t in re.split(r"[,\s]+", clean_value(raw)) if t.isdigit()]
        if ids:
            groups.append(ids)
    return groups


def parse_genders(wikitext):
    return [normalise_gender(clean_value(g))
            for g in field_texts(wikitext, FIELD_RE["gender"])]


def fetch_infoboxes(titles, batch=30):
    """Yield (title, lead-wikitext, [category titles]) for each page, in batches."""
    for i in range(0, len(titles), batch):
        chunk = titles[i:i + batch]
        data = api_get({
            "action": "query",
            "prop": "revisions|categories",
            "rvprop": "content",
            "rvslots": "main",
            "rvsection": "0",
            "cllimit": "500",
            "titles": "|".join(chunk),
        })
        for page in data.get("query", {}).get("pages", []):
            revs = page.get("revisions")
            if not revs:
                continue
            content = revs[0].get("slots", {}).get("main", {}).get("content", "")
            cats = [c.get("title", "") for c in page.get("categories", [])]
            yield page.get("title", ""), content, cats
        print(f"  parsed {min(i + batch, len(titles))}/{len(titles)} pages ...", file=sys.stderr)
        time.sleep(0.2)


def normalize_name(name):
    """Lower-cased, disambiguation-stripped name for cross-referencing the id dump."""
    if not name:
        return ""
    name = re.sub(r"\(.*?\)", "", name)
    return re.sub(r"\s+", " ", name).strip().lower()


def iter_summary(summary):
    """Yield (npc_id:int, name:str) from a full id -> name NPC dump."""
    records = summary.values() if isinstance(summary, dict) else summary
    for entry in records:
        if not isinstance(entry, dict):
            continue
        try:
            npc_id = int(entry.get("id"))
        except (TypeError, ValueError):
            continue
        name = entry.get("name") or ""
        if name and name.lower() != "null":
            yield npc_id, name


def fetch_json_url(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def build_table_from_wiki(limit=None):
    """Build the id -> {race, gender, ethnicity} table and a name -> entry map from the wiki."""
    titles = enumerate_npc_pages(limit=limit)
    print(f"Enumerated {len(titles)} NPC pages from the wiki", file=sys.stderr)
    table = {}
    name_map = {}
    pages_with_ids = 0
    for title, wikitext, categories in fetch_infoboxes(titles):
        groups = parse_id_groups(wikitext)
        genders = parse_genders(wikitext)
        # Infobox race when present (NPC pages); otherwise the page's categories (Monster pages).
        race = bucket_for_race(raw_field(wikitext, "race"))
        if race is None:
            race = bucket_from_categories(categories)
        race = race or MAPPING["defaultRace"]
        ethnicity = ethnicity_key(
            field_value(wikitext, "leagueRegion"), field_value(wikitext, "location"), categories)

        def build_entry(gender):
            entry = {"race": race, "gender": gender}
            if ethnicity:
                entry["ethnicity"] = ethnicity
            return entry

        # Gender can vary per version (e.g. male/female guard variants). When the page lists one
        # gender per id group, pair them; otherwise fall back to the first gender for every id.
        aligned = len(genders) == len(groups) and groups
        default_gender = genders[0] if genders else MAPPING["defaultGender"]

        # First page to claim a name wins, so the canonical NPC page beats a stray transclusion.
        key = normalize_name(title)
        if key and key not in name_map:
            name_map[key] = build_entry(default_gender)

        if groups:
            pages_with_ids += 1
            for index, group in enumerate(groups):
                entry = build_entry(genders[index] if aligned else default_gender)
                for npc_id in group:
                    table.setdefault(npc_id, entry)
    return table, len(titles), pages_with_ids, name_map


def fill_from_summary(table, name_map, summary):
    """Cover ids the wiki pages don't list by matching a full id -> name dump to the wiki
    data by name. Catches variant ids whose name still resolves to a documented NPC."""
    filled = 0
    for npc_id, name in iter_summary(summary):
        if npc_id in table:
            continue
        entry = name_map.get(normalize_name(name))
        if entry:
            table[npc_id] = entry
            filled += 1
    return filled


def apply_overrides(table, overrides):
    """Patch each override's fields over the wiki-inferred entry (not a wholesale replace).

    Per field, an override that names it wins; one that omits it inherits the wiki base; one
    that sets it to ``null`` clears it. So omitting ``ethnicity`` keeps the wiki-inferred accent
    (the common case), while ``"ethnicity": null`` drops a wrong one (a foreigner). The optional
    ``lifeStage`` marks a child ("child" is the only value). ``race`` and ``gender`` are optional too,
    but the post-merge entry must still carry both, else the wiki never supplied one and we raise
    rather than emit a malformed entry.
    """
    override_npcs = overrides.get("npcs", {})
    for key, entry in override_npcs.items():
        npc_id = int(key)
        merged = dict(table.get(npc_id, {}))  # copy: wiki entries are shared across sibling ids
        for field in ("race", "gender", "ethnicity", "lifeStage"):
            if field not in entry:
                continue  # absent: inherit the wiki-inferred value
            value = entry[field]
            if value is None:
                merged.pop(field, None)  # explicit null: clear the field
                continue
            if field == "race" and value not in VALID_RACES:
                raise ValueError(f"Override {key} has invalid race '{value}'")
            if field == "gender" and value not in VALID_GENDERS:
                raise ValueError(f"Override {key} has invalid gender '{value}'")
            if field == "lifeStage" and value not in VALID_LIFE_STAGES:
                raise ValueError(f"Override {key} has invalid lifeStage '{value}'")
            merged[field] = value
        missing = [f for f in ("race", "gender") if f not in merged]
        if missing:
            raise ValueError(
                f"Override {key} leaves {'/'.join(missing)} unset and no wiki base supplies "
                f"{'them' if len(missing) > 1 else 'it'}")
        table[npc_id] = merged
    return len(override_npcs)


def validate_profiles(profiles):
    if not isinstance(profiles, dict):
        raise ValueError("profiles.json must be a JSON object")
    default = profiles.get("default")
    if not isinstance(default, dict) or not PROFILE_FIELDS.issubset(default):
        raise ValueError(f"profiles.default must be complete with {sorted(PROFILE_FIELDS)}")
    for entry in (profiles.get("byCategory") or []):
        if not isinstance(entry, dict) or not entry.get("keywords"):
            raise ValueError(f"byCategory entry missing keywords: {entry!r}")
        life_stage = entry.get("lifeStage")
        if life_stage is not None and life_stage not in VALID_LIFE_STAGES:
            raise ValueError(f"byCategory entry has invalid lifeStage '{life_stage}': {entry!r}")
    for key in (profiles.get("byId") or {}):
        if key.startswith("_"):
            continue
        try:
            int(key)
        except (TypeError, ValueError):
            raise ValueError(f"byId key '{key}' is not a numeric NPC id")
    for where, layer in profile_layers(profiles):
        validate_directions(where, layer)
    return profiles


def profile_layers(profiles):
    for key in ("default", "player", "narrator"):
        if isinstance(profiles.get(key), dict):
            yield key, profiles[key]
    for section in ("byRace", "byEthnicity", "byId"):
        for key, layer in (profiles.get(section) or {}).items():
            if not key.startswith("_") and isinstance(layer, dict):
                yield f"{section}.{key}", layer
    for entry in (profiles.get("byCategory") or []):
        yield f"byCategory.{entry.get('id', '?')}", entry


def validate_directions(where, layer):
    for field in ("accent", "style", "pace"):
        value = layer.get(field)
        if value is None:
            continue
        if FORBIDDEN_DIRECTION.search(value):
            raise ValueError(f"{where}.{field} carries a tag or prompt marker: {value!r}")
        limit = MAX_DIRECTION_LENGTH.get(field)
        if limit is not None and len(value) > limit:
            raise ValueError(f"{where}.{field} is longer than {limit} characters: {value!r}")


def load_json(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--overrides", default=DEFAULT_OVERRIDES)
    parser.add_argument("--profiles", default=DEFAULT_PROFILES)
    parser.add_argument("--summary", default=DEFAULT_SUMMARY_URL,
                        help="Full id->name NPC dump (URL) for name cross-reference; '' to skip.")
    parser.add_argument("--out", default=DEFAULT_OUT)
    parser.add_argument("--limit", type=int, default=None,
                        help="Cap the number of NPC pages (for quick test runs).")
    parser.add_argument("--base", default=None,
                        help="Re-apply overrides/profiles onto an existing npc-voices.json offline, "
                             "skipping the wiki + summary fetch. Use for overrides/profiles-only "
                             "changes to get a minimal, deterministic diff with no wiki drift.")
    args = parser.parse_args()

    profiles = validate_profiles(load_json(args.profiles))
    overrides = load_json(args.overrides)

    if args.base:
        base = load_json(args.base)
        table = {int(npc_id): entry for npc_id, entry in base["npcs"].items()}
        base_meta = base.get("_meta", {})
        page_count = base_meta.get("npc_pages", 0)
        pages_with_ids = page_count
        name_matched = base_meta.get("name_matched_ids", 0)
        print(f"Seeded {len(table)} entries from {args.base} (offline; no wiki fetch)",
              file=sys.stderr)
    else:
        table, page_count, pages_with_ids, name_map = build_table_from_wiki(limit=args.limit)
        name_matched = 0
        if args.summary:
            try:
                summary = fetch_json_url(args.summary)
                name_matched = fill_from_summary(table, name_map, summary)
                print(f"  name cross-ref covered {name_matched} extra ids from the id dump",
                      file=sys.stderr)
            except Exception as exc:  # noqa: BLE001 - tooling, surface and continue
                print(f"  (skipping name cross-ref: {exc})", file=sys.stderr)
    override_count = apply_overrides(table, overrides)

    npcs = {str(npc_id): table[npc_id] for npc_id in sorted(table)}

    race_counts, gender_counts, ethnicity_counts, life_stage_counts = {}, {}, {}, {}
    for v in table.values():
        race_counts[v["race"]] = race_counts.get(v["race"], 0) + 1
        gender_counts[v["gender"]] = gender_counts.get(v["gender"], 0) + 1
        if "ethnicity" in v:
            ethnicity_counts[v["ethnicity"]] = ethnicity_counts.get(v["ethnicity"], 0) + 1
        if "lifeStage" in v:
            life_stage_counts[v["lifeStage"]] = life_stage_counts.get(v["lifeStage"], 0) + 1

    out = {
        "_meta": {
            "description": "Static precomputed npcId -> {race, gender, ethnicity?, lifeStage?} lookup "
                           "plus cloud voice profiles, baked into the plugin. Generated offline by "
                           "tools/generate_npc_voices.py from the Old School RuneScape Wiki "
                           "(Infobox NPC and Infobox Monster pages; Monster races derive from "
                           "page categories). Do not hand-edit; edit tools/overrides.json or "
                           "tools/profiles.json and regenerate.",
            "schema": "npcs[id] = { race, gender, ethnicity?, lifeStage? }",
            "source": "oldschool.runescape.wiki Infobox NPC (race/gender/leagueRegion/location) "
                      "and Infobox Monster (race from page categories), "
                      "cross-referenced by name against a full id dump for variant ids, "
                      "+ curated tools/overrides.json; profiles from tools/profiles.json.",
            "npc_pages": page_count,
            "count": len(npcs),
            "name_matched_ids": name_matched,
            "overrides_applied": override_count,
            "race_counts": dict(sorted(race_counts.items())),
            "gender_counts": dict(sorted(gender_counts.items())),
            "ethnicity_counts": dict(sorted(ethnicity_counts.items())),
            "life_stage_counts": dict(sorted(life_stage_counts.items())),
            "profiles_bespoke": len(
                [k for k in (profiles.get("byId") or {}) if not k.startswith("_")]),
        },
        "profiles": profiles,
        "npcs": npcs,
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(out, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    print(f"Wrote {len(npcs)} NPC entries from {pages_with_ids} pages to {args.out}", file=sys.stderr)
    print(f"  races:   {out['_meta']['race_counts']}", file=sys.stderr)
    print(f"  genders: {out['_meta']['gender_counts']}", file=sys.stderr)
    print(f"  ethnicities: {out['_meta']['ethnicity_counts']}", file=sys.stderr)
    print(f"  life stages: {out['_meta']['life_stage_counts']}", file=sys.stderr)


if __name__ == "__main__":
    main()
