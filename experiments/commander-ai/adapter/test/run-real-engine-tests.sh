#!/usr/bin/env bash
set -euo pipefail
# Run after scripts/build-adapter.sh succeeds. No game server, network calls or deployment.
ROOT="${1:?Usage: run-real-engine-tests.sh BUILT_XMAGE_CHECKOUT [MAVEN_REPOSITORY]}"
REPO="${2:-$HOME/.m2/repository}"
HERE="$(cd "$(dirname "$0")" && pwd)"
[[ -f "$ROOT/Mage.Server.Plugins/Mage.Player.AI/target/classes/mage/player/ai/ComputerPlayerFourthSeat.class" ]] || {
  echo 'BLOCKED: build the isolated adapter against pinned XMage before running these tests.' >&2; exit 2;
}
CP="$(find "$ROOT" -type d -path '*/target/classes' -print | paste -sd: -)"
JARS="$(find "$REPO" -name '*.jar' -type f -print | paste -sd: -)"
CP="$CP:$JARS"
OUT="$(mktemp -d)"; trap 'rm -rf "$OUT"' EXIT
find "$HERE" -name '*.java' -print0 | xargs -0 javac -proc:none --release 8 -cp "$CP" -d "$OUT"
java -ea -cp "$OUT:$CP" mage.player.ai.FourthSeatRealGameTest
