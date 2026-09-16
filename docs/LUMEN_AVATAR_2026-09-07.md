# Lumen eyes-first avatar — 1.3.45 / code 49

## Delivery

Implemented in the Android app; not installed, pushed, or published. Select
**Avatar → Lumen**. Existing avatar selection is preserved. Eyes only is the
new form's default; the Avatar page also offers Cat ears, Dog ears, and explicit
performance buttons. Eye/glow colors and light/dark appearance remain editable.

Reference studied: the user's `20260907_224155.mp4` (49.15 seconds, 1280×720).
Reviewed full-video contact frames and a higher-cadence dog-performance sequence.
Lumen is original code-native artwork, not a reproduction of the reference:
rounded-petal glass lenses, restrained colored irises, dark pupils, broad corneal
reflections, diffuse light, optional satin ears. No permanent head, mouth, nose,
whiskers, 3D engine, Blender asset, or model download was added.

## Motion and interaction

- A single pause-aware active clock drives an analytic critically damped rig.
  Gaze leads the head; head movement leads secondary ear motion. Retargeting
  preserves velocity. No per-frame random noise or idle wandering over a
  tracked person.
- Variable-length fixations, centered rests, asymmetric expression, slower
  reopening than closing, occasional double-blinks, subtle breathing. Lids
  occlude a full-size globe; pupils do not squash with blinking.
- Existing authored eye-open, pupil, gaze-up, lid tilt, head tilt, bounce,
  ear-perk/droop, and speech-energy inputs are bounded and blended. Native
  cat-only mouth/whisker channels intentionally have no painted counterpart.
- “Act like a cat” joins the existing deterministic dog route. Performances
  fade ears in and out without changing the saved form. Casual pet mentions
  are not costume commands. The new cat routine includes a slow blink and a
  gentle curiosity/settle sequence.
- Explicit animation playback no longer opens a browser over the avatar.
- Eye tap hit-testing follows live translation, roll, perspective, and costume
  fit. Long-press opens settings; there are no invisible cat-feature targets.
- Density-aware, theme-contrast camera/mic/speech privacy indicators. Camera
  stays opt-in; selecting a style or playing a costume grants no permissions.
- Native Canvas only, reused paths, no bitmap generation/inference per frame.
  Lumen disables the unused legacy blink/ear/saccade loops. Existing lifecycle,
  screen-off, covered-stage, and severe-thermal work gates remain in force.

## Attention fixes shared by the avatars

Vision samples now carry monotonic observation times. A 100 ms watchdog expires
observations after 500 ms, including camera stalls. Disable/rebind/rotation
invalidates old callbacks; delayed detections cannot revive previous sessions.
Stable target selection uses tracking continuity, spatial continuity for unknown
IDs, and a sustained challenger rather than alternating between similarly sized
faces. Brief visual loss grace is explicitly `hasFace=false`.

The ViewModel and renderer no longer add another hold after Vision expires a
sample. Null clears presence, focus, pose and gaze without a throttle. Physical
following receives only fresh actual faces; it stops instead of searching on
stale data. This is visual attention, **not verified identity, speaker recognition,
emotion reading, or eye-contact estimation**. Detector quality/landmark settings
were not expanded, avoiding additional continuous vision cost.

## Robot-base integration and hard limits

Fixed the scheduler's captured-simulator output with a stable adapter. One
connection-bound owner now arbitrates explicit motion, clips, follow, and mood.
Pause, touch, stop, disconnect, cliff and unknown sensor data invalidate old
leases. Old clips cannot move a newly connected base or brake a newer owner.
Neck commands retain ownership and retry across existing output coalescing;
small target jitter no longer restarts the sustain timer indefinitely.

Main-screen visibility, camera privacy, background and thermal gates now apply
to this BLE adapter. Sensor collectors detach with their link. ViewModel cleanup
closes the robot manager; reconnect does not unnecessarily destroy a live link.
Repeated same-hazard stops are deduplicated. Bare/explicit body stop commands are
narrowly matched, so words such as “stopwatch” do not latch the body off.

**Physical acceptance is not complete.** The existing sensor decoder does not
know the notification layout and returns Unknown. Those notifications therefore
stop movement. No notification is not evidence of a safe floor: translation is
disabled until safety decoding is genuinely verified. A deliberate off/on toggle
can reset the stop latch, but new unknown data stops it again. This is not a
bypass or a claim that tabletop travel is safe. Legacy TCP/servo paths were not
redesigned. No actual motors were actuated during this work.

## Validation and artifacts

Full `:app:testDebugUnitTest` and `:app:assembleDebug` passed: **1,352 tests,
1,347 passed, five skipped, zero failures/errors**. Added 62 tests including
deterministic timing, 30/60/90/120 Hz convergence, held gaze, privacy clears,
authored closed lids, animal routing, costume lifecycle, actual UI selection,
stale camera/session rejection, competing targets, and fake-output body safety.

Native Robolectric render checks cover portrait (360×800), square (360×360),
cover-like (720×520), and wide (1600×500) canvases, at extreme rolls with ears.
Opaque silhouettes must not intersect the canvas boundary; low-alpha glow may
fade through it. Reviewed contact renders and iterated cropped ears, mirroring,
silhouette margins, eye reflections, privacy and hit targets.

An initial longer full run exposed an unrelated latent voice-watchdog lifetime
problem: model-only ViewModels started a microphone watchdog without a speech
engine and later accessed a torn-down test Main dispatcher. The watchdog now
starts only when there is an engine to recover. Final full suite passed.

The motion preview uses the **production rig and painter with scripted inputs**,
not recorded live tracking or a device FPS benchmark. It demonstrates visual
timing, not the quality of a camera or physical servo on a particular phone.

Archive directory:
`/Users/prog/Meowmba-Releases/lumen-1.3.45-code49/`

- `meowmba-1.3.45-code49-preview.apk` — package `com.example.meowmba`,
  version 1.3.45/code49; MWDAT enabled by existing build configuration.
- APK SHA-256: `95758a787de2f0b3153538d98ab2b69690043845b8030f2c577df9ecb66fe897`.
- Debug certificate SHA-256:
  `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.
- `full-test-results/` preserves the complete JUnit XML before optional rendering.
- `lumen-motion.mp4` and `lumen-expressions.png` are the generated visual preview.

The only configured remote is `origin` on Meow. Bounded fetch failed because
192.168.1.69:22 timed out. No remote source was merged over the existing dirty
worktree. Previous app fixes remain in this cumulative preview.

## Worker record and remaining acceptance

Main agent owned native artwork/motion, state/UI/ViewModel integration, visual
iteration, all Gradle builds and packaging. Two bounded core workers handled
attention and BLE safety plus independent integration reviews. This computer's
OpenCode `opencode/big-pickle` passed an isolated no-tools smoke request and
provided bounded timing/test suggestions; no paid fallback or repository access
was given. That does not establish local-GPU inference.

Before release acceptance, separately authorize installation and test:

1. Real camera: move slowly, enter/leave frame, two people, low light, camera
   off, permission denial, phone rotation and cover-screen transitions.
2. Live voice: dog/cat/happy/sad/angry performances while a reply is playing;
   interruptions, ear return, no browser covering the result.
3. Smoothness and thermals on the target phone at 60/120 Hz for a sustained
   session; profile GPU frame times and camera/CPU load, not desktop render time.
4. Robot: document and verify sensor decoding first, then controlled supervised
   non-travel checks, stop/pause/disconnect/touch and neck following. Do not
   enable travel based solely on this preview or on silence from the sensors.

Installation, data clearing, Play upload and physical-base acceptance are not
claimed complete by this document.
