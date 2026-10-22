# Tester recovery — 1.3.41 / versionCode 45

## Evidence and diagnosis

- The supplied tester screenshot reports an on-device MediaPipe/TFLite engine
  initialization failure, not just a network error. The tester's exact model and
  device have not been inspected. Do not call its precise cause confirmed.
- Source inspection found that startup and deleted-model fallback could select
  `needle2.cact` as the conversational model. Needle is a separate tool router;
  passing its weights to MediaPipe is invalid. Chat selection now filters by
  compatible runtime/extension, preserves a valid explicit choice, and otherwise
  prefers an installed catalog chat model. This is not validation of arbitrary
  model contents: the real inference readiness test remains required.
- Connected device: SM_F966U1, debug package `com.example.meowmba`, installed
  1.3.40/code44. Play package `com.meow.meowmba` is absent. No data reset or app
  installation was performed for this increment.
- Device audit recorded `phone.run_goal` failure for the LinkedIn request while
  Meow Accessibility was enabled but disconnected. Later read-only inspection
  showed it bound again, with no crashed services. No post was published.
- Two foreground orb captures differed by 102,072 pixels above an 8-level
  channel threshold in the avatar crop. The orb was moving during this check;
  the earlier freeze was not reproduced. Thermal status was MODERATE (2), battery
  38.6 C. These are observations, not a controlled thermal benchmark.
- Recent video-generation audit also contained an actual unavailable-provider
  failure. Display fixes cannot manufacture an output when generation fails.

## Implemented

1. Prevent Needle/image weights from becoming chat fallbacks. MediaPipe rejects
   unsupported file types before native engine initialization. Both local route
   builders recognize `.litertlm` case-insensitively.
2. Replace the raw native stack trace in the main UI with a concise recovery
   message and **Repair local AI setup** action. No automatic cloud consent or
   paid fallback is introduced.
3. Preserve a visible **Saved result** card after external viewer handoff,
   above chat/settings and below approvals. Images are downsampled off the main
   thread; text/HTML/email previews are bounded. Open result, Files, and Dismiss
   remain available even if no viewer opens. HTML preview is inert text, not
   executable web content. Media still opens in a compatible native viewer;
   HTML attempts Chrome first.
4. File-write and email-draft tools now return explicit artifact markers. Parse
   existing, nonempty, absolute marker paths and deduplicate them. Only a native
   writer's final marker is trusted; read/search/MCP text cannot impersonate one. Validate the
   FileProvider shared root before displaying or handing off a file; private
   model/key/vault roots remain inaccessible. Office MIME types are recognized,
   but this does not implement Office export. Plain-text writes masquerading as
   PDF/Office/media binaries now fail clearly and recommend readable text/HTML.
5. Add direct social-draft routing, inventory-bound `linked it` alias, bounded
   prior conversation/draft reference for the phone planner, and an explicit
   reconnect/retry flow. Requested drafting may use supplied facts; invented
   links/testimonials and unverified completion remain prohibited. Final posting
   still needs separate confirmation; login/permission/payment/deletion handoffs
   remain manual.
6. Explain severe-thermal animation pauses and keep automatic lifecycle/thermal
   resumption. The avatar picker now obeys the same thermal gate as the main
   avatar. No reduction in particles/frame rate or removal of heat protection.

## Verification

- Full JVM/Robolectric/Compose suite: 1,261 tests, zero failures/errors, five
  conditional skips. Includes new startup recovery, native preflight rejection,
  artifact marker/MIME/sandbox, document preview/action, context, and social
  routing tests. Three old download tests now explicitly choose chat-capable
  catalog models instead of mistakenly expecting Needle to be a chat model.
- Debug APK assembled with the configured Meta DAT SDK and existing debug
  signing. Debug-only native shortcut reused the unchanged Needle/SD bridge
  binaries from the previous build in an isolated `/tmp` directory; this is not
  a clean native rebuild or a signed Play release.
- No live image-generation provider request, new-build phone inference, or
  LinkedIn draft insertion has been accepted yet. Unit tests do not establish
  those capabilities on the tester's phone.

Archived preview: `/Users/prog/Meowmba-Releases/meowmba-1.3.41-code45-preview.apk`.
SHA-256: `80405f2bc6455b78cf365236f2a6bf5e038f337c9fe4b3b4398fb51983ebbb17`.
`apksigner verify` passed; debug certificate SHA-256:
`75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.

## Remaining acceptance / next actions

1. Obtain separate permission to install 1.3.41 on the connected Fold, retaining
   app data. Verify the package version after installation and recheck that
   Android has kept Accessibility connected.
2. Run a real local reply; test invalid-model recovery and a valid model
   readiness path without deleting the user's models or exposing API keys.
3. Ask the normal app to draft harmless text in LinkedIn, inspect the visible
   editable field, then cancel. Never publish as part of this regression test.
4. Generate one image using the configured provider and one text/HTML document;
   verify native/Chrome handoff, Back navigation to the saved card, Files, and a
   no-viewer case. A failed generation must remain a failure, not a fake preview.
5. Verify orb motion after chat/settings, screen off/on, and a safe cool-down.
   Do not intentionally overheat the phone. Get the affected tester's app version,
   device model, and selected/downloaded chat model to reproduce their screenshot.
6. Only after device acceptance, sync/build/sign on Meow and upload a new Play
   testing release if requested. Nothing from this increment is published yet.
