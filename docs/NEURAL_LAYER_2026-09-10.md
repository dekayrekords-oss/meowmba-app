# Meowmba neural layer

The first neural increment is implemented under `app/src/main/java/com/example/meowmba/core/neural`.

## What exists

- A typed sensory contract and bounded `SensoryEventBus`. Continuous channels coalesce; transition events retain order; stale channels emit explicit `SENSOR_LOST`.
- A frame-rate-independent `FeatureEncoder` with decaying impulses and a versioned, validated `NeuralCheckpoint`.
- Two deterministic substrates behind `NeuralCore`: a 64-unit leaky recurrent rate network and a seeded LIF spiking candidate.
- A `BehaviorDecoder` and deterministic `BehaviorArbiter`. The network proposes; ownership, dwell, startle rate limiting and action expiry decide.
- `NeuralBehaviorController` with `LEGACY`, `SHADOW` and `NEURAL` modes, deadline/non-finite/exception fallback and thermal tier suspension hooks.
- Pure sense adapters for face presence/looming/hand motion, voice activity, device motion and interaction transitions.
- Feature-only JSONL recording and a standard-library Python readout trainer. Audio, video and transcript data are never recorded.
- `NeuralSettingsSection` is ready to mount during the integration step.

## Safety and rollout

The default is `SHADOW`: the existing hand-authored `NervousSystem` remains the published avatar bias while neural divergence is measured. `NEURAL` mode is opt-in. The fallback is sticky after invalid output, an exception or repeated deadline misses. Survival thermal tier suspends neural stepping and discards stale queued evidence before resuming.

The neural layer has no tools, credentials, privacy action or robot action. A face is presence evidence, not identity. A single microphone gives loudness/onset, not direction. Drive and valence values are behavioural controls, not claims about subjective experience.

## Checkpoints and training

`NeuralCheckpoint.default()` is hand-initialised from the legacy drive semantics. `NeuralModeStore` can load a validated custom `filesDir/neural/core.json`; a rejected file falls back to the built-in checkpoint.

To fit a readout from an opt-in feature trace:

```text
python3 scripts/neural/train_neural_core.py \
  --checkpoint scripts/neural/default_core.json \
  --trace trace.jsonl \
  --output core.json
```

The trainer uses only the Python standard library. It fits a bounded readout, not the recurrent dynamics; BPTT and behavioural learning remain future work. `python3 -m unittest discover scripts/neural` runs the trainer tests.

## Current boundary

The neural controller is intentionally not mounted into `CompanionViewModel`, `SherpaAsrEngine` or `SettingsScreen` yet. That integration is a separate gated step because another agent is actively modifying those files. The existing app therefore keeps its legacy controller until the shared working tree is stable and integration is explicitly approved.