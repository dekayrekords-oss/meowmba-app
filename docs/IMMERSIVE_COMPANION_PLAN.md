# Immersive companion — detailed revision plan

## September 21 requested revision

The avatar selected in settings is the baseline. Automatic character visits last
3–6 seconds, then return to that baseline; gaps are randomized rather than a user-set
interval. Conversation, driving, hidden/paused stage and music interrupt visits.
This supersedes the September 19 interval/randomness controls below.

Needle 3 needs visible download/readiness/loading/inference/idle/failure status.
Own-speech echo must not create user turns, including streamed sentences and late
recognizer results. Music playback should show available title/artist and trigger
idle dancing, with Android notification access requested through an explicit settings
button for metadata. No automatic permission grant or microphone music recording.

## September 19 requested revision

Avatar owns separate automatic-change interval and randomness controls; sequential order at
zero randomness, shuffled coverage at full randomness, preserving every saved main form.
Explicit "turn into" selects all named render styles or existing/generated characters and
clears the temporary idle overlay immediately. Auto owns navigation plus driving settings.
Computer uses OpenCode session APIs with user-reviewed permissions, live progress and results.
OpenRouter is recommended by default without overriding a user's explicit local-only choice.
Generated facts retain a durable history; do not promise perfect semantic novelty or truth.
Implementation and device acceptance status live in BUILD_MASTER.md.

## September 12: full fly habitat preview 99

Full-body fly option, ten shared animated environments, deterministic prop interactions, optional persisted care, bounded preferences and live shared neural controls are implemented. Final focused run:66 passed; full suite retains baseline failures. Biological FlyWire mapping and sustained phone performance gates remain open. See [HABITAT_BUILD_PASS_DOWN.md](characters/HABITAT_BUILD_PASS_DOWN.md) for exact build, installation evidence and limitations.

## September 9: provider cards — 1.3.75/code79

Added automatically refreshed OpenAI model discovery, native Claude Messages,
custom OpenAI-compatible/Claude-format API settings, and OpenCode Zen model
selection. All participate in existing agent/phone/Logic tool routing with
permissions, approvals and audit intact. Phone discovery returned 54 OpenAI
models; reply generation returned HTTP429/no credits. Other new keys remain
unconfigured. Full suite: 2,077 tests, zero failures/errors, five skips; final
wording-only correction: nine provider UI tests passed. All125 runtime assets
preserved. See PROVIDER_CARDS_1.3.75_2026-09-09.md and the providers release folder.


## September 9: installed voice/location/heat repairs — 1.3.73/code77

Installed by USB at 17:54:15, preserving app data and all 125 runtime assets.
Fixed pending mic-start races, added real local bug intake, fresh-location/named-
place navigation and one encrypted place store, official route-free AZ511 work
zones, bounded face/pose/render work and thermal camera unbinding. Phone checks
verify bug persistence/audit, location, saved Google Maps navigation and valid
road-source responses. Cloud is selected, but OpenRouter rejects recordings with
HTTP 402; the visible Whisper fallback works and speech accuracy remains unresolved.
Heat/load improved in short samples; sustained thermal behavior remains unverified.
Android: 2,065 tests, zero failures/errors, five skips. No FlyWire data or physical
robot actuation added. See VOICE_LOCATION_HEAT_1.3.73_2026-09-09.md for evidence,
limits and the lightweight avatar-reflex proposal. Older checkpoints follow.


## September 9: installed Logic adapter preview — 1.3.64/code68

Installed and verified on SM-F966U1 at 14:25:14. Added durable project-allocation
recovery, typed Logic controls/receipts, generic plugin Controls view handling
and WAV sample verification. Live empty-project tests cover Apple Channel EQ
and third-party StudioAI. Android: 2,021 tests, zero failures/errors, five skips;
Python: 55 passed. All 125 prior native/assets preserved. Mac helper installed;
phone pairing is pending availability. Full Logic editing/mastering remains open.
See `LOGIC_CONTROL_1.3.64_2026-09-09.md` for exact evidence and outstanding work.


## September 9: installation follow-up

User authorized installation. Installed the archived 1.3.63/code67 APK in place
on the connected SM-F966U1; package version and lastUpdateTime 12:18:08 verified.
No app data cleared. See release `installation.json`. The earlier pre-install
checkpoints below are retained as historical evidence. Logic implementation is next.

## September 9: floating characters and reliability — 1.3.63/code67

User's final visual direction: floating features only; no whole heads. Added
rabbit, pig, alien, robot and panda; all12 character forms now have distinct
eye geometry. Character forms grow up to26% portrait/8% landscape with travel
reserved; eyes-only retains its established footprint. Fixed partial-morph
clipping and preserved drive/idle motion and hit targets.

