# Meow Flow — design and decision-engine pass-down (not implemented)

Owner decision, 2026-09-22: Meow Flow becomes a selectable home mode — a
context-driven surface, not an avatar. This is a design hand-off only; no app
code has changed. Per BUILD_MASTER.md the integrator (Codex) owns scheduling,
file ownership, builds and installs.

- Interactive demo: `docs/design/meow-flow.html` (open in a browser; step
  through a simulated day, open **Adjust** for settings).
  Published preview: https://claude.ai/artifact/4A63UCV3WPFFebPRgWLX15
- Situation catalogue (Codex): `~/.codex/.chatgpt-projects/g-p-6a92436f93688191b6e16e2b2f9bbef5/work/meow-flow-situations.md`
  (600 situations, 60 areas) and `meow-flow-three-directions.md` (intention model).
- Related: Meow's Desk avatar, `docs/PRO_DESK_AVATAR_PASS_DOWN.md` (separate mode).

## 1. What it is

Meow tracks **unfinished intentions** ("fix the rear brake light", "hand off the
heat fix", "reply to Sam") that it infers from what the user says and does —
conversation with Meow, notifications, calendar, current app, place, movement.
No lists or files are required. Each intention is carried across apps, places
and hours and brought forward at the useful moment. Work comes first; when work
is over, the same surface turns to the user's own interests (music, shows,
films, walks, places).

