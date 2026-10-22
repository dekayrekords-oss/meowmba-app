# Optional care controls — pass-down to Meow

Added pure `core/behavior/PetCareState.kt`, compact `ui/companion/PetCarePanel.kt`,
and `PetCareStateTest.kt`. The integrator owns state persistence, clock ticks,
behavior feedback, and dispatching the panel's HabitatAction callbacks.

Care is off by default. Disabled care fills all meters and never accumulates
neglect; switching either direction starts full. Enabled care decays food by
8 percentage points/hour, energy by 4, happiness by 5, capped at 24 hours of
catch-up. Every meter has a 10% floor, with no death or punishment. Feed raises
food/happiness, play raises happiness and uses a little energy, and rest restores
energy/happiness. Values and timestamps are normalized; backward clock steps do
not damage the pet or charge the same time twice. The versioned preference codec
rejects nonfinite values and malformed input, defaulting to self-sufficient care.
Persist `encode()` after each change and use `decode()` plus `advance(nowMs)` on
restore. An unset timestamp initializes without assuming neglect since epoch.

The panel initially shows one Play button. Expanded controls fit a scrollable
300dp-wide, 360dp-high surface, with explicit Close, labeled care/learning
switches, needs only when care is on, eight habitat actions, preference feedback,
and reset. Learning feedback disables when learning is off. No timer is created
in composition. The integrator should put it below the safe top inset and avoid
covering existing privacy/approval controls.

Tests cover rates, restart persistence, reversible care toggles, no neglect while
disabled, long absences, clock reversal, action bounds and malformed storage.
Run `./gradlew :app:testDebugUnitTest --tests '*PetCareStateTest'` through the sole
build owner. Worker ran `git diff --check`; no Gradle or device commands. Actual
UI/device acceptance and test results must be recorded by the integrator.
