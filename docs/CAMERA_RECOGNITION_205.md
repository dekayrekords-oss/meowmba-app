# Build 205 camera recognition

Build 205 / 2.0 fixes camera recognition needed for the commercial's genuine app footage. It is an in-place capture-emulator preview; no physical phone installation or Play submission is claimed.

## Reproduction and change

Build 204's unconfigured OmniRoute selection could return no vision provider. Selecting On-device reached ML Kit but the capture emulator lacked the separately downloaded Play Services image-label/OCR modules. Both real requests failed.

Build 205 bundles `com.google.mlkit:image-labeling:17.0.9` and `com.google.mlkit:text-recognition:16.0.1`. OmniRoute falls back to the existing on-device vision specialist when no LAN/cloud vision provider is configured. Explicit cloud selections still require their configured provider. Camera permissions, capture resource gates, and QR intent handling are unchanged.

Official dependency guidance: [image labeling](https://developers.google.com/ml-kit/vision/image-labeling/android), [text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).

## Validation

- Nine actual ViewModel camera policy/routing cases, twelve direct-answer routing cases, and six screen-help cases pass: 27 total, no failures/errors. Lint passes with zero errors/fatal, 234 warnings and 12 hints.
- Full Meta SDK debug APK signed with the original preview certificate. All 161 protected build-204 runtime entries match byte for byte. All 90 packaged native libraries pass ELF 16 KB checks; APK ZIP 16 KB alignment passes.
- Preview-only reuse of unchanged native bridges is permitted by the build authority. An initial package check caught three omitted CMake bridges; they were restored from the hash-verified build-204 APK, rebuilt, and the complete preservation check passed. A Play release must compile pinned native sources.
- `adb install -r` upgraded only `emulator-5556` from 204 to 205. First-install time remains October 4 10:08:39. Existing conversation/settings remained visible. Native app launch and foreground activity were inspected; PID 16963 survived both captures, with no crash-buffer entries after the update.
- Native UI `look` requests at 12:03:29 and 12:05:34 captured JPEGs from the emulated back camera. Replies were respectively “I see pattern, toy, screenshot.” and “I see pattern, screenshot, toy.” The second recording contains the real captured-photo thumbnail; its result is retained in the actual native chat. This proves bundled recognition on this capture emulator, not physical-camera quality, OCR accuracy, or configured cloud vision.

## Commercial state

Original takes 12/13 and result continuation 14 are preserved with provenance outside the repository. Their compatibility derivatives omit auxiliary Android metadata tracks and preserve native app content. The camera chapter is being assembled; the complete commercial remains unfinished. No QR scanning belongs in this chapter.

Seven other individual scenes currently total 160.301 seconds. Studio creation, a successfully generated/saved/equipped purple dragon, ring/context scenes, the TV Orb scene, full master assembly and final audio checks remain. Generated robot movement is continuously labeled as a future concept with physical movement unavailable.

The immutable capture preview APK SHA-256 is `341dfdbd268dba63bed4f1308a04f3918c908de5b8c49c113a24e789e2704b0e`. Local receipts in `work/production205` record the exact source patch, tests/lint, signing/native checks and capture logs; artifacts and user runtime files are not committed.
