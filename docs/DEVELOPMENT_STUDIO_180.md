# Development Studio — preview 180 / 1.5.14

Owner request: bring the useful Mobile-Harness development features into Meowmba,
including Claude Code and website/app building, without replacing the existing
Ubuntu/OpenCode setup. Original implementation, not copied Mobile-Harness code.

## Implemented

- Computer & task tools → Development Studio. Manual, on-demand PTY via the existing
  authenticated loopback OpenCode server. No new background daemon, rootfs, model
  service, permission-bypass hook or companion shell tool.
- App-bundled Python menu in Ubuntu. Project folders under `~/MeowProjects`, strict
  project names, one menu per project via advisory file lock. Requires Python 3.
- Claude Code: explicit official stable installer, then interactive account/trust/
  permission flow with default permissions and screen-reader rendering. No paid
  fallback, API key copying or account login performed by Meow. CLI owns its auth.
- OpenCode: separate interactive CLI, ask-all permission config, native continuous
  search index disabled. Existing Computer tasks/credentials stay unchanged.
- Source checkpoint, bounded per-file diff, Keep and Undo. Baseline persists outside
  the project. Rejects stale reviewed hashes and symlinks; retains undone contents as
  SHA-256 objects. Does not overwrite an unreviewed baseline with a new checkpoint.
- Starter website, approved npm install/build, and loopback-only static `site`/`dist`
  preview. WebView denies external origins/file/content access; server denies dotfiles,
  symlinks/traversal and foreign Host headers, includes restrictive CSP. No JS bridge
  or Computer credentials. WebView pauses on background and is destroyed when hidden.
- Approved `:app:assembleDebug`, no daemon, max two workers, exit-code check, exact
  debug output-metadata path, one universal APK only, SHA-256 receipt. Optional
  `MEOW_AAPT2` override. Never chooses the newest file by mtime.
- Temporary APK export on loopback port8766 with a random192-bit capability, fixed
  file and bounded transfer. Explicit fetch verifies size/hash, parses signed APK
  metadata, displays package/version/hash, rejects Meow self-replacement, and requires
  a separate Android installation confirmation. No installation-success claim.
  Uses existing preview-only install permission; does not add it to Play release.
- Optional Ubuntu package prerequisites after explicit agreement. No package installs
  just by entering the screen. Cleanup only removes generated temporary APK downloads
  after confirmation, leaving project/build outputs and installed apps untouched.

## Resource limits / optimization design

No idle polling in this new panel. Bounded64-frame channel, burst-batched updates,
48,000-character display. Checkpoints scan on demand only: 2MiB/file, 32MiB total,
2,000 files,128MiB recovery object store. Diff text64KB/file and300 displayed lines.
Preview files2MiB. APK512MiB, downloaded cache1GiB. These are deterministic bounds,
not measured battery/thermal improvement claims.

## How to use

1. Finish existing Computer setup. Ubuntu must contain `python3`; if missing, use
   the existing manual Ubuntu terminal to install it (`apt-get install python3`).
2. Open Development Studio, connect, enter a project name, send a numbered menu action.
3. Check tools (1). Install optional build prerequisites (12) or Claude (10) only if
   wanted. Native Claude under Android PRoot needs physical-device verification.
4. Create a starter website (6), checkpoint (2), then Claude (4) or OpenCode (5).
   Describe the website/app you want in that CLI. Model/provider charges may apply.
5. Exit the agent normally, then review (3); choose a file, read the diff, type keep
   or undo. **Stop other editors/agents first**: hashes catch stale reviews, but this
   is not an atomic filesystem transaction against arbitrary concurrent writers.
6. Build npm (7) and preview (8); open the preview in Meow and reload after edits.
7. For Android, supply a trusted Gradle project with app module and configure JDK,
   SDK and ARM64-compatible aapt2. Build (9), Fetch built APK, review its identity,
   enable preview-app install permission if needed, then open Android’s installer.
8. Stop preview/export (11) or exit (0). Leaving the screen closes this owned PTY;
   that can interrupt its agent/build. Other Termux sessions and OpenCode tasks remain.

## Explicit limitations

- Not full Mobile-Harness parity. No embedded PRoot replacement, automatic Android
  SDK/license provisioning, full terminal emulator, visual source editor, autonomous
  website publishing, split APK installation or Play signing/publishing.
- The preview blocks external CDNs/APIs/fonts; it is a local inspection view, not a
  full browser security sandbox. Other phone apps can read public preview assets.
- Dependencies/generated files, `.env*` and common private-key extensions are excluded
  from checkpoints. This is source review/recovery, not a complete project backup.
- No guarantee against hostile same-UID processes or repository build scripts.
  Ubuntu/PRoot and its CLI permission prompts are not a hardened filesystem sandbox.
- Recovery objects are content-addressed outside the project. Export/archive them
  manually if128MiB is reached; no silent deletion of recovery data.
- CLI runs/builds/authentication on the owner’s Ubuntu, real Android toolchain builds,
  APK installation and battery/thermal gains need owner-device acceptance.
- Cozmo/LOOI controls, phone companion behavior and existing Computer setup untouched.

## Validation

Prepared APK; subsequently phone-installed as recorded below. App source `9d755427dd134c0d1d774d27286532c7b6cf3091`.
Final build succeeded in10m59s. Full3,054 JVM tests: zero failures/errors, five existing
skips;18 backend tests pass. One emulator UI acceptance test passes (2.257s); corrected
Compose capture visually inspected. Initial UIAutomation capture was blank despite
passing assertions; retained as diagnostic evidence, not a visual pass.
Lint: zero errors,202warnings,12hints. Two added warnings: intentional preview JavaScript
and a Uri.parse/UseKtx suggestion. No permission bridge/credentials exposed to WebView.
APK certificate matches preview;16KB alignment passes; all161 protected baseline179
runtime entries retained (only generated ART profiles changed). Bundled Python program
matches source SHA256 `62216e131dd977a33e360a53cfa42a01c7e72d378238cf961828ca0cec95c836`.
Optimized emulator cold start849ms, PID5955 alive86seconds; foreground verified and
crash buffer unchanged. These are smoke results, not comparative performance claims.
APK368,582,815bytes; SHA256
`11f16a3ccb4c64ee38e2a927ec20cda66b4829c169c58988ca33c11d7e95aadd`.
Immutable APK/evidence in Os `work/development-180/`. Fold R5GL75XFCXD connected but
still177/1.5.11; Termux package absent and both Computer ports refused connections.
No account login, actual Claude/model call, phone install, SDK download/license agreement,
generated APK installation or physical robot command performed during development.

Owner subsequently explicitly authorized the Meow update. Fold R5GL75XFCXD upgraded
177→180 in place at2026-09-25 19:51:44 MST. First-install time/appId retained; Play155
unchanged. Exact installed baseline signature and all161 protected runtime entries
verified. Fresh PID26400 survived65seconds with no new crash entries; foreground and
saved-content Home visually verified. Android's Home reuse produced UNKNOWN launch
state, not a cold-start measurement. App left open; no coding-runtime installation,
permission grant or robot motion. Phone receipt: Os `work/development-180/validation/phone-install/`.

## References used

- https://github.com/techjarves/Mobile-Harness (comparison only; no third-party code copied)
- https://code.claude.com/docs/en/cli-reference
- https://code.claude.com/docs/en/setup
- TypeSafe workflow guidance informed the boundary: deterministic code owns paths,
  receipts, approvals and execution. No new TypeSafe API integration/credentials.
