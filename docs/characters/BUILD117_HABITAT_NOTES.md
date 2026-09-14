# Build 117 habitat expansion — Meow passdown

## What changed

Four new original sci-fi cartoon play habitats join the existing 15 rooms:
Portal arcade (`cartoon_portal_arcade`), Moon garden (`cartoon_moon_garden`),
Alien aquarium (`cartoon_alien_aquarium`) and Meteor garage
(`cartoon_meteor_garage`). All four are selectable for every avatar and persist
through the existing background preference IDs. The picker shows the new rooms
first, explains the left-side play toy, retains all previous backgrounds and
does not change the user's size, dimming or optional-care preferences.

The integrator supplies the four painted PNGs and measures their contact edges.
`SpaceCartoonHabitatPainter` supplies a complete native fallback during decode,
with recessed windows, strong ink contours, edge-framed alien machines and a
quiet center. `PetEnvironmentAtmosphere` supplies distinct materials and two
small edge beacons. Neither component starts a new clock. Existing illustration
decoding remains serialized and pixel-bounded by the established cache.

Each room has a different discoverable play toy in `CartoonHabitatGags`:

- Pocket portal: the machine stays planted while its tiny ringed planet spins.
- Moonflower: a sleeping flower opens its eyes and springs upward when tapped.
- Bubble periscope: a planted pipe blows exactly three outlined bubbles.
- Pocket rocket: a small rocket launches with a bounded flame and settles back.

These use the existing host clock and replace one 1.8-second response per tap.
There is no particle population, timer, coroutine, queued animation or network
work. Reduced motion retains still artwork. Existing avatar and movable-prop
touch ownership takes priority over the room toy; an accepted toy tap still
uses the host's existing PLAY action, care update, neural TOUCH and positive
affect path. This is the same real sensory/control route as the prior rooms,
not another brain or fabricated biological attachment.

## Native 3D matters

Full-fly rooms are opaque GLES geometry. A PNG behind that surface is invisible,
so `Fly3DRenderer` also includes volumetric counterparts for the four rooms:
portal and watching arcade cabinet, swaying moonflowers, bubble tanks with one
helmet fish, and a rocket service lift with tools. The center and table remain
clear. These reuse the six existing meshes and the renderer's paused frame
snapshot, with fixed small loops and no GL texture allocations.

The named play toy remains the same native screen overlay used by all avatars.
Its PLAY event drives the fly's existing simulation-owned ball/play behavior.
The decorative rear machines are scenery, not new graspable physics objects.
The existing glass, book, ball and fruit remain the authoritative inventory.

`PetHabitatWorld` adds crisp contours, tabletop bevel, prop contact shadows,
layered glass rim/water, a book spine/pages/bookmark and cel shading on the ball.
The 3D book gains page edges and a bookmark, and its ball gains an equatorial
band. Prop anchors, interaction radius, table plane, collision geometry and
ownership are unchanged, so visual polish does not create teleporting contacts.

## Files and validation handoff

Production files: `BackgroundPrefs.kt`, `BackgroundScreen.kt`,
`IllustratedEnvironmentBackground.kt`, `PetEnvironmentAtmosphere.kt`,
`PetEnvironmentBackgrounds.kt`, new `SpaceCartoonHabitatPainter.kt`,
`CartoonHabitatGags.kt`, `PetHabitatWorld.kt`, `Fly3DRenderer.kt`.

Tests updated:

- `CartoonHabitatGagTest`: four-room persistence/named-toy contract plus replay,
  paused input, reduced motion, bounded response and foreground ownership.
- `PetEnvironmentBackgroundTest`: all 19 rooms, nine cartoon rooms; native
  distinctness, opacity, periodic animation and stationary contact pixels.
- `IllustratedEnvironmentPlacementTest`: square sources for the four new rooms,
  shared .78 destination plane and cover geometry at extreme aspect ratios.
- `CartoonHabitatUiTest`: seven toy rooms in portrait/landscape; native painted
  pixels must load, every selection remains reachable, and size/dim preferences
  remain intact. Screenshots go to `build/reports/cartoon117`.
- `Fly3DGpuTest`: all seven native cartoon worlds in portrait/landscape; opaque
  coverage, distinct pixels, pause identity and existing draw/triangle budgets.
  This is a physical GPU instrumentation gate, not a claimed host-only result.

The integrator owns Gradle, final native image review, source commit, private
server synchronization, signed package verification and final test counts.
No build, installation, data clearing, commit or public upload ran in this
scoped worker. Record actual verification results in the main Build 117 passdown.

## Debugging lessons for Meow

If a new room looks unchanged only on Full-fly, inspect the renderer's cartoon
room ID set and native scenery branch; loading another bitmap will not solve
an opaque GPU surface. If feet/props do not meet the painted counter after
rotation, measure the PNG's actual contact edge and update only that room's
source anchor. The destination contact plane remains .78; do not move physics
to compensate for an unmeasured illustration.

If a toy is inert, inspect habitat mode, room ID, active clock, thermal/pause
state and avatar/prop ownership at the touch point. Do not remove these guards
to make a covered toy respond. New effects must retain the same bounded
active-time envelope so reopening a menu cannot replay offline motion.

The Mac hit ENOSPC while finishing this work despite `df` showing about 35 MiB
available. Both heredoc creation and apply_patch failed. The unchanged first
target was verified before retrying. The integrator removed only its rebuildable
local Gradle cache to regain write headroom. The worker did not remove releases,
production assets or user data. Builds remain on Meow, not this nearly full Mac.

## Independent 3D review follow-up

Selection wiring was traced from `BackgroundScreen` through `setBackground`,
`habitatBackgroundId`, `AvatarRenderer`'s `Fly3DFrame`, the renderer's cartoon
ID set and all four scene branches. A paused frame still accepts background ID
changes while preserving its previous animation time. The painted assets are
for Canvas backgrounds; Full-fly receives native geometry, not a painted image
misrepresented as a 3D room. Both presentations share the same room names and
play path but are visually different renderings.

The review found an existing depth-occlusion error in the 3D book: its supposed
bottom cover was a full-height solid box, hiding the page block and page-edge
strokes. It is now a thin bottom cover with pages filling the space below the
existing top cover. Outer dimensions, spine, bookmark and physics stay fixed.
When layered prop details seem absent, inspect depth overlap before adding
more detail or disabling depth testing.

Quality limits: the native scenes use outlined reusable primitive meshes,
not the full detail of the painted illustrations. Their rear machines are
decorations; the named native overlay is the interactive button. Native 2D
captures do not validate GLES rasterization, occlusion or sustained device
performance; those remain covered by the device-only GPU checks when hardware
is available. No AAA-quality or physical-phone verification claim follows
from this source review alone.


## Final integration result

All 131 focused checks pass at production source `016ecde`, including 14 drawer
UI tests, 11 Google intent tests and three real-capture cancellation tests. Both
signed packages and preserved assets are verified. See
[MEOW_BUILD_117_PASS_DOWN.md](MEOW_BUILD_117_PASS_DOWN.md) for exact artifacts,
source, diagnostics and physical-phone limits. Build 117 is ready on Meow; it
is not installed on the disconnected phone.
