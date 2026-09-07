# Agent execution plan — immersive companion revision

## September 7: cover toggle — 1.3.35/code39

Added Settings → Phone & device → Cover-screen avatar with saved opt-in,
Samsung/generic setup guidance and the existing selected avatar/approval layer.
The switch controls compact cover rendering only, not OS cover-home replacement
or automatic launching. Main and tall cover screens retain normal avatar UI.
Physical Flip display metadata checked; actual cover launch/fold/mic/approval
acceptance remains pending. Other OEM detection is conservative best-effort.
This change does not resolve the separately reported ambient-noise greeting
loop or unavailable-desktop approval bug.

## September 7: conversational context follow-up — 1.3.34/code38

- Fixed the observed weather → location reply → Files misroute. Weather holds
  a city slot for three minutes; city replies use geocoding/weather, not the
  generic tool router. Cancel and explicit topic changes end the slot. Location
  permission denial keeps the city alternative; permission grant resumes weather.
- Chat and tool planning receive up to eight bounded working messages in the
  current conversation, without the old four-word follow-up cutoff. Referential
  follow-ups bypass the stateless Needle lane. History is not action approval.
- Local endpointed ASR no longer executes partial hypotheses through either the
  always-on fallback or UtteranceCapture. Final/refined results win; legacy
  recognizers retain partial recovery. New regression checks an incorrect
  "open files" partial followed by a corrected clock question.
- Full JVM: 1,202 tests, zero failures/errors, five conditional skips (1,197
  passed). Physical diagnostic: Hugging Face DNS/HTTPS and actual New River,
  Arizona geocoding/weather both passed (two tests, 2.119 seconds).
- Installed only Meow Assistant Preview 1.3.34/code38. Play remains code31.
  Artifact/evidence: Meowmba-Releases/meowmba-1.3.34-code38-context-preview.apk
  and Meowmba-Releases/context-1.3.34-qa on the build Mac.
  Repeated human-spoken context/latency testing remains open; this is not a
  claim of unlimited memory or reliable operation of every tool/service.

## September 7: standalone business-agent implementation

User authorized implementation and extensive testing on the connected Flip 6.
Active acceptance log: `docs/BUSINESS_AGENTS.md`.

- Implemented: durable custom agents, delegation, tool grants, completion-proof
  requirements, approvals, histories, local/cloud setup, MCP HTTP connections,
  scheduling, notifications and chat/voice job controls.
- Verified: 117 focused JVM checks, three explicitly configured local-AI runs,
  and three operations-template WorkManager cases with force-stop/restart,
  persisted approval, exact CSV total, one verified write and completion.
  Varied totals: 25.25, 9.33, 5088.68; the third case changed column names and
  status capitalization. A real local-model/Android MCP synthetic CRM workflow
  passed after fixing named/typed arguments and adding pre-approval validation.
  Dark creation, light approval-denial and failed-save/retry UI checks pass in
  an Android emulator.
  Unlocked follow-up: all three UI checks pass on the physical Flip, including
  light-theme denial and failed-save/retry. MainActivity navigation and actual
  notification taps from cold and warm processes also pass. Exact synthetic
  write arguments were shown and Deny prevented both writes.
  Microphone-denial retry prevention and startup-download lifetime fixes pass
  86 focused checks; an installed denied-permission observation showed no
  repeated capture/error loop. Code 37 also requests audio permission on an
  explicit voice action and resumes that action after grant. The actual Flip
  permission dialog and Sherpa capture startup pass. Human-spoken input and
  audible response are still awaiting user participation, not signed off.
  No business accounts have been connected or exercised.
- Remaining gates: broader business-workload coverage; real spoken Flip voice
  round trips; authorized real-service workflows. Thermal cooldown waits are real constraints,
  not permission to bypass safeguards or substitute a scripted model.
- Full-suite gate resolved: 1,197 tests, 0 failures, 5 conditional no-SDK skips
  in the MWDAT-enabled build. Corrected obsolete capture/provider/orb fixtures
  and real-network/lifetime contamination. Also fixed real status questions
  bypassing task tools, late preambles overwriting completed results, and
  missing-recognizer interruptions remaining stuck in SPEAKING.
  Green JVM tests do not replace the remaining physical/account acceptance.
- Preserve the existing Play installation and data. Testing uses the separate
  debug package, not a destructive uninstall or a Play upload.
