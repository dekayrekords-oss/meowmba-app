# Meow Assistant 1.3.96 — tester update report

September 19, 2026 · version code 152 · package `com.meow.meowmba`

## Distribution status

The owner authorized uploading the newest build to the existing Alpha closed test and
publishing the corresponding feature update on the existing Weebly website. Build 152
has been uploaded and submitted for a full rollout to the existing Alpha closed test.
The final Console check at 21:47 America/Phoenix on September 19 (04:47 UTC September 20)
shows “Changes in review” and “Your changes are now in review.” The automated quick-check
progress has cleared. Managed publishing was already off, so successful review can publish
the tester update automatically. It is not yet confirmed available to testers.
The corresponding website update is published and verified live, as recorded below.
This is a tester release, not a production launch. Alpha previously served 1.3.82/code 138.

## What changed since the previous tester build

### More dependable voice

Two recorded speech crashes are fixed: one used Android speech recognition from the
wrong thread during idle chatter; another failed when a cloud-audio response was cancelled.
The app now hands speech work to the main thread correctly and uses the bundled OkHttp
transport for cloud-audio cancellation. Tests cover repeated cancellation and successful
reuse. These repairs do not establish noisy-room recognition quality for every provider.

### Privacy choices that stay off

Fresh setup begins with microphone off. Choosing “Not now” or finishing setup early
leaves microphone/camera off, and Android permission denial leaves the app's switches
off. The choices survive a restart. The setup prompt requests only the two sensors it
explains. Existing saved choices remain intact during an upgrade.

Optional diagnostics can be shared from Settings → Help & feedback through Android's
share chooser. The report contains bounded version, health, mode and process-exit
metadata; it does not automatically upload chat, credentials or recordings.

### Avatar creation and environments

Avatar creation has clearer elapsed-stage feedback, retry and cancellation controls,
a scrollable review dialog, and better preservation of a validated design when optional
refinement fails. Only approved output is equipped. Failed initial generation is still
reported as failure rather than silently substituting an invented result.

Five animated backgrounds were added: Saffron dunes, Alpine cableway, Coral twilight,
Lantern courtyard and Crystal cavern. Backgrounds use
the existing lifecycle and thermal-controlled animation clock.

### Clearer driving and Computer results

Driving status is more compact, including landscape, with a separate scrollable details
view. Real crash/vehicle alerts retain priority. This is interface work, not certification
of a driving-safety or emergency-response system.

Computer setup better recognizes compatible Termux installations and existing Ubuntu
containers, and restores verified saved connection details. Completed small-file results
have Save & preview actions, hash/readback verification and access through Files. Repeated
saves are deduplicated, and changed copies preserve older files. Large transfers and a
fully unified project/session workflow are still unfinished.

### Performance and compatibility

A sensor-driven redraw bug was fixed: the avatar no longer redraws just because a sensor
updates when its animation clock is paused. Existing detail, cadence and thermal limits
are preserved. In a same-phone 90-second USB-connected comparison at the same Android
thermal status, mean CPU fell from 87.62% to 36.40% of one core (about 58%). That observation
compares 150 with 151; it is not a 152 battery-life benchmark or an all-day guarantee.

Android 12 road-feed compatibility and Bluetooth permission-revocation/socket handling
are fixed. Native bridges use baseline ARMv8-A, and Needle checks CPU support before
loading its vendor runtime. Older physical devices still need acceptance testing.

### Play edition scope

The Play edition supports launching apps and preparing drafts. Autonomous Accessibility
control of other apps' screens is unavailable in this edition. Robot actuation remains
quarantined. Optional wearable, HUB and neural features should be treated as development
features until their specific hardware workflows have been validated.

## Validation completed

- Full build: success; debug and signed/minified release APK/AAB prepared.
- Unit tests: 399 suites, 2,708 total; 2,703 passed, 5 skipped, 0 failures/errors.
- Full lint: 0 errors in debug and release; 162/173 warnings respectively remain visible.
- Four focused device regressions pass on both a physical Samsung Fold and a 16 KB emulator:
  rendering cadence, native entry points, speech cancellation and diagnostics readback.
- Signed fresh-install “Not now”, “Finish later” and Android-denial paths pass, including
  cold restart. Exactly two Android permission prompts appeared; both were denied.
- All 149 protected preview model/runtime entries match 151. All 86 bundled native libraries
  pass 16 KB alignment checks. Bundletool and existing upload-certificate checks pass.
