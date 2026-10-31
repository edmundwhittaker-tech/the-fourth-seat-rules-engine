#!/usr/bin/env bash
# Build the reviewed ten-deck policy and matching core/bridge as one isolated pair.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
DEST=${1:-build/online-commander-ai}
[[ ! -e "$DEST" ]] || { echo 'Use a new build directory; refusing to overwrite evidence' >&2; exit 2; }
(cd experiments/commander-ai && sha256sum -c adapter/test/results/source-sha256.txt)
python3 experiments/commander-ai/scripts/verify-deck-pool.py
python3 - <<'PY'
import json,subprocess
from pathlib import Path
pin=json.loads(Path('integrations/nexus/upstream.json').read_text())
for path,key in [('.upstream/nexus','bridgeCommit'),('.upstream/nexus-server','serverForkCommit')]:
 assert subprocess.check_output(['git','-C',path,'rev-parse','HEAD'],text=True).strip()==pin[key]
 subprocess.run(['git','-C',path,'diff','--exit-code'],check=True)
PY
mkdir -p "$DEST" build/nexus
java -version 2>&1 | tee build/nexus/java-version.txt
"${MAVEN:-mvn}" --version 2>&1 | tee build/nexus/maven-version.txt
node --version | tee build/nexus/node-version.txt
cp integrations/nexus/upstream.json build/nexus/upstream.json
(git rev-parse HEAD 2>/dev/null || printf "%s\n" "source-snapshot-without-git-metadata") > build/nexus/integration-commit.txt
cp experiments/commander-ai/adapter/test/results/source-sha256.txt build/nexus/ai-source-sha256.txt
DEST=$(cd "$DEST" && pwd)
mkdir "$DEST/server" "$DEST/bridge"
git -C .upstream/nexus-server archive HEAD | tar -x -C "$DEST/server"
git -C .upstream/nexus archive HEAD | tar -x -C "$DEST/bridge"
CORE="$DEST/server/Mage/src/main/java/mage/ApprovingObject.java"
[[ $(sha256sum "$CORE" | cut -d' ' -f1) == 1222f150168d28a6317efbd920671221ce0fc73f452b53bedf8209b8f186654f ]]
(cd "$DEST/server" && patch --batch --forward -p1 < "$ROOT/experiments/commander-ai/engine-compat/approving-object-lifetime.patch")
cmp "$CORE" experiments/commander-ai/engine-compat/src/main/java/mage/ApprovingObject.java
[[ $(sha256sum "$DEST/server/Mage/src/main/java/mage/abilities/mana/ManaOptions.java" | cut -d' ' -f1) == 92cfbd396b66b6147d700c36843d32d1a5bbb0fced6ce2dadd77b7e87d72e9c7 ]]
(cd "$DEST/server" && patch --batch --forward -p1 < "$ROOT/experiments/commander-ai/engine-compat/mana-options-trigger-forecast.patch")
MODULE="$DEST/server/Mage.Server.Plugins/Mage.Player.AI"
cp -R experiments/commander-ai/src/main/java/fourthseat "$MODULE/src/"
cp experiments/commander-ai/adapter/src/mage/player/ai/*.java "$MODULE/src/mage/player/ai/"
MVN=("${MAVEN:-mvn}" --batch-mode --no-transfer-progress "-Dmaven.repo.local=$DEST/m2")
if [[ -n "${MAVEN_SETTINGS:-}" ]]; then MVN+=(--settings "$MAVEN_SETTINGS"); fi
"${MVN[@]}" -f "$DEST/server/pom.xml" -pl Mage.Server -am clean install -Dmaven.test.skip=true 2>&1 | tee "$DEST/server-build.log"
"${MVN[@]}" -f "$MODULE/pom.xml" org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath -DincludeScope=runtime "-Dmdep.outputFile=$MODULE/target/fourth-seat-runtime-classpath.txt"
bash experiments/commander-ai/engine-compat/prepare-counter-batching.sh "$DEST/server" "$DEST/m2" 2>&1 | tee "$DEST/counter-core-build.log"
"${MVN[@]}" -f "$DEST/server/pom.xml" -pl Mage.Server package org.apache.maven.plugins:maven-assembly-plugin:3.7.1:single -Dmaven.test.skip=true 2>&1 | tee "$DEST/server-package.log"
"${MVN[@]}" -f "$DEST/bridge/Mage.Proxy/pom.xml" clean verify 2>&1 | tee "$DEST/bridge-build.log"
cp "$DEST/server/Mage.Server/target/mage-server.zip" build/nexus/
cp "$DEST/bridge/Mage.Proxy/target/mage-proxy-1.4.62.jar" build/nexus/
cp "$DEST/bridge/LICENSE.txt" build/nexus/Nexus-LICENSE.txt
cp "$DEST/server/LICENSE.txt" build/nexus/XMage-LICENSE.txt
python3 integrations/fourth-seat/hosting/online_ai.py --create-manifest build/nexus
# JAR-only test classpath, using the very plugin and dependencies packaged above.
printf '%s:%s\n' "$MODULE/target/mage-player-ai-1.4.62.jar" "$(cat "$MODULE/target/fourth-seat-runtime-classpath.txt")" > "$DEST/artifact-runtime-classpath.txt"
