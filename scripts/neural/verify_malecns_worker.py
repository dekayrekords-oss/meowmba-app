#!/usr/bin/env python3
"""Exercise an already compiled worker; no build, downloads, phone, or robot actions.

The report separates cold graph loading from steady-state requests. Full graph
acceptance is conditional on the exact supplied identity and measured budget;
a larger anatomical graph is not automatically a better behavior model.
"""
import argparse
import json
import math
import pathlib
import selectors
import statistics
import subprocess
import time
import uuid

RETAINED = dict(asset_sha256='e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3',
                neurons=176422, pairs=6287749, synapses=90296905)
RATE_KEYS = ('feed_hz', 'escape_hz', 'jump_hz', 'groom_hz', 'groom2_hz', 'wing_hz', 'left_hz', 'right_hz',
             'forward_hz', 'backward_hz', 'halt_hz', 'landing_hz', 'takeoff_hz')
SCENARIOS = dict(quiet=(0, 0, 0, 0, 0, 0), food=(1, 0, 0, 0, 0, 0), touch=(0, 1, 0, 0, 0, 0),
                 loom_left=(0, 0, 1, 0, 0, 0), loom_right=(0, 0, 0, 1, 0, 0),
                 motion_left=(0, 0, 0, 0, 1, 0), motion_right=(0, 0, 0, 0, 0, 1), mixed=(1, 1, 1, 1, 1, 1))


class Worker:
    def __init__(self, argv):
        self.process = subprocess.Popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.DEVNULL, bufsize=0)

    def exchange(self, line):
        start = time.monotonic()
        self.process.stdin.write((line + '\n').encode('ascii'))
        self.process.stdin.flush()
        response = bytearray()
        with selectors.DefaultSelector() as selector:
            selector.register(self.process.stdout, selectors.EVENT_READ)
            while len(response) <= 4096:
                remaining = 8 - (time.monotonic() - start)
                if remaining <= 0 or not selector.select(remaining): raise TimeoutError('Worker exceeded bridge deadline')
                byte = self.process.stdout.read(1)
                if not byte: raise RuntimeError('Worker disconnected')
                if byte == b'\n': break
                response.extend(byte)
            else: raise ValueError('Oversized worker response')
        return json.loads(response), (time.monotonic() - start) * 1000

    def close(self):
        self.process.stdin.close()
        try: self.process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            self.process.kill(); self.process.wait(timeout=1)
        self.process.stdout.close()


def request(session, seq, values, graph):
    return '\t'.join([session, str(seq), *map(str, values), *(['full'] if graph == 'full' else [])])


def measured(row):
    return {key: value for key, value in row.items() if key not in ('session', 'seq', 'compute_ms')}


