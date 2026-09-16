# Meow Assistant beta readiness — 1.3.27 / code 31

## Changes in this candidate

- Guided setup now has progress, Back, Explore first, safe-area and keyboard padding.
- Cloud providers are collapsed initially; local and cloud behavior is described separately.
- API keys and optional connector tokens are masked. Existing saved keys are not cleared by skipping setup or leaving fields blank.
- Optional integrations are collapsed. Home Assistant requires both URL and token before saving.
- Android runtime permission requests no longer appear automatically on launch. The final setup page explains them and exposes an explicit Review Android permissions button. Requests use one launcher, including notifications, so two permission dialogs are not launched concurrently.
- Selecting the phone speech engine no longer also invokes an unrelated voice-pack download hook. Optional neural voices and Whisper disclose their approximate download sizes.
- Settings has a compact Beta help & feedback panel with first-use tasks, troubleshooting, a user-reviewed mail draft, and shareable enrollment instructions.
- Feedback includes version, Android version and device model only. No logs, keys, history or attachments are added, and nothing is sent automatically.
- Existing Files and local-voice fixes remain in this candidate. No Blender avatar was incorporated.

## Play status verified in the Console on September 6, 2026

Closed Alpha is active in 172 countries/regions. Published release: **1.3.22 — Voice runtime and Meta glasses fix**. Current list `leigh1` has 3 addresses. Dashboard shows **2 opted-in testers**. Apply for production is disabled. No unpublished changes were present at inspection.

The pre-launch overview displayed “Upload artifacts to generate pre-launch reports”; no report could be reviewed. This must not be reported as zero crashes or zero accessibility findings.

## Verification completed

- `assembleDebug` succeeded with MWDAT enabled.
- 80 targeted tests passed: zero failures, errors or skips. Coverage includes onboarding, masked keys, editable feedback draft, Files, local voices, app/email routing, workspace, orchestrator, agent routing and media routing. This is not a full-suite or physical-device pass.
- Rendered and visually inspected the actual Compose onboarding (light) and expanded beta-help card (dark) at 412 × 915 dp.
- No phone install or Play upload was performed. The signed debug APK is a development candidate, not a Play upload bundle.

## Release gates still open

1. Obtain explicit permission for an in-place Fold update and run device QA. Do not uninstall or clear app data. Test first-run permissions with a separate test installation/account or an emulator rather than resetting the user's existing setup.
2. Check first-run skip, full setup, saved keys after restart, optional-permission denial, file import/search/reviewed cleanup/restore, media opening, app launches, neural voice selection and interruption, Whisper selection, and poor-network download recovery.
3. Confirm/reproduce and repair the previously observed Android/Ktor stream-cancellation crash (`AsyncTimeout: Unbalanced enter/exit`). This onboarding change does not claim to fix it.
4. Reconnect the Fold's Accessibility service through Android Settings if it is still disconnected. Do not silently grant it or claim universal cross-app control.
5. Build and inspect a signed ARM64 Play bundle with MWDAT and the native voice runtime. Validate signing/package/version, upload to the existing Alpha track after device QA, and review any generated pre-launch findings and Android vitals.
6. Recruit at least 10 additional real opt-ins; a target of 25–30 volunteers gives useful device coverage and room for dropouts. Preserve current testers. Do not buy fake engagement, invent feedback, or require favorable ratings.
7. Keep at least 12 continuously opted in for 14 days, collect genuine feedback, document fixes, then apply for production when Console enables it. Approval is Google's decision.

No new testers, invitations, Google Group, track changes, production application, or phone installation are implied by these files. Use TESTER_START_HERE.md for the actual verified enrollment link and feedback address.
