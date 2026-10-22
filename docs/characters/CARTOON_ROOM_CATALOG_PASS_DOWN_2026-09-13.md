# Cartoon room catalog — scoped Meow passdown

September 13, 2026. This is the catalog/fallback portion of the new cartoon
background work. The integrator owns final illustrated assets, interactive
scenery, assembly, verification, Git commit and private Meow synchronization.

## What changed and why

- `core/background/BackgroundPrefs.kt` appends three stable animation IDs:
  `cartoon_snack_lab` (“Cartoon snack lab”), `cartoon_laundromat` (“Cosmic
  laundry”) and `cartoon_diner` (“Oddball diner”). All are pet environments,
  so they inherit shared tabletop/prop interaction and avatar support.
  Existing IDs, enum order and stored preferences are preserved. The computed
  `isCartoonEnvironment` flag also includes the previous nook and galley.
- `ui/companion/BackgroundScreen.kt` places the three new rooms first under
  “Cartoon worlds,” followed by the previous two cartoon rooms. All ten
  original habitats remain visible under “More places to play.” Selecting a
  room still uses the existing `onHabitatMode` and `onSelect` callbacks; there
  is no new persistence path or automatic change to the current scene.
- `PetEnvironmentBackgrounds.kt` provides original vector loading fallbacks
  with dark outlines and sparse cel colors: a crowned banana laboratory jar,
  washer portals with a sock trophy, and a diner with UFO lamps, pancakes and
  a boot flowerpot. Decorative furniture stays at the edges and above the
  shared `.78` tabletop. It does not paint or take ownership of movable props.
- `PetEnvironmentAtmosphere.kt` adds matching mint/apricot, lavender/mint and
  butter/coral materials, plus at most two tiny ambient accents per scene.
  These reuse the existing clock and slow phase. They require no timer,
  random state, network access, extra bitmap or independent animation loop.
  Illustrated scenes receive only these sparse glints/bubbles; their props
  are not duplicated by ambient drawing.
- `PetEnvironmentBackgroundTest.kt` updates the catalog count to fifteen
  environments/five cartoon rooms and makes the generated contact sheet tall
  enough to show every room. Existing checks still exercise deterministic
  phases, animated scenery, opaque aspect ratios, resize recovery and a
  stationary contact plane.

## Meow diagnosis lessons

The existing fallback test still expected ten rooms despite the two previously
shipped cartoon entries, and its fixed two-row contact sheet silently clipped
them. When adding a room, check catalog expectations and image-review dimensions,
not only the renderer branch. The dynamic sheet now grows by complete rows.

Keep decorative motion above `PET_ENVIRONMENT_TABLE_Y`; moving that plane makes
the avatar and movable objects appear to float. The illustrated artwork requires
its own measured source anchor, owned by the integrator. A source image prompt
or the vector fallback's coordinates are not evidence of the bitmap's anchor.

If scenery fails to load, the room should retain its identity, full coverage
and interaction plane through the vector fallback. Do not silently fall back
to the bedroom palette or add a new timer to get room animation working.

## Validation and integration status

`git diff --check` passed for this scoped change. No Gradle, build, installation
or commit was run by this worker, as required by the single-build-owner rule.
The integrator should run `PetEnvironmentBackgroundTest` with the other focused
  room/placement/UI tests and inspect the new portrait/landscape fallback images
and contact sheet. Build and device acceptance are not claimed here.

## Native 3D counterpart follow-up

`Fly3DRenderer.kt` now has dedicated native room branches for the same three
IDs. The full fly uses an opaque GLES habitat, so simply adding a painted
background would leave that avatar in the old default kitchen. This required
a native counterpart rather than a screenshot, texture or transparent overlay.

The snack lab contains a crowned banana specimen in a stylized bell jar and
an oversized snack-button machine. Cosmic laundry contains a washer portal
with one slowly tumbling sock and a sock trophy on the opposite wall. Oddball
diner contains a hovering UFO over pancakes and a boot cactus. Motifs remain
near the rear edges, with a quiet center. Each branch draws sparse architecture
and returns before the original window/panel/furniture paths. Existing room
branches and the stage renderer remain intact.

The exact original table and prop inventory code is extracted into
`interactionTable`: top at world `y=.80`, half-width `2.10`, half-depth `1.0`,
four legs, and all props from `f.props` including their rotations. This avoids
a second placement system. None of the decorative funny objects becomes a
new physics prop. Original kitchen/table behavior is unchanged.

All new room movement reads `sceneTime`, which comes from the existing
pause-aware coherent `Fly3DFrame` snapshot. Existing uploaded meshes are reused;
there is no texture loading, new GL resource, context I/O, timer or random state.
Only new cartoon scenery uses a slightly thicker existing outline pass, less
material gloss and a 35% flat-color contribution in the existing shader.
Character rendering and the earlier room shaders retain their prior settings.

`Fly3DGpuTest.newCartoonRoomsHaveNativeSceneryAndFreezeWithTheAvatarClock`
adds actual GLES portrait/landscape captures, pixel opacity, distinct room
output, draw/triangle/pixel budgets and paused-frame equality checks. It also
requires each new scene to draw less geometry than the existing kitchen. This
test has **not** been executed by this worker. Native GLES appearance, clipping
and phone performance require device/emulator acceptance; host canvas tests
cannot establish them. Inspect each `cartoon_*` GPU screenshot before claiming
that the full-fly habitats have been visually verified.
