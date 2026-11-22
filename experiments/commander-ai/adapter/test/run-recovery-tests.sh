#!/usr/bin/env bash
set -euo pipefail
BUILD="${1:?Usage: run-recovery-tests.sh BUILD_DIR [EXACT_ARTIFACT_CLASSPATH_FILE]}"
HERE="$(cd "$(dirname "$0")" && pwd)"
if [[ -n "${2:-}" ]]; then
  [[ -s "$2" ]] || { echo "BLOCKED: exact artifact classpath file absent" >&2; exit 2; }
  CP="$(cat "$2")"
else
  [[ -s "$BUILD/runtime-classpath.txt" && -f "$BUILD/classes/mage/player/ai/ComputerPlayerFourthSeat.class" ]] || { echo "BLOCKED: coherent recovered adapter classes and exact runtime classpath required" >&2; exit 2; }
  CP="$BUILD/classes:$(cat "$BUILD/runtime-classpath.txt")"
fi
OUT="$(mktemp -d "$BUILD/test-classes.XXXXXX")"
printf '%s\n' "$OUT" > "$BUILD/latest-test-classes.txt"
find "$HERE" -name '*.java' -print0 | xargs -0 javac -proc:none --release 8 -cp "$CP" -d "$OUT"
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRealGameTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatAdditionalCostContractTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRecoveredCostsTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRecoveredManaTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRecoveredGrowthTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRecoveredProtocolTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRecoveredGroupTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRecoveredCombatTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRecoveredBoundariesTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatCoreCompatTest
