# Emotional acting — 1.3.58 (62)

## What changed

The Lumen avatar previously reduced most feelings to a few eyelid and smile changes. Some authored lean, lower-lid, ear-asymmetry and jaw channels never reached the renderer. Weather/mood refreshes and speech start could replace a conversational expression, and a residual mouth-energy value could leave the character moving its mouth after speaking.

The new local acting director coordinates 16 distinct poses: idle, happy, curious, alert, sleepy, confused, proud, attentive, understanding, concerned, apologetic, surprised, playful, affectionate, determined and relieved. Independent upper/lower lids, brow shape, pupil size, ear asymmetry, head roll, posture and restrained squash contribute to the expression. Eyes notice first; head and ears follow. Authored anticipation, accent, hold and settling replace an undifferentiated expression change. Three bounded accents vary common reactions, selected at state entry without an immediate repeat. A sustained listen has spaced small acknowledgements, not a continuous nod cycle.

Listening and touch produce local reactions without waiting for a model request. Existing selected mood and narrow explicit conversational cues now survive the reply lifecycle. An apology can remain apologetic when speech starts; uncertainty does not automatically become a happy face. These are authored communication cues, not inference about a person's unspoken feelings. Avatar → Lumen → Emotion rehearsal exposes every pose without changing the saved mood.

Original native artwork was refined: curved Australian shepherd ruff and blaze, softer merle patches and folded ears, layered owl facial feathers, a rounded duck crown and a more expressive bill. Pearly eye material, irregular iris fibers and restrained highlights replace harder rings and striping. Shared squash, stage bounds, props and hit testing stay aligned. The selected animal, all 52 performances and 13 idle scenes—including butterfly, mouse pursuit, reading and three hats—remain available.

## Timing and conversation handoff

The renderer now advances custom clips, composed scores and ambient acting from one display-vsync loop. The old extra frequency threshold could skip a display frame; it has been removed. Each explicit playback has a sequence identity, so replaying the same immutable clip starts anew. Hidden/background performances are cancelled and unsolicited performances remain ineligible while the visual stage is covered. Idle rotation cannot override an explicit emotion rehearsal.

Microphone attention or a usable live speech draft releases the current scene. Merely becoming ready for a follow-up does not prematurely erase the animation the user just requested. Speech end, interruption and listening handoffs clear mouth energy; word-driven energy decays fully to zero. The braking reaction retains priority over relaxed eyelids. Face tracking, privacy indicators, reduced-motion policy, drive travel and prop bounds continue through the existing rig.

This is an animation and interaction-lifecycle improvement. It does not remove network/model latency or establish recognition accuracy. The 1.3.56 speech/project/drive repairs and 1.3.57 animal/scene additions remain included. Live transcription under noise, accents, interruptions and long conversation still requires device evaluation; no 1:1 equivalence to ChatGPT or Grok voice is claimed.

## Physical motion boundary

`EmotionMap` now samples the same authored composed-expression engine used by the face, instead of approximating the score with a different sinusoid. It emits bounded intent at the existing 250 ms cadence and ends with braking. Invalid/nonfinite parameters brake; visual zoom and squash are not interpreted as chassis motion. Existing body adapters, profile constraints and lease arbitration remain responsible for any later dispatch.

Physical control remains quarantined. No scan, connection, actuator command or robot experiment was performed. Shared intent is not proof of physical synchronization: transport latency, joint feedback, braking behavior, collision sensing and a separately supervised safety controller need hardware-specific validation. Expressive animation is not awareness, human emotion or evidence that autonomous physical action is safe. This build does not claim those capabilities.

## Reference and visual direction

The earlier DeskMate contact sheets and prior Meowmba animation notes were reviewed again. [KEYi's DeskMate page](https://keyirobot.com/en-us/products/deskmate) informed coordinated eyes/head/body timing and attentive pauses; its response-time claims are manufacturer claims, not measurements of this app. [Loona's technology page](https://loona-official.com/pages/tech_1) describes the separate pet robot's coordinated face/ear/body expression. No vendor character assets or film-studio assets were imported.

The result remains original, procedural floating-avatar art. It is a more expressive prototype, not a completed film-production character or a claim of Pixar/Disney/DreamWorks quality. Further silhouette, anatomy, individual acting and phone performance review remain necessary.

## Verification and artifacts

The final `:app:testDebugUnitTest :app:assembleDebug` run passed in 1m 3s: **1,820 tests, 1,815 passed, five skipped, zero failures/errors**. The skipped group is the existing `MetaGlassesUnavailableTest` group. `git diff --check` passed. APK code 62/version 1.3.58 and the existing signing certificate were verified. Exact APK identity, native speech assets and renderer measurements are recorded in the release's `verification.json`, `full-build-final.log` and archived JUnit XML. Regression coverage includes emotion continuity, 30/60/120 Hz convergence, repeated-state stability, local listening onset, interruption, hidden-stage eligibility, same-clip replay, native emotion sheets, species rendering, body timeline bounds and existing voice/drive/privacy/control-quarantine tests.

`acting-preview.gif` is a 14-second scripted render of the production rig and painter, exported at 24 frames per second. It is not a recording of a phone or a live model conversation. `render-cost.json` measures the native Robolectric painter on the development Mac at 960×580: 313 samples after 24 warmup frames, median 3.96 ms, p95 4.35 ms, max 7.01 ms. It excludes image allocation, encoding, model work and display scheduling. Neither artifact measures on-device frame rate, end-to-end response time or battery use; there is no comparable before-change timing trace.

Artifacts are under `/Users/prog/Meowmba-Releases/emotional-acting-2026-09-09`: candidate APK, native previews, before/after source archives, incremental patch, logs, XML and verification JSON. Preexisting working changes and prior releases are preserved. The LAN Git server could not be reached, so remote currency is unverified. The connected Fold was read-only checked at 1.3.55/code59. This candidate has not been installed; no app data was cleared, and no push or publication occurred. Installation and device acceptance remain separate gates in AGENTS.md and the active plan.
