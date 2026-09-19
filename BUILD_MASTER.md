# Meowmba shared build authority

Updated 2026-09-19. Read this first: Codex, Grok, Claude, OpenCode and human contributors.
This supersedes historical build-location/version instructions in other documents.

## Current recovery

- Integrator: Codex, this recovery task. One owner performs builds and phone installs.
- Integration branch: `recovery/known-good-138`.
- Integration checkout: `/Users/prog/AndroidStudioProjects/Meowmba-repair-20260914`.
- Baseline: `5efed2f`, version 1.3.82/code 138. Historical evidence is dated September 15.
- Verified original APK: `/Users/prog/Meowmba-Releases/idle136-20260915/meow-preview138.apk`.
- Grok source to recover: `529cc69`, preserved as `recovery/grok-lumen` remote-tracking ref.
- Status: preview141 (1.3.85) installed in place; authenticated Computer acceptance remains blocked on the user's private server password. Not a Play release.

## Feature queue

1. Prop placement, animation workshop, companion clock (`f2efb76`, `fda4eac`).
2. Meta glasses / Meow server integration (`c43664a` through `7fab957`).
3. Idle forms, driving awareness and optional OBD2 (`4a1077b`, `529cc69`).
Retain baseline computer setup, cancellation, habitat, onboarding, server and performance fixes.
Port feature commits; do not overwrite the baseline with an experimental branch tree.

## Coordination

### September 19 weather/appearance/Vending revision — preview142 under test

- Sole integrator owns this checkout. User authorized in-place phone installation and tests.
- Weather page removes simulated-condition/time controls and fake numerical empty-state readings;
  saved city or device location feeds default weather fetches, with visible failures and stale-selection fencing.
- Avatar Look: creation moved to Vending, compact swatch dialogs, visible app theme;
  character choices only under Lumen, Jarvis switch, no dog/cat performance buttons (voice logic retained).
- Avatar Space: persisted Care visibility/corner placement and neural-feedback visibility;
  hiding feedback does not disable reactions, privacy marks or agent approval/task feedback.
- New native-avatar requests ask whether to show an example. The example uses actual validated
  geometry and the production painter; accept saves/equips that same design. Refine/cancel/skip exist.
  This is a native cartoon proof, not a photorealistic image-to-3D pipeline or guaranteed perfect likeness.
- Vending photo picker requires explicit analysis consent; bounded image decoding and configured
  vision describe the reference. Avatars/props use visual descriptions; backgrounds can use the photo
  directly or a configured image provider. No silent claim of pixel-exact reference reconstruction.
- Vending animation cancellation now stops underlying work; Vending can use configured AI.
  Custom library props guide supported native prop roles, not arbitrary generated-prop animation.
- Terminal is not an embedded Termux session: phone UID check returns permission denied for its
  private bash. Sandbox label and Open Termux handoff are explicit. OpenCode health remains401.
- Full-suite and device acceptance results will be recorded before declaring this ready.

### September 19 Computer/Auto/avatar revision — preview141 installed

- Integrator owns OpenCodeComputer transport/panel, Auto page, avatar sliders, provider defaults and fact history.
- Computer task UI now submits asynchronous OpenCode sessions with explicit ask-all permissions;
  checks echoed rules before submission, polls progress/todos, offers once/reject approvals,
  answers worker questions, preserves session context and supports abort. Legacy tasks remain reachable.
- Result files can be explicitly copied into memba_workspace/projects/opencode and opened externally
  (bounded export about1.5MB; not automatic image/video streaming). HTTP errors never imply completion.
- OpenRouter is the missing-preference default/recommendation. Explicit local-only choices and cloud consent remain respected.
- Auto replaces NAV with a car icon, driving and navigation controls in one page. Settings links there.
- Avatar page has separate cadence/randomness sliders; all named styles resolve from turn-into requests.
- Facts are generated from the configured model with persistent claim/text history, exact/near-duplicate
  rejection and no old-pool fallback. Semantic equivalence/factual accuracy cannot be absolutely guaranteed.
- Source e407586; signed preview141 installed in place on RFCY61NJEVM, without uninstall or data reset.
- Full suite on a96b1f9: 2,581 tests, zero failures, five skipped. Final e407586 focused tests and packaging pass.
- All148 protected runtime entries match140. APK SHA256: 2ddead0889ca454885bf980faa20bcecbb394538325c506160218a3494594feb.
- Device Avatar/Auto selection and privacy-preservation test passes; screenshots visually reviewed.
- Authenticated worker test blocked: no verified password saved. User asked to enter it privately in Computer setup. A401 health response proves only password protection.
- Artifacts: /Users/prog/Meowmba-Releases/auto141-20260919.
- Do not mark fresh Termux provisioning or real authenticated OpenCode task execution verified without device evidence.

### September 19 automated Computer setup — preview140 installed

- Source bb58155; version1.3.84/code140 installed in place on RFCY61NJEVM without data reset.
- SDK-enabled build and 3 setup contract tests pass. All147 protected runtime/assets match139.
- Original certificate matches; APK SHA256 42864bef8dceacdcbe1304d726b446a34dbba66b15e2600917c17c36ae51a7bb.
- Cold launch, visible Lumen UI and sustained process checked; no new app crash. Existing phone
  OpenCode port4096 returns401, demonstrating authentication is enabled, not authenticated readiness.
- User must enter existing server password privately in Computer setup to complete verification.

