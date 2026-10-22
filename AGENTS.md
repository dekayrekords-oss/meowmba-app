# Meowmba — guide for AI contributors

Read `BUILD_MASTER.md` FIRST for the current recovery branch, build owner,
artifact verification and installation rules. Historical paths below are not
the current build authority. The latest handoff (Flow, Desk, Laya, context
journal, idle avatar; builds 158–166) is `docs/HANDOFF_CLAUDE_2026-09-23.md`.

## Android source publication restriction — owner instruction, 2026-09-13

Do not upload any Android app source code to public GitHub. This includes
source copied into public repositories, gists, issues, PRs, snippets, or public
website assets. A request to connect the website or videos to GitHub does not
authorize publishing the Android implementation. Keep app code in local Git or
owner-authorized private server repositories. Review destinations before any
push; never assume a remote is private from its name. Public website updates
may describe features without including Android source. Record changes and
Meow passdowns locally; a local commit is not a public push.

## Private GitHub synchronization — owner approved, 2026-09-28

The owner authorizes syncing Android source and project documentation to the private
repository `dekayrekords-oss/meowmba-app`. Verify its visibility is still private
before pushing. Whenever you push a work branch to the Meow server (`lan`, or
`origin` when it points to `meow:/home/claude/git/meowmba-app.git`), also push that
same branch to the `github` remote. Report a failed sync; do not imply it succeeded.
Use ordinary pushes, preserve remote history, and do not force-push or overwrite
`main` without specific owner authorization. Do not upload secrets, user data,
signing keys, model downloads, or local build artifacts.

Copy task-relevant plans kept outside the repository into `docs/` before syncing,
and keep their implementation/validation status accurate. The shared integration
branch is `feature/meow-flow-core`; consult `BUILD_MASTER.md` for build state.
Build 187 is work in progress, not an installed release. Fetch the relevant remote
branch before starting work and reconcile it with local changes. The historical
remote names and September 3 sync state below do not override current remotes.

## Active appearance revision and agent assignments

For the current user-requested animation/chat/orb work, read `docs/IMMERSIVE_COMPANION_PLAN.md` and `docs/AGENT_PLAN.md`. They supersede the older orb-ribbon direction, not the security rules or unrelated roadmap below.

Preferred implementation workers use this computer's OpenCode CLI with `opencode/big-pickle`; the executable/model listing is verified, authenticated request access is not. Do not imply local GPU inference merely because the CLI is local. No automatic paid-model fallback or unrestricted `--auto` execution.

Use bounded file ownership, preserve uncommitted changes, and keep one build owner. The integrator owns shared screen/state/ViewModel wiring. New priorities: diffuse floating-parameter Jarvis, retractable compact chat, expressive eyes, anti-repetition direction, safe scene/prop support, and measured optimization. No installation or data clearing without the separate user authorization required by the plan.

> Read this before changing the app. The README describes shipped phases; this
> file is the working state + how to not break things.

## What this is

**Meowmba** (the app is called "Memba") is the Android control-center companion:
animated cat avatar, wake-word voice pipeline, on-device/server/cloud model
routing, long-term memory (Room), a policy-gated agent/tool system, and a
desktop bridge. Package `com.example.meowmba`, minSdk 31, Compose Material3,
single `:app` module.

Repo: `/Users/bobrossjr/AndroidStudioProjects/Meowmba`
Remotes:
- `lan` — primary, `meow:/home/claude/git/meowmba-app.git` (claude account on the server)
- `prog` — same server, prog account: `prog@192.168.1.69:/home/prog/git/meowmba-app.git`
- `origin` — the notebook, `meow-nb:/home/claude/git/meowmba-app.git`

**Sync state (2026-09-03):** `lan` is the CANONICAL repo — it is the one that
gets the real work, and its `main` is what the phone runs. `prog`'s bare repo
was 54 commits behind (stuck at `867f275`, Aug 25) because everything landed on
`lan` after that; both are now at `9b6006e` (v1.3.0), and `prog-main` was
fast-forwarded to the same commit. The `meowmba-130` worktree is now at
`2def58c` / v1.3.10 on `origin/main`; do not assume the older `main` ref or a
v1.3.0 worktree is current merely because its directory name is simpler.

