# Pass-down to Meow — Lumen fruit fly, September 12, 2026

## User direction

Keep the full-body generated fly as a separate commercial character concept. The
Lumen avatar must remain floating face features. Correct the ant-like impression
and mouth. This change implements the character, not the neural master plan.

## What changed and why

- Added `FRUIT_FLY` / `fruit_fly` to the persisted character enum. The existing
  picker and `avatar.form` tool enumerate it automatically. Exact aliases accept
  fruit fly, fruit-fly, fruitfly and drosophila; reset also recognizes the form.
- Added a twentieth damped anatomy channel. Switching forms fades the fly parts
  with the same rig, without altering the shared gaze, blink or speech inputs.
- Added `LumenFruitFlyParts`: short oval antennae with arista bristles, a single
  translucent wing accent per side, and red faceted eye surfaces. Long elbowed
  antennae and mandibles would read as an ant, so neither is used.
- The mouth is one detached curved contour. Speech opens that contour, rather
  than drawing a separate oval underneath a smile. There is no muzzle or snout.
- Retained animated stylized pupils for gaze readability; this is an expressive
  character design, not an anatomically exact insect simulation.
- Extended the shared geometry envelope for wing width and mouth opening. Paint
  and hit testing continue to use the same center/scale, including rotation.
- Expanded render contact sheets dynamically so the thirteenth character is not
  silently cropped by the old fixed twelve-character canvas.
- Debug preview code 96; release version/code remain 1.3.81/85.
- Saved the original generated full-body commercial concept and prompt in this
  directory. No supplied stock reference image is packaged in the app.

## Build and future troubleshooting

Base: 27bcc94, the preview-95 checkout. Fetched the release checkout and canonical
bare main before editing; canonical main had no commits ahead of this base.
The server checkout's unrelated GitHub fetch could not authenticate. Work stays
on `codex/lumen-fruit-fly`, in `/home/claude/meowmba-fruit-fly` on Meow.

Use the existing JDK at `/home/claude/.local-toolchain/jdk-21.0.12+8`.
The isolated worktree reuses the baseline APK's assets and native libraries via
`-PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview` and the
existing debug signing configuration. Runtime files are build inputs, not new
source assets. Compare APK entry hashes before handing off a preview; a fresh
worktree without ignored voice libraries can compile but fail at microphone use.

If a future species clips during travel, update `lumenEnvelope`, not just the
painter: the same envelope reserves travel and defines stage fitting. If a mouth
looks doubled, inspect all muzzle passes and keep only the selected form's path.
If a new form is missing from saved selection, check the enum key, profile map,
and spring channel together. Do not introduce another animation clock.

## Validation

Final focused run: 26 tests passed (FruitFlyAvatarTest, AvatarSpeciesRenderTest,
AvatarSpeciesTest, AvatarFormToolTest) and `assembleDebug` succeeded. The native
renderer contact sheet was visually inspected: floating compound eyes, antennae,
wings and a single mouth, with no opaque head or body. Renderer tests cover all
13 species, strong drive poses in four viewport shapes, stationary privacy marks,
blink/joy/concern differences, and transparent gaps between features.

Expanded run: 32 of 33 passed. The legacy settings test
`AvatarMorphTest.legacyCatSelectionChangesFormAndSurvivesRecreationWithoutResettingColors`
fails with `getApplicationContext(...) must not be null` in FeatureFlags. Re-ran
that exact test on unchanged 27bcc94 and reproduced the same failure. Its mock
Context is incomplete; this is not a fruit-fly regression. No production workaround
or unrelated test rewrite was introduced.

APK retained on Meow (the slow VPN transfer was canceled; no partial local APK is retained):
`/home/claude/meowmba-fruit-fly/app/build/outputs/apk/debug/app-debug.apk`.
Package `com.example.meowmba`, version 1.3.81, code 96. APK SHA-256:
`1086e7a04c322310f91ee0329fe464babb7ee4f1bf3b4b4922cf42408e4ad247`.
All 54 assets/native-library entries from the preview-95 baseline are present
with identical content hashes. APK signature verified; its signing certificate
SHA-256 matches the baseline:
`b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.

No phone installation, data clearing, production deployment, or neural-controller
implementation was performed. On a subsequently installed build, select Lumen →
Choose your character → Fruit fly, or say “become a fruit fly”.

Build-command lesson: Gradle's `--tests` options must directly follow
`:app:testDebugUnitTest`, before `:app:assembleDebug`. Placing them after assemble
produces “Unknown command-line option --tests”; moving them fixed invocation.
