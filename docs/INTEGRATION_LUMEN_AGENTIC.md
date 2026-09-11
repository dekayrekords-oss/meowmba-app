# Lumen nervous system + agentic integration (Phase 0–8 scaffolding)

Status: scaffolding only. **No FlyWire data or assets.** Feature flags default **OFF**.

## Goals

- Introduce a small **Lumen reflex v2** layer (drive state + action competition) that can modulate `LumenInput` before `LumenRig.step`.
- Reserve an **agentic runtime v1** flag for later orchestration work.
- Keep prior animation behavior unchanged when flags are off.

## What this is not

- Not a FlyWire / connectome import.
- Not an always-on agent loop.
- Settings UI: Neural / Lumen section toggles `lumen_reflex_v2` (default OFF) plus reflex gain; agentic flag toggle included. Agentic production UX still later.

## Existing types this builds on

| Area | Location |
|------|----------|
| Lumen input / rig | `app/src/main/java/com/example/meowmba/ui/companion/LumenMotion.kt` — `LumenInput`, `LumenRig`, `LumenFrame` |
| Lumen draw path | `app/src/main/java/com/example/meowmba/ui/companion/AvatarRenderer.kt` |
| Vision / social cues | `app/src/main/java/com/example/meowmba/core/vision/` (`VisionProvider`, `SocialCues`, `AttentionFreshness`, …) |
| Prefs store | `SecureStorageManager.saveBooleanPreference` / `getBooleanPreference` |

## Feature flags (defaults OFF)

| Flag key | Helper | Meaning |
|----------|--------|---------|
| `lumen_reflex_v2` | `FeatureFlags.lumenReflexV2` | When on, `LumenReflexBridge` modulates composed `LumenInput` on the Lumen draw path. When off, the prior `LumenRig.step(...)` path is unchanged. |
| `agentic_runtime_v1` | `FeatureFlags.agenticRuntimeV1` | Reserved for agentic runtime wiring; plumbing only in this phase. |

Implementation: `app/src/main/java/com/example/meowmba/core/config/FeatureFlags.kt` (SecureStorage boolean prefs, default `false`).

## Reflex scaffolding

| Piece | Location |
|-------|----------|
| `ReflexState` (curiosity, socialAttention, fatigue, caution) | `core/lumen/ReflexState.kt` |
| `ActionCompetition` (hysteresis + cooldowns) | `core/lumen/ActionCompetition.kt` |
| Bridge → `LumenInput` | `ui/companion/lumen/LumenReflexBridge.kt` |

Gate: `AvatarRenderer` reads `FeatureFlags.lumenReflexV2` once per composition; only then calls `LumenReflexBridge.modulate` on the composed input before `lumenRig.step`.

## How to toggle flags

Debug broadcast (debug builds only), via existing `DebugConfigReceiver`:

```bash
# Enable Lumen reflex v2
adb shell am broadcast -n com.example.meowmba/.core.debug.DebugConfigReceiver \
  -a com.example.meowmba.debug.CONFIG --ez lumen_reflex_v2 true

# Enable agentic runtime v1 (plumbing only)
adb shell am broadcast -n com.example.meowmba/.core.debug.DebugConfigReceiver \
  -a com.example.meowmba.debug.CONFIG --ez agentic_runtime_v1 true

# Disable either
adb shell am broadcast -n com.example.meowmba/.core.debug.DebugConfigReceiver \
  -a com.example.meowmba.debug.CONFIG --ez lumen_reflex_v2 false
```

Force-stop and relaunch the app after toggling so composition re-reads prefs.

Programmatic: `FeatureFlags.from(context).setLumenReflexV2(true)` / `setAgenticRuntimeV1(true)`.

## Files changed (this phase)

- `docs/INTEGRATION_LUMEN_AGENTIC.md` (this file)
- `app/src/main/java/com/example/meowmba/core/config/FeatureFlags.kt` (new)
- `app/src/main/java/com/example/meowmba/core/lumen/ReflexState.kt` (new)
- `app/src/main/java/com/example/meowmba/core/lumen/ActionCompetition.kt` (new)
- `app/src/main/java/com/example/meowmba/ui/companion/lumen/LumenReflexBridge.kt` (new)
- `app/src/main/java/com/example/meowmba/core/debug/DebugConfigReceiver.kt` (debug extras)
- `app/src/main/java/com/example/meowmba/ui/companion/AvatarRenderer.kt` (gated hook)

## Next phases (out of scope here)

- Feed real vision/`SocialCues` into `ReflexDynamics` (still no FlyWire).
- Agentic runtime v1 scheduler behind `agentic_runtime_v1`.
- Optional Settings toggles for non-debug builds.