- Both phone packages were upgraded in place without clearing personal app data. No new
  app crash appeared in the checked buffers. Release R8 mapping and artifacts are archived.

## What testers should exercise

1. Upgrade normally and check that chats, preferences, appearance and provider setup remain.
2. On a spare fresh installation, decline sensors or finish setup later; check MIC OFF and
   camera off after restarting. Do not erase a personal installation for this test.
3. With deliberate microphone permission, test quiet/noisy speech, interruptions, silence,
   offline/provider failures and returning from the background.
4. Try avatar preview/refine/retry/cancel and the new backgrounds; rotate/fold and use large text.
5. With an intended Computer connection, approve one synthetic file task, Save & preview,
   find it in Files, and test denial/cancellation without broad file permissions.
6. Observe ordinary unplugged use. Report duration, brightness, charging state, ambient
   conditions, warmth, battery change and workload; do not disable thermal protection.
7. Report exact steps, expected/actual behavior, device, Android version and app version.
   Share diagnostics only to a destination you choose; never include keys or passwords.

## Remaining release gates

Unplugged endurance, older physical ARM64 devices, live-provider/noisy-speech acceptance,
final privacy/Data safety declarations and any advertised hardware integrations remain.
At the current Console inspection, 12 testers have 9 continuous qualifying days toward 14,
and production access is not granted. Actual tester usage and feedback are needed for
that application; automated tests and opt-in counts do not substitute for feedback.

## Console receipt

[Publishing overview](https://play.google.com/console/u/0/developers/5214154317103934390/app/4972949930318434862/publishing).
Release: “1.3.96 (152) - Stability, privacy and creations”, Alpha release 11, 100% of this
closed test. Only this one release change was submitted; tester membership, production
track and managed-publishing settings were not changed. Console accepted the bundle with
ReTrace mapping and native debug symbols attached, and showed no loss of supported
device models compared with 138. Device-catalog counts are not hardware acceptance tests.

## Website publication

The [Meow Assistant feature update](https://www.dekayrecords.com/meow-assistant.html#mx-release152)
is published through the existing Weebly site. Weebly confirmed “Website Published!”;
the public page was then loaded and inspected. The new section, “A steadier companion.
More ways to create.”, describes avatar creation, five backgrounds, persistent sensor
choices, verified Computer results, the driving interface and reliability improvements.

An expandable verification report includes the actual test counts, the limits of the
CPU comparison, remaining acceptance work and a tester checklist. The page labels build
152 as submitted to Alpha, subject to Google review and rollout. Existing beta-group,
closed-test and video links are preserved. Older copy promising autonomous phone taps,
swipes or learned navigation was corrected to match the Play edition. Cloud/provider
requirements and differences between local and cloud features are stated.

The published desktop layout and visible text were checked; a separate mobile layout
check was not performed in this publication pass. Existing hosted design assets, media
and the earlier feature section remain. No site plan or payment settings were changed.
See [website publication receipt](../youtube/weebly/MEOW_TESTER_UPDATE_152_2026-09-19.md).

## Follow-up: listing and user manual

The owner subsequently requested the Play listing and user manual update. Both en-US
description fields were saved and submitted. Google required restarting the pending
review to include them. At 22:03 America/Phoenix September 19 (05:03 UTC September 20),
Alpha build 152 and both listing changes appeared together under “Changes in review”;
quick-check progress had cleared. Publication is still not confirmed.

The existing Documents/Meowmba-User-Manual.pdf is now a 23-page build-152 guide, with the
original 57-page build-124 PDF preserved in the release archive. Setup, provider saving,
care mode, privacy, Computer results and Play-edition limits were corrected. All pages
were rendered and visually checked, with 23 bookmarks and no text beyond the margins.
See [listing text and documentation receipt](PLAY_LISTING_152.md).

## Traceability

Source commit: `039da2c5130d878a4751281b914578ac2908f0d2`.
AAB SHA-256: `38567ff645d05802dd8326c0ae7261ee2d087de852ae6a097bdb66a64405ef99`.
Evidence archive: `/Users/prog/Meowmba-Releases/release-fixes152-20260919/`.
Technical receipts: RELEASE_FIXES_152.md, RELEASE_FIXES_151.md,
SPEECH_CRASH_149_PASS_DOWN.md, DRIVE_CREATION_BACKGROUNDS_148_PASS_DOWN.md and
RESULTS_IDLE_147_PASS_DOWN.md. No Android source is published to the website or public GitHub.
