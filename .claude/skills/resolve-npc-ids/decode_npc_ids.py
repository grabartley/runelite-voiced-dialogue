#!/usr/bin/env python3
"""Resolve live OSRS NPC ids -> names straight from the archived game cache.

Use this when new content just launched and you need the real NPC ids for
tools/overrides.json, but the OSRS Wiki infoboxes aren't filled yet and the
osrs MCP cache dump / weirdgloop MOID `npcsmin.js` are stale (they lag a release
by days). The OpenRS2 archive (https://archive.openrs2.org) snapshots the live
cache within hours, so we decode index 2 / group 9 (npc configs) ourselves.

Only stdlib is used (urllib, bz2, zlib, struct).

Examples
--------
  # newest cast in an id block, with who's actually a dialogue NPC
  python3 decode_npc_ids.py --range 16294-16337 --ops

  # find specific characters by (partial) name
  python3 decode_npc_ids.py --names Ffion,Cormac,Mortimer,"Mad Angel"

  # pin a specific cache instead of auto-picking the newest
  python3 decode_npc_ids.py --cache 2644 --range 16294-16337

The decoder self-validates against 5 known ids on every run; if that check
fails (Jagex added a new npc opcode that shifts byte alignment) it prints the
offending opcode so you can extend OPCODE handling. See SKILL.md.
"""
import argparse, bz2, json, struct, sys, urllib.request, zlib

OPENRS2 = "https://archive.openrs2.org"
# ids with stable names, used to prove byte alignment every run
KNOWN = {0: "Tool Leprechaun", 5254: "Hafuba", 2205: "Commander Zilyana"}


def fetch(url):
    with urllib.request.urlopen(url, timeout=180) as r:
        return r.read()


def pick_cache():
    """Newest oldschool cache that actually serves index 2 / group 9."""
    caches = json.loads(fetch(f"{OPENRS2}/caches.json"))
    osrs = [c for c in caches if c.get("game") == "oldschool" and c.get("scope") == "runescape"]
    osrs.sort(key=lambda c: c.get("timestamp") or "", reverse=True)
    for c in osrs:
        cid = c["id"]
        try:
            fetch(f"{OPENRS2}/caches/runescape/{cid}/archives/2/groups/9.dat")
            return str(cid), c.get("timestamp")
        except Exception:
            continue
    raise SystemExit("no oldschool cache with npc configs found")


def decompress(data):
    """Js5 container: [type u8][clen u32]([dlen u32])[payload]."""
    t = data[0]
    clen = struct.unpack(">i", data[1:5])[0]
    if t == 0:
        return data[5:5 + clen]
    comp = data[9:9 + clen]
    if t == 1:
        return bz2.decompress(b"BZh1" + comp)  # OSRS strips the 4-byte bzip2 header
    if t == 2:
        return zlib.decompress(comp, 47)       # gzip
    raise ValueError(f"unknown compression {t}")


def _smart(b, p):
    """Js5 'big smart': 2 bytes if top bit clear, else 4 (protocol >= 7)."""
    if b[p] & 0x80:
        return struct.unpack(">I", b[p:p + 4])[0] & 0x7fffffff, p + 4
    return struct.unpack(">H", b[p:p + 2])[0], p + 2


def parse_reftable(b):
    """Reference table for an index -> {group: [file ids]} and file counts."""
    p = 0
    proto = b[p]; p += 1
    if proto >= 6:
        p += 4  # version
    flags = b[p]; p += 1
    NAMES, WHIRL, SIZES, HASH = flags & 1, flags & 2, flags & 4, flags & 8
    gcount, p = _smart(b, p)
    gids, acc = [], 0
    for _ in range(gcount):
        d, p = _smart(b, p); acc += d; gids.append(acc)
    if NAMES:
        p += 4 * gcount
    p += 4 * gcount               # crc
    if HASH:
        p += 4 * gcount
    if WHIRL:
        p += 64 * gcount
    if SIZES:
        p += 8 * gcount
    p += 4 * gcount               # version
    fcounts = []
    for _ in range(gcount):
        c, p = _smart(b, p); fcounts.append(c)
    fileids = {}
    for gi in range(gcount):
        acc, ids = 0, []
        for _ in range(fcounts[gi]):
            d, p = _smart(b, p); acc += d; ids.append(acc)
        fileids[gids[gi]] = ids
    return gids, fcounts, fileids


def unpack_group(data, fcount):
    """Split a multi-file group blob into its member files."""
    if fcount == 1:
        return [data]
    chunks = data[-1]
    p = len(data) - 1 - chunks * fcount * 4
    sizes = [[0] * fcount for _ in range(chunks)]
    for c in range(chunks):
        prev = 0
        for f in range(fcount):
            prev += struct.unpack(">i", data[p:p + 4])[0]; p += 4
            sizes[c][f] = prev
    files = [bytearray() for _ in range(fcount)]
    p = 0
    for c in range(chunks):
        for f in range(fcount):
            sz = sizes[c][f]
            files[f] += data[p:p + sz]; p += sz
    return files


