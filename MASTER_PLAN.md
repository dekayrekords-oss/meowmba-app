# MEOWMBA MASTER PLAN

## Current release checkpoint — September 5, 2026

- Release candidate: 1.3.20 / versionCode 24, current application ID
  `com.example.meowmba`. Older package/version declarations below are historical.
- 3D work is paused by the user. No Blender assets or Filament model framework
  are included in the app; keep prototypes outside app source sets.
- Unsigned release bundle built and native alignment inspected. This is not
  store approval, signed delivery, device acceptance, or completion of all plans.
- Current evidence and required next steps:
  [release checklist](docs/store/RELEASE_PREP_2026-09-05.md).

## Current priority — immersive companion revision

Detailed scope: [docs/IMMERSIVE_COMPANION_PLAN.md](docs/IMMERSIVE_COMPANION_PLAN.md). Execution ownership: [docs/AGENT_PLAN.md](docs/AGENT_PLAN.md). These override older animation/orb descriptions for this revision; unrelated roadmap work remains intact.

- [ ] Remove the visible Jarvis shell/rings; render parameters with glow diffusing into the actual background.
- [ ] Replace the 60%-height chat with collapsed-button, compact-composer, and explicit-history states.
- [ ] Approve an eye-acting proof, then implement a variation-aware director with emotional transitions and interruption handling.
- [ ] Add six bounded original prop scenes, with Focus and reduced-motion options.
- [ ] Profile app-wide rendering, lifecycle, memory, startup, and test-worker behavior; fix measured bottlenecks.
- [ ] Prefer `opencode/big-pickle` coding workers from the installed local OpenCode CLI; verify request access first. Use Meow for bounded supporting inference.
- [ ] Preserve light/dark themes, colors, privacy, approvals, data, and a separate installation gate.

Status: newly planned scope, not yet implemented or device-verified. Historical version and test summaries below require revalidation before release.

> **Project codename:** Meowmba | **App name:** Memba | **Package:** `com.example.meowmba` (debug) / `com.meow.meowmba` (release)
> **Version:** 1.3.10 (versionCode 14) | **Min SDK:** 31 | **Target SDK:** 36 | **Compile SDK:** 37
> **Language:** Kotlin | **UI:** Jetpack Compose (Material 3) | **Build:** Gradle KTS
> **Last scanned:** 2026-09-03 | **Commit:** `2def58c` (detached worktree at `origin/main`)

---

## Update Checklist

**Test Results:**
- Test results unavailable (run `./gradlew :app:testDebugUnitTest`)

**To update this checklist automatically, run:**
```bash
python3 scripts/update_master_plan.py
```

---

## 1. What It Is

Meowmba is an on-device Android AI companion represented by an animated cat avatar. It is a voice-first, tool-routing, memory-remembering, robot-controlling, desktop-bridging personal assistant. The companion speaks, listens, remembers, performs animations, controls a LOOI robot body, pairs with a desktop for file/build operations, and routes its "brain" across on-device models, LAN servers, and cloud APIs.

**Core identity:** The model proposes; the orchestrator constrains. No shell executor. Honest capabilities only — unavailable adapters are badged UNAVAILABLE, never faked.

---

## 2. Completed Phases (Shipped)

### Phase 1 — Working Avatar Shell

- [x] Memba 2 face design, resolution-independent line art
- [x] CatAvatarStateMachine: interaction state × emotion → AvatarPose
- [x] CatAnimationRegistry: named clips (idle.blink, speech.talk_cycle)
- [x] Independent animation layers: blink, ear twitch, breathing, gaze wander, whisker sway, mouth wobble, halo pulse
- [x] Touch zones: ears (mic privacy), eyes (camera privacy), mouth (mute)
- [x] Stroke-to-purrs, swipe-up chat, swipe-down tiles

**Phase 1 — Working Avatar Shell: 6/6 complete**

### Phase 2 — Real Voice Pipeline

- [x] Provider-neutral SpeechEngine / SpeechSynthesizer interfaces
- [x] Wake phrases: Hey Memba, Hey Meow, Hey [custom name]
- [x] WakePhraseMatcher (pure Kotlin, unit-tested)
- [x] Conversation mode (8s follow-up timeout)
- [x] Interruptible speech (phrase-match interrupt only)
- [x] Audio focus management
- [x] Mouth sync via TTS word-boundary callbacks
- [x] Voice modes: push-to-talk, tap-to-toggle, wake phrase, conversation mode

