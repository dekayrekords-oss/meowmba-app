# Cozmo integration — preview 174 / 1.5.8

## Use

Open **Robot → Cozmo · Wi-Fi**. Wake Cozmo on his charger and use **Wi-Fi settings** to join the network whose password appears on his face. Android may ask whether to stay on a network without internet; keep this connection. Close the official Cozmo app, return to Meowmba, and tap **Connect**. The robot itself must return firmware, body identity and fresh sensor packets before the screen reports connected.

This is a direct Android implementation: no Python runtime, laptop, bridge server, Cozmo app, subscription or downloaded animation pack is required. Only Cozmo firmware **2381**, the version documented by the reference, is accepted. Other firmware is rejected before sending Enable or motor commands. Physical interoperability still requires testing on the owner's robot.

The robot socket alone is bound to the Cozmo Wi-Fi network. The app does not change Android's process-wide network route. Cellular can remain available for other internet traffic when Android provides it; the home LAN/MeowLion route will generally be unavailable on Cozmo's network. Meow's on-device model is the portable offline option.

## Included

- Explicit connect/disconnect, connection and error messages, firmware/body serial.
- Live battery **voltage** (not an invented percentage), head angle, lift height, wheel speed, odometry and charging/pickup/fall/cliff flags.
- Head up/center/down; lift up/down; camera headlight on/off.
- Short forward/back/left/right moves, with a separate floor-move switch and immediate Stop control.
- Six original monochrome eye expressions: neutral, happy, curious, surprised, sleepy and sad; blinking and optional Meow mood reflection. These do not copy Anki animation assets.
- Seven backpack-light choices: green, blue, red, yellow, purple, white and off.
- Local 320×240 grayscale camera preview, requested at about three frames per second. Incomplete/mixed/oversized frames are discarded. Preview stops on leaving the screen; images are neither saved nor uploaded.
- Up to three discovered cubes, explicit cube connection and cube-tap event reporting.
- `cozmo.control` agent tool and deterministic addressed voice commands. Examples: “Cozmo look up”, “Cozmo lift down”, “Cozmo happy”, “Cozmo lights blue”, “Cozmo status”, “Cozmo stop”. Prefixing these with “Meow” also works.
- The existing `robot.move` tool directs its supported motions to Cozmo while Cozmo is connected. Unsupported motions return an error rather than claiming completion.

Meow hears and speaks through the phone. Original Cozmo games, SDK behavior engine, native robot speech/audio, cube stacking, autonomous navigation, face recognition, charger finding and robot-camera AI analysis are **not implemented**. The LOOI controls retain their existing quarantine; Cozmo does not unlock them.

## Implementation and limits

`core/robot/cozmo/` owns the strict wire codec, ordered reliable UDP window, worker, sensor decoder, monochrome face encoding, camera reassembly and Android network binding. Cozmo has a separate controller because the existing `BodyOutput` uses LOOI-specific neck/forward-only semantics; shoehorning Cozmo into those units would misrepresent its actuators. No firmware, nonvolatile storage or Wi-Fi-off commands are implemented.

The phone sends a maximum 0.35-second wheel pulse at at most 60 mm/s (normal commands use 35–45 mm/s). A worker checks stop deadlines independently of UI/model activity. Stale (>500 ms) telemetry, pickup, falling, charger or cliff status block wheel commands. Telemetry silence disconnects after 1.5 seconds. Expired commands are discarded; Stop revokes pending motor retransmissions, clears queued motion and disables driving. Reconnect never restores driving. Leaving the app requests stop and closes the connection; camera preview also stops when its screen closes.

These are implemented software guards, not measured physical stop guarantees. Radio loss or phone/process failure can prevent delivery of a stop packet. First hardware tests belong on a clear floor, with the robot within reach; do not rely on cliff sensors to protect a table edge. Commands report queued/requested, not a claim that a physical movement succeeded. The 0.35-second duration is a phone-side deadline, not a hardware watchdog.

Protocol reference: [PyCozmo](https://github.com/zayfod/pycozmo), revision `1b6dcd9b869a3784f1d8b02e820bb033f95fd13a`. The MIT notice is shipped in `assets/licenses/pycozmo.txt`. Camera JPEG tables and display/protocol behavior derive from this reference. All generated expressions and Android integration are local app code.

## Validation

Preview 174 is built, signed and archived in the Os workspace at `work/cozmo-174/meow-preview-1.5.8-code174.apk`.

- Full JVM regression run: **2,958 tests, zero failures/errors, five existing skips**. The full run preceded the final display-initialization and second background-hook corrections; targeted checks were rerun after those corrections.
- Final targeted run: **14 tests, zero failures/errors/skips**, including the 13 new Cozmo codec, malformed-packet, reordering/retransmission, stop, timeout, firmware, telemetry, face, camera, routing and UI checks. Existing LOOI quarantine remains covered.
- Final Android emulator Cozmo UI test: **one pass**, 2.352 seconds. Verifies both robot panels, scroll access, disconnected controls, and preserved LOOI lockout. A screenshot was visually inspected.
- Full `lintDaily`: **zero errors, 197 warnings, 12 hints**. Final wording-only stop-status adjustment was compiled and tested afterward.
- Optimized `assembleDaily`, debug and Android test assemblies pass. Final packaging uses an 8 GB Gradle heap and excludes the redundant fatal-only `lintVitalDaily` pass after full lint passed. The initial combined build's fatal-only lint task failed; that build was canceled after saving its completed full JVM results.
- Baseline 173 runtime verification: **all 160 protected entries present and unchanged**; only generated ART profiles differ. Signature matches the existing preview identity; **16 KB alignment passes**.
- APK SHA-256: `7d833ad066c0b99da23fc72adc2ceb5f01a43c8d31885126bfddc18403baab79`.
- Optimized preview installed in place on the emulator and cold-launched successfully (494 ms). The same process remained alive with MainActivity foreground afterward; the crash buffer contained only an older September 24 entry, no new crash.
- Preview certificate SHA-256: `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.

No physical Cozmo or Fold is currently connected. The phone has not been updated by this task, and physical Wi-Fi interoperability, actual motor stop bounds, robot-display output, real camera images and cube events remain hardware acceptance work. The software simulator and emulator cannot certify those. Build 174 is consumed; next unallocated code is **175**. Do not rebuild a different release artifact under 174.
