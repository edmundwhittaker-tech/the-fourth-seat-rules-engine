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
RAW_MAVEN_ARGUMENTS=("$@")
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
(cd "$ROOT" && sha256sum -c adapter/test/results/source-sha256.txt)
COMPAT="$ROOT/engine-compat"
[[ $(sha256sum "$DEST/Mage/src/main/java/mage/ApprovingObject.java" | cut -d' ' -f1) == 1222f150168d28a6317efbd920671221ce0fc73f452b53bedf8209b8f186654f ]]
(cd "$DEST" && patch --batch --forward -p1 < "$COMPAT/approving-object-lifetime.patch")
cmp "$DEST/Mage/src/main/java/mage/ApprovingObject.java" "$COMPAT/src/main/java/mage/ApprovingObject.java"
[[ $(sha256sum "$DEST/Mage/src/main/java/mage/abilities/mana/ManaOptions.java" | cut -d' ' -f1) == 92cfbd396b66b6147d700c36843d32d1a5bbb0fced6ce2dadd77b7e87d72e9c7 ]]
(cd "$DEST" && patch --batch --forward -p1 < "$COMPAT/mana-options-trigger-forecast.patch")
MODULE="$DEST/Mage.Server.Plugins/Mage.Player.AI/src"
cp -R "$ROOT/src/main/java/fourthseat" "$MODULE/"
cp "$ROOT"/adapter/src/mage/player/ai/*.java "$MODULE/mage/player/ai/"
"${MAVEN:-mvn}" "${MAVEN_ARGUMENTS[@]}" -f "$DEST/pom.xml" -Dmaven.repo.local="${MAVEN_REPO:-$DEST/.m2}" \
    -pl Mage.Server.Plugins/Mage.Player.AI -am -DskipTests install
bash "$COMPAT/prepare-counter-batching.sh" "$DEST" "${MAVEN_REPO:-$DEST/.m2}" "${RAW_MAVEN_ARGUMENTS[@]}"
"${MAVEN:-mvn}" "${MAVEN_ARGUMENTS[@]}" \
    -f "$DEST/Mage.Server.Plugins/Mage.Player.AI/pom.xml" -Dmaven.repo.local="${MAVEN_REPO:-$DEST/.m2}" \
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath \
    -DincludeScope=runtime -Dmdep.outputFile="$DEST/Mage.Server.Plugins/Mage.Player.AI/target/fourth-seat-runtime-classpath.txt"
printf '\nBuilt isolated overlay only. No configuration, runtime or deployment was changed.\n%s\n' \
    "$DEST/Mage.Server.Plugins/Mage.Player.AI/target/mage-player-ai-1.4.62.jar"
