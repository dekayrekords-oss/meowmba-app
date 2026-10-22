# Avatar setup redesign — teach-back for Meow

## Changes

`AvatarScreen.kt` now leads with the existing live preview and selected avatar
name, then explicit Backdrop / Play habitat choice and one 50–200% size control.
Look and Motion tabs group existing controls. Look retains all styles, character
forms and eye/feature/glow swatches. Motion retains intensity, playfulness,
emotion rehearsals, creation instructions, all animation categories/search,
Surprise me and show-more. Advanced settings starts collapsed and retains theme,
appearance, flip-cover setup and the diagnostics slot. No option or callback was
removed. Selection still applies immediately; performances still use the existing
parent callback. Search, tab and disclosure state use rememberSaveable.

`BackgroundScreen.kt` separates Static, Animated and Habitats. Static keeps plain
background and the persistent image picker; Animated keeps live weather and every
non-habitat animation; Habitats keeps all ten rooms and enables habitat mode when
a room is selected. Selecting a plain/image/animated backdrop disables habitat
mode. Merely browsing a tab does not change the current backdrop. Existing weather
overlay guidance and dim slider remain; avatar size is available here too.

`AvatarSetupDesign.kt` provides named spacing, section, size and mode controls,
using the current MaterialTheme color/type roles. `design.md` records the shared
native workbench design. Native framework conventions supersede irrelevant CSS,
HTML, font-download and web eight-state requirements from Hallmark. No new theme,
font, render loop, shader, business state or inference behavior was introduced.

## Integration API

Both screens append these optional parameters:

- `avatarScale: Float = 1f`
- `onAvatarScale: (Float) -> Unit = {}`
- `habitatMode: Boolean = false`
- `onHabitatMode: (Boolean) -> Unit = {}`

The integrator owns persistence and forwarding scale/mode to the avatar renderer.
The default callbacks preserve source compatibility but intentionally cannot save
changes. Wire all four on both screens before claiming functioning mode/size.

## Layout and verification

Scene columns derive from available width and font scale (1–4 columns), replacing
the old fixed three-column layout and tiny labels. Preview art has an aspect
ratio; content remains naturally sized inside the existing scroll container.
Selected choice semantics and labeled sliders support assistive input; Material
controls keep native focus/pressed/disabled states. Check 320/375/414/768dp, dark
and light themes, font scale 2, and Fold rotation before visual acceptance.

Worker ran `git diff --check`; no Gradle, device commands, deletes or commits.
Main owns compile, test and rendered acceptance. Existing avatar-animation search
tests should remain unchanged. There are no new business-logic tests because this
scope only reorganizes reversible controls and delegates their established APIs.

## Future diagnosis

If a size slider moves but the avatar does not, inspect parent callback wiring
and renderer scale before altering the UI. If choosing a backdrop still shows game
props, inspect persisted habitatMode and scene gating. If settings clip at large
fonts, check parent constraints and grid fontScale before adding fixed heights.
