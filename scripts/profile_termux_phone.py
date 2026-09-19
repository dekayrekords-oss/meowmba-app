#!/usr/bin/env python3
"""Read-only, UID-scoped Termux + Meow CPU sampling. Never reads argv or secrets.

Requires a connected authorized Android device. Missing proc access is reported,
never treated as zero CPU. USB-powered samples do not measure battery drain.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def cpu_ticks(stat):
    try:
        fields = stat[stat.rindex(')') + 2:].split()
        return int(fields[11]) + int(fields[12]), int(fields[19])
    except (ValueError, IndexError):
        return None


def uid_number(value):
    if value.isdigit():
        return int(value)
    match = re.fullmatch(r'u(\d+)_a(\d+)', value)
    return int(match[1]) * 100000 + 10000 + int(match[2]) if match else None


def scoped_processes(table, uids):
    result = []
    for line in table.splitlines():
        fields = line.split(maxsplit=3)
        if len(fields) != 4 or not fields[1].isdigit() or not fields[2].isdigit():
            continue
        uid = uid_number(fields[0])
        if uid in uids:
            # NAME is the executable name, not full command-line arguments.
            result.append(dict(uid=uid, pid=int(fields[1]), ppid=int(fields[2]), name=fields[3][:160]))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--seconds', type=int, default=90)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    if not 10 <= args.seconds <= 600:
        parser.error('--seconds must be 10–600')

    def adb(*command):
        result = subprocess.run(['adb', '-s', args.serial, *command], capture_output=True, text=True, timeout=15)
        if result.returncode:
            raise RuntimeError('Device command failed or phone disconnected; no state was changed.')
        return result.stdout.strip()

    package_uids = {}
    for package in ('com.termux', 'com.example.meowmba'):
        found = re.search(r'^\s*userId=(\d+)', adb('shell', 'dumpsys', 'package', package), re.M)
        if not found:
            raise RuntimeError(f'Cannot resolve installed package UID: {package}')
        package_uids[package] = int(found[1])
    tick_text = adb('shell', 'getconf', 'CLK_TCK')
    if not tick_text.isdigit() or int(tick_text) <= 0:
        raise RuntimeError('Cannot establish device clock tick rate.')
    hz = int(tick_text)
    rows = []
    previous = {}
    started = time.monotonic()
    thermal_start = adb('shell', 'dumpsys', 'thermalservice')
    while time.monotonic() - started < args.seconds:
        sample_start = time.monotonic()
        processes = scoped_processes(adb('shell', 'ps', '-A', '-o', 'UID,PID,PPID,NAME'), set(package_uids.values()))
        current = {}
        for process in processes:
            pid = process['pid']
            # Process exit and Android proc restrictions can make individual reads fail.
            try:
                ticks = cpu_ticks(adb('shell', 'cat', f'/proc/{pid}/stat'))
            except RuntimeError:
                ticks = None
            now = time.monotonic()
            process['cpuPercentOneCore'] = None
            process['readable'] = ticks is not None
            if ticks is not None:
                total, start_ticks = ticks
                key = (pid, start_ticks)  # PID reuse must not create a false delta.
                if key in previous:
                    prior_ticks, prior_time = previous[key]
                    if total >= prior_ticks and now > prior_time:
                        process['cpuPercentOneCore'] = 100 * (total - prior_ticks) / hz / (now - prior_time)
                current[key] = (total, now)
        previous = current
        battery = '\n'.join(line.strip() for line in adb('shell', 'dumpsys', 'battery').splitlines()
                            if line.strip().startswith(('temperature:', 'USB powered:', 'level:')))
        rows.append(dict(elapsed=sample_start - started, processes=processes, battery=battery))
        time.sleep(max(0, 3 - (time.monotonic() - sample_start)))
    thermal_end = adb('shell', 'dumpsys', 'thermalservice')
    def status(raw):
        found = re.search(r'Thermal Status:\s*(\d+)', raw)
        return int(found[1]) if found else None
    report = dict(packageUids=package_uids, clockTicks=hz, samples=rows,
                  thermalStart=status(thermal_start), thermalEnd=status(thermal_end),
                  limits='No argv, credentials, files or settings read/changed. Short-lived processes between samples may be missed. Missing CPU is unknown, not zero. USB samples cannot prove unplugged drain or an all-day heat result.')
    path = Path(args.out)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2) + '\n')
    print(f'{len(rows)} UID-scoped samples saved to {path}')


if __name__ == '__main__':
    main()