## Phase 3 — Agentic runtime stubs (behind `agentic_runtime_v1`)

Status: **compiling stubs only**. Default remains **OFF**. Not wired into main chat / Lumen UX (callers optional / debug). No FlyWire. No SSH executor.

### What was added

| Piece | Location |
|-------|----------|
| Goal / task / event models | `core/agentic/AgenticModels.kt` |
| DAO-shaped in-memory store | `core/agentic/AgenticGoalStore.kt` (`AgenticGoalStore`, `InMemoryAgenticGoalStore`) |
| Facade | `core/agentic/AgenticRuntime.kt` — `createGoal`, `appendEvent`, `listOpenGoals` |

Room is now **v10** with `agentic_goals` / `agentic_events` / `agentic_connection_profiles` and `MIGRATION_9_10`. Runtime uses `RoomAgenticGoalStore` / `RoomConnectionProfileStore` (Empty/InMemory retained for tests).

### Behavior

| Flag | `createGoal` / `appendEvent` | `listOpenGoals` |
|------|------------------------------|-----------------|
| OFF (default) | return `null` (no-op) | empty list |
| ON | write to process-local in-memory store | open goals from store |

```kotlin
val runtime = AgenticRuntime.from(context)
if (FeatureFlags.from(context).agenticRuntimeV1) {
    val goal = runtime.createGoal("example")
    runtime.appendEvent(goal!!.id, type = "progress", detail = "stub")
    val open = runtime.listOpenGoals()
}
```

### How to enable

Same adb path as above:

```bash
adb shell am broadcast -n com.example.meowmba/.core.debug.DebugConfigReceiver \
  -a com.example.meowmba.debug.CONFIG --ez agentic_runtime_v1 true
```

Force-stop and relaunch after toggling. Programmatic: `FeatureFlags.from(context).setAgenticRuntimeV1(true)`.

### Out of scope (later)

- Room tables + migration for goals/tasks/events
- Scheduler / tool loop
- Main UX wiring

## Phase 4 — Connection profiles + SSH exec scaffolding (behind `agentic_runtime_v1`)

Status: **scaffolding compiled**. Default remains **OFF**. No FlyWire. Not wired into main chat UX.

### What was added

| Piece | Location |
|-------|----------|
| `ConnectionProfile` / `ConnectionProfileType` (`SSH` \| `HTTPS_RUNTIME`) | `core/agentic/ConnectionProfile.kt` |
| `ConnectionProfileStore` (+ Secure / Empty / InMemory) | `core/agentic/ConnectionProfileStore.kt` |
| `CredentialResolver` (SecureStorage stub) | `core/agentic/CredentialResolver.kt` |
| `AgenticSshExecutor` / `DefaultAgenticSshExecutor` / `SshExecResult` | `core/agentic/AgenticSshExecutor.kt` |
| Runtime entry points | `AgenticRuntime`: `listTools`, `registerTool`, `sshExec`, profile CRUD |

### Behavior

| Flag | Profiles / tools | `sshExec` |
|------|------------------|-----------|
| OFF (default) | empty / false | `SshExecResult.NotEnabled` |
| ON | SecureStorage-backed profiles; tool registry | Resolve `credentialRef`; try ProcessBuilder `ssh`/`sshpass` if present; else `Unsupported` or clear credential error |

Design choice: **honest on-device stub with optional real ProcessBuilder**. The APK does not ship OpenSSH. `DefaultAgenticSshExecutor` probes Termux / system `ssh` + `sshpass`; if missing, returns `SshExecResult.Unsupported` documenting Termux/runtime-worker needs. Password material is never placed on argv (uses `SSHPASS` env when sshpass exists). Existing chat-agent `SshTool` (JSch + `SshHostRegistry`) stays separate — it still stores passwords in encrypted prefs for the allowlisted agent path; Phase 4 profiles use **credentialRef only**.

### Still Termux / phone-side

- Providing an actual `ssh` / `sshpass` binary (Termux) or an `HTTPS_RUNTIME` worker
- Host-key verify against `hostKeyFingerprint` via known_hosts
- Settings UI to create profiles / write `agentic_cred_<ref>` secrets
- Bridging agentic `sshExec` to JSch `SshTool` (optional later)
- Scheduler / tool loop invoking `sshExec` from goals

### Security notes

