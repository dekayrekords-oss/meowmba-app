# Signed release 1.3.82 (130)

Built on Meow in `/home/prog/meowmba-release129-20260914` from branch `codex/release129-computer-acceptance`, incorporating preview128 fixes and the business planner correction discovered during realistic testing. The earlier code129 artifact is superseded and was never uploaded.

- Package: `com.meow.meowmba`; versionCode 130; versionName 1.3.82.
- Bundle: `/Users/prog/Meowmba-Releases/release129-20260914/meowmba-1.3.82-code130-release.aab`.
- SHA-256: `0f5ddb7eb221519c4452f42b1d407085db42afd639cf418e222cc329e8f30ace` (Mac and server match).
- Upload certificate SHA-256: `21:03:E4:B1:4A:CF:74:A4:62:21:8B:25:F2:A0:44:19:85:6C:5C:26:48:D1:3D:B1:4F:6F:F8:C4:FA:24:7F:C0`, matching the existing Play upload key.

`bundleRelease` succeeded with the existing external signing/Meta SDK configuration and pinned Needle/Stable Diffusion sources. No debug-native bypass was used. Vital lint passed. Bundletool 1.18.3 validation succeeded. Jarsigner reports `jar verified`; the existing self-signed certificate/chain and timestamp warnings remain, so this is not warning-free verification.

All packaged native libraries pass the 16 KB ELF alignment check; required voice/image JNI libraries and image exports are present. All 61 assets match the installed phone122 baseline. The debug package preserves all 74 native/model entries. Release additionally contains the required Meta wearable SDK.

Play destination remains the existing closed-testing Alpha track. The owner's requested realistic agent scenario passed on preview130; see `RELEASE_1.3.82_ACCEPTANCE.md`.

## Confirmed Play submission, 2026-09-14

Uploaded and processed version130 (1.3.82). Play showed zero release errors and zero lost supported devices. One non-blocking warning reports a significant increase in download size. Retained the required runtimes; size reduction remains follow-up work.

Saved the existing Alpha track's full rollout (100%), then clicked **Submit 1 change for review** and confirmed **Send changes for review**. The console now shows **Changes in review**, with exactly one change: **1.3.82 – Computer and business-agent reliability**, **Closed testing - Alpha**, **Start full rollout**. Managed publishing remains off. Quick checks were still running (up to 14 minutes shown) when submission was confirmed. Google review/approval and tester availability are pending; do not describe this as already live or as a production rollout.

Console: https://play.google.com/console/u/0/developers/5214154317103934390/app/4972949930318434862/publishing
