<!-- Hallmark · pre-emit critique: P4 H4 E3 S4 R5 V4 -->
# Meow avatar setup design

Scope: native Compose AvatarScreen and BackgroundScreen, preserving existing
routes, theme preferences, renderers, actions and state ownership.

The two screens use one quiet game-console workbench: an honest selected preview,
clear mode choices, immediately applied controls, then progressive disclosure for
specialist settings. The existing MaterialTheme remains the source of color and
typography. This native adaptation uses Compose tokens rather than unused CSS.

## Tokens

`AvatarSetupTokens`: compact 8dp, item 12dp, section 24dp, page 20dp, corner 20dp.
Color roles: background, surface, surfaceVariant, onSurface, onSurfaceVariant,
primary, primaryContainer, onPrimaryContainer, outlineVariant. Typography roles:
headlineSmall, titleMedium, bodyMedium, bodySmall, labelLarge. No new fonts or
palette overrides. Selected states pair visible labels/checkmarks with color.

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

Selections apply immediately. No save dialogs, celebratory toast, automatic
scroll reveal, or new animation timers. Material controls retain native pressed,
focused, disabled and selected behavior. Content has no fixed height; preview
art uses aspect ratios. Choice columns adapt to width and font scale. Text wraps
in descriptions; primary short action labels remain readable. Scroll containers
keep large-font controls reachable. Web-only eight-state/loading recipes do not
invent loading or error states for synchronous local preference controls.

## Verification limits

Static code review checks retained callbacks and options, responsive sizing,
named colors and focus semantics. The sole build owner must run Compose tests
and inspect 320/375/414/768dp, dark/light, large-font and Fold layouts before
claiming visual acceptance. The critique above is a design review, not proof of
rendered or device results.
