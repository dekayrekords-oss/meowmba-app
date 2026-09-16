# Build 116: Clippy and the wizard as Lumen forms

The user asked for Lumen to turn into Clippy or the classic Microsoft helper
wizard. Both are new selectable appearances of the same Lumen avatar. Existing
forms, saved choices, backgrounds, scale, sensors, care and neural substrate
remain. No additional chat brain or neural worker is created. The full MaleCNS
integration from Build115 stays intact.

## Implementation and reasons

`AvatarAnimal` adds stable `clippy` and `wizard` keys, human-readable Clippy and
The Wizard labels, and Clippit/paperclip/Merlin aliases. `AvatarFormProfiles`
adds two damped appearance channels. The existing selection callback persists
`avatar_animal` and `avatar_style=lumen`; temporary performances still reset to
the selected form. The Avatar Look picker exposes both options and explains the
spoken switching commands. Fixed helper silver/hat/beard palettes do not show
an ineffective animal-color control; chosen eye color and subtle glow remain.

`LumenOfficeHelperParts` contains original code-drawn vector artwork: a continuous
silver paperclip loop with two returns, or a bent starry blue/purple hat, white
brows, moustache and pointed beard with a small wand. Reusable paths and strokes
use the same Lumen root. No downloaded legacy executable, sprite/runtime package,
new rendering clock, bitmap asset, camera or sensor is needed. Backdrop and
foreground share the same form weight and root position; the existing eyes stay
visible between them. Geometry grows and fades with morph weights rather than
appearing at full extent before layout has room.

The eye renderer blends helper forms toward cream cartoon eyes, small dark
pupils, clean outlines and restrained highlights. Existing gaze, blink, speech,
expression, affection, breathing and movement samples drive the result. Other
forms retain their prior material at helper weight zero. Custom eye colors still
work. Body bounds include wire/beard and hat, including rotation and landscape
travel. Matching hit regions stop habitat toys behind those parts stealing a pet
interaction. Privacy indicators remain screen-owned and cannot morph or move.

`AvatarFormRequest`, the deterministic router and existing `avatar.form` tool
accept explicit requests such as “turn into Clippy” and “become the wizard”.
Reset phrases include “stop being Clippy” and “stop being the wizard”. The short
`paper clip` alias is explicitly allowed while general media/negation/compound
requests still fail the anchored form parser. Tool instructions now describe
helpers as well as animals. No permission, tool dispatch or robot boundary is
bypassed, and no result is claimed unless the host callback succeeds.

## Testing and troubleshooting for Meow

Run the OfficeHelpers functional/native suites along with existing form routing,
character traits, full-species rendering, morph, hit and Brain regressions.
Functional checks exercise actual router/tool calls, saved preference restoration,
smooth 22-channel morphing and the same gaze/blink/speech clock. Native rendering
checks distinct art, visible expressions, stationary privacy marks, full and
partial morph bounds, portrait/landscape and the compact picker. Native preview
artifacts write to `app/build/reports/office-helpers/`. Final results and signed package verification are recorded below.

For a form that does not change, inspect the actual `avatar.form` result and
saved `avatar_animal` key. Do not add a substring routing shortcut: “don't become
Clippy” and requests for an image must not silently change a saved appearance.
`setAvatarAnimal` switches to Lumen; the separate Full Fly style does not use
these floating forms. An explicit performance can temporarily wear another form;
its completion must restore the selected helper.

For clipped hats or wire during morphing, compare the painter's complete extent
with `lumenEnvelope`, root stretch/rotation and partial costume fit. Alpha alone
does not make full-size anatomy smaller. Keep both the artwork and hit geometry
in the same root; do not independently shift the eyes, beard, habitat or privacy.
For dead reactions, confirm the existing frame's gaze/open/mouth/affection fields
and the visible-stage/cooling policy rather than adding another animation timer.

Mac disk space is low. Build and render on the existing private Meow checkout
with its preserved native/models/pose assets. Do not delete earlier release APKs
or source to make room. Archive the final Build116 APK on Meow and use the
existing streamed, checksum-verified APK delta workflow. No public Android source
publication is authorized. The phone is disconnected; do not claim installation
or physical touch/heat acceptance from host tests.

## Reference

