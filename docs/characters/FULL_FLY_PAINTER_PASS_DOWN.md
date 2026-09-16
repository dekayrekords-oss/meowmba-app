# Full fly painter handoff to Meow

Added an original native Canvas full-body fly option. No reference image was copied into the product. Dark shaded shell, large coral-red faceted eyes, striped green abdomen, four expressive arms and two humanoid legs, one translucent wing pair, bristled short antennae, and a single smile/talking mouth establish a friendly fly rather than an ant.

## Integration contract

`FullFlyPainter.draw(scope, frame, pose)` consumes the existing `LumenFrame`; it owns no clock, worker, sensor, inference loop or pet state. `FullFlyPose` takes screen fractions x/y, relative scale, horizontal facing sign, flight/reach weights 0–1, walking/wing phases in radians, and optional `holding="book"` or `"glass"`. Feed phases from the renderer's pause-aware clock and feed locomotion from habitat state. The painter draws held props at its front right hand so geometry remains aligned while mirroring. The base unit is 32% of the shorter canvas edge; the whole fly is approximately 42% of that edge tall. Visibility follows zero presence; partial presence currently does not fade.

Center targets are constrained to a conservative wing/prop margin. Relative scale is capped to prevent coerceIn receiving reversed bounds on narrow viewports. This was found by inspecting large-scale corner cases before handing off. For future clipping, test maximum wing rotation at both phase extrema and all held objects, not only the idle body rectangle.

`FullFlyRenderTest` adds deterministic pixel replay, visual changes for movement/flight/gaze/blink/mouth/antennae/props, portrait/landscape corner bounds, and a native eight-pose sheet at `app/build/reports/full-fly/full-fly-character-sheet.png`. Integrator is sole Gradle/build owner; these tests have been authored but not run by this worker. No device actions or deployment performed. No biological connectivity or fly flight simulation is claimed: these limbs are a bounded cartoon rig driven by the host application.

Owned files: `FullFlyPainter.kt`, `FullFlyRenderTest.kt`, and this pass-down. Parent integrator commits and performs visual review after native tests.

Alignment follow-up: FullFlyPose additionally accepts `handDrop` (0–.56) and `handX` (.25–.85, default .39) for approach/reach animations. The single right front hand and held prop use these same anchors. The visual center clamp is now 1.05 units horizontally, .88 above and .73 below; this allows feet to reach the floor while reserving wing room. HabitatWorld uses matching bounds and a foot bottom of .689 units.