**Phase 2 — Real Voice Pipeline: 8/8 complete**

### Phase 2.5 — Background Wake Word

- [x] ForegroundService (foregroundServiceType=microphone)
- [x] Explicit opt-in, persistent notification with Stop action
- [x] Screen-off flow: wake → Yes? → command → high-priority notification

**Phase 2.5 — Background Wake Word: 3/3 complete**

### Phase 3 — Model Routing

- [x] Live streaming: Ollama (NDJSON)
- [x] OpenAI-compatible (SSE) streaming
- [x] On-device MediaPipe / LiteRt inference
- [x] StreamParsers (pure, unit-tested)
- [x] Honest networking: HTTP status checked, missing key fails fast
- [x] Echo-tolerant barge-in (SelfVoiceFilter)
- [x] Hardware page: provider picker, local file check, cloud health ping

**Phase 3 — Model Routing: 7/7 complete**

### Phase 4 — Long-term Memory (Room v3→v7)

- [x] Memory types: EPISODIC, SEMANTIC, PROJECT, PREFERENCE
- [x] EmbeddingInterface: LocalHashEmbedder (deterministic)
- [x] Cosine-similarity recall with explanations
- [x] Remember that… / remember this commands
- [x] Memory screen: FTS search, type badges, delete, JSON export, retention policy
- [x] MetaMemoryConnector (interface, no-op default)

**Phase 4 — Long-term Memory (Room v3→v7): 6/6 complete**

### Phase 5 — Agents & Tools

- [x] AgentRegistry: Meow, Shelve, Keeper, Builder, Forge, Scout
- [x] ToolSystem: ToolDefinition (inputs, risk level, confirmation policy, timeout, audit)
- [x] Risk levels: SAFE / MODERATE / DESTRUCTIVE
- [x] Approval gates: 5min auto-deny, queued approvals
- [x] Sandboxed Workspace (no ../, no absolute paths)
- [x] Audit log: every task transition persisted to Room

**Phase 5 — Agents & Tools: 6/6 complete**

### Phase 6 — Desktop Bridge (P6a–P6d)

- [x] P6a: Pairing (UDP discovery, 8-char code, 256-bit secret, Keystore)
- [x] P6a: Cert pinning, HMAC-SHA256 signed requests, 60s replay window
- [x] P6b: Read-only file tools (list_files, read_file)
- [x] P6b: Workspace confinement, denylist, 64KB cap, 500-entry listing cap
- [x] P6c: Approval-gated writes (write_file), atomic writes, .bak backups
- [x] P6d: Named build triggers (run_build), argv-only (no shell)

**Phase 6 — Desktop Bridge (P6a–P6d): 6/6 complete**

### Phase 7 — Vision

- [x] CameraX integration, face-follow
- [x] Scene descriptions via multimodal model
- [x] Watch mode: MediaProjection foreground service, 1 frame/8s
- [x] ContextDirector: vision-describes frames, classifies, reactive animations
- [x] Follow-only mode for lightweight auto-tracking

**Phase 7 — Vision: 5/5 complete**

### Phase 8 — Avatar Invented Animations

- [x] avatar.animate tool: name + JSON keyframes
- [x] CustomAnimations.parse: validates, clamps, reports every clamp
- [x] Per-field sparse sampling (numbers smoothstep-lerp; booleans step)
- [x] save=true persists to filesDir/custom_animations/
- [x] Built-in clips: ear wiggle, happy bop, curious tilt
- [x] Self-performance disambiguation: avatar.animate ≠ media.generate_video

**Phase 8 — Avatar Invented Animations: 6/6 complete**

### Phase B — BigPickle Brain + Recall + SSH

- [x] BigPickle brain (OpenCode Zen key, model big-pickle)
- [x] Ask-anything recall: messages_fts FTS4 index, memory.recall tool
- [x] SSH management: encrypted hosts, ssh.run (allowlisted read-only commands)

**Phase B — BigPickle Brain + Recall + SSH: 3/3 complete**

### Phase C — IMU + Watch Mode + ContextDirector

- [x] MotionSense: avatar leans with phone (±8° low-passed roll)
- [x] Shake (>2.5g) → startled animation
- [x] Watch mode foreground service
- [x] ContextDirector vision pipeline

