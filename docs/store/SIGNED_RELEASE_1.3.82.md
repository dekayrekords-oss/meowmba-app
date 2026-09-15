# Signed release 1.3.82 (129)

Built on Meow in `/home/prog/meowmba-release129-20260914` from the production sources committed as `f0c6e86`, incorporating preview128 fixes. No production upload has occurred.

- Package: `com.meow.meowmba`; versionCode 129; versionName 1.3.82.
- Bundle: `/Users/prog/Meowmba-Releases/release129-20260914/meowmba-1.3.82-code129-release.aab`.
- Size: 331,982,834 bytes.
- SHA-256: `34036a545fc19d05669a61263a3e8246cb7ed9c0cbe752267aafc9077e77ccd9` (Mac and server match).
- Upload certificate SHA-256: `21:03:E4:B1:4A:CF:74:A4:62:21:8B:25:F2:A0:44:19:85:6C:5C:26:48:D1:3D:B1:4F:6F:F8:C4:FA:24:7F:C0`, matching the existing Play upload key.

`bundleRelease` succeeded with the existing external signing/Meta SDK configuration and pinned Needle/Stable Diffusion sources. No debug-native bypass was used. Vital lint passed. Bundletool 1.18.3 validation succeeded. Jarsigner reports `jar verified`; the existing self-signed certificate/chain and timestamp warnings remain, so this is not warning-free verification.

All packaged native libraries pass the 16 KB ELF alignment check; required voice/image JNI libraries and image exports are present. All 61 assets match the installed phone122 baseline. The debug package preserves all 74 native/model entries. Release additionally contains the required Meta wearable SDK.

Play destination remains the existing closed-testing Alpha track. Upload is held pending the owner's requested realistic agent scenario; see `RELEASE_1.3.82_ACCEPTANCE.md`. A valid signed artifact is not proof of store acceptance, review submission or tester availability.
