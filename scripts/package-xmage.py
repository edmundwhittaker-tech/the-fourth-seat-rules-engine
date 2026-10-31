"""Check the compiled distribution and retain its source/toolchain provenance."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
pin_path = root / 'integrations/xmage/upstream.json'
pin = json.loads(pin_path.read_text())
source = root / '.upstream/xmage'
archive = source / 'Mage.Server/target/mage-server.zip'
out = root / 'build/xmage'
out.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(archive) as bundle:
    bad = bundle.testzip()
    if bad:
        raise RuntimeError(f'Corrupt distribution entry: {bad}')
    names = bundle.namelist()
    required = [
        f'lib/mage-server-{pin["mavenVersion"]}.jar',
        f'lib/mage-{pin["mavenVersion"]}.jar',
        f'lib/mage-sets-{pin["mavenVersion"]}.jar',
        f'lib/mage-player-ai-{pin["mavenVersion"]}.jar',
        f'plugins/mage-player-human-{pin["mavenVersion"]}.jar',
        f'plugins/mage-game-commanderfreeforall-{pin["mavenVersion"]}.jar',
        'LICENSE.txt',
    ]
    for entry in required:
        if entry not in names:
            raise RuntimeError(f'Missing required server component: {entry}')
    (out / 'contents.txt').write_text('\n'.join(names) + '\n')

commit = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
if commit != pin['commit']:
    raise RuntimeError('Source commit changed during build')
shutil.copy2(archive, out / archive.name)
shutil.copy2(source / 'LICENSE.txt', out / 'XMage-LICENSE.txt')
shutil.copy2(pin_path, out / 'upstream.json')
manifest = {
    'upstream': pin,
    'actualCommit': commit,
    'integrationCommit': os.environ.get('GITHUB_SHA'),
    'workflowRun': os.environ.get('GITHUB_RUN_ID'),
    'workflowAttempt': os.environ.get('GITHUB_RUN_ATTEMPT'),
    'java': (root / 'build/xmage-logs/java-version.txt').read_text(),
    'maven': (root / 'build/xmage-logs/maven-version.txt').read_text(),
    'validation': 'Compiled and packaged; gameplay and server startup not tested in this milestone',
}
(out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
checksums = []
for path in sorted(out.iterdir()):
    if path.is_file() and path.name != 'SHA256SUMS':
        checksums.append(f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}')
(out / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
summary = f'''## Pinned XMage server build

- Release: `{pin['release']}`
- Verified upstream commit: `{commit}`
- Server distribution: `{archive.name}`
- Required engine, card-set, native AI, human-player and multiplayer Commander JARs: present
- Archive integrity, source provenance and artifact SHA-256 checksums: recorded
- Scope: source build and package only; no running service or browser game yet
'''
print(summary)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write(summary)