- **No secrets in prompts or `AgenticEvent.detail`** — events log profile id, type, host, credential **ref**, exit code only.
- `ConnectionProfile` never carries password plaintext; use `SecureStorageCredentialResolver` / `pref_agentic_cred_<ref>`.
- Do not log `SSHPASS` or resolver output.
- Flag default **OFF**; enable via existing debug broadcast / `FeatureFlags.setAgenticRuntimeV1`.

```kotlin
val runtime = AgenticRuntime.from(context)
runtime.registerTool("demo", "example")
val tools = runtime.listTools() // empty when flag OFF
val result = runtime.sshExec(profileId = "…", argv = listOf("uname", "-a"), goalId = goal?.id)
```

### Files touched (Phase 4)

- `core/agentic/ConnectionProfile.kt` (new)
- `core/agentic/ConnectionProfileStore.kt` (new)
- `core/agentic/CredentialResolver.kt` (new)
- `core/agentic/AgenticSshExecutor.kt` (new)
- `core/agentic/AgenticRuntime.kt` (wired)
- `docs/INTEGRATION_LUMEN_AGENTIC.md` (this section)


## Phase 5 — Model-driven agent loop scaffold

Status: **scaffolding compiled**. Default remains **OFF**. Heuristic / stub planner; no production LLM required.

### What was added

| Piece | Location |
|-------|----------|
| `AgenticBudgets`, expanded `GoalStatus` (`RUNNING` / `BLOCKED` / `WAITING_APPROVAL`), `ProposedToolCall` | `core/agentic/AgenticModels.kt` |
| `ModelAdapter` / `Stub` / `HttpOpenAiCompatible` / `AgenticModelConfig` | `core/agentic/ModelAdapter.kt` |
| `AgenticPlanner` / `HeuristicAgenticPlanner` (`AgentLoop` alias) | `core/agentic/AgenticPlanner.kt` |
| Store: `getGoal` / `updateGoal` / budgets on insert | `core/agentic/AgenticGoalStore.kt` |
| `AgenticRuntime.tick(goalId)` / `step(goalId)` | `core/agentic/AgenticRuntime.kt` |

### Behavior

| Flag | `tick` / `step` |
|------|-----------------|
| OFF (default) | return `null` (no-op) |
| ON | load goal → planner propose → append `model_attempt` → if `ssh_exec` call executor → update budgets/status |

Planner is heuristic: calls `StubModelAdapter.complete` (canned plan), then picks `ssh_exec` (benign `uname -a`) when an SSH profile exists, else `noop`. Respects `AgenticBudgets` (`maxSteps` / `maxToolCalls` / `maxSshExecs`). Budget exhaustion → `BLOCKED`.

```kotlin
val runtime = AgenticRuntime.from(context)
val goal = runtime.createGoal("probe host")
runtime.tick(goal!!.id) // or step(goal.id)
```

Optional later: replace `StubModelAdapter` with an API-backed `ModelAdapter` (same interface). **No secrets in prompts / event detail.**

## Phase 6 — Lumen ↔ agentic coupling

Status: **presence mapping only**. Does **not** drive SSH from the Lumen frame loop.

| Piece | Location |
|-------|----------|
| `AgenticPresence` / `CompanionAgenticHooks` | `ui/companion/CompanionAgenticHooks.kt` |
| Optional state field | `CompanionState.agenticPresenceHint` |
| Flag-gated call site | `CompanionViewModel.refreshAgenticPresenceHint()` (called once from prefs init) |

Mapping: `WAITING_APPROVAL`→ALERT, `BLOCKED`→CONCERNED, `RUNNING`→DETERMINED, `OPEN`→CURIOUS (+ hint string). Applied **only** when `agentic_runtime_v1` is ON; otherwise hint cleared / null.

## Phase 7 — Memory notes

Status: **SharedPreferences persistence** (`PrefsAgenticMemoryIndex`). Existing `MemoryCore` / `MemoryRepository` / recall path **untouched**.

| Piece | Location |
|-------|----------|
| `AgenticMemoryIndex` / `NoOp` / `InMemory` / `Prefs` | `core/agentic/AgenticMemoryIndex.kt` |
| Runtime helpers | `AgenticRuntime.noteMemory` / `recallMemory` |

### Git / second-brain (later)

A future second-brain can:

1. Implement `AgenticMemoryIndex` over a Git-backed vault or note store.
2. Keep companion `MemoryCore` (`VectorCodec`, `RecallEngine`, `PromptBuilder`) as the chat recall path — do **not** merge namespaces without an explicit migration.
3. Index only sanitized goal notes (ids, statuses, tool names) — never SSH passwords / credential material.

## Avatar frame cadence (~60 FPS when cool)

