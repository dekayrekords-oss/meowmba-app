# Meow teach-back: full fly habitat preview 97

User direction: preserve floating Lumen fruit-fly features and the separate commercial concept. Add a new whole-fly companion, optional care, ten interactive animated environments, shared neural controls and measured phone stabilization. The reference stock illustrations were not copied into APK assets.

## Implementation

`AvatarStyle.FULL_FLY` (`full_fly`, “Fly companion”) is a separate selection. FullFlyPainter consumes the existing Lumen rig frame for gaze, blink, mouth and expression. Six articulated limbs, two translucent wings, short bristled antennae and a small mouth create the fly identity. PetHabitatWorld supplies bounded screen travel and approach/grasp/hold/release poses on the existing render clock. Book and glass contacts use the same hand anchor as the painter; floor/table collision and a bouncing ball are deterministic. Motion is authored screen animation, not aerodynamic simulation.

Ten appended background IDs preserve saved old selections and share SceneClock: see ENVIRONMENTS_PASS_DOWN.md. Habitat props overlay these scenes for all styles. Lumen forms and the full fly move with the habitat actor; older SIGNAL/NEO/CLASSIC styles can display/interact with props but do not yet have full-body grasp retargeting. Arbitrary artwork is not a rigid-body physics world; the book, glass and ball are the interactable props.

Play opens feed/play/rest/read/drink/nudge/release/explore controls. Optional care is persisted, defaults off, decays gently with a bounded offline interval and never kills the companion. Explicit feedback learns four capped activity preferences, persisted with disable/reset and a global update cooldown. The play preference biases exploration; this is bounded preference learning, not emotions or online recurrent synaptic training.

The live ViewModel now uses NeuralBehaviorController shared by Lumen and the fly: legacy, shadow and neural modes, rate or spiking substrate, thermal/deadline fallback. Shadow is the safe default and leaves legacy in control. Existing stimuli plus fresh face, sound energy and motion enter its bounded sensory bus. Incoming events are re-stamped in the controller clock domain. Sound uses existing capture callbacks, never microphone-enabled state or own TTS as evidence of user voice. NeuralSettingsSection exposes modes, live diagnostics and opt-in feature traces. Recording starts from reset and defaults off each process; rotated files still lack complete hidden-state provenance and must not be assumed independently replayable.

## Heat and reliability repairs

FeatureFlags.from previously constructed encrypted storage repeatedly from rendering reads. Cache one application-context-backed facade; preference values remain live. Existing ViewModel reads reuse its storage. The drive loop remains 5 Hz foreground, backs off to 1 Hz without stepping in background, and clamps resumed elapsed time. Use System.nanoTime for this monotonic interval: Android SystemClock breaks plain JVM ViewModel tests. Diagnostics refresh at 1 Hz. No extra painter timers, audio recorder or biological model assets were added.

Trainer repair: fit recurrent hidden-state readouts, not input-width coefficients; retain the intercept. Validate layout, dimensions, finite values, time ordering and reset provenance. See NEURAL_EVALUATION_PASS_DOWN.md. A near-perfect synthetic teacher fit proves algebra, not intelligence.

## Verification and evidence

Initial focused Android run: 52 tests, zero failures/errors/skips; debug assembly succeeded. Python trainer: five passed. Native fly pose sheet and all ten background contact renders were visually inspected. Tests cover animation finite geometry, deterministic world behavior, hand/prop contacts, care persistence/time reversal, bounded preferences, sensory/core replay, sound TTL, thermal policy and cadence.

Actual equal-input JVM comparison: 400 ticks at 50 ms, two warmups/five measured runs. Rate64 median 2.9164 microseconds/tick; spiking256 median 18.8162 microseconds/tick. Both deterministic and finite with zero NaN resets; readout MAE between substrates 0.09743. This is synthetic JVM throughput, not phone p95, behavioral quality or biological evidence.

Initial phone: SM-F966U1, debug package com.example.meowmba code94, separate release package com.meow.meowmba code85. Charging, LIGHT thermal state. Baseline background Meow CPU approximately 0.43% of one core over 58 seconds, battery 34.6→34.5 C. An attempted foreground sample was approximately 45.65%, 35.2→35.5 C, but the user switched apps: it is not a controlled A/B baseline. The profiler now records per-sample foreground and PID to catch this. Raw local evidence lives in ignored build-out/habitat; do not publish unrelated phone screenshots or full activity dumps.

Expanded-suite and installation results are appended below when complete. An earlier legacy AvatarMorphTest fixture failed on the unchanged base with a null application context. Reusing real storage advances that incomplete mock to AgenticRuntime's null context; do not call that a passed integration test.

## Scientific and release boundaries

No measured FlyWire synapses or derivative weights are shipped. The rate64/spiking256 cores use authored/seeded parameters. FlyWire provenance and candidate embodied experiments are documented separately in docs/neural/FRUIT_FLY_CIRCUIT_RESEARCH_2026-09-12.md. A brain connectome alone cannot provide human arms or calibrated flight dynamics. Circuit extraction, biological benchmark and real motor retargeting remain research work.

Short phone tests cannot close an eight-hour soak, unplugged battery/hour comparison, controlled microphone/camera load matrix or learned behavior quality gate. Keep this a debug preview and retain rollback until those gates pass. Robot transfer is a later simulation experiment: no physical actuation or robot safety changes were made.

## Future debugging lessons for Meow

- Compare source version, APK certificate and all native/asset entry hashes before installation; ignored voice runtimes must be reused explicitly in isolated worktrees.
- A stationary prop while a hand moves means world and painter anchors disagree. Table feet use the rendered lower-limb extent, not an arbitrary root Y. Pause must freeze the existing clock; never start a separate timer to fix drift.
- Use fresh monotonic sensory timestamps and suppress stale/own-speech input. “Listening enabled” is not sound. Energy is not semantic praise or calibrated speech detection.
- Compare rate versus spiking on identical inputs before promoting one. Use device p95 and CPU/temperature, not JVM timings as phone claims.
- Background process CPU is not foreground animation CPU. Check resumed activity on every sample and reject mixed-PID traces. Charging and other foreground apps confound heat comparisons.
- Replay needs reset/checkpoint/hidden-state provenance. Preserve readout dimensions and affine intercept when training. Never label random edges as biological synapses.

Build owner: isolated /home/claude/meowmba-fly-habitat. JDK21.0.12+8, existing Android SDK and -PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview. Source changes and this teach-back belong in Git; runtime assets/APKs remain ignored. Do not edit the server's live deploy-only checkout to distribute this note.

## Final source validation

Final focused run after the clock/diagnostics correction: **66 passed**, zero failures/errors/skips; assembleDebug succeeded. Full expanded run: 2,215 tests, 65 failures. Unchanged prior fruit-fly source: 2,188 tests, 64 failures. All 64 baseline failures recur; the extra AvatarFormTool callback test reports UncaughtExceptionsBeforeTest and passes in the clean 66-test run. This is not a fully green whole-repository suite. Existing failing clock/mocked-context/UI fixtures remain open.

Final APK SHA-256: `96047d47bf014a3b6aff44d251071a2fcdbfadd2041e182420b2485ef6dba8c1`.
Verified certificate SHA-256: `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`, matching the previous installed preview. Debug package/version code: com.example.meowmba / 97. Release package remains separate.
