# Connecting the personality to the LOOI body

**Date:** 2026-08-11
**Status:** Design approved, not yet implemented
**Scope:** Phase 1 of two. Phase 2 (no human in the loop) is a separate spec.

## Why

The companion invents its own face animations and performs them. The LOOI base
sits underneath doing almost nothing, because the personality speaks a body
language the hardware does not have.

`FaceMotorMap` emits six servos for an articulated puppet — head tilt, head pan,
ear left, ear right, tail, body. The real LOOI base is a wheeled phone cradle
with a neck, a headlight, and a brake. The translation layer between them
(`RobotManager.BleMotorController`, `RobotManager.kt:163`) shows the damage in
five lines:

- `SERVO_HEAD_TILT` → neck pitch — plausible, but wrong axis (see below)
- `SERVO_BODY` → a rotate pulse — "body at 100°" is not "spin 10"
- ears, tail, pan → `else -> Unit` — **four of six channels silently dropped**

Two consequences follow:

1. **Invented animations never reach the body.** `FaceMotorMap.mapAnimation()`
   recognises four hardcoded clip names. Every clip the model authors through
   `avatar.animate` — all fourteen channels, up to sixteen keyframes — is
   face-only.
2. **The base's real expressiveness is unreachable.** Forward drive, rotation
   with speed and duration, neck pitch, headlight, brake, and touch/cliff input
   are not addressable from a vocabulary made of servo angles.

## What already works, and is not being changed

- **Model-agnostic generation.** `ModelProvider` plus `OmniRoute` already
  classify a prompt, rank routes by tag and priority, and fall through cloud →
  LAN → on-device with the offline provider always last. Nothing in this design
  touches model routing.
- **Animations as authored data.** `CustomAnimationSpec` is a validated
  keyframe format: ≤16 frames, ≤10s, fourteen channels, every value clamped.
  `CustomAnimations.parse()` returns the spec plus a note for every clamp
  applied, and throws `SpecException` on structural defects.
- **The legacy servo path.** `MotorController.setServo(id, angle)` still serves
  the TCP/serial transport (`RobotManager.connectLooi`). It is the wrong
  abstraction for a wheeled base but it is not this design's problem, and it is
  left alone.

## Decisions

| Question | Decision |
|---|---|
| Order of work | LOOI motors first; no-human-in-loop second |
| How the body gets motion | Derive from face clips now; authored body channels later |
| Motion envelope | Desk by default; floor unlocked when the robot determines it is not confined |
| How confinement is determined | Opportunistically, from motion it was going to make anyway — never a probe for its own sake |

## Non-goals

- Changing model routing or the `avatar.animate` tool contract in phase 1.
- Refactoring `MotorController` or the TCP/serial transport.
- Decoding the `fed9` frame layout by guesswork. The design accommodates not
  knowing it (see `LooiSensorParser`).
- Any autonomous behaviour loop. The body follows clips; nothing new decides
  *when* to act. That is phase 2.

---

## Section A — The safety spine

Nothing may drive until sensing exists. In dependency order:

### A1. `LooiBleLink.onCharacteristicChanged`

`LooiBleLink` captures the `fed9` sensor characteristic (`LooiBleLink.kt:230`),
CCCD-subscribes it in the generic init loop, and polls it every 3s in the
keep-alive. It has **no `onCharacteristicChanged` override**, so every
notification is discarded. There is no sensor data in the app today.

Add the override; publish raw payloads on a `SharedFlow<ByteArray>`. This is the
prerequisite for everything else in this document.

### A2. `LooiSensorParser` (new, pure)

Bytes → `SensorEvent`: `Touch`, `Cliff(headingMask)`, `Tof(mm)`, `Unknown(raw)`.

The `fed9` layout is not known. `LooiBleProtocol` documents it as "notify:
touch/cliff; TOF/status partial". Therefore:

- `Unknown` is a **first-class result**, not an error path.
- The parser ships with a **capture mode** that logs raw frames with timestamps
  and the motion command in flight at the time, so the layout can be decoded
  from real robot data.
- Until the layout is decoded, `Unknown` is treated as danger (see A3).

### A3. `BodyProfile`

`DESK` or `FLOOR`.

- `DESK` never emits `driveSpeed()`. Neck, in-place turn, headlight and brake
  only.
- `FLOOR` permits the full vocabulary.
- **Default is `DESK`**, and `DESK` is the value used whenever anything is
  uncertain.

### A4. `ConfinementModel` (new, pure)

Consumes `(commandedHeading, SensorEvent)` pairs; holds decaying per-heading
evidence; outputs a profile and a confidence.

