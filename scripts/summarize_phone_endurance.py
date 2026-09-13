#!/usr/bin/env python3
"""Describe saved device measurements without treating missing evidence as a pass."""
import argparse
import json
from pathlib import Path


def summarize(rows):
    header = next((x for x in rows if x.get('event') == 'header'), {})
    samples = [x for x in rows if x.get('event') == 'sample']
    complete = next((x for x in reversed(rows) if x.get('event') == 'complete'), None)
    intervals = []
    gaps = []
    for a, b in zip(samples, samples[1:]):
        dt = (b['elapsedMs'] - a['elapsedMs']) / 1000
        if dt <= 0 or dt > 15: gaps.append(dt)
        if dt <= 0: continue
        count = b['submittedFrames'] - a['submittedFrames']
        intervals.append({'seconds': dt,
            'activeForeground': a['foreground'] and b['foreground'] and a['stageActive'] and b['stageActive'] and dt <= 15,
            'fps': (b['presentedFrames'] - a['presentedFrames']) / dt,
            'cpuPercent': (b['processCpuMs'] - a['processCpuMs']) / (10 * dt),
            'drawWallMsPerSubmission': (b['drawWallNanos'] - a['drawWallNanos']) / (1e6 * count) if count > 0 else None})
    def percentile(values, p):
        values = sorted(x for x in values if x is not None)
        return values[int((len(values) - 1) * p)] if values else None
    temperatures = [x['batteryTenthsC'] / 10 for x in samples if x['batteryTenthsC'] > 0]
    active = [x for x in intervals if x['activeForeground']]
    active_seconds = sum(x['seconds'] for x in active)
    return {'header': header, 'completed': complete is not None,
        'completion': complete, 'sampleCount': len(samples),
        'gapsOutside0to15Seconds': gaps,
        'validUnpluggedRun': bool(complete and complete['batteryMeasurementValid'] and
            complete['sustainedForegroundMotionGate'] and not gaps),
        'measuredActiveForegroundSeconds': active_seconds,
        'meanActiveForegroundCpuPercentOfOneCore': sum(x['cpuPercent'] * x['seconds'] for x in active) / active_seconds if active_seconds else None,
        'meanActiveForegroundPresentedFps': sum(x['fps'] * x['seconds'] for x in active) / active_seconds if active_seconds else None,
        'medianIntervalFps': percentile((x['fps'] for x in intervals), .5),
        'p05IntervalFps': percentile((x['fps'] for x in intervals), .05),
        'p95IntervalCpuPercentOfOneCore': percentile((x['cpuPercent'] for x in intervals), .95),
        'p95IntervalMeanDrawWallMs': percentile((x['drawWallMsPerSubmission'] for x in intervals), .95),
        'batteryCStart': temperatures[0] if temperatures else None,
        'batteryCEnd': temperatures[-1] if temperatures else None,
        'batteryCMax': max(temperatures) if temperatures else None,
        'maxThermalStatus': max((x['thermalStatus'] for x in samples), default=None),
        'stageModesObserved': sorted({x['stageMode'] for x in samples}),
        'roomsObserved': sorted({x['room'] for x in samples}),
        'limitations': ['Temperature is the battery sensor, not SoC junction temperature.',
            'Charge counter measures whole-phone use, including display, radios and other processes.',
            'CPU includes app and instrumentation; presentation counts are TextureView updates.',
            'Draw timing includes submission and EGL swap wait, not isolated GPU execution.',
            'Ten-second samples and a bounded run do not prove every frame or all-day battery life.']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('input', type=Path); parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    result = summarize([json.loads(x) for x in args.input.read_text().splitlines() if x.strip()])
    output = json.dumps(result, indent=2) + '\n'
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True); args.out.write_text(output)
    print(output)
