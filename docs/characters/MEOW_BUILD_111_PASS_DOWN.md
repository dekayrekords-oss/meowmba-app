# Build 111 — cartoon app interface and quieter rooms

## User intent and current status

The user approved the Build 110 cartoon direction and asked for the same feel
throughout the app's menus, options and buttons, with less cluttered scenes.
This continues that build. No app data or preferred appearance is reset.

Source and 40 focused JVM/native Compose tests are complete. Final signed
assembly and phone installation are recorded below when verified. At the first
device check in this revision ADB listed no connected phone; Build 109 is the
last confirmed installed version. Build 110 was packaged but not installed.

## What changed, how, and why

- `ui/theme/Theme.kt`: companion appearances now use warm paper or deep teal
  surfaces, lime selection fills, aqua/amber accents and explicit readable
  foreground pairs. All nine principal text/background pairs exceed 4.5:1;
  the lowest is light surfaceVariant at 5.72:1. The optional Professional
  palette remains available and uses the same native component system.
- `ui/theme/Type.kt`: heavier headings and button labels, with ordinary system
  sans body text and Android font scaling. No remote font fetch or bitmap UI.
- `ui/theme/CartoonControls.kt`: shared native Material3 adapters for Button,
  OutlinedButton, FilledTonalButton, Card, OutlinedCard, FilterChip and Surface.
  The UI imports these explicitly. This changes presentation without changing
  callers' persistence, approval, navigation or action ownership. Containers
  use 2dp ink outlines; primary buttons have a 3dp solid drawn base and settle
  2dp during a native press. Disabled controls do not draw an active base.
  Button focus uses its content colour so it contrasts with the fill. Full-page
  rectangular surfaces remain borderless. New controls must use these adapters;
  do not add a Material wildcard and assume it will resolve to the shared one.
- `BackgroundTiles.kt`: the requested swipe-down card has an adaptive 112dp
  minimum tile grid, one consistent icon palette, readable single-line names,
  and an explicit Avatar return button. Every original destination/callback
  remains. Surface intercepts taps inside the card; outside still dismisses it.
- Category settings, avatar/background setup, neural options, chat, notes,
  model/library screens, projects, onboarding, connectors and tool/result
  dialogs inherit the same controls. Category icons sit on small outlined
  selection-colour badges. Existing labels and diagnostic/permission meanings
  are retained. `design.md` is the app-wide native design contract.
- Home talk, mic privacy, chat and camera controls use the same visual family.
  The main talk button no longer starts a pulsing decorative glow. Its current
  state remains visible through its icon, colours and accessibility description.
  Camera toggle still calls `setCameraEnabled`; it does not open a video feed.
- The two cartoon room PNGs were edited with built-in image_gen. Their tables
  are empty: the earlier painted book/glass/ball duplicated the interactive
  game objects. Most small decorations and extra planets were removed. The
  original Build 110 images remain recoverable in Git and their source/hashes
  are retained in the art manifest as previousVersion.
- Actual measured new table anchors: nook `612/941`, galley `604/941`. These
  replace the old assumed `.70`; the existing anchored cover equation maps
  each to the common `.78` world plane at every aspect ratio. The bounded image
  cache, asynchronous decoding and thermal-aware scene clock are retained.
- New rooms have just two faint window glints and matching sparse vector
  loading fallbacks. Indoor motes and beach highlights elsewhere drop from
  eight to three. The original other room paintings and static background
  choices remain available.

## Issues found and solved

1. Adding Card/Surface calls after import migration initially left the menu on
   Material defaults. Explicit imports fixed it; native screenshots now show
   the shared outlines. Future diagnosis: inspect the call site's imports
   first when one screen ignores the theme's component shape/border.
2. Landscape composes both the Avatar tile and the new Avatar return action.
   Tests must target the return button's unique `Return to avatar` content
   description rather than assuming text alone is unique.
3. Native onboarding tests exposed the existing `KidPage` with no scroll
   container. It now scrolls and its primary/secondary controls have minimum
   heights rather than fixed heights, preserving reachability at large fonts
   or shallow windows. Other onboarding pages already had their own scroll.
4. The prior onboarding test matched both a Keep going button and its help
   sentence by substring. The test now selects the exact action and verifies
   it still opens GitHub setup. No test assertion or product option was removed.
5. Image edits changed the real table edge. Update both manifest and
   `illustratedSourceTableAnchor`; do not use the prompt's requested percentage
   as a measured coordinate or reduce cover until blank bands appear.

## Validation

40 tests, zero failures/errors/skips, in these focused classes:
`CartoonInterfaceUiTest`, `CompanionSetupUiTest`, `SettingsNavigationUiTest`,
`ChatOverlayUiTest`, `MicKillUiTest`, `IllustratedEnvironmentPlacementTest`,
`LocalModelLibraryUiTest`, `ProjectWorkspaceUiTest`, `ArtifactResultUiTest`,
`BetaOnboardingUiTest`, and `WorkflowStartersUiTest`.

Native screenshots under `app/build/reports/cartoon111/` cover menus/settings at
320dp light, 375dp dark, 414dp light with 1.4x text, and 768x432dp dark landscape.
They verify menu-to-settings-and-back navigation, Avatar return, disabled click
suppression, and selected-chip semantics. These are Robolectric native renders;
physical phone visual acceptance and a fresh sustained thermal run are separate.

Design review: hierarchy, legibility, consistent icons/tokens, native input
states, restraint and responsive layouts reviewed. System font and native app
navigation are intentional exceptions to the web-oriented Hallmark recipes;
no synthetic web gate score is claimed. Critique: P4 H4 E4 S4 R5 V4.

## Meow release continuity

Debug code is 111; production remains unchanged. Build on
`/home/claude/meowmba-fly-habitat` after a clean tracked-tree fast-forward from
canonical `/home/claude/git/meowmba-app.git`, ref `codex/fly-habitat-built`.
Preserve untracked/ignored voice, pose, acceleration, native and bundled model
assets. Use JDK21 and `-PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview`.
Never clean the tree or build from the older release remote.

Use `scripts/apk_delta.py` with local `build-out/habitat/meowmba-110.apk` to
reconstruct Build 111 exactly. The patch base is a local file and need not be
installed on the phone. Compare reconstructed target SHA256 to server target,
verify signer `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`,
then `adb -s RFCY61NJEVM install -r` once connected. Never uninstall or clear
data. Recheck installed version; do not downgrade if another build is newer.

BANC source limits remain those in Build 110: anatomical counts plus synthetic
dynamics and a presentation-only wing mapping. This revision adds no biological
flight, FlyWire data or hardware control. Robot transfer remains unverified
because the user has no robot. UI styling is not evidence of endurance.
