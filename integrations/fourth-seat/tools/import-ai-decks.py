"""Snapshot complete published Archidekt main decks, without card substitutions.

Run from the repository root. Output is a reviewable candidate file, never the
active pool. --cache-dir accepts previously downloaded public API responses.
Bracket estimates are recorded from the public deck pages during review; an
API author bracket, when present, must still be 3. CI checks pinned XMage too.
"""
import argparse
import hashlib
import json
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

# Public-page bracket labels checked 2026-10-16. Estimates are not certification.
SOURCES = [
    (23045205, 'angels', 'estimated'),
    (18019886, 'goblins', 'author'),
    (24644469, 'elves', 'author'),
    (19894242, 'large creatures', 'author'),
    (22625596, 'power-four creatures', 'author'),
    (11353536, 'burn', 'author'),
    (5319321, 'drakes and spells', 'estimated'),
    (23009292, 'landfall', 'author'),
    (25055843, 'dinosaurs', 'author'),
    (2755449, 'human tokens', 'estimated'),
]


def snapshot(data, strategy, bracket_basis, imported_at):
    assert not data['private'] and not data['unlisted']
    assert data['deckFormat'] == 3
    assert data['edhBracket'] == (3 if bracket_basis == 'author' else None)
    included = {c['name'] for c in data['categories'] if c['includedInDeck']}
    excluded = {c['name'] for c in data['categories'] if not c['includedInDeck']}
    # Exclusion wins: a Maybeboard card may also carry a functional category.
    cards = [c for c in data['cards'] if not c.get('deletedAt')
             and any(t in included for t in c['categories'])
             and not any(t in excluded for t in c['categories'])]
    assert sum(c['quantity'] for c in cards) == 100
    commanders = [c for c in cards if 'Commander' in c['categories']]
    assert len(commanders) == 1 and commanders[0]['quantity'] == 1
    assert all(c['card']['oracleCard']['legalities'].get('commander') == 'legal' for c in cards)
    changers = sorted(c['card']['oracleCard']['name'] for c in cards if c['card']['oracleCard'].get('gameChanger'))
    assert len(changers) <= 3
    assert not any(c['card']['oracleCard'].get('extraTurns') or c['card']['oracleCard'].get('massLandDenial') for c in cards)
    # Aggregate different printings of the same card, preserving exact quantities.
    def entries(selected):
        counts = Counter()
        for c in selected:
            counts[c['card']['oracleCard']['name']] += c['quantity']
        return [{'cardName': n, 'amount': q} for n, q in sorted(counts.items())]
    main = entries([c for c in cards if c not in commanders])
    command = entries(commanders)
    canonical = json.dumps({'cards': main, 'commanders': command}, ensure_ascii=False, separators=(',', ':'))
    author = data['owner']['username']
    if '@' in author:
        raise ValueError('Choose a source whose public author name is not an email address.')
    return {'id': 'archidekt-' + str(data['id']), 'strategy': strategy,
            'source': {'url': f"https://archidekt.com/decks/{data['id']}", 'author': author,
                       'title': data['name'], 'updatedAt': data['updatedAt'], 'importedAt': imported_at,
                       'bracket': 3, 'bracketBasis': bracket_basis, 'bracketReviewedAt': '2026-10-16'},
            'checks': {'cardCount': 100, 'gameChangers': changers, 'commanderLegality': 'legal',
                       'legalitySource': 'Archidekt card data, checked at import',
                       'contentSha256': hashlib.sha256(canonical.encode()).hexdigest()},
            'deck': {'name': data['name'], 'cards': main, 'commanders': command, 'sideboard': []}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache-dir', type=Path)
    parser.add_argument('--output', type=Path, default=Path('build/ai-decks-candidate.json'))
    args = parser.parse_args()
    now = datetime.now(timezone.utc).isoformat()
    result = []
    for deck_id, strategy, basis in SOURCES:
        if args.cache_dir:
            data = json.loads((args.cache_dir / f'{deck_id}.json').read_text())
        else:
            with urllib.request.urlopen(f'https://archidekt.com/api/decks/{deck_id}/', timeout=45) as response:
                data = json.load(response)
        result.append(snapshot(data, strategy, basis, now))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'version': 1, 'decks': result}, ensure_ascii=False, indent=2) + '\n')
    print(f'Snapshotted {len(result)} complete published decks to {args.output}. Review before activating.')


if __name__ == '__main__':
    main()
