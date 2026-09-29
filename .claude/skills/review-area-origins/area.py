import argparse
import collections
import glob
import html
import json
import os
import re
import time
import urllib.parse
import urllib.request

TABLE = "src/main/resources/npc-voices.json"
PROFILES = "tools/profiles.json"
OVERRIDES = "tools/overrides.json"
WIKI = "https://oldschool.runescape.wiki"
USER_AGENT = {"User-Agent": "runelite-voiced-dialogue area origin review"}
PLAIN_RACES = {"Human", "Unknown", None}
PICK_LINE = re.compile(r"^(.*) \[([\d,]+)\]: (\S+) -> (\S+)$")
ONE_LINE_ENTRY = re.compile(r'^(\s*)"(\d+)": (\{.*\})(,?)$')


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def cache_symbols(path):
    if not path:
        found = glob.glob(os.path.expanduser(
            "~/.npm/_npx/*/node_modules/@jayarrowz/mcp-osrs/dist/data/npctypes.txt"))
        path = max(found, key=os.path.getmtime) if found else None
    if not path:
        return None
    symbols = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            npc_id, _, symbol = line.rstrip("\n").partition("\t")
            symbols[npc_id] = symbol
    return symbols


def collect(args):
    table = load(TABLE)
    npcs, by_id = table["npcs"], table["profiles"]["byId"]
    pattern = re.compile(args.symbols, re.I) if args.symbols else None
    symbols = cache_symbols(args.npctypes) if pattern else {}
    if symbols is None:
        raise SystemExit("no npctypes.txt found for the cache symbol pattern; pass --npctypes")
    exclude = set(args.exclude or [])
    ids = {k for k, v in npcs.items() if v.get("ethnicity") == args.ethnicity}
    if pattern:
        ids |= {k for k, s in symbols.items() if k in npcs and pattern.search(s)}
    groups = collections.defaultdict(list)
    for npc_id in ids:
        profile = by_id.get(npc_id)
        if isinstance(profile, dict) and profile["name"] not in exclude:
            npc = npcs[npc_id]
            key = (profile["name"], profile.get("style"), npc.get("ethnicity") or "", npc.get("race") or "")
            groups[key].append(npc_id)
    cases = []
    for index, ((name, *_), members) in enumerate(sorted(groups.items())):
        for npc_id in sorted(members, key=int):
            cases.append({"key": f"k{npc_id}", "group": f"{name}|{index}", "kind": "npc",
                          "id": int(npc_id), "name": name, "line": "x"})
    with open(os.path.join(args.out, "probe.json"), "w", encoding="utf-8") as fh:
        json.dump({"cases": cases}, fh)
    print(f"{len(groups)} voiced characters, {len(cases)} ids")


def wiki_api(params):
    query = urllib.parse.urlencode(dict(params, format="json"))
    request = urllib.request.Request(f"{WIKI}/api.php?{query}", headers=USER_AGENT)
    return json.load(urllib.request.urlopen(request, timeout=60))


