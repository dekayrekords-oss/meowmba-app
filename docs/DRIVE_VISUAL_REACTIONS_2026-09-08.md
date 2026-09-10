# Drive Mode visual reactions — 1.3.47 / code 51

Implemented locally in the existing Android app. Not installed, pushed, signed
for production, or published. No driving, emergency, or physical-base test run.

## Behavior

- Hard braking: forward/downward lurch and a small depth increase. Lumen eyes
  widen briefly, pupils become more alert, and ears brace with follow-through.
- Turns: inertial lean opposite the vehicle's acceleration; direction reversals
  preserve spring velocity rather than snapping from one pose to another.
- Acceleration: a restrained backward/depth cue; no speed celebration added.
- Quiet cruise: the normal attentive face returns. Small road vibration has a
  deadband; critical damping avoids repeated bouncing or twitching.
- Shared force layer feeds Classic, Godot, Pearl, particle orb, Lumen and Lumen
  Cat. Lumen receives stronger head/depth/bracing channels and 12% eased-in
  driving headroom to keep ears inside small stages. Color/theme choices remain.
- Reactions do not initiate speech, open UI, change camera/mic preferences,
  create AI requests, or send robot-base commands. Random idle tricks and shake
  celebrations are suppressed while driving. Existing crash decision thresholds,
  decision bodies and emergency workflow are unchanged.

## Input and lifecycle corrections

- Manual Drive Mode immediately enables/persists the master switch and updates
  the avatar/HUD; it no longer waits for an automatic GPS sustain interval.
- Manual off immediately clears driving and transient motion, leaving the master
  automatic detector armed. Master off clears manual/drive state and stops that
  detector. Retained crash history/debounce is not reset by toggling modes.
- Detector's low-rate linear sensor also registers gravity (or raw gravity
  fallback), so dashboard braking on device Z is no longer lost. Raw fallback
  subtracts gravity on all three axes. Maneuver signatures detect left/right and
  turn/brake transitions even if both exceed a threshold.
- Avatar visuals use the existing 20 Hz MotionSense path and existing active
  renderer clock, not the lower-rate HUD stream or a new animation timer.
- Sensor timestamps reject invalid, out-of-order, old and previous-session
  callbacks. Stalled fused gravity falls back to a raw gravity estimate.
- Force and whole-stage tilt decay to neutral after a 700 ms sensor timeout,
  even without another sensor emission. HUD labels have their own finite expiry.
- Background paths unregister the visual sensors and clear forces. Resume
  starts a clean baseline. Car detection remains separate for its existing role.
- Display rotation is removed before visual tilt. Android sensor coordinates
  remain tied to device-default orientation, and event timestamps share the
  elapsed-realtime time base. See [SensorEvent](https://developer.android.com/reference/android/hardware/SensorEvent)
  and [sensor coordinate guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview).

Direction assumes a mounted phone whose back faces toward the windshield.
Arbitrary handheld rotation and accelerometer-only gravity estimates are
approximate; this is expressive animation, not a calibrated vehicle instrument.

## Validation and ownership

- Main owned sensor/visual rig, renderer, ViewModel/UI, lifecycle, integration,
  native previews, regression build and evidence.
- `body_safety` owned DrivingDetector, DrivingSensorInput and 19 detector tests.
- `attention_review` independently reviewed direction, freshness, lifecycle,
  privacy, draw invalidation and short-stage containment. No remaining
  production blocker found in the final source review.
- Review/test findings fixed: stale stage roll, stalled gravity, portrait ear
  clipping at combined turn/brake extremes, and unnecessary Android clock access
  while driving is inactive (preserving the plain-JVM ViewModel test path).

Full suite: **1,429 tests; 1,424 passed; five skipped; zero failures/errors**.
Forty new tests cover detector activation/input, motion response and expiry,
MotionSense lifecycle, ViewModel integration and native Lumen containment.
Native bounds checks include extreme gaze, both turn directions, braking and
acceleration, eyes/CAT/DOG, outer ±14° rotation, portrait/cover/square/wide stages,
and retained privacy marks. Existing avatar/performance/privacy tests also pass.

Build command:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --max-workers=4 -PprebuiltDebugNativeDir=/tmp/meow-issue-native.QdqMRB
```

APK: `com.example.meowmba`, 1.3.47/code51; minSdk 31, targetSdk 36.
Debug signature verified; certificate SHA-256:
`75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.
APK SHA-256:
`cba5df5cfed6f2ee1631d6033a6c3198a70e03b351ba5f58ec79cfaafbbc4d7f`.

Preview: production DriveVisualRig/LumenRig/LumenPainter fed **simulated inputs**,
330 frames, 960×620, 30 fps, 11 seconds. Not live-camera or physical-phone footage,
and not a device frame-rate or thermal measurement. Reproduce via
`MEOW_DRIVE_RENDER_FRAMES=1` with `DriveVisualRenderTest`, then encode its PNGs.
Video SHA-256: `bcf4026c68e820116b1d39808ac1a41f424c91f433015938ee55616e5fdd9b92`.

Archive: `/Users/prog/Meowmba-Releases/drive-reactions-1.3.47-code51/`.
Includes APK, native preview/captures and full-suite XML reports.

Meow fetch was attempted with bounded noninteractive SSH and timed out at
192.168.1.69:22. Remote currency is unverified. Physical-phone acceptance remains
open: authorize the matching debug-package upgrade, then verify safely while
stationary using controlled motion/simulation. Do not ask a driver to operate
the phone or manufacture hard braking/turns for testing. Base actuation remains
outside this visual-only change; the paused Blender model remains excluded.
