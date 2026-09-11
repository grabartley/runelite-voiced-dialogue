---
name: new-quest
description: Find every OSRS quest the plugin does not yet voice correctly and file a research-complete issue for each one. Use when a game update has shipped new quests, when asked to add voice support for a named quest, or to audit quest voice coverage across the whole game.
---

# Add voice support for new quests

Three stages: enumerate every quest in the game, probe which of them the plugin already covers,
then file one research-complete issue per gap quest through `create-issue`. Stage 3 carries the
value; stages 1 and 2 exist so the issues land on the right quests and on nothing else.

Stop at the filed issue. Implementation belongs to [[add-npc-profile]], [[add-race]] and
[[regenerate-npc-voices]], invoked from the issue.

## What "supported" means

Quest support is four things, and only the first arrives for free:

1. **Race and gender**, for any NPC whose wiki page carries an Infobox `id`. The generator's
   wiki sweep picks these up with no help.
2. **A race the generator can bucket.** `bucket_for_race` in `tools/generate_npc_voices.py` ends
   in `return "Human"`, so a wiki race no rule matches voices as a British commoner and nothing
   reports it. This is the highest-value thing this skill finds.
3. **An origin** for Human NPCs, which the generator derives from the page's `leagueRegion`.
   Recent quest pages routinely omit that field, so the origin needs pinning by hand.
4. **A bespoke `byId` profile** for the characters who carry the quest.

A quest counts as supported only when all four hold for its **speaking** cast.

**An issue-title grep is not a coverage test.** Troubled Tortugans has no issue carrying its name
and is nonetheless covered, by the Tortugan race work; the Crab Quest cast was absent from the
bundled table altogether. Treat the backlog as a hint about who already thought about a quest,
and treat the stage 2 probe as the answer.

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

The quest name is the `data-rowid` attribute. The cell layout differs by row type, which is the
one trap here: a quest row carries 7 cells with the number first and the release date last, while
a miniquest row carries 6 with no number column and the date one in from the end. Reading
`cells[-1]` for every row silently turns every miniquest into an undated row that sorts to the
bottom of a list meant to be worked top-down. A quick-guide row also slips into the table with
empty cells and has to be dropped.

To keep the full table out of the conversation, fetch and extract in one command:

```bash
python3 - <<'PY'
import urllib.request, urllib.parse, json, re, html, datetime
UA = {"User-Agent": "runelite-voiced-dialogue quest coverage (contact: grabartley@gmail.com)"}
url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(
    {"action": "parse", "page": "Quests/List", "prop": "text", "format": "json"})
body = json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))["parse"]["text"]["*"]
quests = []
for rowid, row in re.findall(r'<tr data-rowid="(.*?)">(.*?)</tr>', body, re.S):
    cells = [" ".join(re.sub(r"<[^>]+>", " ", c).split())
             for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)]
    if len(cells) == 7:
        number, released = cells[0], cells[-1]
    elif len(cells) == 6:
        number, released = "miniquest", cells[-2]
    else:
        continue
    if number:
        quests.append((html.unescape(rowid), number, released))
def when(quest):
    try:
        return datetime.datetime.strptime(quest[2], "%d %B %Y")
    except ValueError:
        return datetime.datetime(1970, 1, 1)
undated = [q for q in quests if when(q).year == 1970]
print("quests=%d undated=%d" % (len(quests), len(undated)))
if undated:
    print("UNPARSED DATES, fix the parse before trusting the order:", undated)
for quest in sorted(quests, key=when, reverse=True):
    print("\t".join(quest))
PY
```

Expect 210 or more rows and `undated=0`. The `undated` guard is what detects a layout move; the
row count grows with every release, so treat it as a floor. Work newest first: gaps cluster hard at
the recent end, because everything older has already been swept by a region profile batch.

## Stage 2: probe coverage

### Discover the cast

Take the **union** of two wiki queries per quest:

- `list=embeddedin` on `Template:<Quest>`, the quest navbox. This is usually the superset, and it
  is the only one of the two that finds Infobox **Monster** pages, whose infoboxes carry no
  `quest` field. The two Outlaws of A Ruff Situation appear here and nowhere else.
- `list=search` for `insource:/\|quest *= *\[\[<Quest>/`. This finds cast the navbox omits.

Neither alone is enough, and the navbox is not guaranteed to exist at all: Learning the Ropes has
no `Template:` page, which collapses discovery onto the one query this skill calls insufficient.
Print both hit counts so a zero is visible rather than silent.

### Run the probe

Run it from the repo root. It imports the generator rather than re-deriving its rules, and reads
the committed table and overrides as the truth for any id they already know.

