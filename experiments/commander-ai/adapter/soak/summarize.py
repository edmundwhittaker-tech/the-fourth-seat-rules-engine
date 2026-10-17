from loop_diagnostics import loop_diagnostics
import pathlib,re,json,sys,html,collections
root=pathlib.Path(sys.argv[1]); rows=[]
for p in sorted(root.glob('pod-*.log')):
 s=p.read_text(errors='replace'); times=[int(x)/1e6 for x in re.findall(r'elapsedNanos=(\d+)',s)]; times.sort()
 turns=re.findall(r'TURN_DONE turn=(\d+) elapsedMs=(\d+)',s)
 result=re.search(r'RESULT status=(\S+) completedTurns=(\d+) elapsedMs=(\d+)',s)
 row={'pod':p.stem,'decks':re.findall(r'^DECK (.*)$',s,re.M),'status':result[1] if result else 'NO_RESULT_TIMEOUT_OR_ERROR','completedTurns':int(result[2]) if result else len(turns),'runtimeMs':int(result[3]) if result else None,'lastCompletedTurn':int(turns[-1][0]) if turns else None,'castEvents':len(re.findall('EVENT .* casts ',s)),'activationFailures':s.count('activated=false'),'caughtPriorityFailures':re.findall(r'priority failed; passing: (.*)',s),'errorLines':[l for l in s.splitlines() if l.startswith('ERROR') or l.startswith('Exception in thread')],'decisionSamples':len(times),'maxDecisionMs':round(max(times),3) if times else None,'p95DecisionMs':round(times[int(len(times)*.95)],3) if times else None,'passesWithCandidates':len(re.findall(r'pass reason=\S+ candidates=[1-9]',s))}
 plain=html.unescape(re.sub(r'<[^>]+>','',s))
 seats=[]
 for deck in row['decks']:
  name=deck.split(' ',1)[1].split(' cards=')[0]
  prefix='EVENT '+name+' '
  casts=[l for l in plain.splitlines() if l.startswith(prefix+'casts ')]
  activations=[l for l in plain.splitlines() if l.startswith(prefix+'activates: ')]
  seats.append({'commander':name,'landPlays':sum(l.startswith(prefix+'plays ') for l in plain.splitlines()),'spellCasts':len(casts),'activatedAbilities':len(activations),'commanderCasts':sum(l.startswith(prefix+'casts '+name+' [') for l in casts),'commanderActivations':sum((' from '+name+' [') in l for l in activations),'attackDeclarations':sum(l.startswith(prefix+'attacks ') for l in plain.splitlines()),'failures':sum('decision=PLAN:'+name+':' in l and 'activated=false' in l for l in s.splitlines())})
 row['seats']=seats
 row.update(loop_diagnostics(plain))
 row['resultCount']=len(re.findall(r'^RESULT status=',s,re.M))
 exits=re.findall(r'^POD '+re.escape(p.stem.split('-')[-1])+r' exit=(\d+)', (root/'summary.log').read_text() if (root/'summary.log').exists() else '',re.M)
 row['processExitCode']=int(exits[0]) if len(exits)==1 else None
 row['turnEvidenceConsistent']=len(turns)==row['completedTurns'] and all(int(b[0])>int(a[0]) and int(b[1])>=int(a[1]) for a,b in zip(turns,turns[1:]))
 starts=re.findall(r'^START seed=(\d+) turnLimit=(\d+)',s,re.M)
 row['seed']=int(starts[0][0]) if len(starts)==1 else None
 row['turnLimit']=int(starts[0][1]) if len(starts)==1 else None
 observed=[int(x) for x in re.findall(r'elapsedMs=(\d+)',s)]
 row['observedRuntimeMs']=max(observed) if observed else None
 row['runtimeMeasuredToCompletion']=bool(result)
 previous=0; intervals=[]
 for turn,elapsed in turns:
  elapsed=int(elapsed);intervals.append({'completedTurn':int(turn),'intervalMs':max(0,elapsed-previous)});previous=elapsed
 row['largestCompletedTurnIntervalMs']=max((i['intervalMs'] for i in intervals),default=None)
 row['largestObservedProgressGapMs']=max([i['intervalMs'] for i in intervals]+[max(0,(row['observedRuntimeMs'] or 0)-previous)])
 row['slowestCompletedTurnIntervals']=sorted(intervals,key=lambda i:i['intervalMs'],reverse=True)[:5]
 row['failedActionSources']=dict(collections.Counter(re.findall(r'activation-failed source=(.*?) rule=',s)))
 row['guardReasons']=dict(collections.Counter(re.findall(r'fourth-seat guard=(\S+)',s)))
 rows.append(row)
(root/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(json.dumps(rows,indent=2))

if any(r["status"] != "FINISHED" or r["resultCount"] != 1 or r["processExitCode"] != 0 or not r["turnEvidenceConsistent"] or r["turnLimit"] != 120 or not 0 < r["completedTurns"] <= 120 or r["runtimeMs"] is None or r["runtimeMs"] > 300000 or r["largestObservedProgressGapMs"] is None or r["largestObservedProgressGapMs"] > 30000 or r["loopDraws"] or r["activationFailures"] or r["caughtPriorityFailures"] or r["errorLines"] for r in rows) or len(rows)!=3:
 raise SystemExit(1)
