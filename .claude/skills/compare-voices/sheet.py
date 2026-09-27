import json, base64, subprocess, os, html, sys
# Usage: sheet.py <cases.json> <out-dir> <before-label> <after-label> <provider>
CASES, D, BEFORE, AFTER, PROVIDER = sys.argv[1:]
cases = json.load(open(CASES))['cases']
res = {b: {r['key']: r for r in json.load(open(f'{D}/{b}/results.json'))} for b in ('before', 'after')}

def audio(b, key):
    wav = f'{D}/{b}/{key}.wav'
    if not os.path.exists(wav):
        return '<span class="miss">no clip</span>'
    m4a = f'{D}/{b}/{key}.m4a'
    subprocess.run(['afconvert', '-f', 'm4af', '-d', 'aac', '-b', '64000', wav, m4a], check=True)
    data = base64.b64encode(open(m4a, 'rb').read()).decode()
    return f'<audio controls preload="none" src="data:audio/mp4;base64,{data}"></audio>'

def meta(b, key):
    r = res[b].get(key)
    if not r:
        return ''
    region = next((t.split('voiceRegion=')[1].rstrip(')') for t in r['trace'] if 'voiceRegion=' in t), None)
    bits = [f'voice <code>{html.escape(r.get("voice", "?"))}</code>']
    if region:
        bits.append(f'region <code>{html.escape(region)}</code>')
    bits.append(f'accent: {html.escape(r["accent"] or "")}')
    return '<div class="meta">' + '<br>'.join(bits) + '</div>'

groups = list(dict.fromkeys(c['group'] for c in cases))

rows = []
n = 0
for title in groups:
    rows.append(f'<h2>{html.escape(title)}</h2>')
    for c in cases:
        k = c['key']
        if c['group'] != title:
            continue
        if c.get('missing'):
            rows.append(f'<div class="card"><h3>{html.escape(k)}</h3><p class="miss">Not in game: {html.escape(c["missing"])}</p></div>')
            continue
        n += 1
        who = c.get('name') or c['kind'].title()
        rows.append(f'''<div class="card" data-key="{k}">
<h3>{n}. {html.escape(who)} <small>{html.escape(k)}{" #" + str(c["id"]) if c.get("id") is not None else ""}</small></h3>
<p class="line">"{html.escape(c["line"])}"</p>
<div class="pair"><div><b>Before</b>{audio("before", k)}{meta("before", k)}</div>
<div><b>After</b>{audio("after", k)}{meta("after", k)}</div></div>
<div class="verdict"><label><input type="radio" name="{k}" value="go"> Go</label>
<label><input type="radio" name="{k}" value="nogo"> No go</label>
<input class="note" placeholder="note (optional)"></div></div>''')

page = f'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Voice QA Sheet</title><style>
:root{{--bg:#fafaf8;--fg:#1c1c1a;--card:#fff;--line:#ddd;--mut:#666;--go:#1e7d3a;--no:#b3261e}}
@media (prefers-color-scheme:dark){{:root:not([data-theme="light"]){{--bg:#161615;--fg:#eee;--card:#222220;--line:#3a3a38;--mut:#aaa;--go:#5cc27a;--no:#f08a80}}}}
:root[data-theme="dark"]{{--bg:#161615;--fg:#eee;--card:#222220;--line:#3a3a38;--mut:#aaa}}
body{{background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif;margin:0;padding:16px;max-width:980px;margin:auto}}
.card{{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px;margin:10px 0}}
h3{{margin:0 0 4px;font-size:16px}} small{{color:var(--mut);font-weight:normal}}
.line{{font-style:italic;margin:4px 0 10px}} .pair{{display:grid;grid-template-columns:1fr 1fr;gap:12px}}
@media (max-width:640px){{.pair{{grid-template-columns:1fr}}}}
audio{{width:100%;margin:4px 0}} .meta{{font-size:12px;color:var(--mut)}} .miss{{color:var(--mut)}}
.verdict{{margin-top:8px;display:flex;gap:14px;align-items:center;flex-wrap:wrap}} .note{{flex:1;min-width:160px;padding:4px 6px}}
#out{{width:100%;height:140px}} button{{padding:8px 14px;font-size:15px}}
</style></head><body>
<h1>Voice QA: before vs after</h1>
<p>Each speaker says one real in-game line through the plugin's own code at both versions, on {html.escape(PROVIDER)} with default settings. Before: <code>{html.escape(BEFORE)}</code>. After: <code>{html.escape(AFTER)}</code>. Mark Go when After is as good as or better than Before.</p>
{"".join(rows)}
<h2>Results</h2><button onclick="collect()">Copy results</button><textarea id="out"></textarea>
<script>
function collect(){{const lines=[];document.querySelectorAll('.card[data-key]').forEach(c=>{{const k=c.dataset.key;const v=c.querySelector('input[type=radio]:checked');const n=c.querySelector('.note').value;lines.push(k+': '+(v?v.value.toUpperCase():'-')+(n?' ('+n+')':''))}});const t=lines.join('\\n');document.getElementById('out').value=t;try{{navigator.clipboard.writeText(t)}}catch(e){{}}}}
</script></body></html>'''
open(f'{D}/voice-qa-sheet.html', 'w').write(page)
print(f'{D}/voice-qa-sheet.html', n, 'cards')
