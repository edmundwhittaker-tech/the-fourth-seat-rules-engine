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
batches=[[{'status':'FINISHED','completedTurns':30,'activationFailures':0,'caughtPriorityFailures':[],
 'errorLines':[], 'seats':[{'commander':names[(i+rotation)%10],'failures':0} for i in pod]} for pod in pods] for rotation in [0,3,6]]
assert min(m.validate_results(batches,set(names)).values())>=3
for mutate in [lambda x:x.pop(),lambda x:x[0].pop(),lambda x:x[0][0].update(status='TURN_LIMIT'),
 lambda x:x[0][0].update(activationFailures=1),lambda x:x[0][0].update(errorLines=['error']),
 lambda x:x[0][0]['seats'][0].update(failures=1),lambda x:x[0][0]['seats'][0].update(commander='unknown')]:
 bad=copy.deepcopy(batches); mutate(bad)
 try:m.validate_results(bad,set(names))
 except ValueError:pass
 else:raise AssertionError('Invalid evidence was accepted')
assert 'hiddenHand' not in m.selected_summary(dict(batches[0][0],hiddenHand=['private']))
print('ArtifactValidationTest: 9 checks passed')
