#!/usr/bin/env bash
set -euo pipefail
ROOT=${1:?Usage: run-soak.sh BUILT_XMAGE MAVEN_REPO OUTPUT_DIR [SEED] [TURN_LIMIT] [SECONDS_PER_POD] [DECK_ROTATION]}
REPO=${2:?}; OUT=${3:?}; SEED=${4:-20261016}; TURN_LIMIT=${5:-120}; SECONDS_PER_POD=${6:-300}; ROTATION=${7:-0}
HERE=$(cd "$(dirname "$0")/.." && pwd); mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd)
[[ ! -e "$OUT/pod-0.log" ]] || { echo "Use a fresh output directory; refusing to overwrite evidence" >&2; exit 2; }
python3 "$HERE/adapter/soak/prepare-decks.py" "$ROOT" "$HERE/../../integrations/fourth-seat/gateway/ai-decks.json" "$OUT/decks" | tee "$OUT/deck-manifest.log"
if [[ -n "${SOAK_ARTIFACT_CP_FILE:-}" ]]; then
 [[ -s "$SOAK_ARTIFACT_CP_FILE" ]] || { echo "Missing exact artifact classpath" >&2; exit 2; }
 CP=$(cat "$SOAK_ARTIFACT_CP_FILE")
 python3 - "$CP" "$OUT" <<'ARTIFACT'
import hashlib,pathlib,sys,zipfile
entries=sys.argv[1].strip().split(':'); out=pathlib.Path(sys.argv[2]); jar=pathlib.Path(entries[0])
if any(not pathlib.Path(p).is_file() or not p.endswith('.jar') for p in entries): raise SystemExit('Artifact CP must contain existing JAR files only')
with zipfile.ZipFile(jar) as z:
 names=sorted(n for n in z.namelist() if n.endswith('.class') and (n.startswith('fourthseat/ai/') or n.startswith('mage/player/ai/FourthSeat') or n.startswith('mage/player/ai/ComputerPlayerFourthSeat')))
 if not names: raise SystemExit('No experimental classes in exact artifact')
 (out/'all-ai-class-hashes.txt').write_text(''.join(hashlib.sha256(z.read(n)).hexdigest()+'  '+n+'\n' for n in names))
(out/'build-hashes.txt').write_text(hashlib.sha256(jar.read_bytes()).hexdigest()+'  '+str(jar)+'\n')
(out/'artifact-classpath.txt').write_text(sys.argv[1]+'\n')
ARTIFACT
else
CP_FILE="$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/fourth-seat-runtime-classpath.txt"
[[ -s "$CP_FILE" ]] || { echo "BLOCKED: run build-adapter.sh to generate exact Maven runtime classpath" >&2; exit 2; }
CP="$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes:$(cat "$CP_FILE")"
sha256sum "$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes/mage/player/ai/ComputerPlayerFourthSeat.class" \
 "$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes/fourthseat/ai/CommanderPolicy.class" \
 "$ROOT/Mage.Server.Plugins/Mage.Player.AI/src/mage/player/ai/ComputerPlayerFourthSeat.java" "$ROOT/Mage.Server.Plugins/Mage.Player.AI/src/fourthseat/ai/CommanderPolicy.java" > "$OUT/build-hashes.txt"
python3 - "$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes" "$OUT/all-ai-class-hashes.txt" <<'HASHES'
import hashlib,pathlib,sys
root=pathlib.Path(sys.argv[1]); files=sorted(list((root/'fourthseat/ai').rglob('*.class'))+list((root/'mage/player/ai').glob('*FourthSeat*.class')))
if not files: raise SystemExit('No experimental class files')
pathlib.Path(sys.argv[2]).write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(root).as_posix()+'\n' for p in files))
HASHES
fi
python3 - "$OUT/run-config.json" "$SEED" "$TURN_LIMIT" "$SECONDS_PER_POD" "$ROTATION" <<'CONFIG'
import json,pathlib,sys
pathlib.Path(sys.argv[1]).write_text(json.dumps(dict(seed=int(sys.argv[2]),turnLimit=int(sys.argv[3]),secondsPerPod=int(sys.argv[4]),rotation=int(sys.argv[5])),indent=2)+'\n')
CONFIG
BUILD=$(mktemp -d);trap 'rm -rf "$BUILD"' EXIT
javac -proc:none --release 8 -cp "$CP" -d "$BUILD" "$HERE"/adapter/soak/mage/player/ai/*.java
java -Dlog4j.configuration="file:$HERE/adapter/soak/log4j.properties" -Xmx1400m -cp "$BUILD:$CP" mage.player.ai.SoakClasspathCheck > "$OUT/classpath-check.log" 2>&1
if grep -q '^ERROR' "$OUT/classpath-check.log"; then cat "$OUT/classpath-check.log" >&2; exit 2; fi
# Each of the ten actual decks appears at least once; no human and no server needed.
PODS=('0 1 2 3' '4 5 6 7' '8 9 0 6')
for i in 0 1 2; do
 rotated=""; for deck in ${PODS[$i]}; do rotated+="$(((deck + ROTATION) % 10)) "; done; PODS[$i]=$rotated
done
failed=0
for i in 0 1 2;do
 set +e
 timeout --signal=TERM --kill-after=5 "$SECONDS_PER_POD" java -Dlog4j.configuration="file:$HERE/adapter/soak/log4j.properties" -Xmx1400m -ea -cp "$BUILD:$CP" mage.player.ai.FourthSeatSoak "$OUT/decks" "$((SEED+i))" "$TURN_LIMIT" ${PODS[$i]} > "$OUT/pod-$i.log" 2>&1
 status=$?;set -e
 echo "POD $i exit=$status decks=${PODS[$i]}" | tee -a "$OUT/summary.log"
 if [[ $status == 124 ]]; then
   echo "TIMEOUT pod=$i limitSeconds=$SECONDS_PER_POD exit=$status; NOT a completed game"
 elif [[ $status == 137 ]]; then
   echo "KILLED pod=$i exit=137 (timeout hard-kill or external kill); NOT a completed game"
 fi
 if grep -qE 'activation-failed|priority failed|^ERROR|Exception in thread' "$OUT/pod-$i.log"; then
   echo "BOUNDED_DIAGNOSTICS pod=$i first10 action-failure/error lines (max1600 characters each)"
   grep -E 'activation-failed|priority failed|^ERROR|Exception in thread' "$OUT/pod-$i.log" | awk 'NR <= 10 { print substr($0,1,1600) }' || true
 fi
 if grep -q '^SLOW_TURN_STACK' "$OUT/pod-$i.log"; then
   echo "BOUNDED_SLOW_TURN_DIAGNOSTICS pod=$i first stack sample only"
   grep -E '^SLOW_TURN_(STACK|FRAME)' "$OUT/pod-$i.log" | awk 'NR <= 25 { print substr($0,1,500) }' || true
 fi
 grep -E 'RESULT|TURN_DONE|HEARTBEAT|Exception|ERROR|WARN'  "$OUT/pod-$i.log" | tail -8 || true
 [[ $status == 0 ]] || failed=1
 done
python3 "$HERE/adapter/soak/summarize.py" "$OUT"
exit "$failed"
