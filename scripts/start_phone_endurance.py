#!/usr/bin/env python3
"""Start an explicitly selected phone's opt-in recorder; survives loss of USB."""
import argparse
from pathlib import Path
import shlex
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--seconds', type=int, default=600)
    parser.add_argument('--await-unplug-seconds', type=int, default=600)
    parser.add_argument('--wait-for-device', action='store_true')
    parser.add_argument('--label', default='actual-avatar-unplugged')
    args = parser.parse_args()
    if not args.test_apk.is_file(): parser.error('Test APK must exist')
    if not 30 <= args.seconds <= 1800: parser.error('Duration must be 30..1800 seconds')
    if not 0 <= args.await_unplug_seconds <= 600: parser.error('Unplug wait must be 0..600 seconds')
    adb = [args.adb, '-s', args.serial]
    if args.wait_for_device:
        print(f'Waiting for selected phone {args.serial} (up to ten minutes)', flush=True)
        subprocess.run(adb + ['wait-for-device'], check=True, timeout=600)
    subprocess.run(adb + ['install', '-r', str(args.test_apk.resolve())], check=True, timeout=60)
    folder = '/sdcard/Android/data/com.example.meowmba/files'
    command = ['am', 'instrument', '-w', '-e', 'class', 'com.example.meowmba.PhoneEnduranceTest',
               '-e', 'runEndurance', 'true', '-e', 'seconds', str(args.seconds),
               '-e', 'awaitUnplugSeconds', str(args.await_unplug_seconds), '-e', 'label', args.label,
               'com.example.meowmba.test/androidx.test.runner.AndroidJUnitRunner']
    subprocess.run(adb + ['shell', 'mkdir', '-p', folder], check=True, timeout=15)
    shell = f'nohup {shlex.join(command)} > {shlex.quote(folder + "/endurance-instrument.txt")} 2>&1 </dev/null &'
    try:
        subprocess.run(adb + ['shell', shell], check=True, timeout=15)
    except subprocess.TimeoutExpired:
        # Some Android shells retain the ADB session despite nohup. The client can
        # detach while instrumentation continues; never launch a duplicate here.
        print('ADB shell retained its session; detached without relaunching. Verify the phone log.', flush=True)
    print('Recorder dispatch attempted. Verify the newest phone-endurance-*.jsonl event before unplugging.', flush=True)
    print(f'Results: {folder}/phone-endurance-*.jsonl; log: {folder}/endurance-instrument.txt', flush=True)


if __name__ == '__main__':
    main()
