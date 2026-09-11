# Terminal-clone agentic plan (condensed)

Companion to [`INTEGRATION_LUMEN_AGENTIC.md`](./INTEGRATION_LUMEN_AGENTIC.md).
This is the **computer / terminal-clone** half of agentic runtime v1 — how Meowmba
reaches a remote shell without shipping OpenSSH inside the APK and without FlyWire.

## Three setups

| Setup | Where the shell runs | How Meowmba talks to it | When to use |
|-------|----------------------|-------------------------|-------------|
| **A — Termux on-device** | Same phone (`ssh` / `sshpass` under Termux prefix) | `DefaultAgenticSshExecutor` → `ProcessBuilder` (preferred) or Termux `RUN_COMMAND` | Dev / Fold with Termux; no separate worker |
| **B — SSH to a host** | Laptop / NAS / CI box | `ConnectionProfile` (`SSH`) + `credentialRef`; ProcessBuilder or **JSch fallback** | Day-to-day "drive my machine" |
| **C — HTTPS_RUNTIME worker** | Small agent on a trusted host | `ConnectionProfileType.HTTPS_RUNTIME` (scaffold; worker packaging later) | When the phone must not hold an SSH client |

Flags: everything stays behind `agentic_runtime_v1` (default **OFF**).

## Capability probe

Call `AgenticSshCapability.probe()` / `AgenticRuntime.sshCapability()`:

1. **process_builder_termux_or_system** — Termux/system `ssh`+`sshpass` executable via ProcessBuilder
2. **jsch_fallback** — in-process JSch using profile `credentialRef` (no OpenSSH binary)
3. **termux_run_command** — Termux prefix present but binaries not executable from Meowmba UID; enable Termux `allow-external-apps` + `com.termux.RUN_COMMAND`
4. **unsupported** — use HTTPS_RUNTIME worker

Termux discovery prefers `/data/data/com.termux/files/usr/bin/{ssh,sshpass,ssh-keyscan,ssh-keygen}` (also `usr/local/bin`).

### Termux RUN_COMMAND notes

- Action: `com.termux.RUN_COMMAND` → `com.termux.app.RunCommandService`
- Requires Termux **Allow external apps**
- Prefer ProcessBuilder when binaries are executable; Capability summary shows the active path
- Do **not** invoke RUN_COMMAND from the Lumen frame loop

## `ssh_exec` loop

1. Operator saves a **connection profile** (Settings → Neural / Lumen → Agentic profiles).
2. Password / key material only via SecureStorage (`agentic_cred_<ref>`).
3. Planner (`AgenticRuntime.tick`) proposes `ssh_exec` (HTTP `ModelAdapter` when configured, else stub).
4. **Budgets** cap steps / tool calls / SSH execs.
5. **Approval**: side-effecting argv → `WAITING_APPROVAL` until Companion approve/reject.
6. **Host-key**: fingerprint verified; TOFU pin when `pinHostKeyOnFirstConnect=true`.
7. Executor: ProcessBuilder → else JSch; sanitized events only.

## Model adapter

| Config (SecureStorage) | Meaning |
|------------------------|---------|
| `agentic_model_base_url` | OpenAI-compatible base URL |
| `agentic_model_api_key_ref` | Credential ref → `agentic_cred_<ref>` |
| `agentic_model_name` | Model id (default `gpt-4o-mini`) |

Unset / incomplete → `StubModelAdapter`. UI: **Agentic / Tasks** section.

## Journals / persistence

| Store | Backend | Survives process death |
|-------|---------|------------------------|
| Goals / events | Room `agentic_goals` / `agentic_events` (DB v10+) | Yes |
| Connection profiles | Room `agentic_connection_profiles` | Yes |
| Credentials | SecureStorage `agentic_cred_<ref>` | Yes |
| Agentic memory notes | SharedPreferences `agentic_memory_index` by goal/project (`PrefsAgenticMemoryIndex`) | Yes |
| Model adapter config | SecureStorage prefs + credential ref | Yes |

## Main UX

Settings → Neural / Lumen (flag ON):

- **Agentic / Tasks** — create goal, tick / tick-open, list open goals, approve/reject, model config, SSH capability
- **Agentic connection profiles** — SSH targets + credential refs
- Companion presence chip + approval dialog for waiting SSH

## Security reminders

- No secrets in `AgenticEvent.detail`, planner prompts, or UI logs.
- Host-key mismatch is hard-fail.
- Do **not** drive SSH from the Lumen frame / avatar loop.
- No FlyWire / connectome imports on this path.

## Pointers

- Integration checklist: [`INTEGRATION_LUMEN_AGENTIC.md`](./INTEGRATION_LUMEN_AGENTIC.md)
- Code: `app/src/main/java/com/example/meowmba/core/agentic/`

- [`LOCAL_LINUX.md`](./LOCAL_LINUX.md) — Computer (Linux guest) vs SSH (honest limits; Off/Idle/On)

## HTTPS_RUNTIME worker client (on-device)

`HttpsRuntimeClient` talks to an operator-hosted worker. The worker is **not**
shipped inside the APK.

### Stub protocol

| Method | Path | Body / response |
|--------|------|-----------------|
| GET | `/health` | → `{ "ok": true }` |
| POST | `/v1/jobs` | `{ "argv": ["uname","-a"], "cwd"?: "...", "timeoutMs"?: N }` → `{ "jobId": "..." }` |
| GET | `/v1/jobs/{id}` | → `{ "status": "queued\|running\|succeeded\|failed", "exitCode"?: N }` |
| GET | `/v1/jobs/{id}/logs` | → `{ "stdout": "...", "stderr": "..." }` |

Auth: optional Bearer from profile `credentialRef`. Profile `host` may be a full
URL (`https://worker:8443`) or host+port (default port 8443 / https).

`DefaultAgenticSshExecutor.exec` routes `ConnectionProfileType.HTTPS_RUNTIME`
through this client (health → start → poll → logs).

## Termux RUN_COMMAND

`TermuxRunCommandHelper`:
1. Prefers ProcessBuilder when Termux/system `sh` is executable from the app UID
2. Else dispatches `com.termux.RUN_COMMAND` → `com.termux.app.RunCommandService`
   (requires **Allow external apps**; stdout not captured in-APK — platform limit)
3. Used by planner tool `local_shell`

## Background scheduler

`AgenticScheduler` / `AgenticTickWorker` — WorkManager unique periodic work
`agentic-runtime-tick` (15 min), battery-not-low, skips on thermal MODERATE+ /
power-save. Registered from `MeowmbaApp` / `MainActivity.recover` / flag toggle.
Force-stop suspends WorkManager until next app open (Android hard limit).

## Model-parsed tool calls

`AgenticToolCallParser` + `HttpOpenAiCompatibleModelAdapter` surface OpenAI
`tool_calls` / compact JSON into `ProposedToolCall`. Registry: `ssh_exec`,
`local_shell`, `https_runtime`, `noop`.

## Git / vault-lite memory

`FileBackedAgenticMemoryIndex` under `files/agentic-memory/` (jsonl + JSON
export/import). Full git inside APK is intentionally out of scope.

## Bridge ↔ SshHostRegistry

`AgenticSshBridge.importLegacyHost` / `exportToLegacyRegistry` —
`AgenticRuntime.importLegacySshHost` / `exportProfileToLegacyRegistry`.

