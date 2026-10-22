# Native 3D edge and material refinement — 2026-09-13

## Evidence and scope

Inspected `build-out/habitat/gpu106/room-kitchen.png`, an actual 640×800 native GPU image. The room showed stair-stepped dark lines, a visible checker/noise pattern across plaster, striped flooring, sharp toy-block furniture and a small fly. This pass changes only `Fly3DRenderer.kt`, `Fly3DGeometry.kt`, `Fly3DEdgePass.kt`, and `Fly3DGpuTest.kt`. It does not change world simulation, player/care behavior, permissions, sensors, scheduling, thermal policies, backgrounds or authored Blender assets.

## Changes for Meow

The existing five-tap RGBA presentation pass now gives high-contrast edges stronger bounded reconstruction. Low-resolution surfaces can use up to 1.35× linear supersampling, but the existing **1,200,000 render-pixel maximum** remains. Thus 360×800 and 640×800 receive additional geometric coverage; a full-resolution high-DPI phone already at the cap receives no larger framebuffer. The filter remains one full-screen draw, five texture samples and convex premultiplied-alpha weights. Context-loss recreation and framebuffer fallback remain intact.

Removed two high-frequency plaster sine functions and reduced wood frequency from 59 to 8 world-space cycles. The prior pattern was not an authored texture: it aliased at mobile precision and made flat walls look dirty. Broad quiet wood variation remains. Room ink width drops from .014 to .008 world units, while fly ink remains .014. This gives the actor stronger visual hierarchy and reduces the room's heavy diagram appearance. Shading now uses restrained hemisphere fill, clearer broad toon bands, slightly cleaner daylight tint and stronger character rim light. The small smile uses a wider corner expression range; no snout, mandible or tubular mouth was added.

Reusable furniture cubes now have twelve chamfer faces and eight corner triangles. They remain genuine volumetric meshes and retain flat main surfaces. This catches light on edges instead of adding fake highlights or more scene objects. The cube is **44 triangles vs the former 12**; each cube draw adds 32 triangles, or 64 with its existing inverted-hull outline. Draw-call count is unchanged. CPU mesh arrays are created once; VBOs still upload only on EGL context creation.

The game actor baseline grows **22%**. The existing user scale multiplier remains .5..2, stage fitting is unchanged, and the ground compensation uses the same enlarged scale so feet remain at their prior world ground. The inverse model matrix still maps the authoritative world-space grip into the rendered right hand. World prop locations are untouched. Physical limb reach at extreme scales is still cartoon posing rather than a rigid-body skeleton simulation.

## Budgets and validation

Unchanged: <=1.2 million offscreen pixels, approximately 7.2MB RGBA8+depth16 attachment storage excluding driver overhead, one five-tap postprocess draw, no added animation clock/thread, and existing <500 draws / <500,000 triangles GPU assertions. Smaller viewports do rasterize more pixels (up to 1.8225× below the cap); do not describe this as a measured frame-time reduction. Removed fragment noise offsets some ALU cost, but native thermal/timing comparison is still required. `lastRenderPixels` exposes actual allocation to the GPU test.

Added a real-GPU regression at 360×800 and 1100×1400: verify the allocation cap, real supersampling at small size, fractional silhouette coverage and premultiplied-alpha color bounds. Existing ten-room distinction, opacity, draw/triangle budgets, transparent stage edges, scale responsiveness and pause stability tests remain. `git diff --check` passed. This worker ran no Gradle/build/install and claims no new rendered result. The integrator must run the suite, inspect the newly generated kitchen/edge-coverage images, and compare phone frame/thermal samples before calling this validated.

Future diagnosis: distinguish geometry aliasing from procedural-shader noise before increasing mesh density. For transparent edges inspect associated alpha, not just RGB screenshots. If performance regresses below the pixel cap, reduce the optional supersampling ratio first; preserve the cap and thermal scheduler. Do not compensate by restoring high-frequency noise, adding an independent renderer clock or disabling thermal throttling.
