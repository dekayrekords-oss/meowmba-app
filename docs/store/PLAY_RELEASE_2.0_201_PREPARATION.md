# Meow Assistant 2.0 / 201 release receipt

3 October 2026. Local source branch `release/play-2.0-201-20261003`, app/version revision `e6efa2a`; application code retains the validated 200 policy fixes. Health/privacy/disclosure documentation revision `47df73c`. Android source was not pushed remotely.

## Production submission

The matching existing upload key was recovered from `/Users/prog/Meowmba-Releases/play-upload-key-20261001`. Its certificate matches the active Play upload certificate. No key or credential was reset, replaced or published.

Signed 201 / 2.0 bundle was uploaded, processed and accepted. The saved release is `2.0 (201) – First Play production build`, configured for 100% rollout across 172 countries/regions. It excludes old 152. Seven changes were submitted; Publishing overview shows **Changes in review**, with automated quick checks still running. Google dispatches the submission for review after successful checks. **Production is not yet approved or live.** Managed publishing is off.

Alpha 201 full rollout is included in the review submission and excludes previous 152. Internal 201 is already available to internal testers and excludes previous 2. The internal review had only an APK-size increase warning, not a release error. The historical artifacts remain in release history.

## Listing and declarations

Saved en-US short/full listing and 2.0 notes include Desk, Flow/North Stars, memory correction/pinning, projects, Files, configured Computer tools and compatible smart-ring connection/local on-demand readings. Ring results are hardware/firmware dependent, experimental and nonmedical. Play cross-app gesture controls are excluded.

Health apps declaration: Activity and fitness; ACTIVITY_RECOGNITION purpose supplied. Optional Flow physical-activity context is disclosed as collected/shared Fitness info, optional, not ephemeral, for App functionality and Personalisation. Local ring readings are not automatically cloud inputs or shared diagnostics.

Published and verified public privacy HTML: https://dekayrekords-oss.github.io/Meowmba/privacy.html . Static-site commit `44de59a4ebb34ec07876dfe53c3aa40d5e442bd5`; live SHA256 `f6c7514086791a947de0fb708fd7e4baa54606b09a7a43daf1276b3207ce4ab1`. Updated Flow precision/foreground location, pickers, activity/ring data and required nonmedical notice. Only public `docs/privacy.html` was uploaded; no Android source or credentials.

## Phone and validation

Signed private 201 / 2.0 APK is ready for an in-place update. No adb device was connected at completion, so 201 is not installed. Last verified Fold install is 200 / 1.5.34. Its certificate and all 161 protected runtime/model/native entries match 200; 16 KB ZIP alignment passes. Physical ring/sensor acceptance remains unverified. Private cross-app ring controls are retained.

Public signed bundle passes jarsigner certificate verification and bundletool validation. All 758 payload entries are byte-identical to the validated unsigned bundle. Actual-release 12 permission-boundary tests pass with zero failures/errors/skips. Manifest removes broad media/background location and both Accessibility services. All 88 native libraries pass 16 KB alignment and required JNI checks; public APK ZIP alignment passes. Lint: 0 errors, 219 existing warnings, 12 hints. Application code is unchanged from 200, whose full debug suite passed 3235 cases, zero failures/errors, 17 skips.

Signed AAB: `outputs/Meow-2.0-201-play.aab`, 305230135 bytes, SHA256 `8f665ae283ff92f4fbaea659d989b91e24c7ca6f31e560d816fc283d902dac16`.
Private APK: `outputs/Meow-2.0-201-phone.apk`, 466226406 bytes, SHA256 `7e2f3ed70ed3e2d4912479cdea5970baa292c916d51eb5bb25cf668a948b6770`.
Unsigned immutable candidate: `work/production201/Meow-2.0-201-unsigned.aab`, SHA256 `dee6c8b0a5edf77da0a198ae8f9d21a9e9deaba4a75d3eb98044bb6815efa27d`.

Evidence: `work/console-201-receipt.json`, `work/production201/signed-receipt.json`, `work/debug-201-validation.json`, `work/public-201-validation.json`, `work/logs/public-201-build.log`. User-facing status/APK/AAB and saved screenshots are in `outputs/`.

201 consumed; next Android code **202**. User already authorized production upload/full rollout; no new go is needed. Remaining external steps are Google automated checks and review approval.
