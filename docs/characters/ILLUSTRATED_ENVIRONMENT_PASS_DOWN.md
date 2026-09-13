# Painted environment integration — teach-back for Meow

## Implementation

`AnimatedBackgrounds.kt` now routes the ten pet environments and their picker
thumbnails through `IllustratedEnvironmentBackground.kt`. Existing non-room
animations, photo selection, plain background and static/paused routing are
unchanged. No new selector or environment ID is introduced.

The expected bundled assets are
`app/src/main/assets/backgrounds/painted/{room-id}.png`, where room IDs are kitchen,
library, greenhouse, workshop, pond, orchard, cafe, observatory, beach and bedroom.
The root integration owns the actual original image generation, bytes, provenance
and visual checks. This worker only implements consumption and bounded overlays.
Missing, corrupt or undecodable files fall back to the existing complete vector
room, including ambient motion; a missing asset cannot blank the screen.

## Memory and animation behavior

ImageDecoder runs on Dispatchers.IO, serialized with a coroutine Mutex so ten
picker tiles cannot allocate ten decoded full images simultaneously. The longest
edge is capped at 1536px for stage images and 320px for thumbnails; small images
are never enlarged during decode. Software bitmaps use sRGB. A 16MiB LRU bounds
retained decoded pixel memory; active Compose consumers can retain additional
references after eviction. Eviction never manually recycles a bitmap that an
in-flight draw could still reference. This is a retained-cache cap, not a promise
that total process memory stays below 16MiB.

The painter uses the caller's existing phase suppliers, read inside Canvas rather
than composition. Frozen thumbnails use fixed phases. Thermal pause/reduced motion
remain controlled by the established host clock; no timer or simulation loop is
added. The small existing light/mote/ripple/constellation accents remain above the
contact plane, and the original painting remains still beneath them.

## Geometry contract

The image generation prompt's requested table position is not trusted as a
measurement. `illustratedSourceTableAnchor` holds measured source fractions. The
kitchen/library source edges are at approximately .689, not the requested .78.
The measured greenhouse/orchard/workshop/pond/cafe/observatory edges are stored as
994/941/908/974/945/905 source pixels respectively, over 1536px height. Beach and
bedroom use their final measured edges 982/1536 and 887/1536. All ten original
1024×1536 PNGs have been copied by the root integrator. High-quality filtered scaling preserves
painted detail; source-native 1024×1536 assets are no longer downsampled for stage
use.

Placement preserves image aspect ratio and maps the measured source edge to the
world's fixed `PET_ENVIRONMENT_TABLE_Y = .78`. Scale is the maximum needed to
cover width, the region above the target edge and the region below it. Horizontal
cropping stays centered. This prevents either blank bands or sliding contact
geometry on Fold orientation changes. Very wide layouts necessarily crop more of
the upper source scene; the drawing is never stretched to make a portrait fit.

## Validation

`IllustratedEnvironmentPlacementTest` checks portrait/Fold/landscape coverage,
source-to-world anchor alignment, preserved aspect ratio, bounded decode size,
no decode upscaling and invalid viewport handling. Worker ran scoped diff checks,
not Gradle/device actions. Root should run this test plus the existing environment
regressions, verify final PNG dimensions/anchors, render all room thumbnails and
both Fold aspects, then record actual memory/frame measurements.

## Future issue diagnosis

If the book/glass seems to float above an illustrated table, remeasure the image's
edge and correct that room's source anchor; do not move the shared world table.
If a generated image changes, update its anchor measurement with its provenance.
If the old vector room appears, inspect exact asset filename/case and decode
errors/format before changing navigation. If memory grows in the picker, inspect
active retained image consumers and LRU bytes before increasing the cache cap.
Never add background network image fetching or a new per-room clock to this path.
