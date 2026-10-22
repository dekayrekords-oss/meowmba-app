# Idle workload optimization — Builds 136–138

The owner requested less background load with the same visible animation,
smoothness, features and interaction behavior.

## Changes

- Animation sampling scans the original sparse keyframes directly instead of
  building temporary lists and pairs for every property on every frame. Exact
  interpolation, boundary, duplicate-timestamp and discrete-field behavior is
  preserved by differential tests against the previous implementation.
- The sampled ART trace exposed repeated eye-fibre colour conversion. Cache the
  exact iris blend and the two per-frame opacity values instead of recalculating
  the same Oklab blend for 96 lines per frame. Geometry, stroke count, palette,
  colour-space arithmetic and resulting opacity values are unchanged.
- Illustrated habitats retain the stationary image in its own graphics layer.
  Animated objects use a separate layer with the original clock, exact placement,
  clipping, image quality and artwork. No additional offscreen bitmap is forced.
- Lateral and longitudinal sensor values are read in the avatar's draw pass.
  Sensor events no longer recompose its complete UI. Sampling, filtering,
  smoothing, force calculation and animation cadence are unchanged.
- Neural diagnostic snapshots are produced once per second while their panels
  are visible. Hidden snapshots previously sorted timing samples and published
  the whole companion UI state. The controller, motor, drives and sensory inputs
  continue at their previous rates. Opening Brain or Settings refreshes promptly.
- MotionSense reuses its linear-acceleration array and reads scalar input values
  directly instead of allocating two short arrays per accelerometer event.

## Verification

The new native UI regression compares retained and original habitat drawing at
the same screen coordinates for all nineteen rooms, portrait/landscape and two
phases (76 exact pixel comparisons). Same coordinates matter: adjacent views
produce one-channel rounding differences from translated image sampling. The
test uses the established native decorView capture path, avoiding Robolectric's
unsupported PixelCopy capture wait.

Existing cadence, scene-clock, sensor and drive-rig checks cover unchanged timing
and input behavior. All 2,547 JVM tests pass across 366 suites, with no failures,
errors or skips. Debug and Android-test packages assemble. The final full run
took107 seconds. A disconnected SSH command initially left a duplicate test
worker running; both workers were stopped and one clean full run completed under
a build lock. The interruption is not reported as a successful test run.

Preview136 was installed in place with matching signing and all75 protected
runtime entries identical to135. Its real phone aquarium display was inspected.
Final release and phone measurements are recorded in the verification receipts.

Intermediate137 also passes all2547 JVM checks across366 suites (255seconds). Ten eye
scene/contact PNGs and seventeen species/emotion PNGs are byte-identical to the
pre-colour-cache images. The76 retained-habitat comparisons also pass. Preview137
was installed in place, with the matching certificate and all75 protected entries
identical to135. `docs/verification/idle137-20260915/source.sha256` matches the
Mac, debug builder and release builder sources.

Final138 passes 39 targeted tests across seven suites, including the sampler
differential test, custom animations, eyes, species, scenes, drive visuals and
the 76 habitat comparisons. Debug and Android-test assembly pass. All 27 eye and
species PNGs remain byte-identical. The complete 2,547-test suite was run on137,
before the final sampler change; it is not claimed as a full138 run.

Final138 signed/minified release assembly and vital lint pass (189 seconds).
Both APK certificates match their existing installations. All 75 preview and
149 release protected runtime entries match135; only generated release ART
profiles differ. Source hashes match the Mac and both isolated builders.

## Measurement boundaries

Two pre-change 45-second foreground samples measured 65.08% and 64.60% of one CPU
core. Both started/ended at Android thermal status1 with USB power present. These
are process CPU observations, not controlled all-day battery or heat results.
The read-only profiling script also records graphics counters for comparisons.

The intermediate136 samples measured66.85% immediately after installation and
61.80% after warm-up. That was not a convincing heat fix. Graphics counters
improved, but investigation continued. A short sampled ART trace showed heavy
Float conversions and Oklab colour transforms; the iris loop repeated the exact
same blend48 times per eye. Build137 adds the exact-result colour cache.

The three-minute137 sample averaged 66.66% of one core, despite earlier short
samples of 59.11% and 62.95%. Battery temperature stayed at 35.5°C. This variability
prevents claiming that those changes alone solved idle heating.

An explicit ART speed-compilation request for the debug package returned Success
but its reported compilation state remained verify. It is not counted as an
optimization or explanation for any measured change.

Final138 measured 52.14% of one core initially and 49.49% over a subsequent
three-minute run (90/90 samples foreground, same PID). Battery temperature moved
from 35.4°C to 35.2°C with USB power; Android thermal status remained LIGHT (1).
Rendering activity was not identical: gfxinfo deltas were 62.69 frames/s in the
135 confirmation, 54.68 in the137 long run and 38.72 in the138 long run.
These are submitted window frames, not an isolated measurement of avatar pose
updates. No cadence setting or clock code was changed. The varying activity
means the CPU difference cannot be attributed entirely to this optimization;
neither unchanged real-device smoothness nor an all-day heat fix is established
by these samples. Native pixel and sampler checks establish exact tested output.
Longer controlled profiling is still needed if heating persists.

Both preview (`com.example.meowmba`) and regular (`com.meow.meowmba`)138 were
installed over their existing packages using USB `adb install -r`, successfully,
without clearing data. The current aquarium/green-eye screen was inspected.
Detailed receipts are in `docs/verification/idle138-20260915/`; raw measurements,
screenshots, build logs, XML and APKs remain in the artifact directory below.

Artifacts: `/Users/prog/Meowmba-Releases/idle136-20260915/`.
