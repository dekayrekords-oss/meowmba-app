# Build 114 — make Brain usable and show measured activity

The user reported that the Brain section did nothing. Inspection found a passive
full-screen monitor that paused the avatar, hid its interaction controls and did
not explain Classic mode, Shadow authority or missing computer pairing well.
Its old 18 bars were decorative values derived from aggregate activity and step
count, with a nonzero minimum. They were not individual synapse measurements.

## Changes and implementation

- `NeuralInfoPanel` is now a compact inset-aware bottom sheet in portrait and a
  right-side panel in landscape. The avatar/background/props share a smaller
  viewport beside it. Brain no longer pauses visible avatar rendering. Opaque
  menus, onboarding, lifecycle and thermal restrictions still apply. The body
  stage stays disabled while Brain is open; robot policy is unchanged.
- Real measured aggregate mean/peak/active-unit values replace decorative bars.
  MaleCNS shows named feeding/escape/grooming/wing population rates. Measured
  zero remains zero; unknown, stopped, stale or fallback data is unavailable.
  Details explain the limits of aggregates versus individual synapses. There
  is no new polling loop, rendering timer or automatic sensor activation.
- Clear states explain Classic/off, Shadow/observing, Neural/control, cooling,
  missing pairing and remote fallback. Opening Brain takes a fresh controller
  and pairing snapshot without changing the saved neural mode or substrate.
- Explicit Start local brain selects RATE + NEURAL and persists both through
  the existing setters. It is an offline alternative, not MaleCNS. Healthy
  Shadow users can explicitly enable control while preserving their selected
  substrate. Neither happens simply by opening the panel.
- Pet and Feed route through existing touch/affect/food/care paths. Notices
  confirm submission, not a claimed neural response. Current governor tier,
  mode, fallback and remote freshness/pairing guard the callbacks themselves,
  even before the UI's next snapshot. Static-background feeding keeps Brain
  open by avoiding the usual explicit-animation panel dismissal.
- Settings opens directly to Neural / Lumen. Pair computer opens Connections
  & memory and the existing secure pairing workflow. Neural settings provides
  Open Brain activity and explains why the remote brain pauses under settings.
  No pairing credentials, certificate validation, bridge code or worker code
  were changed. Camera/mic choices, care mode, avatar, scale and scenes remain.

## Lessons and troubleshooting for Meow

Start with the status, not whether a decorative chart moves. Classic deliberately
never steps the neural core; Shadow measures it but Classic still controls the
avatar. A selected MaleCNS substrate alone does not establish a connection. It
needs the saved certificate-pinned Desktop Bridge pairing, a reachable Mac
helper and the Meow worker. Use the existing secure pairing flow; never bypass
certificate or HMAC checks to make a status turn green. Local mode can be tried
without any of those connections, but is a small synthetic network.

`faceModeActive` also owns policies outside rendering. Do not globally remove
Brain from it. The separate `avatarSceneVisible` predicate permits the partial
overlay to keep rendering while retaining the original bare-face policy and
the physical body pause. Both background and avatar must be resized together,
or habitat prop contact coordinates will diverge from the visible table.

Do not call a default user-requested animation from an overlay action unless
the panel should close. `applyHabitatAction(..., revealAvatar = false)` retains
the monitor while feeding on static backgrounds. UI snapshots refresh on the
existing 1 Hz cadence; guards also consult the current governor and controller
at click time, preventing stale enabled controls from bypassing cooling.

## Validation and delivery

Focused native UI tests cover compact/landscape layout, actual-zero versus
unknown values, explicit callbacks, mode states, pairing/fallback, touch-through
outside the sheet and details. VM tests cover actual public affection/care/
habitat signals, persistence and privacy, thermal transitions, static feeding,
mode authority and settings routes. Existing neural core and remote transport
tests cover the underlying event/step/readiness paths. Final results and APK
identity will be recorded after assembly in `BUILD_114_VERIFICATION.json`.

Build 114 is debug-only; release remains unchanged. Build on the private Meow
checkout with its preserved native and bundled assets, transfer APK deltas from
Build 113, then verify package, certificate and protected ZIP entries. Source
must remain local/private. The phone is currently disconnected and was last
verified running Build 112; Build 113 was prepared but not installed. Honor the
user's instruction to continue without it. Do not claim installation, secure
phone pairing, physical touch/GLES or sustained thermal acceptance without
device evidence. No robot hardware is available.