`AvatarFrameCadence` default **active** admit interval is **16 ms (~60 FPS)**; **idle/decorative** ambient is **33 ms (~30 FPS)**. Previously active was ~32 ms (~30 FPS) and idle ~66 ms (~15 FPS).

Thermal path is unchanged: `AvatarWorkPolicy.allowAvatarWork` returns false at `THERMAL_STATUS_MODERATE`+, which sets `animationsPaused` and stops decorative redraw / scene clocks. Cadence never overrides that throttle.

Optional: when `lumen_reflex_v2` is ON, gaze / head-follow springs in `AvatarRenderer` use slightly higher stiffness (and a shorter idle settle tween). Flag-off keeps the prior spring values.

## Phase 8 — Hardening checklist

### Completion checklist

- [x] Feature flags default **OFF** (`lumen_reflex_v2`, `agentic_runtime_v1`)
- [x] Reflex bridge gated in `AvatarRenderer`
- [x] Agentic goal / event / profile / sshExec scaffolding
- [x] Planner + `tick`/`step` behind flag
- [x] Presence hooks optional + flag-gated (not in Lumen frame loop)
- [x] Agentic memory index stub; MemoryCore unchanged
- [x] No FlyWire data/assets/imports
- [x] No opencode
- [x] Room migration for goals/tasks/events (v9→v10: agentic_goals / agentic_events / agentic_connection_profiles)
- [x] Real `ModelAdapter` (HTTP OpenAI-compatible; stub fallback)
- [x] Settings UI for Lumen reflex / agentic flag toggles (Neural / Lumen)
- [x] Settings UI for profiles / credential refs (Neural / Lumen → AgenticProfilesSection)
- [x] Host-key verification / known_hosts (fingerprint on ConnectionProfile; TOFU pin policy)
- [x] Production approval UX for `WAITING_APPROVAL` (Companion approve/reject dialog)
- [x] Persist agentic goals across process death (RoomAgenticGoalStore)
- [x] Termux discovery + Capability probe + JSch fallback (HTTPS_RUNTIME worker packaging still later)

### Failure matrix

| Failure | Observed result | User impact (flag OFF) | User impact (flag ON) |
|---------|-----------------|------------------------|------------------------|
| Flag OFF | All agentic APIs no-op / empty / `NotEnabled` | None | N/A |
| Missing SSH binary | `SshExecResult.Unsupported` | None | Goal → `BLOCKED`; event logged |
| Missing credentialRef | `SshExecResult.Error` | None | Goal → `BLOCKED`; no secret logged |
| Budget exhausted | `noop` + `budget_exhausted` event | None | Goal → `BLOCKED` |
| Unknown tool from planner | `unknown_tool` event | None | Goal → `BLOCKED` |
| Stub model only | Canned plan text | None | Heuristic still picks ssh/noop |
| Presence hook error | Should be isolated in ViewModel | None | Hint may stay null |

### Non-FlyWire audit note

Search / review confirmed this integration path does **not** import FlyWire connectome data, assets, or network endpoints. Lumen reflex uses local `ReflexState` / `ActionCompetition` only. Agentic runtime is orchestration scaffolding (goals, profiles, SSH exec stub), unrelated to neural connectome datasets.

### How to enable flags

```bash
# Lumen reflex v2
adb shell am broadcast -n com.example.meowmba/.core.debug.DebugConfigReceiver \
  -a com.example.meowmba.debug.CONFIG --ez lumen_reflex_v2 true

# Agentic runtime v1 (unlocks profiles, sshExec, tick/step, presence hint)
adb shell am broadcast -n com.example.meowmba/.core.debug.DebugConfigReceiver \
  -a com.example.meowmba.debug.CONFIG --ez agentic_runtime_v1 true
```

Force-stop and relaunch after toggling. Programmatic: `FeatureFlags.from(context).setAgenticRuntimeV1(true)`.

### Stub vs real

| Surface | Stub now | Real later |
|---------|----------|------------|
| `ModelAdapter` | `StubModelAdapter` canned text | API / on-device LLM complete() |
| `AgenticPlanner` | Heuristic ssh_exec / noop | Model-parsed tool calls + approvals |
| Goal store | In-memory | Room + migration |
| `AgenticSshExecutor` | ProcessBuilder ssh/sshpass if present else Unsupported | Termux binary or HTTPS_RUNTIME worker + host-key checks |
| Credentials | SecureStorage ref lookup | Settings UI + keystore-backed secrets |
| `AgenticMemoryIndex` | In-memory notes | Git / vault second-brain |
| Presence | Hint string on CompanionState | Optional emotion rehearsal + approval card |
| Lumen reflex | Rich `ReflexSensoryFeed` + ActionCompetition (flag OFF default) | Optional tuning / more VAD when mic RMS is exposed |

