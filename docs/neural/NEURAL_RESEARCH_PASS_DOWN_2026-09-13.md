# Neural research and trace evaluation passdown — 2026-09-13

Meow: this pass adds reproducible research evidence and fixes offline reconstruction assumptions. It does not replace the avatar's synthetic controller with a biological brain. Parent owns Android integration and phone performance evidence; no Android files, Gradle tasks, installs, or robot commands were executed in this research pass.

## Corrected anatomical provenance

The earlier fruit-fly research note described the historical FAFB brain dataset. That limitation must not be generalized to today's BANC release. The primary [BANC project](https://github.com/htem/BANC-project/) describes a unified brain-and-ventral-nerve-cord connectome (v888). Its README declares repository code and Dataverse data CC BY 4.0, with individual archives potentially carrying additional licenses. This is project-reported provenance, not blanket commercial clearance for every connected artifact. The older [FlyWire guidelines](https://home.flywire.ai/guidelines) identify FAFB public v783 as CC BY-NC 4.0. These are different datasets and licensing scopes. The [Codex FAQ](https://codex.flywire.ai/faq) identifies BANC versions and sign-in/token access requirements. Public source files were sufficient here; no authenticated database access was bypassed.

A connectome gives anatomical connections. It does not, by itself, specify every synaptic sign, conductance, delay, muscle attachment, sensory transformation, or body calibration needed to reproduce walking or flight. Humanlike arm gestures are an authored avatar mapping: they are not evidence that the biological fly performs the same action.