**Fetch both before you build anything.** Building from `prog-main` while
`lan/main` had moved on shipped a build that silently reverted a week of work —
including last night's voice fixes (local Whisper) — onto the phone:

```bash
git fetch --all && git log --oneline -1 lan/main
```

**A fresh worktree CANNOT hear.** The voice runtime is gitignored
(`.gitignore`: `/app/src/main/jniLibs/`, `sherpa-onnx-kws-*`, `silero_vad.onnx`,
`speaker.onnx`), so a new worktree builds an app whose mic fails instantly with
`UnsatisfiedLinkError: libsherpa-onnx-jni.so not found` and
`onListeningError code=5`. Run this once per worktree, before the first build:

```bash
scripts/fetch_kws_assets.sh     # ~100MB: sherpa .so files, KWS model, VAD, ASR
```

Bump `versionCode` past whatever is on the phone (`adb shell dumpsys package
com.example.meowmba | grep versionCode`) so an install is always an upgrade, and
sign debug builds with meow's debug keystore (`debugKeystore=` in the gitignored
`local.properties`) or `adb install -r` fails with UPDATE_INCOMPATIBLE.
The repo sets `core.sshCommand` to `id_ed25519_meow` + `IdentitiesOnly`;
all remotes use that key. prog's password exists but its account
lockouts bite after a few failed password logins — always use the key.

## Build & test

### Existing Play release signing and upload path

