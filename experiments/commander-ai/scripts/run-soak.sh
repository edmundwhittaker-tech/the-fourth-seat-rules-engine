#!/usr/bin/env bash
set -euo pipefail
ROOT=${1:?Usage: run-soak.sh BUILT_XMAGE MAVEN_REPO OUTPUT_DIR [SEED] [TURN_LIMIT] [SECONDS_PER_POD] [DECK_ROTATION]}
REPO=${2:?}; OUT=${3:?}; SEED=${4:-20261016}; TURN_LIMIT=${5:-120}; SECONDS_PER_POD=${6:-300}; ROTATION=${7:-0}
HERE=$(cd "$(dirname "$0")/.." && pwd); mkdir -p "$OUT"; OUT=$(cd "$OUT" && pwd)
[[ ! -e "$OUT/pod-0.log" ]] || { echo "Use a fresh output directory; refusing to overwrite evidence" >&2; exit 2; }
python3 "$HERE/adapter/soak/prepare-decks.py" "$ROOT" "$HERE/../../integrations/fourth-seat/gateway/ai-decks.json" "$OUT/decks" | tee "$OUT/deck-manifest.log"
CP_FILE="$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/fourth-seat-runtime-classpath.txt"
[[ -s "$CP_FILE" ]] || { echo "BLOCKED: run build-adapter.sh to generate exact Maven runtime classpath" >&2; exit 2; }
CP="$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes:$(cat "$CP_FILE")"
sha256sum "$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes/mage/player/ai/ComputerPlayerFourthSeat.class" \
 "$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes/fourthseat/ai/CommanderPolicy.class" \
 "$ROOT/Mage.Server.Plugins/Mage.Player.AI/src/mage/player/ai/ComputerPlayerFourthSeat.java" "$ROOT/Mage.Server.Plugins/Mage.Player.AI/src/fourthseat/ai/CommanderPolicy.java" > "$OUT/build-hashes.txt"
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
 grep -E 'RESULT|TURN_DONE|HEARTBEAT|Exception|ERROR|WARN' "$OUT/pod-$i.log" | tail -8 || true
 [[ $status == 0 ]] || failed=1
 done
python3 "$HERE/adapter/soak/summarize.py" "$OUT"
exit "$failed"
