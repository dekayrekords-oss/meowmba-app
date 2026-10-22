# Isolated FlyBody physics smoke — 2026-09-13

Meow: a real headless MuJoCo simulation now runs in a separate research workspace on SSH host `meow-nb` (reported hostname `meow`, user `prog`). This supersedes the earlier note that no simulator had yet been run. It verifies loading and stepping the anatomical body model, not learned locomotion or controlled flight.

## Isolation, source, and terms

Workspace: `/home/prog/research/flybody-smoke-2026-09-13`, approximately 433 MB after setup. Source, virtual environment, downloaded Python, and package cache all live there. No existing research directory was replaced. App checkout, system Python, Android runtime/toolchain, GPU configuration, and physical robot quarantine were unchanged. No renderer, simulator GUI, training process, hardware transport, or background service was started.

Pinned [FlyBody source](https://github.com/TuragaLab/flybody/tree/d015e9bfe441bd90ae431bac24c55cb74bdbce26): commit `d015e9bfe441bd90ae431bac24c55cb74bdbce26`. A clean checkout was checked before simulation. The repository's [Apache 2.0 license](https://github.com/TuragaLab/flybody/blob/d015e9bfe441bd90ae431bac24c55cb74bdbce26/LICENSE) was inspected; a recursive license/notice filename search found the root LICENSE and no additional such files. Repository model XML and meshes were used under this declared scope. No separately hosted trajectories, weights, or connectome files were downloaded for this experiment. This is not a blanket licensing conclusion about other FlyBody-related artifacts. Preserve the source license, notices, and attribution if any future redistribution occurs; nothing is embedded in the APK here.

Attribution: Vaxenburg and collaborators, *Whole-body physics simulation of fruit fly locomotion*, Nature (2025), [paper](https://www.nature.com/articles/s41586-025-09029-4). The [official README](https://github.com/TuragaLab/flybody) explicitly supports direct use of the body XML with MuJoCo, independently of reinforcement-learning task setup.

MuJoCo 3.3.7 has its own [Apache 2.0 license](https://github.com/google-deepmind/mujoco/blob/3.3.7/LICENSE), including third-party notices in that source. Its [Python documentation](https://mujoco.readthedocs.io/en/stable/python.html) provides the direct model/data/step API used here. No proprietary simulator activation or policy download was needed.

## Exact environment and workload

The host system Python was 3.14.4. FlyBody's declared NumPy 1.26.4 pin favors an older interpreter, so `uv` 0.11.24 downloaded Python 3.10.20 into this workspace and created a private venv. Only binary wheels were installed: MuJoCo 3.3.7, NumPy 1.26.4, and their dependencies. `scripts/neural/flybody-smoke-requirements.lock.txt` records all ten resolved package versions. This is a version lock, not a cryptographic wheel lock. Full FlyBody/dm_control/Ray/TensorFlow installation was unnecessary for direct XML physics, and was not attempted.

`scripts/neural/flybody_physics_smoke.py` loads unmodified `floor.xml` and `fruitfly.xml` from the pinned source. It checks source identity/cleanliness, executes two identical episodes for each model, verifies finite state and exact simulated-time progression, hashes every position/velocity state, and reports warnings. It rejects workloads outside 1–2,000 steps per episode, has a 30-second internal wall limit, and ran under an external 45-second timeout with numerical library thread counts limited to one.

The floor condition uses zero actuator control. The no-floor condition applies a deliberately synthetic `0.02 * sin(2π * 20 * t)` signal to all six wing actuators. This low-amplitude probe is not a biological wingbeat model, calibrated controller, learned policy, or flight demonstration. Gravity and fluid parameters remain the XML defaults.

## Observed results

`FLYBODY_PHYSICS_SMOKE_2026-09-13.json` contains the raw report, source/license/XML hashes, model dimensions, timings, and trajectory hashes.

| Measure | Result |
| --- | --- |
| Physics steps | 4,000 total (four episodes × 1,000) |
| Simulation timestep | 0.0001 seconds |
| Time per episode | 0.10000000000000184 seconds |
| Model dimensions | 109 position coordinates, 108 velocity coordinates, 78 actuators, 68 bodies |
| Same-process deterministic replay | Both pairs of complete trajectories byte-identical |
| Finite position/velocity checks | Passed on every step |
| MuJoCo warning counts | Zero in all four episodes |
| Maximum contacts | Floor: 10; no-floor wing probe: 6 (self-contact possible) |
| Measured wall duration | 1.8915825659933034 seconds including model loading |

The no-floor model has no supporting floor and is subject to gravity; finite stepping is not hover success. The report includes maximum absolute changes over generalized coordinates, which mix positions and angles and therefore are numerical diagnostics, not a single physical distance or locomotion quality score. Same-process bitwise repeats do not establish cross-platform deterministic behavior. The short episode says nothing about long-duration stability, realistic gait, neural control, or phone performance.

## Reproduce without touching the app environment

The existing workspace is preserved and ready to rerun:

```sh
ssh meow-nb
cd /home/prog/research/flybody-smoke-2026-09-13
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 timeout 45 venv/bin/python flybody_physics_smoke.py --source source --output physics-smoke-rerun.json --steps 1000
```

For a new isolated workspace, choose a new empty path under `/home/prog/research` and copy the harness and requirements lock from this repository. Initialize/fetch the exact source commit (do not silently follow main), then use this shape of setup:

```sh
# Run inside the NEW research directory; do not activate or modify another environment.
git init source
git -C source remote add origin https://github.com/TuragaLab/flybody.git
timeout 60 git -C source fetch --depth 1 origin d015e9bfe441bd90ae431bac24c55cb74bdbce26
git -C source checkout --detach FETCH_HEAD
UV_PYTHON_INSTALL_DIR="$PWD/python" UV_CACHE_DIR="$PWD/cache" timeout 90 /home/prog/.local/bin/uv venv --python 3.10.20 venv
UV_CACHE_DIR="$PWD/cache" timeout 90 /home/prog/.local/bin/uv pip install --python venv/bin/python --only-binary=:all: -r flybody-smoke-requirements.lock.txt
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 timeout 45 venv/bin/python flybody_physics_smoke.py --source source --output physics-smoke.json --steps 1000
```

No setup blocker remains for this smoke experiment. Next research work would need a specified motor-control task and physically interpretable metrics, then a bounded simulator policy comparison. Do not attach the app's synthetic neural outputs directly to torque channels and call it biological transfer. First define units, coordinate mapping, action ranges, and termination conditions; use simulator-only sinks and retain the physical-control quarantine. Neither trained policy evaluation nor the full FlyBody task stack was tested here.

Only the harness, dependency version lock, aggregate JSON result, and this passdown are added to the app repository for reproducibility. Parent owns the integrated git commit. Research source/model packages remain outside the app checkout.
