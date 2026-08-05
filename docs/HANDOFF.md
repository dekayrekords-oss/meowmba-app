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
- Device: Galaxy Z Fold 6 (SM-F966U1, serial RFCY61NJEVM); USB drops — fix with
  `adb kill-server && adb start-server`.

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

<!-- AUTOBUILD:START -->
(regenerated after each commit)
- 2026-08-04 20:57:39 : Ignore .kotlin daemon log artifacts
- 3dfa052 2026-08-04 20:54 Phase G: LOOI mood engine, idle chatter, and motor control API
- 1219c87 2026-08-04 19:24 Ignore python bytecode cache
- e39d29b 2026-08-04 19:24 Phase F: desktop control bridge, phone system tools, opt-in UI automation
- d91bfbf 2026-08-04 19:05 Phase E: routines engine, memory follow-ups, barge-in
<!-- AUTOBUILD:END -->
