# Build 112 — MaleCNS remote avatar integration

Implementation and host tests are complete; server assembly and phone acceptance
are pending at this checkpoint. The user authorized connecting the tested model
and installing the update. Build 111 is still the installed version until the
installation evidence below is updated.

## What changed and why

The new `MALECNS_REMOTE` substrate keeps the retained 176,422-neuron graph on
Meow. Android sends six bounded sensory scalars through the existing paired
computer's pinned TLS/HMAC Desktop Bridge. The computer uses the owner's SSH
configuration to a request-driven Java worker on Meow; no new public neural
endpoint is created. This route requires both the paired computer and Meow.
The app sends no raw camera frames, audio or chat through this feature.

`MaleCnsWorker` uses the four hash-pinned model classes from the previous audit,
including its independent observer RNG and deterministic reset correction.
`prepare_malecns_worker.py` compiles only those classes and the worker, copies
the MIT notice, and verifies source and data identity before preparing a runtime.
The 23 MB graph stays outside the app checkout. Its provenance and modeling
limits are in `docs/neural/MEOW_MALECNS_MODEL_PASS_DOWN_2026-09-13.md`.

Each request advances exactly 50 ms of neural time (100 half-millisecond steps).
The brain does not catch up missed wall time. One worker and a 512 MiB Java heap
bound the host workload. The bridge permits only one in-flight request, reuses
the worker, rejects invalid scalar/sequence/session inputs, and stops it after
15 seconds without requests. Closing SSH stdin ends the remote Java process.
Responses echo a session and sequence; exact duplicates do not advance twice.
The idle timer uses a generation lease so an expired timer cannot stop a newer
request. A stop for an old session cannot stop its replacement.

`MaleCnsRemoteCore` never blocks Android's controller thread. It permits one
asynchronous request, at most five per second, and uses the original input's
monotonic age to reject replies older than 1.8 seconds. Failures back off for
2.5 seconds. New sessions and cancellation prevent late replies after a reset,
network switch, hidden avatar, backgrounding, or severe heat. Classic behavior
stays warm and automatically takes over while the remote model is unavailable.
Shadow still publishes Classic; remote wing signals require Neural control.
Hidden/resumed input buffers are cleared to avoid replaying old touches.

Food stimulates named sugar GRNs, touch stimulates the grooming inputs, looming
stimulates LC4/LPLC2, and motion stimulates LC10a populations. Measured MN9,
descending/grooming/escape, DNg02 and turning activity is mapped through bounded
authored readouts to existing attention, actions and wing presentation. Neural
feeding can request `content_smile`, with existing busy/ownership/cooldown
guards. These are avatar retargeting choices, not biological muscle commands.
Pet care remains optional and its existing rules remain authoritative.

Neural setup explains the connection and data boundary. Brain activity displays
actual measured feeding/escape/grooming/wing population rates, connection state,
remote compute and the separate neural clock. It introduces no render timer.
The local trace switch is disabled for MaleCNS: a local feature trace lacks the
remote state needed for honest replay. The separate synthetic phone benchmark
now explicitly selects only RATE/SPIKING instead of mislabeling those runs as
new biological substrates when the enum grows.

## Deployment paths and rollback

- Model source: `/home/prog/research/malecns-2026-09-13`.
- Compiled worker: `/home/prog/research/malecns-worker-112`.
- Java: `/home/claude/.local-toolchain/jdk-21.0.12+8/bin/java`.
- Mac paired helper: `~/.meowmba-bridge/runtime-112/meowmba-bridge.py`, version
  0.10.0; launch agent `com.meowmba.paired-bridge`. The unrelated older
  `com.memba.desktop-bridge` agent was not edited.
- Existing paired helper config retains its certificate, pairing and settings;
  added `malecns_enabled` and a fixed owner-side `malecns_worker_argv`.
  Backup: `~/.meowmba-bridge/config.before-build112.json` (private).
- USB: `bash scripts/connect_desktop_usb.sh` restores port 8765 forwarding
  without changing pairing. Network mode uses the same pinned certificate.

To disable the integration, choose Classic or another neural substrate in the
app. To remove the worker route, set `malecns_enabled=false` in the computer
helper config and restart that helper. Never clear app data, overwrite pairing,
disable certificate checks or remove private model assets to repair connectivity.
Do not publish Android source; use only the canonical private Meow Git repo.

## Verification at the implementation checkpoint

The focused Kotlin remote tests cover response identity/finite bounds, one
request in flight, freshness, reset sessions, late-result rejection, fallback
recovery, thermal suspension, food events and visual ownership. Five Python
bridge checks pass for malformed input, disabled execution, process reuse,
session-specific stop, timer races, malformed replies and no queue under load.
The 18 existing bridge control tests pass.

A live, certificate-verified, HMAC-signed Mac bridge probe reached Meow for six
requests: observed MN9 rates peaked at 100 Hz; round trips ranged 90.34–1616.34
ms including worker startup. Unsigned input received HTTP 401. Repeating the
same sequence returned the identical response without another neural step.
The worker was stopped after the probe. Evidence is in
`build-out/habitat/build112/bridge-acceptance.json`.

The opt-in `MaleCnsDeviceTest` (`maleCnsLive=true`) exercises the actual ViewModel,
paired transport, food/touch motor rates, phone controller p95 under 20 ms and
hidden wing-output suppression. `activate=true` retains the verified neural
selection only on success; ordinary invocation restores earlier choices. Pet
care state is restored. Camera/microphone preferences are checked unchanged.

Phone evidence, final test totals, signed APK identity and install status must
be appended after the build. This does not complete sustained heat/battery
validation, biological flight simulation, retinal/odor fidelity, or robot
transfer. The user has no robot; physical control remains quarantined.
