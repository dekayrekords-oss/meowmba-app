# Genuine 3D cartoon fly renderer — pass-down to Meow

## Changed and why

Added `Fly3DRenderer.kt` and `Fly3DGeometry.kt`. The full-body fly is now authored as volumetric geometry: shaded ellipsoid head/thorax/segmented abdomen, projecting red eyes, small dimensional pupils, six articulated limbs, shoes/knuckles, bristled antennae, a small smile/speech opening, and one pair of closed curved wing meshes. Side and back views reveal distinct silhouette and occlusion; this is not a rotated image or flat Canvas billboard. Red/charcoal/green coloring follows the original approved concept without copying any reference image.

GLES20 perspective, depth testing, three-band diffuse cel shading and a black inverted-hull outline supply the flat cartoon finish. Transparent wings draw after opaque surfaces with depth writes disabled; camera-relative left/right ordering reduces wing overlap artifacts. Room/floor/table and prop meshes provide parallax. Kitchen, library, greenhouse, workshop, pond, orchard, cafe, observatory, beach and bedroom each have a distinct animated volumetric scenery motif and palette.

## Host contract

`Fly3DRenderer : GLSurfaceView.Renderer` exposes volatile `frame: Fly3DFrame`, `lastError` and `renderedFrames`. Create a GLES2 context and a depth buffer, set renderer, use `GLSurfaceView.RENDERMODE_WHEN_DIRTY`, publish one immutable frame from the existing active avatar clock, then call `requestRender()`. Pause/resume the view with lifecycle/visibility. The renderer contains no clock, animator, sensor, thread, network work or inference. `active=false` retains the most recent active scene snapshot. It still draws that snapshot if EGL restoration needs a repaint.

The original flat `Fly3DFrame` fields remain: `timeSeconds`, world `x/y/z`, radians `yaw/pitch/roll`, `flight`, `walkPhase`, `gazeX/gazeY`, `blink`, `mouth`, `reach`, `holding`, `backgroundId`, `active`. **blink means closure: 0 open, 1 closed.** `Fly3DWorld` angles are degrees; host converts them. Extra optional `props: List<Fly3DProp>?` and world-space `handX/handY/handZ` connect the simulated room to visible geometry.

The world simulation owns prop base XYZ and angle (degrees); every supplied prop, held or free, draws once at that exact world transform. When a prop snapshot is supplied, local held-prop drawing is disabled. Inverse root transform maps the world hand into the character's local rig, and an articulated elbow connects that hand to the shoulder. Without a prop snapshot, renderer-only previews use static book/glass/ball and a fallback held prop.

Coordinate agreement with the environment worker: table surface y=.8, floor y=0, grounded fly root y≈1.35 and floor root y≈.55. The native mesh scale is .50 and foot bottom is local−1.09 (world−.545, within .005 of the simulation foot constant). Camera is (0,2.5,8) looking toward (0,1.5,0). Perspective width adapts to viewport aspect; camera stays fixed. Main play bounds x±2.5, z±1.5, y .55..4 (floor pickup requires the lower .55 bound).

## Efficiency and context-loss behavior

Meshes allocate immutable vertex arrays once. GL surface creation uploads reusable interleaved vertex/normal VBOs; surface recreation creates fresh GPU names. The draw loop reuses matrices and a bounded stack, uses no texture downloads and avoids per-vertex/per-frame object creation. Low-detail meshes are reused for small anatomy. Every outlined object needs two draw calls; native GPU/frame/thermal profiling remains required, particularly for library/greenhouse backgrounds and transparent wings. This renderer is not evidence of a performance gate passing.

Shader implementation uses GLSL ES 1.00-compatible operations only. A draft used `transpose()` in shader code, which is unavailable in baseline GLES2; it was replaced before handoff by CPU inverse-transpose normals and world-normal extrusion through a view-projection uniform. Shader/link errors populate `lastError` rather than claiming a successful renderer. Future blank-frame diagnosis: inspect `lastError`, require EGL depth bits, check vertex winding/cull mode, verify surface dimensions and a published active frame, then check context recreation.

## Validation and limits

`git diff --check` passed. Worker did not run Gradle, install, deploy, create device sessions, or commit; parent is sole build owner and handles generated Blender artwork. Native screenshots at front/quarter/side/back, animated landing/grasp/drop, orientation/context restoration, depth occlusion and sustained performance remain parent verification work. Graphics geometry is procedural authored cartoon art, not biological connectivity or physical flight reconstruction. The elbow is a visual articulated chain to the supplied hand target; segment lengths may vary for reach and it is not a physical skeleton solver. Wing transparency uses a two-wing approximate depth ordering, not order-independent transparency.
