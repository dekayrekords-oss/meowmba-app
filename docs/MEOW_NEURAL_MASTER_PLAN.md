# Meow neural layer: engineering master plan

Planning date: September 10, 2026. Status: proposed architecture, not an implementation report. Current implementation claims below come from the supplied engineering update; this planning pass did not independently audit the repository or phone.

## 1. Objective and honest baseline

Build a persistent, adaptive sensorimotor neural controller that gives Meow responsive attention, coherent expressive behavior, short-term prediction, and bounded learning across interactions. Preserve the language model for language, reasoning, planning, and tool use. Evaluate actual improvements over the current controller rather than treating neuron count as success.

The reported NervousSystem implementation integrates hand-authored drive values, applies relaxation, and biases authored motion. That is a useful baseline and fallback. The supplied update does not establish simulated neurons, synaptic dynamics, or learned connections. Previous animated diagrams were illustrations, not live measurements; the earlier promotional draft's claim that inputs flow through simulated synapses was unsupported. Face events and interaction state are reportedly connected; touch, loud sound, general motion, and reply-affect integration remain gaps.

For this project, a true neural layer means explicit neural state, weighted recurrent connections, temporal integration, a tested learning procedure, and measured causal influence on behavior. Spiking neurons are one implementation choice, not a prerequisite for intelligence. No milestone claims consciousness, subjective emotions, or guaranteed robot safety.

## 2. Target architecture

Sensor adapters → timestamped feature events → neural perception/attention/memory → behavior proposals → deterministic arbiter → avatar rig or robot adapter.

Language model → validated intent envelope → neural goals and context. Neural summaries → language model when meaningful changes occur. Actual action outcomes return through feedback events.

Keep five separate responsibilities:

1. Perception estimates observable facts and confidence.
2. Neural dynamics maintain attention, short-term context, expectations, and action tendencies.
3. Persistent memory stores explicit user facts and approved preferences with provenance.
4. The planner decides tasks and requests capabilities.
5. The arbiter enforces user interruption, permissions, actuator limits, and action ownership.

The neural controller never gets API credentials, unconstrained tool execution, or direct motor authority. Increased curiosity must not grant new permissions or initiate financial, messaging, or publishing actions.

## 3. Phase zero: establish a stable platform

First reproduce the reported sustained CPU load and heat on the actual installed version. Record device, build, charging status, ambient conditions, camera state, microphone state, local model state, and foreground/background status. Use thread sampling/profiling available on the debuggable build; root is not inherently required for all profiling options.

Attribute runtime cost by feature and instrument worker ownership, loop frequency, queue depth, inference duration, dropped frames, and service restarts. Compare features individually and in combinations. Preserve legitimate user-requested monitoring while suspending unrelated work.

Fix duplicate workers, unbounded queues, busy loops, leaked collectors, and repeated retries before adding a new always-running subsystem. Measure CPU as both process usage and equivalent core time, with the sampling method recorded.

Deliverables: reproducible benchmark script, feature-cost report, stable fallback build, baseline recordings for replay. Gate: known sustained CPU consumers explained, lifecycle cleanup verified, and repeatable foreground/background thermal results.

## 4. Phase one: sensory contracts and replay

Define SensoryEvent(timestampMonotonic, source, kind, values, confidence, freshnessLimit), IntentEnvelope(goal, target, priority, expiry, cancellationId), BehaviorProposal(action, target, intensity, confidence, expiry), and ActionFeedback(result, interruption, error).

Use bounded queues and latest-value coalescing for high-rate gaze/motion events. Preserve ordered events where transitions matter. Reject stale inputs; propagate sensor loss explicitly. A face detection is evidence of presence, not proof of identity, mood, or intent.

Integrate face presence/direction, supported visual motion, genuine petting gestures, voice activity, user interruption, and task outcomes. Sound direction requires suitable hardware and validation; do not invent it from a single microphone. Prevent Meow's own speech from being treated as user praise or new commands.

Store reproducible feature traces with consent and retention controls; raw audio/video is unnecessary for most controller tests. Cover arrivals, departures, rapid detection flicker, silence, interruptions, noisy rooms, sensor failure, and background transitions.

Gate: recorded inputs replay deterministically through the baseline; absent sensors cannot leave stale attention or stuck behavior.

## 5. Phase two: build and compare actual neural candidates

Build two desktop research candidates against the same data and output contract:

