# Fly 3D integration review for Meow

Reviewed 2026-09-12 against checkout HEAD `5d20093` and the new, untracked `Fly3DGeometry.kt`. Read-only review: no production changes, build, installation or commit by this reviewer. The 3D host and shader had not been added when this review was performed; their behavior is a required follow-up, not a passed audit.

## Minimal bridge

Keep one animation owner. `AvatarMotionHost` is a private function near the end of `CompanionScreen.kt`, not a separate file. It already supplies `AvatarRenderer` with the live `nervousBiasNow`, reflex feed, face gaze, mouth-energy State, interaction/emotion, IMU and pause policy. `AvatarRenderer` assembles authored/expression channels, applies `LumenReflexBridge`, advances `LumenRig`, and advances `PetHabitatWorld` using the same active `tSec`.

The smallest change is an optional immutable frame sink in `AvatarRenderer`, used only for FULL_FLY. Immediately after `lumenRig.step(tSec, gatedLumenInput)` and the existing habitat step, publish a snapshot containing `LumenFrame`, `FullFlyPose`, `tSec`, viewport dimensions, and any immutable prop snapshot needed by 3D. Skip only `fullFlyPainter.draw` when a ready 3D sink is active. Preserve the current privacy-only `lumenPainter.draw(frame.copy(presence=0f), ...)`, shared gesture handling, and app overlay ordering. Keep the vector painter as a truthful fallback if shader/context initialization fails; report the failure rather than calling fallback 3D.

Store the snapshot in a single latest-value atomic/volatile reference. The GL renderer consumes it without querying Compose State, the ViewModel, sensors or the neural core. Call `requestRender()` only for a newly admitted timestamp or an explicit surface/privacy redraw. Do not queue a Runnable per historical pose: a busy GL thread must see the latest pose, not replay stale animation. Surface resize may redraw the same pose but must not advance it.

If the integrator instead replaces `AvatarMotionHost` entirely for FULL_FLY, extract/reuse the existing Lumen input and rig pipeline first. A second simplified loop that reads only emotion and sine waves would lose real neural influence, held gaze, authored performance, reflex modulation and mouth timing. Do not instantiate another NeuralBehaviorController, LumenReflexBridge, recorder, audio sampler or frame scheduler inside GL.

| Existing input/output | 3D mapping |
| --- | --- |
| `LumenFrame.gazeX/gazeY` | Bounded eyeball/pupil rotation in the fly's local head coordinates |
| `leftOpen/rightOpen`, lower lids | Independent lid geometry or bounded eyelid morphs; do not add a second random blink timer |
| `mouthOpen`, `smile`, `concern` | Jaw and soft mouth shape channels; do not create speaking energy from microphone-armed state |
| `earLift`, `earLag`, `earAsymmetry` | Antenna roots and lag, with authored clamped joint angles |
| `headX/headY`, `roll`, `screenRoll`, `squash` | Head/root hierarchy, avoiding applying scene/root translation twice |
| `FullFlyPose.x/y/scale/facing` | Screen-to-world root mapping using the current avatar viewport, including unfolded split view |
| `flight`, `wingPhase`, `walkPhase` | Wing and limb pose phase from the habitat clock, not GL wall time |
| `armReach`, `handX`, `handDrop`, `holding` | IK/grasp attachment under the same hand transform as the held glass/book |
| `HabitatWorldSnapshot.objects` | Optional rendered prop state; draw each object in one renderer and one owner only |

`FullFlyPose` is the actual class name (not FlyPetPose). `LumenRig` already clamps elapsed integration to 50 ms and does not advance twice at one timestamp. Keep those semantics. `nervousBiasNow` now reads the integrated NeuralBehaviorController, whose default is SHADOW: in SHADOW the neural candidate does not drive the published bias. UI and documentation must not claim NEURAL authority while SHADOW is selected.

## Lifecycle and thermal gates

Use the `paused` value supplied to `AvatarMotionHost`, not an independent visibility guess. It combines lifecycle/screen-interactive/thermal permission with the covered-stage policy. An unfolded browser leaves the visible half alive; a full-screen browser, onboarding or covered stage pauses it. A secondary avatar preview must not create a simultaneous active world/GL loop behind the full-screen avatar.

Retain `AvatarFrameCadence` and `AvatarFrameClock`: active target is user-selected 15–60 FPS, idle uses half with its floor, LIGHT halves cadence, MODERATE+ refuses frames and stage policy pauses. Do not use GLSurfaceView's continuous default. Configure `RENDERMODE_WHEN_DIRTY` after setting the renderer. Call its pause/resume counterpart as the surface leaves/enters allowed lifecycle, not only when the Activity is destroyed. Preserve active-time continuity across pauses without integrating wall time spent hidden. [Android GLSurfaceView reference](https://developer.android.com/reference/android/opengl/GLSurfaceView).

The existing `framePresented(now)` callback is a frame-owner signal, not proof that the EGL buffer was displayed. If profiling the GL path, label CPU submission, GL draw and actual presentation separately; don't report Compose Canvas cadence as measured GPU frame time.