Repaired project-creation feedback loops/retries, stale mic/session callbacks,
muted turn lockups, missing/corrected replies and typed-message deduplication.
Added local possible-bug capture, Business mode readiness/schedule controls,
local note summaries, honest media failures and Logic read-only preflight.
Live traffic coverage, full Logic editing/mastering and all-day background
notes remain unimplemented/unverified as described in the report.

Full suite **2011 tests:2006 passed, five skipped, zero failures/errors** before
assembly; Python helper20 passed. Source/version/signature and125 native/asset
entries verified. Not installed; no user-account/media/device/physical actions.
LAN fetch timed out. See FLOATING_AVATARS_AND_RELIABILITY_2026-09-09.md and
`/Users/prog/Meowmba-Releases/character-eyes-2026-09-09/`.


## September 9: pre-build agent hardening — 1.3.62/code66

User requested remaining issues be fixed before packaging. Six failures were
reproduced before repair; all now pass. Hardened search/result checks, missing
and contradictory selectors, native snapshot/dispatch consistency, draft-field
protection, manual/social handoff separation, delayed results, and cached recipe
validation. Recipe format2 adds per-step structural context and a persistent
in-flight marker so interrupted replay cannot silently resume. Actual model
calls are distinguished from native planning turns; progress updates captions.

Full suite passed before assembly: **1929 tests; 1924 passed, five
skipped, zero failures/errors**. Source hashes checked before/after packaging;
APK/version/signature and all 125 prior native/asset entries verified. No install
or real-phone/physical operation. Real-device acceptance remains open. LAN fetch
timed out. See AGENT_HARDENING_2026-09-09.md and release evidence at
`/Users/prog/Meowmba-Releases/agent-hardening-2026-09-09/`.


## September 9: verified phone workflows — 1.3.61/code65

User authorized adopting the useful Meridian agent ideas. Main integrated typed
phone outcome checks, unique semantic targeting, bounded settling, cancellation,
nonblocking swipe callbacks, and a version-bound learned-search pilot. Three
matching verified planned runs promote a recipe; replay binds query data and
keeps existing permission/confirmation checks. Settings can disable or forget
learning and revoke an active replay. Business workflow ownership is unchanged.

Full suite/build: **1,908 tests; 1,903 passed, five skipped, zero failures/errors**.
APK/signature and all 125 prior native/asset entries verified; native settings
capture reviewed. No install, real-phone control/inference, or physical actions.
Real-device accuracy/latency remain unverified. LAN fetch failed; local changes
preserved. See VERIFIED_PHONE_AGENT_2026-09-09.md and release evidence under
`/Users/prog/Meowmba-Releases/verified-agent-2026-09-09/`.


## September 9: on-device scene invention — 1.3.60/code64

User requested local AI invention from activities such as “let's go fishing”.
Implemented avatar.invent with local-only planning, bounded native prop tracks,
one repair attempt, saved replay/numbered variations, cancellation/generation
fences and conversational fishing/self-performance routing. Native props include
rod/line, fish, bobber, ripples, ball, star and heart. No generated code execution.
Full suite/build: **1,881 tests; 1,876 passed, five skipped, zero failures/errors**.
APK signature and all prior native libraries/assets verified. Renderer demo uses
a deterministic fixture, not live model output; real on-phone quality/latency
remain unverified. Built, not installed; physical control remains quarantined.
LAN fetch timed out. See LOCAL_ANIMATION_DIRECTOR_2026-09-09.md and release archive
`/Users/prog/Meowmba-Releases/local-animation-director-2026-09-09/`.


## September 9: contextual repertoire — 1.3.59/code63

User approved useful repertoire additions. Implemented one bounded expression plan for
conversational visual targets and supported speech delivery, contextual suppression of
unsolicited play/commentary/greetings, remembered intensity/playfulness, bounded restart
history, three animal story arcs and optional local timing diagnostics. Catalog55/scenes16.
Full suite/build: **1,869 tests; 1,864 passed, five skipped, no failures/errors**.
Package/signature and bundled speech assets verified; native storyboards reviewed.
Diagnostic callback timing is not acoustic onset or GPU jank. No physical-control
changes, new installation, data clearing or publication. Existing1.3.58 install remains.
LAN fetch timed out. See CONTEXTUAL_REPERTOIRE_2026-09-09.md and the release archive
`/Users/prog/Meowmba-Releases/contextual-repertoire-2026-09-09/`.


Installation follow-up: user authorized **1.3.55/code59**. In-place installation
on SM-F966U1 succeeded; Android package version and 13:48:42 update time verified.
No data clearing, new permissions, explicit launch or physical testing. Physical
base control remains disabled; physical acceptance remains open. The checkpoints
below retain their original pre-install status.

