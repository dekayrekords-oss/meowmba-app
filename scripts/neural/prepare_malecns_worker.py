#!/usr/bin/env python3
"""Prepare only the audited Java worker; no Minecraft build or model downloads."""
import argparse
import pathlib
import shutil
from evaluate_malecns import REVISION, DATA_HASH, SOURCE_HASHES, digest, replay_patch, run

p = argparse.ArgumentParser()
p.add_argument('--source', type=pathlib.Path, required=True)
p.add_argument('--output', type=pathlib.Path, required=True)
p.add_argument('--jdk', type=pathlib.Path, required=True)
a = p.parse_args()
source = a.source.resolve()
if run(['git', 'rev-parse', 'HEAD'], cwd=source).stdout.strip() != REVISION:
    raise ValueError('Unreviewed source revision')
asset = source / 'src/main/resources/connectome/malecns-v1.0.flyb.gz'
if digest(asset) != DATA_HASH: raise ValueError('Incorrect data identity')
code = source / 'src/main/java/com/fruitfly/brain'
for name, expected in SOURCE_HASHES.items():
    if digest(code / name) != expected: raise ValueError('Unreviewed source modification')
a.output.mkdir(parents=True, exist_ok=False)
for name in SOURCE_HASHES:
    text = (code / name).read_text(encoding='utf-8')
    (a.output / name).write_text(replay_patch(text) if name == 'LifNetwork.java' else text, encoding='utf-8')
shutil.copy2(source / 'LICENSE', a.output / 'UPSTREAM_LICENSE.txt')
shutil.copy2(pathlib.Path(__file__).with_name('MaleCnsWorker.java'), a.output / 'MaleCnsWorker.java')
run([str(a.jdk / 'bin/javac'), '-encoding', 'UTF-8', '--release', '17', '-d', str(a.output),
     *[str(a.output / n) for n in SOURCE_HASHES], str(a.output / 'MaleCnsWorker.java')])
print('Verified worker compiled. Keep UPSTREAM_LICENSE.txt with the generated classes.')
