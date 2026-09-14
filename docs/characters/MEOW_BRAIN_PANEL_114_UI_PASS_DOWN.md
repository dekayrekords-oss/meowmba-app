# Build 114 brain monitor UI — scoped Meow passdown

Date: 2026-09-13. This is the UI worker's source handoff, not an installation or
phone acceptance record. The root integrator owns routing, controller lifecycle,
ViewModel sensory callbacks, package assembly, testing, Git commit and deployment.

## What changed and why

The former Brain monitor covered the avatar with a dark full-screen scrim. For
local cores it displayed 18 decorative bars derived from simulation steps, with
a minimum 2% even when no measured activity existed. Unpaired MaleCNS showed zero
rates without an immediate recovery action. Together these could make the monitor
appear either inert or deceptively active.

`NeuralInfoPanel.kt` now presents a transparent surrounding viewport with safe
system insets. The portrait sheet is at most 56% of usable height. Landscape uses
a right-side panel at most 360dp wide and 48% of usable width. The integrator can
keep the actual avatar in the remaining space. The sheet adds no polling or
animation timer; it consumes the controller's existing diagnostic cadence.

`NeuralInfoPresentation.kt` derives explicit state text and measurement validity:
Classic/off, Shadow/observing, Neural/controlling, waiting, cooling, error fallback,
unpaired computer, and missing/stale Meow response. Classic, cooling, fallback and
unavailable remote samples show em-dashes and empty tracks rather than old or
invented live measurements. An actual measured zero is shown as zero. Local meters
are the core's mean signal, peak signal and fraction of nonsilent units. MaleCNS
meters are the measured feeding, escape, grooming and wing population rates. The
400Hz visual scale clamps bar length but preserves the actual numerical rate.

Persistent Pet and Feed buttons accept only explicit user clicks and are disabled
unless measurements are usable. Shadow can accept sensory inputs, but the status
clearly says Classic still controls the avatar. A healthy Shadow monitor offers
Let brain control avatar through a separate explicit callback, preserving the
selected substrate in the integrator. Classic/unavailable paths offer
Start local brain, and unpaired MaleCNS offers Pair computer. Neither fires merely
by opening the view. Settings and Close remain available. The host provides the
callbacks and input feedback; changing mode is not a UI side effect.

Details default closed. Opening Details replaces the meters with readable
technical rows and an explanation of aggregate activity versus individual
synapses, preserving existing BANC and MaleCNS provenance/limitations. This avoids
adding invisible detail beneath the fold when someone taps the button.

## Tests and evidence to run

`BrainInfoUiTest.kt` defines eight native Robolectric/Compose tests:

- Classic states and explicit local-start/settings/close/detail actions.
- No diagnostics means unavailable, not measured zero.
- 320dp compact layout, real zero values, Pet/Feed feedback and touches reaching
  the avatar outside the sheet.
- Shadow's observation-only authority and sensory input controls.
- Cooling and three error fallbacks discard stale metrics and disable inputs.
- Unpaired MaleCNS offers pairing plus offline recovery without autofiring.
- Loss of the remote connection cannot keep displaying old rates.
- 840dp landscape width limits and real remote values, including missing versus
  zero samples, while action controls remain reachable.

The integrator should run `:app:testDebugUnitTest --tests '*BrainInfoUiTest'` and
inspect its seven PNG captures in `app/build/reports/brain114/`. This worker did
not run Gradle, install an APK, or claim these tests passed. `git diff --check`
was clean before handoff.

## Lessons for Meow and future diagnosis

Check mode, thermal tier, fallback and remote `malecns.connected` before deciding
that displayed activity is live. A saved pairing is necessary but not sufficient:
a fresh Meow reply is also required. Do not fabricate a visual minimum, random
bars, or missing-rate defaults to make a monitor seem active. The graph must not
run its own clock. An enabled-looking button also needs a ViewModel callback and
a live avatar/controller path; this panel alone cannot validate those routes.

Preserve the root integrator's file ownership. These files contain no networking,
permission changes, camera/mic toggles, model activation on open, or physical
robot controls.

Sticky errors block control but do not necessarily stop core stepping. The
fallback title therefore says “check brain,” not “brain paused.” Cooling does
actually suspend the core. Preserve that distinction in future status wording.
