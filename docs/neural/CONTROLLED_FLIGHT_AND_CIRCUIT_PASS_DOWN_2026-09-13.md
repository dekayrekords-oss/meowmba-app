# Controlled flight and authentic circuit import — 2026-09-13

Meow: this pass moves beyond finite-step smoke. It evaluates the official pretrained FlyBody flight policy in its actual aerodynamic task and imports a genuine published anatomical circuit graph separately. These remain different experiments. The policy is a synthetic reinforcement-learning controller, not a real fly neural connectome, and neither experiment is shipped in the APK.

## Source and supplementary-data boundary

The FlyBody source remains pinned to `d015e9bfe441bd90ae431bac24c55cb74bdbce26` in `/home/prog/research/flybody-smoke-2026-09-13/source`. The [official example notebook](https://github.com/TuragaLab/flybody/blob/d015e9bfe441bd90ae431bac24c55cb74bdbce26/docs/fly-env-examples.ipynb) shows the saved-policy and wing-pattern workflow used here. The harness uses equivalent observation batching, distribution means, float32 policy input, and canonical action scaling with clipping. It does not require Acme/Ray or any training stack.

Source code/model Apache 2.0 terms do not cover all supplements by assumption. The [Figshare metadata API](https://api.figshare.com/v2/articles/25309105) reports article version 4, DOI `10.25378/janelia.25309105.v4`, with **GPL 3.0+** license. Its official download URLs identify `trained-fly-policies.zip` (file 44815195, 6,537,720 bytes) and `datasets_flight-imitation.zip` (file 51196859, 12,880,076 bytes). Article license and file metadata are pinned in `FLYBODY_FLIGHT_DATA_PROVENANCE_2026-09-13.json`. These assets are restricted to the separate research workspace in this project; do not copy the policy, wing pattern, or trajectory dataset into the commercial app without assessing that distribution's obligations.

Both archives matched their supplied MD5 values. Computed SHA-256 hashes are `2d9937c9af2baafad1690c1b318791bde417b4d26dd96d4385ab6723d5d58582` (policies) and `0d152331e38f2ca6bb1f3286c2500eab49b5ccef93c51a9cc9cff9bb6cd368d0` (flight data). Extraction rejected absolute/traversal paths and total expanded sizes over 100 MB. The evaluator rechecks the archive hashes and exact extracted policy/wing-pattern bytes. It checks the source revision and clean state. No arbitrary downloaded Python or pickle was executed; TensorFlow loads the official SavedModel as model data in the research process.

## Environment issue solved

Hardware inventory reported an RTX 3090 and 3090 Ti. No GPU was needed: all policy inference used CPU with CUDA hidden and TensorFlow/BLAS thread limits set to one. The virtual environment remains under the research directory; system Python and the Android build environment were unchanged.

The initial modern TensorFlow 2.15 / Probability 0.23 combination could not deserialize the old `Independent_ACTTypeSpec`. Importing the distribution did not fix the schema mismatch. Probability 0.16 with modern TensorFlow also failed because function argument specifications had changed. Restoring the upstream-declared **TensorFlow CPU 2.8.0 and TensorFlow Probability 0.16.0**, with Keras 2.8.0 and protobuf 3.20.3, successfully loaded the SavedModel. Do not solve this by monkeypatching type registries or fabricating policy outputs. The complete resolved environment is in `scripts/neural/flybody-flight-requirements.lock.txt`; the older smoke lock remains its historical environment record.

MuJoCo is 3.3.7, dm-control 1.0.34, NumPy 1.26.4, Python 3.10.20. A GLFW warning says DISPLAY is missing; no viewer/render call is required for numerical evaluation. Actual physics results remained valid and warning counters inside MuJoCo were checked separately.

## Predeclared evaluation and short results

The official task combines an authentic baseline wing kinematic pattern with the policy's joint adjustments/frequency modulation. Controls enter force-based wing actuators and MuJoCo's fluid dynamics. No external body forces, root teleports during rollout, or post-step state corrections stabilize the fly. The single initial perturbation is explicitly part of its test condition.

Before running, four synthetic references were declared: hover, constant forward speed 15 cm/s, constant forward speed 25 cm/s, and forward speed 20 cm/s with an initial 0.15 cm lateral displacement. They used seed 20260913 and a 0.3-second horizon. These conditions were held out from all **local** tuning because there was no local fitting. The external policy's training overlap is unknown; do not claim a certified unseen biological dataset or paper-level generalization score. The real flight dataset was downloaded as part of the official supplement but its recorded trajectories were not scored in these synthetic tests.

| Condition | Policy position RMSE (cm) | Policy orientation RMSE (rad) | Policy duration (s) | Baseline duration (s) |
| --- | ---: | ---: | ---: | ---: |
| Hover | 0.023069 | 0.032258 | 0.3000 | 0.0592 |
| Forward 15 cm/s | 0.014312 | 0.045668 | 0.3000 | 0.0614 |
| Forward 25 cm/s | 0.033952 | 0.147924 | 0.3000 | 0.0638 |
| Forward 20 cm/s + displacement | 0.062493 | 0.308345 | 0.3000 | 0.0626 |

All policy cases completed the requested duration. All zero-canonical-action wing-pattern baselines terminated early under the unchanged task conditions. These baselines include the pattern generator; they are not an entirely passive fly. Their RMSE is measured only until termination, so compare duration alongside error instead of using a misleading aggregate error ratio. Perturbed-policy final error fell to 0.029831 cm. All eight cases had finite states and zero MuJoCo warning counts.

There were 28,940 physics steps in one complete short comparison. A separate process repeated all eight trajectories with identical full-qpos hashes. This is same-host/version repeatability, not a guarantee across platforms. `FLYBODY_CONTROLLED_FLIGHT_2026-09-13.json` contains the original measured report.

## Sustained evaluation

A separate predeclared extension tests hover, forward 20 cm/s, and initial lateral perturbation for three seconds, includes matching zero-action baselines, and repeats hover. Success requires the full requested duration, maximum CoM reference error below 0.4 cm, finite state, and zero MuJoCo warnings. The helper normally imposes a 0.6-second horizon; the research harness explicitly extends both task and environment horizon, without changing source files or dynamics. Actual CoM/reference positions are sampled every 10 ms for a plotted artifact. The corrected run completed **243,688 physics steps** across seven episodes in **213.18 wall seconds**. All three policy scenarios and repeated hover completed 3.000 seconds (15,000 control steps / 60,000 physics steps each), with finite state, no MuJoCo warnings, and maximum error below the declared limit. Baselines terminated at 0.0592–0.0626 seconds and failed the duration/error criteria. Repeated hover produced an identical full-trajectory hash.

| Three-second condition | Position RMSE (cm) | Maximum error (cm) | Declared criteria |
| --- | ---: | ---: | --- |
| Hover | 0.023458 | 0.029125 | Pass |
| Forward 20 cm/s | 0.028103 | 0.038166 | Pass |
| Forward + 0.15 cm lateral displacement | 0.033163 | 0.149572 | Pass |

The aggregate report and sampled measured states are in `FLYBODY_SUSTAINED_FLIGHT_2026-09-13.json`. `FLYBODY_SUSTAINED_FLIGHT_2026-09-13.png` plots actual CoM error and height, with baseline curves ending at the last 10 ms sample before their termination times. `plot_flybody_flight.py` reproduces the plot without a viewer or GPU. The plot was opened and visually checked for labels and honest time axes.

A preliminary horizon extension ended one 200-microsecond control tick short because the upstream task subtracts future-reference lookahead. It correctly failed the full-duration criterion. We fixed reference/horizon padding and reran the complete sustained evaluation; no criterion or controller was relaxed. These three-second synthetic trials demonstrate controlled flight under the tested simulator conditions, not lifetime stability, realistic biology, or phone readiness.

## Genuine anatomical graph imported separately

The earlier random BANC QC roster could not yield a coherent motor circuit. The primary [BANC Fig. 3h edge export](https://github.com/htem/BANC-project/blob/e31a2e26b9937dca72e5ca1c1960df6454d76114/figures/vignettes/network_fig3h_edges.csv) and [identified-neuron roster](https://github.com/htem/BANC-project/blob/e31a2e26b9937dca72e5ca1c1960df6454d76114/figures/vignettes/network_fig3h_neurons.csv) offer a concrete better source: 76 distinct root IDs in seven named groups, with 14 directed grouped connections totaling 961 reported anatomical counts. One published connection is DNg27 → wing_power_motor_neuron with count 265. Source commit and file hashes are pinned by `import_banc_vignette.py`; actual normalized adjacency and provenance are in `BANC_FIG3H_GROUP_GRAPH_2026-09-13.json`.

This is an authentic published **group-level** anatomical adjacency, not a random synthetic graph. The source declares CC BY 4.0 in its [license section](https://github.com/htem/BANC-project/tree/e31a2e26b9937dca72e5ca1c1960df6454d76114#license). Attribution: Bates, Phelps, Kim, Yang and collaborators, BANC-project, *Distributed control circuits across a brain-and-cord connectome* (2026). Raw CSVs stay outside the app checkout under `/tmp/meow-banc-research-2026-09-13/fig3h`.

This import has precise limits: grouped counts do not say which of the 76 neurons connects to which, and mixed neurotransmitter labels do not establish individual synapse sign or conductance. No conduction delay, muscle mapping, or validated physiological parameters are in these two exports. They can support a graph-constrained research model with declared synthetic dynamics, but cannot truthfully populate a biologically validated per-neuron flight controller. The group vignette alone cannot supply individual pairs. This pass subsequently resolved that limitation using the public full edge export below; no CAVE credentials were necessary.

## Authentic individual-neuron extraction completed

The public Harvard Dataverse metadata API succeeded for `doi:10.7910/DVN/7WTH1N`. It identifies release 3.0 with **CC BY 4.0** and unrestricted v888 v2 individual-neuron edge file ID **13992792**. The actual release file is 305,250,378 bytes, different from the older repository manifest estimate; the tool checks the current release checksum rather than pretending the files are byte-identical. Metadata evidence is saved in `BANC_IDENTIFIED_CIRCUIT_SOURCE_2026-09-13.json`.

A bounded download under `/home/prog/research/banc-fig3h-2026-09-13` matched MD5 `394406f8a9bdf093c895f95aff4f6c49` and SHA-256 `363fdef3813b72a5e45a42f17034cd5a544b654c838929cecf6e1ce5f60625cb`. `import_banc_identified_circuit.py` uses PyArrow 19.0.1 without parallel threads to filter both source and target IDs against the exact 76-neuron roster, preserving IDs as strings. This produced **515 distinct directed neuron pairs, totaling 3,316 anatomical synapse counts**, connecting all 76 roster neurons; 164 pairs have count at least five. The extraction took 0.623 seconds including source checksum validation. Raw subset SHA-256 is `2f2747c9ac506bf67a0239262833dda008d72d6b6d37bba2adb9ce75e93a1862`.

`BANC_IDENTIFIED_CIRCUIT_2026-09-13.json` contains aggregate results and full provenance. The source feather and individual-edge CSV remain outside the app checkout. The [official edge-table documentation](https://github.com/htem/BANC-project/blob/e31a2e26b9937dca72e5ca1c1960df6454d76114/manuscript/print/dataverse/documentation/banc_888_edgelist_simple_v2.md) describes anatomical count and normalized fraction of target inputs, with no conductance semantics. The induced graph omits every connection crossing outside the 76-neuron boundary. Its total differs from the displayed group vignette because graph display filtering/aggregation is not the same extraction; neither total was silently substituted for the other.

There is now a concrete authentic circuit import, not an access blocker. Remaining prerequisites for biological motor simulation are empirical or explicitly assumed synaptic dynamics, outside-circuit input handling, and an identified motor-to-muscle mapping. Anatomical counts must not be renamed as trained weights or claimed to reproduce real fly physiology. The successful flight policy above remains separate and contains no imported BANC synapses.

To reproduce the subset, use the separate research Python environment with PyArrow 19.0.1, download `https://dataverse.harvard.edu/api/access/datafile/13992792` with a 400 MB size cap, and run:

```sh
timeout 60 /home/prog/research/flybody-smoke-2026-09-13/venv/bin/python import_banc_identified_circuit.py --edges banc_v888_simple_v2.feather --neurons /home/prog/research/flybody-smoke-2026-09-13/flight-data/banc-fig3h-neurons.csv --subset identified-76-neuron-edges.csv --report identified-circuit-report.json
```

## Reproduction and next work

Rerun the installed environment:

```sh
ssh meow-nb
cd /home/prog/research/flybody-smoke-2026-09-13
timeout 400 venv/bin/python evaluate_flybody_flight.py --workspace . --output flight-sustained-rerun.json --duration 3 --sustained
```

For a fresh setup, follow the previous FlyBody simulation passdown, use the flight requirements lock, download the two pinned supplement files, check their hashes and safely extract, then copy the evaluator. Keep research dependencies and GPL-marked assets outside the APK checkout. Raw application audio/video/transcripts are not needed for this experiment.

Meow should use these results to distinguish three layers: anatomical connection provenance, learned motor policy, and physical body dynamics. A successful synthetic policy does not fill unknown biological parameters; a genuine anatomical graph does not prove a controller flies. Future biological experiments should keep explicit group-versus-neuron granularity, original counts, declared dynamic assumptions, and degree-matched controls. Future app work can use measured kinematics as design evidence while preserving the separately authored commercial avatar and its bounded neural arbitration. Physical robot quarantine remains unchanged.

## Verification and handoff

All 13 existing Python neural tests pass; new evaluator/import/plot scripts compile; `git diff --check` passes. Actual integration evidence consists of the repeated short flight run, seven sustained physics episodes, complete-pair extraction from the pinned anatomical file, and the opened trajectory plot. Raw supplements, full connectome export, and individual-edge subset remain in separate research directories. Only scripts, version locks, provenance/aggregate reports, and plotted measurements are staged by the parent. Parent owns the integrated commit; this worker did not build/install the app or actuate hardware.
