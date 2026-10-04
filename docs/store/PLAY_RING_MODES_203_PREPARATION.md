# Ring modes and scroll delivery — 203 / 2.0

Owner authorized both app/action shortcuts and configured typed Meow requests.
Based on validated/internal-published 202. Branch `feature/ring-modes-203-20261003`.
GitHub integration fetched, no missing commits; LAN remains unreachable.

## Behavior

- Media, Scrolling and navigation, Photo taking and Custom modes. Custom mappings,
  app targets and requests persist across preset switches. Changes pause controls.
- Requests travel through an in-process, expiring single-use mailbox to the normal
  `onUserTextSubmitted` pipeline; exported Activity intents cannot submit ring text.
  Provider selection, task permissions, approvals and public automation boundaries remain.
- Revised disclosure requires fresh consent for expanded 203 purposes. Consent,
  unlocked phone, connected verified input, verified gestures, allowlist and sleep
  gates apply. Screen contents are not recorded. Raw keys and screen contents are not sent to AI; connection/gesture diagnostics require explicit user-directed sharing.
  Owner-configured Ask Meow text may go to the selected provider like typed chat.
- Scroll keys no longer cancel the overlay-removal delay. Up to four swipes can
  queue for at most two seconds; delivery rechecks foreground app and controls.
  Pausing, configuration changes and cancelled delivery clear pending actions.
- Scroll actions prefer the pane under the target, then focused or main scrollable
  controls; rejected actions fall back to a swipe within that pane's bounds.
- Camera uses the platform still-image-camera intent and an accessible photo-only
  shutter label. Generic Record/Capture/Shutter IDs are rejected to avoid video or
  unrelated controls. Compatibility/localized labels and real hardware need testing.
- Media keys use Android's down/up media dispatch; volume changes music volume.
  Pause stops Meow remapping, not native hardware media keys passed to Android.

## Validation and release

Implementation and phone APK compiled with the full Meta SDK. Final focused
checks: **36 passed, zero failures/errors/skips**. Tests cover physical R08 key
delivery during the overlay delay, queued-swipe cancellation, scroll-pane selection,
photo-only controls and rejected-shutter isolation, mode/request persistence,
revised consent, expiring single-use requests and small-phone mode/editor UI.
Native UI screenshots were inspected. Lint is still running on this frozen source.
The earlier renamed-label/capture-infrastructure issues are resolved without
weakening behavior assertions; screenshots use the existing native View draw route.

The 203 phone APK is signed with the installed key and passes 16 KB ZIP alignment.
The actual installed 202 baseline was pulled and its bytes match the validated
202 APK. All 160 native/model/other runtime entries are unchanged. The only asset
change is the reviewed manual, byte-identical to tracked source. No 203 phone
installation or Play bundle/upload yet. 203 listing changes are saved in Play as
an unsent draft; privacy and declaration drafts are prepared. 201 is still in
production review; 152 superseded / Not published. No live public production
download is verified.
Physical ring acceptance, real reviewer recording, declaration and Google approval
remain required. 203 listing/privacy/declaration drafts are prepared, not published.
Manual change is intentional and must be checked against the signed phone baseline;
native/model payloads and signing identity must remain unchanged.
