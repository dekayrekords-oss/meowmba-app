# Preview 176 — conversation audit and action reliability

Owner request: September 25, 2026. Base: preview175 / `9df2bd8` on `feature/meow-flow-core`.
Version: 1.5.10 / code176. This is a local preview, not a Play release or phone installation.

## What the available phone history actually showed

The installed Fold is preview173, non-debuggable. Its visible current conversation was read
through the normal UI back to the oldest reachable messages. No private app storage was
bypassed, no chat messages sent, and no calendar records changed. Raw UI captures contain
personal information and are kept in a private temporary audit directory, not this repository.

- Repeated assistant output was literally `User Safety: safe`. That is classifier-protocol
  output, not a permission question. NVIDIA's content-safety model template uses that format;
  the exact upstream model selected by the phone's router was not recoverable from the UI.
  See the [official template](https://huggingface.co/nvidia/Nemotron-3.5-Content-Safety/blob/d920080e6b314a1f4c7106bef8df8e20e6a68a22/chat_template.jinja).
- Keyboard clicks, clanging, door/car sounds, music and other bracketed/asterisk sound labels
  became user chat turns. Replies inferred activities and intentions from noise. Coughs and
  sighs sometimes prompted long advice or provider errors instead of remaining observations.
- Music/streaming and navigation were described as already running without matching success
  receipts. Opening Maps is not proof of active turn-by-turn navigation.
- A QR/manual request containing “look up” was misrouted to the robot's neck command.
- A tomorrow-event reminder remained visible in Flow. The original cancellation utterance
  was NOT found in the available conversation; it may belong to another conversation.
- Raw coordinates and emoji appeared in assistant replies. Repeated upstream routing errors
  reported no tokens in 20 seconds; provider availability/credentials are separate unresolved
  runtime dependencies, not repaired by claiming successful actions.

## Implemented behavior

- Known classifier-only model IDs are excluded from chat choices. Classifier-looking streamed
  prefixes are withheld; a classifier final yields a configuration error, not a spoken reply.
  This preserves ordinary chat refusals. Unknown router-selected classifier models can still
  fail, but those failures are no longer presented as conversational answers.
- Whole non-speech ASR annotations are not user requests. Human cough/sigh/laugh and similar
  labels remain short-lived typed observations. Raw microphone loudness is never evidence of
  a human vocalization. This is annotation filtering, not a new acoustic source classifier.
  Old sound labels in history must not be treated as requests or evidence of diagnoses/danger.
- Driving or media playback does not itself authorize routine speech. Explicit announcements
  and urgent alerts can speak (subject to mode/mute); direct voice replies remain possible.
  Typed replies stay silent unless Always is selected. Background routine outputs have an
  explicit purpose rather than inheriting a recent spoken-turn window.
- “Be quiet” stops current speech and persists until a deliberate wake phrase, mic tap or new
  typed message. It also suppresses late background-tool speech and follow-up listening.
- Spoken copies remove emoji, decimal GPS pairs, labeled coordinates and DMS coordinates;
  original text remains visible. Location replies prefer verified platform-geocoder road/
  address text and explicitly admit when no road name is available. No invented cross street.
- “Show avatar” in the swipe-down menu opens the avatar directly, separate from configuration.
  Typed `open …` offers fuzzy completions from installed-app labels; selecting fills the draft
  and does not launch until Send.
- Explicit event cancellations route to `calendar.cancel_event`. Android calendar read/write
  access and an approval card are required. Only one uniquely matching occurrence is changed;
  ambiguous/read-only/no-match cases return a failure without guessing. Recurring events get
  a canceled-occurrence exception, never whole-series deletion. Provider status is checked
  before success; its matching calendar Flow item is dropped. Canceled provider rows are no
  longer imported. An unrelated standalone reminder/routine is not silently deleted.
- Music requests route to real phone media controls or Android music-search intents. Named
  installed apps and active audio players are used; unrelated video sessions aren't selected
  for a music request. Receipts distinguish “request sent” from observed playback beginning;
  they do not pretend the exact requested track is verified. Sign-in, notification access and
  the receiving player's search/play support remain external requirements.
- “Look up the manual” no longer means tilt the robot's head. Cozmo and LOOI remain separate.
  Navigation receipts describe a Maps handoff, not verified turn-by-turn guidance. Unsupported
  saved skills report no action performed, not “Running.” The prompt does not offer automatic
  region-pack installation while no such tool exists.
  A stationary phone jolt no longer starts driving crash assessment; strict SAFE/DANGER
  prefixes replace keyword matching that treated “no crash scene” as danger.

The TypeSafe skill informed the separation of observations, judgments and code-owned actions;
the deployed DecisionEngine/Laya/SemIf stack and provider choices were not replaced.

## Explicitly not complete

- Automatic nearby map-pack download and verified nearest cross-street/highway graph lookup:
  adding public Overpass requests would disclose the phone's current area. That implementation
  was blocked pending owner approval; it was not added. Existing file-import behavior remains.
- The visible stale event was not directly canceled during this audit: its original request/
  exact intended target was not verified. The new tool handles a fresh explicit cancellation.
- “Full app use” does not bypass Android permissions, accessibility consent, logins, app APIs,
  payment/message approval or missing integrations. Unsupported promises are not capabilities.
- Actual phone playback, synced calendar recurrence, real-world driving/noise, physical robots
  and phone update remain hardware acceptance tests. Emulator/JVM tests cannot establish them.

## Validation and delivery

Final full JVM suite: 2,997 tests, zero failures/errors, five skips. The six calendar tests
include SQL NULL preservation; five phone-action receipt tests cover real intent issuance,
map-pin fallback and missing handlers. Four emulator UI tests passed, and the menu-to-avatar
transition was visually verified. Final lint: zero errors, 197 warnings and 12 hints.
Daily/debug/test APK assemblies pass. The installable APK has the existing preview certificate,
16 KB alignment and a passing 161-entry runtime comparison with preview175. Optimized emulator
cold launch: 3,132 ms, remains foreground, no new crash-buffer entries. Final artifact identity
and source commit are recorded in the Os workspace `work/behavior-176/README.md`.
No phone install, source push or public source publication is authorized by this work.
