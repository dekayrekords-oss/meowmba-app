# Robot control quarantine — 1.3.55/code59

## Installation follow-up — user authorized

Installed the archived, SHA-256-matched 1.3.55/code59 APK in place on SM-F966U1
with `adb install -r`. Android returned Success; package inspection confirms
versionName 1.3.55, versionCode 59, lastUpdateTime 2026-09-08 13:48:42.
No uninstall, data clearing, new permissions, explicit app launch or robot test.
The immediate post-install PID check found no running app process. Physical
base control remains disabled and the user was told to keep the base off.
The sections below retain the pre-install investigation/build checkpoint.

## Report and scope

After installation of 1.3.54/code58, the user reports circular motion when the
tracked face disappears and when the phone is removed. Treat this as a physical
safety fault. Keep the base powered off. Disconnecting Bluetooth is not a proven
motor stop, and this patch cannot stop a script already running in firmware.

Read-only device checks confirm SM-F966U1, com.example.meowmba 1.3.54/code58,
last update September 8 at 13:26:14. Only this app had a PID among it, the old
Memba package and the manufacturer's app. Targeted logs contained no command
evidence. Historical Bluetooth entries predate that installation and cannot
establish which command caused the reported spin. No physical tests, actuator
commands, scan/connect, microphone/camera changes or installation this turn.

## Confirmed defect; physical cause not yet confirmed

The previous patch blocked nonzero wheel commands but preserved a stop routine
that submitted FE00 rotateFrame(0, 1), then one byte to each of FF02 and FED0.
The first packet is not all zero: rotation opcode, masks and script parameters
remain nonzero. Neither it nor the other two packets is a verified stop for
this firmware. Calling a command with a zero speed is not a safety guarantee.

An accepted neck-follow operation could mark following active even while wheel
control was blocked. Face loss then invoked BodySafety.stopFollowing → Brake →
LooiBleLink.stopAll, reaching those packets. Pause, touch, unknown telemetry,
lease completion, explicit stop and disconnect also reached that routine.
Zero-speed public frame/turn/drive entry points bypassed the prior wheel gate.

This is a strong source-backed suspect, NOT hardware proof. Phone removal has
no verified decoded event: all FED9 telemetry currently becomes Unknown. We
cannot claim the app correctly identifies physical attachment/removal. The
firmware itself may also react to removal. Community packet research cited in
ROBOT_DISCONNECT_SAFETY_2026-09-08.md conflicts with the historical templates;
no guessed replacement packet or new protocol interpretation was deployed.

The prior tests exercised queue ordering with fake operations, not actual stop
packet safety. Preserving the unverified stop writes in 1.3.54 was a missed
safety path. This increment explicitly supersedes that implementation.

## Containment implemented

- RobotBaseControlPolicy disables physical control without a setting/model
  override. Link connect methods return false before Bluetooth service lookup.
- Every real adapter actuator API rejects, including zero commands, raw frames,
  neck and headlights. No writeCharacteristic call remains in LooiBleLink.
- stopAll closes/revokes without actuator writes and returns false/unconfirmed.
  revokeWithoutWrites terminally discards pending and in-flight bookkeeping,
  even when an old queue is already STOPPING or CLOSING. Late callbacks cannot
  drain old work or restart connection setup.
- Startup revokes a previously saved robot_body_enabled flag. Connect, refresh,
  scan and explicit enable cannot reconnect. Discovery is also gated in manager.
  No unrelated keys, models, camera or microphone preferences are changed.
- Robot controls show Unavailable, disabled connect/scan/toggles, and actionable
  Disable control/Disconnect. Copy makes no mechanical-stop claim. On-screen
  avatar animation and attention remain available.
- Historical protocol comments no longer describe guessed values as verified
  stop or direction behavior. Templates are retained as offline data only.

## Validation and delivery

Main owns production/UI integration and the only Gradle build. body_safety owns
only the new real-adapter LooiBleQuarantineTest: synthetic READY writable GATT,
17 zero/nonzero entry cases, three encoding settings, face loss, four sensor
callback forms, interruptions, lease completion, scheduler stop, queued stops
and late callbacks. These use the REAL adapter, not mocked command methods.
Main adds startup/saved-preference tests and disabled-control Compose checks.
No test uses an actual Bluetooth radio. No local GPU execution is claimed.

Final `:app:testDebugUnitTest :app:assembleDebug` passed in 28 seconds:
**1,719 tests across 227 suites; 1,714 passed, five skipped, zero failures/errors.**
All 12 real-adapter cases, three control UI tests and saved-preference ViewModel
test passed. The initial run caught two new test-harness mistakes (an unmerged
text-node assertion and a Kotlin/Mockito null matcher); both were corrected
before the successful full rerun. These are software checks, not hardware proof.

`git diff --check` passed. APK metadata is com.example.meowmba 1.3.55/code59,
minSdk31/targetSdk36; signature verified and matches the previous debug signer.
Sherpa, LiteRT and MediaPipe inference/vision native libraries remain bundled;
build reports MWDAT enabled.

- APK SHA-256: `20b2dfa0f84f523a9cc5f6aeed47319abc50df6338fab6c3b6917e677626f720`
- Signer SHA-256: `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`
- Archive: `/Users/prog/Meowmba-Releases/robot-quarantine-1.3.55-code59/`.

**Not installed.** New installation requires separate user authorization,
with the base powered off. The bounded
pre-build fetch to Meow at 192.168.1.69:22 timed out; remote currency could not
be verified. Local uncommitted work is preserved. No push or Play submission.

## Remaining acceptance gate — physical functionality is NOT finished

1. Obtain the exact base model/firmware and manufacturer-supported stop,
   attachment and hazard protocol, or a separately authorized reference capture.
2. Validate a firmware-specific stop/watchdog and fail-safe hardware procedure
   before enabling any actuator, including neck movement. Never discover motor
   byte meanings by unsupervised trial and error.
3. With separate consent and safe supervised conditions, verify physical stop
   latency on face loss, phone removal, stale telemetry, app pause and radio loss.
4. Only then restore bounded, calibrated, eased tracking and verify no searching
   spin or reconnect. Successful JVM tests do not satisfy this physical gate.