def strip_markup(text):
    return re.sub(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]|\{\{[^}]*\}\}|'''|''",
                  lambda m: m.group(1) or "", text or "").strip()


def infobox_field(wikitext, field, index):
    match = (re.search(rf"\|\s*{field}{index}\s*=\s*([^\n]+)", wikitext)
             or re.search(rf"\|\s*{field}\d*\s*=\s*([^\n]+)", wikitext))
    return match.group(1) if match else None


def wiki_entry(npc_id):
    request = urllib.request.Request(
        f"{WIKI}/w/Special:Lookup?type=npc&id={npc_id}", headers=USER_AGENT)
    with urllib.request.urlopen(request, timeout=60) as response:
        final = urllib.parse.unquote(response.geturl())
    match = re.search(r"/w/([^#?]+)(?:#(.*))?$", final)
    title = match.group(1).replace("_", " ")
    version = (match.group(2) or "").replace("_", " ")
    if title.startswith("Special:"):
        return {}
    wikitext = wiki_api({"action": "parse", "page": title, "prop": "wikitext",
                         "redirects": 1})["parse"]["wikitext"]["*"]
    index = ""
    for found in re.finditer(r"\|\s*version(\d+)\s*=\s*([^\n|]+)", wikitext):
        if found.group(2).strip() == version:
            index = found.group(1)
    if not index:
        for found in re.finditer(r"\|\s*id(\d*)\s*=\s*([^\n|]+)", wikitext):
            if str(npc_id) in re.split(r"\s*,\s*", found.group(2).strip()):
                index = found.group(1)
    image = re.search(r"\[\[File:([^|\]]+)", infobox_field(wikitext, "image", index) or "")
    url = None
    if image:
        pages = wiki_api({"action": "query", "titles": "File:" + image.group(1).strip(),
                          "prop": "imageinfo", "iiprop": "url", "iiurlwidth": 180})
        info = (list(pages["query"]["pages"].values())[0].get("imageinfo") or [{}])[0]
        url = info.get("thumburl") or info.get("url")
    return {"title": title, "version": version, "image": url,
            "examine": strip_markup((infobox_field(wikitext, "examine", index) or "").split("|")[0]),
            "location": strip_markup(infobox_field(wikitext, "location", index))[:120]}


def voice_region(result):
    for trace in result.get("trace", []):
        if "voiceRegion=" in trace:
            return trace.split("voiceRegion=")[1].split(",")[0]
    return "-"


def build(args):
    table = load(TABLE)
    npcs, profiles = table["npcs"], table["profiles"]
    origins = {k: v for k, v in profiles["byEthnicity"].items() if isinstance(v, dict)}
    resolved = {r["key"]: r for r in load(os.path.join(args.out, "resolved", "results.json"))}
    cases = load(os.path.join(args.out, "probe.json"))["cases"]
    wiki_path = os.path.join(args.out, "wiki.json")
    wiki = load(wiki_path) if os.path.exists(wiki_path) else {}
    for case in cases:
        npc_id = str(case["id"])
        if npc_id not in wiki or "error" in wiki[npc_id]:
            try:
                wiki[npc_id] = wiki_entry(npc_id)
            except Exception as error:
                wiki[npc_id] = {"error": str(error)}
            time.sleep(0.2)
    with open(wiki_path, "w", encoding="utf-8") as fh:
        json.dump(wiki, fh, indent=1)
    groups = collections.OrderedDict()
    for case in cases:
        groups.setdefault(case["group"], []).append(str(case["id"]))
    choices = [("none", "None: British default", "Strong working-class English accent")] + [
        (k, k, (v.get("accent") or "").split(",")[0]) for k, v in sorted(origins.items())]
    cards = []
    for ids in groups.values():
        first = ids[0]
        npc, profile, result, page = npcs[first], profiles["byId"][first], resolved["k" + first], wiki[first]
        human = npc.get("race") in PLAIN_RACES
        current = npc.get("ethnicity") or "none"
        voices = ", ".join(sorted({resolved["k" + i].get("voice") or "-" for i in ids}))
        pictures, seen = [], set()
        for i in ids:
            url = wiki[i].get("image")
            if url and url not in seen:
                seen.add(url)
                pictures.append((url, wiki[i].get("version") or ""))
        if human:
            options = "".join(
                f'<option value="{k}"{" selected" if k == current else ""}>'
                f'{html.escape(label)}{" (current)" if k == current else ""}</option>'
                for k, label, _ in choices)
            picker = (f'<label>Origin <select data-ids="{",".join(ids)}" '
                      f'data-name="{html.escape(profile["name"])}" data-current="{current}">'
                      f'{options}</select></label>')
        else:
            picker = (f'<p class="note">{html.escape(npc.get("race"))} race: its racial accent '
                      'always wins, so origin has no effect.</p>')
        figures = "".join(f'<figure><img loading="lazy" src="{html.escape(u)}" alt="">'
                          f'<figcaption>{html.escape(v)}</figcaption></figure>'
                          for u, v in pictures[:4])
        link = html.escape((page.get("title") or "").replace(" ", "_"))
        cards.append(
            f'<article class="card{"" if human else " other"}"><div class="pics">{figures}</div>'
            f'<div class="body"><h2>{html.escape(profile["name"])}</h2>'
            f'<p class="meta">ids {", ".join(ids)} · {html.escape(npc.get("race") or "?")} · '
            f'{html.escape(npc.get("gender") or "?")} · age {profile.get("age")}</p>'
            f'<p class="meta">{html.escape(page.get("location") or "")}</p>'
            f'<p class="examine">“{html.escape(page.get("examine") or "")}”</p>'
            f'<dl><dt>Origin now</dt><dd><code>{html.escape(current)}</code></dd>'
            f'<dt>Accent</dt><dd>{html.escape(result.get("accent") or "")}</dd>'
            f'<dt>Voice</dt><dd><code>{html.escape(voices)}</code> · region '
            f'{html.escape(voice_region(result))}</dd>'
            f'<dt>Style</dt><dd>{html.escape(profile.get("style") or "")}</dd></dl>'
            f'<p class="meta"><a href="{WIKI}/w/{link}" target="_blank" rel="noopener">wiki: '
            f'{html.escape(page.get("title") or "")}</a></p>{picker}</div></article>')
    legend = "".join(f"<li><code>{html.escape(k)}</code>: {html.escape(a)}</li>" for k, _, a in choices)
    template = open(os.path.join(os.path.dirname(__file__), "sheet.html"), encoding="utf-8").read()
    page = (template.replace("{{AREA}}", html.escape(args.area))
            .replace("{{COUNT}}", str(len(groups)))
            .replace("{{STORE}}", f"{args.area}:{len(cases)}")
            .replace("{{LEGEND}}", legend)
            .replace("{{CARDS}}", "".join(cards)))
    os.makedirs(os.path.join(args.out, "site"), exist_ok=True)
    with open(os.path.join(args.out, "site", "origins.html"), "w", encoding="utf-8") as fh:
        fh.write(page)
    missing = sum(1 for i in wiki.values() if not i.get("image"))
    print(f"{len(cards)} cards, {sum('<select' in c for c in cards)} with an origin picker, "
          f"{missing} ids without a picture")


def apply(args):
    table = load(TABLE)
    names = {k: v["name"] for k, v in table["profiles"]["byId"].items() if isinstance(v, dict)}
    wiki_path = os.path.join(args.out, "wiki.json")
    if os.path.exists(wiki_path):
        names.update({k: v["title"] for k, v in load(wiki_path).items() if v.get("title")})
    origins = {k for k, v in table["profiles"]["byEthnicity"].items() if isinstance(v, dict)}
    picks = {}
    with open(args.picks, encoding="utf-8") as fh:
        for raw in fh:
            line = raw.strip()
            if not line:
                continue
            match = PICK_LINE.match(line)
            if not match:
                raise SystemExit(f"not a pick line: {line}")
            origin = None if match.group(4) == "none" else match.group(4)
            if origin is not None and origin not in origins:
                raise SystemExit(f"unknown origin {origin!r} in: {raw.strip()}")
            for npc_id in match.group(2).split(","):
                picks[npc_id] = origin
    lines = open(OVERRIDES, encoding="utf-8").read().split("\n")
    patched, last = set(), None
    for index, line in enumerate(lines):
        match = ONE_LINE_ENTRY.match(line)
        if not match:
            continue
        last = index
        if match.group(2) in picks:
            entry = json.loads(match.group(3))
            entry["ethnicity"] = picks[match.group(2)]
            lines[index] = (f'{match.group(1)}"{match.group(2)}": '
                            f'{json.dumps(entry, ensure_ascii=False)}{match.group(4)}')
            patched.add(match.group(2))
    indent = ONE_LINE_ENTRY.match(lines[last]).group(1)
    added = [f'{indent}"{i}": {json.dumps({"name": names.get(i), "ethnicity": picks[i]}, ensure_ascii=False)},'
             for i in sorted(set(picks) - patched, key=int)]
    if added:
        if not lines[last].endswith(","):
            lines[last] += ","
        added[-1] = added[-1].rstrip(",")
        lines[last + 1:last + 1] = added
    with open(OVERRIDES, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    load(OVERRIDES)
    print(f"{len(picks)} ids: {len(patched)} overrides patched, {len(added)} added")


def main():
    parser = argparse.ArgumentParser(description="Review the origins of every voiced NPC in an area.")
    commands = parser.add_subparsers(dest="command", required=True)
    gather = commands.add_parser("collect")
    gather.add_argument("--ethnicity", required=True)
    gather.add_argument("--symbols", default="")
    gather.add_argument("--exclude", nargs="*")
    gather.add_argument("--npctypes", default=None)
    gather.add_argument("--out", required=True)
    sheet = commands.add_parser("build")
    sheet.add_argument("--area", required=True)
    sheet.add_argument("--out", required=True)
    picks = commands.add_parser("apply")
    picks.add_argument("--picks", required=True)
    picks.add_argument("--out", required=True)
    args = parser.parse_args()
    {"collect": collect, "build": build, "apply": apply}[args.command](args)


if __name__ == "__main__":
    main()
