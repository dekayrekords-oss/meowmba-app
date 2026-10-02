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
