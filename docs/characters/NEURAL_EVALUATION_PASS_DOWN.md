# Meow pass-down: neural evaluation and bounded preferences

Date: 2026-09-12. Starting source: 8d1f290. Integrator owns final wiring, build, phone gates and git commit.

## What changed and why

- Added `core/neural/AdaptivePreferenceModel.kt`: four allowlisted activity scores (`feed`, `play`, `rest`, `explore`), explicit positive/negative feedback, ±0.05 per accepted update, bounds ±1, one accepted update per 60 seconds across all activities, maximum 1000 updates until reset. Off suppresses influence and learning. Reset erases scores and counters. Versioned compact snapshots reject malformed/nonfinite/out-of-bounds values. This is a preference head, not synaptic plasticity or a claim of feelings.
- Added three JVM preference tests for update caps, clock reversal, off/reset, restart persistence and corrupt snapshots. Integrator stores snapshots and must pass epoch milliseconds so throttling survives process restart. The model performs no I/O and interprets no message text.
- Added `NeuralComparisonReportTest.kt`: identical 400 × 50 ms scripted feature replay through actual Kotlin rate/spiking cores, detector flicker, motion, voice, threat, touch, praise and settling. Checks exact repeated replay after reset and finite outputs within checkpoint bounds. Two warmups and five timing runs per substrate. Writes `app/build/reports/neural/comparison.json`. Timings are JVM throughput for a whole replay including output storage, not a per-tick p95 or phone thermal gate.
- Fixed `scripts/neural/train_neural_core.py`. It previously fitted raw input features, wrote input-width coefficients into output-by-hidden-unit readout weights, and silently discarded the intercept. It now reconstructs rate hidden state using the production equations, fits output-by-units weights, preserves intercept as outputBaseline, and rejects unsafe fits. Original checkpoint is not mutated. CLI requires matching layout names/order and explicit `--from-reset`. Positive finite ridge, finite targets/features, ordered timestamps and at-most-one-second gaps are required.
- Added `scripts/neural/evaluate_trainer.py` and a saved synthetic held-out result. This is a known two-unit teacher to test fitter correctness, not a trained real-fly behavior model. No user traces used.
- Researched primary FlyWire/NeuroMechFly/FlyBody sources in `docs/neural/FRUIT_FLY_CIRCUIT_RESEARCH_2026-09-12.md`. No biological dataset or weights included in the commercial app.

## Existing capability audit

| Roadmap area | Existing source evidence | Remaining boundary |
| --- | --- | --- |
| Sensory events | Typed bounded/coalescing bus, TTL, feature encoder and tests | Integrator found ViewModel still used legacy NervousSystem and is wiring actual live adapters |
| Recurrent network | Leaky tanh recurrent core, adaptation, checkpoint bounds | Hand-authored weights; no demonstrated learned quality advantage |
| Spiking alternative | Seeded LIF network, signed sparse projections, rate readout | Synthetic wiring, untrained; no measured fly connectivity |
| Replay | Deterministic core tests, trace writer | New comparison provides repeatable shared replay; field traces still need explicit reset/state provenance |
| Arbitration | Ownership, intent expiry, bounded proposals, deadline/nonfinite/thermal fallback | App must connect action completion and sense events without bypassing privacy/permissions |
| Learning | No adaptive model in original core directory | New bounded explicit preference head needs saved-state/UI integration; no recurrent plasticity |
| Recording | Opt-in design, size/count capped feature traces | Mid-session/rotated files lack initial hidden state; cannot honestly reconstruct them from reset |
| Phone heat | Controller has telemetry and thermal hooks | Actual profile/deployment evidence belongs to integrator, not this test |

## Actual checks

`python3 -m unittest discover -s scripts/neural -v`: **5 passed**, 0 failures (local Mac).

`python3 scripts/neural/evaluate_trainer.py --output docs/neural/TRAINER_EVALUATION_2026-09-12.json`: 400 training and 400 held-out samples, different pulse period. Recovered readout `[0.30000000408880123, 0.19999995424001438]`, intercept `0.20000001229752656`. Held-out MSE **4.2600429813441213e-17**, training-mean constant baseline MSE **0.018955914750724126**. This near-zero error is expected for a known teacher in the fitter's model family and must not be used as app intelligence or behavior-quality evidence.

Integrator executed the comparison successfully: rate64 median 2.9164175 microseconds/tick, spiking256 median 18.8162425 microseconds/tick; 400 ticks, two warmups, five runs each. Both deterministic/finite, zero NaN resets. Between-substrate readout MAE 0.09743245. This is JVM synthetic replay throughput, not phone p95 or learned behavior quality.

## Teach Meow: diagnosing future problems

If exported weights fail Kotlin validation, check the shape first: `outputWeights.size == outputLayout.size * rate.units`; input feature count is not hidden-state count. Check the affine intercept and declared output bounds separately. Fit against explicit `targetReadout` annotations to learn a desired response; default `neuralReadout` only distills the current model and proves no improvement.

Replay must start from the same core/checkpoint/reset and use identical elapsed intervals. Python replay uses doubles; Android uses floats, so cross-language checks need tolerances. A rotated recording's first time is usually not a core reset; refuse training rather than silently creating an incorrect hidden state. Export initial hidden/adaptation state or restart an opt-in capture from reset in future.

The learned preference head should only select or bias cosmetic activities. Never connect its scores to tool permission, privacy indicators, microphone activation or robot actuation. Persist accepted updates and off/reset operations. A wall-clock jump backward suppresses learning until the saved time is reached; it never creates extra updates.

Do not promote spiking mode just because it sounds biological. Compare same inputs, hold-out tasks, action accuracy and measured device cost. Neither the replay nor fitter measures subjective happiness or engagement benefit. The commercial fly rig remains authored and biologically inspired; genuine connectome research is separate and requires data provenance and a validated motor/physics mapping.

## Follow-up: real input sound energy

Added `core/audio/InputSoundLevel.kt` and four `InputSoundLevelTest` cases. `SpeechService` now publishes live owning recognizer RMS callbacks; `SherpaAsrEngine` publishes energy from successfully captured PCM frames and clears on shutdown. No new recorder, timer, thread or audio storage. Whisper is deliberately untouched: its input is a completed recording, and publishing that during offline transcription would falsely represent past sound as current activity.

The snapshot carries `activity`, `loudness`, `fresh`, and a source. Activity is a sound-energy proxy, not speech classification. PCM energy is normalized dBFS; Android RMS uses a separately documented provider-relative scale because there is no portable acoustic calibration. Consumers use `InputSoundLevel.monotonicNowMs()`, pass own-TTS suppression and use zeros for stale input. Never feed microphone-armed state as speech evidence. The age gate is strictly less than 800 ms; publishing is capped at 10 Hz, RMS work inspects at most 4096 PCM values, nonfinite callbacks are rejected, and source-specific close cannot clear the other capture path.

Own-TTS suppression erases the last energy sample so it cannot reappear immediately after speech ends. This is not acoustic echo cancellation; recent room/reverberation noise can still occur. The existing mic ownership and privacy gates remain authoritative. Test execution remains with the integrator's single build owner.