- A compact recurrent neural network, initially 64–256 hidden units, as an engineering comparator.
- A sparse spiking network, initially 256–1,024 leaky integrate-and-fire neurons, with explicit membrane potential, spikes, refractory periods, excitatory/inhibitory connections, and bounded synaptic weights.

Treat sizes as starting experiment ranges. Expand only when held-out performance improves enough to justify measured runtime cost. Do not deploy a whole fly connectome to the phone by default.

Candidate neural populations represent sensory evidence, novelty, attention competition, short-term traces, approach/avoidance, arousal, and behavior selection. Use adaptation and inhibition to prevent runaway activity. Log spike rates, silent/saturated units, reset events, weight bounds, and numerical failures.

Train first on desired reactions and successful authored behavior traces. Then train prediction of near-future feature states, and action selection in simulation. Include a baseline with equivalent inputs and no neural state to test whether recurrence actually helps.

Norse provides PyTorch-compatible spiking components for research. Select the phone runtime only after verifying model/operator support, numerical parity, and benchmarks. A small custom native sparse integrator is an option, not a presumed optimization. Spiking networks on ordinary phone processors are not automatically faster or more energy efficient.

Gate: neural state causally changes behavior; ablations quantify its contribution; selected candidate beats baseline on predefined measures while meeting resource limits.

## 6. Phase three: embodiment and expressive continuity

Decode network outputs into bounded gaze targets, movement vigor, attention hold duration, ear pose bias, blink modulation, and weighted animation-family proposals. Keep interpolation, authored poses, and final geometry in the existing rig.

Add action ownership, minimum dwell times, interruption rules, and smooth transitions. Prioritize explicit user commands over spontaneous behavior. A startle can interrupt idle motion; it cannot interrupt an emergency stop or repeatedly steal attention from conversation.

Keep stable personality preferences distinct from transient affect. Affect variables describe behavioral controls, not subjective feelings. Couple expression to context: recognition increases orientation; failed tracking triggers brief search; continued absence returns to rest. Preserve subtle motion floors where appropriate for the avatar, but never apply a mandatory-motion floor to physical motors.

Fishing, reading, hats, and new props require a separate skill/asset pipeline: intent → validated scene description → available or generated assets → compatible animation primitives → preview and execution. A neural reflex layer does not automatically create meshes or understand every activity.

Gate: blinded paired comparisons prefer neural behavior for responsiveness and coherence; repeated scene playback shows no twitch loops, clipping regressions, or missed interruption.

## 7. Phase four: memory and bounded adaptation

Separate neural working memory (seconds) from event memory (episodes) and explicit user memory (persistent facts). Store remembered places as named coordinates with accuracy, timestamp, and user confirmation; neural weights are not a dependable address book.

Begin personalization with a small, bounded preference head: gaze duration, idle-family preferences, interaction timing, and responses to explicit feedback. Train offline first. Introduce online updates only with rate limits, rollback checkpoints, and drift detection.

For the spiking candidate, test local timing-based plasticity and reward-modulated updates as experiments. Compare them against simpler preference learning; retain them only if outcomes improve. Never equate long engagement with user benefit. Use task success, explicit feedback, interruption respect, and reduced nuisance as objectives.

Support inspect, forget, reset, and learning-off controls. Prevent downloaded content and messages from issuing training instructions or modifying permissions. Sensitive facts should not be baked into irreversible personalization weights.

Gate: adaptation persists across sessions, generalizes to held-out examples, respects deletion, and does not degrade core skills or increase unwanted interruptions.

## 8. Phase five: connectome research track

Run a separate desktop experiment importing a versioned, licensed FlyWire subset with provenance. Select a circuit for a specific testable behavior such as visual approach detection or attention competition. Document how fly sensory inputs and descending outputs are mapped to Meow features and actions.

Connectivity is a structural constraint, not a complete model of physiology or learning. Specify assumed neuron dynamics, synapse signs, delays, and missing parameters. Compare the circuit to random sparse and trained recurrent controls of similar cost.

Advance a connectome-derived circuit into the product only if it improves a chosen metric. A full-brain simulation may be a worthwhile research artifact, but its completion is not a product release dependency. NeuroMechFly offers relevant embodied simulation research; adapting it to Meow still requires engineering and new validation.

## 9. Phase six: phone deployment and thermal limits

Keep sensory inference cadence, neural integration, and rendering independent. A 5 Hz drive update cannot simply be reused as a biologically faithful spiking integration step. Choose a timestep through convergence testing; benchmark all internal substeps. Bound backlog and discard stale work after suspension instead of replaying minutes of accumulated input.

