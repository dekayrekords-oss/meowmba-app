# Animated environments — pass-down to Meow

## Changes and rationale

Added ten original native Canvas environments: Sunlit kitchen, Candlelit library,
Glass greenhouse, Clockwork workshop, Lotus pond, Peach orchard, Rainy cafe,
Star observatory, Seaside deck, and Cozy bedroom. Each has an individual palette,
recognizable edge scenery and ambient movement. The new entries append to the
existing enum, preserving existing ordinal-seeded backgrounds and persisted IDs.
`BackgroundAnimation.isPetEnvironment` identifies the ten rooms. The normal
background picker already enumerates the enum, so all avatars can select them.

`AnimatedBackground` routes rooms to `PetEnvironmentBackgroundPainter`, sharing
its existing SceneClock. Thumbnails draw the same painter at a frozen phase.
There are no new coroutines, timers, bitmaps, remote assets, model calls or blur
layers. Plant sway, rain, steam, gears, waves, lights, clouds and drifting petals
have fixed iteration budgets. A reusable path supports beach wave geometry.

The tabletop top is exactly `PET_ENVIRONMENT_TABLE_Y = .78f` of background height,
shared by every room and stationary across animation. The main integration owns
interactive book, glass and ball props. Decorative shelf books remain scenery;
no foreground movable book, glass or ball is duplicated in this painter.

## Validation and integration

Added `PetEnvironmentBackgroundTest`: ten-entry persistence and distinctness,
native portrait/landscape/tiny/fold aspect rendering, animation versus static
contact geometry, deterministic cycle seams and invalid phase handling, and
painter reuse after resize. Run through the sole build owner:

`./gradlew :app:testDebugUnitTest --tests '*PetEnvironmentBackgroundTest'`

Native artifacts are written to `app/build/reports/pet-environments/`, including
`contact.png` (five columns, two rows) and individual portrait/landscape PNGs.
The worker has not run Gradle or device commands. Main must record the actual
result and inspect generated renders before claiming visual/device acceptance.

## Teaching notes for future issues

Keep room animation reads inside Canvas; moving them into composition can
recompose settings/chat at animation frequency. Do not create one independent
clock per detail. Paused behavior inherits the existing caller-owned SceneClock.
A scene uses normalized geometry for Fold aspect changes, but stroke thickness
uses the shorter screen dimension. All render phases normalize to [0,1), with
non-finite phases becoming zero, so replay snapshots stay deterministic.
When adding contact physics, derive surface position from this shared constant
and the actual scene bounds, not from the avatar's scaled drawing coordinates.
Check a tiny picker and a wide Fold viewport whenever modifying edge scenery.

No FlyWire circuits, care logic, foreground physics, installation, physical robot
motion or measured thermal improvement is claimed by this background work.
