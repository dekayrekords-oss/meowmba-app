# Build 108 — artwork, native graphics, endurance and research

This is the current integration checkpoint for Meow on September 13, 2026. The
user explicitly resumed 3D fly work; historical September 5 exclusions do not
describe this branch. Canonical repository: `/home/claude/git/meowmba-app.git`.
Build checkout: `/home/claude/meowmba-fly-habitat`. Preserve the independent Play
package and all installed data. The current debug package is `com.example.meowmba`.

## What changed, how, and why

Ten original 1024×1536 paintings replace the simple Canvas room art while keeping
the existing environment IDs, animated light/mote accents, shared clock, static
backgrounds and separate fully volumetric game rooms. The exact image-generation
prompts, source paths, dimensions and SHA-256 hashes are in
`artwork/painted-environments-manifest.json`. Originals remain at their generation
paths; copies are bundled under `assets/backgrounds/painted`. This adds about
27 MiB of PNG assets. It is richer original artwork, not a literal tenfold
resolution increase or a substantiated AAA-quality claim.

The generated images did not place their table edge exactly where prompted.
Each actual edge was measured and mapped to the world's .78 contact plane with
aspect-preserving cover placement. This avoids moving the physics to fit a
painting or stretching the painting on a Fold. Full-size decoding runs off the
UI thread and preserves native resolution; thumbnails cap at 320px. Serialized
decoding and a 16MiB retained-pixel LRU bound cache allocation, not total app RSS.
Missing/corrupt art falls back to the existing vector room. See
`ILLUSTRATED_ENVIRONMENT_PASS_DOWN.md` for geometry and diagnosis details.

The native 3D pass smooths silhouettes, bevels furniture, removes shader noise
that caused checkerboard/striping artifacts, refines lighting and the small
smile, and enlarges the game fly by 22% while maintaining foot and grip alignment.
The 1.2-million-pixel target cap remains; small surfaces may supersample 1.35×.
Consequently the change is visual polish, not a claimed frame-cost reduction.
Four actual-phone GLES tests verify distinct front/side/back views, transparent
stage compositing, scale updates while paused, all ten room renders, fractional
edge coverage, premultiplied alpha and draw/triangle/pixel budgets. The native
kitchen screenshot in `validation108/game-kitchen.png` was visually inspected.

## Build and installation evidence

Debug code108/name1.3.81 assembled successfully on Meow, using the existing signed
native/runtime assets. First focused JVM batch: 50 tests passed, zero failures,
errors or skips. Additional recorded replay, bounded preferences, pet-world and
3D flight-world batch: 21 passed, zero failures, errors or skips. This is 71
focused tests, not a newly run whole-app regression suite. Python research tests:
13 existing tests plus eight new robot replay tests passed. Diff checks pass.

