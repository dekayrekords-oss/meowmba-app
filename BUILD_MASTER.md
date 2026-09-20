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
- Status: preview152 (1.3.96) installed in place; original debug certificate and data
  preserved. The separate signed release package is also updated to152.
- Fixes: all 21 lint errors, API31/32 road-feed compatibility, Bluetooth revocation/socket
  cleanup, CameraX API usage and Activity lookup. Native build uses baseline ARMv8-A;
  Needle3 checks dot-product support before loading. Sensor ticks no longer bypass
  avatar cadence and redraw a paused avatar.
- Validation: 399 suites / 2,708 tests, zero failures/errors, five skipped. Full debug AND
  release lint pass with zero errors (warnings remain). Candidate151 five device checks pass on the
  Fold and a 16 KB emulator, including real Needle inference without executing a tool.
  Candidate152 repeats four focused device checks on both; all pass.
- Candidate151 ten-minute run passes 61/61 foreground samples, no severe thermal event, 32.37% CPU
  including instrumentation, battery 37.1→36.5°C, thermal 2→1, USB connected.
  Separate 90-second comparisons at thermal 2: 87.62%→36.40% of one core. Unplugged/longer
  battery and older physical-device acceptance remain open; no all-day readiness claim.
- Signed APK/AAB prepared with the existing upload key, package `com.meow.meowmba`.
  Signatures, bundletool and all 86 native-library 16 KB alignment/JNI-export checks pass.
  All 149 protected preview runtime/model entries are unchanged from151.
  Play Accessibility exclusion remains enforced.
- Live Play inspected September 19 at 21:47 America/Phoenix: signed 1.3.96/code 152
  uploaded and submitted as Alpha release 11, full rollout to existing closed testers.
  Console shows “Changes in review”; availability of 152 is not yet confirmed. Managed
  publishing was already off. Previous Alpha build: 138. Production access is not granted;
  the dashboard showed 12 testers with 9 qualifying days toward 14 earlier this session.
- Owner-authorized Weebly feature update is published and verified on the existing
  Meow Assistant page at https://www.dekayrecords.com/meow-assistant.html#mx-release152.
  Detailed report: docs/store/TESTER_UPDATE_152_2026-09-19.md. Website receipt:
  docs/youtube/weebly/MEOW_TESTER_UPDATE_152_2026-09-19.md. No public Android source push.
- Fresh setup now starts with microphone off; “Not now” persists both sensors off and
  Android denial during setup leaves switches off. Setup requests only mic/camera.
  Signed152 fresh “Not now”, “Finish later” and Android-denial paths all pass,
  including cold restarts, on the 16 KB emulator.
- Receipt: docs/store/RELEASE_FIXES_152.md (prior performance/native evidence in151). SDK/data-flow review:
  docs/store/DATA_SAFETY_151_INVENTORY.md. Archive:
  /Users/prog/Meowmba-Releases/release-fixes152-20260919/.
- Do not reset phone data, enable monitoring, relax thermal limits, restart servers,
  operate the quarantined robot or invent tester feedback to bypass remaining gates.

## Feature queue

1. Prop placement, animation workshop, companion clock (`f2efb76`, `fda4eac`).
2. Meta glasses / Meow server integration (`c43664a` through `7fab957`).
3. Idle forms, driving awareness and optional OBD2 (`4a1077b`, `529cc69`).
Retain baseline computer setup, cancellation, habitat, onboarding, server and performance fixes.
Port feature commits; do not overwrite the baseline with an experimental branch tree.

## Coordination

### September 19 preview148 prepared — driving, creation, backgrounds and Termux heat

- Installed baseline remains147. Candidate148 is built/tested in this checkout; no second build
  owner. Automatic data-preserving install/test remains authorized. Phone currently absent
  from ADB, so Termux CPU diagnosis, existing-server restart and live avatar verification
  cannot yet be claimed. Do not stop unknown servers or reset Ubuntu to work around this.
- Root owns shared ViewModel wiring, safe phone diagnostics, Computer power truthfulness,
  setup profile and tests/build/install. home_overlap_fix owns responsive DriveCard and
  tests; result_flow_search owns avatar inventor/budget/retry dialog/tests;
  idle_heat_search owns five code-native animated backgrounds and tests. Workers do not build.
- Drive summary becomes compact with separate details; real crash/vehicle alerts stay.
  Avatar generation gets bounded remote queue time, elapsed feedback, retry and retention
  of validated designs when optional polish fails. New background art uses retained static
  layers and the existing lifecycle/thermal clock; no raster assets or per-background loops.
- The old Computer Idle/Off label is NOT server power: closing a PTY leaves OpenCode
  running. Future setup selects supported on-demand search (OPENCODE_DISABLE_FFF=1).
  Existing servers require a separately verified restart; this is not an already-applied
  heat fix. Version1.18.31 already disables FFF content indexing, so do not cite older
  home-directory indexing reports as this phone's measured root cause.