September 8 post-install safety correction: **1.3.55/code59** quarantines ALL
physical base control, not only wheels. The 1.3.54 stop routine retained an
unverified rotation script and is superseded. No actuator writes, automatic
reconnect or scan; old body-enable preferences are revoked. Avatar/visual
attention remains available. This is a safety lockout, not a finished motion
fix or physical validation. New installation requires separate approval with
the base off. Firmware-specific stop/attachment/hazard evidence is required
before restoring movement. Full suite/build passed: **1,719 tests; 1,714 passed,
five skipped, zero failures/errors**; package/signature/native libraries verified.
Not installed. See ROBOT_CONTROL_QUARANTINE_2026-09-08.md.

Installation follow-up: the user authorized 1.3.54/code58 on the connected
SM-F966U1. In-place installation succeeded and package version was verified;
data was not cleared. Physical robot acceptance remains open; wheel motion is
still disabled. The checkpoints below retain their original pre-install status.

September 8 robot safety checkpoint: **1.3.54/code58** fixes cancellation and
stale-connect ownership, serializes Bluetooth stop attempts, handles sensor
reads and adds explicit Stop/Disconnect controls. Physical search on face loss
is removed; follow filtering is elapsed-time based. **Wheel movement remains
disabled pending verified firmware-specific commands and safety evidence**;
legacy unverified transports cannot bypass the gate. A transport acknowledgement
is not a measured physical stop. Whole-character visuals remain available.
Full suite **1,705 tests: 1,700 passed, five skipped, no failures/errors**;
debug package/signature/native libraries verified. Not installed; connected
phone still 1.3.50/code54. Video-inspired natural physical attention is not done:
firmware/stop/sensor/direction and supervised motion acceptance remain required.
Details: `ROBOT_DISCONNECT_SAFETY_2026-09-08.md`.

September 8 whole-character travel checkpoint: **1.3.53/code57** replaces tiny
eye-sized root offsets with available-stage travel and independent held body
postures. Quiet mic-off/offline use now roams; speech, scene and driving
priorities remain. Smaller stable non-driving fit, combined root-local phone
roll, coordinated props/hit targets and fixed privacy marks pass native visual
checks. Full suite: **1,668 tests, 1,663 passed, five skipped, no failures/errors**;
debug build/signature verified. Scripted native preview reviewed, not live
tracking proof. Phone disconnected; no install or physical robot movement.
See `WHOLE_AVATAR_TRAVEL_2026-09-08.md`. Wider acceptance gates remain open.

September8 project/local-thermal checkpoint: **1.3.52/code56** introduces a
persistent project workspace (brief, editable plan, observed tool activity and
real artifact index), project-scoped output/versioning and safe plan-update tools.
Native local-model lifetime/cancellation fixes remove duplicate/concurrent and
superseded inference work without changing model quality. Neck commands gain
minimum-jerk profiles through the existing robot safety gate; translation stays
blocked when sensor interpretation is unverified. Full suite **1,652 tests:
1,647 passed,five skipped,no failures/errors**; debug assembly/signature/native
libraries and project UI render checks passed. Installation approval is pending;
actual inference heat/JNI cancellation and physical motor acceptance remain open.
No new3D integration, microphone enabling, robot actuation, remote sync or Play
publishing. Details: `PROJECT_BRAIN_LOCAL_THERMAL_2026-09-08.md`.

September 8 natural-attention/library checkpoint: **1.3.51/code55** adds
whole-pair idle drift and adaptive observed-face tracking, plus Firefly
woodland, Moonlit lagoon and Rainy reading loft backgrounds. Shared geometry,
privacy, driving and thermal/pause behavior remain intact. LLM → Local now
owns installed models, phone scanning/picker import, device-aware HF browsing,
filtering and download access settings. Speech correctness fixes address
legacy word substitutions, unstable previews, long-audio truncation and
queued-final PCM pairing. Full suite: **1,555 tests, 1,550 passed, five skipped,
zero failures/errors**; debug assembly/signature and native visual QA passed.
No claim of universal model compatibility, rated/benchmarked Hub results or
measured speech accuracy. Install offered and awaiting approval; physical
attention/voice acceptance remains open. See `NATURAL_LIBRARY_ASR_2026-09-08.md`.

September 8 scenes/mic checkpoint: **1.3.50/code54** adds Butterfly Chase,
Detective, Yarn/Ball/Acorn Play, Mischievous Raccoon, Sleepy Nest and Rainy Window.
Original vector props and gaze share one timeline; scenes are explicit,
non-ambient and recover to the selected form. A saved mic kill toggle above
camera play stops app input and blocks background/late callback rearming.
Full suite 1,474 tests (1,469 passed, five skipped); debug build installed and
mic-off persistence verified on phone. Actual phone reported thermal status 4,
so physical animation/performance acceptance remains open; native previews
provide visual evidence. See `SCENES_MIC_KILL_2026-09-08.md`.

