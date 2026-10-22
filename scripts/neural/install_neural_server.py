#!/usr/bin/env python3
"""Install the extracted, verified bundle as this Linux user's persistent service."""
import argparse
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def verify(root):
    expected = json.loads((root / 'SHA256SUMS.json').read_text())
    for name, checksum in expected.items():
        path = (root / name).resolve()
        if root.resolve() not in path.parents or not path.is_file():
            raise ValueError('Invalid package member')
        value = hashlib.sha256()
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                value.update(chunk)
        if value.hexdigest() != checksum:
            raise ValueError('Package checksum mismatch: ' + name)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--prefix', type=Path, default=Path.home() / '.local/share/meow-neural-server')
    p.add_argument('--java', type=Path, required=True, help='Java 17+ executable')
    p.add_argument('--bind', default='127.0.0.1', help='A local IPv4 interface; use the LAN/VPN address for phone access')
    p.add_argument('--advertise-host', required=True)
    p.add_argument('--port', type=int, default=8785)
    p.add_argument('--name', default='Meow full fly brain')
    p.add_argument('--verify-only', action='store_true')
    a = p.parse_args()
    root = Path(__file__).resolve().parent
    verify(root)
    if a.verify_only:
        print('All bundle checksums verified')
        return
    if sys.platform != 'linux':
        p.error('The service installer requires Linux with systemd --user')
    ipaddress.IPv4Address(a.bind)
    if not 1024 <= a.port <= 65535:
        p.error('Use an unprivileged TCP port')
    if not re.fullmatch(r'[A-Za-z0-9_.:-]{1,253}', a.advertise_host):
        p.error('Use a host name or IP, not a URL')
    prefix, java = a.prefix.expanduser().resolve(), a.java.expanduser().resolve()
    # systemd quoting cannot make newlines, percent expansion or arbitrary units safe.
    for path in (prefix, java, Path(sys.executable).resolve()):
        if re.search(r'[\s%"\\]', str(path)):
            p.error('Install and runtime paths must not contain whitespace, %, quotes or backslashes')
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
    match = re.search(r'version "(\d+)', version.stderr + version.stdout)
    if not match or int(match[1]) < 17:
        p.error('Java 17 or later is required')
    release_id = hashlib.sha256((root / 'SHA256SUMS.json').read_bytes()).hexdigest()[:16]
    release = prefix / 'releases' / release_id
    state = prefix / 'state'
    os.umask(0o077)
    state.mkdir(parents=True, exist_ok=True)
    if release.exists():
        verify(release)
    else:
        shutil.copytree(root, release)
        verify(release)
    config_file = state / 'config.json'
    config = json.loads(config_file.read_text()) if config_file.exists() else {}
    config.update(name=a.name, port=a.port, workspace_roots=[], malecns_enabled=True,
                  malecns_worker_argv=[str(java), '-Xmx1g', '-XX:ActiveProcessorCount=1', '-cp', str(release / 'worker'),
                                      'MaleCnsWorker', str(release / 'models/retained.flyb.gz'), str(release / 'models/full.flyb.gz'),
                                      str(release / 'worker/full-worker.properties')])
    pending = config_file.with_suffix('.pending')
    pending.write_text(json.dumps(config, indent=2) + '\n')
    os.replace(pending, config_file)
    unit = Path.home() / '.config/systemd/user/meow-neural.service'
    unit.parent.mkdir(parents=True, exist_ok=True)
    unit.write_text(f'''[Unit]
Description=Meow full MaleCNS neural simulation
After=network-online.target

[Service]
ExecStart={Path(sys.executable).resolve()} {release}/neural_server.py --root {release} --state {state} --bind {a.bind} --advertise-host {a.advertise_host}
Restart=on-failure
RestartSec=5
UMask=0077
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths={state}
PrivateTmp=true
MemoryMax=1536M
TasksMax=64
TimeoutStopSec=15

[Install]
WantedBy=default.target
''')
    subprocess.run(['systemctl', '--user', 'daemon-reload'], check=True)
    subprocess.run(['systemctl', '--user', 'enable', 'meow-neural.service'], check=True)
    subprocess.run(['systemctl', '--user', 'restart', 'meow-neural.service'], check=True)
    print(f'Installed {release_id}. Pairing, certificates and previous releases preserved in {prefix}.')
    print('Open a one-use pairing window: systemctl --user kill --signal=USR1 --kill-whom=main meow-neural.service')
    print(f'Paste {state}/pairing-payload.txt into Memba → Neural setup → Use another server.')


if __name__ == '__main__':
    main()
