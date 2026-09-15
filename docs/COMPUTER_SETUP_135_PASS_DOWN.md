# Computer menu and guided setup — Build 135

## Delivered behavior

Computer is now the first main-menu tile, a direct Settings category, and a
visible button inside Neural / Lumen. The dedicated hub separates Setup,
Tasks & approvals, AI for tasks, Saved connections, and Power & sleep. Logs,
phone probe output and transport diagnostics have their own secondary page.
Neural settings retain their existing avatar controls without the stacked
Computer forms.

Setup starts with a phone/server choice. Phone setup guides Termux installation,
Linux preparation, and opening Terminal. Server setup guides address, sign-in,
and review/test. Advanced fields stay collapsed. The fixed header and actual
back history survive rotation; non-secret drafts survive saved-instance
restoration while passwords and private keys do not enter saved screen state.
Both appearances use the existing theme. Large-text controls remain reachable.

The saved-connection editor opens only after Add/Edit. Task status labels use
plain language, task creation and model configuration are separate pages, and
disabled task automation has an explicit enable action. Saving profiles or
task-model settings refreshes their displayed values immediately.

## Associated correctness fixes

- Saved, checking, connected and installed are distinct states. Termux
  availability and a saved server no longer imply a verified connection.
- Save/test/install share the exact persisted profile and a single active job.
  SSH host-key pinning is reflected in the checked profile. Changes to address,
  identity, authentication reference, transport, workspace or pin policy require
  a fresh test before installation. Installation remains an explicit button.
- Computer profile setup works independently of the task-automation flag.
  Task execution and its connection editor retain the existing enable control.
- Open Terminal dismisses Settings, selects the requested phone/server target
  and reveals the Terminal pane. It does not complete or reset onboarding.
- Phone probing refreshes when returning from Termux without a polling timer.
  Installed Termux alone is described as availability, not a verified guest.

## Verification and scope

Eight new checks cover menu and Neural entry points, separate pages, back
history, three-step phone/server setup, validation, explicit callbacks,
duplicate-operation disabling, exact connection verification, saved-instance
restoration, secret omission, landscape and 320dp layouts at 1.5x font scale.
Native screenshots cover the menu, hub, setup, verified server review and power
page. All 2,546 JVM tests pass across 365 suites with zero failures/errors/skips;
debug, Android-test and signed/minified release builds pass, including release
vital lint. The regular and preview packages are installed at code135 by USB
in-place upgrades. All75 debug and149 release protected entries are present
and unchanged; only generated release ART profiles differ. Signing matches134.

The preview135 phone walkthrough confirmed the first-row Computer tile, hub,
phone/server choice, and first phone step with installed-Termux detection. It
returned to the avatar using Back; the existing navigation overlay was left
alone. No task, remote connection, installation or settings change was triggered.
The minified regular package was installed but not foreground-tested. Final
suite, packaging, source hashes and phone receipts are recorded in
`docs/verification/computer135-20260915/summary.json`.

This task changes navigation and setup behavior. UI tests use synthetic server
callbacks; they do not claim a new live SSH installation or remote task run.
Existing transport, approvals, credentials storage, installer and runtime assets
remain in use. This is not new voice, thermal endurance or full Logic editing
acceptance. The existing Play code130 review is unchanged.

Artifacts are archived in
`/Users/prog/Meowmba-Releases/computer135-20260915/`.