## Surface and privacy hazards

GLSurfaceView is a separate SurfaceView surface, not an ordinary Compose Canvas. `setZOrderOnTop(true)` places it above its containing window and can hide that window's privacy badges, settings, captions and approvals. Compose `zIndex` does not repair that. `setZOrderMediaOverlay(true)` remains behind the window and is only a relation to other regular SurfaceViews. Also, whole-View partial alpha was not generally supported before Android 14; this app supports API 31. [Android SurfaceView reference](https://developer.android.com/reference/android/view/SurfaceView).

Keep UI/privacy in the normal window above the surface. Validate transparent EGL configuration, holder format, framebuffer clear alpha and blending on the actual Fold. Transparent GL over an independently animated Compose background is a compositing gate, not something to infer from a desktop render. If the OEM surface ordering cannot preserve all content, use an opaque GL scene with normal UI above it, or a texture-backed integration, rather than moving GL above privacy indicators.

Fixed camera/mic/mute marks must continue to update while decorative motion is frozen, while the fly travels to a corner, and when the model becomes invisible. They must not inherit fly transforms, wing alpha, material visibility, near-plane clipping or depth testing. `LumenPainter` explicitly anchors these marks to stage dimensions even with `presence=0`; preserve that behavior. Only existing user handlers can change permission state. Mouth animation cannot imply the mic is listening.

Keep a Compose pointer overlay using the existing zone/pet/habitat actions and `interactive=false` semantics. An AndroidView that greedily consumes touches can break the Professional workspace's parent mic handler and the existing controls. Account for safe insets and the current half-width viewport in both touch/world coordinates. Pause/hide the surface when fully covered; window visibility alone does not establish that a stage is unobscured.

## Geometry/shader checks before calling it finished

The new `Fly3DGeometry.kt` contains CPU-backed sphere/cube/wing meshes and VBO upload code. No active Filament/gltfio dependency or Filament production renderer was found in `app/build.gradle.kts` or `app/src/main` during this review. The old `docs/AGENT_PLAN.md` framework/checkpoint narrative is historical; its September 5 removal override matches the current absence of those runtime files. No `art/avatars/framework-draft` directory exists in this checkout. Do not resurrect that framework or GLB imports as an unrelated merge.

For the new GLES path: compile/link shaders with actionable error handling, rebuild all program and VBO handles in `onSurfaceCreated` after EGL loss, and upload immutable meshes once per context rather than per draw. The current mesh object retains CPU vertices, which supports this. Add explicit GL deletion on active-context teardown/recreation if resources are replaced within the same context. All GPU calls belong to the GL thread.

Nonuniformly scaled spheres used as limbs need inverse-transpose normal transformation (or an equivalent correct scaled-normal calculation), then renormalization. Otherwise lighting looks wrong when a limb bends/scales. Enable depth testing for opaque parts, establish consistent winding, render translucent wings after opaque geometry with deliberate depth-write/blend choices, and prevent dark alpha fringes. A sphere/cube must remain volumetric at yaw/pitch angles; don't evaluate only a front pose. Bound field values before matrix construction, protect zero viewport/aspect, and provide a visible fallback on shader failure.

## Testable acceptance gates

1. Same frozen frame, repeated GL draws: no change to rig time, wing phase, world object positions, action count or neural step count. Pausing 30 seconds and resuming produces no catch-up leap.
2. Feed the same Lumen/habitat inputs to 2D and 3D frame bridges: gaze, blink and mouth channels agree before rendering; NEURAL/SHADOW switching changes the published bias through the existing controller only.
3. Camera/mic/mute controls remain legible and tappable during travel, wing overlap, model hide, settings/chat/approval display and thermal pause. Show clear screenshots of each.
4. Switch FULL_FLY to another avatar, open/close full-screen preview, fold/unfold, rotate, background/foreground and lock/unlock: no duplicate active surface, GL thread, cadence source or mic owner; resources recover after EGL context recreation.
5. Measure admitted requests and GL draws under cool idle, active play, LIGHT and MODERATE+. At MODERATE+/hidden there is no sustained decorative rendering; surface recreation/explicit status redraw is a bounded exception.
6. Verify hand/held-prop attachment and viewport constraints from multiple 3D angles, including table crossing and a corner flight target. Props are not painted once in 2D and again in 3D.
7. Run the existing phone profile with a stated renderer and selected background. An isolated offscreen render or JVM suite cannot establish on-phone GLSurfaceView composition, frame delivery or thermal improvement.

## Pass-down lesson

The existing app already has an expensive, carefully gated sensor/behavior/acting pipeline. A 3D renderer should consume its final immutable pose. Treat the work as replacing the last drawing stage, not starting another creature engine. Most likely regressions are a surface above privacy UI, duplicate clocks, stale GL work queues, or lost EGL handles after Fold lifecycle changes. Check those before tuning mesh detail or adding effects.
