# Immersive companion — detailed revision plan

September 7 thermal checkpoint: section 8's waste-removal pass is implemented
in 1.3.37/code41 without reducing model/animation quality settings. Background
clocks, analytic shader caching, speech buffers and inference/capture lifetime
fixes are recorded in `THERMAL_OPTIMIZATION_2026-09-07.md`. Normal-use thermal
acceptance still requires an unlocked, matched physical-device profile; this
checkpoint does not mark the wider animation/content roadmap complete.

Status: planned; this document does not mark the requested changes implemented.
This revision supersedes the earlier orb-ribbon direction and fixed-height chat behavior in APPEARANCE_UPGRADE_PLAN.md. Keep the Professional theme, Light/Dark/System choices, color customization, privacy guarantees, and existing settings organization.

## 1. Requested result

- Jarvis becomes parameters floating in space. Glow diffuses into the actual background, without a visible disk, frame, ring system, or hard-edged stage.
- Chat opens from a compact button, expands only as needed, and retracts into that button without losing the conversation or draft.
- The cat becomes an expressive performer with purposeful eye acting, varied motion, small scenes, and props—not a handful of repeated loops.
- Performance is measured and improved across the app, not guessed from appearance alone.
- Prefer OpenCode Big Pickle agents launched from this computer; use Meow's available local models for bounded supporting work.
- Installation remains a separate approval gate.

## 2. Evidence and boundaries

Inspection confirmed `ChatOverlay.kt` uses `.fillMaxHeight(0.6f)`. `ParticleOrbAvatar.kt` draws several filled circles, a bright nucleus, pulse rings, and the recently added ribbons/filaments. Those treatments explain the oversized chat and visible orb structure.

The existing motion stack already supports expressions, gaze, speech energy, custom keyframes, spring motion, and interaction state. Extend it; do not build a competing second animation clock or bypass the existing command/approval system.

OpenCode exists at `/Users/prog/.opencode/bin/opencode`; its catalog lists `opencode/big-pickle`. This confirms discovery only. Test one small request before assigning work. A locally installed CLI does not prove the model runs on this computer's GPU. Meow's previously observed GPUs are an RTX 3090 and an RTX 3090 Ti; recheck free capacity before use.

Older progress notes and test reports are historical checkpoints. Reverify current source, APK, device, and test results rather than treating a plan's checkmarks as live evidence.

## 3. Implementation order

1. Record baseline, establish file ownership, and smoke-test Big Pickle access.
2. Implement diffuse Jarvis and retractable chat independently.
3. Build the eye-acting rig and animation director before adding a large clip library.
4. Add a bounded scene/prop system and an authored pilot library.
5. Profile, tune quality tiers, and audit app-wide lifecycle/resource usage.
6. Run regression and visual review; prepare the upgrade only after acceptance.

## 4. Jarvis: floating parameters, no visible shell

### Rendering changes

1. Remove filled base disks, hard pulse-ring silhouettes, rigid ribbons, radial spokes, and the opaque-looking central ball from the default look.
2. Render particles with depth-dependent size, brightness, parallax, and soft occlusion. Preserve depth without outlining a sphere.
3. Replace the large circle fills with a low-opacity radial falloff whose outer alpha reaches zero smoothly. Apply it directly over the current theme/background, not a rectangular black panel.
4. Use limited soft halos around brighter particles; cache reusable glow resources. Do not run a full-screen blur separately for every particle.
5. Let listening gently orient the cloud, thinking reorganize clusters, and speech modulate particle energy. Keep transitions continuous; no distracting strobe or constant explosion.
6. Use a separate accessible status label when needed. Do not present the rendered dot count as the model's actual parameter count.
7. Keep user-selected tint, reduced motion, pause, captions, and privacy information.

### Acceptance

- On dark, light, colored, and wallpaper backgrounds there is no visible disk edge or rectangular stage.
- Particles remain readable without a washed-out center, and the glow disappears into the background.
- Idle, listening, thinking, speaking, and offline states are distinguishable without rings.
- Compare before/after recordings and frame timings; do not increase particle count merely to create impact.

## 5. Chat: a retractable composer

