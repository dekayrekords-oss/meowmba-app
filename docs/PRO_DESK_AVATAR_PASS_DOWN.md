# Pro Desk avatar — design pass-down (not implemented)

The owner approved "Meow's Desk" as a **separate, selectable home-screen avatar**
for work use. It does not replace Lumen or any existing avatar. This is a design
hand-off only; no app code has changed. The integrator owns scheduling, file
ownership and builds.

Interactive mockup: `docs/design/pro-desk-avatar.html` (open in a browser; tap
objects). Published preview: https://claude.ai/artifact/KEMDdtCU6aqQ1uMfizX4ih

## Concept

The home habitat becomes a small illustrated office. A micro cat (dark body,
mint #4DFF85 glossy lens eyes, same eye material as Lumen) stands on the desk.
Every object is one data surface; nothing appears as a card until tapped.

| Object | Data | Idle signal |
|---|---|---|
| Corkboard index card | Active project (Meowmba: Play review, tester days tally) | Tally marks fill per day |
| Pinned note | Latest build and test count | Updates on new build |
| Monitor | Last test run / CI | Blinking cursor; red text on failure |
| Sticky note on monitor | Next calendar event | Shows time + two words |
| Letter tray | Texts, email, missed calls | Envelope pile height + red count badge |
| Jar | Personal finance: balances, bills, returns, purchases | Handwritten note for the next due item |
| Desk lamp | AI suggestion waiting | Warm cone glow when an idea is queued |
| Window | Weather (existing provider) | Sky, moon/sun, one handwritten line |
| Wall clock | Time | Replaces the clock chip visually only if the owner agrees |

## Behaviour

- Tap object → cat's eyes glance toward it → cat walks to it (~1.1 s) → one
  speech line (≤ 70 chars) → bottom sheet rises (470 dp, 24 dp top radius,
  2 dp `outlineVariant` border, scrim 55 %). Close via X, scrim tap or back.
- Idle: every ~9 s the cat walks to a new spot and mentions one real item.
  Must obey the existing idle cadence rules (`AvatarFrameCadence`, shared frame
  phase) — walking is an active phase; standing is idle rate.
- Pro avatars show **no Care control and no Care mode** (owner decision,
  2026-09-22). Mic, camera and chat stay in their current components; the
  camera pill takes the Care chip's slot. The scene fills the habitat area only.
- Each object is a focusable button with a content description ("Letter tray:
  3 messages need you"). Reduced motion: no walk, sheet appears without slide.

## Visual rules

- App dark scheme and 2 dp ink (#172D34) contours; buttons keep the 3 dp ink base.
- Handwriting (Patrick Hand in the mockup) only on in-world paper; all sheet
  UI uses the app type scale. Replace with a bundled OFL hand font in the app.
- Wood #C98E52/#8E5E33, cork #B98A5C, paper #F4EEDB, sticky #FFE27A.
- Avoid: uniform card stacks, KPI tiles, icon badges on every row.

## Data sources (to wire)

Project/build/test status: existing Needle/Play status and build info. Messages,
calls, calendar, finance: existing business agents / connectors where present;
objects with no source show empty-state art (empty tray, empty jar) rather than
fake data. Mockup messages, calendar and money figures are placeholders.

## Suggested implementation shape

A new habitat/avatar entry alongside existing ones: a static background layer
(wall, desk, window frame) drawn once, a light dynamic layer for the few
changing objects, and the cat as its own layer — matching the retained-layer
approach in `IDLE_LOAD_138_PASS_DOWN.md`.