- Handoff: implementation is maintained on Meow's
  `feat/image-services-settings`. Final preview APK installed in place and its
  on-phone SHA-256 matches the build artifact documented in BUSINESS_AGENTS.md.
  Play package stays 1.3.27/code31; preview is 1.3.33/code37. Synthetic active
  team fixtures were removed, histories preserved, no pending jobs left running.
  The user unlocked the Flip and physical interaction progressed. Do not mark
  all work done while the spoken/account acceptance gates remain.
  Temporary stay-awake-while-charging has been restored to its original 0;
  the unlocked follow-up reached 38.2°C and did not run another local planning
  workload. Physical screenshots and the full suite XML are preserved in
  `Meowmba-Releases/business-agents-1.3.33-qa/` on the build Mac.

## September 6: onboarding and Flip cover follow-up

- Implemented cover avatar base surface and Flip-only setup help in AvatarScreen.
  Shared approvals remain above it. See FLIP_COVER_SETUP.md for acceptance steps.
- Pending: validate on a connected Galaxy Z Flip and prepare a complete signed build.
- Pending recovery/integration: optional-token guided setup and local/OpenRouter
  onboarding changes were edited in a temporary checkout on the prior Mac, but
  were not committed to Meow before the session moved computers. Do not claim
  those changes are included in this checkout or installed on a phone.
- Onboarding acceptance requires a real inference response. Needle is an action
  router, not a conversational model; constructing its wrapper or finding its
  model file does not verify working local conversation. OpenRouter key status
  alone also cannot verify that the selected model can produce a reply.
- User confirmed OpenRouter. Present local model download first, obtain explicit
  cloud consent, link directly to OpenRouter keys, verify generation before
  completing onboarding, and keep existing saved credentials intact.

## Current override — September 5 release preparation

The user paused 3D work and explicitly excluded it from the app. The Filament
dependencies, 3D avatar entry and model picker/renderer are removed from the
app source sets. Prototype Kotlin files/tests remain locally under
`art/avatars/framework-draft/`; Blender studies remain outside the release.
All 3D checkpoints below are historical, not authorization to integrate them.

Release evidence and remaining publication gates are maintained in
`store/RELEASE_PREP_2026-09-05.md`. Chat/orb/cleanup/appearance focused tests
pass; this does not complete phone QA or the earlier animation roadmap.

Status: assignments planned, not launched by writing this file. Primary specification: IMMERSIVE_COMPANION_PLAN.md. Preserve the existing dirty worktree and earlier appearance work.

## Model and execution policy

- Preferred coding worker: OpenCode CLI on this computer, model `opencode/big-pickle`.
- Verified: executable and model catalog entry. Pending: a bounded authenticated request. Do not assume catalog presence proves availability or free/local inference.
- Before coding, use a read-only smoke task. Example CLI shape, verified against installed help: `opencode run --pure --model opencode/big-pickle --dir <isolated-checkout> '<bounded task>'`.
- Do not use `--auto`, install plugins, expose credentials, or silently substitute a paid model. Establish restrictive tool permissions before agent execution.
- Use a separate scratch checkout or explicitly owned files for each coding worker. A fresh checkout must include the relevant reviewed uncommitted changes; branching from HEAD alone would omit current work. The integrator applies/reviews patches.
- Meow local models: concise review, animation timing suggestions, test-case generation, or visual critique when supported. Recheck model/GPU capacity. Do not send secrets or private app data.
- Start with one Big Pickle worker and one independent review job; increase to two coding workers only if rate limits, host memory, and file separation permit. One Gradle build/test owner at a time.
- Each task receives only the relevant files, contracts, acceptance checks, and a small output budget. Ask for patches and a short findings list, not full-history narration. Stop repeated unsuccessful attempts and return evidence.

## Work packages

| ID / owner | Owned scope | Deliverable | Depends on |
|---|---|---|---|
| I0 / integrator | Baseline, model smoke test, ownership map | Source snapshot, test/trace baseline, verified agent access | None |
| O1 / Big Pickle orb worker | ParticleOrbAvatar.kt; isolated orb helpers/tests | Remove shell/rings; diffused background glow and readable particles | I0 |
| C1 / Big Pickle chat worker | ChatOverlay.kt; new chat presentation state/tests | Button/compact/history states, draft retention, keyboard/back behavior | I0 |
| A1 / Big Pickle eye worker | GodotCatFace.kt or approved isolated rig module; render tests | Eye-acting proof and renderer decision evidence | I0 |
| A2 / Big Pickle director worker | New director/selection helpers and tests | Priorities, cooldowns, variation, transitions, interruption rules | A1 contract |
| S1 / Big Pickle scene worker | New scene schema, registry, assets manifest, validator/tests | Six allowlisted prop scenes and preview controls | A1, A2 |
| P1 / Meow reviewer + integrator | Read-only profiling review; scoped fixes assigned later | Ranked bottlenecks with traces and proposed fixes | Baseline; rerun after O1/C1/A2/S1 |
| Q1 / reviewer | Test specifications and independent patch review | Failure cases, regression counts, visual/performance acceptance | Each package |
| I1 / integrator | CompanionScreen, CompanionState, CompanionViewModel, settings/theme wiring | Single coherent implementation; no duplicate clocks or safety bypasses | Reviewed worker patches |

