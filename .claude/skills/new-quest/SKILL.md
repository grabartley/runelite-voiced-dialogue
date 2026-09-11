---
name: new-quest
description: Find every OSRS quest the plugin does not yet voice correctly and file a research-complete issue for each one. Use when a game update has shipped new quests, when asked to add voice support for a named quest, or to audit quest voice coverage across the whole game.
---

# Add voice support for new quests

Three stages: enumerate every quest in the game, probe which of them the plugin actually
covers, then file one research-complete issue per gap quest through `create-issue`. Stage 3
carries the value; stages 1 and 2 exist so the issues land on the right quests and on nothing
else.

Stop at the filed issue. Implementation belongs to [[add-race]], [[add-npc-profile]] and
[[regenerate-npc-voices]], invoked from the issue.

## What "supported" means

Quest support is four things, and only the first arrives for free:

1. **Race and gender**, for any NPC whose wiki page carries an Infobox `id`. The generator's
   wiki sweep picks these up with no help.
2. **A race bucket that exists.** `bucket_for_race` in `tools/generate_npc_voices.py` ends in
   `return "Human"`, so a wiki race the plugin has never heard of voices as a British commoner
   and nothing reports it. This is the single highest-value thing this skill finds.
3. **An ethnicity** for Human NPCs, which the generator derives from the page's `leagueRegion`
   field. Recent quest pages routinely omit that field, so the origin needs pinning by hand.
4. **A bespoke `byId` profile** for the characters who carry the quest.

A quest counts as supported only when all four hold for its **speaking** cast.

**An issue-title grep is not a coverage test.** Troubled Tortugans has no issue with its name
in the title and is nonetheless fully covered, by the Tortugan race work; the Crab Quest cast
was absent from the bundled table altogether. Treat the backlog as a hint about who already
thought about a quest, and treat the probe in stage 2 as the answer.

## Stage 1: enumerate every quest

Read the wiki's quest list and pull out every quest with its number and release date:

```
mcp__osrs__osrs_wiki_parse_page(page="Quests/List")
```

That page transcludes the free-to-play, members and miniquest tables, so the rendered output
holds all of them and runs to roughly 137KB. Each quest is one row shaped like:

```html
<tr data-rowid="Crab Quest"> <td>183 </td> <td ...><a href="/w/Crab_Quest" ...>Crab Quest</a></td>
  ... <td><a href="/w/8_September">8 September</a> <a href="/w/2026">2026</a> </td></tr>
```

The quest name is the `data-rowid` attribute, the number is the first cell and the release date
is the last. To keep the full table out of the conversation, fetch and extract in one command
instead:

```bash
python3 - <<'PY'
import urllib.request, urllib.parse, json, re, html, datetime
UA = {"User-Agent": "runelite-voiced-dialogue quest coverage (contact: grabartley@gmail.com)"}
url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(
    {"action": "parse", "page": "Quests/List", "prop": "text", "format": "json"})
page = json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))
body = page["parse"]["text"]["*"]
quests = []
for rowid, row in re.findall(r'<tr data-rowid="(.*?)">(.*?)</tr>', body, re.S):
    cells = re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)
    if len(cells) < 3:
        continue
    strip = lambda c: " ".join(re.sub(r"<[^>]+>", " ", c).split())
    quests.append((html.unescape(rowid), strip(cells[0]), strip(cells[-1])))
def released(quest):
    try:
        return datetime.datetime.strptime(quest[2], "%d %B %Y")
    except ValueError:
        return datetime.datetime(1970, 1, 1)
for quest in sorted(quests, key=released, reverse=True):
    print("\t".join(quest))
PY
```

Expect a little over 200 rows. Work newest first: coverage gaps cluster hard at the recent end,
because everything older has already been swept by a region profile batch.

## Stage 2: probe coverage

### Discover the cast

Take the **union** of two queries per quest:

- `list=embeddedin` on `Template:<Quest>`, the quest navbox. This is the superset, and it is the
  only one of the two that finds Infobox **Monster** pages, whose infoboxes usually carry no
  `quest` field at all. The two Outlaws of A Ruff Situation appear here and nowhere else.
- `list=search` for `insource:/\|quest *= *\[\[<Quest>/`. This finds cast members the navbox
  missed.

Neither alone is enough. The navbox for Fallen From Grace lists the quest cast but not the
island locals who also gained dialogue, and the `insource` search for that quest returns mostly
items.

### Run the probe

