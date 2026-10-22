# Robot disconnect and erratic motion — September 8, 2026

## Superseded safety behavior — 1.3.55/code59

The user reports spins after the 1.3.54 installation. That build preserved an
unverified zero-speed rotation script inside stopAll. It was not an all-zero or
verified safe stop. The physical cause remains unconfirmed, but the path must
not execute. All physical control is now quarantined in source, including neck,
stop packets and reconnect. See ROBOT_CONTROL_QUARANTINE_2026-09-08.md for the
current scope and acceptance gates. The historical checkpoint below is not an
assurance that the base can safely stop; keep it powered off.

## Installation follow-up — user authorized

Installed the archived 1.3.54/code58 debug APK on the connected SM-F966U1 with
`adb install -r`; Android returned Success and package inspection confirmed
versionName 1.3.54, versionCode 58, lastUpdateTime 2026-09-08 13:26:14.
Archive hash matched the validated build. This was an in-place update: no
uninstall, data clearing or new permissions. A running app process was observed
after installation; no explicit app launch or robot test was performed.
The user was asked to keep the base switched off. Physical stop/movement
verification remains open and wheel control remains disabled. The sections
below describe the earlier pre-install build checkpoint.

## Delivery scope

1.3.54/code58 is a **safety and connection-reliability build**, not a claim of
manufacturer-equivalent motion. No physical actuation, Bluetooth connection,
installation, microphone/camera enable, remote push or Play submission was
performed. Read-only ADB identified the connected phone running 1.3.50/code54;
targeted robot logs did not supply useful motion evidence. The installed build
has not been replaced. Meow's current Git remote timed out on SSH port 22, so
remote currency could not be verified; existing local user changes are preserved.

## Confirmed source defects and changes

1. The ViewModel returned early behind a connect guard before honoring Disable.
   Disable now cancels retained work and disconnects immediately. A captured
   connection epoch fences even work that has not started. Preferences keep
   explicit Disconnect off on subsequent refreshes.
2. RobotManager held its connecting candidate only in a local variable. It now
   owns the pending candidate before blocking. Disconnect retires that candidate;
   a late successful result cannot attach it. Callback ownership, epoch and
   actual link state are checked together. Stale INITIALIZING cannot detach READY.
3. Scan/connect/init callbacks could outlive close. Each BLE instance is now
   terminal after close, including close-before-connect. Late callbacks cannot
   resurrect it. New explicit connections require a new instance. Name discovery
   accepts suffixed/advertised robot names during a bounded active scan.
4. Runtime movement, keepalive and three stop writes competed on GATT. A single
   asynchronous operation lane now serializes them. Busy ordinary writes are
   rejected, never retained for delayed playback. Stop clears queued work, uses
   the adapter's then-assumed zero-speed stop writes and closes after completion or a
   750 ms deadline. Repeated stop/close requests cannot extend that deadline.
5. Stop receipt distinguishes pending, platform-completed and unconfirmed.
   Missing channels, failed writes and timeouts do not become success. These
   are transport observations, **never measured mechanical stopping**. Existing
   zero packets are preserved, not newly certified. Physical stopping remains
   unverified; switch the base off if it continues moving.
6. Sensor reads were discarded. Read callbacks and notifications now both reach
   the existing fail-closed parser. Unknown frames latch motion disabled. Failed
   initialization closes instead of reporting READY. Callback delivery avoids
   the Bluetooth-lock/body-safety-lock inversion.
7. Wheel turns lacked the verification gate used for translation. All nonzero
   BLE wheel entry points are now disabled, including captured encodings and
   raw frame submission; the shared gate also rejects unverified rotation for
   clips, following, mood and explicit tools. Legacy TCP/SPP motor control is
   unavailable instead of bypassing safety with unverified ASCII servo commands.
   A selected address never falls back to another device or protocol.
8. Face loss cannot start a physical search sweep. Non-finite and regressed
   samples reset safely. Follow intent uses monotonic elapsed-time smoothing,
   not a per-frame blend. Missing observations never initiate a neck target.
   Visual idle motion and the prior whole-character Lumen rig remain intact.
9. Robot controls have separate Stop movement and Disconnect buttons, including
   during connection. Connected status uses real state, not substring matching.
   Editing the device address disconnects; typing is not permission to connect.
   The selected address is labeled selected, not falsely labeled in use.

## Reference review and limits

Opened all five supplied videos in Chrome and sampled playback/poses. This was
not exhaustive frame-by-frame timing analysis or a measured motion trace:

