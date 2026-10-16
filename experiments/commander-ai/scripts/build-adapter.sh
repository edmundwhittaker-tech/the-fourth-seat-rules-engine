#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PIN=825d1205acca114825f235c6993f1e5a6a6db9fa
if [[ $# -lt 2 ]]; then
  echo "Usage: $0 PINNED_XMAGE_CHECKOUT NEW_ISOLATED_BUILD_DIRECTORY [MAVEN_ARGUMENTS...]" >&2
  exit 2
fi
SOURCE=$(cd "$1" && pwd)
DEST=$2
shift 2
MAVEN_ARGUMENTS=("$@")
if [[ -n "${MAVEN_SETTINGS:-}" ]]; then
  [[ -f "$MAVEN_SETTINGS" ]] || { echo "MAVEN_SETTINGS must name an existing file" >&2; exit 2; }
  MAVEN_ARGUMENTS=(--settings "$MAVEN_SETTINGS" "${MAVEN_ARGUMENTS[@]}")
fi
[[ "$(git -C "$SOURCE" rev-parse HEAD)" == "$PIN" ]] || { echo 'Wrong XMage source revision' >&2; exit 2; }
[[ ! -e "$DEST" ]] || { echo 'Build directory must not exist; source/runtime are never modified' >&2; exit 2; }
mkdir -p "$DEST"
DEST=$(cd "$DEST" && pwd)
git -C "$SOURCE" archive "$PIN" | tar -x -C "$DEST"
MODULE="$DEST/Mage.Server.Plugins/Mage.Player.AI/src"
cp -R "$ROOT/src/main/java/fourthseat" "$MODULE/"
cp "$ROOT"/adapter/src/mage/player/ai/*.java "$MODULE/mage/player/ai/"
"${MAVEN:-mvn}" "${MAVEN_ARGUMENTS[@]}" -f "$DEST/pom.xml" -Dmaven.repo.local="${MAVEN_REPO:-$DEST/.m2}" \
    -pl Mage.Server.Plugins/Mage.Player.AI -am -DskipTests package
printf '\nBuilt isolated overlay only. No configuration, runtime or deployment was changed.\n%s\n' \
    "$DEST/Mage.Server.Plugins/Mage.Player.AI/target/mage-player-ai-1.4.62.jar"
