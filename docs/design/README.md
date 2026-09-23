# Design references — Meow Flow and Meow's Desk

These are the owner-approved UI references. **Build them to look exactly like
this.** The HTML demos are the source of truth for layout, spacing, type, colour
and motion; the PNGs are fixed snapshots of each state for side-by-side checks.

| File | What it is |
|---|---|
| `meow-flow.html` | Meow Flow home mode, full interactive demo (simulated day, settings, themes, interruptions, Maps notification). Open in Chrome. |
| `pro-desk-avatar.html` | Meow's Desk avatar with the micro cat (tap objects). |
| `screens/flow-*.png`, `screens/desk-*.png` | Reference screenshots at 2× (phone screen is 412 × 960 CSS px). |

Plans: `../MEOW_FLOW_AND_DESK_MASTER_PLAN.md` (phases, gates, ownership),
`../MEOW_FLOW_PASS_DOWN.md` (behaviour), `../PRO_DESK_AVATAR_PASS_DOWN.md` (Desk).

Demo content (tester Sam, bills, stores, shows) is example data. The demo loads
Google Fonts from the web for convenience; the app must bundle fonts (below).

## How to regenerate the screenshots

```bash
# from docs/design; injects a phone-only style and a start state, then screenshots at 2x
G="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PHONE='<style>.side{display:none!important}body{padding:0!important}.wrap{display:block!important;margin:0!important}.holder{width:432px!important;height:980px!important}.frame{transform:none!important}</style>'
sed "s|render(1); start();|stop(); render(4);|; s|</style>|</style>$PHONE|" meow-flow.html > /tmp/t.html
"$G" --headless=new --force-device-scale-factor=2 --window-size=432,980 --virtual-time-budget=5000 --screenshot=out.png file:///tmp/t.html
```
States by index: 0 driving in · 1 deep work · 2 email urgent · 3 before call ·
4 interrupt (store) · 5 working late · 6 work wraps (night) · 7 winding down.
Hooks: `openSettings()`, `SET.theme = "linen"`, `SET.level = 3` (held interrupt),
`document.querySelector("#where [data-w=maps]").click()` (notification path).

## Units

Demo phone viewport = 412 × 960 CSS px. **1 CSS px = 1 dp; font px = sp.**
Line heights are given in sp (set `lineHeight` explicitly in Compose).

## Fonts (bundle, no network)

| Role | Family | Licence |
|---|---|---|
| Serif (headlines, "because", flow item titles, ask hint, settings title) | Source Serif 4 — 400, 400 italic, 600 | OFL |
| Sans (dateline, labels, body, buttons) | Archivo — 400, 500, 600, 700, 800 | OFL |

Put under `res/font/flow_serif_*.ttf`, `flow_sans_*.ttf`. Fallback: system serif/sans.

## Colour tokens

Every colour on the Flow screen comes from these six tokens (+ derived on-accent).

| Theme | Look | paper | ink | ink2 | ink3 | rule | accent |
|---|---|---|---|---|---|---|---|
| Paper | day | #F2EEE5 | #1C1B19 | #56524A | #8C877C | #D5CEBF | #C23B16 |
| Paper | night | #1E1C19 | #EDE6D6 | #B7AF9F | #8A8376 | #3A362F | #E8794F |
| Linen | day | #F4F1EA | #16233B | #4A5568 | #8791A3 | #D9D4C8 | #1F4FBF |
| Linen | night | #131A26 | #E6E9F0 | #AEB6C6 | #7D879A | #2B3445 | #86A6FF |
| Stone | day | #EEEFEA | #1B211C | #4E574F | #8A928A | #D3D7CF | #2F7A3E |
| Stone | night | #171B18 | #E4E9E2 | #AAB3AA | #7E877E | #2F3630 | #7CCB8A |
| Sand | day | #F3ECE2 | #231A20 | #5A4C55 | #948690 | #DCD0C4 | #8A2E5C |
| Sand | night | #1D171B | #EFE5EA | #BBAAB4 | #8C7C86 | #3A2F36 | #E08AB4 |
| White | day | #FAFAF7 | #111111 | #4D4D4D | #8A8A8A | #E2E2DC | #B54708 |
| White | night | #141414 | #F0F0EC | #B5B5B0 | #80807C | #2E2E2C | #F2A93B |
| Graphite | day | #1B1B1A | #ECECEA | #B3B3AF | #80807C | #343432 | #F2A93B |
| Graphite | night | #121211 | #E6E6E3 | #ABABA7 | #7A7A76 | #2C2C2A | #F2A93B |