### State model

`Collapsed button → Compact composer → Expanded history` with explicit transitions back.

- Collapsed: only the launcher button remains; optionally show an unread-reply indicator.
- Compact: one-to-four-line input, microphone, send/stop, and an explicit collapse control. No large empty history panel.
- Expanded: user-requested scrollable history, with a compact composer anchored above the keyboard. History may use more screen space only after that deliberate action.

### Interaction details

1. Replace the fixed 60% height with content-driven height and a viewport-aware maximum.
2. Morph position/size/opacity toward the launcher anchor on collapse; respect reduced motion with a simple fade or immediate transition.
3. Preserve draft, messages, stream state, and scroll anchor independently of whether the panel is composed.
4. Collapse hides input focus and keyboard, not the conversation. Android Back dismisses the keyboard first, then the panel, before leaving the screen.
5. Never auto-collapse while typing, selecting text, scrolling, recording, or resolving an approval. After sending, use an explicit user choice or safe idle policy rather than a surprise timeout.
6. Keep streaming replies accessible via captions and the launcher indicator; provide a real stop-generation action only where cancellation exists.
7. Preserve voice-call behavior explicitly: collapsing the UI must not secretly start, stop, or mute a call. Show an unmistakable ongoing-call indicator.
8. Reuse familiar assistant conventions—message history, composer, microphone, send/stop—without copying another product's branding or requiring its APIs.

### Acceptance

- The closed state occupies only the button's footprint.
- The compact state has no fixed half-screen reservation.
- Fold/unfold, landscape, keyboard, large text, TalkBack, rotation, and streaming do not lose state or cover essential controls.
- Approval cards remain above the chat and always reachable.

## 6. Cat: eye acting and a real animation director

### A. Rig and quality decision

First deliver a short native-rendered acting test: listen → consider → understand → respond. Review eye expression, timing, shading, and silhouette against the concept.

Feature-film quality is an aspiration, not an automatic result of procedural effects. If the native Canvas approach cannot achieve the approved look, evaluate an authored rigged 2D/2.5D or 3D character with blend shapes, eyelids, materials, and animation clips. Compare APK size, authoring cost, runtime performance, licensing, and color control before choosing. Do not add a heavy rendering engine without this decision gate.

### B. Eye rig

- Independent upper/lower eyelids, soft lid tension, eye aim, convergence, iris/pupil controls, catchlights, and subtle asymmetry.
- Gaze leads a head turn; ears and whiskers follow with smaller delayed movement.
- Keep the iris stable under a closing eyelid; do not squash the entire eye for a blink.
- Use pupil change and lid shape together. Avoid indiscriminate pupil pulsing or a fixed wide-eyed stare.
- Distinguish attentive, curious, thinking, understanding, pleased, concerned, uncertain, apologetic, surprised, playful, and sleepy poses.
- Do not infer the user's private emotional state as fact. Reactions follow explicit conversation/context signals and remain restrained.

### C. Animation director

1. Introduce one director with channels for gaze, face, head, ears, mouth, whiskers, prop contact, and scene motion.
2. Prioritize privacy/safety and interruptions, then speech/listening, explicit performances, conversational reactions, and ambient idles.
3. Give clips anticipation, action, hold, and recovery. Use easing, overlapping action, contact constraints, and emotional transitions rather than just periodic sine waves.
4. Track recent clip IDs and motion families. Apply cooldowns, weights, and contextual eligibility; prevent immediate repeats and repeated near-identical variants.
5. Use a seeded scheduler for reproducible tests. Variation changes timing, gaze target, ear asymmetry, and intensity within safe limits—not unrestricted random jitter.
6. Stop competing channels from fighting each other. Speech owns mouth articulation unless an intentional, interruptible performance takes precedence.
7. On interruption, release props and return smoothly to listening without snapping or hiding privacy indicators.

### Pilot content budget

- 12 distinct emotional poses with transition tests.
- At least 3 meaningfully different reactions for each common state: greeting, listening, thinking, success, uncertainty, and interruption.
- 8 ambient behaviors with cooldowns and contextual triggers; not continuous random tricks.
- A ten-minute scripted conversation review must show varied appropriate behavior without the same performance recurring back-to-back.

