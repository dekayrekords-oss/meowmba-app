# Design note: Godot avatar prototype (idle + paw-lick)

## Goal & scope

This documents a prototype of the MEOWMBA avatar renderer built in Godot,
for a side-by-side visual/feel comparison against the existing Jetpack
Compose Canvas renderer. The prototype covers exactly two things: the idle
ambient behavior (blink/breathe/ear-twitch/gaze-drift/whisker-sway/mouth-wobble)
and the built-in "paw lick" clip. It's reachable as a second screen
(`GodotAvatarPreviewActivity`) inside the real app, launched from a debug
entry point on `CompanionScreen`, so both renderers can be viewed on the
same device back to back.

**Non-goals.** This is an evaluation artifact, not a migration. The existing
`AvatarRenderer.kt` / `CatAvatarStateMachine` / `CustomAnimation.kt` are
untouched and still drive the app's real avatar unmodified. Whether to
replace the whole avatar system with Godot is a separate, later decision —
this prototype only informs it. There is no Kotlin↔Godot signal bridge
(`GodotPlugin`) — building one is real-integration-scope work, not
prototype-scope work.

## Integration mechanism (verified, not assumed)

Verified against Godot's own docs for the installed version
(`docs.godotengine.org/en/stable/tutorials/platform/android/android_library.html`,
Godot 4.7.1.stable) and the actual `GodotHost.java` / `GodotFragment.java`
source at tag `4.7.1-stable` in `godotengine/godot` — not taken on faith
from the task brief's summary, which turned out to under-sell how simple
this is:

- Godot 4.7.1.stable publishes a ready-built Android library AAR straight to
  Maven Central: `org.godotengine:godot:4.7.1.stable` (packaging `aar`,
  "Godot Engine Android Library - Template Build"). No export step, no
  building the AAR ourselves — it's a plain Gradle dependency.
- `defaultConfig` needs an `aaptOptions.ignoreAssetsPattern` override so
  Gradle stops stripping the hidden directories Godot's project format uses
  under `assets/`.
- The hosting component must implement `GodotHost`
  (`org.godotengine.godot.GodotHost`). Reading the interface source directly:
  only `getActivity(): Activity` and `getGodot(): Godot` are actually
  abstract — every other member (`getCommandLine`, `onGodotSetupCompleted`,
  `getHostPlugins`, etc.) has a default no-op body, so a minimal host is
  genuinely small.
- The host hosts a `GodotFragment` inside a `FragmentContainerView` via a
  normal `supportFragmentManager` transaction. `GodotFragment` already
  forwards `onConfigurationChanged`/`onActivityResult`/
  `onRequestPermissionsResult`/lifecycle to the engine itself, so the host
  activity doesn't have to.
- `GodotFragment` is a real `androidx.fragment.app.Fragment`, so — as the
  task brief expected — Compose can host it via the standard `AndroidView`
  interop pattern. But the host needs `supportFragmentManager`, which means
  a `FragmentActivity`, and the app's single `MainActivity` is a plain
  `ComponentActivity`. Rather than change `MainActivity`'s base class for a
  prototype, this adds a **new, separate** `GodotAvatarPreviewActivity`
  extending `androidx.fragment.app.FragmentActivity`. `MainActivity` and
  `CompanionScreen` are untouched.
- Godot's docs describe two ways to ship project content on Android: (a)
  the Godot project lives directly, unsubfoldered, inside the host app's
  `assets/`, and the engine runs it as-is — this is the primary documented
  walkthrough; or (b) export a `.pck`/`.zip` elsewhere and point at it with
  `--main-pack` from `GodotHost#getCommandLine()`. This prototype uses (a):
  the app had no pre-existing `assets/` directory (checked — zero collision
  risk), and since the avatar is pure procedural vector drawing with zero
  imported textures/binary assets, there's nothing for Godot's import
  pipeline to do anyway — no export-template pipeline needed at all for
  this prototype.
