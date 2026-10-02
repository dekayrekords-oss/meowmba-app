# R08 Smart Ring (Android)

Open Settings → Smart Ring. Disconnect your R08 from QRing, wake it, scan,
select it and accept Android's pairing prompt. Enable Meowmba Ring Controls
in Accessibility settings. This Android app requires Android 12 or newer; ring setup uses Nearby Devices permissions.

Controls start paused. Turn on the gesture tester and verify swipes and taps.
The tester binds one Android input-device descriptor (reset gesture verification to bind again); only confirmed gestures
can execute actions. Turn the tester off, choose allowed apps (or enter their package IDs),
then turn Pause ring controls off. Next/Previous scroll, double tap goes Back,
and triple tap opens this page; single tap is unmapped. Directions, distance
and each action are configurable. Firmware may expose different tap behavior;
use the live log rather than assuming all taps work on version 3.10.46.

The connection sends three 16-byte 0x3B control frames (media-key profile 1,
built-in gesture mode off, wake). It checks the R08 service/characteristics,
subscribes to notifications, serializes GATT writes, and reconnects up to three
times after disconnection. Android manages the HID input channel after bonding.
Accessibility owns background recovery while enabled; without that service,
the setup connection lives only as long as the app process. After a reboot,
service startup attempts to reconnect the saved bonded ring.

Input is local. Actions require an unlocked, interactive phone, a configured
connection, the tested device descriptor, a verified gesture and an explicit
allowed package. Unrelated input devices are ignored. Outside allowed apps or while paused, Android may handle ring inputs as ordinary media keys. Empty allowlists enable
no actions. Diagnostic sharing opens Android's chooser with connection/input
logs, including input-device and foreground-package names. Logs stay in memory
until explicitly shared and contain no screen text. Firmware capture/flashing,
QRing update interception and health-data collection are not included.

Protocol reference: https://github.com/Anezium/R08-Access-Bridge
(service 6e40fff0-b5a3-f393-e0a9-e50e24dcca9e, write 6e400002,
notify 6e400003). This is an independent, limited implementation of the wire
format, not the Rokid-specific bridge or its ADB/self-arm workflow.

Build validation: Android debug APK and Android JVM unit tests. Hardware pairing, firmware
3.10.46 tap support and scrolling in the user's apps require a real R08/phone
acceptance test. Do not label a completed GATT write as proof of gesture support.


## Build 193 validation

Base: release 192 / `4f579ba`. Experimental branch `codex/r08-ring`, preview
package `com.example.meowmba`, version 1.5.27/code 193. Build/test results are
recorded in the build receipt beside the APK. Native bridges are reused from
installed daily build 190; protected runtime/model entries are compared against
that APK, with prior 191/192 manual-copy updates reviewed separately.

No device is connected. Pairing, R08_4D04 firmware 3.10.46, multi-tap timing,
service recovery and cross-app scrolling have not been physically accepted.
The app does not flash firmware or enable Accessibility without the user's
system-settings action. No app installation, Play upload or source push is part
of this integration run.


Validation completed October 2, 2026: full SDK debug build passes; package
com.example.meowmba, version 1.5.27/code 193, certificate matches installed daily
190 (b7850d5f…). Four ring checks pass, including a native 320dp UI render and
allowlist picker interaction. The 24-case avatar integration class also passes
in isolation. The broader run had 3,208 cases, one avatar task-completion timing
failure and four skips; it is not reported as an all-green full suite. Full lint
was stopped after a prolonged scan and remains unverified.

Runtime review: all 160 protected native/model/other runtime entries match
installed 190 exactly. The changed manual matches the release-192 source. Two
R8-generated ART profiles present in daily 190 are absent in the debug variant;
this artifact is a signed **debug test APK**, not a validated shrunk daily or
Play release. Source is committed locally; no installation or upload occurred.


## Phone installation — October 2, 2026

At the owner's request after connecting the Samsung, signed debug build 193 was
installed with `adb install -r` over 190 on SM_F971U / R5GL75XFCXD. The installed
certificate matches; protected runtimes were checked against the APK pulled
from this phone. Android's original first-install time remains unchanged.
Meowmba is the resumed activity, its process remains alive through a 20-second
check, and no new app crash is recorded. Pairing, Accessibility opt-in and
physical ring-gesture verification remain pending. Installation evidence and
the updated receipt are in the chat workspace `work/ring-193/install-evidence`.