```bash
python3 - <<'PY' "Crab Quest" "A Ruff Situation"
import importlib.util, json, re, sys, urllib.parse, urllib.request
UA = {"User-Agent": "runelite-voiced-dialogue quest coverage (contact: grabartley@gmail.com)"}

def api(**params):
    params["format"] = "json"
    url = "https://oldschool.runescape.wiki/api.php?" + urllib.parse.urlencode(params)
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))

spec = importlib.util.spec_from_file_location("gen", "tools/generate_npc_voices.py")
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)
table = json.load(open("src/main/resources/npc-voices.json"))
shipped, bespoke = table["npcs"], table["profiles"].get("byId", {})
pinned = json.load(open("tools/overrides.json"))["npcs"]

def bucket(race):
    """The bucket the generator's own rules give a wiki race, or None when no rule matches."""
    for pattern, name in gen.RACE_BUCKET_RULES:
        if pattern.search(race):
            return name
    return None

def cast(quest):
    navbox = api(action="query", list="embeddedin", eititle="Template:" + quest,
                 eilimit=500, einamespace=0)
    navbox = {p["title"] for p in navbox.get("query", {}).get("embeddedin", [])}
    tagged = api(action="query", list="search", srlimit=100, srnamespace=0,
                 srsearch="insource:/\\|quest *= *\\[\\[%s/"
                 % re.escape(quest).replace("/", "\\/"))
    tagged = {p["title"] for p in tagged.get("query", {}).get("search", [])}
    print("  cast discovery: navbox=%d tagged=%d" % (len(navbox), len(tagged)))
    if not navbox:
        print("  WARNING: no Template:%s, discovery rests on the insource query alone" % quest)
    return sorted(t for t in navbox | tagged if not t.startswith(quest))

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

def shown(value):
    """The display text of a possibly piped link, so [[Crab (disambiguation)|Crab]] reads Crab."""
    if not value:
        return None
    return re.sub(r"\s+", " ", re.sub(r"\[\[([^\]|]*\|)?([^\]]*)\]\]", r"\2", value)).strip(" |")

def npc_ids(text):
    """NPC ids only. A Multi Infobox page also carries item ids, which must never be voiced."""
    block = text.split("|text2", 1)[0]
    return sorted({int(i) for i in re.findall(
        r"\b\d{3,6}\b", " ".join(re.findall(r"\|\s*id\d*\s*=\s*([\d,\s]+)", block)))})

for quest in sys.argv[1:]:
    print("=" * 78, "\n" + quest)
    undocumented = []
    for title, text in sorted(wikitext(cast(quest)).items()):
        if "Infobox NPC" not in text and "Infobox Monster" not in text:
            continue
        ids = npc_ids(text)
        if not ids:
            undocumented.append(title)
            continue
        race, gender = shown(field(text, "race")), field(text, "gender")
        voiced = bucket(race) if race else None
        origin = gen.ethnicity_key(field(text, "leagueRegion"), field(text, "location"))
        absent = [i for i in ids if str(i) not in shipped]
        races = {shipped[str(i)]["race"] for i in ids if str(i) in shipped}
        plain = not races or races <= {"Human", "Unknown"}
        hand_gender = any("gender" in pinned.get(str(i), {}) for i in ids)
        has_origin = (any("ethnicity" in pinned.get(str(i), {}) for i in ids)
                      or any(shipped.get(str(i), {}).get("ethnicity") for i in ids))
        gaps = []
        if absent:
            gaps.append("NOT-IN-TABLE:" + ",".join(map(str, absent)))
        if plain and race and voiced is None:
            gaps.append("RACE-UNMAPPED:" + race)
        if plain and not race:
            gaps.append("no-race")
        if plain and voiced == "Human" and not origin and not has_origin:
            gaps.append("no-origin")
        if not gender and not hand_gender:
            gaps.append("gender-unverified")
        if not any(str(i) in bespoke for i in ids):
            gaps.append("no-byId")
        settled = sorted(races - {"Human", "Unknown"})
        if settled:
            gaps.append("race=" + ",".join(settled))
        elif not races and voiced and voiced != "Human":
            gaps.append("race=%s(wiki)" % voiced)
        print("  %-44s ids=%-30s %s" % (title[:42], str(ids)[:28], "; ".join(gaps) or "ok"))
    if undocumented:
        print("  NEEDS-MANUAL, infobox but no id, resolve by hand:", ", ".join(undocumented))
PY
```

Import the generator; do not re-derive its tables by regex. One `RACE_BUCKET_RULES` entry is
written as an implicit two-line string concatenation, so a line-wise scrape silently drops the
Undead rule and then reports every vampyre, zombie, skeleton, ghost, ghoul, mummy, banshee,
ankou, wight, shade, revenant, spectre, wraith and lich as an unmapped race. That turns any
Morytania quest into a fabricated request for a race the plugin already ships.

Read the committed table and `tools/overrides.json` as the truth for ids they know. The wiki is
the input the generator consumed, not the current state: the Troubled Tortugans elders carry no
wiki gender field and are nonetheless pinned Male in overrides, so judging them from the wiki
alone reports a settled NPC as a gap.

### Triage the flags

**Hard gaps. These justify an issue:**