**Phase C — IMU + Watch Mode + ContextDirector: 4/4 complete**

### Phase D — Launcher + App Drawer

- [x] Optional launcher (HOME + DEFAULT intent categories)
- [x] App drawer: swipe right on face, alphabetical grid, live search

**Phase D — Launcher + App Drawer: 2/2 complete**

### Phase G — LOOI Robot Body

- [x] BodyVerb vocabulary (Neck, Light, Drive, Turn)
- [x] BodyScheduler: plays body half of every clip on same clock as face
- [x] FaceMotorMap: mood → resting body pose (no drive in resting pose)
- [x] ConfinementModel: evidence-based envelope (cliff, unknown, link loss)
- [x] fed9 sensor frames → SensorEvent parsing
- [x] RobotManager: simulated default, LOOI BLE body when connected

**Phase G — LOOI Robot Body: 6/6 complete**

### v1.3.0 — Chrome Browser + MCP + Wake-Tolerant Animations

- [x] Chrome replaces swipe browser
- [x] MCP browser server (navigate, read, links, html, click, type)
- [x] Wake-tolerant animations (don't break on wake-word detection)

**v1.3.0 — Chrome Browser + MCP + Wake-Tolerant Animations: 3/3 complete**

### v1.3.1 — Bundled ASR Pack + Sync

- [x] Bundled on-device streaming ASR pack
- [x] Matchable debug key
- [x] Wake-word engine starts off main thread
- [x] Lazy-load on-device brain, release when idle
- [x] Right-edge app rail with swipe-open pinned apps

**v1.3.1 — Bundled ASR Pack + Sync: 5/5 complete**

### v1.3.2–v1.3.10 — Connections, Skills, MCP, and Onboarding

- [x] Guided per-provider API-key and model setup
- [x] Cloud Gemini ASR wired as an honest selectable backend
- [x] Plugins & MCP Connectors screen replaces the old Tools tile
- [x] Remote HTTP MCP client with connection testing and encrypted configuration
- [x] Phone-hosted LAN HTTP MCP endpoint
- [x] Curated app gallery with per-service setup guidance
- [x] Executable on-device skill runner
- [x] Live Spotify and GitHub service clients
- [x] Live Home Assistant integration
- [x] Expanded messaging, smart-home, streaming, finance, developer, and sibling-Meow connections
- [x] First-run “Connect your AI” wizard with local, free-cloud, and paid-cloud paths

**v1.3.2–v1.3.10: 11/11 complete**

---

## 3. Summary

**Shipped features tracked:** 87
**Verified shipped:** 87
**Open roadmap items:** 18 engineering/quality/verification items plus future phases

“100%” applies only to the shipped checklist above; it does not mean the product roadmap is finished.

---

## 4. Key Technical Details

### Brain Routing (OmniRoute order)
1. **Keyword fast path** — deterministic, free ("create file called notes.txt")
2. **Hermes Agent endpoint** — OpenAI-compatible `/v1/chat/completions` with native function-calling `tools`
3. **On-device brain** — LlmToolCallRouter, single-JSON tool decision under compact prompt

### Voice Pipeline
- **Wake:** SherpaKwsEngine (keywords from KwsKeywords.kt — tokens must exist in model vocab)
- **VAD:** VadGate + WindowCarry (Silero VAD)
- **ASR:** Whisper / Sherpa / Gemini (multi-engine)
- **TTS:** SpeechEngine interface (Piper, cloud APIs)
- **Speaker verification:** SpeakerVerifier (on-device)
- **Echo suppression:** SelfVoiceFilter (TTS word-boundary tracking)
- **Turn deduplication:** TurnDeduplicator (8s window)

### Memory Architecture
- **Room v7:** messages, memories, memories_fts, audit_log, characters
- **Embeddings:** LocalHashEmbedder (signed feature hashing, L2-normalized)
- **Recall:** cosine similarity × importance weight, top-5 with explanations
- **Context window:** persona + capabilities + recalled memories + last 10 messages
- **Vault:** Obsidian-compatible markdown notes in `filesDir/vault`

### Security Model
- **No shell executor** — phone side or desktop bridge
- **Secrets:** Keystore-backed SecureStorageManager
- **API 30+ package visibility:** `<queries>` entries for metamemory/inventory/maps/speech
- **Desktop bridge:** HMAC-SHA256 signatures, 60s replay window, cert pinning
- **SSH:** allowlisted read-only commands, reject shell metacharacters, TOFO host keys
- **Cloud backup:** chat/keys/files OFF
- **Cleartext HTTP:** LAN/localhost only (documented in network_security_config.xml)

### Tool System
- **Registry:** ToolRegistry (register, byId, definitions)
- **Risk levels:** SAFE (no confirm), MODERATE (confirm per task), DESTRUCTIVE (always confirm)
- **Approval:** 5min auto-deny, queued (one at a time)
- **Audit:** every task transition → Room audit_log
- **No shell:** fixed argv lists only, no string interpolation

---

## 5. Project Roadmap (Remaining)

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 7 | Vision (full) | Partial — follow-only done, Commentary mode built |
| Phase 8 | Advanced avatar (expanded animation registry) | Started — invented animations shipped |
| Phase 9 | Desk & car modes | Planned |
| Phase 10 | Ecosystem | In progress — plugins, remote MCP, phone MCP, service clients, and setup gallery shipped |
| Phase 11 | Native conversational audio | Planned — Gemini Live/Firebase AI Logic and/or OpenAI audio, preserving deterministic commands and audit |
| Phase 12 | Android AppFunctions | Preview investigation — Android 16 API exists; cross-app execution is restricted to approved system agents |
| — | llama.cpp provider (run any GGUF on-device) | Queued |
| — | Native on-device image gen (stable-diffusion.cpp) | Queued |
| — | Music generation | Branch exists |
| — | Video generation | Branch exists |
| — | Godot avatar prototype | Branch exists |

---

## 6. Repositories & Remotes

| Remote | URL | Role |
|--------|-----|------|
| `lan` | `meow:/home/claude/git/meowmba-app.git` | **CANONICAL** — gets real work, what phone runs |
| `prog` | `prog@192.168.1.69:/home/prog/git/meowmba-app.git` | Mirror (was 54 commits behind, now synced) |
| `origin` | `meow-nb:/home/claude/git/meowmba-app.git` | Notebook (unreachable — Tailscale dead) |

**Working copies:**
- PRIMARY (edit here): `/Users/bobrossjr/AndroidStudioProjects/Meowmba`
- Local mirror: `~/meowmba-app`
- This worktree: `/Users/prog/Documents/Codex/2026-08-25/ssh/meowmba-130`

**Sync rule:** `git fetch --all && git log --oneline -1 lan/main` before building. Building from `prog-main` while `lan/main` moved on silently reverts a week of work onto the phone.

---

## 7. Build & Test

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests (~1 min)
./gradlew :app:testDebugUnitTest

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release bundle (Play Store)
./gradlew :app:bundleRelease
```

**Pre-build requirements:**
- `local.properties` with `sdk.dir` (and `debugKeystore=` for matchable debug key)
- `scripts/fetch_kws_assets.sh` on fresh worktree (~100MB voice models)
- Bump `versionCode` past on-device build before install

**Known pre-existing test failures (NOT yours):**
- `ModelDownloadViewModelTest`: 3 JVM-only failures (unmocked `android.content.Intent`)

---

## 8. Device & Debugging

- **Device:** Galaxy Z Fold 7 (serial RFCY61NJEVM)
- **Dual-display screencap gotcha:** bare `adb shell screencap` grabs INNER display (OFF) → pure black. Always capture cover display: `adb shell screencap -p -d 4630946872173396372`
- **On-device diagnosis:** pull Room DB via `adb exec-out run-as com.example.meowmba cat /data/data/com.example.meowmba/databases/meowmba_database` — audit_log + messages tables
- **Room WAL:** pull `meowmba_database-wal` too, or miss fresh rows
- **Verify running process predates last install:** `ps -o ETIME`

---

## 9. Open Work Items (Ranked)

### Tier 0 — Product Completion
1. **Needle 2 default action brain** — official Cactus Compute model is now the recommended first-run download, with a pinned Android native runtime, automatic background install/load, and safe fallback. Complete native build/device verification before marking shipped.
2. **End-to-end speech** — add a production native-audio provider (Gemini Live is preferred for Android), preserve local wake/endpointing/commands, and provide cascade fallback.
3. **MCP account linking** — replace token-paste setup with standards-compliant OAuth discovery, PKCE, callback handling, refresh, disconnect, and revoked-token recovery.
4. **Installed-app integrations** — use real PackageManager labels/icons; identify remote MCP, Android AppFunctions, intent-only, and accessibility-fallback capabilities without calling every app “MCP.”
5. **Android AppFunctions** — expose Meowmba functions now; consume other apps only when the OS grants `EXECUTE_APP_FUNCTIONS` to this build. Never imply sideloaded builds have privileged access.
6. **Connection safety** — classify remote tools conservatively, route every call through approval/audit, defend against prompt injection, and show exact granted scopes.
7. **On-device verification** — install the signed upgrade, complete Gmail/GitHub/Home Assistant login flows, execute read and write tools, and verify audit records.

### Tier 1 — Real Engineering
1. **Face-track staircase** — dead-band + maxStep + second dead-band → discrete jumps. Keep one dead-band, drop maxStep, publish on fixed cadence.
2. **mouthEnergy recomposes avatar ~14×/sec** — move out of CompanionState into own StateFlow, read as lambda in draw pass.
3. **IMU/face-track servo writes on main thread at ~50Hz** — route setServo through conflated channel on Dispatchers.IO, register sensor on HandlerThread.
4. **Duplicate GATT connect storm** — ~8 concurrent LooiBleLink connections in 20ms. May be incidentally fixed; confirm or fix properly.
5. **ConfinementModel.onPickedUp()** — implemented, tested, never called. Needs IMU signature (free-fall window or gravity vector change).
6. **SensorEvent.Tof** — parsed but not used as evidence. Needs real frame capture to interpret.

### Tier 2 — Animation Quality
1. 11 keyframe built-ins permanently unreachable (composed expression owns name)
2. Four expression pairs effectively identical (head bonk/bunt, content sigh/relief, groom/paw lick, zoomies/zoomies burst)
3. EyeL.WIDE ≈ EyeL.ALERT once clamped — re-space on pupil size
4. Body.RECOIL at full amplitude on frame 0 (no anticipation); Body.COIL ends mid-launch
5. Ear/jaw rhythms on normalized clip time — pass absolute seconds
6. JawL.PULSE straddles MouthMode threshold → purr strobes HAPPY/SPEAKING
7. quick blink 0.45s below visibility floor; keyframe built-ins push eyeOpen to 1.4–1.6
8. applyTo fires sparkle on startle/recoil/surprise — delight VFX on fear beat

### Tier 3 — Verification Needed
1. Wake word end-to-end (4 causes fixed, never observed triggering a turn)
2. robot.move reaching motors (routing unit-tested, no spoken command watched hitting fe00)
3. Settings staying open through idle clip
4. Alarm end-to-end

---

## 10. Play Store Submission

- **Package:** `com.meow.meowmba` (release) / `com.example.meowmba` (debug USB)
- **Version:** 1.3.10 (versionCode 14)
- **AAB:** `app/build/outputs/bundle/release/app-release.aab` (154 MB)
- **Privacy:** https://dekayrekords-oss.github.io/Meowmba/privacy.html
- **Native:** 16KB ELF-aligned, arm64-v8a only
- **Status:** Code-side prep done. Console work (create app, upload AAB, data safety form, permissions declarations, store listing) still required.

---

## 11. Conventions

- **Commits:** `<type>(<area>): <plain-english what + why>` — types: feat/fix/chore
- **Honesty over polish:** never fake a capability
- **Comment style:** KDoc explaining the *why*, often with observed device behavior
- **Deterministic fast path first:** keyword routing before any model call
- **Model proposes, orchestrator constrains:** tools never decide policy
- **Tests are pure-JVM where possible:** android.net.Uri etc. not mocked; put string logic in static/companion functions
- **Room migrations:** bump + migrate, never `fallbackToDestructiveMigration`

---

*Updated: 2026-09-03 from meowmba-130 worktree at commit 2def58c (v1.3.10).*
# Interchangeable 3D avatars — implementation checkpoint

Added a Filament GLB renderer, private local model library, persistent selection,
Avatar-page import UI, shared named morph/state animation contract, smooth gaze,
speech energy, driving-force response and external privacy indicators. Existing
avatar styles remain available. See `docs/AVATAR_MODEL_CONTRACT.md` for authoring
and limitations. Compilation and focused contract tests passed; hardware render,
asset QA and installation remain pending. No Blender character was supplied.
