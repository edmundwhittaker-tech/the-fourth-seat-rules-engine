import json
from pathlib import Path
import re
import subprocess

pin = json.loads(Path('integrations/nexus/upstream.json').read_text())
assert pin['repository'] == 'Adamychen/xmage-nexus'
for folder, key in [('nexus', 'bridgeCommit'), ('nexus-server', 'serverForkCommit')]:
    commit = pin[key]
    assert re.fullmatch('[0-9a-f]{40}', commit)
    target = Path('.upstream') / folder
    target.mkdir(parents=True)
    for command in [['git', 'init', str(target)], ['git', '-C', str(target), 'fetch', '--depth=1', 'https://github.com/Adamychen/xmage-nexus.git', commit], ['git', '-C', str(target), 'checkout', '--detach', 'FETCH_HEAD']]:
        subprocess.run(command, check=True)
