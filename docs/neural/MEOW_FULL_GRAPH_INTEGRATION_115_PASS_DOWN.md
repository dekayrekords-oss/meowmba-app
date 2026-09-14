# Build 115: full annotated MaleCNS graph and measured avatar motors

The user asked to incorporate the entire fly brain, or as much as possible.
Build 112 already stepped all 176,422 annotated neurons, but its graph dropped
connection pairs with fewer than five synapses and all self-connections. Build
115 restores the available positive connections between those same neurons.
This is the MaleCNS v1.0 brain, optic lobes and ventral nerve cord dataset; do
not describe it as the different FAFB/FlyWire dataset or a validated animal.

## What changed and why

The official confidence-0.5 flat connectome is converted to the existing FLYB
format without changing neuron annotations, transmitter signs or the retina
projection. All 6,287,749 old pairs match the official source exactly. The new
asset has 25,862,574 directed pairs containing 125,024,863 anatomical synapse
counts, including weak connections and 122 self-connection pairs. These are
weighted counts, not 125 million independently modeled synaptic compartments.
The 176,422-neuron roster stays fixed. Connections involving unannotated
segments are reported and excluded because their cell identity/parameters are
not established; silently inventing neurons would not constitute integration.

`MALECNS_FULL_GRAPH_115_PROVENANCE.json` records exact included/excluded counts,
source checksums and preservation checks. `MALECNS_FULL_GRAPH_115_MANIFEST.json`
is the strict deployment identity. The new compressed asset is 79,963,380 bytes,
SHA-256 `8860e68b59e454d8d4e8bd601dd7a3cb9049d18516f1ea8df34eabe1ae400d08`.
It stays on Meow, outside Git and the Android package.

The Java worker lazily loads one named graph at a time, verifies every CSR row,
weight, target, roster hash and total, then runs the audited point-neuron LIF
implementation. Retained protocol 1 remains compatible with Build 112. Full
protocol 2 requires a separate session and exact graph identity. The Mac bridge
accepts only six bounded sensory scalars and the fixed graph selector. It keeps
one in-flight request, a total deadline, duplicate caching, session-specific
stop and 15-second idle shutdown. Pairing, certificate pinning and HMAC remain.

Android requests FULL only when the user has selected MaleCNS; it does not
change the saved neural mode/substrate. Full responses require the pinned hash,
counts, protocol, session, sequence and finite measured motor rates. Old missing
body measurements remain unavailable rather than fabricated zeros. Late or
mismatched replies cannot drive the avatar. The graph is never simulated on the
phone. No camera, microphone, robot or care preference is enabled by this work.

Additional measured populations expose forward (DNp09), backward (MDN), halt
(DNg60), landing (DNp07/DNp10) and takeoff (DNp11/DNp02/DNp04), alongside existing
DNa02 left/right turning, DNp01 escape, TTMn jumping, DNg62/DNge078 grooming and
DNg02 wing drive. The full graph receives existing abstract food, touch, looming
and motion cues. Labels describe a bounded cartoon interpretation of population
activity, not validated motor physiology. DNp09 remained silent in the acceptance
stimuli: a measured zero is retained; no synthetic forward firing was added.

`FlyMotorIntent` translates fresh measured rates into bounded body proposals.
The 3D world uses these only during free exploration. Touch destinations and
explicit habitat actions keep priority. Stop inhibits translation; landing and
grooming settle toward the surface; takeoff/jump/escape lift; asymmetric DNa02
rates turn the body. Existing velocity, room, collision, grasp and active-time
limits still apply. Grounded grooming animates the front arms toward the
antennae without adding meshes or a new render timer. Static avatars retain
existing gaze, reaction and wing pathways. This is a model-driven kinematic
cartoon, not aerodynamic flight or a physical actuator controller.

The Brain details sheet reports graph coverage, real additional population
rates and the limits of the model. Full graph requests run at most every 400 ms;
each advances exactly 50 ms of neural time. Animation interpolates independently.
Shadow/Classic, stale data, hidden stages, background lifecycle and cooling have
no body authority. Existing thermal limits remain unchanged.

## Validation and performance lessons

The complete conversion took 35.19 seconds, peak RSS 1,184,868 KiB, no swaps.
All fourteen converter tests passed in Meow's isolated PyArrow environment;
the Mac passed thirteen with the Arrow-specific test skipped. Ten bridge tests
passed. The initial focused Android run passed 33 tests with no failures/skips,
covering exact full identity, legacy compatibility, request cadence, freshness,
mode/thermal authority, movement, deterministic replay, bounds, explicit input
priority, grounding/grooming and existing native Brain UI. Final package and
server acceptance results are recorded separately in Build 115 verification.

