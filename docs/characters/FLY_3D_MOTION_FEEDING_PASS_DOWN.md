# 3D fly motion and feeding follow-up — 2026-09-13

Owned changes: `Fly3DWorld.kt`, `Fly3DRenderer.kt`, and `Fly3DWorldTest.kt`. Geometry, asset authoring, shared state, build/install and commit remain with the integrator. This is deterministic cartoon control, not a fruit-fly connectome simulation.

## What changed and why

The actor floor height now matches its native mesh feet: chest .55 world units, feet roughly -.545. Previously the .70 floor chest left it visibly hovering. Changing from a floor destination to an airborne/table destination no longer raises the minimum Y through the actor: the actual floor remains available until the actor has climbed above the table plane. Existing vector velocity caps, 50ms resume cap and <=10ms physics substeps remain.

Neutral grip offset rotates with chest yaw. Exact prop contact still overrides that pose, and the existing 1.1-unit/second complete-vector hand speed cap still applies during reaching/lifting. This prevents cruising turns from dragging the right hand across the torso. When horizontal speed settles below .035, walk phase eases toward the nearest planted-foot phase instead of holding one shoe in mid-air.

FEED now creates one small original orange fruit prop on the table. The fly approaches, makes hand contact, grasps, raises it toward its mouth, and consumes it after at least 2.5 seconds of active holding time and arrival near the presentation target. Same command IDs cannot replenish food; a new explicit FEED replaces any previous fruit serving. Pause/equal timestamps freeze all of this. Needs/persistence remain independent responsibilities of the existing care controller. There is no added timer or inference.

Renderer retains real mesh anatomy, articulated four arms/two legs and original red-eyed charcoal/green silhouette. Separate eyelid closures preserve Lumen winks. Smile/concern alter the small face contour; ear lift/lag drive short bristled antennae. World integrated wing phase replaces time multiplied by changing frequency, avoiding abrupt phase changes during takeoff/landing. Stage uses a fixed-frequency fallback from the existing frame time. Fruit is a bounded four-primitive mesh detail in the existing prop list; no new loop/texture/runtime asset.

## Exact bridge contract for Meow

New optional trailing `Fly3DFrame` fields:

```kotlin
wingPhase: Float? = null
leftBlink: Float? = null
rightBlink: Float? = null
smile: Float = 0f
concern: Float = 0f
antennaLift: Float = 0f
antennaLag: Float = 0f
```

Publish from the same immutable Lumen frame used by AvatarRenderer: `leftBlink=1f-frame.leftOpen`, `rightBlink=1f-frame.rightOpen`, `smile=frame.smile`, `concern=frame.concern`, `antennaLift=frame.earLift`, `antennaLag=frame.earLag`. Supply `wingPhase` from the game world's `pose.wingPhase`; stage may leave it null. Public `blink` remains the fallback for existing callers. No new frame clock. Existing inactive-frame freezing and appearance-only paused updates remain unchanged.

## Verification and future diagnosis

Added meaningful pure regressions for a complete feeding sequence and no duplicate serving, paused feeding, floor contact and bounded continuous takeoff, planted stopped legs, and yaw-relative grip geometry. Existing replay/contact/speed tests remain intact. `git diff --check` passed. Worker did not run Gradle, GPU tests or install; integrator must run all Fly3DWorldTest tests and the native GPU suite, then inspect feeding, wink and flight on the phone.

When diagnosing contact issues, compare world hand XYZ with prop base + .10, inspect per-frame displacement, and ensure only one owner draws held props. Never repair a jump by teleporting the prop to the actor. When diagnosing animation discontinuity, check whether a phase is integrated over bounded active dt or recomputed as time times a changing frequency. Scale extremes still use the established actor transform/hand inverse and need real-device visual review; this change does not introduce physical collision meshes or articulated limb collision constraints.