## 7. Scenes and props

Start with six short, original, optional performances:

| Scene | Prop | Acting purpose |
|---|---|---|
| Investigating | Magnifying glass | Curious eye focus and a small discovery beat |
| Thinking | Floating notebook and pencil | Deliberate consideration, pause, then insight |
| Waiting | Small mug | Relaxed patience, slow blink, return to attention |
| Playful | Yarn ball | Anticipation, gaze tracking, controlled bounce |
| Celebrating | Tiny paper star | Warm restrained delight after confirmed success |
| Comforting | Soft umbrella | Gentle concerned eyes and a reassuring lean |

These are expressive illustrations, not claims that a real note, search, or task occurred. Confirmed tool results drive success reactions; the avatar cannot fabricate completion.

### Scene contract

- Define scene ID, duration, eligible states, required assets, anchors, keyframes, intensity, cooldown, and interruption policy.
- Use licensed/original lightweight assets; cache and unload them predictably.
- Props attach to defined anchors and use contact timing; no disconnected sliding or teleporting.
- Keep the floating-head identity. Optional transient paws can support a prop, but do not silently turn the character into a permanent full-body mascot.
- Keep scenes behind chat/privacy/approval controls. Disable scenic props in a Focus/Professional setting if the user wants a restrained workplace presence.
- Provide settings for Scenes on/off, subtle/expressive intensity, reduced motion, and a Preview action.
- Let the model propose only allowlisted scene IDs and bounded parameters. Validate duration, coordinates, assets, and intensity; no arbitrary scripts, URLs, or runtime shaders from model output.

## 8. App optimization audit

Record baseline and after-change measurements under the same device conditions:

- Frame timing/jank for idle, orb, speech, a prop scene, and chat transitions.
- CPU, GPU, memory, allocation rate, startup, and ten-minute idle battery/thermal behavior.
- Recomposition frequency: keep audio/IMU/gaze updates in draw-time state rather than recomposing whole pages.
- Hidden/offscreen animation loops, duplicate avatar instances, active collectors, camera/IMU lifecycle, and background timers.
- Particle sorting/allocations, repeated paths/brushes/text measurement, excessive alpha overdraw, and blur cost.
- Model loading/unloading, duplicate downloads, bitmap caches, and I/O on the main thread. Investigate before changing behavior.
- Test infrastructure: run bounded batches with recorded XML results; investigate retained workers/threads rather than assuming all full-suite failures are only host RAM.

Target a 60 Hz presentation where practical (16.7 ms frame budget), with lower-cost quality tiers if measured hardware/thermal behavior requires them. Targets are not claimed measurements. Never optimize by silently disabling privacy checks, wake functionality, approval/audit, or user-selected features.

## 9. Validation and delivery

- Unit tests: chat transitions/state retention, director priority, no-repeat rules, cooldowns, deterministic seeds, scene parsing, interruption cleanup, and privacy overrides.
- Render/visual tests: orb on four background types; cat expression sheets; props at contact/release frames; light/dark and narrow/wide layouts.
- Integration: incoming reply while collapsed, keyboard Back behavior, live voice interruptions, stopped generation, approval while a scene runs, restart and fold-state changes.
- Performance: before/after traces and ten-minute soak; check battery and memory stabilization rather than a single screenshot.
- Release: clean scoped diff, recorded test results, correct version/signature, recoverable prior APK, explicit install approval, then device acceptance.

## 10. Definition of done

- [ ] Jarvis contains floating particles and background-diffused glow, not a visible shell.
- [ ] Chat retracts into its launcher and does not reserve half the screen.
- [ ] Eye acting test approved and rendering approach chosen.
- [ ] Director prevents repetitive/competing performances.
- [ ] Pilot emotions, reactions, scenes, and props pass visual and interruption tests.
- [ ] Professional/Focus and reduced-motion modes remain usable.
- [ ] Measured optimization report and regression results recorded.
- [ ] Installation separately approved and device QA completed.

See AGENT_PLAN.md for bounded assignments, model routing, file ownership, and integration gates.