Microsoft identifies Merlin as its wizard character in the original
[Microsoft Agent announcement](https://news.microsoft.com/source/1997/06/18/new-microsoft-agent-beta-2-0-allows-creation-of-interactive-characters-for-internet-and-activex-based-applications/)
and documents its [Merlin animations](https://learn.microsoft.com/en-us/windows/win32/lwef/microsoft-agent-animations-for-merlin-character).
The new native vector art implements the user's requested character forms; it
contains no Microsoft binary assets and does not imply Microsoft endorsement.

## First validation findings

The first complete Meow run compiled both APKs and passed 73 of 75 checks.
The helper render's new catchlight crossed the central pupil sample used by the
existing visibility regression; it now sits at the outer upper corner, keeping
the pupil center clear. Actual native previews show distinguishable Clippy and
wizard forms, gaze/blink/speech motion, and readable compact picker controls.

The older `AvatarMorphTest` preference test failed while its mocked Context
started unrelated AgenticRuntime/Room setup, before any appearance assertion.
It now uses the same scoped Android/AgenticRuntime mock and fake speech fixture
as current preference tests, disables spontaneous idle performances during the
scenario, and restores all fixtures afterward. Its transformation, persistence,
color, performance-return and reset assertions are unchanged. Do not mask this
kind of host-test problem by weakening production Context checks or assertions.


## Final verification and delivery

Source commits: `3281f05` adds the helper forms; `41ee783` contains the final
pupil and isolated preference-fixture fixes. Build116 was built from
`41ee783d318e064c486219a803eae44ff5825b48` on the private Meow checkout.
The final Gradle run passed **75 tests, zero failures/errors/skips** and built
both debug and instrumentation packages. Instrumentation was compiled, not run
on a phone. The final native expression contact sheet, landscape view and
compact picker were inspected. Art, gaze/blink/speech differences and picker
selection are visible. Touch ownership and full/partial morph bounds pass in
portrait and landscape at 50%, 200% and 300% scales.

Complete signed packages are archived on `meow-nb` in
`/home/prog/research/meowmba-build116-41ee783/`:

- `meowmba-116.apk`: 455,409,477 bytes; SHA-256
  `73f66c3661e3e932fdc1bc8ba2be118609e59cbf1000c047372a32a9cd1e876b`.
- `tests116.apk`: 2,740,418 bytes; SHA-256
  `fc604d9b4f3c847130b7c84bcc1181ff66bb57706df4ed144980914505b2071e`.
- Both APK signatures verify with the existing certificate SHA-256
  `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
- App package is `com.example.meowmba`, versionCode116. All 53 protected runtime
  entries and 15 painted backgrounds match Build115 by uncompressed SHA-256.
  The full MaleCNS graph is not packaged into Android.

The same archive includes signature logs, verification JSON, four native PNG
previews, and Build115-to-116 delta files. Small deltas, JSON and previews were
retrieved into `build-out/habitat/build116/`; transfer hashes match Meow.
The full APK was not reconstructed on the low-space Mac. When space is available,
use `scripts/apk_delta.py apply` with the exact Build115 base and the corresponding
patch; the script verifies the base and output hashes. Alternatively retrieve
the archived complete APK. Never delete previous builds without authorization.

On Meow, apksigner requires the JDK bin directory in `PATH` even when `JAVA_HOME`
is set. The initial archive verifier failed with `java: not found`; adding
`/home/claude/.local-toolchain/jdk-21.0.12+8/bin` to its environment resolved it.
This was an archive-tool environment issue, not an APK signing failure.

The phone remains disconnected, last verified on Build112. Build116 is ready
for installation; no installation, physical touch or sustained heat/battery
acceptance is claimed. Install as an update only; preserve data, preferences,
pairing and the server's untracked production assets. After installation choose
Avatar settings → Look → Clippy or The Wizard, or say “turn into Clippy” /
“become the wizard”. Check both forms and an existing form, portrait/landscape,
touch, spoken switching, restart persistence and cooling behavior on hardware.

Machine-readable evidence: [BUILD_116_VERIFICATION.json](BUILD_116_VERIFICATION.json).
This passdown is also copied to Meow's private research archive so its server
agent can reuse the implementation details and troubleshooting lessons.
