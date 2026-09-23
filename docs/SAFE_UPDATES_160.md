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