## Interactive tester — build 194

Settings → Smart Ring → Gesture tester shows a pulsing ring, physical key press/release,
last recognized gesture and per-session counts. Tiles preview the indicator without sending
commands or granting gesture verification. The recognizer includes up to four taps and
one/two taps followed by Next/Previous; each needs physical verification on this firmware.

Sensor tests query battery and today's ring-reported steps, plus on-demand heart rate,
oxygen saturation, temperature, HRV and stress estimates. Wear the ring and keep still;
HRV allows 80 seconds. One test runs at a time. The GATT owner serializes start/stop writes,
checks 16-byte packet checksums/types and errors, requests the final stop reading when a
measurement expires, and cancels on leaving the screen. Missing replies remain unverified.
Firmware support is confirmed only by actual replies; no simulated reading is displayed.

Sensor readings are session-only, remain local and are excluded from diagnostic sharing.
Disconnect marks old values as from the previous connection. Sleep/health history, continuous
sampling and raw optical/accelerometer streams are not implemented in this tester. The wire
format is independently implemented from documented R08 protocol fields:
https://github.com/Anezium/R08-Access-Bridge/tree/main/ring-health

194 validation: signed full-SDK debug APK (1.5.28/code 194) builds. Nine focused
checks pass: three existing action/protocol gates, four sensor decoder/session cases,
and two native 320dp UI tests (preview, real-reading state, disconnected controls, setup
and allowlist). Synthetic UI readings are test fixtures, not hardware evidence.
All 161 protected lib/assets entries match the actual installed 193 APK, and the
preview signing certificate matches. This is a debug test build; full lint/broad
suite were not repeated. 194 installed in place over 193 on SM_F971U / R5GL75XFCXD on October 2. Android's
original first-install time was retained; MainActivity starts, PID remains stable
through the startup check and no new process crash is recorded. Accessibility
is still off; physical gesture and sensor acceptance remain pending. Evidence:
chat workspace `work/ring-194/receipt.json` and `install-evidence/`.


## Scroll and click across apps — build 195

Enable Android Accessibility → Meowmba Ring Controls, connect the ring, then
open Settings → Smart Ring. Turn on **Allow all apps** and choose **Use scroll &
click controls**. Verify each physical gesture in Gesture tester; turn tester off
and unpause controls. Leave **Show click target** on.

Next/Previous scroll, single tap clicks the visible crosshair, double tap goes
Back and triple tap opens ring settings. One tap + Next/Previous moves the target
right/left; two taps + Next/Previous moves it down/up. The target can also be dragged
with a finger. Center click target resets its position. Gestures unavailable on this
firmware cannot control the pointer. Cursor movement accepts either a verified combo
or its individually verified tap/swipe inputs; clicks and other actions require
their exact gesture to be verified.

The target uses an Accessibility overlay, removed before injecting the tap or
swipe so input reaches the foreground app. Global app scope is a separate saved
opt-in; turning it off restores the explicit app list. Click and target movement
require the visible target option. Controls remain disabled when paused, testing,
disconnected, locked or missing foreground identity. The service reads the active
package only and records no screen text. Android/app touch restrictions still apply.

195 validation: full-SDK signed debug build and 12 focused cases pass, covering
scope/verification gates, pointer movement/bounds and overlay removal, all-apps toggle
and click-preset interaction on a native 320dp UI, plus previous sensor/protocol checks.
All 161 protected runtime entries match the APK pulled from the phone's installed 194.
Clicks also check the app at the target point, and scrolling stays inside the focused
app window (including split-screen). Full lint/broad regression were not repeated.
195 installed in place over 194 on the connected Samsung Fold. The preview certificate
matches, original first-install time is retained, MainActivity launch succeeds and the
PID survives the startup check without a new process crash. Physical ring acceptance
remains pending; Android settings now show Ring Controls enabled. Receipt and evidence are in the chat
workspace `work/ring-195/`.


## Cursor axes correction — build 196

