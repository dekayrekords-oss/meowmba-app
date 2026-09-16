# Meowmba 1.3.73 — measured reliability repairs

Installed over the existing app on Samsung SM-F966U1 by USB on September 9,
2026 at 17:54:15 (versionCode 77). App data and all 125 previously bundled
native/asset entries were preserved. Debug certificate verified. APK SHA-256:
`3a63d9924c9ac4c16038610b389fe5a8e4b0aac587ed8c37cdc57df0d4633584`.

## Voice: repaired lifecycle, cloud accuracy still blocked

Phone logs showed repeated microphone starts around wake/always-on transitions.
Pending starts now share one job and are revoked on mode/permission changes.
Continuous capture uses a 150 ms rearm interval instead of the old two seconds;
existing session-generation fences, manual cancellation and finalization deadlines
remain in place. Regression tests exercise repeated foreground requests and mute
while the wake engine is still releasing its recorder.

The user explicitly selected configured cloud transcription. This phone has a
configured OpenRouter provider and no configured direct OpenAI transcription key;
it now selects Gemini cloud. Actual speech reaches that HTTP adapter, but the
provider returns **HTTP 402**. Adding a 1,024-token output limit did not resolve it.
This is a payment/credit-limit rejection, not a successful cloud transcript.
The exact account condition cannot be determined from the status alone.
No credits were purchased and no secrets were exported.

Cloud failures are now visible in the conversation, not just the model-settings
page. They retain their actionable HTTP cause across microphone rearming. On
failure the original PCM goes to available on-device Whisper before falling back
to the streaming draft. The cloud timeout remains 15 seconds and local fallback
has a separate four-second budget; stale sessions cannot deliver into a new turn.
Successful empty cloud transcripts do not resurrect local guesses.

A Mac-speaker fixture saying “Report a bug” reached the phone. OpenRouter rejected
it, and Whisper ran, but the words were misrecognized. This does **not** establish
improved acoustic accuracy, particularly in noise, with glasses, or at a distance.
The Mac's original muted/zero-volume setting was restored after each audible test.
A funded/enabled provider account or another valid configured provider is required
before cloud accuracy can be validated. The app remains selected for cloud with
visible local fallback. No claim of parity with ChatGPT/Grok voice is made.

## Bug intake and locations

“Report a bug” is now a bounded local intake, with an optional next description,
cancellation and a way out when the next turn is an unrelated question. Explicit
reports and recognized failure statements save only after a real local write.
The receipt includes a local ID; reports remain possible defects, not automated
repair instructions. On the phone, a report was saved in
`Files → diagnostics/possible-bugs.json` and verified in the Room audit log.

“Where are we?” now requests a fresh location and returns a real address or
coordinates with an accuracy estimate. Approximate fixes may describe the area;
saving “here” requires a more precise fix. Reverse geocoding has a three-second
callback deadline on supported Android versions. GPS coordinates are no longer
written to routine CurrentLocation logs.

“Remember here” captures a fresh fix, asks for a name, and remembers that original
point for the short follow-up. “OK, let's go to NAME” resolves the saved place
before generic routing. NAV, navigation tools and voice share one encrypted store,
with migration from both historical formats. A deleted entry cannot be revived
from the old voice copy. Legacy records are retained inertly to avoid migration
loss; they are not queried after the new store exists.

Phone checks: current-location answer, naming “Meow acceptance spot”, and opening
Google Maps for that saved point. Two completed navigation tasks are recorded,
including after the final upgrade/migration. The temporary place was removed
through NAV. A final current-location query still succeeded. Precise coordinates
are intentionally omitted from this report.

## Heat and rendering

The old phone logs showed roughly 30 face-detector calls/second without a face.
The old skip counter admitted every normal-mode frame. Face detection now uses a
wall-clock budget and FAST mode: up to eight updates/second while tracking and
about 2.5 without a recent face when cool, reducing further with thermal severity.
Pose detection is limited to once/second without a tracked face and is disabled
while warm or in follow-only mode. Decorative redraws are bounded to roughly
30 FPS while the motion clocks still use real elapsed time.

At Android MODERATE thermal status or above, decorative animation pauses and
CameraX is unbound. Merely skipping inference left the camera/ISP streaming, so
it was insufficient. The existing attention watchdog resumes automatic camera
binding after cooling, without overwriting the user's camera preference.
Explicit camera captures fail clearly while this thermal pause applies.

