#!/usr/bin/env python3
"""Summarize measured foreground process cost; never infer unplugged battery drain."""
import argparse
import json
from pathlib import Path
import re
import statistics


def summarize(data, ticks_per_second=100):
    samples = data['samples']
    def ticks(row):
        stat = row.get('stat', '')
        try:
            fields = stat[stat.rindex(')') + 2:].split()
            return int(fields[11]) + int(fields[12])  # utime, stime (proc fields 14/15)
        except (ValueError, IndexError):
            return None
    intervals = []
    for a, b in zip(samples, samples[1:]):
        ta, tb = ticks(a), ticks(b)
        dt = b['elapsed'] - a['elapsed']
        if a['meow_foreground'] and b['meow_foreground'] and a['pid'] == b['pid'] and dt > 0 and ta is not None and tb is not None and tb >= ta:
            intervals.append((100 * (tb-ta) / ticks_per_second / dt, dt))
    temperatures = [float(m.group(1)) / 10 for row in samples if (m := re.search(r'temperature:\s*(\d+)', row['battery_temp']))]
    thermal = lambda key: next((int(m.group(1)) for m in [re.search(r'Thermal Status:\s*(\d+)', data.get(key, ''))] if m), None)
    return {
        'label': data['label'], 'version': data['package'], 'sampleCount': len(samples),
        'foregroundSamples': sum(s['meow_foreground'] for s in samples),
        'durationSeconds': samples[-1]['elapsed'] - samples[0]['elapsed'] if len(samples) > 1 else 0,
        'measuredForegroundCpuSeconds': sum(dt for _, dt in intervals),
        'meanCpuPercentOfOneCore': sum(cpu*dt for cpu, dt in intervals) / sum(dt for _, dt in intervals) if intervals else None,
        'p95IntervalCpuPercentOfOneCore': sorted(cpu for cpu, _ in intervals)[int((len(intervals)-1)*.95)] if intervals else None,
        'batteryCStart': temperatures[0] if temperatures else None,
        'batteryCEnd': temperatures[-1] if temperatures else None,
        'batteryCMax': max(temperatures) if temperatures else None,
        'thermalStatusStart': thermal('thermal'), 'thermalStatusEnd': thermal('thermal_end'),
        'usbChargingObserved': any('USB powered: true' in s['battery_temp'] for s in samples),
        'batteryDrainConclusion': 'Not measured: USB-powered observations cannot establish unplugged drain.',
        'limitations': 'Foreground does not prove motion was active. No GPU/frame-time gate, room-temperature control, or all-day guarantee.',
    }


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('input'); p.add_argument('--out'); p.add_argument('--clock-ticks', type=int, default=100)
    a = p.parse_args(); result = summarize(json.loads(Path(a.input).read_text()), a.clock_ticks)
    text = json.dumps(result, indent=2) + '\n'
    if a.out:
        path = Path(a.out); path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
    print(text)
