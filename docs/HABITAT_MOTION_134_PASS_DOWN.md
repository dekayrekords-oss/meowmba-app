# Stationary habitats and Care mode switch — Build 134

## Delivered behavior

- Care now has themed Backdrop / Habitat choices. They call the existing saved
  mode setter, retaining the chosen room, backdrop, care needs and size preference.
- Painted habitats no longer pan horizontally or vertically. Removed the broad
  animated light wash and shimmer veil from this renderer. Original PNGs and
  source/table framing are unchanged.
- All nineteen illustrated habitats have small source-anchored animated details.
  Clipping follows the artwork through aspect changes and matches the image's
  rounded destination coordinates. Portal Arcade has window/skylight stars,
  a tiny arcade player and pellets, and orbiting portal arcs. Alien Aquarium has
  three original fin/eye-stalk fish and two rising bubble columns. Other scenes
  use window birds/stars/rain, plant fireflies, falling leaves, water ripples,
  candle flicker, steam, brewing bubbles, washer swirls or drifting meteors.
- The 3D fly's aquarium also has three small alien swimmers and rising bubbles
  inside the recessed glass. Its arcade has moving pupils and window stars.
  The room camera and furniture remain stationary.
- Habitat avatars use a 1.30× root multiplier. Lumen, Signal/Pearl/classic,
  generated forms, full-fly rendering, held generated props and hit guards share
  the change. The slider value and backdrop sizing are unchanged. Existing 2D
  full-fly containment limits still cap oversized wings/props to the viewport.
- Everything uses the existing pause/thermal-aware scene clock. Paths and
  materials are retained; no new timer, sensor, model request or bitmap asset.

## Validation

All 2,538 JVM tests passed across 363 suites, zero failures/errors/skips.
Debug, test and signed/minified release APKs assembled; release vital lint passed. The five new tests exercise repeated Care mode
switches on compact/landscape layouts without changing care preferences;
habitat sizing and eye hit mapping; native rendering of all nineteen scenes in
portrait/landscape; stationary foregrounds; deterministic pause replay; and
exact aquarium/arcade loop endpoints. Native previews were inspected.

The sampled portrait scenes changed only 93–3,075 of 288,000 pixels (about
0.03–1.1%). Foreground pixels are identical across phases. Existing wide crops
can exclude the animated windows in kitchen, library, cafe, observatory, beach
and bedroom; those crops stay still instead of placing effects on unrelated
floor/cabinet artwork. Other landscape crops contain visible localized motion.
This is deliberate clipping, not full-room motion or a missing asset.

Both app packages are installed at134 on the Fold7 by USB in-place upgrades.
The regular package's update time is2026-09-15 14:16:09. The production signing
certificate matches the previous release. All75 debug and149 release protected
entries remain present and unchanged; only generated release ART profiles differ.
All five real-phone GLES checks passed in11.727seconds, including new 3D rooms,
freeze behavior, premultiplied alpha, draw budgets and framebuffer limits.

Care's two native UI captures were inspected in light/compact and dark/landscape
layouts. The phone was running another app with navigation visible, so no
foreground Care walkthrough was forced. Graphics tests used offscreen EGL and
no activity; no on-phone interactive Care or cold-launch claim is made.

Build receipts, final signed installation and hardware GPU results are recorded
in `docs/verification/habitat134-20260915/summary.json`. APKs, full test XMLs,
38 native room previews and aquarium/arcade frame sequences are archived at
`/Users/prog/Meowmba-Releases/habitat134-20260915/`.

No app data, providers, keys, neural pairing, approvals or physical actuator
permissions were changed. This visual patch is not an all-day heat/battery or
voice accuracy signoff. Existing Play code130 review is not replaced.
