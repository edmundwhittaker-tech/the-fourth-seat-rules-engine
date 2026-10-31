#!/usr/bin/env bash
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "${1:?Built pinned XMage source required}" && pwd)
REPO=${2:?Isolated Maven repository required}; shift 2
[[ -f "$ROOT/Mage/target/classes/mage/cards/CardImpl.class" && -f "$ROOT/Mage.Sets/target/classes/mage/cards/m/MossbornHydra.class" ]] || { echo 'Build the pinned core and Sets before the resolved-source audit.' >&2; exit 2; }
(cd "$ROOT" && sha256sum -c "$HERE/counter-event-preimages.txt")
MVN=("${MAVEN:-mvn}" --batch-mode --no-transfer-progress "-Dmaven.repo.local=$REPO")
if [[ -n "${MAVEN_SETTINGS:-}" ]]; then MVN+=(--settings "$MAVEN_SETTINGS"); fi
MVN+=("$@")
for module in Mage Mage.Sets; do
 "${MVN[@]}" -f "$ROOT/$module/pom.xml" org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath \
  -DincludeScope=compile "-Dmdep.outputFile=$ROOT/$module/target/counter-proof-classpath.txt"
done
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1]); paths=[str(root/'Mage/target/classes'),str(root/'Mage.Sets/target/classes')]
for module in ('Mage','Mage.Sets'):
 paths.extend((root/module/'target/counter-proof-classpath.txt').read_text().strip().split(':'))
(root/'counter-proof-classpath.txt').write_text(':'.join(dict.fromkeys(paths))+'\n')
PY
java -Xmx1800m "$HERE/tools/AuditCounterObservers.java" "$ROOT" \
 "$ROOT/Mage/src/main/java/mage/counters/CounterObserverRegistry.java" "$ROOT/counter-proof-classpath.txt"
(cd "$ROOT" && patch --batch --forward -p1 < "$HERE/counter-event-batching.patch")
cp "$HERE/src/main/java/mage/counters/CounterEventBatching.java" "$ROOT/Mage/src/main/java/mage/counters/"
# Replace only the isolated build's framework. Existing consumers remain binary-compatible.
"${MVN[@]}" -f "$ROOT/Mage/pom.xml" install -Dmaven.test.skip=true
