# Stage presentation and 3D room polish — handoff to Meow

## What changed and why

`Fly3DFrame` adds `avatarScale=1f` (sanitized .5–2) and `stageMode=false`. Game mode retains the actual room, prop transforms and world-space hand contact. Stage mode clears RGBA to transparent, omits every room/prop draw, centers a larger 3D character, and adds restrained orbit/breath translation from the existing active timestamp. Parent must use a nonopaque TextureView over AppBackground; this worker changes no screen/ViewModel/persistence code.

Stage fitting targets a conservative65% of viewport width at the nominal setting, with separate horizontal/vertical limits and wing/outline/orbit reserve. Actual painted width depends on yaw and wing phase. Larger slider values saturate at the safe-fit cap, especially landscape, rather than clip. The game slider changes only the character; grounded root compensation `.545*(scale−1)*(1−flight)` keeps feet at the same surface. The inverse root matrix still transforms the authoritative world hand into the scaled local skeleton. Physics objects and room never scale with the character.

Paused frames freeze pose/time but accept appearance changes (scale, stage mode, backgroundId). This corrects a cross-layer bug found while the host switched to complete-frame equality: freezing the entire prior frame had discarded legitimate paused appearance changes.

## Room quality

All ten rooms retain distinct3D motifs and receive layered furnishings: kitchen cabinetry/tile/pot rims; library rug/lamp/book details; greenhouse planters/pavers/stems; workshop crates/pegboard/bench; pond water edge/stones/reeds/ripple; orchard fruit crate/tree/ground leaves; cafe chairs/string lights; observatory floor ring/telescope tripod; beach shells/towel/ball; bedroom quilt/nightstand/lamp/curtains. Pond/orchard/beach use an open sky instead of an indoor window. Every detail loop has a fixed small bound. Plants, cloud drift, ripple and lights read the same timestamp; no new ticking source exists.

The material shader retains the cartoon silhouette while softening cel transitions and adding restrained glossy highlights/rim light, world-space wood grain, cool/warm palette tint and light aerial perspective. Four analytic soft contact shadows follow the fly and3props onto their actual floor/table planes, reducing the previous floating appearance. These are bounded projected contact approximations, not ray-traced/general dynamic shadows. Props still have one simulation owner and one draw transform.

`Fly3DGeometry` adds one reusable torus for rims/ripples and increases the large sphere tessellation to28×18; small anatomy retains its lower-detail shared mesh. Mesh/VBO construction remains initialization-only.

## Edge smoothing and transparency

New `Fly3DEdgePass` draws into an RGBA/depth target capped at1.2million pixels (approximately7.2MB color+16-bit depth, excluding driver overhead), then presents one five-tap edge-aware filter. It is a small postprocess antialias approximation, not MSAA or a claim of production FXAA. Larger displays upscale this bounded target. GL context loss recreates shaders/buffers; resize replaces render targets. Incomplete framebuffers fall back to direct drawing.

Transparent stage rendering uses separate alpha blend factors: RGB uses source alpha, but alpha uses ONE/ONE_MINUS_SRC_ALPHA. This keeps the offscreen image premultiplied for TextureView compositing, avoiding dark transparent wing/outline halos. The postpass preserves and smooths alpha. No clock, extra recurring work, thermal override or background worker was added.

## Verification for the parent build owner

Expanded `Fly3DGpuTest` preserves front/side/back and paused-frame checks; adds transparent-stage borders/corners and actual scale change across360×800,800×360 and640×800; confirms paused appearance changes while motion stays frozen; renders all ten rooms and checks distinct opaque output plus fixed draw/triangle ceilings; saves game-scale contact fixtures for visual review. Renderer exposes lastDrawCalls/lastTriangles as diagnostic counts. These are submission counts, not timing/thermal measurements.

Worker ran `git diff --check` only; no Gradle, installation or GPU test execution. Parent must run the actual phone GPU tests, inspect stage over the real static background and room screenshots, then measure frame/thermal impact under the existing gates. No AAA/performance/visual acceptance is claimed before that evidence. If blank output appears, inspect `lastError`, FBO completeness, EGL RGBA/depth format and resize sequencing. If stage edges are dark, first verify TextureView.isOpaque=false and the separate alpha blend equation. If scale seems ignored only when paused, verify the host republishes appearance changes once and the renderer merges those fields into frozen motion.

Owned files: Fly3DRenderer.kt, Fly3DGeometry.kt, new Fly3DEdgePass.kt, Fly3DGpuTest.kt and this pass-down. Parent owns commits and all host integration.

## Actual-screenshot art correction

Reviewed the first real kitchen/stage GPU PNGs. The room geometry ended inside the camera frustum, exposing the clear color above/below the set; the lighting/palette gave little focal contrast, floor grain read as broad stripes, and the conservative stage fit painted onlyabout43% of360px width. The follow-up extends actual3D floor/walls beyond the frustum, tightens game framing, replaces long stray panel lines with bounded teal wainscoting/trim, and adds a deeper recessed sky/hill/cloud window with sill greenery. Warm analytic window spill and softened mullion shadows contrast with cool cabinetry; floor grain is now much subtler and the room shader has a wider light/dark range while the character retains its gentler cel treatment.

Kitchen composition now has teal paneled cabinetry, cream worktop, brass handles, a shelf with jars, a planted pot, warmer copper vessel, and a real tapered pendant shade/rim/light at the top of the tall composition. These remain bounded mesh draws, not a baked background. All rooms benefit from the improved enclosing set, material/light contrast, window details and floor shading. A new24-segment shade mesh is shared/reused.

Stage fitting is recalibrated against the actual rendered silhouette rather than an overly large nominal bound, increasing default size toward65% actual portrait width. Maximum scale still saturates at independent width/height limits with orbit reserve. Parent must rerun the native border/transparency tests after this calibration; previous3-test success covered the earlier smaller stage. These changes were made after inspecting device evidence; no unmeasured quality/performance claim is inferred from authored geometry alone.

Revision102 GPU diagnosis: actual800×360 small/large stage PNGs were identical because the width-derived nominal size already exceeded the height cap at50%. The minimal correction fits the nominal size to BOTH width and height first, then applies the user multiplier and final safe cap. No GPU assertion was weakened. Arithmetic checks confirm strictly increasing50/100/200% sizes for360×800,800×360 and640×800; parent must rerun the existing actual-pixel GPU test in103. Portrait framing stays unchanged.
