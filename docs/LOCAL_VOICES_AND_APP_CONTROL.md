# On-phone voices and broader app support — 1.3.25 / code 29

## Status

Installed on the Fold with an in-place update, preserving app data and Whisper.
The 349,418,188-byte pack was downloaded on Meow, checked against the pinned
SHA-256, validated for safe archive entries, and transferred by USB. Direct
GitHub connections failed on the phone and Mac; the in-app download path is
therefore not verified end-to-end. No server-side voice inference or paid API
is used by this implementation. The existing chat
model/provider is unchanged; this does not claim the entire AI stack is offline.

The initial feature build passed 39 targeted JVM/UI tests; the callback repair
passed all 6 voice regression tests. On the Fold, native generation of four
distinct non-silent 24 kHz samples, AudioTrack playback/completion and stop,
and actual chat-command launches of Chrome, Calculator and My Files passed.
Human-indistinguishable speech and ElevenLabs-equivalent quality are not
verified or guaranteed. Listening quality still needs user evaluation.

The first hardware test found a JNI callback crash caused by Kotlin's generated
lambda lacking invoke(float[]): Integer. Version 1.3.25 uses a kept concrete
adapter and defers callback exceptions until outside JNI. Version 1.3.24 should
not be used for neural playback.

## Measured generation on the Fold

| Voice | First audio | Total synthesis | Generated duration |
|---|---:|---:|---:|
| Heart (cold model) | 2261 ms | 4321 ms | 3341 ms |
| Bella (warm model) | 1331 ms | 4596 ms | 3233 ms |
| Fenrir (warm model) | 1463 ms | 4350 ms | 3205 ms |
| Michael (warm model) | 1694 ms | 5275 ms | 3832 ms |

These are one-run observations, not latency guarantees. Synthesis was slower
than real time in this sample; sentence gaps and further CPU optimization need
evaluation before claiming premium conversational responsiveness. AudioTrack
completed its playback test and stop returned in 7 ms (software measurement,
not an acoustic latency measurement). Samples are in the release voices folder.

## Four voice choices

| Voice | Presentation | Official Kokoro v1.0 speaker |
|---|---|---|
| Heart | Female, American English | af_heart / 3 |
| Bella | Female, American English | af_bella / 2 |
| Fenrir | Male, American English | am_fenrir / 14 |
| Michael | Male, American English | am_michael / 16 |

These are four distinct pretrained voice identities, not four pitch shifts and
not newly cloned humans. The speaker map comes from the
[official sherpa-onnx Kokoro documentation](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html).
See also the [original voice catalog](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md).

## Implementation

- Voice & Conversation page: separate speaking/listening sections, one-time
  download and enable action, progress, four selectors, preview buttons,
  offline Android-voice fallback choice and visible playback errors.
- Official sherpa-onnx v1.13.4 Kotlin TTS bindings match the native library
  already present in the app. Uses Kokoro multi-lang v1.0, with a pinned
  GitHub release checksum and bounded, path-checked archive extraction.
- One shared native model per process; sentence-chunk PCM playback through
  AudioTrack, a serial synthesis worker, cancellation tokens and immediate
  playback interruption. Voice selection persists on the phone. Neural
  voices keep a stable identity instead of receiving mood-driven pitch shifts.
- Android fallback selects an installed, non-network English voice only.
  Synthesis requests are not uploaded. Internet is used for initial model
  download, not for generating these voices.
- Fixed stale Android TTS completion callbacks and the first-sentence speaking
  transition when multiple sentences are already queued.

## App-control scope

- The phone loop now uses the same installed-launcher catalog/matcher as
  single-action launches, instead of a hard-coded map of popular apps.
- Preserves the previous audited launch route and policy checks.
- Screen-based actions fail explicitly if the current screen cannot be read.
  Multiple action failure strings now return ToolResult.Failure instead of
  successful results. Fixed accessing children after recycling a node.
- This is not a promise of universal app automation. Apps must expose launchers
  and usable accessible controls; protected screens, login requirements,
  permissions and Android restrictions remain. Some older gesture/loop paths
  still need stronger completion/approval integration from the agent audit.

## Remaining verification

Evaluate the four samples by ear and profile longer conversational replies.
Retest the in-app download when GitHub connectivity works. Android still marks
MeowAccessibilityService crashed/disconnected: the user must toggle Meow
Assistant off/on under Accessibility / Installed apps before screen-based
control can be verified. No accessibility permission was force-granted.

A separate pre-existing crash log (before this installation) shows Ktor Android
stream cancellation failing with AsyncTimeout "Unbalanced enter/exit". This
was not repaired in the local-voice change and remains a reliability follow-up.
