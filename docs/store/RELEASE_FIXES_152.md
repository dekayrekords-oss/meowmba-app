# Release fixes — candidate152 (1.3.96)

Built, signed and tested locally. This continues candidate151,
commit `ff936b2`; it does not publish to Play or change a hosted policy.

## Fresh-install permission fix

Candidate151 reached the signed-release home screen after “Not now”, but incorrectly
showed MIC ON even though Android had not granted microphone access. That acceptance
check failed and prompted this follow-up.

- Initialize fresh, unfinished setup with a persisted microphone-off preference before
  constructing the ViewModel. Existing stored choices and completed legacy setup retain
  their behavior. Camera already defaults off.
- “Not now” persists microphone and camera off. “Finish later” inherits the fresh off
  default. Android denial during setup also turns the corresponding app switch off.
- An Android grant during the explicit setup request enables that sensor. A late result
  after skipping cannot undo the saved off choice.
- The setup permission request is limited to the mic and camera explained on screen.
  Other feature permissions are no longer bundled into this consent step.

The focused suite passes 31 tests, including five new default/choice/permission cases
and the actual “Not now” button callback in the existing UI regression.

## Inherited evidence and limits

Candidate151 removes all 21 lint errors, checks Needle CPU compatibility before native
loading, builds baseline ARMv8-A bridges, and fixes sensor-driven avatar redraws.
See [candidate151](RELEASE_FIXES_151.md) for the measured CPU reduction and ten-minute
USB-connected observation. Those measurements are for151, not a new152 benchmark.

Release acceptance still needs unplugged endurance, an older physical ARM64 device,
real-provider/noisy-speech acceptance, verification of any advertised wearable/hardware
flows, the final privacy/Data safety review and actual closed-tester feedback. Play
production access was not granted at the September19 inspection. No tester engagement,
production approval, upload or publication is inferred from these automated checks.

## Validation receipts

- Full build passed in 12m 40s: debug/test APKs, minified release APK/AAB, full unit suite
  and full debug/release lint. 399 suites / 2,708 tests, zero failures/errors, five skipped.
- Debug lint: 0 errors, 162 warnings, 12 hints. Release lint: 0 errors, 173 warnings, 12 hints.
  No new baseline or blanket suppression. Warnings remain visible.
- Four device tests pass on the Fold (2.442s) and 16 KB emulator (15.679s): paused-avatar
  redraw prevention, native CPU/image entry points, cloud-speech cancellation, diagnostics.
  An initial emulator command had two incorrect test class paths; the corrected run
  passes and the initial harness failure is retained separately.
- All 149 protected preview runtime/model entries are byte-identical to151. Preview signing
  certificate is unchanged. APK and AAB use the existing upload certificate; package
  com.meow.meowmba, version 1.3.96/code 152. Bundletool, all 86 native-library 16 KB alignment
  checks and required JNI exports pass. Accessibility remains excluded from release.
- Both phone packages were updated in place; preview152 is foreground with microphone
  and camera off. Crash buffer contains only the historical147 failures at 14:45/14:50.
- Fresh signed release in a disposable, read-only 16 KB emulator: “Not now” → home shows
  MIC OFF and camera off → cold restart retains MIC OFF. No actual sensor grant.

- `meow-preview152.apk` SHA-256: `115e4b5c5ca17505aa02b511b262382ed0a53ac4ecd4d93f51031f52466562fd`.
- `meow-preview152-tests.apk` SHA-256: `351a647a5db333320e53d0c5714e41e9a0614c1851e6526ab47be30f0580350d`.
- `meow-1.3.96-code152-release.aab` SHA-256: `38567ff645d05802dd8326c0ae7261ee2d087de852ae6a097bdb66a64405ef99`.
- `meow-1.3.96-code152-release.apk` SHA-256: `552dac97df0daa4570191d815776bc6a0a14b3f7a29c4617f02e6ed640b6028f`.

Archive: `/Users/prog/Meowmba-Releases/release-fixes152-20260919/`.

The additional fresh “Finish later” and Android-denial flows also pass, including cold
restarts. Exactly two system prompts appeared (camera and microphone); both grants are
false in Android package state. The final screenshot visibly confirms MIC OFF and
Camera off. No app crash appears in the emulator crash buffer. R8 mapping is archived
and native JNI class names are preserved.

Only the newly installed test package inside this task's read-only emulator session was
cleared between fresh-install scenarios. No phone app data or original AVD disk data was
reset. The temporary emulator was closed after preserving all receipts.

## Draft152 release notes

- Fix speech cancellation crashes and improve Android12/Bluetooth compatibility.
- Reduce unnecessary avatar redraws and check native-model CPU support before loading.
- Keep the microphone and camera off when setup is deferred or permission is declined.
- Add optional diagnostic sharing; retain explicit approval for assistant actions.
- The Play edition supports app launching and drafting; autonomous screen control is
  unavailable in this edition.