- Final397 suites/2,693 tests:2,688 passed, five skipped, zero failures/errors. Both APKs
  built. Avatar review landscape, real native backgrounds, failed-preview retry, optional
  review recovery and cancellation-before-dispatch have regression coverage. The latter
  had three failing red tests before the ownership-guard fix; do not remove that coverage.
- Archive:/Users/prog/Meowmba-Releases/drive148-20260919-RwdioX/. APK SHA256
  c6034d1dbd045103573bb9187d0e7941ec5a4d4fa3633120e722a02c500d4452. Same certificate;
 148 protected entries, none missing,147 unchanged, reviewed setup-phone.sh change only.
  No phone install, existing-server restart, user-data change or Play upload in this batch.

### September 19 preview147 installed — bounded acceptance complete

- Source09db18d installed12:57:50;2,646 full tests, five skipped, zero failures/errors;
  seven phone checks pass46.224s including actual result save/readback.148 protected assets
  unchanged, same certificate. APK0f595f4b5f584ae278ae962c4967f3d9e65d884016b5925739260f6f8cc7fb1f.
- See docs/RESULTS_IDLE_147_PASS_DOWN.md for provenance,146 failure and repaired storage,
  remaining transfer/project limits and profiling boundaries.129 exact native comparisons
  validate optimized fly facet drawing. No visual cadence or thermal-safety reduction.
- Source pushed to private Meow recovery branch. Actual UI test passes: exact-file once
  approval, Save & preview visibly renders the generated text, Files finds the saved copy,
  and Chrome opens its content URI correctly with a one-time viewer choice.
- Three-minute idle observation: 90/90 foreground samples, battery 34.7→34.5°C, Android
  thermal status 1→0, USB connected. CPU remains 54.53% of one core; do not claim a measured
  CPU improvement against the uncontrolled baseline. Graphics counters show 4,918/7,282
  janky frames in the interval; frame pacing is a follow-up, not signed off. No all-day
  heat, unplugged battery or production-readiness claim. Mic/camera remain off.

### September 19 next batch — preview146 tested;147 repair in preparation

-146 source242a63d installed in place at12:51:31, all148 protected assets unchanged,
  matching certificate, APK SHA2565f78ad2492e752004d5acba652e02c9375441573feda26b4653b94bfbddba33f.
  Fresh full suite2,644 tests, zero failures/errors, five skipped. Six device checks pass,
  but productive result saving FAILED: Android denies hard links in private app storage.
  Do not call146 production-ready.147 uses the no-replacement same-directory move API
  and must pass the actual phone save/readback test. No files/data were deleted/reset.
- Baseline sampled ART v3 trace identified fruit-fly facet drawing as a major app-side
  sampled CPU cost. Worker owns only exact-output facet paint/canvas reuse + differential
  pixel tests for147; no animation cadence, detail or thermal-threshold reduction.
- Archive146: /Users/prog/Meowmba-Releases/results146-20260919-ZFX8ax/. Source242a63d
  is pushed to Meow private recovery branch.147 install follows full/device verification.

- User permits warmer operation during demanding requested tasks; idle heat is the target.
  Keep Android thermal safety, visual quality and cadence. No promise that every ambient
  temperature/charger can yield a cold phone. User authorizes automatic in-place installation
  and testing of subsequent builds, preserving data and monitoring-off state; no repeated ask.
- Existing work through6f88496 pushed to Meow private /home/prog/git/meowmba-app.git,
  recovery/known-good-138, over NetBird100.77.97.236. LAN alias timed out; do not push public.
- One build owner/integrator. Delegated bounded work: idle_heat_search owns trace allocation
  gate/unused ear loop + tests; result_flow_search owns resultPaths known-tool parser + tests;
  home_overlap_fix owns measured home header/card layout + tests. Main owns result store,
  preview/Files wiring, build receipts, profiling, packaging and installation. No worker builds.
- Baseline145 idle sample:49/60 samples foreground,54.38% of one CPU core over94.2 valid
  seconds, battery35.7→36.0°C, thermal1→1 while USB connected. Not a controlled all-day
  comparison; preserve raw evidence and do not attribute heat to a single component yet.

### September 19 broader acceptance audit — app145 unchanged

- Active consolidated backlog: docs/TEST_AUDIT_AND_BACKLOG_2026-09-19.md (52 work items,
  with existing implementation distinguished from missing functionality and validation).
- Fresh full suite:2,615 tests, zero failures/errors, five skipped. Six live UI/privacy/
  terminal/agent/render checks pass; separate productive OpenCode probe passes11.662s:
  exact-target once approval, actual file text retrieval and same-session context recall.
