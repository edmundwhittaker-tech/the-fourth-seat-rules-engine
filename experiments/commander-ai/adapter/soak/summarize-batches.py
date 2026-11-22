"""Strict nine-game acceptance; never promote historical/partial evidence."""
import collections, hashlib, json, pathlib, re, sys
root = pathlib.Path(sys.argv[1]); games=[]; coverage={}; failures=collections.Counter(); manifests=[]; problems=[]
base_pods = ((0,1,2,3),(4,5,6,7),(8,9,0,6))
expected_batches = {'batch0','batch1','batch2'}
actual_batches = {p.name for p in root.glob('batch[0-9]*') if p.is_dir()}
if actual_batches != expected_batches: problems.append('Exactly batch0, batch1 and batch2 are required')
for b in range(3):
    batch=root/('batch'+str(b))
    if not (batch/'results.json').exists(): problems.append('Missing results: '+str(batch)); continue
    config=batch/'run-config.json'
    expected_config=dict(seed=20261016+b*100,turnLimit=120,secondsPerPod=300,rotation=b*3)
    if not config.exists() or json.loads(config.read_text()) != expected_config: problems.append('Missing/mismatched exact run configuration: '+str(batch))
    deck_manifest=batch/'deck-manifest.log'
    if not deck_manifest.exists() or 'DECK_FILE_SHA256 9e5625b2060db2ddd457ade4b67f623716e301f77cb72178efe052920e94748c' not in deck_manifest.read_text(): problems.append('Frozen deck identity missing/mismatched')
    rows=json.loads((batch/'results.json').read_text())
    manifest=batch/'all-ai-class-hashes.txt'
    text=manifest.read_text() if manifest.exists() else ''
    lines=text.splitlines()
    if not lines or any(not re.fullmatch(r'[0-9a-f]{64}  (?:fourthseat/ai/|mage/player/ai/).*\.class',l) for l in lines): problems.append('Missing/invalid recursive class manifest: '+str(batch))
    manifests.append(text)
    if {r.get('pod') for r in rows} != {'pod-0','pod-1','pod-2'} or len(rows)!=3: problems.append('Exactly three unique pods required: '+str(batch))
    for row in rows:
        row=dict(row,batch=batch.name); games.append(row); failures.update(row.get('failedActionSources',{}))
        try: i=int(row['pod'].split('-')[1]); expected=[(d+b*3)%10 for d in base_pods[i]]
        except (ValueError,KeyError,IndexError): problems.append('Invalid pod identity'); continue
        if row.get('seed') != 20261016+b*100+i: problems.append('Seed mismatch: '+batch.name+'/'+row['pod'])
        decks=row.get('decks',[])
        if len(decks)!=4 or [int(d.split(' ',1)[0]) for d in decks] != expected or any(not d.endswith(' cards=99 commander=1') for d in decks): problems.append('Frozen pod rotation/deck size mismatch: '+batch.name+'/'+row['pod'])
        for seat in row.get('seats',[]):
            name=seat['commander']; total=coverage.setdefault(name,dict(appearances=0,landPlays=0,spellCasts=0,activatedAbilities=0,commanderCasts=0,commanderActivations=0,attackDeclarations=0,failures=0))
            total['appearances']+=1
            for key in total:
                if key!='appearances': total[key]+=seat.get(key,0)
        if len(row.get('seats',[])) != 4: problems.append('Missing seat metrics')
        if row.get('status')!='FINISHED' or row.get('resultCount')!=1 or row.get('processExitCode')!=0 or row.get('turnEvidenceConsistent') is not True: problems.append('Missing/invalid natural completion evidence')
        if row.get('turnLimit')!=120 or not 0 < row.get('completedTurns',0) <= 120 or not isinstance(row.get('runtimeMs'),int) or not 0 < row['runtimeMs'] <= 300000: problems.append('Turn/runtime bound failure')
        if not isinstance(row.get('largestObservedProgressGapMs'),int) or not 0 <= row['largestObservedProgressGapMs'] <= 30000: problems.append('Missing/excessive progress gap')
        if row.get('infiniteLoopDrawEvents') is None or row.get('infiniteLoopDrawEvents',1)>0: problems.append('Missing loop evidence or actual loop draw')
        if row.get('activationFailures')!=0 or row.get('caughtPriorityFailures')!=[] or row.get('errorLines')!=[]: problems.append('Action/exception/error evidence not clean')
identity=bool(manifests) and len(manifests)==3 and bool(manifests[0]) and len(set(manifests))==1
if not identity: problems.append('Recursive compiled artifacts differ or are missing')
if len(games)!=9 or len(coverage)!=10 or any(c['appearances']<3 for c in coverage.values()): problems.append('Nine games / ten decks / three appearances coverage incomplete')
report=dict(games=len(games),finishedMarkers=sum(g.get('status')=='FINISHED' for g in games),naturalCompletions=sum(g.get('status')=='FINISHED' and g.get('infiniteLoopDrawEvents')==0 for g in games),completedTurns=sum(g.get('completedTurns',0) for g in games),runtimeSeconds=round(sum((g.get('runtimeMs') or 0)/1000 for g in games),3),observedRuntimeSecondsLowerBound=round(sum((g.get('observedRuntimeMs') or g.get('runtimeMs') or 0)/1000 for g in games),3),progressGapLimitMs=30000,excessiveOrMissingProgressGapGames=sum(g.get('largestObservedProgressGapMs') is None or g['largestObservedProgressGapMs']>30000 for g in games),infiniteLoopDrawGames=sum(g.get('infiniteLoopDrawEvents',0)>0 for g in games),incompleteGames=sum(g.get('status')!='FINISHED' for g in games),activationFailures=sum(g.get('activationFailures',0) for g in games),failedActionSources=dict(failures),caughtPriorityFailures=sum(len(g.get('caughtPriorityFailures',[])) for g in games),errors=sum(len(g.get('errorLines',[])) for g in games),identicalAllAiCompiledHashes=identity,compiledManifestSha256=hashlib.sha256(manifests[0].encode()).hexdigest() if manifests else None,coverage=coverage,pods=games,acceptanceFailures=sorted(set(problems)),accepted=not problems)
(root/'aggregate-results.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='pods'},indent=2))
raise SystemExit(1 if problems else 0)
