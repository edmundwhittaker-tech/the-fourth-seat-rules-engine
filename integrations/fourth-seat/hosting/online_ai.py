"""Fail-closed pairing checks and the single reviewed XMage player-type mapping."""
import hashlib
import io
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

PLAYER_CLASS = 'mage.player.ai.ComputerPlayerFourthSeat'
PLAYER_JAR = 'mage-player-ai-1.4.62.jar'
CORE_CLASS = 'mage/ApprovingObject.class'
FILES = ('mage-server.zip', 'mage-proxy-1.4.62.jar')
MANIFEST = 'commander-ai-manifest.json'

def digest(data):
    return hashlib.sha256(data).hexdigest()

def inspect_pair(directory):
    directory = Path(directory)
    with zipfile.ZipFile(directory / FILES[0]) as server:
        core = server.read('lib/mage-1.4.62.jar')
        plugin = server.read('lib/' + PLAYER_JAR)
        with zipfile.ZipFile(io.BytesIO(plugin)) as ai:
            for name in (PLAYER_CLASS.replace('.', '/') + '.class', 'fourthseat/ai/CommanderPolicy.class', 'fourthseat/ai/DeckPlan.class'):
                if name not in ai.namelist():
                    raise ValueError('Missing required deck-aware AI class: ' + name)
        with zipfile.ZipFile(io.BytesIO(core)) as jar:
            core_class = jar.read(CORE_CLASS)
    with zipfile.ZipFile(directory / FILES[1]) as bridge:
        if bridge.read(CORE_CLASS) != core_class:
            raise ValueError('Bridge and server core do not match')
    return {'schema': 1, 'playerClass': PLAYER_CLASS,
            'serverForkCommit': '825d1205acca114825f235c6993f1e5a6a6db9fa',
            'bridgeCommit': '850411ec4db71ea7c10f4c3869a63084760057c2',
            'files': {name: digest((directory / name).read_bytes()) for name in FILES},
            'pluginSha256': digest(plugin), 'coreSha256': digest(core),
            'approvingClassSha256': digest(core_class)}

def verify_pair(directory):
    actual = inspect_pair(directory)
    expected = json.loads((Path(directory) / MANIFEST).read_text())
    if actual != expected:
        raise ValueError('AI/core/bridge artifact manifest mismatch; refusing startup')
    return actual

def configure_player(config):
    tree = ET.parse(config)
    entries = [entry for entry in tree.getroot().findall('./playerTypes/playerType')
               if entry.get('name') == 'Computer - mad']
    if len(entries) != 1:
        raise ValueError('Expected exactly one Computer - mad player type')
    entries[0].set('jar', '../lib/' + PLAYER_JAR)
    entries[0].set('className', PLAYER_CLASS)
    tree.write(config, encoding='utf-8', xml_declaration=True)

if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--create-manifest':
        directory = Path(sys.argv[2])
        (directory / MANIFEST).write_text(json.dumps(inspect_pair(directory), indent=2) + '\n')
    elif len(sys.argv) == 2:
        print(json.dumps(verify_pair(sys.argv[1]), indent=2))
    else:
        raise SystemExit('Usage: online_ai.py [--create-manifest] ARTIFACT_DIRECTORY')
