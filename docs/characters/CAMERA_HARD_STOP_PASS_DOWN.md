# Meow camera hard-stop pass-down — September 12, 2026

## Change and contract

`VisionManager.setCaptureEnabled(Boolean)` is the app's explicit camera gate and defaults to OFF. Android CAMERA permission alone does not authorize a camera session. Enabling the gate does not open a camera by itself: callers then request tracking, preview, or a capture. The integrator owns saved-preference synchronization, the home video button, and pending Android permission intent.

Explicit OFF invalidates consent for pending captures, stops tracking/freshness watchdog, clears analyzer and detector objects, disconnects/nulls the preview provider, stops orientation/display tracking, clears attention/capture state, and asynchronously unbinds CameraX use cases. The analysis executor is retained idle for future explicit enable; full manager close shuts it down. No capture timer or thread was added.

Temporary `setFaceTrackingEnabled(false)` retains its previous analysis-pause semantics for a preview. It cannot restart anything while the explicit capture gate is OFF. Reopening an overlay or resuming the Activity must use the stored enabled state; neither grants consent.

## Races fixed

The old async preview bind only checked `closed` before queuing a provider callback. The callback could later unbind and bind after a camera OFF action. `CameraCaptureGate` now tracks separate consent and binding generations. Provider callbacks check the latest binding generation, explicit gate, closed state and permission while holding the manager lock immediately before binding. A stale queued unbind is skipped if a newer binding request owns the manager; the new bind performs its own required unbind.

One-shot capture preserves its original consent token throughout provider waiting, camera binding, capture submission, returned image handling, JPEG processing and retry. Switching OFF invalidates that token; a later ON does not revive the old operation. Binding after provider waiting is dispatched to Main and rechecks permission/thermal state. Capture errors report a switched-off/cancelled state rather than silently reopening the camera. Closing the manager also revokes consent.

The helper is pure Kotlin and performs no camera operations. Its tests cover default OFF, OFF→ON rejection of old captures/binds, replacement-bind invalidation, valid retry across temporary unbind, and idempotent preference sync.

## Required integration

- Sync saved `KEY_CAMERA_ENABLED` to the new manager before a permitted resume.
- Home video button must call the camera-enabled toggle, not the old preview visibility toggle (which reopened its own preview).
- Explicit OFF should update/persist UI state immediately, cancel any pending enable permission request, clear stale display/capture references as appropriate, then call `setCaptureEnabled(false)`.
- Existing authorized `lookAround`, `answerSeeMe`, and content-studio camera requests must call `setCaptureEnabled(true)` through the explicit camera-enable path before capture. Inspect VM capture call sites near 7734, 8472 and 10269 in the source audited for this change. Browser screenshots use WebViewBridge and are unaffected.
- Do not re-enable for a late Android permission result whose initiating request was cancelled by OFF.

## Verification and limitations

Changed `VisionManager.kt`, added `CameraCaptureGate.kt` and five `CameraCaptureGateTest` cases. `git diff --check` passed. No Gradle/build/device test or installation was run by this worker; the single build owner must run those tests and physical ON/OFF→background/reopen/fold checks.

The pure tests prove token-policy behavior, not CameraX hardware shutdown. Verify Android's camera indicator and actual bound/use-case state on the phone after explicit OFF, including while a provider bind/capture is pending. Also verify authorized one-shot capture still succeeds after a fresh explicit enable and that the OFF preference survives process restart.

Lesson for Meow: a hidden preview is not a disabled camera. UI preference, camera resource gate, pending asynchronous operations and returned image handling all need the same explicit privacy boundary. Separate temporary analysis suspension from a user's persistent OFF choice.
