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

<!-- AUTOBUILD:START -->
(regenerated after each commit)
- 2026-08-04 18:40:52 : Phase C: IMU motion sense, screen watch mode, context director
- 490fab4 2026-08-04 18:26 Phase B: BigPickle brain, ask-anything recall, SSH server management
- 99172fc 2026-08-04 18:11 Merge remote-tracking branch 'origin/main'
- f8f1d98 2026-08-04 18:09 Face mode: keep-screen-on toggle, random idle animations, live HANDOFF doc
- 6e9cc38 2026-08-04 21:32 chore(sdd): record final review outcome, Task 3, and overall plan status
<!-- AUTOBUILD:END -->