```bash
python3 - <<'PY' "Crab Quest" "A Ruff Situation"
import urllib.request, urllib.parse, json, re, sys
UA = {"User-Agent": "runelite-voiced-dialogue quest coverage (contact: grabartley@gmail.com)"}

def api(**params):
    params["format"] = "json"
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(params)
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))

def cast(quest):
    titles = set()
    navbox = api(action="query", list="embeddedin", eititle="Template:" + quest,
                 eilimit=500, einamespace=0)
    titles |= {p["title"] for p in navbox.get("query", {}).get("embeddedin", [])}
    tagged = api(action="query", list="search", srlimit=100, srnamespace=0,
                 srsearch=r"insource:/\|quest *= *\[\[%s/" % quest)
    titles |= {p["title"] for p in tagged.get("query", {}).get("search", [])}
    return sorted(t for t in titles if not t.startswith(quest))

def wikitext(titles):
    pages = {}
    for batch in (titles[i:i + 40] for i in range(0, len(titles), 40)):
        data = api(action="query", prop="revisions", rvprop="content", rvslots="main",
                   titles="|".join(batch))
        for page in data["query"]["pages"].values():
            if "revisions" in page:
                pages[page["title"]] = page["revisions"][0]["slots"]["main"]["*"]
    return pages

def field(text, name):
    match = re.search(r"\|\s*%s\d*\s*=\s*(.*)" % name, text)
    return match.group(1).strip() if match else None

def display(value):
    """The shown text of a possibly piped wiki link, so [[Crab (disambiguation)|Crab]] reads Crab."""
    if not value:
        return None
    return re.sub(r"\s+", " ", re.sub(r"\[\[([^\]|]*\|)?([^\]]*)\]\]", r"\2", value)).strip(" |")

table = json.load(open("src/main/resources/npc-voices.json"))
known, bespoke = table["npcs"], table["profiles"].get("byId", {})
generator = open("tools/generate_npc_voices.py").read()
valid = set(re.findall(r'"([A-Z][A-Za-z]+)"',
                       re.search(r"VALID_RACES = \{(.*?)\}", generator, re.S).group(1)))
rules = [(re.compile(p, re.I), b) for p, b in re.findall(
    r'\(r"([^"]+)",\s*"(\w+)"\)',
    re.search(r"RACE_BUCKET_RULES = \[(.*?)\n\]", generator, re.S).group(1))]

def bucket(race):
    for pattern, name in rules:
        if pattern.search(race):
            return name
    return None

for quest in sys.argv[1:]:
    print("=" * 78, "\n" + quest)
    for title, text in sorted(wikitext(cast(quest)).items()):
        if "Infobox NPC" not in text and "Infobox Monster" not in text:
            continue
        ids = sorted({int(i) for i in re.findall(
            r"\b\d{3,6}\b", " ".join(re.findall(r"\|\s*id\d*\s*=\s*([\d,\s]+)", text)))})
        race, gender = display(field(text, "race")), field(text, "gender")
        voiced = bucket(race) if race else None
        gaps = []
        if race and voiced is None:
            gaps.append("RACE-UNMAPPED:" + race)
        elif voiced and voiced != "Human":
            gaps.append("race=" + voiced)
        absent = [i for i in ids if str(i) not in known]
        if absent:
            gaps.append("NOT-IN-TABLE:" + ",".join(map(str, absent)))
        if not gender:
            gaps.append("no-gender")
        if voiced == "Human" and not field(text, "leagueRegion"):
            gaps.append("no-origin")
        if ids and not any(str(i) in bespoke for i in ids):
            gaps.append("no-byId")
        print("  %-46s ids=%-34s %s" % (title[:44], str(ids)[:32], "; ".join(gaps) or "ok"))
PY
```

Run it from the repo root, so it reads the committed table and generator rather than guessing at
them. `valid` is read but not asserted on; it is there to confirm a bucket name the rules emit is
one the generator will accept in an override.

### Triage the flags

**Hard gaps. These justify an issue:**

| Flag | Means |
|---|---|
| `RACE-UNMAPPED:<race>` | The wiki race hits no bucket, so every NPC of it voices as a British commoner. A new first-class race is wanted; see [[add-race]]. |
| `NOT-IN-TABLE:<ids>` | The bundled table does not know the id, so the NPC resolves to the unknown-race default. |
| `no-gender` on a speaker | No wiki gender field, so the generator defaults Male. Fine for a male NPC, wrong for a female one, and only reading the transcript settles it. |
| `no-origin` | A Human NPC whose page carries no `leagueRegion`, so they keep the British default instead of their region's accent. |

**Soft gaps. Note them, do not let them carry an issue on their own:**

