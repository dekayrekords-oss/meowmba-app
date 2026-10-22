# Original scenes and microphone kill — 1.3.50/code54

## Result

Added the six requested concepts as eight original native performances:
Butterfly Chase, Detective, Yarn Play, Ball Play, Acorn Play, Mischievous Raccoon,
Sleepy Nest and Rainy Window. Find them in Avatar → animations → Scenes, or use
explicit commands such as “chase a butterfly”, “play detective”, “play with
yarn”, “play with a ball”, “play with an acorn”, “hide a snack”, “curl up in a
nest” and “watch the rain”. Animal play temporarily selects the appropriate
form; completion/reset restores the saved form.

The scenes combine the existing bounded pose keyframes with original vector
props. One progress value determines prop position and the eye target; gaze
blends back into live attention using the existing spring rig. Camera-off
remains camera-off during authored acting. The snack's sideways glance uses
an eased transition. Each scene includes anticipation, action, a readable hold,
and recovery within six seconds. The catalog now has 47 entries. New scenes are
non-ambient; they do not enter the idle trick pool automatically.

The 48dp **MIC ON / MIC OFF** toggle is directly above the camera play control
on the main screen. It controls the app's input microphone separately from
push-to-talk and speaker mute. The saved `microphone_killed` preference defaults
to false for existing users. Kill latches UI state before stopping capture,
cancels utterance/follow-up/wake-ack/rearm and pending permission actions, stops
the actual active recognizers, and stops background wake service. The service
and activity handoff consult the same saved preference. Delayed startup,
permission, transcript and speech-finished callbacks cannot reopen capture
while killed. Text input stays available. This is an app microphone control,
not Android's system-wide microphone switch.

## Validation and review

- Main owned shared UI/ViewModel/activity, native props/gaze, routing, build,
  integration tests and visual/device checks.
- `body_safety` owned catalog/enum plus catalog tests.
- `attention_review` reviewed mic paths, implemented the bounded background
  service/helper/tests, and found the late wake-ack UI path; main corrected it.
- An initial render fixture needed its nullable end-frame handled. Focused
  checks then caught the raccoon gaze jump and nullable Mockito callback
  matchers; both were corrected. A regression still expected 39 entries; it now
  validates 47 entries and eight searchable scenes.
- Host render checks sample complete scene timelines in portrait, cover,
  square and landscape stages, with colors and privacy marks. Final preview
  uses the production rig and painter; it is scripted, not camera footage.
- Existing animation lifecycle, driving and body safety gates are retained.
  No new engine/assets download or physical motor command was introduced.

Build: bundled Android Studio JDK, `:app:testDebugUnitTest :app:assembleDebug`,
`--no-configuration-cache --max-workers=4`, cached native debug libraries via
`-PprebuiltDebugNativeDir=/tmp/meow-issue-native.QdqMRB`. One build owner.

Debug identity: `com.example.meowmba`, 1.3.50/code54. `apksigner` verified the
configured debug certificate and `aapt2` verified package/version.
APK SHA-256: `19e0031486476fc82f74d53f689eeef9814b0937e33d99b2612f5da9143028ac`.

Final suite/deployment results are recorded below. Physical-device thermal/frame
rate and real microphone recognition of all scene wordings remain separate
acceptance items. Meow fetch timed out before the build; no remote merge, push
or Play publication was performed. Installation follows the user's existing
authorization to update the connected phone without clearing data.

## Final evidence

- Full final suite: **1,474 tests, 1,469 passed, five skipped, zero failures or
  errors**; build successful in 26 seconds after the catalog-count correction.
- Installed with `adb install -r` on Samsung SM-F966U1, serial RFCY61NJEVM.
  Device package inspection confirmed 1.3.50/code54; activity launch Status: ok.
- Actual UI hierarchy places mic control at [946,1953][1004,2011] and camera play
  at [944,2111][1007,2174], confirming the requested vertical placement.
- Tapped mic off in the real UI, then force-stopped/relaunched to check saved
  privacy. Both screenshots/hierarchy showed MIC OFF. RECORD_AUDIO appops had
  a completed 32.323s capture and no new access after restart (last access age
  advanced from 35s to 95s). No preference reset or app data clearing.
- Mic was left OFF after testing and reported to the user. Speaker control
  remains independent. The new mic button explicitly enables capture again.
- Phone reported Thermal Status 4, IsStatusOverride false, and displayed the
  existing cooldown message before mic testing. Live scene playback/phone frame
  rate was therefore not tested or claimed; the thermal pause was preserved.
- Native preview is H.264 720×540, 30fps, 1,364 decoded frames / 45.47 seconds.
  Contact sheet, individual light/dark captures and complete timeline were
  rendered; final raccoon frames were regenerated after smoothing the glance.
- Archive: `/Users/prog/Meowmba-Releases/scenes-mic-1.3.50-code54/`, containing
  debug APK, native preview/contact PNGs, device screenshots, test XML and this
  evidence file. Earlier APKs remain available.
