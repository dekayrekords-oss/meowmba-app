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
artifacts write to `app/build/reports/office-helpers/`. Final results will be
recorded after inspecting the native images and signed package.

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
