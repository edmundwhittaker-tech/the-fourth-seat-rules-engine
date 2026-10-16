#!/usr/bin/env bash
set -euo pipefail

task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="$task_root/.upstream/xmage"
logs_root="$task_root/build/xmage-logs"
mkdir -p "$logs_root"

expected_commit="$(python3 -c 'import json; print(json.load(open("integrations/xmage/upstream.json"))["commit"])')"
actual_commit="$(git -C "$source_root" rev-parse HEAD)"
if [[ "$actual_commit" != "$expected_commit" ]]; then
  echo "XMage source does not match the locked commit." >&2
  exit 1
fi
git -C "$source_root" diff --exit-code
git -C "$source_root" diff --cached --exit-code

java -version 2>&1 | tee "$logs_root/java-version.txt"
mvn --version 2>&1 | tee "$logs_root/maven-version.txt"
git -C "$source_root" show -s --format=fuller HEAD > "$logs_root/upstream-commit.txt"

# Server runtime dependencies pull in the card set, AI and Commander modules.
# This first milestone builds/packages the source; upstream gameplay tests are
# deliberately a later gate and are not represented as passing by this build.
mvn --batch-mode --no-transfer-progress -f "$source_root/pom.xml" \
  -pl Mage.Server -am clean install -Dmaven.test.skip=true \
  2>&1 | tee "$logs_root/compile.log"

assembly_version="$(python3 -c 'import json; print(json.load(open("integrations/xmage/upstream.json"))["assemblyPluginVersion"])')"
mvn --batch-mode --no-transfer-progress -f "$source_root/pom.xml" \
  -pl Mage.Server package "org.apache.maven.plugins:maven-assembly-plugin:${assembly_version}:single" \
  -Dmaven.test.skip=true 2>&1 | tee "$logs_root/package.log"
