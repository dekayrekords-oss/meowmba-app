#!/usr/bin/env python3
"""Prepare only the audited Java worker; no Minecraft build or model downloads."""
import argparse
import json
import pathlib
import re
import shutil
from evaluate_malecns import REVISION, DATA_HASH, SOURCE_HASHES, digest, replay_patch, run

ROSTER_HASH = 'a21dcf2576c352eda0f4d455683b648ec28272fd7f4918fdfbf2c97aff9df465'
FULL_KEYS = ('schema', 'graph', 'dataset', 'asset_sha256', 'asset_size', 'neurons', 'pairs',
             'synapses', 'min_pair_synapses', 'autapses_removed', 'roster_sha256')


def full_profile(asset, manifest):
    """Convert the owner's exact JSON identity into Java's small, strict local profile."""
    if manifest.stat().st_size > 4096:
        raise ValueError('Oversized full graph manifest')

    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result: raise ValueError('Duplicate graph manifest key')
            result[key] = value
        return result

    data = json.loads(manifest.read_text(encoding='utf-8'), object_pairs_hook=unique_object)
    if not isinstance(data, dict) or set(data) != set(FULL_KEYS):
        raise ValueError('Unexpected full graph manifest fields')
    exact = dict(schema=1, graph='full', dataset='male-cns:v1.0', neurons=176422,
                 min_pair_synapses=1, autapses_removed=False, roster_sha256=ROSTER_HASH)
    if any(type(data[key]) is not type(value) or data[key] != value for key, value in exact.items()):
        raise ValueError('Unreviewed full graph identity')
    if not isinstance(data['asset_sha256'], str) or not re.fullmatch('[a-f0-9]{64}', data['asset_sha256']):
        raise ValueError('Invalid full graph hash')
    for key, low, high in (('pairs', 6287750, 30000000), ('synapses', 90296906, 200000000),
                           ('asset_size', 22964095, 300000000)):
        if type(data[key]) is not int or not low <= data[key] <= high:
            raise ValueError('Full graph exceeds reviewed capacity')
    if asset.stat().st_size != data['asset_size'] or digest(asset) != data['asset_sha256']:
        raise ValueError('Full graph asset identity mismatch')
    return ''.join(f'{key}={str(data[key]).lower() if isinstance(data[key], bool) else data[key]}\n' for key in FULL_KEYS)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=pathlib.Path, required=True)
    p.add_argument('--output', type=pathlib.Path, required=True)
    p.add_argument('--jdk', type=pathlib.Path, required=True)
    p.add_argument('--full-asset', type=pathlib.Path)
    p.add_argument('--full-manifest', type=pathlib.Path)
    a = p.parse_args()
    if (a.full_asset is None) != (a.full_manifest is None):
        p.error('--full-asset and --full-manifest must be supplied together')
    source, output = a.source.resolve(), a.output.resolve()
    if output == source or source in output.parents:
        p.error('output must be outside the upstream checkout')
    if run(['git', 'rev-parse', 'HEAD'], cwd=source).stdout.strip() != REVISION:
        raise ValueError('Unreviewed source revision')
    asset = source / 'src/main/resources/connectome/malecns-v1.0.flyb.gz'
    if digest(asset) != DATA_HASH: raise ValueError('Incorrect data identity')
    code = source / 'src/main/java/com/fruitfly/brain'
    for name, expected in SOURCE_HASHES.items():
        if digest(code / name) != expected: raise ValueError('Unreviewed source modification')
    properties = full_profile(a.full_asset, a.full_manifest) if a.full_asset else None
    output.mkdir(parents=True, exist_ok=False)
    for name in SOURCE_HASHES:
        text = (code / name).read_text(encoding='utf-8')
        (output / name).write_text(replay_patch(text) if name == 'LifNetwork.java' else text, encoding='utf-8')
    shutil.copy2(source / 'LICENSE', output / 'UPSTREAM_LICENSE.txt')
    shutil.copy2(pathlib.Path(__file__).with_name('MaleCnsWorker.java'), output / 'MaleCnsWorker.java')
    if properties is not None:
        (output / 'full-worker.properties').write_text(properties, encoding='utf-8')
    run([str(a.jdk / 'bin/javac'), '-encoding', 'UTF-8', '--release', '17', '-d', str(output),
         *[str(output / n) for n in SOURCE_HASHES], str(output / 'MaleCnsWorker.java')])
    print('Verified worker compiled. Keep UPSTREAM_LICENSE.txt with the generated classes.')
    if properties is not None:
        print('Full profile identity verified; worker will validate its complete CSR graph on first use.')


if __name__ == '__main__': main()
