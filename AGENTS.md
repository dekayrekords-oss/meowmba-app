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
Remotes: `lan` (primary, `meow:/home/claude/git/meowmba-app.git`), `origin` (meow-nb).
Local `main` is currently **31 commits ahead of `lan/main`** — unpushed work.

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

1. **Email/app routing fix — COMMITTED** (`96ca7d3`; `AgentRouter.kt`,
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

2. **Runtime IPC bridge for sibling apps — COMMITTED** (`01dc16f`;
   `bridge/MeowmbaRuntimeService.kt`, `core/memory/IpcMetaMemoryConnector.kt`).
   - `MeowmbaRuntimeService`: exported Messenger service, action
     `com.example.meowmba.action.RUNTIME_BRIDGE`, already declared in the
     manifest. Msgs: `PING=1`, `DESCRIBE=2` (jpeg→caption via VisionProvider),
     `GENERATE=3` (text via Ollama/MediaPipe provider), `PUSH_MEMORY=4`
     (→ Room, tagged `meta_glasses`/`meta_memory`), `PULL_MEMORIES=5`.
     Caller allowlist by UID→package: self, `com.meow.metamemory`,
     `com.meow.inventory`.
   - **OPEN THREAD — not yet wired:** `IpcMetaMemoryConnector` implements
     `MetaMemoryConnector` but nothing instantiates it — `MemoryRepository`
     and the memory UI still have no connector injected. If you pick this up,
     find the app container / ViewModel factory and inject it there.
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