The absence of `keystore.properties` on the Mac does not mean the upload key is
missing. The existing signing configuration is on SSH host `meow` at
`/home/claude/meowmba-app/keystore.properties`. Use it through
`-PreleaseSigningProperties=...` in an isolated release checkout on that host;
do not print or copy its secrets into the repository. The matching upload
certificate and successful build procedure are recorded in
`docs/store/SIGNED_RELEASE_1.3.40.md` and `SIGNED_RELEASE_1.3.20.md`.
The existing Play app is `com.meow.meowmba`, closed-testing Alpha. Inspect the
signed-in Play Console browser before assuming that upload access is missing.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest            # 320 unit tests, ~1 min
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` needs only `sdk.dir`. No tokens required to build.

### Known pre-existing test failures (NOT yours)

`ModelDownloadViewModelTest` has **3 failures on a clean HEAD**:
`Method setAction in android.content.Intent not mocked` — `CompanionViewModel.
downloadModel/downloadCustomModel` call `DownloadService.start()`, which builds
a real `Intent`. Fix is either `testOptions.unitTests.isReturnDefaultValues = true`
plus intent-capture, or Robolectric, or extracting the service start behind an
interface. If you touch downloads, fix these; otherwise don't confuse them with
failures from your change.

## Work state (as of 2026-09-03)

### Current release line — v1.3.10 (`2def58c`)

- Plugins & MCP Connectors replaced the old Tools tile. `McpHttpClient` handles
  remote HTTP MCP; `McpHttpServer` + `PhoneMcpServer` expose approved phone
  capabilities over LAN. Connector secrets remain in `SecureStorageManager`.
- The app gallery is curated in `core/plugins/AppConnections.kt`; live adapters
  live in `ServiceIntegrations.kt`. A card is not proof of a working service:
  connection tests and honest error/status text are mandatory.
- `SkillRunner` executes bounded on-device skills. It is not permission to add
  a general shell or bypass `AgentOrchestrator`.
- Gemini cloud ASR is selectable, but this is still transcription in the
  ASR→LLM→TTS cascade. Native Gemini Live audio is not shipped yet.
- The onboarding wizard (`SetupWizardRoute` + `SetupWiring`) guides local,
  free-cloud, and paid-cloud setup. Keep setup paths usable by non-developers.
- Home Assistant, Spotify, and GitHub have live clients. Other catalog entries
  may be setup templates or intent/deep-link integrations; label them exactly.

### Agent implementation plan (priority order)

1. Preserve deterministic routing, permissions, approval cards, and audit when
   adding any cloud, MCP, AppFunctions, or native-audio capability.
2. Finish remote MCP OAuth: protected-resource discovery, authorization-server
   discovery, PKCE, callback, encrypted refresh tokens, revocation, and logout.
3. Add native conversational audio behind a separate pipeline selector. Local
   wake detection, endpointing, deterministic commands, captions, and fallback
   must continue to work.
4. Build the installed-app catalog from PackageManager with real labels/icons.
   Classify each entry as remote MCP, Android AppFunctions, intent, or UI
   automation. Never advertise an ordinary installed app as an MCP server.
5. Treat Android AppFunctions as preview-gated. Android 16 provides the API,
   but cross-app discovery/execution requires `EXECUTE_APP_FUNCTIONS` and is
   currently restricted to approved system agents. Detect and explain denial.
6. Verify on the Fold with real accounts and inspect Room `audit_log`; passing
   unit tests alone is insufficient for account linking, audio, BLE, or OAuth.

1. **Invented avatar animations — COMMITTED** (`6d23205`; new `ui/companion/CustomAnimation.kt`,
   `core/agent/tools/AvatarAnimateTool.kt`; edits to `AgentModel.kt`,
   `CompanionState.kt`, `CompanionViewModel.kt`, `AvatarRenderer.kt` +
   `CustomAnimationTest.kt`, 14 tests, all green).
   - The companion invents its own face animations: `avatar.animate` takes a
     name + JSON keyframes (sparse pose overrides), validates/clamps via
     `CustomAnimations.parse` (every clamp reported in the result), plays the
     clip over the state-machine pose, and auto-clears. `save=true` persists
     clips as JSON files in `filesDir/custom_animations/`.
   - Owned by Meow (`main`) in `AgentRegistry` — self-expression, not a
     specialist skill. SAFE risk, no approval card.
   - Same trust boundary as every tool: the model proposes,
     `CustomAnimations` constrains. Privacy marks (mic/camera X, dim, MUTED
     mouth) are NOT animatable — a clip cannot fake a privacy state.
   - Sampling is per-field sparse: a clip can wiggle just the ears while the
     eyes keep reacting. Numbers smoothstep-lerp; booleans/mouth/glow step.
   - Not on the keyword fast path by design — mostly. After the on-device
     report ("it makes the animation but does not show me — it makes a file
     instead", fixed in `ed0f380`): trick/dance phrases route deterministically
     to `avatar.animate` with BUILT-IN clips (`ear wiggle`, `happy bop`,
     `curious tilt` — instant, no model call), and "play the X animation"
     replays built-ins/saved clips by name through the tool. The bare
     `"animate "` prefix was REMOVED from media.generate_video — it swallowed
     avatar performances ("animate yourself" became a video OF "yourself").
     Invented keyframes still come only from the LLM brain.
   - **Self-performance disambiguation (2026-07-31):** the on-device brain sent
     "animation of you licking your paw" to `media.generate_video` → a FILE,
     while the user watched the thinking face + ambient ear twitch and called
     it "60 seconds of ear wiggle". Fixes: `avatar.animate`'s description leads
     with "PERFORM on its own face… never a media tool", the video tool says it
     only makes files, and BOTH tool-call prompt contracts (`ToolCallPrompt`
     CONTRACT + HERMES_NATIVE) carry the rule "self-performance is
     avatar.animate, never media.*". Pinned in `ToolCallPromptTest`.

## Debugging on-device behavior (read this before guessing)

The audit log + chat history are IN Room on the phone, and the debug build is
debuggable — pull them instead of theorizing:

```bash
adb exec-out run-as com.example.meowmba cat \
  /data/data/com.example.meowmba/databases/meowmba_database > /tmp/meowmba.db
sqlite3 /tmp/meowmba.db \
  "SELECT * FROM audit_log ORDER BY id DESC LIMIT 20;"
