#!/usr/bin/env python3
"""Verify the expanded model's replay, observer independence and graph dependence.

These checks validate the implementation, not biological physiology or behavior.
Run only with the verified worker classes and pinned full graph manifest.
"""
import argparse
import json
import os
import pathlib
import subprocess
from prepare_malecns_worker import full_profile


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--jdk', type=pathlib.Path, required=True)
    p.add_argument('--classes', type=pathlib.Path, required=True)
    p.add_argument('--asset', type=pathlib.Path, required=True)
    p.add_argument('--manifest', type=pathlib.Path, required=True)
    p.add_argument('--output', type=pathlib.Path, required=True)
    a = p.parse_args()
    full_profile(a.asset, a.manifest)
    a.output.mkdir(exist_ok=False, parents=True)
    probe = pathlib.Path(__file__).with_name('MaleCnsProbe.java')
    subprocess.run([str(a.jdk / 'bin/javac'), '--release', '17', '-cp', str(a.classes),
                    '-d', str(a.output), str(probe)], check=True, capture_output=True)
    results = {}
    for scenario, mode in [('quiet', 'off'), ('sugar', 'reset'), ('sugar_bitter', 'off'),
                           ('groom', 'off'), ('groom', 'on'), ('loom', 'off'),
                           ('sugar', 'ablated'), ('loom', 'ablated')]:
        run = subprocess.run([str(a.jdk / 'bin/java'), '-Xmx1g', '-XX:ActiveProcessorCount=1',
            '-cp', str(a.output) + os.pathsep + str(a.classes), 'MaleCnsProbe', str(a.asset),
            scenario, mode, '600', 'full'], check=True, capture_output=True, text=True, timeout=90)
        results[f'{scenario}/{mode}'] = [json.loads(line) for line in run.stdout.splitlines() if line.startswith('{')]
    def first(key): return results[key][0]
    def signature(row): return (row['state_sha256'], row['spikes'], row['mean_population_hz'])
    checks = dict(
        quiet_is_silent=first('quiet/off')['spikes'] == 0,
        reset_replays_all_neuron_states=signature(results['sugar/reset'][0]) == signature(results['sugar/reset'][1]),
        observer_does_not_change_neuron_states=signature(first('groom/off')) == signature(first('groom/on')),
        sugar_reaches_feeding=first('sugar/reset')['mean_population_hz']['MN9'] > 0,
        bitter_suppresses_sugar=first('sugar_bitter/off')['mean_population_hz']['MN9'] < first('sugar/reset')['mean_population_hz']['MN9'],
        touch_reaches_grooming=first('groom/off')['mean_population_hz']['DNg62'] > 0,
        loom_reaches_jump=first('loom/off')['mean_population_hz']['TTMn'] > 0,
        feeding_requires_connections=first('sugar/ablated')['mean_population_hz']['MN9'] == 0,
        jump_requires_connections=first('loom/ablated')['mean_population_hz']['TTMn'] == 0)
    report = dict(scope='Full graph point-neuron model implementation checks; not biological validation',
                  identity=json.loads(a.manifest.read_text()), checks=checks, results=results)
    (a.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(checks, indent=2))
    if not all(checks.values()): raise SystemExit('Model checks failed; inspect the report before deployment')


if __name__ == '__main__': main()