- Test-only fixes handle root-relative permission patterns by binding absolute metadata;
  never broaden grants. Model omitted requested final newline; final content acceptance
  allows trailing-line differences and does not claim arbitrary byte-exact generation.
- Confirmed clock/Reflex overlap; Computer auto-result list only recognizes write/edit,
  and bounded JSON export supports approximately1.5MB files. Prioritize complete project
  result UX, clean-phone onboarding/recovery and thermal/voice reliability. Robot stays
  quarantined. Thermal status rose0→1 during mixed testing; heat is not signed off.
- Test/docs changes only; no new app install, data reset, account action or Play upload.
  Two synthetic QA files remain in Ubuntu workspace; no personal files altered. Evidence:
  /Users/prog/Meowmba-Releases/ubuntu145-20260919/audit-5Fanjn/.

### September 19 Ubuntu reuse repair — preview145 installed

- Owner reported container-busy during guided setup; phone has PRoot-Distro5.3.0, with
  Ubuntu listed as installed and read-only login returning MEOW_UBUNTU_OK. Old script only
  checked installed-rootfs/ubuntu; version5 uses containers/ubuntu/rootfs. This attempted
  unnecessary installation, refused by the active container lock. No reset/uninstall needed.
- Integrator fixes both layouts and refuses overwrite of incomplete existing containers.
  Seven executable shell fixtures cover reuse, repeat setup, missing/partial/broken layout
  and failed installation. Explicitly reviewed runtime delta: assets/computer/setup-phone.sh
  only; all other native/model assets must still match144. Do not whitelist arbitrary changes.
- User requests production-equivalent behavior and allows deletion only if needed. Preserve
  Ubuntu/Termux and existing servers; guided private-password entry remains user-owned.
- Source6b6cab3 installed over144 at2026-09-19 11:55:58. Full suite2,615 checks,
  zero failures/errors, five skipped; on-phone Computer143DeviceTest passes. Real read-only
  Ubuntu login and existing OpenCode1.18.31 executable pass. No files/containers deleted.
- Certificate unchanged. APK SHA256 0fbb53d981af4d9691858083d23078ed3a14cc5002d2bb481864ba4cbf0e4ce8.
  All147 other protected entries unchanged; reviewed script SHA256
  af5b176099d3a82c0567bff40acc26a0f40196b9e30921472d9dc7142dcbaba9 equals packaged asset.
- Archive:/Users/prog/Meowmba-Releases/ubuntu145-20260919. Fresh downloading is fixture-tested,
  not a real clean-phone production signoff. Authenticated worker/terminal still needs final
  private password entry and verification; do not confuse read-only binary execution with that.
- Follow-up completed: user finished guided command without errors. In-app Verify connection
  confirms OpenCode1.18.31 at4097 and securely persists credentials/endpoint. Live device tests
  now pass both authenticated Ubuntu PTY round trip (read-only /etc/os-release identifies ubuntu)
  and exact MEOW_COMPUTER_READY worker reply, with no tool permission granted. Monitoring untouched.
  Initial worker assertion timed out; its transcript matching could miss blank lines from reasoning.
  test now compares assistant text parts and restores saved endpoint before probing. Two final
  live tests pass in3.969s. Only the test harness changed; installed app artifact remains6b6cab3.

### September 19 Termux edition repair — preview144 installed

- Sole integrator. User authorized adapting the installed edition, correcting onboarding and
  installing/testing after checks. No Termux uninstall, replacement, data reset or unknown-server stop.
- Phone's actual Termux is googleplay.2026.06.21/code141, installed by Play. Its manifest has
  neither RUN_COMMAND permission nor RunCommandService.143's permission prompt could never succeed.
  Detect actual permission/service availability; a package-name check is not capability verification.
- Automatic setup stays available for supported editions. Other installed editions get fixed,
  user-pasted setup with hidden password entry, the same Ubuntu-preserving installer and explicit
  return-to-Meow authenticated verification. Clipboard contains no password. Pending secret and
  selected loopback endpoint are stored together in encrypted preferences for return/recreation.
- Onboarding/Computer share official F-Droid (recommended)/GitHub guidance, refresh on return,
  keep installed editions and warn against source mixing. Removed the obsolete copy-only wizard,
  blind sandbox OpenCode auto-start and unauthenticated NetBird APK mirror from onboarding.
- Source991fff0, installed over143 at2026-09-19 11:43:37 on RFCY61NJEVM. Full suite:
  2,608 tests, zero failures/errors, five skipped. All 148 protected runtime entries unchanged;
  matching signing certificate. APK SHA256 c5c42e31749fb17ac1d42b348ebaeb1be8177507b47031f20122b0418d7ebb09.
- Phone acceptance passes: real installed edition classified guided, no nonexistent permission,
  no replacement download offered, recovery4097 available, HUB/personal monitoring off and
  camera/mic unchanged. Test harness uses accessibility actions with its animation clock paused;
  coordinate-scroll attempts were unreliable with that frozen test clock. Production clock unchanged.