The first full-graph run exceeded the old 200 ms compute target (p95 244.670 ms).
Do not erase that failure or claim it met the original budget. We changed the
actual Android cadence to 400 ms and defined a 300 ms compute budget for FULL;
retained stays at 200 ms cadence/budget. A longer 40-tick, eight-scenario run
then passed both budgets: full p95 254.053 ms, retained 117.338 ms; full cold
request 654.458 ms. Both graphs together were tested sequentially in one bounded
JVM, peak process-tree RSS 574,072 KiB, no swaps. No two graph instances remain
resident deliberately, and the production process uses one CPU and a 1 GiB heap.
These short compute tests do not establish sustained phone heat/battery behavior.

All nine separate full-model checks passed: quiet silence, complete neuron-state
reset replay, observer-independent states, food/groom/jump responses, bitter
suppression, and feeding/jump activity disappearing after connection ablation.
`verify_malecns_worker.py` also checks exact duplicate replies, directional motion,
new-session replay, finite rates, exact identity and profile-switch rejection.
The ablation probe runs offline only; production cannot request an altered graph.

## Meow deployment and troubleshooting

- Data/source directory: `/home/prog/research/malecns-full-2026-09-13`.
- Original pinned model checkout: `/home/prog/research/malecns-2026-09-13`,
  upstream revision `6cfa30175003ef25da68a237d5eda958f8047b82`.
- Verified worker classes/license/profile: `/home/prog/research/malecns-worker-115`.
- Mac helper: `~/.meowmba-bridge/runtime-115`, launch label
  `com.meowmba.paired-bridge`. Only its neural module changed; other helper Python
  files match runtime 112. Existing pairing/certificate bytes are preserved.
- Backup of previous config and launch plist:
  `~/.meowmba-bridge/backups/build115-20260913-213330`.
- Keep server build assets `models_bundled`, `mlkit_pose` and
  `mobile_acceleration` untracked and intact. Build using the prebuilt native
  directory documented in Build 114. No public source push is authorized.

Use the provenance manifest first when an identity check fails. Do not relax a
hash or source threshold, clip weights, or silently substitute the retained
graph. Check file sizes/checksums and regenerate into a new output path.
Python 3.14 lacked the attempted PyArrow 19.0.1 wheel; PyArrow 25.0.1 and NumPy
2.3.5 work in the isolated venv. Avoid modifying production Python dependencies.
The converter preserves all old edges, reports every excluded row, and rejects
fractional/negative/overflowing weights. Its passdown explains the disk-sort
algorithm and recovery details.

A helper restart returns before the listener is ready. The first live test saw
connection refused immediately after launch; it made no neural request. Retry
only after the launch service is listening, without changing pairing or TLS.
For later unavailable status, check helper startup, private SSH, worker identity,
latency, selected mode and freshness in that order. Stale results never justify
raising the 1,800 ms motor freshness limit. No automatic catch-up or queued
simulation is allowed. End tests with `malecns_stop`; an idle worker must exit.

For inert body movement, distinguish a valid zero population rate from missing
telemetry. Check Shadow versus Neural, remote freshness, stage/lifecycle/thermal
eligibility and explicit habitat action priority. READ/DRINK/REST remain explicit
commands until another habitat command changes them; choose Explore for free
brain-driven movement. Forward activity is not guaranteed by the current cues.
Adding arbitrary activation would obscure what the anatomical network does.

For memory questions, loading more anatomical connections does not add a second
chat brain or biological learning. Room/SQLite conversation memory, optional
Mem0 sync and optional project-note memory are unchanged. Mushroom-body neurons
are in the roster, but the model has fixed weights, no validated neuromodulation,
dendritic compartments, receptor physiology, learned synaptic plasticity or
persistent biological memories. Robot transfer remains unvalidated without
hardware. Phone installation, live phone pairing/GLES and sustained heat/battery
acceptance remain separate work while the user continues without the phone.

## Sources and attribution

Data: Janelia FlyEM, University of Cambridge/MRC LMB and Google Research,
[MaleCNS downloads](https://male-cns.janelia.org/download/) and
[v1.0 release](https://male-cns.janelia.org/release/),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). The official source
URL/hash is preserved in the provenance file. Simulator: Blendi Remade / fal.ai,
[fly-brain-minecraft](https://github.com/blendi-remade/fly-brain-minecraft), MIT;
the upstream license is retained with deployed classes. Local changes include
replay/observer fixes from the earlier audited model, complete graph conversion,
strict profiles, readouts and bounded avatar retargeting. Do not claim that
open-source connectivity establishes whole-animal biological fidelity.
