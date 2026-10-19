#!/usr/bin/env bash
set -euo pipefail
# Build only an isolated copy of the pinned Mage framework; never install it.
HERE=$(cd "$(dirname "$0")" && pwd)
PIN=825d1205acca114825f235c6993f1e5a6a6db9fa
PREIMAGE=1222f150168d28a6317efbd920671221ce0fc73f452b53bedf8209b8f186654f
[[ $# -ge 2 ]] || { echo "Usage: $0 XMAGE_GIT_REPOSITORY NEW_OUTPUT_DIRECTORY [MAVEN_ARGUMENTS...]" >&2; exit 2; }
SOURCE=$(cd "$1" && pwd)
DEST=$2
shift 2
[[ ! -e "$DEST" ]] || { echo 'Output must not exist; baseline and prior candidates are immutable' >&2; exit 2; }
[[ "$(git -C "$SOURCE" rev-parse "$PIN^{commit}")" == "$PIN" ]] || exit 2
ACTUAL=$(git -C "$SOURCE" show "$PIN:Mage/src/main/java/mage/ApprovingObject.java" | sha256sum | cut -d' ' -f1)
[[ "$ACTUAL" == "$PREIMAGE" ]] || { echo 'Pinned source preimage mismatch' >&2; exit 2; }
mkdir -p "$DEST"
DEST=$(cd "$DEST" && pwd)
git -C "$SOURCE" archive "$PIN" pom.xml Mage | tar -x -C "$DEST"
[[ "$(sha256sum "$DEST/Mage/src/main/java/mage/ApprovingObject.java" | cut -d' ' -f1)" == "$PREIMAGE" ]] || exit 2
(cd "$DEST" && patch --batch --forward -p1 < "$HERE/approving-object-lifetime.patch")
cmp "$HERE/src/main/java/mage/ApprovingObject.java" "$DEST/Mage/src/main/java/mage/ApprovingObject.java" \
    || { echo "Unified patch and replacement source disagree" >&2; exit 2; }
[[ "$(sha256sum "$DEST/Mage/src/main/java/mage/abilities/mana/ManaOptions.java" | cut -d' ' -f1)" == 92cfbd396b66b6147d700c36843d32d1a5bbb0fced6ce2dadd77b7e87d72e9c7 ]] || exit 2
(cd "$DEST" && patch --batch --forward -p1 < "$HERE/mana-options-trigger-forecast.patch")
ARGS=("$@")
if [[ -n "${MAVEN_SETTINGS:-}" ]]; then ARGS=(--settings "$MAVEN_SETTINGS" "${ARGS[@]}"); fi
"${MAVEN:-mvn}" "${ARGS[@]}" -f "$DEST/Mage/pom.xml" -Dmaven.repo.local="${MAVEN_REPO:-$DEST/.m2}" -DskipTests package
python3 - "$DEST" "$HERE" "$PIN" "$PREIMAGE" <<'PY'
import hashlib,json,sys,zipfile
from pathlib import Path
out,src=map(Path,sys.argv[1:3]); jar=out/'Mage/target/mage-1.4.62.jar'
hashfile=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
with zipfile.ZipFile(jar) as z:
 data=z.read('mage/ApprovingObject.class')
 assert data==(out/'Mage/target/classes/mage/ApprovingObject.class').read_bytes()
r={'engine_pin':sys.argv[3],'preimage_sha256':sys.argv[4],
 'patch_sha256':hashfile(src/'approving-object-lifetime.patch'),
 'mana_forecast_patch_sha256':hashfile(src/'mana-options-trigger-forecast.patch'),
 'replacement_source_sha256':hashfile(src/'src/main/java/mage/ApprovingObject.java'),
 'jar':str(jar),'sha256':hashfile(jar),'approving_class_sha256':hashlib.sha256(data).hexdigest(),
 'baseline_modified':False,'opt_in_compatibility_patch':True}
(out/'core-compat-provenance.json').write_text(json.dumps(r,indent=2)+'\n')
print(json.dumps(r,indent=2))
PY