The official [FlyBody repository](https://github.com/TuragaLab/flybody) supplies a body and locomotion simulation framework; its [code license](https://github.com/TuragaLab/flybody/blob/main/LICENSE) is Apache 2.0. Check separately downloaded trajectories and policy weights independently. [NeuroMechFly](https://neuromechfly.org/) is a separate embodied simulation project with sensory and body models; its current API differs from older examples. Neither framework was installed or run here. Their presence does not validate the Android wing animation as physical flight.

## Completed real-data experiment: bounded roster quality audit

The concrete permissible experiment was structural quality control of a small public anatomical candidate roster, kept outside the commercial app checkout. It deliberately does not estimate physiology or train motor weights.

Pinned source: BANC-project commit `e31a2e26b9937dca72e5ca1c1960df6454d76114`, `data/synapses/banc_888_v2_synapse_sample_small_2026-04-21.csv`. [Source file](https://github.com/htem/BANC-project/blob/e31a2e26b9937dca72e5ca1c1960df6454d76114/data/synapses/banc_888_v2_synapse_sample_small_2026-04-21.csv) and [synapse QC description](https://github.com/htem/BANC-project/blob/e31a2e26b9937dca72e5ca1c1960df6454d76114/data/synapses/README.md). Attribution: Bates, Phelps, Kim, Yang et al., *Distributed control circuits across a brain-and-cord connectome*, BANC-project (2026).

`BANC_V888_SAMPLE_MANIFEST.json` pins source revision, hash, bytes, rows, attribution, license evidence, and anatomical versus synthetic provenance. The actual CSV remains at `/tmp/meow-banc-research-2026-09-13/banc_v888_review.csv`; only manifest and aggregate audit are in the repository.

| Observation | Measured result |
| --- | ---: |
| Bytes / rows | 218,533 / 1,263 |
| Unique synapse IDs / endpoint IDs | 1,263 / 2,472 |
| Observed directed pairs / self-connection rows | 1,262 / 10 |
| Weak components / largest component nodes | 1,220 / 7 |
| Central brain / optic lobe / outside / VNC rows | 734 / 79 / 17 / 433 |
| Endpoint labels fragment / identified / proofread | 1,388 / 32 / 1,106 |
| Rows with at least one fragment endpoint | 1,050 (83.14%) |
| Blank human-review Tags | 1,263 |

All review tags are blank. Therefore this file cannot estimate synapse precision, label candidates correct/incorrect, or establish a functioning circuit. Graph components describe this sampled roster, not fragmentation of the complete brain. Repeated pairs are sampled observations, not complete synaptic weights. The result explains why copying a random collection of genuine synapses would not produce correct fly locomotion.

The validator enforces a 5 MB / 100,000-row ceiling, exact SHA-256/bytes/rows, required metadata, HTTPS provenance, unique synapse IDs, and positive decimal-string identifiers. Keeping large root IDs as strings avoids irreversible floating-point rounding. Five tests use synthetic CSV fixtures and test corruption, provenance omissions, duplicate IDs, graph statistics, and determinism.

Reproduce from the repo root (no package installation required):

```sh
mkdir -p /tmp/meow-banc-research-2026-09-13
curl --fail --silent --show-error --max-time 20 --max-filesize 1000000 'https://raw.githubusercontent.com/htem/BANC-project/e31a2e26b9937dca72e5ca1c1960df6454d76114/data/synapses/banc_888_v2_synapse_sample_small_2026-04-21.csv' -o /tmp/meow-banc-research-2026-09-13/banc_v888_review.csv
python3 scripts/neural/audit_connectome_sample.py --input /tmp/meow-banc-research-2026-09-13/banc_v888_review.csv --manifest docs/neural/BANC_V888_SAMPLE_MANIFEST.json --output docs/neural/BANC_V888_SAMPLE_AUDIT_2026-09-13.json
python3 -m unittest discover -s scripts/neural -v
python3 scripts/neural/evaluate_trainer.py --output docs/neural/TRAINER_EVALUATION_2026-09-13.json
```

Next meaningful biological experiment: select an explicitly identified, connected sensorimotor subcircuit with reviewed cells, full within-circuit edge counts, versioned cell labels, and a separately documented licensing boundary. Compare anatomical adjacency against degree-preserving randomized adjacency and a matched synthetic network on a fixed sensory-response task. Declare unknown synaptic physiology and fitted parameters as assumptions; hold out perturbation responses. Only afterward attach its outputs to a simulator with measured kinematics. The downloaded random QC roster cannot support that experiment, so it was not relabeled as a successful circuit controller.

## Offline trace fixes and tests

`train_neural_core.py` now requires trace format v2 with `startsFromReset=true`, exact ordered layouts, matching checkpoint name, at least two samples, and explicit finite positive `dtMs` up to 1,000 ms. `--from-reset` is retained only for CLI compatibility: a user assertion cannot supply missing recorder evidence. Rotated continuation and legacy traces are rejected for fitting. Reconstruction uses each recorded core integration interval rather than deriving it from wall timestamps. Timestamp gaps can include pauses and economy scheduling, so wall time alone is not the integrator input.

The trainer's readout fit reconstructs recurrent hidden state and fits its output weights and baseline; it does not fit raw sensory inputs as if they were hidden units. Synthetic known-teacher evaluation uses 400 training and 400 held-out samples with different pulse schedules. `TRAINER_EVALUATION_2026-09-13.json` records held-out MSE `4.2600429813441213e-17`, versus constant-training-mean MSE `0.018955914750724126`. Recovered weights are approximately `[0.3, 0.2]` and baseline `0.2`. This is a numerical correctness fixture, not measured user preference, biological realism, or learned phone behavior.

Current verification: 13 Python unit tests pass (eight trainer, five provenance audit). No raw audio, frames, transcripts, biological weights, or private data are included.

## Remaining trace and comparison limits for Meow

The audit identified checkpoint-name ambiguity, dropped samples with no serialized continuity, and fault resets without provenance. Root is adding `checkpointJson` (full serialized capture checkpoint), contiguous `stepIndex` starting at one, and `resetGeneration` starting at zero and increasing on core fault reset. The Python validator now requires parsed checkpoint contents to equal the supplied checkpoint, strictly integral contiguous ordinals, and generation zero throughout. Missing nullable legacy fields, changed weights under the same name, gaps, and fault generations all fail closed. Root owns Android recorder integration and its tests; this worker verified Python rejection with an additional regression test. Substrate changes must still end/reset recording, and any future reset path must update generation; otherwise a recorder can lie accidentally even though its schema is correct. These checks validate evidence and do not recover omitted samples.

The low-level Python reconstruction function can be used for generated fixtures; callers accepting external recordings must run provenance validation first. The CLI does so. Header evidence is necessary but cannot reconstruct events the recorder never wrote.

The existing same-sequence RNN/SNN comparison establishes determinism, bounded outputs, divergence, and execution cost. It does not establish which model makes a better companion. A quality comparison needs held-out cue-to-action episodes with target response delay, false-startle rate, gaze settling, interruption correctness, and animation repetition, including a stateless baseline and equalized parameter/compute budgets. Root is adding phone execution timing and motor arbitration separately; those measurements must remain distinct from accuracy claims.

## How to continue without repeating mistakes

Keep source revisions and artifact licenses alongside results. Re-download to a separate research directory and fail on hash changes instead of silently updating the manifest. Never convert anatomical IDs through float. Inspect annotation completeness before scoring quality. Preserve actual core integration time and reset continuity before fitting recurrent models. Export source, seed, split, configuration, and limitations with every comparison. Parent integrator should stage these exact scripts and docs with the shared batch and record its final commit; no commit was created by this worker.