Signed APK SHA-256:
`fd07e7b559f9be4aa3e5ccc2c21857afb20dca586681e2ae8019fa03dedc1e1f`.
Signing certificate SHA-256:
`b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
In-place installation succeeded; package manager reports code108 and
lastUpdateTime `2026-09-13 10:17:26`. A 32,075,210-byte lossless delta reused
402,254,180 bytes from build103, and reconstructed the exact signed APK hash.
Do not install a differently signed Mac APK or uninstall to bypass signature
errors. The production Play package remains untouched.

All four build108 GPU tests passed on physical Samsung SM_F966U1, serial
RFCY61NJEVM. Build107's all-avatar real-phone gesture replay passed earlier this
turn after unlock. The first build108 gesture attempt hit keyguard and found no
Compose hierarchy; its failed attempt is preserved, not counted as a pass.
Host build108 replay covers every style and Lumen form in both modes.

## Endurance recorder and USB diagnosis

The ten-minute build107 host profile lost USB and contains only 43 foreground
samples / 78.12 seconds of usable CPU intervals. It measured mean71.42% of one
core, battery31.3→32.4C (max32.5), thermal0→0; this is not a sustained or unplugged
acceptance result. See `PHONE_PROFILE_107_SUMMARY.json`.

`PhoneEnduranceTest` is explicitly opt-in instrumentation. It launches the actual
MainActivity, temporarily applies only its keep-screen-on window flag, leaves
privacy choices and thermal policy intact, waits for real USB unplug if requested,
and appends local JSONL every ten seconds. It measures process CPU, battery charge
counter/temperature, thermal status, actual window focus/unlocked/interactive
state, and fly frames submitted/presented. `Fly3DPhoneTelemetry` has fixed-size
atomic lifetime counters; it adds no clock, file IO or frame history to the app.
Draw wall time includes EGL swap waiting and is not mislabeled GPU execution.

The recorder now waits for owner authentication before ActivityScenario launch;
launching behind keyguard otherwise fails before any sample. It does not unlock
or disable the lock screen. The caller's global stay-awake setting was already2
and was never changed. The window flag is restored at test exit/process teardown.
The shell helper targets only the specified physical serial, never the emulator.
It installs only the test APK, starts a detached recorder, and never retries an
uncertain launch: some Android shells retain an ADB session despite nohup, while
the on-phone test is already running. Verify the newest JSONL event instead of
starting a duplicate. USB disappearance was confirmed once at the Mac hardware
inventory level, not merely in ADB; neither Mac nor Meow saw a phone at that time.

Reproduction (actual in-place app must already be build108):

```sh
python3 scripts/start_phone_endurance.py --serial RFCY61NJEVM --test-apk build-out/habitat/tests108.apk --wait-for-device --seconds 600 --await-unplug-seconds 600 --label build108-unplugged-actual-avatar
```

Verify `/sdcard/Android/data/com.example.meowmba/files/phone-endurance-*.jsonl`
contains `waiting_for_unplug`, then physically unplug and leave the avatar visible.
Reconnect after ten minutes and pull the report. Do not use `dumpsys battery set`:
changing reported charging state does not stop actual charging. Summary command:

```sh
python3 scripts/summarize_phone_endurance.py REPORT.jsonl --out SUMMARY.json
```

The gate requires at least95% foreground samples, no severe thermal status and
at least5 presented frames/second averaged over the run. Unplugged validity also
requires all samples physically unplugged and available charge counters. This
is a minimum sustained-motion gate, not a 60fps promise. Review interval FPS,
CPU, temperature, pause state and completeness; a paused or incomplete run cannot
be used to advertise cooling improvements. Whole-phone drain includes radios,
display and other processes; ten minutes does not establish all-day battery life.

Build108 endurance outcome: measurement is in progress; append verified results
after retrieval. The verified starting screen shows the full fly over the user's
star background with MIC OFF and CAM OFF; it does not test every game room.

## Research milestones and remaining boundaries

Separate Meow research now evaluates an official FlyBody RL controller against
the actual aerodynamic simulator. Three-second hover, forward20cm/s and displaced
forward-flight trials passed predeclared duration/error/finite/warning criteria;
243,688 physics steps included matching baselines and repeatability. This is
controlled simulation under declared conditions. It does not contain an authentic
neural connectome and is not embedded in the APK. The pinned GPL-marked policy
supplements stay separate from the Apache source and the commercial artwork.

An authentic BANC v888 circuit extract now contains 515 directed individual-neuron
pairs among76 identified neurons and3316 original synapse counts, from verified
CC BY4 data. BANC is a separate brain-and-cord connectome source, not a relabeled
FlyWire dataset. Counts do not establish conductances, missing boundary inputs,
synaptic dynamics or muscle mappings. Those unresolved physiological facts still
prevent calling the app a biological fly controller. See
`../neural/CONTROLLED_FLIGHT_AND_CIRCUIT_PASS_DOWN_2026-09-13.md` and its plotted
measured trajectories, exact source hashes and reproducible environments.

The robot-transfer plan now has an executable transport-free intent simulator
with explicitly authored inertial parameters. Eight tests cover stale/order/
generation/NaN rejection, bounds, newest-only dispatch, interrupt/resume and a
bounded history. Its ten-second replay is deterministic; no actual neural output
or real robot dynamics is claimed. Exact robot model/firmware and availability
are still awaiting the user's answer. Existing physical quarantine remains:
the prior purported brake caused rotation, so unverified zero packets are not
assumed safe. See `../neural/ROBOT_INTENT_REPLAY_PASS_DOWN_2026-09-13.md`.

Meow should diagnose each layer separately: anatomy provenance, synthetic neural
dynamics, authored avatar motion, aerodynamic control, renderer presentation,
and actual hardware commands. Successful evidence in one layer does not prove
another. Preserve all new files in canonical Git and this passdown with the
signed build; never report the unresolved hardware/biology work as completed.