September 8 reset follow-up: **1.3.49/code53** supports “go back to normal”
and “stop being an animal” to restore persistent eyes only, including during
call mode. Temporary performances are canceled; the shared rig and colors
continue. All 29 focused checks and debug assembly/signature passed.

September 8 shape-shifting checkpoint: **1.3.48/code52** adds persistent Cat,
Dog, Squirrel, Raccoon and Eyes-only forms on one shared native rig. Anatomy
channels morph without restarting gaze/blink/speech; colors, privacy and
driving behavior remain shared. Voice/UI selection and the audited `avatar.form`
tool persist the choice; temporary performances return to it. More forms need
authored/tested profiles—this is not unrestricted runtime animal generation.
Full suite: 1,453 tests, 1,448 passed, five skipped, zero failures/errors;
debug build/identity/signature verified. Native preview uses scripted inputs.
See `LUMEN_ANIMAL_FORMS_2026-09-08.md`. Plans and framework are updated; phone
acceptance, installation and the wider roadmap are not marked complete.

September 8 driving checkpoint: **1.3.47/code51** adds shared smooth turn/brake
inertia, stronger Lumen bracing/depth, immediate manual mode activation and
gravity/display/lifecycle/freshness corrections. Visual-only; no extra speech,
network work, crash-threshold change or physical-base motion. Full suite:
1,429 tests, 1,424 passed, five skipped, zero failures/errors; debug build/signature
verified. Native driving preview uses simulated inputs. See
`DRIVE_VISUAL_REACTIONS_2026-09-08.md`. No install/push/publication; real-device
acceptance and the wider roadmap remain open. Meow fetch timed out.

September 8 checkpoint: **1.3.46/code50** adds Lumen Cat, shared color controls,
39 original performances, native transient props, search/category selection and
anti-repetition. Six quiet ambient performances yield to user interaction.
Full suite: 1,389 tests, 1,384 passed, five skipped, no failures/errors. Debug
assembly/signature verified; visual captures use the production native rig and
painter with scripted inputs. See `LUMEN_CAT_PERFORMANCES_2026-09-08.md` for
official reference limits and remaining acceptance. No exhaustive Loona library
claim, no manufacturer assets shipped, no installation or physical-base test.
This advances eye acting/variation/props; it does not close measured phone
performance, wider scene contracts or the remaining production-release gates.

September 7 Lumen checkpoint: 1.3.45/code49 adds an original eyes-only style,
temporary cat/dog ears and an active-clock spring rig. Shared attention now
expires stale detections; BLE expression output is connection/safety gated.
The existing sensor decoder is unverified, so unknown data stops motion and
physical translation remains disabled. See `LUMEN_AVATAR_2026-09-07.md` for the
1,352-test result, native render evidence and separate phone/base acceptance.
This does not close the wider scenes/props roadmap or authorize installation.

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


## Creative animals and story scenes — September 8, 2026

Candidate **1.3.57/code61** adds Australian shepherd, owl and duck forms;
Mouse Chase, Book Reading, Cowboy Hat, Wizard Hat and Painter Beret scenes.
The 52-performance catalog now has 13 scenes in idle rotation. Hats follow the
head and reserve shared stage space during raised-idle handoff; mouse pursuit
uses the existing whole-avatar springs. Full suite: **1,792 tests, 1,787 passed,
five skipped, zero failures/errors**. Native visual review, APK assembly,
signature and speech-asset checks passed. Original 1.3.56 repairs and existing
working changes remain included. No phone installation or physical action;
separate install/device-acceptance gates remain open. See
`CREATIVE_ANIMALS_SCENES_2026-09-08.md` for evidence and release artifacts.

## Emotional acting checkpoint — September 9, 2026

Candidate **1.3.58/code62** implements a 16-pose Lumen acting pilot with
independent lids, brow shape, pupil, ear asymmetry, posture and small squash.
Entry accents vary without restarting on identical state updates. Listening
reacts locally, and a usable speech draft releases an active scene. One display
clock drives the visual score. Original animal materials/anatomy are refined;
all 13 scenes and their interruption/privacy/drive bounds are retained.

Full suite: **1,820 tests, 1,815 passed, five skipped, zero failures/errors**.
Native light/dark sheets and a 14-second scripted preview support visual review.
Development-Mac painter timing is recorded with its limits. This does not close
G2 visual approval or G4/G5 phone timing, ten-minute live conversation, thermal,
fold/resume and installation gates. Physical control remains quarantined;
shared authored intent is not awareness or proof of actuator synchronization.
Details and artifact locations: `EMOTIONAL_ACTING_2026-09-09.md`.
