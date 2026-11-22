#!/usr/bin/env python3
"""Detect silent deck/profile drift without fetching or changing any deck."""
import hashlib,json,pathlib,re
root=pathlib.Path(__file__).resolve().parents[1]
pool=root.parents[1]/'integrations/fourth-seat/gateway/ai-decks.json'
manifest=json.loads((root/'deck-snapshot.json').read_text())
raw=pool.read_bytes()
assert hashlib.sha256(raw).hexdigest()==manifest['poolSha256'],'Frozen source pool changed; review strategy and regenerate manifest explicitly'
data=json.loads(raw)
source=(root/'src/main/java/fourthseat/ai/DeckPlan.java').read_text()
profiles={}
for m in re.finditer(r'register\(("(?:[^"\\]|\\.)*"), new String\[\] \{(.*?)\}\);',source,re.S):
    name=json.loads(m.group(1))
    cards=[json.loads(x).split(' // ')[0] for x in re.findall(r'"(?:[^"\\]|\\.)*"',m.group(2))]
    profiles[name]=set(cards)
assert len(profiles)==10,'Expected ten explicit profiles'
for d in data['decks']:
    name=d['deck']['commanders'][0]['cardName']
    expected={x['cardName'].split(' // ')[0] for x in d['deck']['cards']+d['deck']['commanders']}
    assert profiles[name]==expected,(name,profiles[name]^expected)
    print('PASS frozen membership:',name,len(expected),'unique deck/commander names')
print('Frozen deck pool and all ten policy memberships match.')