- Archive:/Users/prog/Meowmba-Releases/termux144-20260919. Termux remains Play code141;
  no uninstall, data reset, old-server stop or Play upload. Guided script syntax/contracts pass;
  live provisioning, authenticated terminal/task acceptance still await private user input.
  Hidden password entry remains the user's action; do not claim authenticated readiness prematurely.

### September 19 Computer recovery / personal companion — preview143 installed

- Sole integrator retains ownership. User approved in-place143 installation after checks,
  with new monitoring modes off. No Play upload authorized in this step.
- Existing4096 OpenCode server rejects the user's password. Do not guess credentials, kill
  that server or reset Ubuntu. Explicit Forgot password flow provisions a separate4097
  server, preserving files/tasks; atomically saves verified endpoint + encrypted credential.
- Native manual Ubuntu terminal uses authenticated OpenCode PTY, not a sandbox shell or
  WebView. It is not exposed as an agent tool. Real authenticated device acceptance pending.
- Remote prop generation gets a bounded180–240s queue allowance, elapsed feedback and
  cancellation, without increasing local inference budgets or changing model/billing.
- Computer HUB: off by default; paired desktop helper screenshots and accessible controls,
  explicit session + per-press confirmation, local OCR or two-confirmation one-frame cloud
  interpretation. A generic USB hub cannot capture computer video; raw UVC/HDMI capture
  and continuous movie/game commentary are not implemented. Physical pairing acceptance pending.
- Personal companion: off-by-default local cue reactions; no emotional diagnosis/profile.
  Private optional interview answers remain encrypted and outside chat/tools/cloud prompts;
  exact selected answer requires two red confirmations to send to OpenRouter. Private panel
  blocks screenshots and cancels pending approvals on background. No absolute security claim.
  Answers currently serve the private panel only, not automatic personalized conversation.
- Small continuous Lumen body sway uses the existing clock; drive target continuity retained.
- App source621cfec; installed over142 on RFCY61NJEVM at2026-09-19 11:21:53, preserving
  data/signature. Full suite2,601 tests, zero failures/errors, five skipped. All 148 protected
  runtime entries match142. APK SHA256 cb275097ffa7fc2319f914ffa677533568f2a27d574a0f1e390e4621490c454a.
- Phone UI test passes: recovery choice, HUB default-off, personal switches default-off,
  private-panel FLAG_SECURE and unchanged camera/microphone. The instrumentation harness
  freezes/advances its own animation clock; production animation settings are untouched.
- Archive:/Users/prog/Meowmba-Releases/computer143-20260919. Actual authenticated Ubuntu
  terminal/tasks, fresh provisioning and paired HUB capture remain pending; not release signoff.
- Live configured-model prop acceptance passes in76.446s: validated geometry saved/equipped,
  previous appearance/privacy restored, only the synthetic test prop removed. No provider/key
  selection changed. This is one successful free-route workflow, not a reliability guarantee.
- During device checks Android reported thermal status2 (moderate); cooling banner active.
  Protection remained enabled. Do not mistake thermal-reduced motion for a missing animation
  or claim sustained thermal performance has been resolved by this build.

### September 19 weather/appearance/Vending revision — preview142 installed

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
- App source3f5969c; installed in place over141 on RFCY61NJEVM, preserving app data and signature.
- Full suite:2,592 tests, zero failures/errors, five skipped. All 148 protected runtime entries match141.
- APK SHA256:161525ff247e42e977efa8f5857d32d6cbb42f8d4fe67b874edde79752a1374c.
- Device appearance/weather/home-controls check passes; screenshots visually reviewed. Care and neural
  labels hide without changing microphone/camera/driving privacy. Weather has no sample readings;
  this phone currently lacks location access, so select a city or explicitly grant device location.
- All seven Vending lanes open in UI tests; approval/refine/skip/cancel/schema checks pass locally.
  Live FAST avatar and prop generation timed out (75s/60s); no existing design was replaced.
  Route diagnostic resolves OpenRouter openrouter/free; phone reaches its model endpoint (HTTP200).
  BALANCED avatar retry passes actual candidate validation, no save/equip before approval,
  exact acceptance persistence and restoration (96s total test). One intermediate run was cancelled;
  notification shade obscured final preview screenshots, so do not claim visual-quality approval.
  Final device settings/privacy test passes again; test-created form removed, prior appearance and
  FAST reason preference restored. Prop generation still requires live acceptance.
  This is not evidence that all live generation providers or photo-to-model paths work.
- Archive:/Users/prog/Meowmba-Releases/creation142-20260919. No Play upload/public source publication.

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
- All 148 protected runtime entries match140. APK SHA256: 2ddead0889ca454885bf980faa20bcecbb394538325c506160218a3494594feb.
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
