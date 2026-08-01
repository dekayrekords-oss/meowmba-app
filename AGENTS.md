# Meowmba — guide for AI contributors

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

**Sync state (2026-07-31):** `lan` and `prog` are current. `origin` (meow-nb)
is unreachable — Tailscale IP dead, nothing on the LAN accepts the meow key —
and is 6+ commits behind. When the notebook returns: `git push origin main`.
The repo sets `core.sshCommand` to `id_ed25519_meow` + `IdentitiesOnly`;
all three remotes use that key. prog's password exists but its account
lockouts bite after a few failed password logins — always use the key.

## Build & test

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

## Work state (as of 2026-07-31)

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
- Room is at **v4**; bump + migrate, never `fallbackToDestructiveMigration`.

## House style for commits

`<type>(<area>): <plain-english what + why>` — e.g.
`fix(chat): "what model are you using" answers instead of promising to look`.
Recent types: feat/fix/chore; areas: voice, weather, agents, models, chat,
inventory, calendar.
