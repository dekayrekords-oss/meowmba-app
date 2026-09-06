# Whisper and app-opening repair — 1.3.23 / code 27

Installed over the development app on the connected Fold, preserving its
database, saved connectors, workspace and existing models. Nothing submitted
to Google Play in this repair.

## Changes

- Connected the Voice Recognition page's Whisper download and streaming-repair
  callbacks. These callbacks are now required constructor parameters, so a
  future screen caller cannot silently omit them.
- Placed repair/progress controls next to Whisper. Prevented duplicate download
  requests and removed the false IN USE badge from unavailable backends.
- Whisper repair checks both the streaming pack and Whisper files; refreshes
  availability and attaches the native recognizer after successful download.
- Added the missing Accessibility screen-content capability. Android may
  require the user to reconnect the service after the update/test restart.
- Routed common inbox requests, including the observed ASR spelling “emile,”
  to the actual Gmail launcher. The result explicitly says messages were not
  read; this is not a Gmail API integration or an automatic inbox summary.
- Routed the older immediate-launch shortcut through AgentOrchestrator so
  ownership, policy and task auditing apply. Deterministic commands take
  precedence over the multistep model loop.

## Connected-device evidence

- Whisper files downloaded via the real ViewModel repair method: encoder
  29,120,534 bytes; decoder 130,669,978 bytes; tokens 835,554 bytes.
- Whisper selection succeeded on the real ViewModel. The native recognizer
  initialized and decoded a one-second silent PCM test buffer successfully.
  This validates loading/decoding, not real microphone transcription accuracy.
- Submitting “open gmail” through the real chat submission method brought
  Gmail to the foreground. The final connected launch test passed.
- Room audit entries 59–61 record launch start, submit and completion, with
  the truthful result that no messages were read. No email was sent.
- The first launch test used the target app's Accessibility singleton as its
  observer and failed after instrumentation disconnected that service. Android
  activity logs nevertheless confirmed Gmail opened. The final test observes
  only the system foreground-activity record, not email contents.
- The installed APK declares both gesture and screen-reading capabilities.
  Accessibility still needs the user's off/on reconnect; full screen navigation
  is not certified while Android lists that service as disconnected/crashed.

## Test scope

The final targeted routing, voice-availability, UI, download and model-pack
run passed all 52 tests, with zero failures, errors or skips.
Broader testing found old ViewModel tests that advanced virtual time forever
through recurring microphone jobs. Download tests now cancel their ViewModels
and isolate explicit downloads from first-run Needle setup. The unrelated
VoicePipelineTest still stalls and was stopped; the entire test suite is not
claimed green.

Artifact: `meowmba-1.3.23-code27-whisper-control-debug.apk`.
