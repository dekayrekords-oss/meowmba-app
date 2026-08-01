# Memba: Intelligent AI Companion

Memba is a production-quality Android application that acts as the control center for an intelligent personal AI companion, represented by an animated cat avatar.

## Running real Hermes on the phone

The full `hermes-agent` build from the Memba server stack (8B class) does not
fit a phone practically — but **real Hermes 3 runs on-device at 3B**:

1. Get **NousResearch/Hermes-3-Llama-3.2-3B** (real Hermes 3 lineage, trained
   for function calling; GGUF quants also exist).
2. Convert it to a MediaPipe `.task` bundle with Google's AI Edge tools
   (Llama 3.2 3B is a supported architecture — the Hermes fine-tune shares it).
3. Push to the phone: `adb push hermes3.task /data/local/tmp/llm/hermes3.task`
4. In Meowmba: Settings → local model path → `/data/local/tmp/llm/hermes3.task`,
   Hardware page → select "Gemma 2b (Local)" and "Check local file" to verify.
5. The app **auto-detects** "hermes" in the model path and switches the agent
   brain to Hermes's native function-calling template (`<tools>/<tool_call>`
   ChatML) — the exact format the model was trained on. The parser already
   speaks it.

Practical notes: 8GB+ RAM phone recommended; routing decisions take a few
seconds on-device (chat answers longer). For the fastest, smartest brain, the
server Hermes endpoint still wins — the phone path is for offline/no-server use.

## On-device image generation (real Stable Diffusion, no server)

`media.generate_image` uses **MediaPipe's Image Generator** (Stable Diffusion
1.5 architecture) when weights are installed, and falls back to the simple
canvas renderer when they are not. Everything runs on the phone — no endpoint,
no key, no network.

Filenames tell you which ran: `image_*.png` came from diffusion, `canvas_*.png`
from the fallback.

### Installing the weights

The task needs a **directory of converted .tflite bins**, not a single file.
The weights are not bundled — they are ~1.5 GB.

1. Get an SD 1.5 checkpoint (`runwayml/stable-diffusion-v1-5` or any SD 1.5
   fine-tune — the architecture is what matters, not the exact model).
2. Convert it with MediaPipe's image-generator converter (Python, on a desktop).
   Google's guide: <https://ai.google.dev/edge/mediapipe/solutions/vision/image_generator>
3. Push the output folder to the phone:
   ```
   adb push bins /data/local/tmp/image_generator/bins
   ```
4. In Meowmba: **Settings → Image generation → Image model folder** →
   `/data/local/tmp/image_generator/bins`, then tap **Check image model**.

### RAM, and why the main model may need to shrink

SD 1.5 wants roughly **2 GB resident** while generating. The generator is
created per request and closed immediately after, so it does not hold that
memory between images — but it still has to coexist with whatever LLM is
loaded at that moment.

On an 8 GB phone, pairing it with a 3B-class local model (or a server/cloud
brain, which uses no local RAM at all) is comfortable. Pairing it with a large
local `.task` model is where you will see the generator fail to allocate.
If image generation reports an allocation failure, drop the main model a size
before doing anything else.

Expect ~15s per image on flagship hardware and well over a minute on midrange.

> Note: Google lists the MediaPipe Image Generator task as **no longer
> actively maintained**. It works and is stable, but there will be no new
> versions — worth knowing before building anything load-bearing on it.

## Agent Brain (Hermes Agent + on-device routing)

The companion's tool routing is model-driven, with a strict trust boundary:
**the model only proposes; the orchestrator's permission checks and your
approval card still decide what executes.**

Routing order for every utterance:
1. **Keyword fast path** — free, deterministic ("create file called notes.txt").
2. **Hermes Agent endpoint** *(when configured)* — the Memba stack's
   `hermes-agent` (v0.19+) speaks OpenAI-compatible `/v1/chat/completions`
   with native function-calling `tools`. `HermesAgentBrain` converts the
   `ToolRegistry` into OpenAI tool schemas, parses structured `tool_calls`,
   and — because the knowledge base warns hermes sometimes *speaks raw JSON as
   text* — falls back to the defensive `ToolCallParser` for content. Unknown
   tools and garbage are rejected, never guessed. Unreachable/declined →
   falls through honestly to the on-device brain.
