# Build 113 cartoon toy interactions — Meow passdown

This scoped change adds one original outlined push-toy to each new cartoon
habitat: spring jelly in `cartoon_snack_lab`, a sock puppet in
`cartoon_laundromat`, and a pancake stack in `cartoon_diner`. The room catalog,
background illustrations and GPU room decorations are owned by the integrator
and other assigned agents. This file describes the toy overlay only.

## What changed and why

`CartoonHabitatGags.kt` owns the art, placement, hit target and bounded response.
The toys use reusable vector paths, heavy dark outlines and flat colors so they
remain crisp at phone, Fold and landscape sizes. Their lower-left placement
leaves the central avatar and tabletop clear. The base contains a visible button
mark, and the host exposes a named accessibility action for each toy.

`AvatarRenderer.kt` remembers the selected toy and draws it from its existing
active `clockSec`. A single tap replaces the current 1.8-second squash, spring
and bounce; it never adds a timer, coroutine, network call, particle population
or queued event. Android's disabled animator setting yields still toy artwork.
Normal overlay/background/thermal pause stops the existing clock; paused or
severely throttled input cannot start a new toy response.

An accepted tap calls the existing `onHabitatAction(HabitatAction.PLAY)` once.
`CompanionViewModel.habitatAction` already updates play/care, emits neural TOUCH
and positive affect, and sends PLAY to the existing habitat world. The new toy
therefore participates in the real shared sensory/control path; it does not
invent a separate neural simulation or claim physical attachment to the fly.
The generic pet callback is skipped for that tap to avoid counting it twice.
Double taps, long presses, drag petting and navigation swipes retain their
existing behavior. No camera, microphone or privacy settings are changed.

## Touch ownership and diagnosis

The toy is tried before the old background action, only when neither the
visible avatar nor a movable prop owns that point. Lumen uses its existing
feature geometry and current draw transform. Other Canvas avatars use a
conservative body envelope. Existing Signal/Godot/classic transform bookkeeping
now records their actual habitat scale and destination instead of stage scale;
the old transforms were unused by the previous tap path.

Full-fly has a separate GPU surface. `cartoonProjectHabitatPoint` reproduces its
habitat camera basis/frustum, and `cartoonFlyOwnsPoint` reserves a generous body
and wing envelope, including current scale and grounded root compensation.
Projected prop bounds also retain ownership. These are conservative exclusions,
not GPU depth picking or biological collision geometry. The native toy remains
a screen overlay over the GPU room; it is not a graspable 3D prop. The fly plays
through the existing PLAY/ball behavior when the toy is pressed.

If a toy stops responding, check habitat mode, the selected room ID, pause and
thermal state first. Then inspect whether the avatar or a moved prop currently
covers its hit target. Keep the exclusion camera in sync with
`Fly3DRenderer.configureCamera` if the camera changes. Do not remove ownership
guards merely to make an overlapped toy tappable. If an animation jumps on
resume, confirm it is receiving the host's continuous active clock rather than
wall time. Do not add a separate timer to remedy a paused host.

## Verification handoff

Six pure tests in `CartoonHabitatGagTest` cover foreground ownership, pause,
equal-time replay, reduced motion, bounded retriggering, invalid input,
portrait/landscape/square/small-window targets, and GPU camera projection at
50%, 200% and 300% avatar scale. The painter creates its reusable `Path` lazily
so those clock/input tests remain pure JVM tests. Native illustrated-room
captures can call `CartoonHabitatGag.draw` from the existing CanvasDrawScope.

`git diff --check` passes for the scoped source changes. The integrator owns
Gradle, native render review, final evidence, source commit, private server sync
and package delivery. No Gradle build, device installation, Git commit or
public source upload was run by this worker. Phone behavior and GPU visual
occlusion are not claimed verified by these host tests alone.