Publish one immutable controller snapshot for the renderer. Avoid per-neuron Compose state, frame-rate allocations, continuous diagnostic logging, and unnecessary LLM calls. Wake on relevant events and reduce work when no input changes, while preserving explicitly enabled background services.

Initial engineering targets, to revise after baseline measurement:

| Measure | Proposed acceptance target |
|---|---|
| Feature event to behavior proposal | p95 ≤20 ms on target phone |
| Captured visual event to visible response | p95 ≤150 ms; report perception and display contributions |
| Incremental neural CPU | ≤3% of one core during defined active benchmark |
| Incremental controller memory | ≤20 MB, excluding existing perception models |
| Battery cost | ≤1 percentage point/hour above matched baseline; verify repeatability |
| Thermal behavior | No sustained worsening versus matched baseline; degrade gracefully |
| Stability | 8-hour soak without deadlock, queue growth, or stuck ownership |

These are proposed targets, not observed capabilities. Test while charging and unplugged, with and without camera, voice, local inference, and accessibility work. Thermal controls reduce nonessential compute first. Hardware-independent emergency controls remain available.

## 10. Phase seven: robot transfer

Start with a simulated body and feedback delays, missing sensors, noisy estimates, obstacles, and actuator saturation. Then use a restrained bench setup before supervised low-speed physical tests.

The learned controller proposes motion; an independent deterministic controller checks limits and commands the actuators. Require device-specific speed/torque/workspace limits, command expiry, feedback verification, watchdogs, and a physical emergency stop. Communication loss must produce a defined safe state. The LLM, cloud connectivity, and neural learning are outside the emergency-stop path.

Hardware acceptance requires a robot-specific hazard analysis and measured stop behavior. Avatar smoothness or apparent intelligence cannot certify physical safety.

## 11. Evaluation and release sequence

Maintain three replayable modes: existing controller, neural shadow mode, and neural control. Shadow mode computes proposals without moving the avatar and allows comparison before deployment. Record model version, sensor feature provenance, selected behavior, and interruption reason at a bounded rate.

Benchmark face arrival/loss, occlusion, competing attention targets, user interruption, repetitive stimuli, low energy, camera denial, microphone contention, rotation, process recreation, thermal throttling, and long idle periods. Test quiet and noisy speech sessions separately; this project must not conceal the existing speech-recognition and microphone-state bugs.

Evaluate correct reaction rate, false startles, response latency, repetition, transition smoothness, task interference, energy, and explicit user preference. Freeze acceptance criteria before tuning; retain unseen scenarios. Fail back to the existing controller on invalid output, crash, model incompatibility, or sustained deadline misses.

Roll out through replay → simulation → internal phone shadow mode → opt-in avatar control → wider release → robot simulation → physical validation. Each stage has independent rollback. Commit complete compiling dependency sets after reconciling overlapping edits; do not discard unrelated work.

## 12. Work packages and execution order

Suggested modules: SensoryEventBus, NeuralCore, NeuralCheckpoint, NeuralIntentBridge, BehaviorDecoder, BehaviorArbiter, AdaptivePreferenceModel, FeatureReplayHarness, NeuralDiagnostics, and RobotCommandGate. Exact files and runtime language depend on repository audit.

First implementation package: audit current integration; reproduce CPU/heat; establish feature events and replay; add shadow-mode seam and fallback. Second: train/test both neural candidates on identical traces. Third: deploy the measured winner for avatar behavior. Fourth: bounded learning and persistent memory integration. Fifth: connectome experiments and robot transfer.

A small experienced team should plan in months, with research uncertainty explicitly tracked. Do not promise a completion date for learned behavior, full connectome transfer, or robot safety before feasibility results. Maximum effort means rigorous experiments and complete integration, not making the phone run the largest possible network.

## Primary research references

- FlyWire whole-brain annotations and neuron count: https://www.nature.com/articles/s41586-024-07686-5
- Connectome-derived sensorimotor modeling: https://www.nature.com/articles/s41586-024-07763-9
- Connectome network statistics: https://www.nature.com/articles/s41586-024-07968-y
- Embodied sensorimotor simulation, NeuroMechFly v2: https://www.nature.com/articles/s41592-024-02497-y
- Norse spiking-network research framework: https://github.com/norse/norse

These sources motivate experiments; they do not demonstrate that Meow already has these capabilities or that a fly circuit will transfer successfully to phone assistance.