def validate(row, session, seq, graph, identity):
    assert row['session'] == session and row['seq'] == seq
    assert row['graph'] == graph and row['protocol'] == (2 if graph == 'full' else 1)
    assert all(row[key] == identity[key] for key in RETAINED)
    for key, maximum in [('compute_ms', 8000), ('mean_hz', 2000), ('max_hz', 2000),
                          *[(key, 2000) for key in RATE_KEYS]]:
        assert type(row[key]) in (float, int) and math.isfinite(row[key]) and 0 <= row[key] <= maximum, key
    for key, maximum in [('active', identity['neurons']), ('silent', identity['neurons']), ('spikes', identity['neurons'] * 100)]:
        assert type(row[key]) is int and 0 <= row[key] <= maximum, key
    assert row['sim_ms'] == seq * 50


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=pathlib.Path, required=True)
    parser.add_argument('--classes', type=pathlib.Path, required=True)
    parser.add_argument('--retained-asset', type=pathlib.Path, required=True)
    parser.add_argument('--full-asset', type=pathlib.Path)
    parser.add_argument('--full-properties', type=pathlib.Path)
    parser.add_argument('--full-manifest', type=pathlib.Path)
    parser.add_argument('--report', type=pathlib.Path, required=True)
    parser.add_argument('--max-step-ms', type=float, default=200, help='Retained graph compute budget')
    parser.add_argument('--full-max-step-ms', type=float, default=300, help='Full graph budget; Android requests at most every 400ms')
    parser.add_argument('--ticks', type=int, default=12)
    args = parser.parse_args()
    if not 12 <= args.ticks <= 100 or not 0 < args.max_step_ms <= 1800 or not 0 < args.full_max_step_ms <= 1800:
        parser.error('ticks must be 12..100 and max-step-ms in (0, 1800]')
    full_args = (args.full_asset, args.full_properties, args.full_manifest)
    if any(full_args) and not all(full_args): parser.error('Supply all three full graph arguments together')
    argv = [str(args.java), '-Xmx1g', '-XX:ActiveProcessorCount=1', '-cp', str(args.classes),
            'MaleCnsWorker', str(args.retained_asset)]
    profiles = dict(retained=RETAINED)
    if all(full_args):
        argv += [str(args.full_asset), str(args.full_properties)]
        profiles['full'] = json.loads(args.full_manifest.read_text())
    report = dict(schema=1, max_step_ms=args.max_step_ms, full_max_step_ms=args.full_max_step_ms, ticks=args.ticks, profiles={})
    worker = Worker(argv)
    try:
        for graph, identity in profiles.items():
            episodes, latencies, cold_ms = {}, [], None
            for scenario, values in SCENARIOS.items():
                session = str(uuid.uuid4()); rows = []
                for seq in range(1, args.ticks + 1):
                    line = request(session, seq, values, graph)
                    row, elapsed = worker.exchange(line)
                    validate(row, session, seq, graph, identity)
                    if cold_ms is None: cold_ms = elapsed
                    else: latencies.append(elapsed)
                    # Exact duplicate: no new randomness, elapsed neural time, or motor state.
                    duplicate, _ = worker.exchange(line)
                    assert duplicate == row, 'Duplicate request changed neural state'
                    rows.append(row)
                episodes[scenario] = rows
            # Reset and observation remain deterministic after all intervening scenarios.
            session = str(uuid.uuid4())
            for seq, expected in enumerate(episodes['food'], 1):
                row, _ = worker.exchange(request(session, seq, SCENARIOS['food'], graph))
                validate(row, session, seq, graph, identity)
                assert measured(row) == measured(expected), 'Session reset did not reproduce measured activity'
            compute = sorted(row['compute_ms'] for rows in episodes.values() for row in rows)
            p95 = compute[math.ceil(.95 * len(compute)) - 1]
            checks = dict(quiet_is_silent=all(row['spikes'] == 0 for row in episodes['quiet']),
                          food_reaches_feeding=max(row['feed_hz'] for row in episodes['food']) > 0,
                          touch_reaches_grooming=max(max(row['groom_hz'], row['groom2_hz']) for row in episodes['touch']) > 0,
                          loom_reaches_jump=all(max(row['jump_hz'] for row in episodes[key]) > 0 for key in ('loom_left', 'loom_right')),
                          lateral_motion_is_directional=(max(row['left_hz'] for row in episodes['motion_left']) > max(row['right_hz'] for row in episodes['motion_left']) and max(row['right_hz'] for row in episodes['motion_right']) > max(row['left_hz'] for row in episodes['motion_right'])),
                          steady_compute_within_budget=p95 <= (args.full_max_step_ms if graph == 'full' else args.max_step_ms),
                          steady_responses_fresh=max(latencies) <= 1800)
            report['profiles'][graph] = dict(identity=identity, checks=checks, cold_request_ms=cold_ms,
                                             compute_p95_ms=p95, steady_round_trip_max_ms=max(latencies),
                                             steady_round_trip_mean_ms=statistics.mean(latencies), episodes=episodes)
            args.report.write_text(json.dumps(report, indent=2) + '\n')
        # Profile cannot be changed inside a sequence/session, even when the number advances.
        session = str(uuid.uuid4())
        worker.exchange(request(session, 1, SCENARIOS['quiet'], 'retained'))
        error, _ = worker.exchange(request(session, 2, SCENARIOS['quiet'], 'full'))
        assert 'error' in error, 'Unavailable or in-session graph change was accepted'
        report['profile_change_rejected'] = True
    finally:
        worker.close()
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: {**value['checks'], 'compute_p95_ms': value['compute_p95_ms'],
                            'cold_request_ms': value['cold_request_ms']} for key, value in report['profiles'].items()}, indent=2))
    if not all(all(profile['checks'].values()) for profile in report['profiles'].values()):
        raise SystemExit('One or more model/performance gates failed; inspect report before activating this graph.')


if __name__ == '__main__': main()