- `no-byId` on a minor NPC. A walk-on part does not need a bespoke personality, and the bespoke
  backlog has its own umbrella.
- `race=<bucket>` is not a gap at all. It records that the NPC resolves to a distinctive race
  already, which is the outcome wanted.

A quest whose every row reads `ok`, `race=...` or `no-byId` is covered. Skip it and say so rather
than filing an issue to prove it.

### Confirm who actually speaks

The probe finds NPCs, not speakers. Before an id reaches an issue, read
`Transcript:<Quest>` and sort its lines:

- A plain line is a dialogue widget line and **is** synthesized today.
- A `{{overhead|...}}` line is overhead chatter, which stays inert until the overhead chatter
  feature ships, so it is out of scope.
- A `{{tbox|...}}` line is narration and already voices in the narrator voice.

This distinction decides real scope. In A Ruff Situation the stray dog has plain lines
(`Arf arf!`, `Whimper.`) while her puppies have overhead ones only, so the dog's voice is a
live defect and the puppies' is not.

## Stage 3: file one issue per gap quest

Invoke `create-issue` once per quest, never one issue covering several. Title it
`[Build] <Quest> NPCs` plus whatever the quest actually adds, for example
`[Build] Crab Quest NPCs: Crab and Penguin races`.

The body has to let someone implement without redoing the research:

1. **Goal**, naming the quest, its number, its release date and the single worst symptom.
2. **A resolved id table**: ids, page, race, gender, and **where each gender came from** (wiki
   field, prose pronoun, or inferred from the name). Name the inferred ones as inferred. A name
   is not evidence of gender: Ffion of Wyrmscraig reads female and is male.
3. **Out-of-scope ids, listed explicitly.** Every non-speaking id in the quest's id block, named
   so it is visibly excluded rather than quietly forgotten. A reader has to be able to tell
   "decided against" from "never looked at".
4. **The wiring points** for any new race, as a path-by-path table. Read the live paths off the
   tree rather than copying them from [[add-race]], whose paths predate the package restructure.
5. **Ready-to-paste snippets** for `tools/overrides.json` and `tools/profiles.json`.
6. **Acceptance criteria per NPC**, naming ids and the expected race, gender and origin, plus
   the regenerate-and-verify step and a passing `./gradlew clean build`.

Keep the body public-safe and timeless: no local paths, no dates beyond the quest's own release,
no em dashes, and issue references rendered as links rather than bare numbers.

## Gotchas

- **The plugin resolves by active id, not base composition id.** `NpcDemographicAnalyzer` prefers
  `npc.getId()` and falls back to the composition id, so a transformed multiloc NPC is looked up
  under the id it transformed into. When two characters share a transformed id, pin it to one of
  them on purpose, say which lines that trades away, and flag it for Debug Mode confirmation.
  Crab Quest has two such ids.
- **A `Multi Infobox` page mixes NPC and item ids.** The Stray puppy page carries both its NPC
  ids and its item ids; the item ids must never reach an override. Check which infobox block an
  id sits under before trusting it.
- **Unwrap a piped race link before matching it.** `[[Crab (disambiguation)|Crab]]` has to read
  `Crab`, or it matches no bucket and the NPC looks unmappable when it is merely badly linked.
- **`byEthnicity` applies to `Human` and `Unknown` only.** `NpcProfileTable#collectLayers` skips
  the ethnicity tint for a distinctive race, so an `ethnicity` on a Dwarf or a Dog is a no-op.
  Set it on Human NPCs and nothing else.
- **Versioned infoboxes carry per-version genders.** `gender1`, `gender2` and `gender3` belong to
  different ids, so gender each id pair from its own version rather than from the first field
  found.
- **A freshly released quest is in no cache dump.** The bundled `npctypes.txt` dump stops well
  short of current ids, so the wiki Infobox `id` is the source of truth. Only reach for
  [[resolve-npc-ids]] when the infoboxes are genuinely empty, which recent quest pages usually
  are not.
- **Quest release does not mean wiki completeness.** A cast member whose page is a stub with no
  infobox is invisible to the generator and needs a full override entry, race and gender
  included.

## Related skills

- [[add-race]], when a quest brings a species the plugin has no bucket for
- [[add-npc-profile]], for per-NPC race, gender, origin and personality work
- [[regenerate-npc-voices]], the mandatory regenerate-and-verify step the issues hand off to
- [[find-npc-true-origin]], when a Human's real origin differs from where they are found
- [[resolve-npc-ids]], only when the wiki infoboxes carry no ids