3. **On-device brain** — no server, no agent framework: the active model
   renders a single-JSON tool decision under a compact prompt contract
   (`LlmToolCallRouter`). With a local MediaPipe model selected, this never
   leaves the phone. A command-heuristic gate skips the model call for
   chit-chat so conversation stays fast.

Configuration: Settings → "Agent brain" toggle (master switch), "Hermes agent
endpoint" (blank = on-device only). Everything runs through the
`AgentBrainRouter` adapter seam — no coupling to any one agent framework.

## Phase 6: Desktop Bridge (P6a — pairing & signed channel, complete)

Meowmba can pair with exactly one computer running `bridge/meowmba-bridge.py`
(single file, Python 3.9+ stdlib only, no pip installs). P6a establishes the
trust channel every later bridge tool rides on:

- **Pairing:** run `python3 bridge/meowmba-bridge.py --pair` on the desktop.
  The daemon broadcasts its presence over UDP :8766; Meowmba → Settings →
  Desktop Bridge → **Find desktops** lists every daemon on the LAN — pick
  it and type the **8-character code** (single use, 10-minute window, 20
  attempts max). The phone generates a 256-bit secret, hands it over once
  over the pinned TLS channel, and stores it in Keystore-encrypted
  preferences; the desktop stores it in `~/.meowmba-bridge/paired.json`
  (chmod 600). (The full payload string remains as a manual fallback.)
- **Cert pinning:** the daemon serves HTTPS with a locally generated
  self-signed cert (openssl, one-time). The phone pins the cert's SHA-256
  learned from the daemon's own discovery broadcast and refuses any other
  certificate; the code is the human proof of physical access.
- **Signed requests:** every request after pairing carries
  `X-Meowmba-Timestamp` + `X-Meowmba-Signature` (HMAC-SHA256 over
  `method + path + timestamp + sha256(body)`); the daemon enforces a 60-second
  replay window. Tampered bodies, stale timestamps, and unsigned requests are
  rejected (smoke-tested).
- **P6a surface:** `ping` and `status` RPCs, plus a Settings section
  (pair / ping / unpair). File and build tools are P6b+ and are confined
  to workspace roots declared in the desktop's `config.json` — the no-shell
  security contract holds across the bridge.

### P6b — read-only workspace file tools (complete)

- **Daemon methods:** `list_files` and `read_file`, read-only, confined to
  the `workspace_roots` declared in `~/.meowmba-bridge/config.json`. Paths
  are resolved with `realpath` before the confinement check, so `../`,
  absolute-path tricks, and symlink hops all fail with an honest 403.
  Sensitive names (`.env`, private keys, certs, `local.properties`, …) are
  filtered from listings and refused on read; only utf-8 text is served
  (binary → 415), reads are size-capped (64 KB default, 256 KB max), and
  listings cap at 500 entries.
- **Phone:** `DesktopBridgeManager.listFiles` / `readFile` on the signed
  channel, daemon error messages surfaced verbatim, and a read-only
  workspace browser in Settings → Desktop Bridge (roots → directories →
  size-capped file previews).
- **Verified:** live smoke suite covers pairing, signing, replay/tamper
  rejection, root listing, denylist filtering, traversal/absolute/symlink
  escapes, truncation, binary refusal, and missing-path 404s.

### P6c — approval-gated desktop writes (complete)

- **Daemon `write_file`:** same workspace confinement + denylist, utf-8
  only (256 KB cap), atomic (temp file + `os.replace`), existing files
  backed up first (`<name>.bak-<timestamp>`), every write appended to
  `bridge.log` with the paired phone's name.
