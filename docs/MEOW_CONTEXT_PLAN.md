# Meow Context — always knowing the user's situation (plan, 2026-09-23)

Owner request (2026-09-23): "the app should remember everything the user tells it with full
context. and the app should always have the context of what the user wants like location,
driving, surrounds, conversations, noise level, everything a normal person with access to your
phone and constantly with you would be able to help with or remind you of."

Standing decisions that still apply: everything stays on the phone (D8); background location,
notification access and usage access are approved (D10); no Care in Flow/Desk; heat matters
(the phone must not run hot, see `docs/IDLE_HEAT_TRACE_2026-09-22.md` and the 158 heat finding).

## What exists today (honest baseline)

| Need | Today |
|---|---|
| Remember what the user said | Chat turns saved as low-importance "episodic" memory text, keyword recall. No place, activity or surroundings stored with it. |
| "I'm going to work right now" | Not understood as a trip. Not a Flow item. |
| Location | Last-known fix on demand (`core/nav/CurrentLocation`). Flow has a `place` slot nothing fills. No learned places. |
| Driving | `DrivingDetector`: GPS speed, only with Car mode on (default off), only while the app runs. |
| Commute / work trip | Not known. Work state comes from learned hours and app use only. |
| Noise / surroundings | Sound level exists only while the mic is already listening (`InputSoundLevel`). Not stored. |
| Who you are with / messages | Message senders via notification access (Flow "Reply to X"). |

## Design

### 1. Live context (always current, low power)

`core/context/live/LiveContext` — one `StateFlow<ContextNow>` the whole app reads:

- **Activity** — Google Activity Recognition *Transition* API (IN_VEHICLE, ON_BICYCLE,
  WALKING, RUNNING, STILL). Runs in Play services' sensor hub; near-zero battery; delivered to
  a `BroadcastReceiver` even when Meow is closed. Replaces Car-mode-only GPS for *knowing*
  you're driving; `DrivingDetector` keeps doing the drive card and crash check.
- **Location** — Fused Location with `PRIORITY_BALANCED_POWER_ACCURACY` via `PendingIntent`
  (background, no foreground service): every ~10 min when still, ~1 min while in a vehicle,
  switched by the activity transitions. Needs `ACCESS_BACKGROUND_LOCATION` (D10).
- **Places** — learned, not typed: a dwell of ≥ 10 min within ~150 m becomes a place.
  Labels: *home* = where the phone sleeps (most 00:00–05:00 dwell), *work* = most weekday
  09:00–17:00 dwell away from home; others named by reverse geocoding (street / business).
  The user can rename ("this is the gym").
- **Trips** — leaving a place + IN_VEHICLE = a trip; heading predicted from time of day and
  the usual next place (home→work on weekday mornings). "On your way to work" becomes a fact.
- **Surroundings** — sound level (dBFS → quiet / talking / loud) sampled only when the mic is
  already open for wake word or conversation (no new always-on mic: heat, privacy, and
  Android forbids background mic without a visible service); headphones / car Bluetooth;
  charging; screen on; Wi-Fi network name (strong home/work signal, no location needed);
  foreground app (usage access); weather; next calendar event.

### 2. Context journal (remember everything, with context)

Room table `life_event` (DB v12): `at, kind, text, lat, lon, place_id, place_label,
activity, speed_mph, noise, app, bluetooth, charging, weather, calendar_next, extras_json`.

- **Every user utterance** (typed or spoken, chat or Flow) is written with the full
  `ContextNow` stamp — "Tue 8:12 AM, at Home, still, quiet, on Wi-Fi 'Gilmore'".
- **Every context change** worth remembering: arrived/left a place, started/stopped driving,
  trip to work began/ended, first unlock of the day, went to bed (screen off at home at night).
- Kept on the phone indefinitely (user can clear); not sent anywhere.

Recall: questions about when/where ("when did I say I'd call Dana?", "where was I when I said
that?", "what time did I get to work yesterday?") retrieve journal rows by keyword + time
window and put 3–8 stamped lines into the chat prompt. Meow answers with time and place.

### 3. Understanding what the user says about their situation

`IntentionCapture` gains *situation statements* (rules first, Laya when present):

- Going: "I'm going to / heading to / leaving for / on my way to <place>" → a trip fact
  (destination resolved against learned places: work, home, "the store"), with time.
- Arrived / doing: "I'm at work", "just got home", "I'm in a meeting", "I'm driving".
- Plans: "I'll be home by 6", "back in 20 minutes" → expectation Meow can check against
  location later ("You said you'd be home by 6 — leaving now gets you there at 6:10").

These become evidence for Flow (place, route, activity) and journal rows.

### 4. Flow uses it

`FlowRuntime` gets `place`, `route` and `activity` from `LiveContext` (today: only the
in-app driving flag). Laya's state already has `place`, `activity`, `route`; they finally get
real values, so "pick up milk" can surface when you leave work, not at a fixed time.

## Heat budget (hard rules)

- No new always-on sensor. Activity transitions are hardware-batched; location is balanced
  power and interval-switched; the motion sensor already stops outside the avatar.
- Journal writes are small and batched; no per-second writes.
- Laya runs only on meaningful changes (see `LayaAssessor` material key), ≤ 6 items per run,
  pauses at thermal MODERATE.

## Slices

| # | Slice | Owner |
|---|---|---|
| C1 | `LiveContext` + activity transitions + background fused location + permissions UI | Claude (started) |
| C2 | Journal table + every utterance stamped + context-change rows | Claude |
| C3 | Place learning (dwell clustering, home/work labels, rename) | Claude |
| C4 | Situation statements ("going to work") + trips + Flow evidence | Claude |
| C5 | Journal recall in chat (when/where questions) | Claude |
| C6 | Sound level + Bluetooth/Wi-Fi/charging stamps | Claude |
| C7 | Physical acceptance on the phone: a real commute, heat check | Owner + integrator |

## Status (2026-09-23, Claude)

- **Laya** on the phone: `c197e39`. On-device check (SM-F971U): 26/26 agree with the reference,
  max shift 0.0158, ~1.05 s/question at 4 threads; 26 back-to-back questions raised skin 33→40 °C,
  so production uses 2 threads, ≤2 items per burst, a 3-minute quiet gap and stops at thermal LIGHT.
  Files: in-app Wi-Fi download (Adjust › Thinking) or adb push to
  `/sdcard/Android/data/com.example.meowmba/files/models/laya/v1/` (then `chmod 777` the created dirs).
- **C1–C2** done: `c02fd55`. Room v12 (`life_events`, `location_samples`), migration tested 10→12.
- Backup note for the integrator: `UserDataBackupStore` picks up the new tables automatically, but its
  100,000-row cap will eventually be reached by the journal (years) — consider a per-table cap or
  exporting `life_events` in chunks. `location_samples` is pruned to 30 days.
- Not installed: every build since `674b8f9` is for Codex to number (161+) and install.
- Next: C3 places (dwell clustering, Home/Work, reverse-geocoded names, arrive/leave, parking map),
  C4 situation statements, C6 surroundings (Bluetooth/DND/call state), then a real-day heat check.
