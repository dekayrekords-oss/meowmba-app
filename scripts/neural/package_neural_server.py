#!/usr/bin/env python3
"""Build an offline uploadable server bundle from owner-provisioned audited assets."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as src:
        for chunk in iter(lambda: src.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--full-asset', type=Path, required=True)
    p.add_argument('--jdk', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    repo = Path(__file__).resolve().parents[2]
    manifest = repo / 'docs/neural/MALECNS_FULL_GRAPH_115_MANIFEST.json'
    if a.output.exists():
        p.error('Choose a new output file; existing packages are preserved')
    with tempfile.TemporaryDirectory(prefix='meow-neural-package-') as tmp:
        root = Path(tmp) / 'meow-neural-server'
        root.mkdir()
        subprocess.run([sys.executable, str(Path(__file__).with_name('prepare_malecns_worker.py')),
                        '--source', str(a.source), '--output', str(root / 'worker'), '--jdk', str(a.jdk),
                        '--full-asset', str(a.full_asset), '--full-manifest', str(manifest)], check=True)
        shutil.copytree(repo / 'bridge', root / 'bridge', ignore=shutil.ignore_patterns('__pycache__', 'test_*', 'fixtures'))
        (root / 'models').mkdir()
        shutil.copy2(a.full_asset, root / 'models/full.flyb.gz')
        shutil.copy2(a.source / 'src/main/resources/connectome/malecns-v1.0.flyb.gz', root / 'models/retained.flyb.gz')
        for name in ['neural_server.py', 'install_neural_server.py', 'verify_malecns_worker.py', 'verify_malecns_bridge.py']:
            shutil.copy2(Path(__file__).with_name(name), root / name)
        for name in ['MALECNS_FULL_GRAPH_115_MANIFEST.json', 'MALECNS_FULL_GRAPH_115_PROVENANCE.json']:
            shutil.copy2(repo / 'docs/neural' / name, root / name)
        shutil.copy2(repo / 'docs/neural/SERVER_PACKAGE.md', root / 'README.md')
        (root / 'SHA256SUMS.json').write_text(json.dumps({str(f.relative_to(root)): digest(f)
            for f in sorted(root.rglob('*')) if f.is_file()}, indent=2) + '\n')
        a.output.parent.mkdir(parents=True, exist_ok=True)
        pending = a.output.with_suffix('.pending')
        with tarfile.open(pending, 'w:gz') as archive:
            archive.add(root, arcname=root.name)
        pending.rename(a.output)
    print(json.dumps({'package': str(a.output), 'bytes': a.output.stat().st_size, 'sha256': digest(a.output)}))


if __name__ == '__main__':
    main()