**Heading cannot be tracked yet, so the model does not use it.**
`LooiBleProtocol.rotateFrame(speedPercent, ticks)` exposes no direction
parameter — it was derived from a single sniffed rotation packet, and any
direction bit in `param1`/`param3`/`maskB` is unverified. Turn direction is
therefore neither commandable nor observable, which makes signed dead reckoning
impossible rather than merely imprecise.

The model instead accumulates **motion-window evidence**, which needs no
heading:

- A *motion window* is a contiguous stretch of commanded rotation or drive.
- A window that completes with no `Cliff` is one unit of clean evidence,
  weighted by its duration.
- Any `Cliff` zeroes the accumulated evidence and sets `DESK`.
- `FLOOR` unlocks only after clean evidence exceeds a threshold spanning
  several separate windows, so one lucky spin is never enough.
- Evidence decays over ten minutes; `Unknown` frames, link drop, pick-up, and
  app restart reset it to zero.

This is strictly weaker than per-sector evidence — it can conclude "not
confined" while a cliff sits in an untested direction. That is why `FLOOR`
requires several windows and why `Cliff` is absolute and immediate. Sector
logic becomes possible once turn direction is decoded, and is a natural
follow-up to the same capture work `LooiSensorParser` needs.

- Any `Cliff` → `DESK` immediately, and remember the heading.
- A full expressive rotation that samples every heading without a cliff →
  confidence rises.
- Sustained confidence above threshold → `FLOOR` unlocks.
- `Unknown` frame, link drop, or pick-up detected on the IMU → collapse to
  `DESK` and reset confidence.

It never commands motion. It only interprets motion that was going to happen.

### A5. Interlock

A cliff during motion calls `stopAll()` before any other reaction. Brake first,
interpret second.

### Hardware constraints that shape the mapping

- **`driveSpeed()` is forward-only** (`0f..1f`); there is no reverse channel.
  "Back away" can only be a 180° turn followed by drive, which reads as a
  different gesture. The mapper must know this rather than pretend otherwise.
- **Neck is already safety-clamped** in `LooiBleLink` (`NECK_UP_SAFE = 0x18`,
  `NECK_CENTER = 0x40`, `NECK_DOWN = 0xF0`), so neck motion is inherently safe
  and available in both profiles.

---

## Section B — Mapper and scheduler

### B1. The axis correction

`FaceMotorMap` maps `SERVO_HEAD_TILT` → `setNeckPitch()`. But `head_tilt` in the
animation format is **roll** — "-25 to 25 deg; negative=left", a head cocked
sideways. `fed1` is **pitch** — up and down. They are different axes. Today a
cocked head makes the base nod.

The channel that means pitch is `gaze_up`.

### B2. `BodyMotionMap` (new, pure)

`(CustomAnimationSpec, BodyProfile) → List<BodyCue>`, where `BodyCue` is
`(atMs, BodyVerb)` and `BodyVerb` is one of `Neck(pitch)`,
`Turn(speedPercent, ticks, headingSign)`, `Drive(speed)`, `Light(on)`, `Brake`.

| Face channel | Body verb | Rationale |
|---|---|---|
| `gaze_up` 0..1 | `Neck(pitch)` | Looking up *is* neck pitch — the correct primary mapping |
| `head_tilt` ±25° | single short `Turn` pulse | Roll has no actuator; a slight swivel reads as a cocked head |
| `body_bounce` ±1 | `DESK`: `Neck` bob · `FLOOR`: short forward `Drive` pulse | The only verb that differs by profile |
| `tail_wag` ±1 | single short `Turn` pulse per wag extreme | Reads as a wiggle |
| `glow` / `sparkle` | `Light(on)` | Bright or sparkling → headlight on |
| clip end | `Brake` | Always, unconditionally |
| `ear_perk`, `ear_droop` | *(unmapped)* | No actuator exists. Left unmapped rather than faked |

**On turn direction.** An earlier draft of this design paired each `Turn` with
an opposite one so the gesture would net to zero heading. That is not
implementable today: `rotateFrame` has no direction parameter, so both pulses
would rotate the same way and the pair would double the drift instead of
cancelling it. `BodyVerb.Turn` still carries a `sign` field, and
`BodyMotionMap` still populates it from the source channel, because the
information is real and free to compute — but `LooiBleBodyOutput` ignores it
and documents why. When direction is decoded, the pairing becomes a change to
one class.

Rotation is in-place, so uncontrolled direction is a *heading* problem, never a
*falling* problem. Turn pulses remain safe in both profiles.

The profile is an **input to the pure function**, so "DESK never drives" is a
property that can be tested exhaustively rather than a promise made in prose.

