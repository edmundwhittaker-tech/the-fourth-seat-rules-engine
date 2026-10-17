import io
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from online_ai import configure_player, inspect_pair, verify_pair, MANIFEST


def jar(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as archive:
        for name, contents in entries.items():
            archive.writestr(name, contents)
    return out.getvalue()


class OnlineAIContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.core = b'paired-core-class'
        plugin = jar({
            'mage/player/ai/ComputerPlayerFourthSeat.class': b'adapter',
            'fourthseat/ai/CommanderPolicy.class': b'policy',
            'fourthseat/ai/DeckPlan.class': b'plans'})
        (self.root / 'mage-server.zip').write_bytes(jar({
            'lib/mage-1.4.62.jar': jar({'mage/ApprovingObject.class': self.core}),
            'lib/mage-player-ai-1.4.62.jar': plugin}))
        self.bridge = self.root / 'mage-proxy-1.4.62.jar'
        self.bridge.write_bytes(jar({'mage/ApprovingObject.class': self.core}))
        (self.root / MANIFEST).write_text(json.dumps(inspect_pair(self.root)))

    def test_pair_verified(self):
        self.assertEqual(verify_pair(self.root)['playerClass'], 'mage.player.ai.ComputerPlayerFourthSeat')

    def test_mismatched_bridge_refused(self):
        self.bridge.write_bytes(jar({'mage/ApprovingObject.class': b'old-core'}))
        with self.assertRaisesRegex(ValueError, 'do not match'):
            verify_pair(self.root)

    def test_artifact_tamper_refused(self):
        self.bridge.write_bytes(jar({'mage/ApprovingObject.class': self.core, 'extra': b'changed'}))
        with self.assertRaisesRegex(ValueError, 'manifest mismatch'):
            verify_pair(self.root)

    def test_manifest_required(self):
        (self.root / MANIFEST).unlink()
        with self.assertRaises(FileNotFoundError):
            verify_pair(self.root)

    def test_map_only_selected_player_type_idempotently(self):
        config = self.root / 'config.xml'
        config.write_text('<config><playerTypes><playerType name="Computer - mad" className="old" jar="old"/><playerType name="Human" className="human" jar="human"/></playerTypes></config>')
        configure_player(config)
        configure_player(config)
        entries = ET.parse(config).getroot().findall('./playerTypes/playerType')
        self.assertEqual(entries[0].get('className'), 'mage.player.ai.ComputerPlayerFourthSeat')
        self.assertEqual(entries[0].get('jar'), '../lib/mage-player-ai-1.4.62.jar')
        self.assertEqual(entries[1].get('className'), 'human')

    def test_missing_or_duplicate_mapping_refused(self):
        for entries in ('', '<playerType name="Computer - mad"/><playerType name="Computer - mad"/>'):
            config = self.root / 'config.xml'
            config.write_text('<config><playerTypes>' + entries + '</playerTypes></config>')
            with self.assertRaisesRegex(ValueError, 'exactly one'):
                configure_player(config)

    def test_missing_ai_adapter_refused(self):
        (self.root / 'mage-server.zip').write_bytes(jar({
            'lib/mage-1.4.62.jar': jar({'mage/ApprovingObject.class': self.core}),
            'lib/mage-player-ai-1.4.62.jar': jar({'other.class': b'old-ai'})}))
        with self.assertRaisesRegex(ValueError, 'Missing required'):
            verify_pair(self.root)

if __name__ == '__main__':
    unittest.main()