- **Phone:** `desktop.write_file` is a real agent tool — MODERATE risk,
  `ConfirmationPolicy.ALWAYS`, desktop environment — so the orchestrator's
  existing approval card gates every write, exactly like phone-side file
  tools. Unpaired bridge fails honestly; daemon errors surface verbatim.
- Smoke suite now covers create/overwrite/backup/read-back, denylist and
  escape refusals, unicode round-trips, missing parents, and audit-log
  entries; unit tests pin the tool's policy contract.

### P6d — named build triggers + audit (complete)

- **Daemon `run_build`:** runs ONLY commands pre-declared by name in the
  desktop's own `config.json` (`commands: {name: {argv, cwd, timeout_s}}`).
  argv is a fixed list (no shell — same contract as the phone), cwd is
  confined to workspace roots, output is capped (last 32 KB), timeouts
  kill, and every run lands in `bridge.log` with exit code and duration.
- **Phone:** `desktop.run_build` — DESTRUCTIVE, approval-card gated, with
  the exit code and output tail spoken/rendered. Undeclared names fail
  with the list of what IS declared.
- Ready on your Mac: `assemble-meowmba` and `test-meowmba` are declared in
  `~/.meowmba-bridge/config.json`. Add your own the same way.
- **Interop is unit-tested:** the Kotlin signing/base64 code is verified
  against CPython-generated vectors (`DesktopBridgeProtocolTest`), and the
  daemon passes a live smoke test covering pairing, single-use tokens,
  signature checks, and replay rejection.
- Honest limits: LAN-direct only (no relay yet); the Settings UI has been
  built and unit-tested at the protocol layer but still needs an on-device
  run against a real desktop.

## Phase 5: Agents & Tools (Completed)

- **Agent runtime:** the companion orchestrates specialist agents — Keeper (memory), Shelve (files), Builder (projects), Forge (coding), Scout (research, marked LIMITED), and email/calendar/desktop/media agents registered but clearly marked UNAVAILABLE until their adapters exist. Every agent has an ID, purpose, model assignment, tool permissions, and a concurrency limit.
- **Tool system with policy:** every tool declares its inputs, risk level (SAFE / MODERATE / DESTRUCTIVE), confirmation policy, timeout, and audit behavior. **No shell executor exists** — per the security contract.
- **Sandboxed workspace:** file/project tools can only touch app-private storage; path resolution rejects `../` escapes, absolute-path tricks, and non-empty directory deletion.
- **Approval gates:** risky tools block on an approval card (red border for destructive) — Approve/Deny, 60s auto-deny on timeout, and multiple pending approvals queue in order instead of overwriting each other.
- **Audit log:** every task transition, approval, and outcome persists to Room (v4) and streams into the Agents screen.
- **Honest voice routing:** `AgentRouter` maps explicit commands ("create file called notes.txt", "delete file old.txt", "create a project called Website", "search memory for tea", "what tasks are running") to agent intents; everything else stays normal conversation. Task outcomes are spoken aloud.
- **Agents & Tools screen:** agent roster with honest availability badges, live tasks with cancel, and the audit trail.

## Phase 4: Long-term Memory (Completed)

- **Memory types (Room v3):** `EPISODIC` (auto checkpoints of finished turns), `SEMANTIC` (explicit "remember that …" facts), `PROJECT`, `PREFERENCE` — each with importance, tags, source-message links, timestamps, last-recalled audit stamps, and an embedding blob.
- **Embeddings behind an interface:** deterministic on-device `LocalHashEmbedder` (signed feature hashing, L2-normalized) works everywhere with zero model files; a MediaPipe/server embedder can replace it behind `EmbeddingInterface` without changing recall.
- **Embedding recall with explanations:** every user turn ranks the memory pool by cosine similarity weighted by importance. Recalled memories are injected into the model prompt (persona + memories + recent turns via `PromptBuilder`) AND shown to the user in "Recalled from memory" chips with the exact reason (matched snippet, similarity score, importance) — you always know why the cat remembered something.
- **"Remember" commands:** "remember that I like green tea" stores a high-importance semantic memory and confirms aloud; "remember this" captures the companion's last statement. Works mid-conversation.
- **Real chat history:** the chat workspace renders the Room-backed conversation live — restart the app and everything is still there.
- **Memory screen (Memory tile):** FTS search, per-type badges, per-item delete, clear-all (with confirmation), JSON export via share sheet, retention policy (30d/90d/1y/∞, purged on start and on change), and a pause switch that stops all memory writes.
- **Meta Memory adapter:** `MetaMemoryConnector` interface with an honest no-op default, ready for the Meta Memory app's sync contract. Local Room stays the source of truth.

