#!/usr/bin/env python3
import sys
sys.dont_write_bytecode = True
import importlib.util
import copy
import pathlib
spec=importlib.util.spec_from_file_location('package_artifact', pathlib.Path(__file__).with_name('package-artifact.py'))
m=importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
names=['commander-'+str(i) for i in range(10)]
pods=[[0,1,2,3],[4,5,6,7],[8,9,0,6]]
batches=[[{'status':'FINISHED','completedTurns':30,'runtimeMs':30000,'largestObservedProgressGapMs':1000,'loopDraws':0,'activationFailures':0,'caughtPriorityFailures':[],
 'errorLines':[], 'seats':[{'commander':names[(i+rotation)%10],'failures':0} for i in pod]} for pod in pods] for rotation in [0,3,6]]
assert min(m.validate_results(batches,set(names)).values())>=3
for mutate in [lambda x:x.pop(),lambda x:x[0].pop(),lambda x:x[0][0].update(status='TURN_LIMIT'),
 lambda x:x[0][0].update(completedTurns=121),lambda x:x[0][0].update(runtimeMs=300001),lambda x:x[0][0].update(largestObservedProgressGapMs=30001),lambda x:x[0][0].update(loopDraws=1),lambda x:x[0][0].pop('largestObservedProgressGapMs'),lambda x:x[0][0].update(activationFailures=1),lambda x:x[0][0].update(errorLines=['error']),
 lambda x:x[0][0]['seats'][0].update(failures=1),lambda x:x[0][0]['seats'][0].update(commander='unknown')]:
 bad=copy.deepcopy(batches); mutate(bad)
 try:m.validate_results(bad,set(names))
 except ValueError:pass
 else:raise AssertionError('Invalid evidence was accepted')
assert 'hiddenHand' not in m.selected_summary(dict(batches[0][0],hiddenHand=['private']))
engine = ''.join(label+': 1 passed, 0 failed real-engine checks\n' for label in m.ENGINE_SUITES)+'FourthSeatAdditionalCostContractTest: 27 checks passed\n'
counts,cost=m.validate_engine_summary(engine)
assert sum(counts.values()) == len(m.ENGINE_SUITES) and cost == 27
for label in m.ENGINE_SUITES:
 for bad in (engine.replace(label+': 1 passed, 0 failed',label+': 1 passed, 1 failed'),engine.replace(label+': 1 passed, 0 failed real-engine checks\n',''),engine+label+': 1 passed, 0 failed real-engine checks\n'):
  try:m.validate_engine_summary(bad)
  except ValueError:pass
  else:raise AssertionError('Invalid fixture evidence was accepted')
print('ArtifactValidationTest: '+str(15+3*len(m.ENGINE_SUITES))+' checks passed')