- User accent overrides `accent` in both looks.
- `onAccent` = whichever of `#161513` / `#FFF8F0` has the **higher contrast ratio** against the accent (never a fixed luminance cut-off; Paper-night #E8794F needs dark text).
- Theme change animates colours over 900 ms.
- The Flow screen never uses the app's Material colour scheme, gradients, glows,
  shadows (except the system notification), pill chips or category colours.

## Layout (412 × 960 reference)

| Element | Spec |
|---|---|
| Status bar | height 40; side padding 28; 14 sp / 700 sans |
| Stream (content column) | left 28, right 26, top 52, bottom 112; vertical column; main item at top, "also for you" pinned to bottom (flexible space between) |
| **Dateline** line 1 | 12 sp / 600 sans, UPPERCASE, letter-spacing 0.9 sp, line height 20; date in `ink`; "Adjust" right-aligned, 700, underlined (offset 3) |
| Dateline line 2 (signals) | same style in `ink2`; separator " / " in `ink3` with 5 dp side margins; **single line**: shrink font by 0.5 sp steps to min 10 sp (letter-spacing 0.5), then ellipsize; newest signal in `accent` with 2 dp accent underline |
| Dateline rule | padding-bottom 12, then 1.5 dp `ink` rule |
| Main — kind label | margin-top 30; 12 sp / 800 sans, UPPERCASE, letter-spacing 1.2; `ink2` (urgent → `accent`) |
| Main — headline | margin-top 8; serif 31 sp / 600, line height 37, letter-spacing −0.4; `ink` |
| Main — because | margin-top 12; serif italic 17 sp, line height 25; `ink2` |
| Main — actions | margin-top 20; gap 20; primary: height 46, padding h 20, radius 4, fill `ink`, text `paper` 15 sp / 700; secondary: text link 15 sp / 600, underline 1 dp, offset 4 |
| Also-for-you header | 12 sp / 800 UPPERCASE letter-spacing 1.2 `ink2`; 8 padding then 1 dp `rule` |
| Flow item | padding v 13; bottom 1 dp `rule`; title serif 18 sp / 600 lh 24; detail 13.5 sp lh 19 `ink2`; action right-aligned 14 sp / 700 + " →"; max 3 items |
| Held label (in item) | 11.5 sp / 800 UPPERCASE letter-spacing 1, `accent`, margin-bottom 3 |
| Leader-line dot | 7 dp circle `ink`, 22 dp left of item text, vertically centred on title first line |
| Ask line | left 28, right 26, bottom 34, height 56; 1.5 dp `ink` top rule; hint serif italic 17 sp `ink2`; mic: 46 dp circle, 1.5 dp `ink` border, mic glyph 18×22 stroke 1.8 |
| Evening | "Also for you" header text becomes "If you feel like it" |

### Leader lines (Canvas)

- Stroke 1 dp `ink3`; the newest source's line 1.4 dp `accent`.
- Path per (source signal → item): down 8 from signal bottom (x = signal left + min(14, width/2)),
  corner radius 8 into a horizontal run at +16, to a lane at x = 11 + 4·signalIndex,
  down the lane, corner radius 6, right into the item dot / kind label.
- On snapshot change: each line draws in over 800 ms (staggered 120 ms per item,
  80 ms per source), and one 2.4 dp `ink` dot travels the path once (900 ms).
  Then **static** — no looping animation.

### Interruption band

| Part | Spec |
|---|---|
| Container | full width, from top of screen; fill `accent`, text `onAccent`; padding 52 top (under status bar), 28 sides, 22 bottom; slides down 450 ms, cubic-bezier(.2,.9,.2,1) |
| Kind | 12 sp / 800 UPPERCASE, 80 % opacity, e.g. "STOP HERE? · WORTH IT" |
| Headline | serif 27 sp / 600, lh 33, letter-spacing −0.3 |
| Why | 15 sp lh 22, 92 % opacity |
| Voice caption (when voice on) | serif italic 15 sp; left bar 2 dp; three 3 × 10 dp bars pulsing only while speaking |
| Distance | 13 sp / 700; dotted 3 dp track, 9 dp position dot, 2 × 15 end tick; "600 ft … store" counts down |
| Actions | primary fill `onAccent` text `accent`; secondary underlined `onAccent` |
| Effect on content | stream top moves to band height + 14 (450 ms); while pushed: main kind margin 18, headline 25/31, hide "because" and item details |
| Status bar | while the band is showing, status-bar text/icons use `onAccent` (set `isAppearanceLightStatusBars` accordingly) |
| Resolve | replace why with confirmation line, remove distance/actions, hold 2.6 s, slide up |

### Adjust (settings) sheet

Bottom sheet over 35 % scrim; `paper` background; 1.5 dp `ink` top rule; padding
18/28/30; max height 86 %; title serif 26 sp / 600 with "Done" link. Groups
separated by 1 dp `rule` (margin 20, padding 12); group labels 12 sp / 800
UPPERCASE `ink2`. Segmented controls: 1.5 dp `ink` border, radius 4, selected =
`ink` fill + `paper` text, 13 sp / 600, no wrap. Toggles 46 × 26 outline style.
Theme swatches 52 × 52 radius 6 (paper colour + 12 dp accent dot), selected = 2 dp
`ink` outline offset 2. Groups: Colour · Interruptions · How it reaches you ·
Thinking · Work hours (see `screens/flow-11`, `flow-12`).

### Motion rules

- Item enters: fade + 8 dp rise, 600 ms, stagger 100 ms. Leaves: fade + 10 dp up, 300 ms.
- Handled main item → next item moves up and becomes main.
- Everything is still between changes. Respect reduced motion (no rise/slide; instant).

## Accessibility

Single focus node per item (title + detail + action); order: dateline → main →
actions → items → ask. Minimum 48 dp touch targets (text links get padding).
Contrast (WCAG, measured): `ink` ≥ 13.7:1 and `ink2` ≥ 6.5:1 on `paper` in every theme; `accent` on `paper` ≥ 4.6:1; `onAccent` on `accent` ≥ 5.0:1 with the rule above. `ink3` is only 2.8–4.8:1, so use it **only** for decorative marks (separators, leader lines, inactive toggle knob) — never for text a user must read.

## Meow's Desk

See `../PRO_DESK_AVATAR_PASS_DOWN.md` for the scene, object bindings and cat
motion, and `pro-desk-avatar.html` for exact artwork (SVG coordinates in a
412 × 960 viewBox can be ported 1:1 to Compose `Canvas` in dp). The Desk keeps
the app's dark companion palette and handwriting on in-world paper; its bottom
sheet uses the app's existing sheet style. No Care control.
