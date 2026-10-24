#!/usr/bin/env bash
set -euo pipefail
# Run after scripts/build-adapter.sh succeeds. No game server, network calls or deployment.
ROOT="${1:?Usage: run-real-engine-tests.sh BUILT_XMAGE_CHECKOUT [RUNTIME_CLASSPATH_FILE]}"
CP_FILE="${2:-$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/fourth-seat-runtime-classpath.txt}"
HERE="$(cd "$(dirname "$0")" && pwd)"
[[ -f "$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes/mage/player/ai/ComputerPlayerFourthSeat.class" ]] || {
  echo 'BLOCKED: build the isolated adapter against pinned XMage before running these tests.' >&2; exit 2;
}
[[ -s "$CP_FILE" && -f "$CP_FILE" ]] || {
  echo 'BLOCKED: expected exact Maven-generated runtime classpath file, not a dependency-cache directory.' >&2; exit 2;
}
CP="$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes:$(cat "$CP_FILE")"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
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

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatCelestialReunionTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatCounterBatchTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatSepharaTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatTriggeredPaymentTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatFinalePaymentTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatCropRotationReserveTest

java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatOpeningHandTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatActivityTest
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatHybridPaymentTest
