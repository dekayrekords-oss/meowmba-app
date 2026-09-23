# Meow Flow + Meow's Desk — progress receipt, 2026-09-22 (Claude)

Owner asked Claude to "start the work and do as much as you can". This records exactly
what was done, how it was checked, and what remains. No APK was built or installed, no
phone was touched, no third-party files were downloaded, nothing was pushed, and no
integrator-owned file was edited (BUILD_MASTER / master plan §12).

- Branch: `feature/meow-flow-core` from `recovery/known-good-138@d815fda`
- Worktree: `/Users/prog/AndroidStudioProjects/Meowmba-flow-20260922`
- Plan: `docs/MEOW_FLOW_AND_DESK_MASTER_PLAN.md`; wiring: `docs/FLOW_WIRING_GUIDE.md`

## Done, by master-plan phase

| Phase | Status | What exists |
|---|---|---|
| 0 Spikes | **Partly** | Laya parity pipeline ready: `LayaInputsExportTest` writes `app/build/flow-laya/inputs.json` (30 items, longest state 381 chars ≈ 95 tokens, well inside Laya's 512/1024); `scripts/flow/laya_golden.py` for golden answers. S1 (ORT sharing), S2 download/contract and S3 device probe need owner approval + device → integrator. |
| 1 Flow core | **Done (not wired)** | Model, settings (+store), themes, SituationBuilder (budget + redaction), assessors (model/rules chain; Laya/Jev question wire shape), FlowPolicy, WorkStateDetector, IntentionCapture, stores, FlowController, FlowRuntime, Room entities/DAO/store + 10→11 migration statements |
| 2 Laya on device | Not started | Needs S1/S2 (download approval) |
| 3 Flow UI | **Done (not wired)** | `MeowFlowHome`, `FlowAdjustSheet`, tokens, samples; renders match the approved screenshots (`docs/design/screens-compose/` vs `docs/design/screens/`) |
| 4 Interruptions | **Done (not wired)** | `InterruptDelivery`, speech gate adapter, heads-up notification with actions, action receiver/bus |
| 5 Context | **Partly** | Evidence mappers (driving, notification w/o bodies, app, place, need), `SituationAssembler`, `CalendarSource`, `UsageSource` (foreground app + work context, on demand). Geofences, usage events and the listener generalisation need manifest/gradle → integrator |
| 6 Eval | **v0** | 25 labelled replay cases from Codex's catalogue (labels *proposed*); harness verified by mutation (fails on wrong labels) |
| 7 Desk | **Done (not wired)** | `DeskHabitat` + painter + `DeskState.from(snapshot, facts)`; three layers; cat walks via `AvatarFrameCadence` |
| 8 Jev | Hook only | FlowRuntime includes Jev only when the user enables it |
| 9 Release | Not started | |

## Verification (all on this branch)

- New Flow/Desk tests: **83** (`com.example.meowmba.flow.*`), 0 failures.
- Full unit suite: **420 suites / 2,805 tests, 0 failures, 0 errors** (run after the core,
  UI, Desk, delivery, context and Room commits; later commits add FlowRuntime/Laya-export/
  calendar tests, all green in the Flow run).
- Compose renders at 412×960 dp via Robolectric native graphics; compared by eye with the
  approved demo screenshots. Known differences: system fonts until OFL fonts are approved;
  Material slider thumb style in the Adjust sheet; accent choice uses preset swatches (no
  native colour picker on Android); Desk monitor cursor is static (no blink loop, for heat).
- Bugs found and fixed by tests during the work: interrupt double-counting on recompute;
  dismissed interrupts re-appearing; notification cancelled twice; Desk text scaled twice;
  driving evidence overwriting the last place.
- Lint (`lintDebug`): **0 errors**. 186 warnings/hints project-wide, of which 2 were in new code (animated offset overload, prefs KTX) — both fixed. No new lint issues remain in `core/flow`, `ui/flow` or `ui/desk`.

## Needs the owner

1. Approve downloading Laya (`convaiinnovations/laya-typed-decisions` and/or the community
   ONNX `nvkudva/laya-web-q8`, ~524 MB) → unblocks Phase 0 S2 and Phase 2.
2. Approve bundling OFL fonts (Source Serif 4, Archivo, Patrick Hand) → exact type match.
3. Confirm the 25 proposed replay labels (or let Codex review them).

## Needs Codex (integrator)

Follow `docs/FLOW_WIRING_GUIDE.md`: enums, FlowRuntime hook, CompanionScreen branch, Desk in
AvatarMotionHost, Room v11 + migration test, manifest receiver/permissions, evidence
producers, then Phase 0 S1 (ORT 1.27 sharing with sherpa-onnx) and the device checks.