- [LOOI / DeskMate comparison](https://www.youtube.com/shorts/woYE9nbGeDA)
- [DeskMate demonstration](https://www.youtube.com/shorts/7ZOKWiNgXLY)
- [LOOI introduction](https://www.youtube.com/watch?v=XQ7tCi3j2IE)
- [A New Kind of Friend](https://www.youtube.com/watch?v=XGjy86PT2hA)
- [Personality demonstration](https://www.youtube.com/watch?v=Gingb1_qpk8)

The viewed poses show gaze/expression, screen tilt, whole-base orientation and
occasional on-screen props working together. Our design target is coordinated
eye-leading attention, held listening postures, eased body follow-through and
brief contextual gestures—not continuously feeding camera jitter to motors.
This is an interpretation of the references, not their internal algorithm.

DeskMate and LOOI must not share an assumed actuator model. KEYi specifies three
rotational axes for [DeskMate](https://keyirobot.com/products/deskmate). That does
not establish independently controllable pitch/yaw/roll on a LOOI tread base.

Firsthand community research conflicts with our historical wire assumptions:
[Andrey's protocol notes](https://github.com/andrey-tut/LOOI-Robot/blob/main/docs/PROTOCOL.md)
report two signed FED0 movement bytes and a two-byte stop, while FE00 remains
experimental and FF02 unverified. Its neck center also differs. The author's
[open questions](https://github.com/andrey-tut/LOOI-Robot/blob/main/docs/OPEN_QUESTIONS.md)
leave firmware dependence unresolved. These findings are not compatibility
proof for the user's base. No replacement packet recipe was installed.

[Telemetry notes](https://github.com/andrey-tut/LOOI-Robot/blob/main/docs/FED9_TELEMETRY.md)
report tested cliff/dock observations but no firmware compatibility matrix.
[LOOInteract](https://github.com/CozmoCyke/LOOInteract) documents another endpoint
family and explicitly unproven physical effects. An accepted Bluetooth packet
is not sufficient evidence of direction, duration, position or safe stopping.

## Remaining physical acceptance gate

- Keep the base powered off/detached until the safety build is installed with
  separate user approval. An app-level disconnect is not a hardware power switch.
- Identify exact base model and firmware; obtain manufacturer documentation or
  a narrowly scoped, user-authorized reference capture for that firmware.
- Establish safe stop, attachment and fresh cliff evidence before admitting any
  wheel movement. Confirm both directions, bounded duration and mounting/camera
  orientation under supervised physical testing with immediate power cutoff.
- Only then implement firmware-specific closed-loop following, acceleration and
  settling limits. Lost face, hazard, touch, background, disabled camera and
  disconnect must each override it. No settings switch may bypass verification.
- Measure physical gaze acquisition/settling, stable listening, target loss,
  no oscillation, no autonomous reconnect and actual stop latency. Until this
  passes, do not advertise identical LOOI/DeskMate behavior.

## Validation

Main owns shared integration and the only Gradle build. `body_safety` owns BLE
lifecycle/serialization and 20 fake-GATT policy tests. `attention_review` owns
KeepInFrame and 20 deterministic tracking tests, plus read-only protocol review.
Main added manager race/fallback tests, verification-gate regression coverage and
Compose Stop/Disconnect tests. No local GPU or Big Pickle execution is claimed.

Final `:app:testDebugUnitTest :app:assembleDebug` passed in 32 seconds:
**1,705 tests across 225 suites; 1,700 passed, five skipped, zero failures/errors.**
Includes 20 BLE lifecycle, 20 tracking, five manager disconnect/fallback/race,
two robot controls UI cases and the shared verification-gate regression.
These are simulated/JVM/Compose checks, not physical calibration or radio proof.
`git diff --check` passed. APK package/version/minSdk/targetSdk verified;
signature matches the prior debug signer. MWDAT enabled; Sherpa, LiteRT and
MediaPipe inference/vision native libraries remain bundled.

- APK SHA-256: `52a5669d1973837b82823c77d7fbeae5e1c5c32517ad9683ab5ec484707435bf`
- Signer SHA-256: `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`
- Archive: `/Users/prog/Meowmba-Releases/robot-safety-1.3.54-code58/`.
- Final read-only device check: SM-F966U1, `com.example.meowmba` 1.3.50/code54.
  **Not installed.** No user data cleared and no radio/robot commands sent.