sqlite3 /tmp/meowmba.db \
  "SELECT role, substr(content,1,140) FROM messages ORDER BY timestamp DESC LIMIT 8;"
```

`audit_log` = every task submit/start/complete/fail with timestamps;
`messages` = what the app actually heard and said. Two production bugs were
diagnosed from this table in one day ("email compose" and "paw lick").
Saved custom animations are plain files:
`adb exec-out run-as com.example.meowmba ls /data/data/com.example.meowmba/files/custom_animations/`

**Context window facts:** chat prompts carry persona + capabilities + recalled
memories + the **last 10 messages** (`getRecentMessages(10)`); the tool-routing
brain (`LlmToolCallRouter`) decides from the CURRENT UTTERANCE ONLY — except
short follow-ups (≤4 words, "yes", "do it"), which now get the last 2 chat
messages attached (`followUpContextFor`) after a bare "yes" invented a random
60ms clip on device. Clips under 500ms are stretched to 500ms (reported like
every clamp) — a 60ms performance is indistinguishable from "nothing happened".

   - **The chat model can FAKE tool results (2026-07-31, root cause of a
     week-of-one-day bug chain):** "lick your paw"/"yes" failed
     `CommandHeuristics` (no signal words) → the CHAT model answered → with
     real tool results in its last-10 history it MIMICKED them (`Playing
     "head tilt" (30ms)` — no task in `audit_log`, store empty, clip
     nonexistent). Diagnosis rule: **a "played/did X" claim with no matching
     audit entry is a hallucination, not a tool bug.** Gate now passes
     performance words + affirmations; the capabilities prompt forbids
     imitating tool results. Also learned: Room WAL means `cat` of the main
     DB file alone misses fresh rows — pull `meowmba_database-wal` too, and
     verify the RUNNING process predates the last install (`ps -o ETIME`)
     before trusting on-device behavior.

   - **Crash anatomy worth knowing:** asked to "make an animation called lick
     your paw", the model emitted `frames` as a REAL JSON array —
     `ToolCallParser`'s `value.jsonPrimitive` threw on the main thread and
     took the whole app down (`6bf9543`). The parser now never throws and
     structured args ride as JSON text, which is exactly what
     `avatar.animate`'s `frames` parses. Same fix in `HermesAgentBrain`.
     Regression-pinned in `ToolCallParserTest`.

2. **Email/app routing fix — COMMITTED** (`96ca7d3`; `AgentRouter.kt`,
   `EmailTools.kt` + `AppAndEmailRoutingTest.kt`, 15 tests, all green).
   - Reported bug: "open gmail and write a message that says hi to X@Y.com"
     → `No installed app matches "gmail and write a message that says hi to…"`.
   - Fix: `composeEmail()` runs BEFORE the app-launcher rule and fires on any
     address + compose cue anywhere in the sentence; `boundTarget()` cuts
     extracted values at sentence boundaries (" and ", " then ", " to ", …);
     app names longer than 3 words are rejected ("open up about your feelings"
     stays conversation).
   - `EmailSendTool.mailtoUri(to, subject, body)`: subject/body go in the
     mailto **query string**, not just extras — measured on device, Gmail's
     external compose ignores `EXTRA_TEXT` for `ACTION_SENDTO`. Percent-encode,
     never `+` for space.
   - Finished 2026-07-31: subject extraction is mid-sentence (`indexOf`, gated
     by `bodyStart` so "saying the subject is green" stays a message, not a
     subject); "draft …" routes to `email.draft` (a draft saves a file, it does
     not open the composer); the draft recipient is bounded at conjunctions.
   - Gotcha that bit once: `extractAfter` is PREFIX-only — never use it for
     mid-sentence extraction.

3. **Runtime IPC bridge for sibling apps — COMMITTED** (`01dc16f`;
   `bridge/MeowmbaRuntimeService.kt`, `core/memory/IpcMetaMemoryConnector.kt`).
   - `MeowmbaRuntimeService`: exported Messenger service, action
     `com.example.meowmba.action.RUNTIME_BRIDGE`, already declared in the
     manifest. Msgs: `PING=1`, `DESCRIBE=2` (jpeg→caption via VisionProvider),
     `GENERATE=3` (text via Ollama/MediaPipe provider), `PUSH_MEMORY=4`
     (→ Room, tagged `meta_glasses`/`meta_memory`), `PULL_MEMORIES=5`.
     Caller allowlist by UID→package: self, `com.meow.metamemory`,
     `com.meow.inventory`.
   - **Wired 2026-07-31:** `CompanionViewModel.metaMemory` lazily builds the
     real `IpcMetaMemoryConnector` from `secureStorage.context` +
     `memoryRepository` (constructor-injectable for tests, no-op when either
     is absent — same pattern as `inventoryClient`). The Memory screen shows
     an honest link line ("MetaMemory linked" only when the app is actually
     installed; `CompanionState.metaMemoryLinked`, refreshed on screen open).
     `push`/`pull` still have no callers by design: captures land in Room
     directly via `MSG_PUSH_MEMORY`, so there is no sync button to wire.
   - Contract documented from the other side in
     `~/meta-memory-app/docs/MEOWMBA_BRIDGE.md`. Sibling apps:
     `~/meta-memory-app` (Meta glasses capture, has its own AGENTS.md),
     `~/AndroidStudioProjects/MeowInventory`.

## Conventions that matter here

- **Honesty over polish:** never fake a capability — unavailable adapters are
  badged UNAVAILABLE, errors surface verbatim, empty completions are errors.
  Match this in UI copy and tool results.
- **Comment style:** non-obvious rules get a KDoc explaining the *why*, often
  with the observed device behavior ("measured on device: …"). Keep it.
- **Deterministic fast path first:** `AgentRouter` keyword routing runs before
  any model call; model only proposes, orchestrator's permission checks decide.
- **No shell executor, ever** — phone side and desktop bridge side. Destructive
  tools are approval-card gated (60s auto-deny).
- **Security:** secrets in Keystore-backed `SecureStorageManager`; API 30+
  package visibility means every cross-app interaction needs a `<queries>`
  entry in the manifest (already there for metamemory/inventory/maps/speech).
- **Tests are pure-JVM where possible** — `android.net.Uri` etc. are not
  mocked; put string/URI logic in static/companion functions (see `mailtoUri`)
  and test those.
- Room is at **v7** (v6→v7 added `characters`); bump + migrate, never
  `fallbackToDestructiveMigration`.

## Content studio — ON DEVICE (2026-08-24)

Meow makes comics/scripts/ads inside the app and SHOWS them: every visual tool
returns its artifact path on a `MEDIA_FILE:` line, and `onTaskCompleted` opens
it straight in the browser WebView overlay. Claiming "I made X" without that
line = faking a tool result — the exact complaint this feature fixes.

- **Tools** (`core/agent/tools/StudioTools.kt`, agent "Ink" id `studio`):
  `studio.save_character` / `studio.get_character` (locked design sheets),
  `studio.create_comic` (`panels` = JSON array of {caption, dialogue,
  description}; named characters' sheets are embedded in the page's CAST
  footer so comic + cartoon versions stay identical), `studio.write_script`
  (kind=script/ad/post → scripts|ads|posts dir).
- **Character memory** is the Room `characters` table (name PK, upsert) —
  deterministic by design; do NOT store sheets as ordinary memories (fuzzy
  recall was the whole problem).
- **Renderers** are pure JVM (`core/studio/ComicRenderer.kt`), tested in
  `StudioTest.kt` with an in-memory CharacterDao fake.
- **UI**: Studio tile → `StudioScreen` lists saved characters + everything in
  workspace `comics|scripts|ads|posts`; tap reopens the artifact in the same
  browser overlay used at creation time.

## House style for commits

`<type>(<area>): <plain-english what + why>` — e.g.
`fix(chat): "what model are you using" answers instead of promising to look`.
Recent types: feat/fix/chore; areas: voice, weather, agents, models, chat,
inventory, calendar.
