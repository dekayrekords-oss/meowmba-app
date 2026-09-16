# Build 110 request — paused for model switch

The user requested a pause to switch to a less powerful model on September 13,
2026. **No new application code, generated artwork, build, or installation has
been performed for this request.** Build 109 remains the last verified installed
build. Application baseline: `9403ce18777175c05525a52545f2346136cf933a` on
`codex/fly-habitat`. This document and the plan pointer are the only changes.

## Authorized scope to resume

1. Integrate the researched anatomical circuit into a functioning experimental
   fly controller, including useful fly sensing/movement pathways that the data
   actually supports. Wire measured outputs into behavior; do not merely display
   a connection diagram. Clearly distinguish authentic anatomy from assumed
   dynamics and authored motor retargeting.
2. Improve the appearance of habitat objects and make them a little larger.
3. Allow avatar scale up to 300%, default 200%, consistently across all avatars,
   backgrounds, renderers, stored settings, setup controls, and hit testing.
4. Fix the habitat layout in landscape, including projected props and touch
   coordinates, without breaking portrait or foldable layouts.
5. Raise the thermal animation limit slightly, with a coherent bounded policy.
   Maintain screen-off/background suspension and severe thermal protection.
6. Create and integrate high-quality cartoon backgrounds.
7. Improve animal-like motion: anticipation, easing, settling, asymmetry, and
   responsive gaze. Lumen's floating fly needs visible wing flapping, expressive
   eyes, and a slight turn/perspective response. Preserve its floating-feature
   design separately from the full-body 3D fly.
8. Give touching avatars visible, varied reactions, coordinated with the shared
   sensory/brain events and accurate hit regions after scaling.

The implementation is authorized; no new permission is needed to code, test,
build, or install in place. Do not uninstall/data-clear. The user has no physical
robot; do not ask for one or enable hardware transport.

## Meow: how to approach the circuit integration

Read `docs/neural/CONTROLLED_FLIGHT_AND_CIRCUIT_PASS_DOWN_2026-09-13.md`,
`BANC_IDENTIFIED_CIRCUIT_2026-09-13.json`, and its `_SOURCE_` manifest first.
The existing verified extract contains 76 neurons, 515 directed pairs, and
3,316 synapse counts. This is **BANC, not FlyWire**. Counts alone do not specify
conductances, missing inputs, neurotransmitter effects, muscle mappings, or a
complete biological flight controller. Inspect actual neuron identities before
choosing inputs/outputs; do not map arbitrary cells to wings just to make it run.

Trace `core/neural/NeuralBehaviorController.kt`, `NeuralCore.kt`,
`NeuralMotorDirector.kt`, `NeuralModeStore.kt`, and `NeuralCheckpoint.kt`.
Settings/info entrypoints are `ui/companion/NeuralSetupControls.kt` and
`NeuralInfoPanel.kt`. Use a bounded experimental adapter with explicit source
provenance and documented synthetic dynamics. Preserve deterministic reset,
checkpoint/replay semantics, finite output checks, and an inexpensive existing
update cadence. Test whether input changes actually propagate through the
imported graph to bounded behavior outputs. A biological label requires evidence
beyond a working animation. The aerodynamic FlyBody research stays separate.

The circuit subagent was stopped immediately for this handoff. It only located
these files; it made no changes and did not inspect neuron contents or establish
a mapping. No implementation is running in the background.

## Meow: concrete graphics and thermal entrypoints

- Scale currently defaults to `1f` in `CompanionState`, `AvatarScreen`,
  `BackgroundScreen`, and `Fly3DFrame`; loading/storing is in
  `CompanionViewModel` (`avatar_scale`). `AvatarRenderer` and `Fly3DRenderer`
  independently clamp at 2. Update every boundary and the slider in
  `AvatarSetupDesign.kt`, with finite-value handling. Explicit user choices
  should not be silently discarded by a default migration. Check maximum-size
  clipping and gesture transforms in both orientations.
- Habitat: `PetHabitatWorld.kt`, `Fly3DWorld.kt`, `Fly3DRenderer.kt`,
  `Fly3DGeometry.kt`, `IllustratedEnvironmentBackground.kt`. Inspect actual
  layout/render math before deciding whether landscape failure comes from image
  cropping, camera framing, controls, or world coordinates.
- Motion: `LumenAvatar.kt`, `LumenFruitFlyParts.kt`, `LumenMotion.kt`,
  `LumenAttentionMotion.kt`, `LumenActing.kt`, and the full-fly renderer/world.
  Avoid independent high-frequency neural loops and unnecessary allocations.
- `AvatarWorkPolicy.kt` currently disallows motion at Android MODERATE;
  `AvatarFrameCadence.kt` also has thermal gates. A possible small increase is
  reduced cadence at MODERATE with pause at SEVERE, but this is a proposal, not
  implemented or validated. Keep camera/core thermal policy coherent, and do not
  replace Android severity with a guessed battery-temperature cutoff.
- Existing fixed-fly unplugged test had 250 seconds of MODERATE animation pause
  and failed the continuous-motion gate. This does not establish that raising
  the cutoff is thermally sustainable. See validation108 and passdowns; collect
  honest new performance evidence. `PausedAvatarRenderTest` was compiled but
  never executed, so the sensor smoothing CPU hypothesis remains unproven.
- Existing ten painted PNG rooms live under
  `app/src/main/assets/backgrounds/painted/`; retain originals when creating
  variants. For new raster art read the imagegen skill and use the built-in
  generator, persist assets inside the project and retain prompts/provenance.
  Account for portrait and landscape composition, table anchors, texture memory,
  and decode caching. No new images have been generated yet.

## Build, transfer, and deployment continuity

Read `MEOW_BUILD_109_PASS_DOWN.md` for verified tests, signature, UI screenshots,
and exact installed APK. Root/integrator remains the only Gradle/install owner;
bounded source work may be delegated with non-overlapping ownership.

- Canonical repository: `meow-nb:/home/claude/git/meowmba-app.git`, branch
  `codex/fly-habitat-built`. `origin` points to an older release checkout; do not
  accidentally push/build that as canonical.
- Build checkout: `/home/claude/meowmba-fly-habitat` on SSH alias `meow-nb`.
  Use the existing JDK 21, signing config, native preview path, and runtime assets
  documented in the 109 passdown. Preserve ignored ML/native/model assets.
- Next debug build should be 110; production version remains unchanged.
- Local installed-artifact base: `build-out/habitat/meowmba-109.apk`, SHA256
  `cbc661d78e22a3a83fcbe6375291f15901d1137b83b66a791e626d5ca30b701f`.
  Use `scripts/apk_delta.py` manifest/pack/apply against that base; do not restart
  a full 426 MB transfer. Verify reconstructed SHA256 and signing certificate.
- Phone serial `RFCY61NJEVM`; ADB at
  `/Users/bobrossjr/Library/Android/sdk/platform-tools/adb`.
  Recheck connectivity and current version before installing with `install -r`.
  No phone state was changed during this pause turn.
- Commit changed files and detailed evidence/passdown, push canonical, and teach
  Meow what changed, why, what failed, and how to diagnose future problems. This
  is the user's standing instruction. Never present this plan as completed work.