## Lumen synapse status (this pass)

Done (behind `lumen_reflex_v2` / `agentic_runtime_v1`, defaults **OFF**, no FlyWire):

### Lumen / nervous (“synapse”) — DONE
- [x] **Rich feed into ReflexState** — `ReflexSensoryFeed` from VisionManager / FaceTrack / SocialCues / AttentionFreshness / FollowSource / speechLikely / trackingId, published each vision tick from `CompanionViewModel` into `LumenReflexBridge.modulate`. Curiosity rises on novel targets + loss-of-track recovery; socialAttention on face lock + talker; fatigue on continuous tracking time; caution on stale/loss/thermal. `ActionCompetition` still uses those drives with hysteresis.
- [x] **Settings UI** — Settings category **Neural / Lumen** uses `NeuralSettingsSection` to toggle `lumen_reflex_v2`, `agentic_runtime_v1`, and reflex gain slider (0.25–1.5).
- [x] **Presence UI** — `PresenceHintChip` shows `agenticPresenceHint` and/or `reflexSummary` when non-null and flags warrant.
- [x] **Extra drives** — pet/affection pulse, speech energy / loud (mouthEnergy + mouthOpen), reply-affect/emotion arousal, drive IMU / drive-G mapped into `ReflexSensoryFeed`.
- [x] **No FlyWire / connectome** (intentional; keep it that way unless licensing is explicit).

### Still open — Agentic runtime → production
- ~~**Room persistence + migration**~~ — done (`MeowmbaDatabase` v10 + `RoomAgenticGoalStore`).
- ~~**Real `ModelAdapter`**~~ — `HttpOpenAiCompatibleModelAdapter` via SecureStorage refs; stub if unset.
- ~~**Settings UI** for connection profiles / `agentic_cred_<ref>` secrets~~ — done (`AgenticProfilesSection`).
- ~~**Termux path**~~ — improved binary discovery + `AgenticSshCapability` + JSch fallback; RUN_COMMAND documented; HTTPS_RUNTIME worker later.
- ~~**Host-key verification / known_hosts**~~ — done (probe + pin / mismatch reject).
- ~~**Production approval UX** for `WAITING_APPROVAL`~~ — Companion approve/reject dialog.
- ~~**Scheduler / tool loop**~~ — Agentic / Tasks section: create goal, tick / tick-open, approval.
- ~~**AgenticMemoryIndex**~~ — `PrefsAgenticMemoryIndex` (SharedPreferences by project/goal); Git/vault later.
- **Bridging agentic `sshExec` ↔ JSch `SshTool`** (optional).

### Files touched (Phases 5–8)

- `core/agentic/AgenticModels.kt`
- `core/agentic/AgenticGoalStore.kt`
- `core/agentic/ModelAdapter.kt` (new)
- `core/agentic/AgenticPlanner.kt` (new)
- `core/agentic/AgenticMemoryIndex.kt` (new)
- `core/agentic/AgenticRuntime.kt`
- `ui/companion/CompanionAgenticHooks.kt` (new)
- `ui/companion/CompanionState.kt`
- `ui/companion/CompanionViewModel.kt`
- `docs/INTEGRATION_LUMEN_AGENTIC.md` (this file)


### Files touched (synapse pass)

- `core/lumen/ReflexState.kt` (`ReflexSensoryFeed` + rich `ReflexDynamics`)
- `core/lumen/ActionCompetition.kt` (unchanged hysteresis consumer)
- `core/config/FeatureFlags.kt` (reflex gain pref)
- `ui/companion/lumen/LumenReflexBridge.kt` (rich feed + temporal novelty/loss)
- `ui/companion/CompanionViewModel.kt` (publish sensory each tick; flag setters)
- `ui/companion/CompanionState.kt` (`reflexSummary`, flag mirrors)
- `ui/companion/AvatarRenderer.kt` (feed into modulate)
- `ui/companion/NeuralSettingsSection.kt` / `SettingsScreen.kt` (Neural / Lumen page)
- `ui/companion/PresenceHintChip.kt` / `CompanionScreen.kt` (chip + wiring)
- `docs/INTEGRATION_LUMEN_AGENTIC.md`


## Terminal-clone plan

Condensed computer/terminal-clone setups (Termux / SSH host / HTTPS_RUNTIME), `ssh_exec` loop, and journals: [`TERMINAL_CLONE_AGENTIC.md`](./TERMINAL_CLONE_AGENTIC.md).
