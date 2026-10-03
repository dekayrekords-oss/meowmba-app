# Play 200 and phone test preparation — October 3, 2026

Candidate: 200 / 1.5.34, local branch `release/play-200-20261003`, based on build199 / `ab84e38`. Preserve newer ring, Desk, memory, voice and Computer work. User requested fixes and a phone test update, with all remote pushes and Play uploads/submission/publication held until their go-ahead.

## Public distribution

The existing scoped Android photo/video/document import fix is retained. Release strips all four broad media permissions, background-location permission and both Accessibility services. Flow location setup asks only for while-using access. Foreground lifecycle unregisters optional Flow location, and late batches/old grants cannot re-enable its background journal sampling. Public ring setup shows Bluetooth connection and local on-demand sensor tests, without unavailable gesture or cross-app controls. The private phone variant retains background location, broad local media inventory and ring controls. No Room schema or destructive migration changes.

## Live Console evidence

October3: publishing overview has four changes not submitted, production draft152 and one broad-photo/video policy blocker. App content Need attention is empty; 13 declarations are actioned. Data safety is ready to send. Existing Health apps declaration selects Activity and fitness. Production badge is Active with 5 devices and172 countries; this badge does not establish publication of the pending draft. Upload certificate SHA256 is `01:B0:88:88:54:AA:D8:9E:C5:35:43:08:30:28:C5:FB:22:3F:F6:40:C3:33:CE:0D:BA:60:DC:96:B8:BC:FE:AE`, matching the replacement certificate recorded for192.

## Validation

In progress. Initial debug build stopped because macOS rejected the cached downloaded SQLite JDBC verifier native library. A local verifier dependency was built from official xerial/sqlite-jdbc tag3.41.2.2, commit080c808eac7612f90852e09088f75806afafa15b, with SQLite3.41.2 source; this affects host compilation only, not Android payload. The locally compiled host verifier inherited Chrome quarantine metadata. That single file’s quarantine flag was removed, with its source revision, original attribute and SHA256 recorded externally; the downloaded original and global security settings were retained. A real SQLite3.41.2 create/insert/query smoke passed. Do not describe a candidate as validated until checks finish.

## Before any upload

1. Finish debug/actual-release tests, release lint, package/native alignment, signature and preserved-runtime validation.
2. Sign the Play AAB with the existing replacement upload key and verify the certificate above. The earlier task folder containing signing preparation is currently blocked by macOS file access; no new key/reset is authorized or necessary. The old Meow server is offline.
3. Give the signed private phone APK to the owner for acceptance; phone USB is disconnected. Preserve installation and settings during any authorized upgrade.
4. Wait for the owner’s go. Then replace draft152 and every affected active testing bundle with the compliant signed release; review listing, accurate Data safety/health/foreground-service/install-package/package-visibility declarations against the actual upload. Never select a private/debug/emulator QA APK for Play.
5. Inspect Google’s new checks and review result. Public availability requires its own verification.

No source push, upload, submission, Console setting change, physical phone installation, permission grant, key change or app data clearing has occurred in this preparation.
