# Shared environment detail pass — teach-back for Meow

Date: September 13, 2026.

## Exact changes

`PetEnvironmentBackgrounds.kt` now delegates reusable materials and ambient accents
to new `PetEnvironmentAtmosphere.kt`. All ten existing Canvas rooms and their
frozen picker thumbnails receive the same improvements:

- Kitchen, greenhouse and orchard get soft directional window light.
- Library, cafe and bedroom have warm local light; pond and observatory have a
  restrained moonlight pool. Edge shading gives every room a clearer interior.
- Interior rooms have eight subtle floating dust points near the outer edges.
  Pond adds three animated ripples, beach eight sunlight glints, orchard five
  low leaves, and observatory a five-point rotating constellation.
- The shared tabletop now has a top highlight and a shaded material ramp. Its
  geometry and every pixel below the contact plane stay stationary across time.
- Candle flames have a bright flickering core instead of continuous kettle-like
  steam. The kitchen kettle retains its actual steam animation.

`PetEnvironmentBackgroundTest.kt` adds one material-depth/stationary-contact test.
Existing deterministic replay, invalid-phase, resize, ten-room identity, native
contact sheet and portrait/landscape tests remain in place.

## Why and how

Previously the scene architecture was readable but the table was one flat color;
there was little directional light linking the furniture to a room. This change
uses small original vector details and material contrast instead of introducing
large textures, glow blur layers, more simulation clocks or animated UI chrome.
The performance-facing change caches all ten wall palettes/brushes and beam paths
once per painter. The tabletop brush only rebuilds on an actual height change.
Materials belong to a painter instance, so thumbnail and full-stage resize caches
cannot race through a global shared mutable brush.

All animation consumes the existing normalized clock/slow arguments. There are
no new effects, coroutines, sensors, models, audio, physics or network calls.
Each room has at most eight new animated accents; environmental detail never
creates duplicate movable book/glass/ball props.

## Integration boundaries

This is the shared Canvas environment implementation used by floating-avatar
habitats and room thumbnails. Full-body GLES mesh environments are owned by the
separate 3D renderer worker. No changes to photo backdrops, static-vs-habitat
selection, avatar scale, thermal pause, screens, ViewModel, world controllers or
3D files are included. The existing `.78` normalized contact-plane contract is
unchanged. These improvements do not claim AAA visual quality or biological
simulation fidelity.

## Verification and future diagnosis

Worker checked the scoped diff for whitespace; no Gradle/build/device action was
run. Sole build owner should run `*PetEnvironmentBackgroundTest`, inspect
`app/build/reports/pet-environments/contact.png` and each room at portrait and
landscape sizes, then profile the actual phone. Existing tests prove deterministic
paint and stationary contact pixels; they do not measure GPU/frame/thermal cost.

If contact props appear to slide, inspect world/surface coordinate mapping before
altering decorative light. If scene colors move during a resize, inspect the
height cache and ensure painter instances aren't shared between simultaneous
canvases. If dust or light continues during pause, investigate the existing host
SceneClock: this helper cannot advance itself. Avoid adding randomness in draw,
per-frame palettes, bitmap decoding, or a timer per light to extend these rooms.
