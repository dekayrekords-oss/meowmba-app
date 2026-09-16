#!/usr/bin/env python3
"""Bounded direct-MuJoCo model smoke, with no viewer, network, policy or hardware."""
import argparse
import hashlib
import importlib.metadata
import json
import platform
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--steps', type=int, default=1000)
    args = parser.parse_args()
    if not 1 <= args.steps <= 2000:
        raise ValueError('steps must be 1..2000 per episode')
    import mujoco
    import numpy as np
    source = args.source.resolve()
    revision = subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'], text=True).strip()
    expected = 'd015e9bfe441bd90ae431bac24c55cb74bdbce26'
    if revision != expected or subprocess.check_output(['git','-C',str(source),'status','--porcelain'], text=True).strip():
        raise ValueError('requires pinned clean FlyBody source')
    assets = source/'flybody/fruitfly/assets'
    started = time.monotonic()
    reports = []
    for filename, wing_drive in [('floor.xml',False), ('fruitfly.xml',True)]:
        model = mujoco.MjModel.from_xml_path(str(assets/filename))
        wing_ids = [i for i in range(model.nu) if (mujoco.mj_id2name(model,mujoco.mjtObj.mjOBJ_ACTUATOR,i) or '').startswith('wing_')]
        trajectory_hashes, episode_reports = [], []
        for repeat in range(2):
            data = mujoco.MjData(model)
            mujoco.mj_resetData(model,data)
            mujoco.mj_forward(model,data)
            initial_qpos = data.qpos.copy()
            digest = hashlib.sha256()
            max_contacts = 0
            for step in range(args.steps):
                if time.monotonic()-started > 30:
                    raise TimeoutError('30 second smoke workload limit exceeded')
                data.ctrl[:] = 0
                if wing_drive:
                    # Deliberately synthetic low-amplitude probe, NOT a flight controller.
                    data.ctrl[wing_ids] = .02*np.sin(2*np.pi*20*data.time)
                mujoco.mj_step(model,data)
                if not np.all(np.isfinite(data.qpos)) or not np.all(np.isfinite(data.qvel)):
                    raise ValueError('nonfinite physical state')
                if abs(data.time-(step+1)*model.opt.timestep) > 1e-9:
                    raise ValueError('unexpected simulation reset or time discontinuity')
                digest.update(data.qpos.tobytes()); digest.update(data.qvel.tobytes())
                max_contacts = max(max_contacts,int(data.ncon))
            warnings = {mujoco.mjtWarning(i).name:int(data.warning[i].number) for i in range(len(data.warning)) if data.warning[i].number}
            trajectory_hashes.append(digest.hexdigest())
            episode_reports.append({'repeat':repeat,'steps':args.steps,'simulatedSeconds':float(data.time),
                'maxAbsQposChange':float(np.max(np.abs(data.qpos-initial_qpos))),
                'finalMaxAbsQvel':float(np.max(np.abs(data.qvel))), 'maximumContacts':max_contacts,
                'warningCounts':warnings, 'trajectorySha256':digest.hexdigest()})
        if trajectory_hashes[0] != trajectory_hashes[1]:
            raise ValueError('same-process deterministic replay mismatch')
        reports.append({'model':filename,'syntheticWingProbe':wing_drive,'wingActuators':len(wing_ids),
            'nq':model.nq,'nv':model.nv,'nu':model.nu,'nbody':model.nbody,
            'timestepSeconds':float(model.opt.timestep), 'gravityModelUnits':model.opt.gravity.tolist(),
            'densityModelUnits':float(model.opt.density), 'viscosityModelUnits':float(model.opt.viscosity),
            'identicalRepeatTrajectories':True,'episodes':episode_reports})
    result = {'scope':'direct body dynamics smoke; synthetic control probe; no learned policy, no sustained-flight or biological-control claim',
        'sourceRevision':revision,'sourceLicense':'Apache-2.0 repository LICENSE; no external trajectories or weights',
        'python':platform.python_version(), 'packages':{k:importlib.metadata.version(k) for k in ['mujoco','numpy']},
        'assetSha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [assets/'floor.xml', assets/'fruitfly.xml', source/'LICENSE']},
        'wallSeconds':time.monotonic()-started,'totalPhysicsSteps':args.steps*4,'models':reports}
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
