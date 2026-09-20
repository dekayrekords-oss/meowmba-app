# Release fixes — candidate151 (1.3.95)

Continuation of candidate150. Local preparation and authorized device testing only;
no Play upload, publication, public source push, data reset or robot actuation.

## Changes

- Replaced Arizona road-feed InputStream.readNBytes (API33) with bounded reads that
  work on supported Android12. Tests cover exact limits, short reads, oversize input,
  empty input and I/O failure. Size limits remain enforced.
- Made Bluetooth permission-revocation recovery explicit, including scan callbacks
  and stopScan; close RFCOMM sockets when connect fails. Robot control remains
  quarantined; this is not physical robot acceptance.
- Removed restricted CameraX surface-provider getter usage by retaining only previews
  with an assigned provider. Declared the existing experimental camera API usage.
- Replaced the Activity cast with LocalActivity, corrected speech-block indentation,
  and moved SettingsCategory to its own file so Kotlin UAST lint can inspect the screen.
- Rebuilt app JNI and Stable Diffusion/GGML for ARMv8-A with host-native selection off.
  A separate baseline JNI probe checks dot-product support before loading the pinned
  Needle3 runtime; other assistants/basic commands remain usable when unsupported.
- Motion sensor state is sampled without subscribing the Canvas to every sensor tick.
  The existing avatar clock still controls cadence; the frame target, visual detail,
  sensor/camera behavior and thermal thresholds are unchanged.

## Regression evidence

The first JVM rendering probe could not initialize AndroidKeyStore and is not counted
as a product failure. A first device probe counted an outer cached layer and incorrectly
passed; it was corrected to count actual Lumen drawing. On installed150, 20 synthetic
IMU updates then caused 20 draws while the avatar clock was paused. On151, that same
regression passes (at most two settling draws). These intermediate records are retained.

Both the physical Fold and a read-only emulator session passed four checks: paused-sensor
redraw prevention, native probe/image-library entry points, cloud-speech cancellation,
and diagnostic report/FileProvider readback. The emulator reports PAGE_SIZE=16384.
Both also passed real Needle3 inference proposing a registered memory tool; no tool was
executed. Model weights were copied into the temporary emulator session for that test.

No claim is made that every third-party native SDK path has been exercised or that the
emulator is an older ARM CPU. Unsupported-CPU unit tests verify the vendor loader is
never called without dot-product support. The exact pinned archive was disassembled;
its optional SDOT requirement is documented in NATIVE_BUILD.md.

## Performance observations

Read-only 90-second / 30-sample UID measurements on the same phone, same preview setup,
USB-connected, with Android thermal status MODERATE(2) at both ends:

| Build | Mean CPU, one core | Battery temperature |
| --- | ---: | --- |
| 150 | 87.62% | 36.3 → 37.2°C |
| 151 | 36.40% | 37.3 → 37.1°C |

This is an observed approximately 58% CPU reduction, not a randomized benchmark or
unplugged battery-life measurement. The initial 30-second foreground simpleperf profile
attributed most CPU to UI/rendering threads. A background sample had zero events and
was not treated as useful foreground evidence. Residual CPU/performance work remains.

## Packaging and privacy

All 148 protected runtime assets from150 are present. Only the two intentionally rebuilt
Needle/Stable-Diffusion JNI libraries changed; the CPU-probe library is newly added.
The debug signing certificate is preserved. Installed151 in place on the physical phone.

The app/SDK data-flow inventory is in DATA_SAFETY_151_INVENTORY.md. In particular,
on-device ML inference is not proof that SDK diagnostics are absent. The live policy,
final Data safety answers and exact-version third-party disclosures still need review.

## Outstanding acceptance and external gates

- Complete unplugged and longer-duration battery/thermal testing and actual older-arm64
  testing. Continue optimizing remaining foreground CPU without reducing safety gates.
- Complete full signed-release onboarding, real providers/noisy speech and any advertised
  wearable/hardware workflows. Initial core release copy must omit unverified features.
- Large Computer exports and unified projects remain separate roadmap work.
- Play production access was not granted at the last inspection: 12 opted-in testers
  had9 continuous qualifying days. Finish the required period and use actual tester
  feedback in the application. No engagement/feedback was invented.

## Final receipts

Full Gradle build passed in 12m 54s: debug APK, instrumentation APK, signed-ready
release APK/AAB, full debug lint, full release lint and the full unit suite. 399 suites,
2,703 tests, zero failures/errors, five skipped. Debug lint: zero errors, 162 warnings,
12 hints. Release lint: zero errors, 173 warnings,12 hints. Warnings are not hidden with
a new baseline or blanket suppression.

The signed APK/AAB were verified with the existing upload certificate. Bundletool
validation, 86 native-library alignment checks and CPU/image JNI export checks pass.
Release manifest excludes Accessibility; JNI class names survive release shrinking.

Signed APK SHA-256: `5316c2ba605c85cb1c3c037741324245055deff41dc8e04e805abd47680c657e`.
Signed AAB SHA-256: `c4d165871ff6e05e2d472e042c125880c3e6f85ffc751ed24385de9b4978a902`.
Preview APK SHA-256: `0a0760a746718cb7179c1e8864b45638ebda1b617b63a7265d451402fd00a7f3`.
Upload certificate SHA-256: `2103e4b14acf74a462218b25f2a04419856c5c2648d13db14f6ff8c4fa247fc0`.

The 600.013-second physical-phone observation passed: 61/61 foreground samples, no
cancellation or severe thermal sample, and 32.37% average CPU including instrumentation.
Battery temperature 37.1→36.5°C; thermal MODERATE(2)→LIGHT(1). USB stayed connected,
so battery-drain measurement is invalid. Lumen does not emit fly telemetry; its separate
motion gate is unavailable, not passed. No animation-quality/FPS claim is inferred.

The existing separate signed-release package on the phone was updated in place to151;
preview151 was restored to the foreground; UI semantics confirm camera and microphone off.
The phone crash buffer contains only the two historical147 crashes, no new app crash.

Fresh signed-release setup on the 16 KB emulator reached the avatar home without a
crash, but FAILED the permission-choice acceptance check: choosing “Not now” still
showed MIC ON. Android microphone access was not granted. Candidate152 will fix
and retest this defect before fresh-install acceptance can pass. The read-only
emulator remains open for this follow-up; its original disk is not overwritten.
Archive:
`/Users/prog/Meowmba-Releases/release-fixes151-20260919/`.

## Draft151 release notes

- Fix Android12 compatibility for the road-work feed and improve Bluetooth permission handling.
- Prevent motion sensors from forcing redundant avatar redraws, including while animation is paused.
- Improve native compatibility and check local action-model CPU support before loading it.
- Retain the prior speech-crash fixes and optional diagnostic sharing.
