# Safe backups and updates — 1.4.4 / 160

## User workflow

Open **Settings → Backup & updates**. Choose a password of at least 12 characters,
repeat it, then save an encrypted `.meowbackup` file in Documents or your own cloud
storage. Keep the password separately: Meow cannot recover it. The saved document
is reopened, decrypted, and checked before it is accepted as a backup.

For a preview APK update, choose the APK, save a fresh backup, allow updates in
Android settings if needed, then choose **Verify backup & install update**.
Meow checks package, signing certificate, newer version, unchanged APK and backup,
and a backup age of at most 15 minutes. Android asks for installation approval.
A normal same-package update already replaces the old APK and retains app data.
Never uninstall first or bypass a signature mismatch by clearing data.

For the Play edition, **Back up & open Play Store** verifies the backup before
opening the listing. Meow cannot intercept updates initiated outside this page,
including Play auto-updates, Files, ADB, or another installer.

When moving between preview and Play editions, back up from the old edition and
restore in the new one. A legacy edition without this feature must remain installed
until it receives backup support. No cross-app sandbox access is attempted.
Restore adds missing items and retains identical ones. Conflicting records,
settings, or files block the restore before additions. Corrupt, incompatible, or
wrong-password archives are rejected. An I/O failure can leave some additions;
retry is safe and old-edition removal remains blocked.

After a complete restore, reload Meow and check keys, content and features. Confirm
that excluded data is saved separately and no newer changes were made in the old
edition. Only then can Meow open Android's removal confirmation for the other
known Meow package. Source version must match that installed edition, this edition
must be at least as new, and the original encrypted backup must still be accessible
and unchanged. There is no silent uninstall; no backups are automatically deleted.
DecisionLab and other apps are never cleanup targets.

## Coverage and safeguards

Includes logical Room data (chats, memories, tasks, projects, routines, connection
metadata and Flow records), portable API keys and encrypted settings, ordinary
app settings, supported user-created workspace/media/vault content, generated
props, saved avatar forms, custom animations, recipes, business-agent state,
neural custom state, and fact history. Native Android Keystore secrets and raw
encrypted preference XML are never exported; logical secrets are re-encrypted
inside the password-protected archive and re-encrypted again on import.

Excludes downloaded models, cache/screenshot/trace artifacts, Android permissions,
and data owned by other apps such as Termux. External files/references may require
access to be granted again. Same-schema additive restore is supported; newer or
incompatible database schemas require a compatible Meow version first. Limits:
450 MiB of user files, 32 MiB of logical metadata, 100,000 database rows and 19,000
user files. Exceeding limits fails closed; it never authorizes removal.

The v2 archive format permanently defines PBKDF2-HMAC-SHA256 (600,000 iterations),
AES-256-GCM, a random salt and nonce prefix, authenticated 64 KiB indexed frames,
and an authenticated empty final frame followed by EOF. Header/index/length are
AAD. Any future KDF change must use a new format version and retain the v2 reader.
An independently generated v2 fixture locks compatibility. ZIP paths, entry counts,
expanded sizes, manifest membership/uniqueness and SHA-256 hashes are checked.
Archive plaintext stages stay in private `noBackupFilesDir` and are removed after
normal completion/failure. Passwords are not persisted or logged.

Restore prevalidates schema, paths and typed rows, compares existing primary keys,
uses a database transaction and verifies foreign keys. Preference writers share a
lock; restore rechecks its baseline before committing so a concurrent key/settings
save is not overwritten. Backup authorization state is never imported.

## Release validation

Source and final verification receipts are recorded alongside the immutable APK.
Full regression tests, lint, APK signature/alignment, runtime asset parity and
connected-device smoke results are appended after completion. Laya remains deferred;
Flow continues using the existing local rules. Flow and Desk UI structures are unchanged.

### Completed September 23, 2026

- Application source: `813bcc5f20975938207b3b626b762fc933a2648a`.
- Full regression run: 438 suites, 2,887 tests, zero failures/errors, five expected
  SDK-absent tests skipped because this build includes the real glasses SDK.
- Daily assembly and lint pass. Lint: zero errors, 185 warnings, 12 hints. New
  warnings are style suggestions and intentional synchronous persistence on IO
  threads before authorizing installation/removal.
- APK signature matches the original preview certificate; 16 KiB ZIP alignment
  passes. All 152 fixed runtime/model entries match159; the two generated ART
  profiles are present and changed as expected (154 total checked entries).
- SHA-256: `eca5b2ff3c2d0696c5ac182bb322624a8ebc1b6ddb726ef3d45f83300ec42271`.
- Installed with `adb install -r` on Fold8 / SM-F971U / R5GL75XFCXD at 12:39:10
  America/Phoenix. Original firstInstallTime remains September23 09:05:20;
  appId10496 retained, no uninstall or data clear. The Play edition155 remains.
- Process1132 survived repeated checks; foreground app and the new backup warning
  were verified. No new preview crash entries after installation. Android reported
  launch delivery to an existing top instance, so no cold-start timing is claimed.
- No real API key was inspected/printed and no user's backup password was chosen.
  Real-user export awaits their private password/location choice in the app. Tests
  use synthetic keys/data; no physical cross-edition removal was exercised.
- Immutable APK and validation/device receipts:
  `/Users/prog/Meowmba-Releases/backup160-20260923/`.
- Meow SSH timed out; source is committed locally, not pushed to the server. No
  Play upload or website update was performed as part of this backup change.

### Existing issue discovered in historical device logs

Build159 crashed at12:08:07, before this update, when music awareness called
`playAnimationNamed` during ViewModel construction before the `avatarAnimationStore`
lazy delegate had initialized. Inspection confirms the declaration-order issue.
It remains in160; the backup change does not alter Claude's animation/music behavior.
Smallest follow-up: move that existing property before the init blocks and add a
regression covering construction with music already playing and immediate dispatch.
Keep this separate from backup validation; the absence of a new crash during smoke
checks does not establish that the historical trigger has been repaired.
