# Agent execution plan — immersive companion revision

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
