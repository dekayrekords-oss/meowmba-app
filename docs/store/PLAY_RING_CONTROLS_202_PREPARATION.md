# Ring controls in the Play release — candidate 202 / 2.0

The owner requires phone-touch-free ring scrolling and clicking in the regular
Play edition. This expands the existing 202 allocation on
`feature/flow-idle-default-20261003`; the next code remains 203.

## Implementation

- `RING_CONTROLS_ENABLED=true` is separate from public
  `PHONE_AUTOMATION_ENABLED=false`. The release manifest includes the ring
  service and still removes `MeowAccessibilityService`.
- The ring service is explicitly not an accessibility tool. Its binding retains
  Android's signature-only `BIND_ACCESSIBILITY_SERVICE` permission.
- A separate disclosure appears after connection without prior consent, and
  before Android Accessibility settings. Agree and continue is required.
  Declining, dismissal or an older Android grant cannot enable actions.
- Consent is stored separately. Missing consent forces pause. Withdrawal ends
  testing, pauses, hides the target and cancels pending touches. Delayed gestures
  recheck consent and existing lock/app/pause/test/sleep restrictions.
- Apply the scroll/click preset: swipes scroll, single tap clicks, one tap plus
  swipe moves the target horizontally and two taps plus swipe vertically.
  Verify physical gestures and input descriptor, choose allowed apps and unpause.
  All-app operation remains an explicit option. Double-tap wake is consumed and
  cannot also click or go Back.
- Bundle verification requires the ring service and continues to reject the
  autonomous phone service, broad media and background-location permissions.

The ring controls phone UI. Configured Computer tools remain a separate feature.
No ring-driven vehicle controls, direct PC cursor or physical robot movement is
introduced.

## Validation

24 focused SDK-free debug checks pass, zero failures/skips. They cover mapping,
verification, target bounds, click selection/fallback, delayed-touch cancellation,
consent gates, sleep/wake, sensors and the small-phone setup/disclosure workflow.
The actual native 320dp consent image was inspected: full disclosure, Not now and
Agree and continue are readable. Consent withdrawal and no-consent unpause/test
blocking are exercised. Evidence: `../video-meow-2.0/ring-release-tests-final.log`.

16 actual public-configuration checks pass on API 31/35, zero failures/skips.
They confirm the signature-protected ring service, its explicit non-accessibility-
tool metadata, absence of the autonomous service, consent/verification/app gates,
and the existing media/location boundaries. Refreshed `lintDebug` passes with
zero errors, 231 warnings and 12 hints. Remaining findings include KTX style
suggestions, dependency-version notes and existing platform/manifest findings;
one KTX suggestion points to the new consent preference write.

These use the supported SDK-free test variant, task-local unchanged SQLite/
Robolectric JNI bootstrap, and explicitly skip Meta release preflight for unit
validation only. The release tests run separately with `release-test.init.gradle`
and the already compiled local helper, avoiding shared native-library loading
across KSP classloaders. This is not full-SDK or minified artifact acceptance,
or permission to upload an SDK-free release. Evidence:
`../video-meow-2.0/ring-play-checks-isolated.log` (successful in 34 seconds) and
`../video-meow-2.0/ring-play-boundary-lint-final.log` (lint tasks completed).

The updated bundle checker correctly rejects the existing 201 bundle because it
lacks the now-required ring service; its other native/permission checks pass.
Python compilation and Git whitespace checks pass. Evidence:
`../video-meow-2.0/old-201-ring-bundle-check.json`.

## Listing, policy and film

The current listing draft includes ring scrolling, aiming and clicking, with
72-character short and 3909-character full descriptions. The privacy HTML and
`PLAY_ACCESSIBILITY_RING_202_DRAFT.txt` describe the separate local ring service.
The listing changes are saved as a Play draft. The privacy page is published and verified. The Accessibility declaration/reviewer recording is unfinished and unapproved. No false disability-tool designation is used.

Google's current guidance permits narrow deterministic user-defined actions and
requires disclosure, consent, a declaration and approval for non-accessibility
tools: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en
The proposed service-specific data answer accounts for local processing and
user-directed diagnostic export under its Data safety guidance:
https://support.google.com/googleplay/android-developer/answer/10787469?hl=en

Both storyboard pages and the 300-second film plan now show physical ring
gesture-to-phone actions in car/Desk/Flow scenes, without private-edition labels.
All sixteen built-in forms, full-body dragon creation, camera photo recognition,
Studio planner/refinement, default idle Flow, opt-in charger/avatar, TV Orb and
labeled future robot remain. Screen inserts require real app recordings and
hardware acceptance. Storyboards were edited with built-in image generation;
the finished Higgsfield film and narration remain unrendered.

## Full-SDK artifacts and phone update

Existing verified registration and package credentials were reused in memory,
without writing a credential file or skipping release gates. The minified
202 / 2.0 bundle includes Meta SDK 0.8.0, all 88 aligned native libraries and
required JNI exports. Bundletool validates it. Sixteen actual full-SDK public
configuration checks pass, zero failures/errors/skips. Signing preserves every
payload entry; embedded certificate matches the existing Play upload key and
JAR signature verification succeeds. Evidence: `../production202/` receipts.

The full-SDK preview APK is installed in place over verified build 200. Its
certificate and 16 KB ZIP alignment pass. All 160 runtime entries remain
unchanged; only the reviewed manual adds the default Flow idle instructions.
The original installation date is retained. At 75 seconds the app has the same
live PID and an unchanged crash buffer. The ring is connected, paused, with no
visible target; consent has not been accepted. Physical gesture acceptance is
still required. The signed 202 bundle is published to internal testing and available to testers. Production 201 remains in review; 152 is superseded and explicitly Not published. No public production download is live.

## Remaining release work

Real scroll, horizontal/vertical cursor and single-tap click acceptance, a
reviewer recording, the Accessibility declaration and Play approval remain
required before production submission. The ring privacy HTML is published at the existing public URL and its live
bytes exactly match the reviewed 202 policy.
Build 201 was submitted without these controls; its last verified status is in
review. Build 202 is not submitted to production. No remote Android source push.