Every item is an **offer or prepared action**, never executed without the user's
go-ahead. Confirmed facts, predictions and unknowns stay distinct. v1 has no
store-stock sources, so Meow says a shop "usually carries" an item and never
claims it is in stock (owner decision D12 in the master plan). When nothing useful survives the evidence and
timing checks, it shows nothing new (catalogue #300).

## 2. Screen anatomy (keep as in the demo)

Owner said: keep the flow and layout; don't change it much.

| Part | Behaviour |
|---|---|
| Dateline | What Meow senses, newspaper-style: `TUE 22 SEPT · 9:40 AM / OFFICE / ANDROID STUDIO · MEOWMBA / FREE TILL NOON`. New signal underlined in accent. **Adjust** opens settings. |
| Main thought | Kind label, serif headline, italic "because" line, one solid button + one text link. |
| Also for you | Up to three smaller items, hairline rules, single action each. Evening header: "If you feel like it". |
| Leader lines | Thin ink lines from a dateline word, down the left margin, to the item it caused; a small dot travels when context changes. |
| Ask line | "Ask, or tell me what you're doing…" + mic. Replaces avatar controls. No Care mode. |
| Interruption | Full-width accent band slides from the top and **pushes** content down (content compacts). Shows why, a distance/time countdown, a spoken caption when voice is on, and two choices. Resolves with a confirmation line, then recedes. |
| In another app | Arrives as a system notification with actions (Add / Not today / Open Meow) and is spoken if voice is on. |
| Handling | Acting on or dismissing the main thought slides it away; the next item moves up. |

Visual language: paper + ink + one accent, serif for meaning, sans for metadata,
4 px radii, no gradients/glow/pill chips/colour-coded categories. All colours come
from the theme (section 4).

## 3. Settings (owner answers, 2026-09-22)

| Setting | Values | Default |
|---|---|---|
| Speak interruptions | on / off | on |
| Notify in other apps | on / off | on |
| How many a day | 0–10 | 3 |
| How often (min gap) | 15 m / 30 m / 1 h / 2 h | 1 h |
| How urgent may interrupt | Urgent / Worth it / Useful | Worth it |
| Work end | learned schedule + **context decides** (on/off) | on |
| Colour theme + accent | see §4 | Paper |
| After-work look | Auto / Day / Night | Auto |
| Ask Jev on hard cases | on / off (cloud, opt-in) | off |

An item that fails the interrupt gate is **not dropped**: it joins the flow with
a visible reason, e.g. `HELD: THIS IS "WORTH IT", AND YOU ONLY ALLOW "URGENT"`.

Work end (owner Q3): learn the schedule from observed days, but decide from
context. Schedule-only "done" is overruled by active work signals (typing in a
work app, office network). Evening begins when several signals agree (laptop
closed, left work place, no work apps for N minutes).

## 4. Colour themes

Each theme defines a day and an after-work palette:
`paper, ink, ink2, ink3, rule, accent`. The accent may be overridden by the user.
Text on the accent band picks whichever of dark/light has the higher contrast against the accent.

| Theme | Day paper / ink / accent | Night paper / ink / accent |
|---|---|---|
| Paper | #F2EEE5 / #1C1B19 / #C23B16 | #1E1C19 / #EDE6D6 / #E8794F |
| Linen | #F4F1EA / #16233B / #1F4FBF | #131A26 / #E6E9F0 / #86A6FF |
| Stone | #EEEFEA / #1B211C / #2F7A3E | #171B18 / #E4E9E2 / #7CCB8A |
| Sand | #F3ECE2 / #231A20 / #8A2E5C | #1D171B / #EFE5EA / #E08AB4 |
| White | #FAFAF7 / #111111 / #B54708 | #141414 / #F0F0EC / #F2A93B |
| Graphite | #1B1B1A / #ECECEA / #F2A93B | #121211 / #E6E6E3 / #F2A93B |

Full token values (ink2, ink3, rule) are in the demo's `THEMES` object.

## 5. Decision engine — Laya first, Jev optional

Meow Flow's intelligence is mostly many small, repeated, bounded judgments. That
is exactly what System One-style models do: typed questions over state, returning
calibrated probabilities, no generated text.

- **Laya** (Convai Innovations, Apache-2.0, open weights) — default engine, **on the phone only** (no server host, owner 09-22).
  Checkpoints on Hugging Face: `convaiinnovations/laya` (ModernBERT-large, 421M,
  512-token context, English), `convaiinnovations/laya-typed-decisions` (421M,
  1024 context), `laya-multilingual` (mmBERT-base, 322M). `pip install laya`;
  `Router(preload=True).predict(state, questions)`. Primitives: `choice`,
  `score`, `noul`. Reported ~33 ms/question on a T4; 0.766 vs Jev 0.727 on
  their typed-decisions benchmark (vendor numbers — re-measure).
- **Jev** (TypeSafe, hosted, `jev-latest` → `jev-1.13.0`, `POST /v1/systemone`,
  64k context) — optional escalation, off by default, sends trimmed state to
  TypeSafe. Codex already deployed a TypeSafe/Jev adapter on the AIOS host, but
  its handoff records the key as missing and live inference **not verified**.
  Laya removes that dependency and keeps personal context on the user's hardware.

Laya limits that shape the design (from its README):
- Base checkpoints are near chance zero-shot on typed decisions → use
  `laya-typed-decisions` and **calibrate on our own data** (one temperature per
  question type × option count).
- 512/1024-token context → state must be compact (see §5.2); use Jev only when
  a case truly needs long context.
- `score` is its weakest primitive → prefer `choice` for urgency levels and
  several `noul`s for independent conditions.
- High-cardinality choices degrade → shortlist in code first (`predict_shortlist`).
- `laya` is English-only; route other languages to `laya-multilingual`.

### 5.1 Pipeline (event-driven, never polling)

1. **Evidence** arrives: geofence enter/exit, activity change, notification
   posted, app to foreground, calendar alert, user utterance, laptop/desktop
   bridge event.
2. **Code** normalises it into compact state and fetches candidate intentions
   (open intentions store + any new utterance). Facts that are lookups stay in
   code: time, distance, ETA, opening hours, stock APIs, settings, caps.
3. **Laya** answers all independent questions over the same state in one batch.
4. **Code policy** combines answers with facts and the user's settings:
   interrupt ⇔ urgency ≥ user level ∧ P(chance passing) ≥ θ ∧ feasible (open,
   fits schedule; stock never asserted in v1) ∧ under daily cap ∧ past
   min gap ∧ channel allowed. Everything else is ranked into the flow.
   Changing a setting re-applies policy without re-running inference.
5. **Low confidence** (margin below threshold, too long for context, too many
   options): Jev if the user enabled it; otherwise hold, or ask one short
   question at a useful time (catalogue #299).
6. **Wording**: templated by code from the facts; optional phrasing by the local
   chat model. Text never introduces facts that were not in state.
7. **Learning**: log outcome per item (acted, dismissed, too late, too early,
   manually requested later). Refit calibration; adjust lead times and
   thresholds per situation type (catalogue §59); periodically fine-tune Laya.

### 5.2 Example: the parts-store interruption

State (compact JSON, well under 512 tokens):

```json
{
  "now": "Tue 16:58",
  "activity": "driving",
  "route": {"destination": "hardware store pickup", "eta_min": 6, "buffer_min": 12},
  "place_ahead": {"name": "Main Street Auto Parts", "category": "auto parts", "distance_ft": 600, "side": "right", "open": true},
  "intention": {"text": "fix the rear brake light", "source": "user said Sunday 19:14: 'the rear light is out again'", "part": "7443 bulb"},
  "stock": {"status": "unknown", "note": "no stock source in v1"}
}
```

Questions (one Laya batch):

```python
questions = {
  "store_fits": {"type": "noul",
    "instructions": "Would a shop like `place_ahead` normally sell the part needed for `intention`?"},
  "chance_passing": {"type": "noul",
    "instructions": "If the user does not act in the next 10 minutes, will this opportunity be gone for this trip?"},
  "urgency": {"type": "choice",
    "instructions": "How urgent is acting on `intention` right now, given `route` and `activity`?",
    "criteria": {
      "not_now": "Better handled another time; no loss from waiting.",
      "useful": "Nice to do now, small benefit, easily done later.",
      "worth_it": "A real chance that will pass soon and fits the schedule.",
      "urgent": "Delay causes a missed commitment, cost, or safety issue."}},
  "channel": {"type": "choice",
    "instructions": "Best way to reach the user now, given `activity`.",
    "criteria": {"speak_and_show": "Driving or hands busy.",
                 "show_only": "Looking at the phone, quiet setting.",
                 "hold": "User should not be disturbed now."}}
}
```

Code then checks: stock is never asserted (copy says "usually carries"), detour 4 min ≤
buffer, urgency ≥ user setting, cap/gap OK, voice/notify toggles → band or
system notification, spoken line if enabled. Matches catalogue #17, #22, #23,
#37, #592.

### 5.3 Intention capture (owner Q4 — life as a whole)

From each user utterance / message the user writes to Meow:
- `noul` "Does this express something the user needs or intends to do?"
- `choice` over a small code-built candidate set for the intention type
  (buy / fix / reply / attend / return / pick up / other) — shortlist first.
- Values (part numbers, names, dates) are **selected from candidates found in
  code** (select, don't generate), e.g. car model → bulb 7443 via a lookup.
Intentions close automatically when a confirmed change satisfies them
(catalogue #295, #600).

### 5.4 Where it runs

**Superseded 2026-09-22 — see `docs/MEOW_FLOW_AND_DESK_MASTER_PLAN.md` §6 and D8.**
On-phone Laya only (ONNX Runtime Android, community Q8/FP16 conversions, sharing
the ORT 1.27.0 runtime already shipped for voice). No Meow Server host (owner).
Jev optional and off by default; rules-only as last resort.

## 6. Permissions and privacy

Ask one at a time, in plain words, at first use: location (incl. background for
geofences), usage access (current app), notification access, calendar, media
sessions, microphone for voice. With none granted the mode still works from
calendar and what the user tells Meow. Show and allow deleting learned facts.
Private content never appears on cast/shared displays (catalogue §58).

## 7. Evaluation

Use Codex's 600 situations as replay cases. For each: did Meow understand the
evidence, choose a useful time, respect uncertainty, offer a feasible action,
use the right channel, learn from feedback, and stay quiet when appropriate.
Use the same set to fit Laya's temperatures and to compare Laya vs Jev on our
own data before trusting vendor benchmarks.

## 8. Suggested build order

1. Intentions store + event bus (no UI) with the settings model.
2. Laya on the phone (ONNX Runtime) + calibration on the 600 situations.
3. Meow Flow home mode UI exactly as the demo (themes, band, notification path).
4. Voice path for interruptions (existing TTS), notification channel.
5. Work-end detector (schedule prior + context).
6. On-device Laya spike (measure before shipping).
7. Optional Jev escalation.

## Owner answers

All answered 2026-09-22 — see `docs/MEOW_FLOW_AND_DESK_MASTER_PLAN.md` §15
(background location yes, notification/usage access yes, no Meow Server host,
no stock sources in v1, Flow sits beside Professional mode).
