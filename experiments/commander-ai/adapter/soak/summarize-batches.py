"""Aggregate completed run-soak output directories without hiding failed games."""
import collections,json,pathlib,sys
root=pathlib.Path(sys.argv[1]);games=[];coverage={};failures=collections.Counter();hashes=[]
for batch in sorted(root.glob('batch[0-9]*')):
 if not batch.is_dir(): continue
 result=batch/'results.json'
 if not result.exists(): raise SystemExit('Missing completed batch summary: '+str(batch))
 rows=json.loads(result.read_text());h=(batch/'build-hashes.txt').read_text().splitlines();hashes.append([line.split()[0] for line in h[:2]])
 for row in rows:
  row=dict(row,batch=batch.name);games.append(row);failures.update(row.get('failedActionSources',{}))
  for seat in row['seats']:
   name=seat['commander'];total=coverage.setdefault(name,dict(appearances=0,landPlays=0,spellCasts=0,activatedAbilities=0,commanderCasts=0,commanderActivations=0,attackDeclarations=0,failures=0))
   total['appearances']+=1
   for key in total:
    if key!='appearances':total[key]+=seat.get(key,0)
report={'games':len(games),'naturalCompletions':sum(g['status']=='FINISHED' for g in games),'completedTurns':sum(g['completedTurns'] for g in games),'runtimeSeconds':round(sum((g['runtimeMs'] or 0)/1000 for g in games),3),'activationFailures':sum(g['activationFailures'] for g in games),'failedActionSources':dict(failures),'caughtPriorityFailures':sum(len(g['caughtPriorityFailures']) for g in games),'errors':sum(len(g['errorLines']) for g in games),'identicalMainCompiledHashes':bool(hashes) and all(h==hashes[0] for h in hashes),'compiledClassHashes':hashes[0] if hashes else [],'coverage':coverage,'pods':games}
(root/'aggregate-results.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='pods'},indent=2))
if len(coverage)!=10 or any(c['appearances']<3 for c in coverage.values()) or report['naturalCompletions']!=9 or report['activationFailures'] or report['caughtPriorityFailures'] or report['errors'] or not report['identicalMainCompiledHashes']:
 raise SystemExit(1)
