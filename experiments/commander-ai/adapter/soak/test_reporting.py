"""Synthetic reporting contracts only. These are NOT engine acceptance evidence."""
import copy, json, pathlib, subprocess, sys, tempfile, unittest
from loop_diagnostics import loop_diagnostics
HERE=pathlib.Path(__file__).parent
YES='EVENT A has confirmed that the game is a draw because of infinite looping.\n'
NO='EVENT B has NOT confirmed that the game is a draw because of infinite looping.\n'
DRAW='EVENT For A the game is a draw.\n'
class ReportingTests(unittest.TestCase):
    def test_loop_accepted(self): self.assertEqual(loop_diagnostics(YES+YES+DRAW)['infiniteLoopDrawEvents'],1)
    def test_loop_refused(self): self.assertEqual(loop_diagnostics(YES+NO+'RESULT status=FINISHED')['infiniteLoopDrawEvents'],0)
    def test_refusal_only(self): self.assertEqual(loop_diagnostics(NO)['infiniteLoopDrawEvents'],0)
    def test_ordinary_finish(self): self.assertEqual(loop_diagnostics('RESULT status=FINISHED')['infiniteLoopDrawEvents'],0)
    def test_refused_unrelated_draw(self): self.assertEqual(loop_diagnostics(YES+NO+DRAW)['infiniteLoopDrawEvents'],0)
    def test_reporting_gate(self):
        with tempfile.TemporaryDirectory() as d:
            root=pathlib.Path(d)
            pods=((0,1,2,3),(4,5,6,7),(8,9,0,6))
            for b in range(3):
                batch=root/('batch'+str(b));batch.mkdir()
                (batch/'run-config.json').write_text(json.dumps(dict(seed=20261016+b*100,turnLimit=120,secondsPerPod=300,rotation=b*3)))
                (batch/'deck-manifest.log').write_text('DECK_FILE_SHA256 9e5625b2060db2ddd457ade4b67f623716e301f77cb72178efe052920e94748c\n')
                (batch/'all-ai-class-hashes.txt').write_text('a'*64+'  fourthseat/ai/Policy.class\n')
                rows=[]
                for i,pod in enumerate(pods):
                    ids=[(v+b*3)%10 for v in pod]
                    rows.append(dict(pod='pod-'+str(i),decks=[str(v)+' Commander'+str(v)+' cards=99 commander=1' for v in ids],seats=[dict(commander='Commander'+str(v)) for v in ids],seed=20261016+b*100+i,turnLimit=120,status='FINISHED',resultCount=1,processExitCode=0,turnEvidenceConsistent=True,completedTurns=1,runtimeMs=1000,largestObservedProgressGapMs=1000,infiniteLoopDrawEvents=0,activationFailures=0,caughtPriorityFailures=[],errorLines=[]))
                (batch/'results.json').write_text(json.dumps(rows))
            def run(): return subprocess.run([sys.executable,str(HERE/'summarize-batches.py'),str(root)],stdout=subprocess.DEVNULL).returncode
            self.assertEqual(run(),0)
            p=root/'batch0/results.json'; original=json.loads(p.read_text())
            mutations=[('processExitCode',124),('resultCount',2),('turnEvidenceConsistent',False),('status','TURN_BOUND'),('runtimeMs',300001),('completedTurns',121),('largestObservedProgressGapMs',None),('largestObservedProgressGapMs',30001),('infiniteLoopDrawEvents',1),('activationFailures',1),('seed',0)]
            for key,value in mutations:
                rows=copy.deepcopy(original);rows[0][key]=value;p.write_text(json.dumps(rows))
                with self.subTest(key=key,value=value): self.assertNotEqual(run(),0)
            p.write_text(json.dumps(original))
            (root/'batch2/all-ai-class-hashes.txt').write_text('b'*64+'  fourthseat/ai/Policy.class\n')
            self.assertNotEqual(run(),0)
    def test_raw_result_cannot_hide_timeout(self):
        with tempfile.TemporaryDirectory() as d:
            p=pathlib.Path(d)
            for i in range(3):
                (p/('pod-'+str(i)+'.log')).write_text('START seed=20261016 turnLimit=120\nTURN_DONE turn=1 elapsedMs=1000\nRESULT status=FINISHED completedTurns=1 elapsedMs=1001\n')
            (p/'summary.log').write_text('POD 0 exit=124\nPOD 1 exit=0\nPOD 2 exit=0\n')
            r=subprocess.run([sys.executable,str(HERE/'summarize.py'),str(p)],stdout=subprocess.DEVNULL)
            self.assertNotEqual(r.returncode,0)
            self.assertEqual(json.loads((p/'results.json').read_text())[0]['processExitCode'],124)
if __name__=='__main__': unittest.main()
