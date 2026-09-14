# Build 118: face pipeline repair and Meow teach-back

## Confirmed code defects

1. `Viewfinder` disposes its surface through `VisionManager.setPreviewSurface(null)`.
   That method called `unbind()`, which removed every CameraX use case, including
   face analysis. `_faceTrackingEnabled` remained true. The normal resume path
   then called `setFaceTrackingEnabled(true)`, whose idempotent early return left
   the camera unbound. Hiding a preview could therefore stop gaze indefinitely
   until another explicit OFF/ON or camera rebind. This is a code-path defect;
   no particular phone event is assumed without device evidence.
2. Normal face detection requested `LANDMARK_MODE_NONE`, while `mouthOpenOf`
   requires `MOUTH_LEFT`, `MOUTH_RIGHT`, and `MOUTH_BOTTOM`. All mouth-opening
   measurements consequently fell back to zero, disabling the existing
   talking-person selection signal. This does not alone explain every lost
   gaze report, but was a concrete broken input.
3. Face publication waited for `Tasks.whenAllComplete(faceTask, poseTask)`.
   Optional pose warmup could hold an already successful face beyond the
   existing 500 ms observation lifetime. The freshness rejection was correct;
   coupling face publication to the slower optional skeleton was unnecessary.
   Actual pose latency on the user's phone remains unmeasured.
4. Async camera binding and ML Kit failures were logged but the enabled flag
   still looked successful. Detector failures were treated like an empty
   successful frame. The UI had no reliable way to distinguish those failures
   from a camera still starting, a missing face, or cooling.

## Changes and reasoning

Detaching Preview now clears the Preview object, invalidates the previous
session, and requests a replacement analysis-only bind if tracking is still
explicitly authorized. The existing consent and binding generations protect
the replacement. OFF before a queued provider callback cancels it, and MODERATE
thermal status still unbinds without opening a camera. No preview is reopened.
With no requested tracking, preview disposal only unbinds.

Normal detection now requests mouth landmarks while retaining FAST mode,
classification, no contours, and the same resolution/cadence/minimum face size.
`followOnly` retains LANDMARK_MODE_NONE. This restores a consumed signal without
enabling ACCURATE mode or another inference loop.

`completeFaceFirst` publishes a successful nonempty face result as soon as that
reader finishes. If the pose result is already complete it is retained; if no
face was detected the callback waits for pose so hand attention still works.
It publishes at most once and releases the shared ImageProxy only after both
readers finish. Failure also reports promptly, while retaining the image until
the remaining reader completes. The one-frame admission gate and 500 ms
freshness contract are unchanged; a slow face result cannot become fresh by
finishing later. There is no new executor, poller, timer, or parallel inference
admission.

`VisionManager.trackingStatus` is a read-only `StateFlow<FaceTrackingStatus>`.
The enum exposes a short `label` and distinguishes OFF, PERMISSION_REQUIRED,
STARTING, WAITING_FOR_FRAMES, NO_FACE, TRACKING, PAUSED, COOLING and ERROR. State
changes originate in the existing lifecycle, bind, detector and freshness
callbacks. StateFlow suppresses duplicate enum values. TRACKING requires a
selected current face; accepting a tracking request or binding use cases is
not sufficient. Errors retain the detailed exception in existing local logs
and expose a generic retry instruction to the UI.

An explicit repeated `setFaceTrackingEnabled(true)` retries a failed bind only
when the current status is ERROR. It invalidates the old session and queues one
replacement bind, whose permission/consent/thermal gates remain intact. Starting,
healthy and cooling states keep their idempotent behavior. Synchronous provider
initialization failures are also caught and reported as ERROR; turning OFF or
closing the manager keeps OFF even if provider acquisition fails during cleanup.

The public two-argument VisionManager constructor remains. An internal
constructor accepts the camera-provider future source so tests can hold and
release the actual production binding callbacks without opening hardware.
The normal constructor still uses `ProcessCameraProvider.getInstance(context)`.