### B3. `BodyScheduler` (new)

Walks the cue list on the same clock and from the same start instant as the
face, and owns three concerns the mapper does not:

1. **Write budget.** Coalesce to a ~60ms minimum write interval so BLE is not
   flooded. The ≤16-keyframe cap makes cues naturally sparse; emit at keyframes
   plus only those midpoints where a channel moves materially.
2. **One motion verb in flight.** `Turn` and `Drive` carry their own duration in
   ticks; overlapping them stacks unpredictable motion. `Neck` and `Light` are
   state writes and may interleave freely.
3. **Cancellation.** Hooks the existing `customAnimJob?.cancel()`
   (`CompanionViewModel.kt:5027`) and calls `stopAll()` immediately.

### B4. The body never blocks the face

A BLE failure mid-clip stops scheduling and marks the link down. The face plays
through unaffected. This asymmetry is deliberate and gets its own test.

---

## Section C — Wiring, failure, testing

### C1. Insertion point

`playCustomAnimation()` (`CompanionViewModel.kt:5026`) is already the sole
funnel — the animate tool, the describe tool, `IdleAnimationDirector`, and three
other call sites all route through it. The body scheduler starts there and
cancels with the same job.

### C2. Output vocabulary

New `BodyOutput` interface — `neck`, `turn`, `drive`, `light`, `brake` — with
two implementations: `LooiBleBodyOutput(link)` and `SimulatedBodyOutput(log)`.
This mirrors the honest-fallback idiom `SimulatedMotorController` already
establishes.

`FaceMotorMap.mapAnimation()` is deleted. It is already dead in production — its
only caller is `PhaseGTest.kt:85`, which asserts each of its four hardcoded clip
names yields servos. That assertion is removed with it, and the coverage it
pretended to give is replaced by the `BodyMotionMap` table tests in C4.

`FaceMotorMap.mapMood()` stays but changes signature: it returns
resting `BodyVerb`s instead of `Pair<Map<Int, Float>, String>`. That is a
breaking change to exactly one call site — `CompanionViewModel.kt:1924`, where
the destructured `(servos, expression)` currently feeds
`motorController.setServo`/`setExpression`. That site moves to `BodyOutput`.
`FaceMotorMap.balanceTilt()` and `panForFace()` are untouched; they serve the
IMU and face-tracking paths, which are outside this design.

### C3. Failure modes

| Condition | Reaction |
|---|---|
| BLE drops mid-clip | Stop scheduling, mark link down, **face plays through** |
| Cliff during motion | `stopAll()` first, then `DESK`, then record the heading |
| Unknown `fed9` frame | `DESK`, log raw bytes for later decoding |
| Clip cancelled or superseded | `stopAll()` |
| App backgrounded | `stopAll()` and stop the scheduler |
| Base picked up (IMU) | `DESK`, reset confinement confidence |

Backgrounding is the one most likely to bite: a scheduler coroutine that
outlives the foreground leaves a robot driving near an edge with nobody
watching. `CLAUDE.md` carries this exact bug class as its worked example.

### C4. Testing, in order of value

- **`BodyMotionMap`** — pure, table-driven. Load-bearing test: *`DESK` never
  yields a `Drive` cue*, asserted across every built-in clip plus a fuzz corpus.
- **`ConfinementModel`** — pure over event sequences: cliff → `DESK`; clean full
  rotation → confidence rises; `Unknown` → collapse.
- **`BodyScheduler`** — fake `BodyOutput`; assert verb order, timing, never two
  motion verbs in flight, brake on cancel.
- **Face independence** — `BodyOutput` throws on every call; assert the face
  timeline still completes.
- **`LooiSensorParser`** — `Unknown`-path tests only until capture mode yields
  real frames. No assertions are written against a format nobody has seen.

---

## Phase 2 — authored body channels

Add `drive`, `turn`, `neck`, `light` to `AnimFrame`. `CustomAnimations.parse`
clamps them and reports notes exactly as it does for existing channels.
`BodyMotionMap` prefers an authored channel over its derived guess, so
derivation becomes the default rather than the only option. Four lines in the
`avatar.animate` tool description. Clips saved before phase 2 are unaffected —
backward compatible by construction.

## Why this sets up the autonomy phase

The body produces something the face never could: **objective consequence**. A
clip that hit a cliff, got braked, or requested drive while in `DESK` profile is
measurably bad with no human judging it. Combined with the clamp notes and
`SpecException` that `CustomAnimations.parse` already returns, that is a real
self-critique signal — the substrate the no-human-in-loop phase needs.
