<!-- Hallmark · pre-emit critique: P4 H4 E3 S4 R5 V4 -->
# Meow cartoon interface design

Scope: native Compose app menus, settings, chat, onboarding and tools. The
September 13 user request extends the original cartoon rooms across the app's
interface. Existing routes, preferences, actions and state ownership remain.

Genre: playful. Structure: native game-console workbench. Audience: the phone's
companion owner; primary action: return to and interact with the avatar. Bold
ink contours, warm paper/light or deep teal/dark surfaces, lime selections,
aqua and amber accents, and strong sans headings carry the cartoon feel.
Labels and descriptions stay plain and readable. There is no decorative
illustration inside settings; the avatar lives in the habitat.

The requested swipe-down card remains the navigation entry, with an explicit
Avatar return action and an adaptive grid. Settings use focused category pages;
specialist details stay progressively disclosed. This native adaptation uses
Compose tokens rather than introducing unused web stylesheets or custom widgets
with weaker accessibility semantics.

## Tokens

`AvatarSetupTokens`: compact 8dp, item 12dp, section 24dp, page 20dp, corner 20dp.
Color and shape roles are locked in `ui/theme/Theme.kt`; `CartoonTokens` defines
the shared ink (#172D34), 2dp contour and 3dp solid button base. All consumers
use named MaterialTheme roles. Dark surfaces range from #182A32 to #3B5960;
light surfaces range from #F3EEDC to #FFFAEB. Selection containers are #354E36
dark / #D3EAA7 light. Text pairs are explicit in both appearances. The existing
Professional appearance remains an optional calmer palette; it uses the same
controls and spacing. There is no forced preference reset.

System sans typography preserves Android font fallback and scaling. Headings
use bold/extra-bold; labels use bold; descriptions retain normal weight. The
native workbench hierarchy is in `ui/theme/Type.kt`.

## Structure

Avatar: preview and current name → Backdrop / Play habitat → size → Look / Motion
→ collapsed advanced settings. Look holds character, form and colors. Motion holds
expression, playfulness, emotion rehearsal and searchable performances. Advanced
keeps app appearance, flip-cover setup and diagnostics available.

Background: current selection → Static / Animated / Habitats → responsive scene
choices → dim and avatar size. Static retains plain app background and personal
image; animated retains live weather and all non-game animations. Habitats use
those same original rooms with explicit play-mode selection.

## Interaction and accessibility

Selections apply immediately. No new save dialogs, celebratory toast, automatic
scroll reveal, or animation timers. `CartoonControls.kt` delegates to Material3
and preserves native pressed, focused, disabled and selected behavior. Buttons
settle by 2dp on press without a running animation; native input, switch, slider,
error, loading, success, focus and hover behavior is retained. Controls have no
fixed text height; preview
art uses aspect ratios. Choice columns adapt to width and font scale. Text wraps
in descriptions; primary short action labels remain readable. Scroll containers
keep large-font controls reachable. Web-only eight-state/loading recipes do not
invent loading or error states for synchronous local preference controls.

## Scene restraint

Cartoon Sky Nook and Cartoon Space Galley have empty foreground tables and quiet
central airspace. The game owns the moving glass/book/ball; the illustration must
never duplicate them. Measured image anchors align the table with the habitat's
0.78 contact plane. Only two faint window glints animate in each cartoon room;
indoor motes and beach highlights are reduced elsewhere. The renderer uses the
existing paused/thermal-aware scene clock and bounded image cache.

## Verification

`CartoonInterfaceUiTest` exercises the menu, settings round trip, Avatar return,
native disabled/selected semantics and native screenshots at 320/375/414/768dp,
dark/light and 1.4x text. Existing setup, settings, chat, mic privacy and placement
tests remain release gates. Record exact results in the Build 111 passdown.
Screenshots are native Robolectric evidence, separate from physical-phone checks.

## September 14: onboarding and remaining submenus

First run now opens with a feature tour, explicit avatar/animal selection, then
anatomy-aware automatic fitting. Users review a display-proportioned preview,
can inspect it at full screen, and may reveal a size slider or restore auto-fit.
Computer, permissions, AI providers, GitHub and verified server pairing follow
as optional setup. A quick completion path still requires choosing an avatar
and reviewing size. Existing completed users are not enrolled again.

The native settled preview uses the same anatomy and envelope as the live
renderer without warming up a morph, starting sensors, or running an animation
clock. Only confirmed size is persisted. The Terminal peek retains wizard
state, and completing setup clears that transient state for a fresh later visit.

Dropdowns, searchable model menus, dialogs and modal sheets now share the ink
border, rounded shapes and surface roles. Voice/model setup, browser chrome,
Terminal, weather controls, inventory, agent status and remaining settings badges
use semantic colors in both appearances. Avatar art, weather artwork, terminal
syntax and third-party Android/system interfaces keep their own visual purpose.
The optional Professional palette continues to use the same shared controls.
