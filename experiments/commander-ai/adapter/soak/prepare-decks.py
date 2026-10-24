import json,pathlib,re,sys,hashlib
source=pathlib.Path(sys.argv[1]); deckfile=pathlib.Path(sys.argv[2]); out=pathlib.Path(sys.argv[3]); out.mkdir(parents=True,exist_ok=True)
def norm(s): return re.sub('[^a-z0-9]','',s.lower())
classes={norm(p.stem):'.'.join(p.relative_to(source/'Mage.Sets/src').with_suffix('').parts) for p in (source/'Mage.Sets/src/mage/cards').rglob('*.java')}
classes.update({norm(p.stem): 'mage.cards.basiclands.'+p.stem for p in (source/'Mage/src/main/java/mage/cards/basiclands').glob('*.java')})
data=json.loads(deckfile.read_text()); missing=[]
for i,row in enumerate(data['decks']):
 lines=[]
 for zone in ['cards','commanders']:
  for card in row['deck'][zone]:
   name=card['cardName']; key=norm(name.split(' // ')[0]); cls=classes.get(norm(name)) or classes.get(key)
   if not cls: missing.append(name);continue
   lines.append('\t'.join([zone,str(card['amount']),name,cls]))
 (out/f'{i}.tsv').write_text('\n'.join(lines)+'\n')
 print(i,row['deck']['commanders'][0]['cardName'],sum(c['amount'] for z in ['cards','commanders'] for c in row['deck'][z]))
print('DECK_FILE_SHA256',hashlib.sha256(deckfile.read_bytes()).hexdigest())
if missing: raise SystemExit('UNSUPPORTED CARD NAMES (no substitutions): '+repr(missing))
