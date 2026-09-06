# On-phone voices and broader app support — 1.3.24 / code 28

## Status

Implemented and built; not yet installed. The one-time 349,418,188-byte model
download and connected-device tests are awaiting user approval. No server-side
voice inference or paid API is used by this implementation. The existing chat
model/provider is unchanged; this does not claim the entire AI stack is offline.

The final build and all 39 targeted JVM/UI tests pass. Connected native voice
generation, playback latency, listening quality and app-launch tests are
prepared but have not run for this version. Human-indistinguishable speech and
ElevenLabs-equivalent quality are not verified or guaranteed.

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

## Next verification

Install without clearing user data, download/check the pack, synthesize and
save all four samples on the Fold, measure cold/warm first-audio latency and
real-time factor, listen to the results, and test actual AudioTrack playback
and stop behavior. Check Chrome, Calculator and My Files through the real
chat submission method, without inspecting account contents. Reconnect
Accessibility manually if Android still marks the service disconnected.
