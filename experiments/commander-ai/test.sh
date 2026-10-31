#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
find src/main/java src/test/java -name '*.java' -print0 | xargs -0 javac --release 8 -d "$build"
for source in src/test/java/fourthseat/ai/*Test.java; do
  java -ea -cp "$build" "fourthseat.ai.$(basename "$source" .java)"
done