- The manifest entry for the new activity needs
  `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"`
  and a fixed `android:screenOrientation="portrait"`, per the docs — Godot
  doesn't handle live resize/rotation itself.

## File layout & porting decisions

`app/src/main/assets/project.godot`, `avatar.tscn`, `avatar.gd` — a single
`Node2D` scene ("Avatar") with one script, doing pure procedural drawing in
`_draw()` (`draw_circle`, `draw_line`, `draw_colored_polygon`,
`draw_polyline`). This deliberately mirrors the existing Compose Canvas's
vector-line-art style rather than switching to sprites, both to keep the
comparison apples-to-apples and because it needs no import pipeline.

**Idle layers** were ported 1:1 in structure from `AvatarRenderer.kt`'s
`LaunchedEffect` loops and `CatAnimationRegistry`'s timing constants:
breathe (2600ms, 0.98↔1.02, ping-pong), blink (randomized 2200–5200ms
interval, with the original's 1-in-4 chance of a double-blink), ear twitch
(randomized 3000–8000ms interval, 1-in-3 chance of a second twitch), gaze
drift x/y (4800ms/3400ms linear ping-pong), whisker sway (1500ms linear
ping-pong), mouth wobble (1900ms ping-pong). The randomized layers became
GDScript coroutines using `await get_tree().create_timer(...).timeout`
inside `while true` loops — structurally the same delay-then-tween-repeat
shape as the Kotlin `LaunchedEffect`s. The deterministic infinite ones use
Godot's `Tween` with `set_loops()`.

**The paw-lick clip** was ported field-for-field from
`CustomAnimation.kt`'s `BUILT_INS["paw lick"]` — all 8 keyframes (0ms–1800ms,
driving `headTiltDeg`/`eyeOpen`/`whiskerFlutter`/`tongueOut`) — and sampled
at runtime in GDScript with the exact same smoothstep formula
(`u*u*(3-2u)`) `CustomAnimations.sample()` uses. This was deliberately kept
as plain data plus a small sampler function, rather than hand-authoring a
Godot `AnimationPlayer` `.tres` resource: this build machine has no display
(no X server / Xvfb), so there's no way to visually verify an
editor-authored timeline, whereas a data table + sampler is mechanically
transcribable and reviewable directly against the Kotlin source.

Since there's no signal bridge yet, the paw-lick clip auto-plays on a
4-second repeating timer, so it's visible for passive side-by-side viewing
without needing a trigger that has nowhere to come from in this prototype.

## Known simplifications (documented, not hidden)

- Eyes/pupils are sharp-cornered rotated rectangles, not rounded rects —
  Godot has no built-in rounded-rect draw primitive.
- The halo glow is a flat, static, translucent circle — no radial-gradient
  falloff, no pulse (there's no `LISTENING` state in this prototype to
  drive one).
- The original's 420ms gaze-follow smoothing tween (between the touch/drift
  target and the rendered pupil position) is collapsed away: there's no
  touch input here, so pupils track the idle drift value directly.
- Nothing neither clip ever touches was ported: happy-eye arcs, mic/camera
  privacy marks, dimmed/faceAlpha, sparkle particles, tail wag, body
  bounce, `SPEAKING` mouth mode, `THINKING` gaze-up bias.

## Verification status at time of writing

Godot 4.7.1.stable (current stable release) was downloaded directly
(official Linux x86_64 editor build) to validate the hand-written
`.gd`/`.tscn` content: a headless project scan
(`--headless --path . --editor --quit-after 3`) and a timed headless run
(`--headless --path .`) both completed with **zero parse or script
errors**. That confirms the project and script load and execute cleanly —
it does not confirm how the animation looks. This build machine has no X
server/Xvfb, so there was no way to render even a desktop preview frame,
and no Android device or emulator was attached, so the Android build's
visual result is unverified. See the final status report for exactly what
was and wasn't checked.
