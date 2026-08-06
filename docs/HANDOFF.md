# MEOWMBA — Live Handoff

> This file is the passdown for whoever (or whatever) picks the project up next.
> The "Recent builds" section is regenerated automatically by scripts/autobuild.sh
> after every commit; everything else is maintained by hand at each phase.

## What this app is
MEOWMBA (`com.example.meowmba`) is an on-device cat-companion voice assistant:
wake word ("hey meow" + variants), Silero VAD + speaker-verification gates,
on-device ASR/TTS, an agent runtime with tools, animated avatar, memory (Room +
markdown vault), desktop/LAN/USB bridge for computer control, and optional
native image generation. Kotlin + Jetpack Compose, minSdk 31, NDK 27, arm64 only.

## Working copies (keep them converged)
- PRIMARY (edit here): `/Users/bobrossjr/AndroidStudioProjects/Meowmba`
- origin: bare repo on `meow-nb:/home/claude/git/meowmba-app.git`
- remote worktree: `meow-nb:/home/claude/meowmba-app` (has JDK 21 + Android SDK, can build)
- local mirror: `~/meowmba-app`
- Device: Galaxy Z Fold **7** (serial RFCY61NJEVM); USB drops — fix with
  `adb kill-server && adb start-server`.
  DUAL-DISPLAY SCREENCAP GOTCHA: the Fold 7 has two displays; a bare
  `adb shell screencap` grabs the INNER display (state OFF) and returns a
  pure-black image that looks like a rendering bug. Always capture the cover
  display explicitly: `adb shell screencap -p -d 4630946872173396372`.

## Build & release
- Auto-build: `.git/hooks/post-commit` → `scripts/autobuild.sh` builds debug AND
  release APKs to `build-out/meowmba-{debug,release}-<sha>.apk` and notifies macOS.
- Release signing: `keystore.properties` + `meowmba-release.keystore` (gitignored,
  chmod 600). Release APK signature differs from debug — uninstall before switching.
- All native libs are built 16KB page-aligned (required by Android 15).
- Model assets (KWS/VAD/speaker) are gitignored; regenerate with
  `scripts/fetch_kws_assets.sh` on a fresh checkout before installing on device.

## Known quirks / pre-existing issues
- `ModelDownloadViewModelTest`: 3 JVM-only failures (unmocked android.content.Intent).
  Known since before the voice fixes; not a device problem. Everything else is green.
- Cleartext HTTP allowed app-wide for user-typed LAN endpoints (documented in
  `network_security_config.xml`); all fixed endpoints are HTTPS.
- `MeowmbaRuntimeService` is exported but double-gated: package allowlist AND
  same-signing-certificate check (fails closed).
- Cloud backup excludes database/sharedpref/files domains (privacy).

## Feature map (where things live)
- Voice pipeline: `core/audio/` (WakeWordService, SherpaKws, VadGate+WindowCarry,
  SpeakerVerifier). Wake variants: `KwsKeywords.kt` (authoritative; tokens must
  exist in the model vocab or they silently never fire).
- Agent: `core/agent/` (OmniRoute routing, runtime/, tools/). Providers: Ollama,
  OpenAI, BigPickle/OpenCode, on-device (LiteRt/MediaPipe/GeminiNano).
- Desktop control: `core/bridge/` + daemon `bridge/meowmba-bridge.py`.
- Memory: Room (`messages`, `memories`, `memories_fts`, `audit_log`) +
  `core/vault/` markdown notes (Obsidian-compatible, filesDir/vault).
- Animations: `ui/companion/CustomAnimation.kt` (BUILT_INS + invented clips,
  MIN_VISIBLE_MS=1200 stretch, smoothstep, clamps) + `IdleAnimationDirector`.
- Backgrounds: `core/background/BackgroundPrefs.kt` enum + `ui/companion/AnimatedBackgrounds.kt`.
- Settings persistence: `core/security/SecureStorageManager.kt`
  (saveBooleanPreference/getBooleanPreference for toggles; API keys encrypted).

