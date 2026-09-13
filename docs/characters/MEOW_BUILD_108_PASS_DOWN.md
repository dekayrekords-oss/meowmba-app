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
Host build108 replay covers every style and Lumen form in both modes. The
subsequent unlocked build108 device replay passed in10.103 seconds; see
`validation108/gesture-tests.txt`.

## Endurance recorder and USB diagnosis

The ten-minute build107 host profile lost USB and contains only 43 foreground
samples / 78.12 seconds of usable CPU intervals. It measured mean71.42% of one
core, battery31.3→32.4C (max32.5), thermal0→0; this is not a sustained or unplugged
acceptance result. See `PHONE_PROFILE_107_SUMMARY.json`.

`PhoneEnduranceTest` is explicitly opt-in instrumentation. It launches the actual
MainActivity, temporarily applies its keep-screen-on window flag, leaves
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
Separating directory creation from the backgrounded nohup command let the later
dispatch return immediately. A timeout remains an uncertain dispatch, not a
reason to start another recorder.

The later `--fixed-fly` option adds a visible countdown and tap-to-end label,
temporarily selects the full fly/game, and restores the prior style/mode on normal
exit. An explicit different selection during the run cancels the test and is
preserved. This temporary mode is opt-in; normal runs preserve all preferences.
The countdown is a test-only native TextView, not a product menu or a dialog that
steals window focus. A killed instrumentation process cannot promise finally-block
preference restoration; inspect the current avatar if the owner kills the test.

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

The first recorder gate required at least95% foreground samples, no severe
thermal status and at least5 presented frames/second averaged over the run.
The final schema2 recorder additionally requires active motion in at least95%
of sampled periods. The summary independently requires at least95% of intervals
to be foreground, active and at least5fps; this stricter summary also applies to
earlier schema1 reports. It prevents long paused stretches from being hidden by
a faster moving stretch in a whole-run average. Unplugged validity also
requires all samples physically unplugged and available charge counters. This
is a minimum sustained-motion gate, not a 60fps promise. Review interval FPS,
CPU, temperature, pause state and completeness; a paused or incomplete run cannot
be used to advertise cooling improvements. Whole-phone drain includes radios,
display and other processes; ten minutes does not establish all-day battery life.

Build108's first true unplugged run completed600.009 seconds and59 samples,
all physically unplugged. Whole-phone charge declined70.4mAh; battery33.1→32.7C,
max33.1C; Android thermal status remained0. The selected scene changed from the
full-size fly/star stage to the kitchen game, and the avatar was hidden for about
two minutes. It therefore **failed** the sustained-motion gate (48/59 foreground
samples) despite obtaining a real mixed-use battery measurement. Two sampling
gaps exceeded15 seconds. Over437.439 eligible active foreground seconds, app+
instrumentation CPU averaged55.41% of one core and presented frames20.77fps.
The aggregate entire-run average43.54% CPU includes hidden time and must not be
compared directly with an active-only baseline. Raw JSONL and summary are in
`validation108/phone-endurance-summary.*`.

The next USB run also completed ten minutes but changed avatar/style and had only
7/61 foreground samples. It failed the sustained gate; median interval fly FPS
was0. Battery33.7→34.0C, maximum Android status1. Its50.013 eligible active
foreground seconds averaged57.55% of one core and19.97fps. No unplugged-drain
claim is made for this USB run. See `phone-game-usb-summary.*`.

Afterward Android reported moderate status2 from its SKIN sensor (42C), with a
visible cooling banner; the battery sensor alone did not explain the governor.
A subsequent58.09-second screen-off profile measured approximately0.103% of one
core for the app, while charging battery temperature38.2→38.3C. This confirms low
background process cost for that interval, not foreground cooling acceptance or
proof of the cause of whole-phone heat. All samples were screen-off; the host
summary correctly reports zero foreground samples and no foreground CPU value.
The unverified earlier single `top` snapshot must not override this interval.

The fixed-fly countdown run is in progress. Append verified results after
retrieval. The original starting screen showed MIC OFF and CAM OFF. No complete
all-room, all-day battery or sustained uninterrupted foreground result is claimed
until that evidence actually passes. Do not relax the governor to make it pass.

The retrieved fixed-fly run completed 600.026 seconds with 61 physically
unplugged samples and a valid 79.2 mAh whole-phone charge decline. It stayed on
the kitchen game, but Android thermal status was already MODERATE (tier 2) at
start and the renderer remained paused for about 250 seconds before resuming.
Only 34/60 measured intervals met the active-motion threshold, so the stricter
summary correctly marks `validUnpluggedRun=false`; this is useful diagnostic
evidence, not a sustained animation acceptance result. Battery temperature fell
38.6→37.2°C and no severe status occurred. The raw JSONL and summary are
`validation108/phone-fixed-fly-unplugged-summary.jsonl/.json`.

The tile grid now includes a read-only Brain activity panel. It reuses the
controller's once-per-second `NeuralDiagnostics` stream to show mode, rate or
spiking substrate, core units, activity, steps, p95 step time, current action and
thermal tier. Its bars are a deterministic visualization of measured aggregate
core activity. The panel states that the separate BANC extract (515 pairs,
3,316 counts) is research provenance and is not a literal FlyWire spike trace or
an input to the avatar controller.

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
