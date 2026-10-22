# Thermal optimization — 1.3.37 / code 41

Request: optimize the Flip 6 without reducing capabilities or visual/voice
quality. This is a repair pass, **not full thermal acceptance sign-off**.

## Observations

- Physical SM-F741U, serial R5CY3358HDY, preview com.example.meowmba.
- Existing 1.3.36 session: battery 42.2°C, app about 230% CPU (100% = one
  core). Thread snapshot: RenderThread 103%, UI 82.1%; speech workers also busy.
- Graphics history: 41,529 frames, 68.73% janky, median 44 ms; GPU median 5 ms.
  These are historical session totals, not a controlled idle benchmark.
- Logs showed `(buzzing)` entering conversation, and streaming fragments such
  as `And` repeatedly invoking Whisper. While locked, AudioRecord also supplied
  exact digital zero; the old pipeline still decoded that into fragments.
- Meow is the selected launcher. Open Settings before force-stopping preview;
  stopping it while it is home causes Android to relaunch it.
- The phone locked during the attempted controlled visible comparison. These
  samples had zero graphics frames and do not establish a rendering speedup.
- In the locked/digitally-muted case, the final gate reduced post-startup CPU
  samples from about 130–210% to 8–12% (100% = one core). RSS settled around
  380 MB without loading Whisper on silence. This is a short background/muted
  comparison, not an all-day battery estimate or a conversational benchmark.
- The phone cooled to 36.3°C with preview stopped between tests. That is
  cooldown, not evidence that active-use overheating is solved.

## Changes

1. Reuse analytic radial shaders for stars and cat glows. Position, scale and
   opacity use transforms. No raster downsampling, particle reduction, palette
   redesign or removal of blur/depth layers. Cache bound: 16 colors.
2. Weather/background layers share one display-rate clock per scene, read only
   in drawing. Pause when hidden, screen off, or under the existing Android
   severe-thermal policy; retain phase on resume. Fullscreen settings/onboarding
   pause backgrounds as well as the avatar. Visible partial chat still animates.
3. Fixed primitive PCM ring instead of boxed samples and shifting lists,
   retaining the newest 20 seconds. Reuse conversion buffers. Correct 8-kHz SCO
   snapshots to the advertised 16-kHz Whisper timebase.
4. One ordered bounded final-transcription worker per listening session,
   replacing an untracked coroutine per endpoint. Stop cancels queued audio
   and blocks late results. Overflow reports recognizer busy, not success.
5. Serialize Whisper initialization/decoding. Retain a successfully constructed
   recognizer even if its requester is cancelled at the return boundary.
6. Serialize capture restart/release. The old loop checked its parent scope's
   lifetime; a restart could reset the shared running flag while that cancelled
   loop was returning from native code. Check the actual capture job, await old
   cleanup, and prevent it from clearing the new session's state. Defer native
   destruction until capture/pre-roll exits its mutex.
7. Reject explicit noise-only annotations and empty/junk refined results
   without resurrecting a hallucinated partial. Refinement failure still falls
   back to usable streaming text. Requests about music/buzzing are retained.
8. Skip information-free digital-zero PCM before a phrase and before Whisper.
   **No energy threshold**: even a sample of +/-1 opens full decoding. Preserve
   trailing zeros after real audio so endpointing still finishes a sentence.
   This is not a general acoustic VAD and will not filter every real-world noise.

No model, thread count, visible animation rate, privacy, cloud-consent, approval,
tool or local-execution setting was downgraded. No data was cleared. The Play
package had already been removed at the user's request; older plan text about
preserving it does not authorize reinstalling it.

## Verification and artifacts

- Full JVM: 1,215 tests, zero failures/errors, five conditional SDK skips;
  1,210 passed. Three physical checks passed again in 6.072 seconds. Actual
  command output is archived with the release evidence.
- Physical checks: rapid capture stop/restart; installed Whisper native decode
  using a nonzero synthetic tone; Android bitmap comparison of original/cached
  glow across 27 color/radius/opacity combinations. These do not claim actual
  human-spoken recognition acceptance.
- Pixel tolerance: mean channel difference below 0.5/255 and max at most 8/255.
  This tests glow fidelity, not full-app GPU/frame performance.
- JVM regressions cover digital zero vs very quiet PCM, preserved endpoint
  tails, noise annotations, ring order/overflow/SCO, ordered refinement, bounded
  backlog, cancellation across non-interruptible work and scene phase.
- Debug native bridges reused unchanged with prebuiltDebugNativeDir;
  MWDAT-enabled build. No Play upload or native rebuild.
- Evidence on build Mac: `/Users/prog/Meowmba-Releases/thermal-1.3.37-qa/`.
- APK: `/Users/prog/Meowmba-Releases/meowmba-1.3.37-code41-thermal-preview.apk`.

## Remaining acceptance

1. Unlock Flip and run matched visible-avatar baselines: same display, selected
   avatar/background, brightness, charging, microphone and model. Exclude
   startup/model loading; record thread CPU, gfxinfo, temperature and state.
2. Compare a 10-minute conversational session, quiet idle and lock/unlock. Keep
   thermal safeguards; do not heat an already-hot phone for a benchmark.
3. Human-spoken quiet/normal speech, interruptions, restart, Bluetooth SCO and
   prolonged ambient noise. Synthetic tests do not establish microphone
   audibility or conversational accuracy.
4. If acoustic noise still repeatedly invokes costly refinement, profile the
   existing VAD/native inference options and validate a conservative gate
   against real quiet speech before enabling it. Do not hide heat by switching
   users to cloud, reducing model quality or disabling enabled features.