## Tools window vs Agents window
Same screen. Both tiles open `AgentsScreen.kt` (roster, active tasks, tool list
with risk levels, audit trail).

## Recent builds
## Phase B features (2026-08-04)

- **BigPickle brain**: OpenCode Zen key in Settings → route `bigpickle`
  (https://opencode.ai/zen/v1, model `big-pickle`). "Default brain" selector
  forces one provider (others dropped except the always-kept on-device
  fallback). Route table rebuilds on Settings save (`rebuildOmniRoute`).
- **Ask-anything recall**: `messages_fts` FTS4 index (Room v5, data-preserving
  MIGRATION_4_5 + LIKE fallback). `memory.recall` tool searches chat history +
  memories + vault notes. Completed agent tasks are journaled into the vault
  ("Task log: …", appended, never overwritten).
- **SSH management**: Settings → Servers stores encrypted hosts; `ssh.run`
  tool runs allowlisted read-only commands only (ls/df/free/uptime/systemctl
  status/journalctl tail/docker ps), rejects shell metacharacters, TOFU host
  keys, 4KB output cap, 30s timeout. jsch = com.github.mwiede:jsch:0.2.21.

## Phase C features (2026-08-04)

- **IMU (MotionSense)**: avatar leans with the phone (±8° low-passed roll),
  shake (>2.5g, 1s debounce) plays a startled animation. RobotPoseSource
  interface is the hook for the future robot body.
- **Watch mode**: MediaProjection foreground service captures 1 frame / 8s,
  downscaled to ≤512px. Settings toggle triggers the one-time system consent;
  the persistent notification has a Stop action.
- **ContextDirector**: vision-describes watched frames (max 1 call/30s),
  classifies (game/document/error/browser/media), maps to reactive
  animations, and in Commentary mode speaks ≤1 comment per 5 min, never
  while the user is talking. Both toggles default OFF (except animations).

## Phase D features (2026-08-04)

- **Optional launcher**: MainActivity now declares HOME + DEFAULT intent-filter
  categories, so the app can be chosen as the device launcher in system
  settings. It stays a normal app unless the user opts in; back on the bare
  face does nothing destructive.
- **App drawer (swipe right on the face)**: alphabetical grid of all
  launchable apps with a live search bar up top (`ui/companion/AppDrawerScreen.kt`).
  Tap to launch, back or swipe to close.
- **Agent browser (swipe left on the face)**: built-in WebView with URL bar
  (`ui/companion/BrowserScreen.kt`). On every page load a `window.__meow`
  helper set is injected (`core/browser/WebViewBridge.kt`): open / title /
  links / click / type / scroll, plus screenshot-to-PNG.
- **web.browser tool** (`core/agent/tools/WebBrowserTool.kt`, MODERATE risk):
  lets Meow drive the browser by voice — open a URL, read the title/links,
  click elements, type text, scroll, and screenshot what it sees. Selectors
  support `tag=text` text matching; all inputs are JS-escaped.
- Gesture wiring in `AvatarRenderer`: horizontal drag >280 px triggers
  onSwipeLeft/onSwipeRight; short strokes stay petting. Overlays are mutually
  exclusive (`toggleAppDrawer` / `toggleBrowser` / `closeOverlays`) and are
  covered by the BackHandler in MainActivity.
- Tests: `PhaseDTest.kt` (JS escaping + DOM helper contract).

## Phase E features (2026-08-04)

- **Routines engine** (`core/routines/`): time-of-day or interval routines in
  a Room table (DB v6, additive MIGRATION_5_6). `RoutineScheduler` arms exact
  alarms (`setExactAndAllowWhileIdle`, guarded by `canScheduleExactAlarms`);
  `RoutineAlarmReceiver` reschedules and brings the app forward; `BootReceiver`
  re-arms after reboot. Default templates seed on first run: Morning brief
  (08:00, date + weather) and Memory follow-ups (20:00). Settings shows a
  Routines card with per-routine toggles.
- **Proactive memory follow-ups** (E2): the 20:00 routine scans memories for
  pending/todo tags (or matching keywords) and speaks the top 3.
- **Barge-in** (E3): after Meow finishes speaking it keeps listening 8 s
  (FOLLOW_UP mode) whenever the wake word is armed — you can cut in without
  re-saying the wake word. Setting toggle, default ON. Pure state machine in
  `CompanionViewModel.shouldEnterFollowUp`, timeout resumes wake standby.
- Tests: `PhaseETest.kt` (9 tests: trigger math + barge-in decision table).

## Phase F features (2026-08-04)

- **Desktop bridge control (F1)**: new daemon RPC methods `open_app`,
  `open_url`, `open_file`, `clipboard_get`, `clipboard_set`, `media`
  (play/pause/next/volume), `screenshot` (returns PNG + base64) in
  `bridge/meowmba-bridge.py` — cross-platform (macOS/Linux/Windows) with
  graceful "tool not installed" errors. Kotlin side: matching
  `DesktopBridgeManager` methods + `DesktopControlTool` (MODERATE, always
  confirms); screenshots are saved to filesDir/desktop_screenshots and
  described by the vision provider when available.
- **Phone system tools (F2)** in `PhoneTools.kt`: `phone.set_alarm` /
  `phone.set_timer` (system Clock intents), `phone.flashlight` (torch),
  `phone.dnd` (requests DND policy access once, opens the settings page),
  `phone.battery` (level + charging + saver readout).
- **UI automation (F3)**: opt-in `MeowAccessibilityService` (OFF until the
  user enables it in system accessibility settings) + `ui.automate` tool
  (tap/type/scroll/read, MODERATE + ALWAYS confirmation; honest failure when
  disabled). Settings has a UI-automation card linking to the system page.
- Tests: `PhaseFTest.kt` (off-by-default honesty + approval policy).

## Phase G features (2026-08-04)

- **Mood & personality engine (G1)** (`core/personality/MoodEngine.kt`):
  pure state machine — moods content/excited/sleepy/curious/startled/proud
  driven by wake greetings, praise in transcripts, shake, task success, and
  idle >30 min → sleepy. Mood biases idle animation choice, nudges TTS
  pitch/rate (TextToSpeechManager.setPitch/setSpeechRate), and enables
  LOOI-style idle chatter: time-aware one-liners, max once/hour, toggle in
  Settings ("Idle chatter", default ON).
- **Motor control API (G2)** (`core/robot/`): `MotorController` interface
  (setServo/setExpression) with `SimulatedMotorController` (logging) until
  real hardware arrives; `FaceMotorMap` maps every built-in animation, every
  mood, and the IMU tilt (balance demo: head counter-rotates, clamped ±15°)
  to servo commands. Wired in the VM: tilt and shake drive the simulated
  motors today.
- Tests: `PhaseGTest.kt` (11 tests: mood transitions, chatter rate-limit,
  TTS modulation, servo mapping, clamp behavior).

## UX overhaul + MeowLion brain (2026-08-05)

- **Gestures**: swipe UP = app menu/drawer, swipe RIGHT = chat window, swipe DOWN = tiles, swipe LEFT = agent browser.
- **Animation freeze**: the avatar stops animating while the chat sheet, app menu, or the full-screen (folded) browser is open. Ambient loops freeze at neutral values; blink/ear-twitch/idle clips are suspended and resume on close.
- **Foldable split view**: on the unfolded inner display (screen width >= 600dp) opening the browser shows it in the right half with the companion (avatar + chat) in the left half, so meow can drive the browser in real time while visible. Folded keeps the full-screen overlay.
- **MeowLion default brain**: the local Ollama box at http://192.168.1.163:11434 is now the out-of-the-box default brain (model `qwen3.6-memba:35b-a3b`, vision + tools). Route priority 10; on-device model remains the offline fallback. Settings still overrides endpoint/model/default-brain.

## Drawer favorites, face pins, live browser agent (2026-08-05)

- **App drawer**: solid black background; opens instantly (app list + icons load async on IO with a shared icon cache); Favorites row at the bottom with up to 8 pinned apps (drawer-only). Long-press any app to pin/unpin; pins persist in encrypted prefs (`pinned_apps`).
- **Face pins**: the first two pinned apps show as tappable icons top-right on the companion screen.
- **Live browser agent** (`core/agent/BrowserAgentLoop.kt`): while the browser is open, speech becomes the standing goal. The loop screenshots the page (JPEG), asks MeowLion vision for the next JSON action (inject/js/click/type/scroll/open/save/done/wait), executes it in the WebView, and verifies on the next cycle. Max 12 consecutive actions, then it reports; after "done" it keeps slow re-verification every 45s. User touch edits are picked up via the next screenshot. Pages can be saved to `filesDir/web_pages/` and reopened with `openlocal`.
- **New DOM helpers / tool actions**: `inject(html)`, `pageHtml()`, plus web.browser actions inject/html/save/openlocal; `WebViewBridge.captureJpeg()` for vision.
- **Vision defaults moved to MeowLion** (`qwen3.6-memba:35b-a3b` on http://192.168.1.163:11434) for all endpoint/model fallbacks.
- meow-nb role: publish target for built pages via the existing SSH tool; MeowLion is the brain driving the loop.

## Background follow-up handoff + show-in-browser (2026-07-29)

- **Follow-ups now survive the background hop.** The in-app recognizer goes deaf
  the moment the app loses the foreground (mic is foreground-only), so follow-up
  turns died unheard when the user switched apps. `MainActivity.onPause` now
  detects an active LISTENING state (`isAwaitingFollowUp()`), tears down the
  in-app capture (`handOffListeningToBackground()`), and starts
  `WakeWordService.startFollowUp()` — the mic-exempt foreground service captures
  the reply immediately (no wake phrase needed) and delivers it through the
  existing wake-command notification path.
- **"Show it in the browser" intent.** `looksLikeShowInBrowser()` (display verb
  + "in/on … browser", minus creation verbs like make/build/create) routes
  speech to `showLatestArtifactInBrowser()`: scans `web_pages/` + workspace for
  the newest html/png/jpg/gif/webp/mp4/webm/wav/mp3, opens the browser panel,
  and renders it — pages via `location.href`, media via a black
  `document.write` wrapper page (`mediaWrapperJs`).
- **Like every LLM: generated media auto-displays.** The `MEDIA_FILE:` handling
  in the orchestrator result path now calls `showFileInBrowser(file)`, so images
  and videos meow generates pop open in the browser panel automatically.

## Direct answers + browser searches (2026-07-29)

- **Questions are answered, not deflected.** `looksLikeDirectQuestion()` catches
  informational questions (how/what/why…, "how to …", trailing "?") before agent
  routing and sends them through `respondDirectly()` — the normal streaming chat
  path with agent routing skipped, so a question can never become a queued task
  narrating "looking it up". Commands ("open …", "can you play …") keep the agent
  path. `PromptBuilder` also lost its "offer to do it and ask them to say the
  word" instruction and gained an explicit rule: answer questions directly from
  your own knowledge, never reply with another question, never promise a lookup.
- **Searches open live in the browser.** `extractSearchQuery()` matches "search
  for / search the web for / look up / google …" (memory/notes/photos searches
  excluded, bare verbs ignored) and `showSearchInBrowser()` opens the side panel
  on `google.com/search?q=…`. Checked before the browser-agent routing so it also
  works while the panel is already open.

## Deflection fix: three real culprits (2026-08-04)

Questions were STILL being answered with questions. MeowLion itself answers
directly (verified via curl), so the app was not reaching it:

- **Phone Wi-Fi had silently dropped to cellular.** MeowLion lives on the home
  LAN (192.168.1.163), so every remote call failed and OmniRoute fell through
  to the on-device Gemma — the model that deflects. Restored with
  `adb shell svc wifi enable` (SSID "MEOW", 192.168.1.213).
- **Short questions misrouted to the on-device model.** OmniRoute tags prompts
  of ≤6 words as `FAST`, and only the local Gemma route carries that tag — so
  "what is a capacitor" was classified straight onto the deflecting model.
  `OmniRoute.plan(prompt, preferRouteId)` can now pin a route to the front, and
  `respondDirectly()` pins `"ollama"` (MeowLion) for direct answers.
- **Cold-launch wake commands were dropped.** `consumeWakeCommand()` runs in
  `onCreate`, before the ViewModel exists (it is created during first
  composition), so background-heard questions never reached the pipeline.
  `MainActivity` now stashes a `pendingWakeCommand` and delivers it from the
  ViewModel factory's `.also {}` block, mirroring `pendingRoutineId`.
- Also: the PromptBuilder anti-deflection rule is now unconditional (moved out
  of the capabilities block), and question detectors harden `stripAddress`
  plus "tell me how/what/…" phrasing.

Verified end-to-end on the Fold 6: cold-launch wake commands
"what is a capacitor" and "how do I install ollama on a mac" both produce
`MeowDirect` logs and direct spoken answers from MeowLion (postThought
notifications), zero FATAL.

## Standalone build: OpenRouter brain, no home server (2026-08-04)

meow is shipping to the Play Store, so the app may never depend on the home
network. The MeowLion (Ollama) route is gone entirely:

- **OpenRouter is the cloud brain.** `buildOmniRoute()` now registers route
  `openrouter` (https://openrouter.ai/api/v1, OpenAI-compatible streaming via
  `RemoteModelProvider`). User pastes their own key + optional model id in
  Settings; default model `nvidia/nemotron-3-super-120b-a12b:free` (free).
- **On-device fallback, always.** Without a key the OpenRouter route errors
  before producing output, and OmniRoute's fallback chain hands the turn to
  the local Gemma model. Verified on-device: "what is a quasar" answered
  fully offline, zero connections to the old home server (checked
  /proc/net/tcp).
- **Default brain**: OpenRouter when a key is saved, else `local`; explicit
  Settings choice still wins. `respondDirectly()` pins `"openrouter"`.
- **UI**: picker entry "OpenRouter (Cloud)" replaces "Ollama (Local Network)";
  Settings section swapped to OpenRouter key + model; model-manager card now
  shows openrouter.ai; default-brain choices updated. Vision: the network lane
  only works with a user-configured `network_vision` endpoint, else honest null.
- All `MEOWLION_*` constants and the hardcoded 192.168.1.163 are deleted. The
  desktop-bridge service keeps its opt-in configured-endpoint behavior only.

Tests: ModelStatus/VisionPipeline rewritten for OpenRouter; suite 486/3
(3 known pre-existing). E2E cold-launch wake questions answered standalone.

## Stability: crash, error 11, listening dropouts, artifact display (2026-08-04)

- **Crash fixed: ForegroundServiceDidNotStartInTimeException.** The wake
  service initialized EncryptedSharedPreferences BEFORE keeping its
  startForeground promise; a rare keystore failure left the promise unkept and
  Android killed the whole app. `WakeWordService.onCreate` now keeps the
  foreground promise first, tolerates storage failure, and `companionName()`
  works before storage exists. `start()`/`startFollowUp()` wrap
  startForegroundService in runCatching.
- **Error 11 no longer surfaces.** ERROR_SERVER_DISCONNECTED (11) and the
  other network/server codes are classified transient; the VM silently
  re-opens the capture session (max 2 retries, 600 ms apart) while staying
  LISTENING, and the service's follow-up capture does the same. Only
  persistent failures show the honest caption.
- **Follow-up errors were silent resets.** The follow-up capture's onError was
  a bare `resetState()` — now it goes through the shared retry/caption path.
- **Artifacts always shown.** Saved browser pages now emit `MEDIA_FILE:` so
  the panel opens them immediately; the frame-video path keeps one PNG in the
  workspace (frames were deleted before display) and points MEDIA_FILE at it.

Tests: +2 transient-error classification tests; suite 488/3 (3 known
pre-existing).

## Avatar animations finally visible (paw / tail / body)

User report: only the ambient ear wiggle was ever seen; paw, tail and other
authored clips never appeared. Root causes found and fixed:

- **Tail invisible at rest**: the renderer only drew the tail when a clip was
  actively setting `tailWag`, so between clips the cat had no tail. Now an
  ambient 3.6s tail-sway (±0.14 wag) always draws the tail; clips override it.
- **No tail built-in existed**: added a "tail wag" built-in clip (big sweeping
  wags + happy eyes), now replayable by name and in the idle pool.
- **Idle director too rare / gated wrong**: first clip now arrives in 12–25s
  (was 45–120s), later cadence 25–80s; `mouthEnergy == 0f` exact float compare
  relaxed to `<= 0.02f` so lingering TTS energy can't block performances.
- **First clip is always showy**: the session's first idle pick is forced to
  the big visible set (tail wag / zoomies / paw lick / happy bop / stretch /
  headshake) or any user-saved invention, so body animations show early.
- Added `MeowAnim: playing "<name>"` System.err log in playCustomAnimation for
  on-device verification (JVM-test safe, logcat-visible).

Device-verified on Fold 6: tail visible at rest in screenshots; logcat shows
the first idle clip ("paw lick") firing ~15s after launch.

## Tail points down (user request)

Tail path rewritten to hang downward from its base (was drawn pointing up
behind the whiskers): quadratic curve now extends +Y with a slight outward
curl, rest angle +12°, wag still ±35°. Device-verified on the Fold 7
screenshot. (Also: the test phone is a Galaxy Z Fold **7**, not 6.)

## Twenty new built-in animations (user request: "have meow create 20")

Added 20 authored clips to CustomAnimations.BUILT_INS: slow blink of trust,
zoomies burst, belly flop, head scratch tilt, bird stare, loaf tuck, nose
boop, morning stretch, surprise dilate, purr loaf, sassy tail flick, double
take, sleepy collapse, alert perch, blep, wiggle pounce, star gaze, grumpy
squint, confetti pop, ear radar sweep. All replayable by name via
avatar.animate and in the idle performance pool. New unit test asserts all 20
exist and sample cleanly. Device-verified: idle performances firing on the
Fold 7 (MeowAnim logcat).

## Rotation black-screen + home-stickiness + OpenRouter errors (4 fixes)

1. **Rotation black screen (root-caused).** Rotating as the HOME app killed the
   process with `ForegroundServiceDidNotStartInTimeException`: MainActivity.onPause
   called `startForegroundService(WakeWordService)` and onResume's `stopService`
   tore it down before `startForeground()` ran, breaking the platform's FGS
   promise. Fix: MainActivity now declares
   `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|..."`
   (rotation is handled in place, no pause/resume) AND debounces BOTH the plain
   and follow-up wake-service starts by 500ms (`wakeStandbyStart`/`followUpStart`
   runnables, cancelled in onResume) so transient pause/resume churn never starts
   the service. WakeWordService also guards the `secureStorage` lateinit in the
   STOP path. Verified: crash buffer clean across repeated rotate+HOME churn;
   landscape renders the face+tail on the cover display.
2. **Home stickiness.** The crash above was resetting Android's default-home
   choice (process death acts like a force-stop), which is why the launcher
   picker kept re-appearing. With the crash gone, `launchMode="singleTask"` on
   MainActivity (no duplicate home instances), and a new Settings → "Home
   screen" section that shows whether MEOWMBA is the default home and offers a
   "Set as home screen" button (opens `Settings.ACTION_HOME_SETTINGS`), the
   choice now sticks. Verified: single HOME press returns to MEOWMBA.
3. **OpenRouter errors.** Free models answer HTTP 200 then push the rate-limit
   error as a mid-stream SSE `{"error":{...}}` line, which the old parser dropped
   (no `choices`) → empty response, no fallback. `StreamParsers.parseOpenAISseLine`
   now surfaces mid-stream errors as `ModelResponse.Error` (so OmniRoute falls
   back to on-device), and a new `extractApiErrorMessage()` turns
   `{"error":{"message":...}}` bodies into clean captions in RemoteModelProvider
   and VisionProvider instead of dumping raw JSON. Added OpenRouter attribution
   headers (HTTP-Referer, X-Title). New StreamParsersTest cases cover all of it.
4. **LOOI robot:** audit confirms only a simulated motor/expression layer exists
   (`core/robot/MotorController` + `SimulatedMotorController` + `FaceMotorMap`);
   no LOOI hardware protocol is integrated. Answered to the user; no code change.

## LOOI robot body (real motor control)

The robot subsystem is no longer simulated-only. `core/robot/` now has:
- `LooiProtocol` — tiny LF-terminated line protocol: `S <servo> <angle>` (clamped 0-180), `E <face>`, `PING`. Pure + unit-tested.
- `RobotTransport` (+ `TcpRobotTransport` serial-over-TCP, `LoopbackTransport` for tests).
- `LooiMotorController` — a `MotorController` that encodes frames and writes them to a transport; guards on `connected`.
- `RobotManager` — owns the active controller, defaults to `SimulatedMotorController`, swaps to a live LOOI body via `connectLooi(host, port)`, and honestly reports `status` ("simulated (no body)" / "LOOI body @ h:p" / "LOOI unreachable ... - simulated").

`CompanionViewModel` now drives `robotManager.active` (instead of a fixed simulated controller) for both the IMU balance demo and mood poses, and reconnects on foreground. New state fields `robotBodyEnabled/robotHost/robotPort/robotStatus` surface in Settings under a "Robot body (LOOI)" section (enable switch + host/port). With no base attached the cat stays simulated and says so.

Tests: `app/src/test/java/com/example/meowmba/RobotTest.kt` (protocol clamping/validation, controller-over-transport, manager simulated fallback). Suite now 502 tests; only the 3 known ModelDownloadViewModelTest failures remain.

## LOOI hardware control (Bluetooth) + black-screen fixes

- **Bluetooth hardware control (#48):** `core/robot/RobotTransport.kt` gains `BluetoothRobotTransport` (classic BT RFCOMM/SPP, standard serial UUID) alongside TCP. `RobotManager.connectBluetooth(address)` + shared `bringUp`. Manifest adds BLUETOOTH/BLUETOOTH_ADMIN (maxSdk 30) + BLUETOOTH_CONNECT. Settings robot section gains "Use Bluetooth (paired device)" switch + MAC field. `CompanionViewModel.refreshRobot` picks BT vs TCP vs simulated honestly.
- **Black canvas web page (#50):** root cause was `WebViewBridge.evaluate` returning the raw JSON-encoded JS string with only the quotes trimmed — `\n`, `\"`, `\uXXXX` stayed literal, corrupting saved HTML into a black/garbage canvas; a 4096 cap also truncated pages. Added `decodeJsValue`/`unescapeJsonString` (pure, unit-tested) and raised the `pageHtml` cap to 200k.
- **Black YouTube (#49):** hardened `BrowserScreen` WebView for video-heavy sites: `mediaPlaybackRequiresUserGesture=false`, mixed-content always-allow, database enabled, modern Chrome mobile UA so YouTube serves a playable mobile page instead of a black player.

Tests: `WebViewBridgeTest.kt` (decode escapes/unicode/pass-through). Suite now 506; only the 3 known ModelDownloadViewModelTest failures remain.

## Settings cleanup + LOOI Bluetooth default

- **Bluetooth is now the default LOOI transport.** `robot_use_bluetooth` defaults to true in `CompanionState`, and both the init-restore and `refreshRobot` read it with default true. With no paired MAC the manager still falls back to simulated and says so.
- **Settings robot section reordered Bluetooth-first:** "Connect over Bluetooth" switch + MAC field show by default; the TCP host/port fields only appear when Bluetooth is turned off.
- **Settings menu cleaned up (collapse advanced):** the developer/server tail (image/music/video generation, desktop bridge, OpenAI/OpenCode/HF/OpenRouter keys, SSH servers, local model) is now wrapped in an "Advanced" expander (`Show`/`Hide`), defaulting collapsed, so the everyday items (voice, wake, robot, home, routines) are a short scroll. `Save and Close` stays always visible.

Verified on device: settings opens with the robot section showing "Connect over Bluetooth" + MAC, and the advanced tail absent until expanded. 0 crashes.

## LOOI auto-connect

- The robot body now connects on its own: robot_body_enabled defaults to true,
  and refreshRobot (ViewModel init + MainActivity.onResume) resolves the Bluetooth
  address from the saved MAC, else RobotManager.discoverLooiAddress() picks the
  first bonded device whose name contains "looi"/"meow" and connects via RFCOMM/SPP.
  No paired base -> stays simulated (honest status).
- BLUETOOTH_CONNECT added to the startup runtime-permission request (bondedDevices
  throws on Android 12+ without it).

## Wake phrase fixes (2026-08-05)

- False "Wake phrase stopped - the speech recognizer keeps failing": AIAI on the Fold 7
  emits bursts of ERROR_CLIENT (code=5) at ~700ms right after (re)start; the 5-strike
  rule tripped inside one burst. RecognizerWakeWordEngine now gives ERROR_CLIENT its own
  leash: 25 failures at a 1.2s backoff (~30s sustained) before stopping; any completed
  session resets both counters. ERROR_AUDIO/SERVER still ride the 5-strike rule.
- "Only yo meow works": added 16 more emittable decodes of the hey/ok carriers to
  KwsKeywords EXACT + WakeWordEngineFactory.BASE_PHRASES (hey miaw/miow/miou/meao/meou/
  meu/mia/meuw/maow/maw, he meow, ay meow, yo miaw/miow/meao, ok miaw) and bumped all
  meow-phrase tuning from :3.5 #0.08 to :4.0 #0.06. False wakes stay bounded by the VAD
  + speaker-verification gates.
- Tests: VoicePipelineTest updated (audio-error escalation + new CLIENT-churn test);
  KwsKeywordsTest re-pinned at the new tuning. 507 tests, only the 3 known
  ModelDownloadViewModelTest failures.

<!-- AUTOBUILD:START -->
(regenerated after each commit)
- 2026-08-06 12:30:15 : fix: error 11 backoff + screen-contextual command routing + improved screen reading
- deba3f9 2026-08-06 12:01 fix: LOOI robot connection — GATT error 11 retry + Initialize button (from MEMBA 2)
- f423d9a 2026-08-06 11:01 feat: LUMI edge agent — DualModeRouter, OfflineQueue, device settings tools
- 3aff73b 2026-08-06 10:34 feat: 'meow start listening' — Voice Access + LLM hybrid mode
- 2658c68 2026-08-06 09:54 feat: Loona-style features — notification listener, face attention, proactive nudges, focus mode
<!-- AUTOBUILD:END -->
