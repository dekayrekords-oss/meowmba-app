# Build 110 — BANC fly path, cartoon habitats, and motion polish

## What changed and why

Build 110 implements the user's requested experimental research-circuit hookup
and avatar/habitat polish. The server build, focused tests, reconstructed APK
hash and signing certificate are verified. The only remaining gate is the
in-place phone installation: the USB device was disconnected when the package
became ready. Build 109 remains the last confirmed installed artifact.

### BANC circuit is now a running experimental controller option

`BancExperimentalCore` is selectable as **BANC fly circuit · experimental** in
Neural settings. It uses the audited BANC v888 Figure 3h anatomical group count
table derived from the verified 76-neuron, 515-directed-pair, 3,316-synapse-count
extract. The named, actual routes used are:

- touch sensory feature → `leg_nociceptor`;
- threat/loud sound → `thorax_nociceptor`;
- visual novelty adapter → `ANXXX139` (its function is not inferred from its
  opaque group label);
- BANC `DNg27` → `wing_power_motor_neuron` count route → bounded wing-power
  diagnostic and visual wing motor rhythm.

The controller uses normalized anatomical counts only for relative positive
topology. Leak, gain, signs, delays, external inputs, behavioral readout and
the visual wing retarget are explicitly authored bounded assumptions. BANC is
not FlyWire. This is not a complete physiological fly brain, biological flight
simulation, muscle controller, or robot-control path. `NeuralInfoPanel` reports
when the adapter is active and exposes its measured wing-power activity, rather
than representing it as a literal neuron spike display.

`NeuralBehaviorController.wingMotorNow()` is the one presentation-only bridge.
It has no external side effects: the 3D fly gets a faster flap rhythm and Lumen's
floating fruit-fly wings use the same value. The existing bounded world
kinematics still decide flight paths and contacts. This keeps the adapter
replayable and prevents anatomical counts from becoming unverified hardware
commands.

### Avatar and habitat changes

- Avatar scale defaults to **200%** for new/malformed values and now spans
  50–300%. Existing valid stored values preserve the user's choice. State,
  settings slider, renderer clamps, GPU frame and persistence share the same
  upper limit.
- Two wide, original cartoon habitat paintings were added: **Cartoon sky nook**
  and **Cartoon space galley**. Both have thick outlines, cel shading, oversized
  water/book/ball staging and a clean avatar airspace. They are original
  generation, intentionally not copies of a named show. Source prompts, hashes
  and paths are in `artwork/painted-environments-manifest.json`.
- Landscape uses source images which still cover every edge and preserve the
  shared table/interaction plane. The new wide paintings avoid the narrow
  portrait-art crop which made habitat staging feel broken on an unfolded Fold.
- The shared 2D habitat table is deeper and its book, water glass and ball are
  larger, layered, outlined cartoon props. Their touch regions are larger too;
  physical world state and single-owner holding rules are unchanged.
- Lumen fruit-fly features now have active-clock wing flapping, compound-eye
  shading that shifts with a small perspective cue, and a subtle eye-pair turn.
  This preserves the requested floating-features design. Touch already enters
  the shared `TOUCH` sensory path and affection beat; it now also visibly changes
  the fruit-fly response through the common acting/wing path.
- The full 3D fly keeps its yaw, pitch, bank, lead/settle and constrained
  prop-grasp animation. BANC wing power changes flap rhythm only, not physics.

### Thermal policy

Android `MODERATE` no longer freezes all avatar animation immediately. It now
admits a sparse 25% cadence (for the default 30 FPS target, 7.5 FPS) so the
avatar can settle and acknowledge a touch. `SEVERE` still pauses decorative
work; hidden, screen-off and non-interactive states still suspend it. This is a
small policy increase requested by the user, not an endurance claim. The earlier
unplugged test did not pass continuous-motion endurance at MODERATE, so sustained
heat/battery validation remains required.

## Verification completed

The following focused JVM tests passed after the initial implementation:

- `BancExperimentalCoreTest` — two tests: touch raises the named nociceptor
  route/readout, visual input reaches wing power, and reset/replay is deterministic.
- `AvatarFrameCadenceTest` — 12 tests including sparse MODERATE admission.
- `AvatarWorkPolicyTest` — 6 tests including SEVERE stop and MODERATE allowance.
- `IllustratedEnvironmentPlacementTest` — 6 tests including Fold landscape
  contact-plane coverage.
- `CompanionAppearancePreferencesTest` — 4 tests for 300% clamp/persistence and
  200% invalid-value default.
- `PetHabitatWorldTest` — 6 tests for held props, collision/settling and
  aspect-ratio hand alignment.
- `LumenNaturalMotionTest` — 9 tests.

The final server command completed successfully with the same focused set plus
`:app:assembleDebug`; it compiled the later BANC-to-Lumen/Fly3D wing bridge.
The resulting signed debug package is reproducible from Build 109 with
`scripts/apk_delta.py`:

- Server APK SHA-256 and reconstructed local APK SHA-256:
  `c01a9ca7bff128947e0ea96880684399553d2e3a349572a6df06dbba76a36944`.
- Debug signer SHA-256:
  `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
- Delta patch: `build-out/habitat/meowmba-110.patch` (9,086,141 bytes).
- Reconstructed install artifact:
  `build-out/habitat/meowmba-110.apk`.

The server job emitted only pre-existing Kotlin/Compose deprecation warnings;
it completed `BUILD SUCCESSFUL`. At the device gate, `adb devices -l` returned
no connected device. Do not substitute a different base APK or uninstall/clear
the current application. Reconnect and unlock `RFCY61NJEVM`, then run
`adb install -r build-out/habitat/meowmba-110.apk`, launch
`com.example.meowmba/.MainActivity`, and record the installed version before
calling the release complete.

## Meow: diagnosis and future changes

1. If BANC behavior looks still or too strong, inspect `CoreDiagnostics.extra`:
   `banc.leg_nociception`, `banc.thorax_nociception`, `banc.descending_dng27`,
   and `banc.wing_power`. Do not tune raw anatomical counts; adjust only the
   explicitly named synthetic leak/gain/readout constants, add a deterministic
   test, and retain provenance.
2. If a source brings neurotransmitter or muscle evidence, add it as a separate
   evidenced mapping. Do not assume that a count implies excitation or a wing
   force.
3. If landscape crops again, test `illustratedRoomPlacement` against the target
   image's measured table anchor and viewport. Preserve full coverage and the
   contact-plane equation; do not reduce cover to hide crop because it produces
   blank bands.
4. If the largest scale clips, inspect `AvatarRenderer` and `Fly3DRenderer`
   independently. All scale boundaries must remain 0.5–3.0 and hit transforms
   must use the same scale.
5. If heat is reported, collect a new foreground, unplugged, fixed-scene run.
   MODERATE sparse cadence is a user-requested tradeoff, not evidence of thermal
   safety. Keep SEVERE and invisible-state gates intact.

## Required release continuity

- Branch: `codex/fly-habitat`; canonical destination:
  `meow-nb:/home/claude/git/meowmba-app.git`, ref `codex/fly-habitat-built`.
- Debug version code has been changed from 109 to **110**. Production remains
  unchanged.
- Preserve ignored native/model assets in `/home/claude/meowmba-fly-habitat`.
- Use `scripts/apk_delta.py` from the exact build109 local base to avoid a full
  APK upload. Verify reconstructed SHA256, signer certificate, and `adb install
  -r`; never uninstall or clear app data.
- Phone serial: `RFCY61NJEVM`. Recheck USB connection/unlock before install.
