# Companion interactions, motor integration and research — September 13

## Shipped changes and why

Build 107 follows installed 106. All seven styles and all Lumen animal forms use
one gesture handler: attention taps, held/short-stroke petting, double-tap play,
and navigation swipes. A host Compose replay reproduced a lost first tap after
changing habitat/style. Restarting only the tap pointerInput while the adjacent
drag recognizer stayed alive was the cause: using a stable handler with updated
style/world references fixed it. Pet pulses now use event increments instead of
wall timestamps. Navigation produces no pet/learning event. Privacy controls
remain explicit; the original cards and Android Home intent behavior remain.

Jarvis, Godot/Pearl and Classic now follow the game world's position at habitat
scale, as Lumen already did. Static mode retains full-sized faces and the shared
scale setting. Full-fly expression passes independent blinks, smile/concern,
antenna lift/lag and integrated wing phase into the 3D rig. The fly approaches,
lifts and consumes visible fruit; takeoff from the floor no longer teleports,
resting strides settle, and a neutral hand follows body yaw. See the specialized
FLY_3D_MOTION_FEEDING_PASS_DOWN for implementation and regression details.

Ten shared Canvas environments gained cached lighting/material depth and bounded
ambient details, with stationary table contact preserved. This is a polish pass,
not a claim of AAA studio parity. The existing photographic backdrop and ten
separate 3D room layouts remain. Preference feedback now explicitly tells the
user whether a bounded update was saved, rather than silently discarding rapid
repeated feedback.

## Neural motor integration

Existing shared sensory features, recurrent/spiking cores and mode/fallback
controls remain. NEURAL mode's arbitrated orientation now reaches avatar gaze;
touch and observed camera gaze retain priority. This does not start camera or
microphone capture. Shadow/legacy modes do not take over gaze or clip selection.
The new NeuralMotorDirector maps inspect/startle/settle to existing allowlisted
clips, with a ten-second cooldown, no interruption of an existing performance,
and stage, conversation, background and thermal gates. Completion/interruption
of the authored clip lifecycle reports back to the arbiter; this is not proof of
physical action completion. Explicit stop reports INTERRUPTED. Real robot
transport quarantine is unchanged.

## Reproducible recording repair

V2 recordings begin atomically with reset under the controller monitor. Each
sample includes actual core dtMs, contiguous stepIndex and resetGeneration; the
header includes full checkpoint JSON and startsFromReset. Rotation marks a
continuation, so a rotated file cannot pretend it has a fresh hidden state.
Reset/substrate controls end recording. Fault resets increment generation.
Oversized samples are dropped without recursive rotation or file flooding;
unique file names avoid same-millisecond overwrites. Header size is cached, so
recording does not serialize the entire checkpoint on every tick. Nonfinite
serialization failure drops a sample instead of crashing the drive loop.

The Python trainer requires exact checkpoint contents, explicit integration
intervals, a reset-origin first segment, contiguous ordinals from1 and generation0.
Old/missing provenance, gaps, continuation files and fault resets are rejected.
This validates evidence; it does not reconstruct omitted state. Trace consent
remains opt-in and features remain local. See neural research passdown for caveats.

## Evidence and limits

Build106 device GLES tests: 3 passed, including ten distinct room renders,
transparent stage/scale and pause behavior. Real-phone neural comparison passed:
2,000 timed steps/core, rate p95 68.125 microseconds; spiking p95 241.563
microseconds. Both reproduced exact repeated outputs and remained under the
20ms gate. This is core cost and reproducibility, not learned behavioral quality.
Results saved in validation107/neural-phone-comparison.json.

The initial real-phone gesture run failed because keyguard locked the phone
before interaction (No compose hierarchies); no gesture pass is claimed from
that attempt. Host gesture replay then found and fixed the mode-transition bug.

The ten-minute build105 USB-charging profile had only106/300 samples in the
foreground (206 seconds of usable CPU intervals), so it is not sustained avatar
acceptance. Foreground CPU averaged72.89% of one core; battery32.2→34.3C,
max34.6C; thermal status0→1. Do not advertise improved heat from this mixed-use
sample. A controlled final-build run and unplugged battery measurement remain.
Do not fake unplugged status using dumpsys battery set: that does not stop charging.

## Real anatomy, physics and robot transfer

Separate research audited a pinned BANC v888 public roster containing1,263
candidate anatomical synapses. All review tags were blank; fragments and missing
annotations prevent calling it a reviewed motor controller. No arbitrary weights
were labeled biological and no dataset was embedded in the commercial APK.
The new source/terms findings supersede broad assumptions about historical FAFB
terms; exact manifests and primary sources are in docs/neural.

A separate pinned FlyBody/MuJoCo workspace on Meow ran4,000 real dynamics steps,
all finite, zero warnings, byte-identical repeated trajectories. Its synthetic
wing probe is not controlled flight, a connectome-driven controller, or a policy
quality result. No training job or physical robot command was issued. The
simulation passdown provides paths, hashes, environment lock and rerun command.
Robot transfer remains simulation-only because the existing stop/protocol
quarantine and hardware verification are unresolved. Its source-audited plan is
in ROBOT_TRANSFER_SIMULATION_PLAN.md.

## Teaching Meow / next diagnosis

If touch fails after selecting a style, reproduce a mode transition without
recreating the Activity; restarting the whole screen hides the recognizer bug.
If the fly jumps, test the exact previous floor/holding pose before issuing
Explore. Keep simulation dt capped and use the existing active clock.
If replay diverges, check full checkpoint, first step ordinal, dt and reset
generation before fitting weights. Never assume wall elapsed equals integrated
neural time across thermal suspension. If a phone test finds no Compose tree,
check keyguard/foreground before attributing it to application code.

Controlled phone validation requires an unlocked foreground avatar. Preserve
camera/mic choices, installed signature and data; use in-place adb install -r.
Do not disable thermal controls to pass a performance test. Keep physics research
outside the app/runtime checkout, and keep learned outputs away from robot
transport and privacy/permission controls.

## Final build and installation

Final build107 assembly and instrumentation APK assembly succeeded. Focused JVM
suite:59 tests, zero failures/errors/skips, including the actual Compose gesture
replay for every style plus all Lumen animal forms in both habitat modes. The
replay checks attention, long pet, short stroke, double-tap play, downward menu
swipe and zero sensor-zone dispatch across live mode/style changes. Python
research/trainer suite:13 passed. Earlier host KeyStore harness failure was fixed
with the existing test-only mocked storage pattern; production Keystore stays
unchanged. The substantive recognizer failure was then reproduced and repaired.

In-place USB install succeeded with data preserved. Package manager verifies
code107/name1.3.81, device lastUpdateTime2026-09-13 09:28:55. Default HOME still
resolves to com.example.meowmba/.MainActivity. Exact signed APK SHA256:
6b8b3fbee62399c8ea85ffc67b9eb5188c021289730921be85ab98c01a44414c.
The final phone remains keyguard-locked. Live gesture recheck, final foreground
thermal gate and unplugged battery measurement remain explicitly incomplete.
Controlled biological flight, validated connectome motor mapping, physical
robot transfer and AAA visual parity are also not claimed complete.