One tap + Next/Previous now moves the cursor right/left. Two taps + Next/Previous
moves it down/up. The preset and instructions agree. Existing users of the original
complete four-combination preset receive the corrected axes once on upgrade;
customized combinations are preserved. Reapply Use scroll & click controls for
the new preset if needed. Signed full-SDK debug APK builds; seven focused cases
pass, including migration preserving customized mappings and native preset labels.
All 161 protected runtime entries and the signing certificate match installed 195.
196 installed in place on the Fold, with original first-install time retained,
successful launch and stable PID/no new process crash. Physical ring acceptance
remains pending. Receipt/evidence: chat workspace `work/ring-196/`.


## Left movement verification fix — build 197

Read-only inspection of the phone's ring preferences showed 1 tap + Next was verified,
but 1 tap + Previous was not. Single tap and Previous were already verified, and
left was correctly mapped to POINTER_LEFT. The exact-combo gate blocked that movement.

Cursor movement now accepts individually verified component inputs as well as an exact
verified combo. One-tap combinations need Single tap + direction; two-tap combinations
also need Double tap. This does not mark unseen combinations verified. Click, Back and
other actions retain their exact-gesture check; input-device binding, app scope, pause,
lock-screen, testing and visible-target checks are unchanged. Blocked gesture logs now
explain the missing verification or target option. Signed full-SDK debug build and nine
focused cases pass, including the exact missing-left-combo state and unchanged click,
pause, app-scope and target gates. All 161 protected runtime entries match the APK
pulled from installed 196, and the preview certificate matches. Installed in place
on the Fold; first-install time preserved, launch/PID stable, no new process crash.
Physical left-gesture retest pending. Receipt/evidence: chat workspace `work/ring-197/`.


## Idle sleep — build 198

Sleep mode is enabled by default with a 30-second idle timeout. Settings → Smart Ring
allows disabling it or choosing 5–300 seconds, plus Sleep now / Wake controls buttons.
Ring input or dragging the crosshair restarts the timer. When asleep, the crosshair
hides and inputs are consumed without scrolling/clicking. Double tap wakes controls
and is consumed without also performing Back or a click. The timer then restarts.

This is local control standby: Bluetooth remains connected so a ring wake can arrive;
it does not command hardware power-off or lock/wake the phone's display. Automatic
sleep runs only with connected, enabled Accessibility controls, unpaused and outside
the gesture tester. Disconnect/pause/testing/disabled sleep clear standby and stop its
timer. Sensor queries remain independent. Fourteen focused checks pass, including
30-second expiry, activity resets, settings changes, delayed Handler deadlines,
consumed double-tap wake, policy gates and native 320dp timeout-setting interaction.
Signed full-SDK debug APK builds; all 161 protected runtime entries match the installed
197 APK, and the preview certificate matches. Full lint/broad regression were not
repeated. Installed in place on the Fold with original first-install time retained;
launch succeeds and PID survives the 35-second check with no new process crash.
Physical idle/double-tap wake acceptance remains pending. Receipt/evidence:
chat workspace `work/ring-198/`.

## Single-tap selection — build 199

Phone preferences confirm Single tap is verified and mapped to CLICK. The original
failure has not been reproduced independently; sleep state at the failed tap is unknown.
Selection now activates the deepest visible enabled clickable control under the crosshair
using Android ACTION_CLICK. Non-clickable labels use their clickable parent. A rejected
action falls back to touch without trying a different ancestor. For custom views without
an accessible control, the crosshair is removed and touch injection waits 100 ms for
Android's input-window update; it rechecks pause/testing/sleep/lock/app state before
delivery. Pending touches cancel with pending ring input. Wake remains double-tap only.

The debug service dump includes local ring state and recent input/action events, without
screen text or sensor values, so subsequent hardware reports can be diagnosed. Nineteen
focused checks pass, including nested-control selection, label/parent handling, rejected
actions, hidden/disabled/outside controls, overlay removal delay, cancellation and active-app
changes. Signed full-SDK debug APK builds; all 161 protected runtime entries and certificate
match the actual installed 198 APK. Full lint/broad regression were not repeated.
Installation and physical single-tap acceptance pending. Evidence: chat `work/ring-199/`.