Observed old visible-app CPU samples after the first sample: 178, 172, 174, 176,
175 percent of one CPU core. Final samples during a voice/cloud/Whisper test
ranged from 29.6 to 116 percent; idle intervals included 29.6–37 percent. Android
camera diagnostics showed no active app camera, and the final process had zero
face-detector calls while thermally paused. The phone remained USB-powered and
warm (battery approximately 38.3°C; thermal status changed from SEVERE to MODERATE).
A subsequent 33-second sample had a median of 38.0% CPU after its first sample, with the camera and decorative animation thermally paused.
These short samples include different workloads and are not a controlled battery
benchmark. Sustained overheating, cool-state responsiveness and automatic camera
resume still need longer real-world validation.

## Route-free traffic: actual source and remaining gaps

The Arizona envelope now uses the official AZDOT WZDx endpoint,
https://az511.com/api/wzdx, cached for five minutes. It rejects invalid/stale feed
responses, future and expired work zones, and invalid geometry. Distance is to
the nearest point on each work-zone segment, not just its endpoints. Source and
coverage remain explicit. The captured public feed contained 3,280 work-zone
features, including future events that are not announced until active.

On the phone, AZ511 and OSM returned valid responses with no reports near the
current location. This was verified in the visible settings status. Road alerts
are enabled. Arizona work zones do not supply mobile police reports or general
congestion. Outside that area the existing unofficial community feed remains
best-effort and can fail. Mapped cameras are incomplete and are not radar sensing.
Google's Navigation SDK supports police and other disruption callouts along routes
and alerts during navigation; its documented feature does not provide the requested
route-free police feed. See the official documentation below. No police-detection
or comprehensive hazard-coverage claim is made.

## Avatar nervous-system proposal

A small autonomous attention/reflex layer is technically appropriate. Existing
`LumenRig`, `LumenGazeMotion`, `LumenBodyMotion`, the acting director and freshness
guards already keep visual movement separate from language-model calls. The next
prototype should feed those components from existing camera/audio/touch observations,
with bounded curiosity, social attention, fatigue and caution states, action
competition, hysteresis and habituation. It should use the current render clock,
start no extra sensor/model loops, yield to user speech/performance, and stop work
under the same thermal/privacy policies.

A FlyWire connectome was **not** installed. DesktopFly is an experimental example,
not proof of human-level awareness or robot safety; its code is MIT, while its
shipped FlyWire-derived data is CC BY-NC 4.0. Do not copy that data into a commercial
product without compatible rights. An original lightweight controller avoids that
dependency. Expressive behavior is not evidence of consciousness. Physical robot
actuation remains quarantined; no motor experiment was performed.

## Verification and evidence

- Full Android suite: **2,065 tests; zero failures/errors; five existing skips**.
- Signed APK assembled after the final successful suite; 125 runtime entries match
  the prior 1.3.71 APK byte for byte.
- Final phone process: zero `FATAL EXCEPTION` log entries during acceptance checks.
- Local source was preserved; no reset, commit, push, account purchase or data clear.
  `git fetch --all` succeeded; only `origin` is configured here and it adds no
  commits beyond this checkout. The historical `lan` remote is not configured.
- Private evidence folder:
  `/Users/prog/Meowmba-Releases/bug-audit-2026-09-09/`.
  It contains build/test logs, APK/signature/hash, CPU/camera/thermal samples,
  phone logs, local bug receipt, UI evidence and Room audit snapshots.

Sources checked September 9, 2026:
- [OpenAI transcription model](https://developers.openai.com/api/docs/models/gpt-transcribe)
- [OpenAI speech-to-text guide](https://developers.openai.com/api/docs/guides/speech-to-text)
- [OpenRouter credits and billing](https://openrouter.ai/docs/faq)
- [AZ511 API documentation](https://az511.gov/developers/doc)
- [Google Navigation SDK disruptions](https://developers.google.com/maps/documentation/navigation/android-sdk/real-time-disruptions)
- [DesktopFly code and data licensing](https://github.com/kulikov0/desktop-vibe-fly)
- [FlyWire simulation experiment](https://github.com/pusulamkendim/flywire-neuro)
