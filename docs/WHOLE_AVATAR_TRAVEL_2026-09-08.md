# Whole-character screen travel — September 8, 2026

## Request and evidence

The user reported that the whole avatar was not moving around the screen like
the supplied `/Users/prog/Downloads/20260907_224155.mp4`, despite eye motion.
Reviewed the reference contact sheets again and compared the previous native
1.3.51 preview with new production-rig captures. This is an original Lumen
animation implementation, not a copied device face or new Blender integration.

Two source causes were confirmed:

- Root travel was only `0.12 × face unit` horizontally and `0.075 × unit`
  vertically, fed by a fraction of pupil gaze. A nearly full-width portrait
  character consumed most remaining movement space. The earlier wide-screen
  test only required 35 pixels of extra horizontal travel.
- Mic kill puts interaction into MUTED, but quiet roaming required IDLE.
  Thus camera-off/mic-off use disabled the idle score entirely. Muting input
  should not turn the visible avatar into a statue.

ADB listed no devices during this turn. The last phone version observed in the
previous task was 1.3.50/code54; that is historical, not a fresh device reading.
Neither the new behavior nor temperature has been physically verified here.

## Implemented in 1.3.53/code57

- Separate whole-character posture targets from iris direction. The eyes can
  return to the viewer while the body briefly holds its off-center position.
  A damped root channel follows the shared idle score or observed attention;
  eyes acquire first, body follows, ears/muzzle/tail share the same root.
- Map normalized root position into actual available screen space, capped at
  22% width / 20% height in each direction and constrained by the rotated
  anatomy. Reserve a stable smaller non-driving footprint, not gaze-dependent
  shrinking. Portrait lower controls and the fixed privacy row retain room.
- Quiet IDLE, MUTED and OFFLINE can roam. Actual output speech energy suppresses
  ambient roaming even if mic kill has changed the interaction label to MUTED.
  No camera enable, audio capture, network call or model inference is added.
- Preserve the active pause/thermal clock, deterministic variation, scene
  handoffs, driving suppression and existing full-driving footprint.
- Apply the existing filtered phone roll around the character's root, not the
  whole screen. The old screen-origin rotation could push a roaming eye beyond
  an edge. Painter, projected bounds and touch targets use the same combined
  roll. Scene props also inherit phone roll, preserving their relative contact.
- Keep privacy marks at a stage-owned bottom-start position and scale, outside
  movement, costume changes and hide effects. Other avatar styles are unchanged.

## Validation

Main owns implementation and all builds. `attention_review` performed bounded
read-only review and identified the muted/TTS distinction, outer screen-roll
clipping and prop-rotation follow-through. No Big Pickle/local-GPU work is
claimed for this increment.

New tests measure actual root and opaque silhouette travel, not pupil motion:
portrait/landscape, every animal, quiet muted/offline, response to observed
attention, 30/60/120 Hz, held posture versus gaze, smooth reversal/morph/scene
entry, frozen clock, transformed eye taps, full driving, tilt/drive transitions,
fixed privacy pixels and scene-entry props. The legacy driving render harness
now uses the same root-local phone rotation as production.

The 32-second native preview uses scripted attention and the production rig and
painter. It is not a phone recording. Root-position spans in that capture:

| Phase | Portrait 360×740 X / Y | Landscape 720×360 X / Y |
| --- | --- | --- |
| First 10 seconds, quiet idle | 79.74 / 99.41 px | 244.34 / 26.95 px |
| Scripted tracking and animal morphs | 104.68 / 176.48 px | 278.95 / 55.52 px |

These are geometric preview measurements, not camera latency, frame-rate or
thermal benchmarks. The first focused run exposed an overambitious portrait
travel test for the earlier footprint; the stable fit was corrected without
loosening containment. A later failure was the obsolete full-screen rotation
in the driving test harness, corrected to the new production pivot.

Final `:app:testDebugUnitTest :app:assembleDebug` passed in 33 seconds:
**1,668 tests across 222 suites: 1,663 passed, five skipped, zero failures/errors.**
The 16 added checks cover whole-character motion/rendering; all prior project,
local inference, speech, safety and UI tests were included. `git diff --check`
passed. Package `com.example.meowmba`, 1.3.53/code57, minSdk31/targetSdk36.
APK signature verified; MWDAT enabled; LiteRT, MediaPipe inference/vision and
Sherpa native libraries remain present.

- APK SHA-256: `8a4d8f56cd913b321d78e68e3f1566bbb625b5a6aca808c644882dea187c09d5`
- Signer certificate SHA-256: `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`
- Archive: `/Users/prog/Meowmba-Releases/whole-avatar-1.3.53-code57/`.

## Delivery and remaining acceptance

- Preview frames/MP4/contact sheet and root CSV:
  `app/build/reports/lumen-whole-body/`.
- Separately authorized installation is still required; the phone is currently
  disconnected. Preserve its data and mic-off preference. Do not enable camera
  or actuate a robot to claim attention acceptance.
- On phone: confirm whole-character travel with mic/camera off; then, after
  the owner enables the camera, test slow/fast human movement, face loss,
  portrait/landscape/fold transitions, privacy controls and scene interruption.
  Measure frame timing and heat under matched conditions.
- Bounded `git fetch --all` failed: Meow SSH at 192.168.1.69 timed out. No remote
  push/sync, Play publication, installation, data clearing or robot actuation.
- This scoped change does not complete unrelated master-plan/release gates.
