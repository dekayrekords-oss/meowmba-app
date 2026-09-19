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
- Status: baseline restored; feature integration IN PROGRESS, not released.

## Feature queue

1. Prop placement, animation workshop, companion clock (`f2efb76`, `fda4eac`).
2. Meta glasses / Meow server integration (`c43664a` through `7fab957`).
3. Idle forms, driving awareness and optional OBD2 (`4a1077b`, `529cc69`).
Retain baseline computer setup, cancellation, habitat, onboarding, server and performance fixes.
Port feature commits; do not overwrite the baseline with an experimental branch tree.

## Coordination

### September 19 recovery checkpoint — NOT a release

- Phone remains original preview 138; do not confuse candidate builds with installation.
- Reproduced/fixed CompanionState JVM argument-slot overflow by grouping feature state.
- Fixed Meta wake-prefix corruption of ordinary "hey can you…" commands; targeted regressions pass.
- Fixed idle morph timer restarts and virtual-time test loop; stopped vehicle lookup while drive mode is off.
- SDK-enabled full suite checkpoint: 2,568 tests, 0 failures, 5 skipped (before subsequent updater/connection UI work).
- Actual phone: baseline UI visible, weather city lookup and Hugging Face network checks pass.
- Actual phone: free structured prop response rejected with "x is required". Production repair prompts now include rejected data; live retry acceptance remains pending.
- Needle 3 weights already exist on phone and match official SHA256 c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38.
  Candidate updates BOTH native archive and default catalog to Needle 3; native device acceptance pending.
- Added local-library checksum update checker, keeping old files; custom models report unknown. Voice/image updater coverage still pending.
- User confirms Ubuntu/OpenCode runs. Local server port4096 was unreachable. Added Ubuntu-aware launch and a password-protected loopback connection card.
  Task submission, approvals, session persistence and result integration are NOT complete; do not claim the connection card implements them.
- Bundled speech/native assets restored and external Meta build configuration located. Never replace a full build with missing runtimes.
- Remaining: final source tests, artifact audit, version allocation, phone acceptance of candidate, complete OpenCode task integration,
  drive-mode physical testing, all-character randomized Lumen acceptance, model-update tests/coverage, shared-server authority and clutter archive.

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
