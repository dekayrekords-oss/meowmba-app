# Build 172 review and action fixes

## Review scope

Based on Claude's `1aba37b` on `feature/meow-flow-core`, including builds 169–171. The leader lines, moving dots, Needs attention panel, Desk/dock interactions, startup repair, mic-dependent music checks and existing local model integration are retained. No model download or provider setting was changed.

Recent accessible phone logs and the visible camera conversation were checked. The installed daily build is non-debuggable, so its private conversation database could not be read through `run-as`; this was not an exhaustive chat-history audit. The visible reply told the user to tap a shutter that was not open. Temporary raw phone diagnostics were removed after extracting the finding.

## Changes

| Area | Problem | Result |
|---|---|---|
| Action truthfulness | Reply-only prompts included a rule against admitting inability to act, encouraging promises without a tool result. | The reply prompt separates capabilities from performed actions and requires current action evidence. Assistant relay wording says which app opens instead of claiming a request was sent. |
| Camera / QR | Natural requests could reach conversational vision handling without a visible scanner. | Explicit scan requests route to a rear-camera CameraX/ML Kit scanner. Permission denial, cancellation, unavailable camera and recognition failures return honest results. Decoded values do not auto-open links. |
| Other apps | No simple one-shot screen-help entry point. | Ask Meow Quick Settings tile, Android capture consent, one frame, result notification, screenshot/text Share target and a question panel. OCR runs locally; configured-provider use is an explicit choice for each question. |
| Lifecycle | Activity recreation could leave its ViewModel reference unset and strand shared input. | Each committed composition binds the retained ViewModel and drains pending shares. A device regression test covers sharing again after recreation. |
| Message navigation | Flow rebuilt a generic package-launch intent and could mark an action done after opening. | Active notifications retain their original conversation PendingIntent. Flow and its notification use that exact destination; opening is not treated as sending. Stale targets report unavailable. |
| Notification identity | Sender hashes could collide or combine distinct conversations, and reconnects could resurrect closed items. | Stable SHA-256 notification-key identity, bounded active targets, stale-event checks and reconnect handling. Old sender-only rows are replaced when a corresponding current notification arrives. |
| North Stars | The owner's North Star document was a design only. | Local goal store/capture, editable steps, evidence links, one next step, momentum, weekly look back and Desk pin. Edits invalidate outdated links; dismissed evidence remains suppressed. |
| Upgrade compatibility | Adding Room tables would reject otherwise compatible version 12 logical backups. | Additive Room 12→13 migration and explicitly scoped 12→13 backup import, preserving existing goals and all conflict/no-overwrite protections. |

## Practical limits

- Screen help answers from extracted screen text. It does not continuously watch or autonomously operate another app. An installed local chat model is needed for local answers; the optional configured provider can receive the extracted text and question, not the image.
- Exact message navigation depends on the messaging app supplying a conversation destination in its notification. Meow does not fabricate private deep links or send test messages to real contacts.
- North Star linking uses bounded recent local evidence and conservative rules, with optional existing on-device Laya decisions. A connection is not proof of progress. Bank-balance inference, comprehensive contact knowledge and automatic attendance detection are not implemented.
- No Play Console or website publication is part of this repair. This is the data-preserving preview update.

## Validation

Installed preview **1.5.6 / 172** on Fold8 (SM-F971U / R5GL75XFCXD) at **2026-09-24 22:09:39 America/Phoenix** using `adb install -r`. Android retained firstInstallTime `2026-09-23 09:05:20`; no uninstall or data clearing occurred. The preview launches and has no new crash-process entry after installation. The separate Play edition remains 1.3.99 / 155. Private settings/keys were not exported or printed.

Final APK SHA-256: `6a6d8f5c72176c7b8aac0f4e690be3c96dd8eb7e834ddd018f033a32dbd1f327`. It matches the existing signing certificate, passes 16 KB alignment, and retains all 156 protected native/model entries byte-for-byte (only generated ART profiles differ). Daily lint completed with **zero errors, 197 warnings and 12 hints**.

The release receipt in `/Users/prog/Meowmba-Releases/northstar172-20260924/` records the final APK, checks and installation. The full JVM run completed 2,936 tests with zero failures/errors and five existing skips. All four device tests pass: real bundled QR decoding, blank-image rejection, real OCR/cache consumption and sharing after activity recreation with the same retained model. The optimized APK is also checked through its visible scanner, goals and Android consent/capture flow.

On the final optimized APK, the real Quick Settings tile → Android consent → Settings capture → result-notification tap opened the picture/OCR/question panel. The capture service had stopped. The emulator without a local chat model displayed the explicit missing-model message when asked a question; it did not silently use a remote provider. The synthetic goal survived the debug/daily in-place updates. Exact-message routing has identity/staleness regression coverage; a live Jim conversation was not opened or sent test messages during verification.

The final build uses a fresh Gradle daemon and two workers after a superseded lint pass spent over 16 minutes in API/UAST analysis. An attempted Kotlin frontend override was ignored by this AGP setup (the stack still showed FIR); the final full lint check therefore uses its normal analysis engine. No lint checks were disabled. The one suppression documents the guarded pre-API-34 Quick Settings overload: newer Android versions use PendingIntent, while Android 12/13 require the older overload.

Build flags retain MWDAT and the verified native runtime:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  --no-daemon --max-workers=2 \
  -PincludeMwdat=true \
  -PmwdatProperties=/Users/prog/AndroidStudioProjects/Meowmba/mwdat.properties \
  -PprebuiltDebugNativeDir=/Users/prog/Meowmba-Releases/flow156-20260923/verified-jni \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDaily :app:assembleDaily
```

Device instrumentation runs against the matching debug build: the debug test runner cannot use the optimized daily APK's renamed Kotlin runtime symbols. The lifecycle test starts with a plain intent because MainActivity consumes a Share intent's action, which otherwise causes ActivityScenario to ignore its lifecycle callbacks. Both harness issues were corrected before the four passing tests. Signature, 16 KB alignment, full runtime parity and real UI checks cover the daily artifact itself.

User instructions: [Meow 172 user guide](MEOW_172_USER_GUIDE.md).
