# Production preparation — candidate150 (1.3.94)

Prepared September19,2026. Not uploaded or published. This is a release candidate,
not a statement that the full52-item roadmap is finished.

## Live Play Console evidence (read-only)

- Package: com.meow.meowmba. Highest uploaded bundle observed:138 (1.3.82).
-138 is available to closed Alpha testers; no unpublished changes were present.
- Production access is NOT granted. The dashboard shows12 testers opted in for9
  continuous days. The14-day condition therefore needs at least5 more days with
  sufficient continuous participation, followed by the production-access application
  and Google's decision. Do not promise automatic approval on a particular date.
- Account notification requests Android developer verification registration by
  September30,2026. Registration completion was not established in this inspection.
- No track edits, upload, review submission, agreements or account changes performed.
- Sources: [release overview](https://play.google.com/console/u/0/developers/5214154317103934390/app/4972949930318434862/releases/overview),
  [production dashboard](https://play.google.com/console/u/0/developers/5214154317103934390/app/4972949930318434862/app-dashboard).

## Candidate scope

Keep the working chat/voice/avatar, explicit provider selection, local library,
verified small Computer results, existing approval gates and privacy controls.
Candidate150 adds explicit user-controlled support diagnostics and release packaging
boundaries. It includes149's speech crash repairs and148's prepared UI/creation work.

The Play release removes MeowAccessibilityService from its merged manifest, does not
register autonomous phone-control tools or start their loop, hides the unavailable
settings/recipes, and explains unsupported requests. Private debug previews retain
this existing capability. App-launch intents and manual drafting remain available.
This addresses a concrete [Play policy restriction](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en-GB),
not a claim that all app-policy questions are settled.

Support reports contain version/API, memory/thermal health when available, microphone/
camera/wake/background modes and at most5 recent process-exit metadata records.
No chat, preferences, recordings, exception descriptions, raw logcat, endpoints, account
identifiers or credentials are read. Report sharing requires the user's Settings action
and Android destination chooser; no automatic report upload was added.

## Measurement and testing boundaries

Initial149 read-only180s/60-sample baseline: Termux had no running UID processes.
Meow averaged29.11% of one core; battery36.1→35.6°C while USB charging; thermal2→1.
This cannot explain an inactive Termux server's earlier heat or establish unplugged drain.
The old endurance test mistakenly required fly-only telemetry for Lumen. That run was
stopped and preserved as a failure, not counted as acceptance. The corrected harness
retains the specialized fly-motion gate but marks it unavailable for other forms and
measures foreground/process stability separately. An empty ART trace is also discarded.

## Gates before a production request

1. Complete actual sustained thermal/battery, voice/noisy-room, restart and offline
   acceptance. Charging observations are not unplugged or all-day evidence.
2. Validate onboarding on a fresh release package and a spare/lower-memory phone.
3. Validate signed/minified APK and AAB, package/version/signature, protected assets,
  16KB alignment and manifest/capability boundaries. Keep the exact source snapshot.
4. Current native compile flags still request ARMv8.7-A. Older arm64 native-model
   compatibility is NOT established; resolve baseline/runtime dispatch or supported-device
   scope before broad production distribution. Do not infer CPU support from minSdk31.
5. Finish large transfers and unified project results, or clearly omit those promises
   from the initial release. Verify each advertised provider/integration separately.
6. Review current privacy/data-safety forms, live policy, screenshots, permissions and
   developer verification. The old PLAY_LISTING.md/PRIVACY.md are historical drafts.
7. Complete Play's tester-duration requirement, collect real feedback, apply for access,
   then obtain explicit owner approval for any upload/publication step.

## Production-access application preparation

Known:12 opted-in testers;9 continuous days at inspection; closed Alpha138 is live.
Needed from actual testing: how testers used the app, their feedback, defects fixed as a
result, and evidence supporting readiness. Do not invent tester engagement or feedback.

## Draft release notes

- Fixes two speech crashes during idle chatter and cloud transcription cancellation.
- Improves compact driving layout, avatar creation retry/cancellation and result previews.
- Adds optional diagnostic sharing with operational metadata only.
- The Play edition supports launching apps and preparing drafts; autonomous control of
  other apps' screens is unavailable in this edition.

## Build and regression receipts

- Version 1.3.94 / code 150; debug package `com.example.meowmba`, release package
  `com.meow.meowmba`. Build includes the configured Meta wearable SDK.
- Full Gradle build completed: debug APK, instrumentation APK, minified release APK,
  release AAB, and 2,697 unit tests in 398 suites. Zero failures/errors; five skipped.
- Release-critical `lintVitalRelease` passed. This is NOT a full lint pass.
- Full debug lint has **21 errors, 166 warnings, 12 hints**. A temporary extraction
  of SettingsCategory into a separate file allowed lint past a Kotlin UAST crash;
  that diagnostic-only edit was reverted, so shipped sources retain the built form.
  Findings are preserved in `full-lint-findings.txt` in the artifact archive.
- Required follow-up before broad release: replace API-33 `InputStream.readNBytes`
  in ArizonaWorkZones for supported API 31/32; fix/check Bluetooth permission paths
  (9 errors); replace CameraX restricted surface-provider reads (4); declare and
  review camera experimental API usage (5); correct one suspicious indentation and
  use LocalActivity rather than a LocalContext activity cast (1 each).
- Bundletool 1.18.3 validated the AAB. Required native libraries and image JNI exports
  passed; every packaged native library has at least 16 KB ELF load alignment.
  This is packaging evidence, not a 16 KB-device runtime test.
- Preview 150 installed in place with the original debug certificate. All 148 protected
  library/model/runtime asset entries match preview 149 byte for byte.
- On-phone support-report generation and FileProvider readback passed, together with
  the 12-cycle HTTP cancellation/reuse regression (two tests, 1.301 seconds).
- Archive: `/Users/prog/Meowmba-Releases/production-readiness-20260919/`.

## Signed artifacts

Signed using the existing private-server upload key; no keystore or passwords were
copied to the Mac. Downloaded artifacts were independently checked locally.

- APK: `meow-1.3.94-code150-release.apk`, SHA-256
  `b510f1f0787ea0588bb780d1814b05d47754909a698baa42bb99cc7478ad571c`.
- AAB: `meow-1.3.94-code150-release.aab`, SHA-256
  `46a82494669c8a63b9e5c3108136ae6c97d5eaaf328751d234d422e40771982f`.
- Upload certificate SHA-256:
  `2103e4b14acf74a462218b25f2a04419856c5c2648d13db14f6ff8c4fa247fc0`.
- Signed APK reports `com.meow.meowmba`, code 150 / 1.3.94. Its manifest omits
  MeowAccessibilityService. APK signature verifies; AAB JAR signature and bundletool
  validation pass; all 85 native libraries pass alignment and required JNI checks.
- Jarsigner reports the expected self-signed upload certificate and no timestamp;
  this is not a trusted public certificate chain and was not presented as one.
- Debug APK SHA-256:
  `063ba66b9b8268fefd9de35bf4d5167112a13b122a13f9783414daf5a2ea2921`.

## Sustained preview observation

Corrected endurance test passed in 607.036 seconds. The measurement itself lasted
600.007 seconds: 61/61 foreground samples, no cancellation, no severe thermal samples.
The selected Lumen avatar does not emit fly-motion telemetry, so motion acceptance is
explicitly unavailable, not passed. USB remained connected throughout; unplugged battery
measurement is invalid. Battery temperature went 36.4 to 36.8°C, thermal status remained
LIGHT (1), and combined app/instrumentation CPU averaged 75.21% of one core. This is
high and is not a thermal/performance sign-off or a valid controlled comparison to the
separate 180-second baseline. See `endurance150.jsonl` and `endurance150.txt`.

## Fresh signed-release smoke check

The release package was absent before testing. Signed APK installation succeeded
alongside the existing preview, without clearing or replacing preview data. Cold start
reported OK in 461 ms and displayed “Meet your Meow”, “Let’s go” and “Finish later”.
This verifies launch into fresh onboarding only; completing onboarding, providers and
native model loading on the signed build remains acceptance work. No permissions or
cloud accounts were enabled. Preview150 was restored to the foreground afterward;
the separate signed-release package remains installed at the initial onboarding screen.

Release exit history was empty. Preview exit history after the install contains expected
package-update and instrumentation-completion stops; the crash buffer still contains
only the two historical afternoon147 crashes, with no newer app crash observed.

## Source and next work

Changes are saved locally on `recovery/known-good-138`; no source was pushed publicly.
The artifact archive's `source-receipt.json` records the exact final local commit.
Next technical batch should fix the full-lint compatibility/permission/camera errors,
resolve native CPU baseline support, then rebuild with a new version code and repeat
relevant checks. Candidate150 is not approved for production publication.