## On-device Model Downloads

The Hardware page installs real on-device models — no adb required:

- **Downloads land in app-private storage** (`filesDir/models`) — the only
  place the app can both write and load from. (The old default
  `/data/local/tmp/llm/` is adb-only, which is why "no model is installed"
  could never fix itself.)
- **Curated catalog (verified against the Hugging Face API):**
  | Model | Size | Gated? | Best for |
  |---|---|---|---|
  | Qwen 2.5 0.5B Instruct | ~0.6 GB | No | Any phone, instant routing |
  | Qwen 2.5 1.5B Instruct *(recommended)* | ~1.7 GB | No | Best balance, no account |
  | Qwen 2.5 1.5B (long context) | ~1.8 GB | No | Long memory-recall prompts |
  | Gemma 3 1B IT int4 | ~0.6 GB | Free HF token | Google's smallest quality model |
- **Any model via custom URL** — point at your own server or mirror; must be
  a MediaPipe `.task` bundle (Hermes-3-3B path documented above).
- Downloads stream with progress to a `.part` file and atomically rename —
  a failed attempt never leaves a corrupt "installed" model.
- Gated models (Gemma) use your free Hugging Face token (Settings →
  Hugging Face) after you accept the license in a browser.
- An installed model is adopted automatically at startup; "Use"/"Delete"
  per model; the "no model installed" banner disappears once one exists.
- **The Hardware page shows disk truth:** "Currently Loaded" names the real
  model file (catalog display name when recognized), a Network card covers
  the Ollama LAN provider, catalog INSTALLED badges reflect files on disk,
  and custom-URL downloads get their own "Custom installs" section with
  Use/Delete — a custom download can never silently vanish again.

## Phase 2.5: Background Wake Word (Completed)

The wake phrase now works with the screen off, the way Android allows it:

- **Foreground service** (`WakeWordService`) with
  `foregroundServiceType="microphone"` — the only sanctioned way to hold the
  mic in the background. A persistent low-importance notification is always
  visible, with a **Stop** action that disables background listening.
- **Explicit opt-in only:** Settings → "Background wake phrase" (off by
  default, persisted). The service starts when the app goes to background and
  only when wake phrase + background listening + mic permission all hold.
- **Clean handoff:** service starts in `onPause`, stops in `onResume` — the
  in-app engine and the service never hold the mic at once.
- **Screen-off flow:** say the wake phrase → the companion answers "Yes?"
  aloud → speak your command → a high-priority notification delivers it;
  tapping opens Meowmba with the command already streaming through the normal
  pipeline. (Android 10+ blocks background activity launches, so a
  notification is the honest, policy-compliant bridge.)
- Battery note: continuous recognition in the background costs power — the
  trade-off is stated in the toggle's description, and recognition prefers
  on-device.

## Phase 3: Model Routing (Completed)