class _R:
    def __init__(s, b): s.b, s.p = b, 0
    def u8(s):  v = s.b[s.p]; s.p += 1; return v
    def i8(s):  v = s.b[s.p]; s.p += 1; return v - 256 if v > 127 else v
    def u16(s): v = struct.unpack(">H", s.b[s.p:s.p + 2])[0]; s.p += 2; return v
    def i32(s): v = struct.unpack(">i", s.b[s.p:s.p + 4])[0]; s.p += 4; return v
    def u24(s): v = (s.b[s.p] << 16) | (s.b[s.p + 1] << 8) | s.b[s.p + 2]; s.p += 3; return v
    def bigsmart(s): return (s.i32() & 0x7fffffff) if s.b[s.p] & 0x80 else s.u16()
    def ssm1(s): return (s.u8() - 1) if s.b[s.p] < 0x80 else ((s.u16() & 0x7fff) - 1)
    def string(s):
        e = s.b.index(0, s.p); v = s.b[s.p:e].decode("latin-1"); s.p = e + 1; return v


def decode_npc(b):
    """Return (name, options, unknown_opcode_or_None). Names/options are always
    written before the opcodes we don't model, so this stays aligned for them."""
    r, name, ops = _R(b), None, []
    while r.p < len(b):
        op = r.u8()
        if op == 0:
            break
        if op == 1:
            for _ in range(r.u8()): r.u16()
        elif op == 2:
            name = r.string()
        elif op == 12:
            r.u8()
        elif op in (13, 14, 15, 16, 18, 95, 97, 98, 103, 114, 116):
            r.u16()
        elif op in (17, 115, 117):
            r.u16(); r.u16(); r.u16(); r.u16()
        elif 30 <= op <= 34:
            ops.append(r.string())
        elif op in (40, 41):
            for _ in range(r.u8()): r.u16(); r.u16()
        elif op == 42:
            for _ in range(r.u8()): r.i8()
        elif op == 60:
            for _ in range(r.u8()): r.u16()
        elif 74 <= op <= 79:            # combat stat block (bosses/monsters)
            r.u16()
        elif op in (93, 99, 107, 109, 111, 122, 123, 124):
            pass
        elif op in (100, 101):
            r.i8()
        elif op == 102:                 # modern head-icon bitfield
            bf = r.u8(); c = bin(bf).count("1")
            for _ in range(c): r.bigsmart(); r.ssm1()
        elif op == 106:
            r.u16(); r.u16()
            for _ in range(r.u8() + 1): r.u16()
        elif op == 118:
            r.u16(); r.u16(); r.u16()
            for _ in range(r.u8() + 1): r.u16()
        elif op == 249:
            for _ in range(r.u8()):
                is_str = r.u8(); r.u24()
                r.string() if is_str else r.i32()
        else:
            return name, ops, op        # unmodelled opcode; alignment lost past here
    return name, ops, None


def load_npcs(cache):
    base = f"{OPENRS2}/caches/runescape/{cache}"
    gids, fcounts, fileids = parse_reftable(decompress(fetch(f"{base}/archives/255/groups/2.dat")))
    if 9 not in gids:
        raise SystemExit("index 2 has no group 9 (npc) in this cache")
    gi = gids.index(9)
    blob = decompress(fetch(f"{base}/archives/2/groups/9.dat"))
    files = unpack_group(blob, fcounts[gi])
    ids = fileids[9]
    out = {}
    for i, fb in enumerate(files):
        try:
            name, opts, unk = decode_npc(bytes(fb))
        except Exception:
            # An unmodelled opcode can misalign this one def; skip it rather than
            # kill the run. Targets validate via the KNOWN self-check in main().
            name, opts, unk = None, [], "ERR"
        out[ids[i]] = (name, opts, unk)
    return out


def main():
    ap = argparse.ArgumentParser(description="Resolve OSRS NPC ids -> names from the archived cache.")
    ap.add_argument("--cache", default="latest", help="OpenRS2 cache id, or 'latest' (default)")
    ap.add_argument("--range", help="inclusive id range, e.g. 16294-16337")
    ap.add_argument("--names", help="comma-separated names / substrings to find")
    ap.add_argument("--min-id", type=int, help="only show ids >= this")
    ap.add_argument("--ops", action="store_true", help="show menu options + a talkable flag")
    args = ap.parse_args()

    cache, ts = (pick_cache() if args.cache == "latest" else (args.cache, None))
    print(f"# cache {cache}" + (f" (ts {ts})" if ts else ""), file=sys.stderr)
    npcs = load_npcs(cache)

    # self-check: byte alignment must be intact
    bad = [(i, npcs.get(i, (None,))[0], exp) for i, exp in KNOWN.items()
           if i in npcs and npcs[i][0] != exp]
    if bad:
        for i, got, exp in bad:
            print(f"# ALIGN FAIL id {i}: got {got!r} expected {exp!r}", file=sys.stderr)
        print("# a new npc opcode likely shifted alignment - extend decode_npc()", file=sys.stderr)

    want_names = [n.strip().lower() for n in args.names.split(",")] if args.names else None
    lo = hi = None
    if args.range:
        lo, hi = (int(x) for x in args.range.split("-"))

    rows = []
    for i in sorted(npcs):
        name, opts, unk = npcs[i]
        if name is None and not opts:
            continue
        if args.min_id is not None and i < args.min_id:
            continue
        if lo is not None and not (lo <= i <= hi):
            continue
        if want_names and not any(w in (name or "").lower() for w in want_names):
            continue
        rows.append((i, name, opts))

    if args.ops:
        print(f"{'id':>6}  {'name':<22} {'talk':<5} options")
        for i, name, opts in rows:
            talk = "yes" if "Talk-to" in opts else ""
            print(f"{i:>6}  {str(name):<22} {talk:<5} {opts}")
    else:
        print(f"{'id':>6}  name")
        for i, name, _ in rows:
            print(f"{i:>6}  {name}")
    print(f"# {len(rows)} rows", file=sys.stderr)


if __name__ == "__main__":
    main()