| Flag | Means |
|---|---|
| `NOT-IN-TABLE:<ids>` | The bundled table does not know the id, so the NPC resolves to the unknown-race default. |
| `RACE-UNMAPPED:<race>` | The wiki names a race no rule matches, so the NPC voices as a British commoner. |
| `no-race` | The page carries no race field at all, so the NPC falls to the default. Common on Infobox Monster pages, where the generator falls back to page categories. Resolve the race by hand, and settle the origin too when it lands on Human. |
| `no-origin` | A Human NPC with no usable `leagueRegion`, so they keep the British default instead of their region's accent. |

**Check, not a gap on its own:**

- `gender-unverified` means no wiki gender field and no pinned gender, so the generator defaults
  Male. That is correct for a male NPC and for a non-speaker, and wrong only for a female
  speaker. Settle it from the transcript before it reaches an issue.

**Soft. Never carries an issue alone:**

- `no-byId` on a minor NPC. A walk-on part does not need a bespoke personality, and the bespoke
  profile backlog has its own umbrella.

**Not a gap at all:**

- `race=<bucket>` records that the NPC already resolves to a distinctive race, which is the
  outcome wanted.

A quest is covered when no row carries a hard gap and every `gender-unverified` row turns out to
be a non-speaker or genuinely male. Troubled Tortugans and The Ides of Milk both read that way.
Say a quest is covered and move on rather than filing an issue to prove it.

### RACE-UNMAPPED does not always mean a new race

Most unmapped races map onto an existing bucket in `tools/overrides.json`, the way Dorgeshuun
goes to Goblin, Vampyre to Undead, Imp to Demon and ape to Gorilla. Reach for [[add-race]] only
when the species genuinely needs its own accent and voice pool, as Crab, Penguin and Dog did.
A species that is merely unusual and has one speaking member gets a `byId` style instead. See
[[add-npc-profile]] for that decision.

### Confirm who actually speaks

The probe finds NPCs, not speakers. Before an id reaches an issue, read `Transcript:<Quest>` and
sort its lines:

- A plain line is a dialogue widget line and **is** synthesized today.
- A `{{overhead|...}}` line is overhead chatter, inert until the overhead chatter feature ships.
- A `{{tbox|...}}` line is narration and already voices in the narrator voice.

This decides real scope. In A Ruff Situation the stray dog has plain lines (`Arf arf!`,
`Whimper.`) while her puppies have overhead ones only, so the dog's voice is a live defect and
the puppies' is not.

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
   so a reader can tell "decided against" from "never looked at".
4. **The wiring points** for any new race, as a path-by-path table, read off the tree rather than
   copied from another document.
5. **Ready-to-paste snippets** for `tools/overrides.json` and `tools/profiles.json`.
6. **Acceptance criteria per NPC**, naming ids and the expected race, gender and origin, plus the
   regenerate-and-verify step and a passing `./gradlew clean build`.

Keep the body public-safe and timeless: no local paths, no dates beyond the quest's own release,
no em dashes, and issue references rendered as links rather than bare numbers.

## Gotchas

- **A `Multi Infobox` page mixes NPC and item ids.** The Stray puppy page carries its NPC ids and
  its item ids under one title, and the item ids must never reach an override. The probe splits at
  `|text2`, the second tab label, which assumes the NPC block comes first. Check that assumption
  per page, because it inverts when the item tab leads.
- **Unwrap a piped race link before matching it.** `[[Crab (disambiguation)|Crab]]` has to read
  `Crab`, or the race matches no bucket and a badly linked NPC looks like an unmapped species.
- **A present `leagueRegion` is not an origin.** `ethnicity_key` returns nothing for `No`,
  `General`, `N/A` and any multi-region value, so call it rather than testing the field's
  presence.
- **Versioned infoboxes carry per-version genders.** `gender1`, `gender2` and `gender3` belong to
  different ids, so gender each id from its own version rather than from the first field found.
- **A shared transformed id needs a deliberate call.** The table is keyed on the active id (see
  [[add-npc-profile]]), so when a transformation leaves two characters sharing one id, pin it to
  one of them on purpose, state which lines that trades away, and flag it for Debug Mode
  confirmation. Crab Quest has two such ids.
- **A freshly released quest is in no cache dump.** The bundled `npctypes.txt` stops well short
  of current ids, so the wiki Infobox `id` is the source of truth. Reach for [[resolve-npc-ids]]
  only when the infoboxes are genuinely empty, which recent quest pages usually are not.
- **Quest release does not mean wiki completeness.** A cast member whose page is a stub with no
  infobox is invisible to the generator and needs a full override entry, race and gender included.

## Related skills

- [[add-npc-profile]], for per-NPC race, gender, origin and personality work, and for the
  decision between mapping a race onto an existing bucket and adding a new one
- [[add-race]], when a quest brings a species that genuinely needs its own voice
- [[regenerate-npc-voices]], the regenerate-and-verify step the issues hand off to
- [[find-npc-true-origin]], when a Human's real origin differs from where they are found
- [[resolve-npc-ids]], only when the wiki infoboxes carry no ids
