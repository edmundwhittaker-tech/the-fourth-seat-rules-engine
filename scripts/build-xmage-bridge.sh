#!/usr/bin/env bash
set -euo pipefail

task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$task_root"
mkdir -p build/nexus
python3 - <<'PY'
import json, subprocess
from pathlib import Path
pin = json.loads(Path('integrations/nexus/upstream.json').read_text())
for path, key in [('.upstream/nexus', 'bridgeCommit'), ('.upstream/nexus-server', 'serverForkCommit')]:
    actual = subprocess.check_output(['git', '-C', path, 'rev-parse', 'HEAD'], text=True).strip()
    assert actual == pin[key], f'{path}: wrong source commit'
    subprocess.run(['git', '-C', path, 'diff', '--exit-code'], check=True)
Path('build/nexus/upstream.json').write_text(json.dumps(pin, indent=2)+'\n')
PY
java -version 2>&1 | tee build/nexus/java-version.txt
mvn --version 2>&1 | tee build/nexus/maven-version.txt
node --version | tee build/nexus/node-version.txt

mvn --batch-mode --no-transfer-progress -f .upstream/nexus-server/pom.xml \
  -pl Mage.Server -am clean install -Dmaven.test.skip=true \
  2>&1 | tee build/nexus/server-build.log
mvn --batch-mode --no-transfer-progress -f .upstream/nexus-server/pom.xml \
  -pl Mage.Server package org.apache.maven.plugins:maven-assembly-plugin:3.7.1:single \
  -Dmaven.test.skip=true 2>&1 | tee build/nexus/server-package.log

# A fresh local Maven repository is populated from the pinned fork before this
# standalone build. Run the bridge's Java tests as well as packaging its JAR.
mvn --batch-mode --no-transfer-progress -f .upstream/nexus/Mage.Proxy/pom.xml \
  clean verify 2>&1 | tee build/nexus/bridge-build.log
cp .upstream/nexus-server/Mage.Server/target/mage-server.zip build/nexus/
cp .upstream/nexus/Mage.Proxy/target/mage-proxy-1.4.62.jar build/nexus/
cp .upstream/nexus/LICENSE.txt build/nexus/Nexus-LICENSE.txt
cp .upstream/nexus-server/LICENSE.txt build/nexus/XMage-LICENSE.txt
