# Play release 191 preparation — 30 September 2026

Candidate: **191 / 1.5.25**, based on installed daily190 (`608d15ac9afafa1ab9af01762b670ae027dce7f0`), branch `release/play-191-20260930`.

**Production is not public and nothing is in human review.** Google blocked the attempted submission of reviewed beta152 because it requests broad photo/video permissions. The saved production draft, 172 countries, description and Data safety changes remain pending. Do not use Proceed anyway.

## Changes

- Play removes READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO and READ_MEDIA_VISUAL_USER_SELECTED. Private debug/daily media inventory remains available.
- Photos/videos use Android's picker; other files use the document picker. Only selected copies enter the library; originals stay unchanged.
- Play media-cleanup requests open Files with accurate guidance. Local-library cleanup, connected-folder browsing and app organization remain.
- The bundle checker rejects media permissions reintroduced by a dependency.
- Optional Flow location access has prominent disclosure, a decline choice and a separate visit to Android settings for background access. Returning refreshes actual access.
- The embedded manual explains pickers, Play limits, optional location and cloud/SDK data handling.
- Claude's190 workbench, voice, Computer and memory work is retained. No model download, physical phone installation or data clearing was performed. Laya remains on the existing rules fallback.

## Validation

- Full debug regression: 516 suites, **3,204 cases, 0 failures/errors, 9 skipped** (four public-only cases and five existing skips).
- Actual release flags: **4 tests passed**, covering Android12/API31 and Android15/API35, including previously granted broad media access.
- Native Android15/API35 emulator: actual minified Play APK started, optional first-run setup completed, and beta152 updated in place to191. Real Room10-to15 migration preserved a synthetic preference, local file, SQL fixture and a real memory row. Old image/video permissions granted to QA152 were revoked in191. No Accessibility automation service is in Play.
- Native public UI: Menu → Files → Android photo picker imported the chosen synthetic photo; the new copy matched byte for byte and the original was unchanged. The test used a disposable emulator with no accounts, personal history or real keys.
- Memory correction/save and explicit pin confirmation: **1 Android device test passed** on debug. The earlier Robolectric native-graphics test stalled on the edit dialog; it was moved to this real dialog/IME path, keeping the same assertions.
- Release lint: **0 errors**, 211 warnings and 12 hints. Pinned native compilation and package verification passed, including required JNI exports and16KB ELF alignment.
- Eight bundled voice model assets and the existing sherpa/ONNX voice libraries match190's source. No downloaded model assets are committed.

AndroidX's runner cannot start against the minified release because shared Kotlin/AndroidX facade classes are removed by R8. The SDK-only harness in `scripts/play-device-qa/` tests the actual optimized APK without changing production keep rules. AndroidX UI testing remains debug; testing libraries are not shipped in Play. Initial failed harness attempts remain in the external validation logs.

## Candidate artifact

- `app/build/outputs/bundle/release/app-release.aab` — **UNSIGNED; never upload this file before existing-key signing**.
- Bytes:305046953.
- SHA256:`2830712ab75dcebf2cbc7319bcf771ce5628170b205e969c2bf9e6bc3fae286c`.
- Packaged compressed base is about233MB. Android's current AAB FAQ documents a500MB base-module compressed limit: https://developer.android.com/guide/app-bundle/faq?hl=en . Play's final per-device download size has not been verified because the candidate has not been uploaded.
- QA copies signed with the debug key are emulator-only and must never reach Play or a user's phone.

## Remaining publication steps

1. Reconnect SSH host `meow`. Its existing upload signing configuration is `/home/claude/meowmba-app/keystore.properties`; the Mac has no matching upload key and SSH timed out. Keep its secrets on the server. Use the existing signing workflow and verify the matching upload certificate and packaged artifact.
2. Replace the production152 draft with signed191 and replace affected active test bundles too. Google requires broad photo/video permissions removed from every affected active track.
3. Complete declarations that the uploaded bundle actually requests, including background location and its real demonstration. Disclosure/privacy changes are done; the declaration/video are not submitted. See `PLAY_RELEASE_191_LOCATION_REVIEW.md`.
4. Confirm the accurate191 listing, release notes, 172 countries and pending23-type Data safety answers. Submit and inspect automated checks.
5. Confirm Google review approval and public availability separately before announcing an unrestricted public release.

The published privacy policy includes optional background location and SDK/provider data handling: https://dekayrekords-oss.github.io/Meowmba/privacy.html (public static HTML commit `29780be6dbff430abe9b444dbac458dbc6f12f84`). No Android source was added to the public website repository.

External receipts: Os workspace `work/production-release-20260930/`. Credentials, QA APKs, personal data, downloaded models and local build artifacts must stay out of the source repository. Private GitHub synchronization and Meow server synchronization must be reported separately.
