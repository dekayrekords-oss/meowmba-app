# Terminal-clone agentic plan (condensed)

Companion to [`INTEGRATION_LUMEN_AGENTIC.md`](./INTEGRATION_LUMEN_AGENTIC.md).
This is the **computer / terminal-clone** half of agentic runtime v1 — how Meowmba
reaches a remote shell without shipping OpenSSH inside the APK and without FlyWire.

## Three setups

| Setup | Where the shell runs | How Meowmba talks to it | When to use |
|-------|----------------------|-------------------------|-------------|
| **A — Termux on-device** | Same phone (`ssh` / `sshpass` under Termux prefix) | `DefaultAgenticSshExecutor` → `ProcessBuilder` | Dev / Fold with Termux; no separate worker |
| **B — SSH to a host** | Laptop / NAS / CI box | `ConnectionProfile` (`SSH`) + `credentialRef` in SecureStorage | Day-to-day “drive my machine” |
| **C — HTTPS_RUNTIME worker** | Small agent on a trusted host | `ConnectionProfileType.HTTPS_RUNTIME` (scaffold; worker packaging later) | When the phone must not hold an SSH client |

Flags: everything stays behind `agentic_runtime_v1` (default **OFF**).

## `ssh_exec` loop

1. Operator saves a **connection profile** (Settings → Neural / Lumen → Agentic profiles): host, port, user, `credentialRef` name.
2. Password / key material is written only via SecureStorage (`agentic_cred_<ref>`) — never into Room, events, or prompts.
3. Planner (`AgenticRuntime.tick`) may propose `ssh_exec` with argv.
4. **Budgets** cap steps / tool calls / SSH execs (`AgenticBudgets`).
5. **Approval**: side-effecting argv (not in the read-only allowlist) → `GoalStatus.WAITING_APPROVAL` until Companion UI approve/reject.
6. **Host-key**: fingerprint on the profile is verified (mismatch → reject). First connect may **pin** when `pinHostKeyOnFirstConnect=true` (TOFU); document and keep operator-visible.
7. Executor runs argv; sanitized events only (`profile=… argv=… exit=…`).

Read-only probes (`uname`, `ls`, `df`, …) may skip approval; shell metacharacters always count as side-effecting.

## Termux notes

- Probe paths include `/data/data/com.termux/files/usr/bin/{ssh,sshpass,ssh-keyscan,ssh-keygen}`.
- Missing binaries → `SshExecResult.Unsupported` (goal → `BLOCKED`), not a crash.
- Prefer key agents later; password path uses `SSHPASS` env with `sshpass` (never on argv / logs).

## Journals / persistence

| Store | Backend | Survives process death |
|-------|---------|------------------------|
| Goals / events | Room `agentic_goals` / `agentic_events` (DB v10+) | Yes |
| Connection profiles | Room `agentic_connection_profiles` (legacy SecureStorage JSON migrated once) | Yes |
| Credentials | SecureStorage `agentic_cred_<ref>` | Yes (encrypted prefs) |
| Agentic memory notes | In-memory stub (`AgenticMemoryIndex`) | No (Git/vault later) |

Room migration is additive (`MIGRATION_9_10`). No `fallbackToDestructiveMigration`.

## Security reminders

- No secrets in `AgenticEvent.detail`, planner prompts, or UI logs.
- Host-key mismatch is hard-fail; clearing a fingerprint is an explicit Settings action after a reinstall.
- Do **not** drive SSH from the Lumen frame / avatar loop — presence hints only.
- No FlyWire / connectome imports on this path.

## Pointers

- Integration checklist & flag toggles: [`INTEGRATION_LUMEN_AGENTIC.md`](./INTEGRATION_LUMEN_AGENTIC.md)
- Code: `app/src/main/java/com/example/meowmba/core/agentic/`
- DB: `MeowmbaDatabase` + `AgenticGoalEntity` / `AgenticEventEntity` / `ConnectionProfileEntity`
