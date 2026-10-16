#!/usr/bin/env python3
"""Package only a tested inactive plugin; never include runtime configuration or raw logs."""
import argparse
import collections
import datetime
import hashlib
import json
import pathlib
import re
import shutil
import zipfile

PIN = '825d1205acca114825f235c6993f1e5a6a6db9fa'
ROOT = pathlib.Path(__file__).resolve().parents[1]

def validate_results(batches, expected):
    if len(batches) != 3:
        raise ValueError('Exactly three soak batches are required')
    coverage = collections.Counter()
    for batch in batches:
        if not isinstance(batch, list) or len(batch) != 3:
            raise ValueError('Each batch must contain three pods')
        for pod in batch:
            if not {'status', 'completedTurns', 'activationFailures', 'caughtPriorityFailures', 'errorLines', 'seats', 'runtimeMs'} <= set(pod):
                raise ValueError('Incomplete soak evidence')
            if pod.get('status') != 'FINISHED' or not 0 < pod.get('completedTurns', 0) <= 120:
                raise ValueError('Incomplete soak pod or exceeded 120-turn bound')
            if pod['runtimeMs'] is None or not 0 <= pod['runtimeMs'] <= 300000:
                raise ValueError('Soak exceeded 300-second bound')
            if any(pod.get(k) for k in ('activationFailures', 'caughtPriorityFailures', 'errorLines')):
                raise ValueError('Soak contains activation failures or errors')
            seats = pod.get('seats', [])
            if len(seats) != 4 or len({s.get('commander') for s in seats}) != 4:
                raise ValueError('Each pod must contain four distinct commander seats')
            for seat in seats:
                commander = seat.get('commander')
                if commander not in expected:
                    raise ValueError('Unknown commander in soak')
                if seat.get('failures', 0):
                    raise ValueError('Seat has failed activations')
                coverage[commander] += 1
    if set(coverage) != expected or min(coverage.values()) < 3:
        raise ValueError('Every frozen commander must complete at least three games')
    return dict(sorted(coverage.items()))

def selected_summary(pod):
    # Explicit allowlist: no raw logs, hidden hands, paths, configuration or credentials.
    keys = ('pod', 'status', 'completedTurns', 'runtimeMs', 'castEvents', 'activationFailures',
            'decisionSamples', 'maxDecisionMs', 'p95DecisionMs', 'passesWithCandidates', 'seats')
    row = {k: pod[k] for k in keys if k in pod}
    row['seats'] = [{k: v for k, v in s.items() if k in (
        'commander', 'landPlays', 'spellCasts', 'activatedAbilities', 'commanderCasts',
        'commanderActivations', 'attackDeclarations', 'failures')} for s in pod['seats']]
    return row

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--build', type=pathlib.Path, required=True)
    ap.add_argument('--results', type=pathlib.Path, nargs=3, required=True)
    ap.add_argument('--policy-log', type=pathlib.Path, required=True)
    ap.add_argument('--engine-log', type=pathlib.Path, required=True)
    ap.add_argument('--commit', required=True)
    ap.add_argument('--output', type=pathlib.Path, required=True)
    args = ap.parse_args()
    if not re.fullmatch(r'[0-9a-f]{40}', args.commit):
        raise ValueError('An exact source commit is required')
    if args.output.exists():
        raise ValueError('Refusing to overwrite an existing artifact directory')
    frozen = json.loads((ROOT / 'deck-snapshot.json').read_text())
    batches = [json.loads(p.read_text()) for p in args.results]
    coverage = validate_results(batches, {d['commander'] for d in frozen['decks']})
    engine_text = args.engine_log.read_text()
    result = re.search(r'RESULT: (\d+) passed, (\d+) failed real-engine checks', engine_text)
    if not result or int(result[1]) <= 0 or int(result[2]) != 0:
        raise ValueError('Real-engine fixture suite has not passed')
    cost_result = re.search(r'FourthSeatAdditionalCostContractTest: (\d+) checks passed', engine_text)
    if not cost_result or int(cost_result[1]) <= 0:
        raise ValueError('Additional-cost contract checks have not passed')
    policy_summary = [line for line in args.policy_log.read_text().splitlines()
                      if re.fullmatch(r'[A-Za-z][A-Za-z0-9 ]*: \d+ (?:checks|assertions) passed', line)]
    names = {line.split(':', 1)[0] for line in policy_summary}
    if not {'ActivationPolicyTest', 'CombatPolicyTest', 'CommanderPolicyTest', 'DeckPlanTest'} <= names:
        raise ValueError('A policy test summary is missing')
    jar = args.build / 'Mage.Server.Plugins/Mage.Player.AI/target/mage-player-ai-1.4.62.jar'
    with zipfile.ZipFile(jar) as archive:
        for name in ('mage/player/ai/ComputerPlayerFourthSeat.class',
                     'mage/player/ai/FourthSeatCombatAdapter.class',
                     'fourthseat/ai/CommanderPolicy.class', 'fourthseat/ai/DeckPlan.class'):
            if name not in archive.namelist():
                raise ValueError('Plugin jar is missing ' + name)
    args.output.mkdir(parents=True)
    shutil.copyfile(jar, args.output / jar.name)
    shutil.copyfile(ROOT / 'LICENSE-XMAGE.txt', args.output / 'LICENSE-XMAGE.txt')
    shutil.copyfile(ROOT / 'deck-snapshot.json', args.output / 'deck-snapshot.json')
    manifest = {'status': 'tested-experimental-inactive', 'sourceCommit': args.commit,
                'xmageServerForkCommit': PIN, 'createdUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'poolSha256': frozen['poolSha256'], 'policyTests': policy_summary,
                'realEngineFixturesPassed': int(result[1]), 'additionalCostContractChecksPassed': int(cost_result[1]),
                'completedSoakGames': 9, 'maximumTurnsPerGame': 120, 'maximumSecondsPerGame': 300,
                'commanderGameCoverage': coverage,
                'limits': ['Heuristic beta, not proof of human-level strength or all-card mastery',
                           'No production activation, server configuration or gateway changes included',
                           'Soak seeds alone do not fully reproduce engine UUID-based tie breaking']}
    (args.output / 'BUILD-MANIFEST.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (args.output / 'SOAK-SUMMARY.json').write_text(json.dumps(
        [[selected_summary(p) for p in batch] for batch in batches], indent=2) + '\n')
    readme = ('TESTED EXPERIMENTAL COMMANDER AI - INACTIVE\n\n'
              'This is one plugin jar, not a complete server distribution. Do not install it on the live game.\n'
              'The existing gateway and production bot have not been changed. Review the source, limitations,\n'
              'separate test-server installation and rollback instructions at:\n'
              'https://github.com/edmundwhittaker-tech/the-fourth-seat-rules-engine/tree/' + args.commit +
              '/experiments/commander-ai\n\n'
              'Checksums below identify the packaged files. Test success establishes bounded compatibility,\n'
              'not universal Magic competence or a hard think-time guarantee.\n')
    (args.output / 'READ-ME-FIRST.txt').write_text(readme)
    (args.output / 'SHA256SUMS.txt').write_text(''.join(
        hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n'
        for p in sorted(args.output.iterdir()) if p.is_file()))
    print(json.dumps(manifest, indent=2))

if __name__ == '__main__':
    main()