- Added fixed Termux provisioning flow and declared RUN_COMMAND permission; no AI-supplied shell commands.
- Phone setup now offers Setup computer instead of Ubuntu command copying. Reuses Ubuntu/OpenCode,
  installs missing components, starts loopback password-protected worker and verifies authenticated health.
- Termux external-app opt-in and Android permission still require user action. Do not promise zero-touch.
- Pending real-device validation: process lifetime under proot/Termux, clean-install downloads, callback,
  permissions and resume after leaving the setup screen. Keep screen open during this preview.
- OpenCode task/approval/session integration is still incomplete. A healthy server is NOT a working agent.

### September 19 recovery checkpoint — NOT a release

- Phone updated in place to preview139 (1.3.83), source71b9194. No uninstall/data reset.
- Final SDK-enabled suite: 2,570 tests, 0 failures, 5 skipped. Device: Needle3 inference proposal, named-city weather and HF connectivity all pass.
- Correct signing key is Meow `/home/prog/.android/debug.keystore`, NOT `/home/claude/.android/debug.keystore`.
  Local ignored copy: `/Users/prog/.android/meow-prog-debug.keystore`; original matching certificate above.
- APK SHA256: 51bcdde3638eea4e2853dfe50613081bee8039a20a08e3e26db5037fc9253345.
  All75 baseline native/assets retained. Needle JNI intentionally updated; SD JNI rebuilt from same pinned sources and differs bytewise.
  Strict byte-identity verifier therefore reports two changed libraries, not a blanket pass.
- Reproduced/fixed CompanionState JVM argument-slot overflow by grouping feature state.
- Fixed Meta wake-prefix corruption of ordinary "hey can you…" commands; targeted regressions pass.
- Fixed idle morph timer restarts and virtual-time test loop; stopped vehicle lookup while drive mode is off.
- SDK-enabled full suite checkpoint: 2,568 tests, 0 failures, 5 skipped (before subsequent updater/connection UI work).
- Actual phone: baseline UI visible, weather city lookup and Hugging Face network checks pass.
- Actual phone: free single-response prop probe rejected with "x is required". After repair, the actual configured-model production workflow passed (79.603s): valid geometry saved and equipped; temporary test prop removed and prior appearance/privacy restored. This proves one prop workflow, not every avatar design.
- Needle 3 weights already exist on phone and match official SHA256 c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38.
  Preview139 updates BOTH native archive and default catalog to Needle 3; native phone smoke test passes.
- Added local-library checksum update checker, keeping old files; custom models report unknown. Voice/image updater coverage still pending.
- User confirms Ubuntu/OpenCode runs. Local server port4096 was unreachable. Added Ubuntu-aware launch and a password-protected loopback connection card.
  Task submission, approvals, session persistence and result integration are NOT complete; do not claim the connection card implements them.
- Bundled speech/native assets restored and external Meta build configuration located. Never replace a full build with missing runtimes.
- Source is pushed privately to `/home/prog/git/meowmba-app.git`, branch `recovery/known-good-138`.
- Old main/restore app/build directories (about5.1GB) moved recoverably to `/Users/prog/Meowmba-Releases/archive-build-output-20260919`.
  Source/credentials/known-good artifacts preserved; legacy checkout marker files point here.
- Remaining: complete OpenCode task integration,
  drive-mode physical testing, all-character randomized Lumen visual acceptance, model-update tests/voice+image coverage.
  On-phone visual inspection also found the new wall clock overlapping the reflex chip; layout correction is still pending.

Workers must start from the integration branch, claim bounded files/tasks here before editing,
and provide source commit, changed files, tests, limitations and handoff. No concurrent edits
to CompanionState, CompanionViewModel, Gradle versions or shared navigation without integrator
coordination. Only the integrator advances release versions and installs. Existing external
agents must read this file before continuing; a file alone does not stop running agents.

## Build and install requirements

- Run Gradle with an explicit `--project-dir` pointing at this checkout. A wrapper path alone
  does NOT select its project; the working directory still controls it.
- Build a consistent full source snapshot; do not mix incremental Kotlin/DEX output from branches.
- Preserve gitignored voice/native/model assets and Meta SDK configuration. Run the runtime
  verifier against the original APK; a fresh checkout can silently omit these dependencies.
- Choose a version code above BOTH the installed phone and all published/reserved versions.
  Check variant overrides as well as defaultConfig. Never select artifacts by mtime alone.
- Record source commit, clean/dirty state, version, package, SHA-256, certificate and checks
  beside every immutable artifact. Experimental output must not be called `latest.apk`.
- Compare signing certificate before install. Use `adb install -r` only; NEVER automatically
  uninstall, clear data, reset encrypted preferences or force a downgrade to overcome a mismatch.
- Uninstall destroys Android Keystore keys. A tar backup cannot preserve encrypted credentials.
- Verify sustained process survival, foreground UI and crash logs after cold start. An
  `am start` response of `Status: ok` alone does not prove the app stays running.
- Preview package: `com.example.meowmba`; Play package: `com.meow.meowmba`.
- Original preview certificate SHA-256:
  `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.

## Cleanup policy

Keep the known-good artifacts and their evidence immutable. Inventory old worktrees, untracked
files, signing configuration and native assets before archiving. Move superseded artifacts to
an explicitly named archive with provenance. Do not delete dirty worktrees, credentials, or
source history. Keep one active build checkout; legacy folders must point here and be marked
ARCHIVED / NOT FOR INSTALL. Publish this authority to Meow before claiming other agents see it.
