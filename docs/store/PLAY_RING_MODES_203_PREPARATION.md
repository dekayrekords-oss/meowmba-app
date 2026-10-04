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
Native UI screenshots were inspected. Final lint completed on this frozen source: zero errors, 234 warnings.

The 203 phone APK is signed with the installed key and passes 16 KB ZIP alignment.
The actual installed 202 baseline was pulled and its bytes match the validated
202 APK. All 160 native/model/other runtime entries are unchanged. The only asset
change is the reviewed manual, byte-identical to tracked source. At the owner’s explicit
request, 203 installed in place over 202 and launched successfully; the original
September 23 installation date and app data were retained. Lint finished after
installation. The phone then disconnected, so a longer post-install stability check
and physical ring acceptance are unavailable. All 16 actual public-release
configuration tests pass, bringing the verified total to 52. The full-SDK minified
Play bundle is assembled, signed with the matching upload certificate and validated
by bundletool. All 88 native libraries pass 16 KB alignment/JNI checks. Compared
with signed public 202, all 160 runtime entries are unchanged and only the reviewed
manual differs. Signing preserved all 758 unsigned payload entries. The permitted
unsigned inspection stage was followed by verified upload-key signing; the final
upload is signed. Google accepted 203 / 2.0 and published it to the existing internal testers,
release “2.0 (203) – Ring modes and scroll fix”, updated October 3 at 18:00.
The track explicitly says Available to internal testers. No 203 production
submission. The source commit 3a0f0d6 is pushed to the verified private
GitHub repository. 203 listing changes are saved in Play as an unsent draft; the
public privacy HTML is published and byte-for-byte verified. The declaration
is still a draft. 201 is still in
production review; 152 superseded / Not published. No live public production
download is verified.
Physical ring acceptance, real reviewer recording, declaration and Google approval
remain required. Listing/declaration submission is pending; privacy is published.
Manual change is intentional and must be checked against the signed phone baseline;
native/model payloads and signing identity must remain unchanged.

Internal release: https://play.google.com/console/u/0/developers/5214154317103934390/app/4972949930318434862/tracks/4701352764658866051/releases/4/details