- **Live streaming inference:** Ollama (NDJSON `/api/generate`) and OpenAI-compatible (SSE `/v1/chat/completions` — also covers llama.cpp/vLLM-style servers), plus on-device MediaPipe LLM Inference. Line parsing lives in pure, unit-tested `StreamParsers`.
- **Honest networking:** HTTP status is checked before parsing, a missing API key fails fast instead of sending `Bearer null`, connect timeout 10s / generation timeout 5min, and empty completions are treated as errors, never spoken as success.
- **Honest failure UX:** provider errors surface as the red OFFLINE halo with an explanatory caption, then settle back to idle — nothing is ever faked.
- **Hardware page upgrades:** "Change" opens a provider picker bottom sheet; "Check local file" verifies the on-device model exists; "Test cloud" pings the server health endpoint (`/api/tags` or `/v1/models`) and reports model counts or the exact failure.
- **Echo-tolerant barge-in (live listening during audio):** the recognizer stays live while the companion speaks. `SelfVoiceFilter` tracks the utterance via TTS word boundaries and suppresses anything matching the companion's own remaining speech (echo lags are handled with a lookback window). The wake phrase or any novel user speech interrupts instantly; the companion never interrupts itself.

## Phase 2: Real Voice Pipeline (Completed)

- **Provider-neutral speech layer:** `SpeechEngine` / `SpeechSynthesizer` interfaces — Whisper, sherpa-onnx, Vosk, Piper, or cloud speech APIs can be swapped in without touching the ViewModel or avatar.
- **Wake phrase:** "Hey Memba", "Hey Meow", and "Hey *[custom name]*" (rename in Settings). `WakePhraseMatcher` is pure Kotlin and unit-tested; `RecognizerWakeWordEngine` runs a local restart-on-complete recognition loop behind a swappable `WakeWordEngine` interface (Porcupine/sherpa-onnx KWS can replace it later). Runs on-device while the app is open; a justified foreground service for background listening is a later phase.
- **Conversation mode:** after responding, the companion stays for follow-ups (8s timeout); say "stop" / "never mind" / "thanks" to end politely.
- **Interruptible speech:** say the wake phrase — or tap the mic — while the companion is talking to cut it off and speak a new command. Only phrase matches interrupt, so the companion never cancels itself hearing its own voice.
- **Audio focus:** requested for the whole voice interaction (duck other audio), released on idle.
- **Mouth sync:** TTS word-boundary callbacks (API 26+) pulse `mouthEnergy`, amplifying the avatar's mouth in rhythm with real speech; older devices fall back to the base talk cycle.
- **Voice modes:** push-to-talk, tap-to-toggle, wake phrase, and conversation mode — all persisted in encrypted preferences, all off by default until enabled in Settings.

## Phase 8: Avatar — invented animations (started)

The companion can **invent its own face animations** and perform them:

- **`avatar.animate` tool:** the agent brain writes a name + JSON keyframes
  (sparse pose overrides — `eye_open`, `ear_perk`, `head_tilt`, `happy_eyes`,
  `mouth`, `glow`, …). The clip plays over the state-machine pose and the face
  returns to normal when it ends.
- **Propose/constrain, like every tool:** `CustomAnimations.parse` validates
  structure, clamps out-of-range values (and reports every clamp in the tool
  result), and rejects broken specs with the reason. Privacy marks (mic/camera
  X, dim, MUTED mouth) are not animatable — an invented clip cannot fake a
  privacy state.
- **Per-field sparse sampling:** only the fields a keyframe names are animated
  (numbers smoothstep-interpolate; booleans/mouth/glow step), so a clip can
  wiggle just the ears while the eyes keep reacting to the conversation.
- **`save=true` persists** clips as JSON in `filesDir/custom_animations/`, so
  an animation the cat invented today still exists after a restart.
- Owned by Meow (the main agent) — self-expression, not a specialist skill.
  SAFE risk, no approval card. Pure-Kotlin pipeline, 14 unit tests.

## Phase 1: Working Avatar Shell (Completed)

Phase 1 establishes the foundation of the companion experience with a highly interactive and animated shell.

### The Avatar — "Memba 2" Face

The cat avatar uses the **Memba 2 animation design** (the face that runs on the LOOI desk robot in the Memba 2 project), rebuilt as animation-ready layered Compose artwork:

- **Resolution-independent line art:** every measurement derives from `s = min(width, height)`, so the face scales cleanly across phones, tablets, and portrait/landscape desk-dock mode.
- **Animation state machine** (`CatAvatarStateMachine`): maps the single observable `CompanionState` (interaction state × emotion × privacy flags) into a declarative `AvatarPose` (eye openness, pupil size, gaze bias, head tilt, ear perk/droop, mouth mode, privacy marks, halo color). Pure Kotlin, no Compose imports, fully unit-tested.
- **Animation registry** (`CatAnimationRegistry`): named clips (`idle.blink`, `idle.ear_twitch`, `speech.talk_cycle`, …) supply timing parameters, so the planned 100+ animations from the roadmap can be registered by name without touching drawing code.
- **Independent animation layers:** randomized blinking (with occasional double-blinks), ear twitches, breathing, gaze wander, whisker sway/flutter, mouth wobble, and a pulsing halo — all interruptible and blended via easing.

### Key Interactions

- **Eye tracking:** pupils follow your touch position, then drift back to an idle wander.
- **Touch zones (with exact face geometry):**
  - Tap either **ear** → microphone privacy toggle (X marks appear on the ears, face dims)
  - Tap either **eye** → camera privacy toggle (eyes nearly close, X marks in the pupils)
  - Tap the **mouth** → mute/unmute companion speech (muted X on the mouth)
  - Tap/hold the **forehead** → quick settings
  - Tap the **background** → Memba tile overlay
- **Stroke the cat** → it purrs (happy `^^` eyes, whisker flutter, haptic feedback).
- **Swipe up** → chat workspace · **Swipe down** → tile overlay.
- **Privacy dashboard:** working switches for mic/camera/voice, the accessible equivalent of the face touch zones (via the Privacy tile).

### Voice & Conversation

- Glowing microphone button with state-aware animations (listening / thinking / speaking).
- Push-to-talk via Android `SpeechRecognizer` with a mock fallback; Android TTS behind an interface.
- Animated cartoon captions that stream text in real-time.
- Chat workspace sends text through the same companion pipeline as voice.
- Mock model provider selector (Local / Network / Cloud) with secure Keystore-backed key storage.

### Architecture

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3 base)
- **Pattern:** MVVM with unidirectional data flow — one observable `CompanionState`
- **Animation:** Procedural Compose Canvas, state-machine-driven poses, registry-based clip timing
- **Persistence:** Room (conversation + memory), DataStore-ready encrypted storage via `SecureStorageManager`

### Testing

72 unit tests + instrumented UI tests:
- `MemoryCoreTest` — vector codec, embedder determinism/similarity, recall ranking + explanations, command parser, prompt builder, JSON export.
- `MemoryViewModelTest` — remember commands, last-statement capture, paused-memory honesty.
- `WakePhraseMatcherTest` — phrase matching, word boundaries, custom names.
- `VoicePipelineTest` — wake detection, interruption, echo-tolerant barge-in, conversation follow-ups, privacy cuts.
- `SelfVoiceFilterTest` / `StreamParsersTest` / `ModelRoutingViewModelTest` — Phase 3 echo suppression, SSE/NDJSON parsing, honest error surfacing.
- `AvatarStateMachineTest` / `CompanionZoneTest` / `ModelStatusTest` / `CompanionViewModelTest` — avatar poses, touch zones, Hardware page snapshot, transitions.
- `CompanionScreenTest` (instrumented) — mic button → listening caption.

## Build Instructions
1. Open the project in Android Studio (Koala or newer).
2. Sync Gradle dependencies.
3. Run the `app` module on an Android emulator or physical device (Min SDK 24).

## Project Roadmap
- **Phase 6:** Desktop bridge — P6a (pairing, signed channel) done; P6b+ file/build operations next.
- **Phase 7:** Vision (CameraX, face-follow, scene descriptions via multimodal model).
- **Phase 8:** Advanced avatar (expanded animation registry).
- **Phase 9:** Desk & car modes.
- **Phase 10:** Ecosystem.
- Also queued: llama.cpp provider (run any GGUF on-device).
