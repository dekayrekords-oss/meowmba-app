# Build 120 — Computer integration and phone acceptance

Date: 2026-09-14. Owner requested double-check, finish and install. Android source stays private. Build 119's avatar line was merged with the independently discovered Computer P0 branch (Preview 96), preserving the newer avatar implementation rather than installing the older branch over it.

## Changes and why

- Computer chat uses the bounded goal loop. It distinguishes successful exit 0 from failure, missing completion, approval, timeout and cancellation. Random per-command frames prevent echoed or partial terminal output from appearing complete.
- Commands explicitly prefixed `Run on Computer:` bypass the model planner. Phone acceptance exposed the old stub/model response replacing the user's command with an SSH probe; that produced BLOCKED with no Computer output. Approvals still happen in the runtime/executor. Approved waiting goals can now proceed through the real planner.
- Stop Computer prevents future steps and suppresses stale chat results. A command already dispatched may finish; the interface says this explicitly. Goal commits preserve cancellation and completion, and per-run ownership prevents old tasks from updating newer UI.
- Worker port 18766, authenticated health/jobs, maximum two concurrent jobs, 64 retained jobs, 120-second command ceiling and 200 KB per output stream. Nonblocking pipe drains retain only bounded tails; no unbounded temporary output file. Installer validates readiness before creating its marker and returns failure if health fails.
- Installer generates Docker configuration but starts only its systemd service. Reinstallation restarts that specific service. It does not start Docker alongside systemd or create a symlink inside the same installation directory.
- SSH supports saved private key material as well as passwords. The actual handshake key is compared against the saved fingerprint before credentials are sent. A first connection can pin its key according to the profile setting; mismatched keys fail. Both installer and runtime use the same implementation. Output polling respects the command deadline.
- Moon garden has one expressive animated flower per planter instead of three, reducing native draw work and visual crowding. Original static/avatar backgrounds and prior artwork remain preserved.

## Meow troubleshooting instructions

1. Do not equate a quiet terminal, a dispatch acknowledgement, or `SshExecResult.Ok` alone with success. Require the completion frame and actual exit 0. Unknown exit is failure.
2. When Computer is BLOCKED, inspect that goal's events on the device. An absent `computer_exec` event indicates planning/policy/routing before the executor. The stub planner used to select a probe even for explicit commands. Test the real planner, not only injected planner fixtures.
3. Credentials remain in secure storage/on the authorized host. Do not print them. The worker's `/health` requires Bearer authentication; an unauthenticated 401 means the worker is reachable, not broken. Use SSH/private networking for the HTTP worker.
4. Host fingerprint verification must happen inside the actual SSH handshake. A separate `ssh-keyscan` probe followed by `StrictHostKeyChecking=no` is insufficient. Saved private keys must use `JSch.addIdentity`, not `setPassword`.
5. Respect terminal result ownership and cancellation after every suspension or blocking tool call. Cancellation stops subsequent steps; it cannot promise to undo or kill an already dispatched command.
6. Check both private Git repositories before merging. Preserve voice/native/model assets in the build worktree; these are not all tracked. One build owner only. Do not push a new commit directly onto the branch checked out by the server worktree's shared Git directory while building. Use a staging ref and fast-forward the worktree.
7. Run `scripts/verify_avatar_forms.sh` plus Computer suites. Verify actual selected class names/counts, APK signer, version and asset hashes with `docs/characters/verification/verify_build120.py`. Recheck the actual GPU on the phone; a JVM render test cannot validate device GLES behavior.
8. Keep port 8766 untouched: the existing TCP service there was identified as an APK HTTP server, not this Computer worker. Only the new `meow-computer.service` was deployed.

## Validation and deployment record

The pre-phone candidate passed 280 JVM tests and five Python worker integration tests. Real phone acceptance exposed the planner routing issue and the moon-garden draw regression above. The corrected production candidate `bb59324` passed all 281 selected JVM tests, zero failures/errors/skips. All five real GPU tests then passed. Device test revision `7b45c4f` reads uniquely marked persisted chat replies, because `speak()` intentionally replaces the visual caption with a short spoken summary.

The compiled app SSH adapter was exercised from the Mac against Meow using the existing key in place. It authenticated, verified the pinned ED25519 server key, returned exact command output, and rejected a deliberately wrong fingerprint. No key was copied to the phone.

Worker installation: `/home/prog/meow-computer`, `systemctl --user status meow-computer.service`, localhost TCP 18766. Private token stays in `.worker-token` mode 600. Authenticated readiness passed. Optional desktop packages/Docker were not installed or started.

Phone: Samsung SM-F966U1 / RFCY61NJEVM. Starting version 112. In-place upgrade only (`adb install -r`), no uninstall or data clearing. Initial Build 120 installed successfully; final post-fix installation evidence is recorded below once completed.

## Limits

No claim of a completed biological fly simulation, robot transfer, sustained battery/heat validation, or successful live model-generated avatar design. Full MaleCNS graph remains server-side. The newly integrated Computer P0 is a bounded execution/approval/connection feature, not proof of an unrestricted reliable multi-step autonomous planner.

Automatic approval review rejected transferring the existing key/token to phone storage and exporting the entire app database. Neither action executed. We used Mac-side compiled-adapter SSH checks, worker integration tests, and targeted device acceptance instead. The two credential-dependent phone tests were not run; a live authenticated phone-to-Meow profile still requires user-configured credentials/connection.

## Installed production candidate

Production source `bb59324`; test-only update `7b45c4f`. Final app SHA-256 `606ea8fdfffd2f12d39220467122b299266c905281ca9e4b9a33058f8de78bf3`. Final archive `/home/prog/research/meowmba-build120-7b45c4f`. Debug signer matches the established phone signer. All 53 protected runtime assets and 19 painted backgrounds retained byte-for-byte.

Corrected Build 120 installation returned Success. Five real-GPU tests passed (including portrait/landscape cartoon scenes and paused frames). The Computer command completed, but its first assertion incorrectly read the TTS caption instead of saved chat. Test-only correction compiled and was signature/hash verified. USB disconnected before its final installation/run, so the persisted-output/nonzero-exit/cancellation device run remains pending. No sustained heat/battery claim; pre-install battery was 97%, 29.8 C, charging.

To finish: reconnect RFCY61NJEVM, install the final tests120 APK (do not reinstall/clear the main app), run Computer120DeviceTest#computerChatMeasuresExitAndCancellation plus Fly3DGpuTest, verify package version/update time, launch the avatar, record results. The credential-dependent phone tests must not be run without explicitly authorized credentials; Mac-side actual SSH and worker tests are already green.