## Integration

The integrator owns CompanionViewModel, CompanionState and UI collection/display
of `trackingStatus`. Keep saved `isCameraEnabled` separate from actual pipeline
state. Camera OFF must still be persistent, clear pending permission intent,
and call `setCaptureEnabled(false)`. Do not auto-enable to resolve NO_FACE,
WAITING_FOR_FRAMES, ERROR, or a missing permission. The explicit UI Retry can call
the existing enabled/resume path while status is ERROR; a deliberate camera
OFF/ON remains available as well.

## Validation prepared

Ten `VisionManagerLifecycleTest` cases use the real manager with mocked
CameraX hardware boundary and explicit queued provider callbacks:

- Preview disposal binds analysis without Preview and does not require resume.
- OFF cancels an already queued replacement and cannot publish running status.
- Preview disposal with no tracking cannot open another camera session.
- Missing permission is distinct from OFF and cannot bind.
- Permission revoked before the async callback cannot bind.
- MODERATE thermal status preserves cooling/unbind behavior.
- Async provider failure is reported as ERROR rather than tracking.
- Explicit retry after an async failure requests another bind without toggling OFF.
- Synchronous provider initialization failure is observable and can recover on retry.
- Normal detector settings include required landmarks; body-follow remains cheap.

Five `FaceFirstFrameCompletionTest` cases use real Google TaskCompletionSource
tasks to verify early face publication, delayed image release, no duplicates,
pose-only hand input, pose failure, face failure, and the face-only path.

`git diff --check` passes. This worker did not run Gradle, install, access camera
hardware, or claim these prepared tests have passed. The single build owner
must run both new suites plus CameraCaptureGateTest, AttentionFreshnessTest,
FaceTrackGeometryTest, FaceAttentionSmoothingTest and the actual ViewModel
privacy/status regressions, then record results in the Build 118 pass-down.

## Future diagnosis

Start with actual status, saved camera choice, Android permission and thermal
state. If the camera is enabled but WAITING_FOR_FRAMES persists, inspect bound
use cases and lifecycle state; do not equate the requested flag with a running
camera. Reproduce preview show/hide, overlay disposal and Activity resume before
guessing camera hardware trouble. If frames exist but NO_FACE persists, compare
active lens, buffer size, rotation and detection count using existing VMrot logs.
If valid face results are being discarded, compare monotonic observation time,
completion latency and session generation; never extend robot freshness to hide
latency. If mouth activity stays zero, confirm that detector options supply the
landmarks consumed by the mouth calculation.

Physical verification remains required for front/cover lens selection,
portrait/landscape/fold mapping, real ML Kit face and mouth detection, Android
camera-indicator shutdown, and sustained heat. These changes do not verify a
particular phone configuration or remove the thermal pause.

## Integrator test follow-up

The integrator reported that all 15 new pipeline tests and the camera privacy
and status tests passed in the first 247-check run. Ten older VisionPipelineTest
cases failed during unrelated `AgenticRuntime.from` initialization, before
their vision assertions, because their mock Context did not provide preferences.
The fixture now uses the existing ScopedCompanionAndroidMocks for bounded
clock/log mocks and temporary AgenticRuntime isolation. Created ViewModel scopes
are cancelled before restoring that fixture, the idle-animation property is
restored, and IO work uses the same test dispatcher. All original routing,
provider-selection and honest no-camera assertions remain unchanged. This is
a fixture repair, not a production null-handling change. Integrator retest of
the repaired older suite is pending.


## Final integration result

All 247 focused Android checks and four Python helper checks passed at built
source `d1a544b`. Both signed APKs and all 53 runtime/19 painted assets are
verified. Final previews were inspected. Build 118 is archived on Meow and is
not installed on the disconnected phone. Exact evidence, artifact hashes and
hardware limits are in [the final Meow handoff](MEOW_BUILD_118_PASS_DOWN.md).
