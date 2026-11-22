"""Reuse an immutable, previously verified Actions build, else build fresh."""
import hashlib
import json
import os
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent.parent
ready = False
try:
    ref = json.loads((root / 'integrations/nexus/browser/reusable-build.json').read_text())
    source = root / '.reused-xmage/build/nexus'
    pin = json.loads((root / 'integrations/nexus/upstream.json').read_text())
    result = json.loads((source / 'runtime-result.json').read_text())
    assert result['passed'] is True and result['integrationCommit'] == ref['commit']
    assert result['pins'] == pin
    assert hashlib.sha256((root / 'scripts/build-xmage-bridge.sh').read_bytes()).hexdigest() == ref['buildScriptSha256']
    checksums = dict(line.split('  ', 1)[::-1] for line in (source / 'SHA256SUMS').read_text().splitlines())
    for name in ('mage-server.zip', 'mage-proxy-1.4.62.jar'):
        assert hashlib.sha256((source / name).read_bytes()).hexdigest() == checksums[name]
    out = root / 'build/nexus'
    out.mkdir(parents=True, exist_ok=True)
    for name in ('mage-server.zip', 'mage-proxy-1.4.62.jar', 'Nexus-LICENSE.txt',
                 'XMage-LICENSE.txt', 'upstream.json', 'java-version.txt',
                 'node-version.txt', 'maven-version.txt', 'bridge-build.log'):
        shutil.copy2(source / name, out / name)
    reports = Path('.upstream/nexus/Mage.Proxy/target/surefire-reports')
    shutil.copytree(root / '.reused-xmage' / reports, root / reports, dirs_exist_ok=True)
    (out / 'reused-build.json').write_text(json.dumps(ref, indent=2) + '\n')
    ready = True
    print(f'Reusing verified pinned server/bridge from Actions run {ref["runId"]}')
except (OSError, ValueError, KeyError, AssertionError) as error:
    print(f'Fresh build required: {type(error).__name__}: {error}')
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'], 'a') as handle:
        handle.write(f'ready={str(ready).lower()}\n')
