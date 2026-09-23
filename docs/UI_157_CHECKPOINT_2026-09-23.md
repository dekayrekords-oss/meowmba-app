# UI repair checkpoint — build 157

Stopped at the owner's request on September 23, 2026, after installation and launch verification. This is a checkpoint, not completed physical acceptance.

## Installed artifact

- Preview package `com.example.meowmba`, version 1.4.1/code 157, daily variant (shrunk, non-debuggable).
- Source commit `c9f74c9`; checkout was clean at build start.
- APK: `/Users/prog/Meowmba-Releases/ui157-20260923/meow-preview-1.4.1-code157.apk`.
- SHA-256: `2a64d10512f25d7ea187ec84337434c2fa5edbf30360c96f52fb0004d35f904d`.
- Certificate SHA-256: `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
- Installed with `adb install -r` on SM-F966U1 / RFCY61NJEVM at 08:22 America/Phoenix. First install remains September 18 at 10:56:14. No uninstall, data clearing, or sensor enabling.
- Launch succeeded and process remained alive. UI dump confirms the new Flow Menu, Add task, Desk, and Settings controls. No Meow entries in the captured crash buffer.

## Changes

- Visible full-menu navigation restored to Flow, Desk, and other companion avatars; Menu contains existing tools plus direct Settings, Flow, and Desk entries. Back closes the menu.
- Each of the seven desk objects opens a relevant detail sheet, with an explicit action into the corresponding existing screen. Empty objects remain visible and explain what can be added.
- Desk rendering and touch targets share a centered viewport on wide screens; wide-screen navigation moves to the side.
- Flow uses the approved bundled Source Serif 4 and Archivo fonts, desk paper uses Patrick Hand. Font licenses and provenance are included. Removed the mismatched Material top toolbar.

## Validation so far

- Four new navigation regression tests pass, including coordinate touches on all seven objects in portrait and wide/reduced-motion layouts, sheet close controls, and visible navigation to the full menu.
- Full suite: 430 suites / 2,837 tests, four failures, zero errors, five skips. The four failures are old CartoonInterfaceUiTest assertions expecting the former `Memba` heading. Those tests also still expect `Return to avatar`; both labels should be updated to `Meow menu` / `Close menu` and the suite rerun. All other tests passed.
- Daily lint finished with zero errors, 172 warnings and 12 hints. APK assembly succeeded; the combined Gradle invocation exits failed because of the four stale assertions.
- Runtime verification passes: 149 protected native/model/asset entries unchanged; two generated ART profiles regenerated. APK signature and 16 KB ZIP alignment pass.
- Full logs, initial test XML, signing and runtime receipts, and before/installed screenshots are in the artifact directory.

## Resume here

1. Update the two obsolete labels in `app/src/test/java/com/example/meowmba/CartoonInterfaceUiTest.kt`, then rerun that class and HomeNavigationRegressionTest. No production code change is required for those assertions.
2. Complete physical acceptance: every desk object opens/closes its panel, sheet actions open the right destination, Settings and Menu work from Desk, menu Back works, and Flow navigation/task entry remains usable. Check actual screenshots against approved references and confirm sensors remain off.
3. Prepared phone script `/tmp/meow-ui157-desk-check.py` is NOT yet run. It checks seven desk sheets by coordinate taps; start from Desk first. Helper `/tmp/meow-ui157-phone.py` saves UI dumps/screenshots into the 157 archive.
4. Finish the receipt and synchronize the earlier recovery checkout only after validation. That checkout remains at 8ef3fb7; active source is in Meowmba-flow-20260922.
5. Meow server SSH timed out; no server push, Play upload, or website publication happened.

All Gradle and install commands have completed. No ongoing build or test job was left running.