Do not run A1/A2/S1 as independent edits to the same renderer. Agree on contracts first, then sequence changes. Workers request shared-file changes from I1 instead of editing them concurrently.

## Required worker task header

Every dispatched task states:

1. Exact outcome and specification section.
2. Allowed files and forbidden shared files.
3. Existing changes to preserve and upstream contracts.
4. Safety rules: no install/data clearing, no secrets, no fabricated tool results.
5. CPU/GPU/context limits; do not launch Gradle concurrently.
6. Required tests and measurable acceptance criteria.
7. Return format: changed files, concise rationale, test evidence, limitations, and patch location.

## Integration gates

- G0: Big Pickle access verified; unknown model/service failures reported honestly.
- G1: orb/chat patches compile and isolated state/render tests pass.
- G2: eye-acting proof accepted before large content/engine investment.
- G3: director and prop validators pass conflict, repetition, privacy, and interruption tests.
- G4: complete light/dark/keyboard/fold/reduced-motion review and measured performance audit.
- G5: full regression in bounded batches, correct APK version/signature, explicit install authorization.

## Current queue

- [ ] I0 baseline and Big Pickle authenticated smoke test.
- [ ] O1 diffuse-particle redesign (replaces prior ribbon polish).
- [ ] C1 retractable chat (replaces fixed 60% panel).
- [ ] A1 eye-acting proof and rig choice.
- [ ] A2 variation-aware animation director.
- [ ] S1 six-scene prop pilot.
- [ ] P1 measured app optimization report.
- [ ] Q1/I1 integrated validation and release gate.

No task is complete merely because code was generated. Record actual commands, results, and artifact paths; separate source completion from device acceptance.
# 3D framework checkpoint

Independent UI/optimization review requested and completed on September 5.
Integrator fixed the chat visibility/draft/call issues, model diagnostics and
opaque-screen animation lifetime. Findings and remaining device gates are in
`UI_REVIEW_2026-09-05.md`. A first rigged cat exists in `art/avatars/snow-cat`;
phone rendering acceptance is still pending.

Integrator implemented the optional Filament GLB framework; authoring contract:
`AVATAR_MODEL_CONTRACT.md`. Model import, selected-library persistence, named
state clips and live morph controls are implemented. Remaining bounded work:
author/import a real character, validate rig channels and backgrounds on phone,
profile native lifecycle/memory, and test fold/resume. This does not mark the
earlier eye-acting pilot or scene/prop library complete.

## Refined cat asset checkpoint — September 5, 2026

User-requested cleanup produced `art/avatars/snow-cat-v2/`, preserving v1.
The Blender source and GLB now have blended facial anatomy, rounded ear bowls,
curved amber irises, sliding lids and finer modeled fur. Export is ~3.2 MB and
61,944 triangles; shared blink/gaze/jaw names and eight state clips retained.
`scripts/verify_snow_cat_refined.py` checks the contract and renders reimported
GLB neutral/blink/gaze-jaw poses. Remaining gates: user visual approval, full
acting library, runtime lighting and phone performance. No installation.

## Direction correction — first model preferred

The user rejected v2's rounded redesign and asked to retain the first model,
clean stray/jagged geometry, and follow the supplied kitten illustration.
Current candidate: `art/avatars/snow-cat-reference/`, built with
`scripts/build_snow_cat.py -- --reference-cleanup`. Original v1/v2 assets remain
available. Fur tessellation/normals and floating lid strips were corrected;
reference-view pose and eye proportions were adjusted. GLB contract assertions
and neutral/blink/gaze-jaw render checks passed. Likeness is still not exact:
flowing fur, eye depth and the lower ruff need further visual authoring. Do not
mark the model approved or claim film-quality animation. No installation.
