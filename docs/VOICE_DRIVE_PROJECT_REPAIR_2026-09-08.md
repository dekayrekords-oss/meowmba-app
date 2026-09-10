# Voice, drive and project repair — 1.3.56 (60)

Source and APK candidate completed September 8, 2026. Phone acceptance is pending. This extends the existing dirty `feat/image-services-settings` checkout and preserves earlier appearance work.

## Voice

Cloud wrappers were treated as short-session recognizers although the recorder remains continuous. Quiet endpoints and stop/restart handoffs could cancel pending results, while concurrent requests could deliver into later sessions. SpeechEngine now identifies continuous capture, pending finalization and explicit submit. RefiningSpeechEngine uses a bounded ordered queue per mic session, invalidates cancelled callbacks, and pairs finals with their original audio.

Mic submit requests a final instead of discarding the recording. Finalization has a 20-second deadline, including when no partial arrived. Pause timers cannot execute a draft while submit is pending. Cloud requests have 15-second timeouts and visible local fallback. Empty/noise finals clear waiting UI without resurrecting a bad draft. AudioRecord failures now report errors rather than silently exiting. Final transcript validation preserves numeric replies, short non-Latin answers and natural repetitions.

Foreground interruption uses the selected engine. Continuous playback-to-follow-up handoff swaps callbacks without cancelling pending audio. Genuine interruption finals are processed directly. Short replies no longer fail merely because they occur within the companion's previous answer.

Added optional **OpenAI transcription**, using `gpt-transcribe` and `POST /v1/audio/transcriptions` with original 16 kHz mono WAV audio. Selection requires an OpenAI key in existing encrypted storage and the local recording model. OpenRouter keys do not qualify. Settings disclose audio upload and API charges; default local Whisper is retained. Gemini shares the repaired queue/cancellation behavior.

This is ASR → model → TTS, not native full-duplex audio or a ChatGPT/Grok replica. No paid API request or real microphone recording was made. Real accent/noise/route accuracy is unmeasured. Automatic endpoints still depend on local Sherpa recognition: automatic audio with no local hypothesis is not forwarded, while manual submit can forward it. Background WakeWordService still uses its platform capture path.

Official references checked: [speech-to-text](https://developers.openai.com/api/docs/guides/speech-to-text), [gpt-transcribe](https://developers.openai.com/api/docs/models/gpt-transcribe), [Realtime transcription](https://developers.openai.com/api/docs/guides/realtime-transcription).

## Avatars and projects

Drive weight previously reduced autonomous stage travel to zero. Natural travel now continues at reduced amplitude alongside vehicle-response springs. Shared idle eligibility permits resting muted/offline states and gates scenes during speech, work, panels and driving.

Butterfly Chase, Detective, Yarn Play, Ball Play, Acorn Play, Mischievous Raccoon, Sleepy Nest and Rainy Window already existed. The old idle pool excluded all eight because they were not marked ambient. A shuffled bag now interleaves scenes with quiet acting, avoids immediate repeats and observes scene-duration cooldowns. A persisted Idle scenes toggle and reduced-motion preference control this behavior.

Project continuation previously routed only one action. ProjectStepRunner now runs up to six tool actions using saved constraints and observed results. Every action retains normal orchestrator permissions and approvals. Failure, cancellation, invalid scope, repetition, empty output or the action budget stop the run; model claims never auto-complete checklist items. Relative file reads now prefer the captured project workspace and listings are project scoped. Creation phrases route to persistent project creation. The UI distinguishes executing from planning, supports cancellation and presents actual artifacts for review. Unique run epochs prevent an old cancelled run from clearing a new run in the same project. Completion/cancellation restores listening mode.

## Road reports

The poller survives temporary foreground/drive pauses and evaluates GPS independently of slow provider requests. It requires fresh, precise locations, filters stale reports, uses heading only with usable bearing data and picks the nearest eligible report. Camera text takes precedence over generic police words; police-station and removed-report text are rejected. No stale location fallback is used.

Waze/OSM availability is distinguished from empty results. Alerts use nearby/source/age wording rather than invented ahead or speed claims. These are unofficial community reports and mapped cameras, not radar hardware or guaranteed route matching. No live drive or provider-coverage accuracy trial was performed.

## Evidence

- Full `:app:testDebugUnitTest :app:assembleDebug` passed: 1,769 tests, 0 failures/errors, 5 skipped (existing MetaGlassesUnavailableTest group); 1,764 passed.
- Two further OpenAiAsrRequestTest checks passed with Ktor MockEngine: multipart WAV/model/auth/endpoint contract and no request without a key with visible fallback. No production code changed after the full suite.
- Regressions cover quiet endpoints and playback handoff with pending transcripts, cancellation, timeouts, empty finals, long turns, manual submit deadlines, project read→write using actual results, approvals/cancellation, same-project restarts, idle selection, drive movement and road poller/freshness policy.
- Native production render tests passed for scenes/drive poses, aspect ratios and privacy marks; the scene contact sheet was visually inspected.
- `git diff --check` passed. Incremental patch is relative to the before-task snapshot, preserving the preexisting dirty tree.
- Package `com.example.meowmba`, candidate 1.3.56/code 60; connected Fold remains on 1.3.55/code 59.
- APK v2 signature verifies; certificate SHA-256 `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.
- APK SHA-256 `56252107860ef7428e498b5deb29b66b51d19b1556e3b251b49b895f8229aa58`; 421,902,227 bytes.
- ZIP inspection verified arm64 Sherpa/ONNX native libraries, complete keyword and streaming-ASR models/tokens, Silero VAD and speaker model.
- LAN Git fetch failed (`192.168.1.69: Host is down`); remote source currency is unverified.

## Delivery and device gate

Artifacts: `/Users/prog/Meowmba-Releases/voice-drive-repair-2026-09-08/`: APK, before/after source snapshots, incremental patch, verification JSON, test XML/logs and scene previews. No installation, data clearing, microphone activation, robot movement, push or publication was performed.

AGENTS.md and the active plan require separate install authorization. After installation, test submit/silence recovery, follow-ups/interruption, background transitions and Bluetooth routing with consenting speakers. Compare transcripts against a fixed real-recording set before making customer accuracy claims. Validate projects with a real input/artifact and road reports along a known route. These device checks remain open.
