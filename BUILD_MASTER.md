# Meowmba shared build authority

Updated 2026-10-03. Read this first: Codex, Grok, Claude, OpenCode and human contributors.
This supersedes historical build-location/version instructions in other documents.

- October 3 **202 / 2.0 allocated for Flow as the default idle view**, branch `feature/flow-idle-default-20261003`, based on submitted build 201. Lumen/Orb remain opt-in; Off retains the current page, and existing explicit preferences are preserved. Idle Desk visits Flow without changing the saved home. Focused SDK-free debug checks: **12 passed; lint passed, zero errors** (207 warnings / 12 hints remain). Full-SDK phone assembly is blocked by missing external MWDAT credential configuration; no 202 APK/AAB, installation or Play upload. See `docs/FLOW_IDLE_202_20261003.md`. Codex is build owner. **202 allocated; next 203.**

- October 3 **201 / 2.0 uploaded and submitted for Play production**, branch `release/play-2.0-201-20261003`, based on validated200 / `4f2395d`. Existing upload key recovered and verified; signed201 bundle accepted by Play. Seven changes are in the review queue with automated quick checks running; **production approval/live publication pending**. Production100%/172countries, managed publishing off; old152 excluded. Alpha201 full rollout submitted, internal201 available replacing2. Updated listing, ring/nonmedical details, Health apps/Fitness info and published public privacy HTML. Private APK201 ready, certificate/all161 protected entries match200; phone disconnected,201 not installed. Public signature/bundletool,12 actual-release tests,88 native16KB/JNI and APK ZIP alignment pass; lint0 errors,219 warnings/12hints. Physical ring acceptance remains pending. No remote Android source push. Receipt `docs/store/PLAY_RELEASE_2.0_201_PREPARATION.md`. **201 consumed; next202.**

- October 3 **200 / 1.5.34 allocated for Play policy remediation and phone testing**, branch `release/play-200-20261003`, based on installed199 / `ab84e38`. Signed private APK installed in place over199 on Fold R5GL75XFCXD; version, original installation date, foreground launch and live process verified, no startup AndroidRuntime error. Matching certificate/16KB ZIP alignment and all161 protected runtime entries pass. 3235 debug cases (0 failures,17 skips) and12 actual-release boundary cases pass. Unsigned AAB/bundletool,88 native library16KB alignment and required JNI checks pass; release lint passes (0 errors,219 warnings/12 hints), existing upload key folder inaccessible. Public variant removes broad media/background location and both Accessibility services. No remote source push, Play upload, submission or publication. **200 consumed; next201.**

- October 2 **199 / 1.5.33 allocated for ring single-tap selection**,
  branch `codex/r08-ring`, based on installed 198. Selects the accessible control
  under the crosshair, with a delayed touch fallback after removing the overlay.
  Local debug service diagnostics expose control state/input events, no screen text.
  Signed full-SDK debug build and 19 focused cases pass. Certificate and all 161
  protected runtime entries match installed 198. Installed in place on the Fold;
  original installation retained, launch/PID stable, no new process crash. Ring
  reconnect/visible target and subsequent 30-second idle sleep observed in local
  service diagnostics. Physical single-tap selection pending. **199 allocated; next 200.**

- October 2 **198 / 1.5.32 allocated for ring controls sleep mode**,
  branch `codex/r08-ring`, based on installed 197. Enabled by default, 30-second idle
  timeout adjustable 5–300 seconds; hides target/ignores actions until double-tap wake.
  Wake tap is consumed. Local standby keeps the BLE link. Signed full-SDK debug build
  and 14 focused idle/policy/overlay/native UI cases pass. Timeout setting/30-second
  default visually checked at 320dp. All 161 protected runtime entries/certificate match installed 197.
  Installed in place on the Fold, original installation retained; launch/PID stable
  beyond 35 seconds with no new app crash. Physical double-tap wake acceptance pending.
  **198 allocated; next 199.**

- October 2 **197 / 1.5.31 allocated for blocked left cursor movement**,
  branch `codex/r08-ring`, based on installed 196. Phone's left combo was unverified,
  though Single tap and Previous were verified. Cursor movement now composes verified
  component inputs; clicks/Back retain exact-gesture verification. Signed full-SDK debug
  build and 9 focused regression/policy/overlay/native UI cases pass, including the
  phone's verified-input state. All 161 protected runtime entries/certificate match installed 196.
  Installed in place on the Fold; original installation retained, launch/PID stable
  and no new app crash. Physical left-gesture retest pending.
  **197 allocated; next 198.**

- October 2 **196 / 1.5.30 allocated for ring cursor axes correction**,
  branch `codex/r08-ring`, based on installed 195. One tap + swipe moves left/right;
  two taps + swipe moves up/down. Original preset migrates once; custom mappings retained.
  Signed full-SDK debug build and 7 focused policy/migration/native UI cases pass.
  Runtime assets and signing are checked against installed 195 before upgrade.
  Installed in place over 195 on the Fold; certificate/161 runtime entries match,
  original installation retained, launch/PID stable with no new app crash.
  Physical ring acceptance pending. **196 consumed; next 197.**

- October 2 **195 / 1.5.29 allocated for ring scrolling and clicking across apps**,
  branch `codex/r08-ring`, based on installed 194. Separate Allow all apps opt-in,
  movable visible crosshair and click action, scroll/click mapping preset. Signed full-SDK
  debug APK and 12 focused protocol/policy/overlay/native UI cases pass. All 161 protected
  runtime entries match the phone's installed 194; signing certificate matches. Installed
  in place over 194 on the Fold, first-install time retained, launch/PID stable with no
  new app crash. Ring Accessibility is enabled; physical acceptance pending. No source publication.
  **195 allocated; next 196.**

- October 2 **194 / 1.5.28 allocated for interactive ring tester**, branch `codex/r08-ring`,
  based on installed 193. Live press/release visualization, gesture counts and local on-demand
  battery, steps, heart rate, oxygen, temperature, HRV and stress tests. Signed SDK debug build
  and 9 focused protocol/native UI checks pass. All 161 protected runtime entries match
  installed 193; certificate matches. Installed in place over 193 on the connected Fold;
  original first-install time retained, MainActivity launches, PID stable, no new app crash.
  Accessibility opt-in and physical ring gestures/sensor acceptance remain pending.
  **194 allocated; next 195.**

- October 2 **193 / 1.5.27 allocated for R08 ring integration**, branch `codex/r08-ring`,
  based on 192 / `4f579ba`. Settings → Smart Ring, bonded BLE media-key configuration,
  gesture tester, local accessibility scrolling, mappings, pause and explicit app allowlist.
  Signed debug APK builds with MWDAT; 4 ring checks and 24 avatar integration cases pass.
  Full suite: 3,208 cases, 1 avatar timing failure, 4 skips; that class passes in isolation.
  Full lint was stopped after a prolonged run. All 160 protected native/model/other runtime
  entries match installed 190; the manual matches release 192; debug omits generated ART profiles.
  October 2: signed debug 193 installed in place over 190 on SM_F971U / R5GL75XFCXD.
  Certificate and protected runtimes verified against the phone APK; original first-install
  time retained. Foreground MainActivity and stable process confirmed, no new app crashes.
  Ring pairing/gesture acceptance still pending. No Play upload. **193 consumed; next 194.**

- October1 **192 /1.5.26 allocated for truthful Play location wording**, branch release/play-192-20261001, based on191/5c6bd7c. Optional context sensing and permissions unchanged; disclosure/setup/manual remove unsupported reminder/named-place claims and clarify local storage versus selected cloud-AI sharing. Isolated checkout; clean bundle build,3204debug cases,4release cases,lint and signing/package verification passed. No policy approval; location demo remains pending. Signed191 remains immutable. No upload, submission, phone installation or credential changes. **Next193.**

- September30 **191 / 1.5.25 Play candidate validated by Codex** (branch `release/play-191-20260930`, based on installed190 / `608d15a`): Play media pickers/permission boundaries, accurate cleanup/manual and optional Flow location disclosure. 3,204 regression cases (0 failures,9 skips),4 release permission tests, native152-to191 migration/photo-import checks and1 native memory-dialog test passed; lint/native package checks passed. Candidate is UNSIGNED with the upload key, not uploaded to Play or installed on the Fold. Production remains blocked after Google rejected152 broad photo permissions; existing signing configuration is on unreachable Meow. Receipt: `docs/store/PLAY_RELEASE_191_PREPARATION.md`. **191 allocated; next192.**

- September29 **190 / 1.5.24 phone Computer + voice fixes** (branch `feature/flow-fixes-20260928`, Claude, on top of 189):
  setup-phone.sh apostrophe that broke every OpenCode server start since preview148 ("code 1"); setup after a
  reboot (startForegroundService fallback) and first-request retry; Tasks & approvals shows readable full diffs and
  Allow works for large edits; VoiceTurnGate stops clicks/reactions/overheard talk reaching the LLM; Development
  Studio session survives Back/navigation; Back from a Flow visit returns home; saved HTML results render in-app;
  labelled checkboxes toggle from their label. Built (daily, MWDAT + verified-jni flags) and installed on the Fold
  R5GL75XFCXD on 2026-09-29 at the owner's request; NOT published. **190 consumed; next 191.**
- September28 **189 / 1.5.23 Computer setup fixes** (branch `feature/flow-fixes-20260928`, Claude, on top of 188):
  Setup computer now recognises Termux's "RunCommandService requires allow-external-apps" refusal (was "code -1")
  and offers "1. Copy Termux fix & open Termux" (one-line command setting `allow-external-apps=true` in
  `~/.termux/termux.properties` + `termux-reload-settings`); Check installation and Check phone again show a
  visible result; show/hide eye on the phone setup and Linux-server password fields. Built (daily, MWDAT +
  verified-jni flags) and installed on the Fold R5GL75XFCXD on 2026-09-28 at the owner's request; NOT published.
  **189 consumed.**
- September28 **188 / 1.5.22 reserved for the integrated build** (branch `feature/flow-fixes-20260928`, Claude):
  Codex's `feature/meow-flow-core` (indexed long-term recall, memory correction/pin controls, additive Room15)
  plus Claude's `feature/desk-apartment` through `21f6367`, plus Flow fixes: the Later button, an Ignore button that
  stops a Flow item coming back, a swipe-away "cooling animations" notice and a higher thermal limit.
  Later now clears the card at once (it used to wait behind a debounced model pass, so on a phone with a slow
  Laya pass it looked dead; an in-flight pass could also bring the card back). Ignore is a new `IGNORED`
  status: nothing re-adds it (managed sync, package notices and message re-posts all leave it alone) and
  Settings has "Bring back N ignored items". Thermal: notice/easing start at SEVERE (was MODERATE), decorative
  work stops at CRITICAL (was SEVERE); camera/mic protection in `ThermalGovernor` is unchanged. No Room version
  change. Built (daily, MWDAT + verified-jni flags) and installed on the Fold R5GL75XFCXD on 2026-09-28 at the owner's request; NOT published. Codex remains build owner. **188 consumed.**

- September28 **187 / 1.5.21 built and installed by Claude** at the owner's request: measured rain outranks a dry
  weather code (`8caa4df`). Installed on Fold 09:18:11 MST over 186, PID alive, crash buffer unchanged; weather now reads
  DRIZZLE where it read CLOUDY. Receipt: `/Users/prog/Meowmba-Releases/rain187-20260928/README.md`.
  The device therefore already has versionCode 187 (apartment + rain, NO memory integration). Codex's earlier 187
  allocation for the memory integration was never installed and moves to 188 above. **187 consumed.**

- September28 **186 / 1.5.20 built and installed by Claude** at the owner's explicit request. Installed on Fold R5GL75XFCXD 07:58:22 MST over 185, firstInstallTime retained, PID alive, crash buffer unchanged:
  Desk landscape fill with the menu on top, a room-size slider, the moving room (time-of-day sky, weather, rare plane/UFO,
  blinking cursor, sticky note, lamp at night, arcade game) and the cat's habits; plus seven fixes found recording the
  Android demo videos. Branch `feature/desk-apartment` (`c2c5f04` + version bump); 3,148 tests, 0 failures/errors/skips;
  lint not run. Certificate/16KB alignment verified; runtime check vs 185: 163 protected entries, none missing, only the
  manual asset intentionally changed. Receipt: `/Users/prog/Meowmba-Releases/desk-life186-20260928/README.md`.
  **Integrator: merge `feature/desk-apartment` before building 187.** **186 consumed; next 187.**

- September26 **185 / 1.5.19 built and installed by Claude** at the owner's explicit request:
  Desk cat moves like a real cat (walk cycle, jumps, breathing, sit/loaf), a "visits other rooms" setting,
  living room redone (tower behind the couch, wider stereo and TV, a picture the user chooses), one moon
  (office window; clouds in the bedroom), and landscape-only bigger rooms (bookshelf, TV, bed).
  Branch `feature/desk-apartment` on `d2313da`; 3,128 tests, 0 failures/errors/skips; lint not run.
  Certificate/16KB alignment verified; runtime check vs 184: 163 protected entries, none missing, only the
  manual asset intentionally changed. Installed on Fold R5GL75XFCXD 23:45:27 MST over 184, firstInstallTime
  retained, PID alive, crash buffer unchanged. Receipt: `/Users/prog/Meowmba-Releases/desk-living185-20260926/README.md`.
  **Integrator: merge `feature/desk-apartment` before building 186, or 186 drops these features.**
  **185 consumed; next 186.**

- September26 **184 / 1.5.18 prepared by Codex** for Facebook destination selection and
  preview-loop lifecycle repair. Claude's `feature/desk-apartment` (183) has been merged
  into `feature/meow-flow-core`, preserving apartment features. Source `6bbc098`;
  3,117 tests,0 failures/errors,5 skips; lint0 errors/208 warnings/12 hints.
  Signature/16KB alignment/native-model preservation verified; manual asset intentionally
  updated. APK/receipt: Os `work/facebook-repair-184/`.
  Installed on Fold R5GL75XFCXD September26 at15:25:25 MST over183 with matching
  certificate/hash, original appId/firstInstallTime retained and Play package unchanged.
  Foreground PID survived79.1seconds; crash buffer unchanged. Receipt:
  Os `work/facebook-repair-184/validation/phone-install/README.md`.
  No public upload; live Facebook acceptance pending.
  See `docs/FACEBOOK_PREVIEW_184.md`. **184 consumed; next185.**

- September25 **183 / 1.5.17 built and installed by Claude** at the owner's explicit request:
  Meow's apartment (living room, office, bedroom), room media, Dev Studio projects on the Desk computer,
  MAME launch, 13 cat expressions, auto avatar while driving. Branch `feature/desk-apartment`
  (worktree `Meowmba-apartment-20260925`, based on 182's `80e28c1`); **not yet merged into
  feature/meow-flow-core — the integrator must merge it before building 184, or 184 drops these features.**
  3,103 JVM tests, 0 failures; lint not run. Signature/16KB OK; all 162 protected runtime entries match 182.
  SHA256 `da4281766e7278cddc6643a0dec58f513a676c3bcd7c508a7719f52c5dedb59c`. Installed on Fold
  R5GL75XFCXD at 22:44:50 MST in place; data retained; crash buffer unchanged. Receipt:
  `/Users/prog/Meowmba-Releases/apartment183-20260925/README.md`. Details: `docs/MEOW_PLACE_APARTMENT.md`
  (in the branch). **183 consumed; next184**.

- September25 **182 / 1.5.16 prepared by Codex**: native saved companion work/results,
  action checkpoints, explicit verified/handoff/unconfirmed receipts, Flow/Desk/chat review,
  owner-selected North Star evidence links and paused project/desktop continuation at
  uncertain outcomes. Interrupted actions are never replayed. Additive Room14 and
  compatible schema12/13 backup import. Based on181; charger/launcher, Development Studio,
  permissions, model configuration and native runtimes retained. Source
  `05c540b6a668f322949ee881ff0aa91051bb8b6b`. Full3,094 JVM tests: zero failures/errors,
  five existing skips; lint0errors/205warnings/12hints. Signature/16KB alignment pass;
  all162 protected181 runtime entries preserved. APK/receipt: Os `work/tasks-182/`;
  SHA256 `4b3f43a227231a4823660c1a7c930826fe30b78602298e157ed1656b489d5e31`.
  Native Robolectric component renders verified, including small-phone large text.
  Subsequently installed on Fold R5GL75XFCXD at21:54:58 MST September25 after explicit
  owner request:180→182 in place, firstInstallTime/appId retained, Play155 unchanged.
  Installed APK hash matches; PID22154 foreground/alive109.5seconds, crash buffer unchanged.
  Saved Desk and new work screen verified; final foreground screen Computer & task tools. UNKNOWN launch state
  because Home auto-resumed; no measured cold-start claim. Receipt: Os
  `work/tasks-182/validation/phone-install/README.md`. No emulator install, remote push,
  Play upload or real external action; real-service acceptance pending.
  Server browser/container workers and recurring website watches are future
  components. Details: `docs/WORK_RESULTS_182.md`. **182 consumed; next183**.

- September25 **181 / 1.5.15 prepared by Codex**: opt-in wireless-charger avatar
  (foreground or separately approved background opening, unlock deferral, silent
  notification fallback, coil-dropout debounce) and optional HOME-role onboarding.
  Source `a08b5e2970bf6fd483ff7e11cdb22f419e1a35dd`. Full3,070 JVM tests: zero
  failures/errors, five existing skips. Lint0errors/205warnings/12hints; three new
  warnings are KTX style suggestions. Signature/16KB alignment pass; all162
  protected180 runtime entries preserved. APK/receipt: Os `work/charging-181/`;
  SHA256 `9fa8b05f152eeaca2701bfce89dd378d0639b0ea5215f0d844eb0d4aef924e6d`.
  Owner explicitly chose **Skip emulator installation**; no emulator/phone install,
  native startup/UI check, default-home change, permission grant or robot action.
  Phone remains180. Actual charger/background/unlock acceptance pending. Android
  reports wireless power, not magnets/charger identity; process must be running.
  Details: `docs/CHARGING_LAUNCHER_181.md`. **181 consumed; next182**.

- September25 **180 / 1.5.14 prepared by Codex**: on-demand Development Studio;
  manual Claude Code/OpenCode, bounded per-file checkpoints/Keep/Undo, local website
  preview, approved npm/Gradle builds and verified APK transfer to Android confirmation.
  Reuses existing Ubuntu; no permission bypass or new always-on worker. App source
  `9d755427dd134c0d1d774d27286532c7b6cf3091`. Full3,054 JVM tests: zero failures,
  five existing skips;18 Python tests and one emulator UI test pass. Lint0errors,
  202warnings,12hints. Signature/16KB/runtime checks pass, all161 protected entries
  preserved. Optimized emulator COLD849ms, PID5955 alive86seconds; foreground/crash
  checks pass. No speed/battery improvement claim. Receipt/APK: Os `work/development-180/`;
  SHA256 `11f16a3ccb4c64ee38e2a927ec20cda66b4829c169c58988ca33c11d7e95aadd`.
  Details/limits: `docs/DEVELOPMENT_STUDIO_180.md`. Automatic Android SDK provisioning,
  physical Ubuntu/Claude/build acceptance remain pending. Owner subsequently authorized
  installation: Fold R5GL75XFCXD updated177→180 at19:51:44 MST, retaining firstInstallTime
  and appId; Play155 unchanged. Installed certificate/all161 runtime entries verified.
  Fresh PID26400 alive65seconds, crash buffer unchanged, foreground/saved-content UI
  verified. Android reported UNKNOWN launch state (Home auto-restarted), not COLD.
  Phone receipt: Os `work/development-180/validation/phone-install/README.md`.
  Termux/Ubuntu/Claude setup still pending; no permission grants or robot commands.
  **180 consumed; next181**. Cozmo/LOOI remain separate, unchanged.

- September 25: **179 / 1.5.13 built by Codex — Cozmo repair and original gestures**.
  Fixes confirmed WakeWordService uninitialized-storage crash; isolates bounded camera
  decoding from robot UDP; adds animation ticks, initialization/ACK health, calibration,
  full-range presets, half lift, curves, six finite gestures and sensor-only face reactions.
  **75 focused JVM tests pass**, no failures/errors/skips; two emulator UI tests pass.
  Daily lint: zero errors,200warnings,12hints. Signature/16KB alignment pass; all161
  protected entries preserved against178 (only generated ART profiles differ).
  Optimized emulator COLD1822ms, PID3311 foreground/alive73seconds; crash buffer unchanged.
  APK/receipt: Os `work/cozmo-repair-179/`, SHA256
  `8c8c7488ff45847e3f9623da28ec784e336f9592da34338e7fd5e2625824eb81`.
  Details: `docs/COZMO_REPAIR_179.md`. NOT full official-app parity. Actual motor failure
  root cause and real camera/movement acceptance remain unproven pending owner testing.
  No phone install or physical motor command; Fold remains177. **179 consumed; next180**.

- September 25: **178 / 1.5.12 built by Codex — guided access and Flow comments**.
  Calendar read/write, Gmail, notification and on-screen-tool guidance with live access
  rechecks; Comment on main/more/attention/interrupt cards and Android notifications.
  Notes persist on their Flow; explicit cancellation submissions target provider ID +
  occurrence, verify the write and retire only that reminder. No new Gmail OAuth integration.
  Full JVM: **3,043 tests, zero failures/errors, five existing skips**; eight emulator UI
  checks pass. Daily lint: zero errors, 200 warnings, 12 hints (three new UseKtx style hints
  reported as warnings). Matching signature/16KB alignment and all161 runtime entries
  preserved against177. Optimized emulator COLD launch2485ms, samePID31865 foreground
  after36seconds, crash buffer unchanged; actual guide and comment deep link checked.
  APK/receipt: Os `work/flow-access-178/`, SHA256
  `fb1fe9472bcea72b45b0956ab90e626fc05b734eb34693cdca30fb48f3a9647c`.
  Details: `docs/FLOW_COMMENTS_178.md`. No phone install, real-calendar mutation,
  permission grant or account sign-in during testing. Fold last verified177.
  **178 consumed; next unallocated code179**.

- September 25: **177 / 1.5.11 built by Codex — on-request store/deal assistance**.
  On-request store identification from existing place sources; no arrival announcements.
  Named-store deal search with source links, explicit uncertainty, and a one-shot human-request
  guard. Sharing inferred store names with search engines awaits owner consent; the default
  asks for a named deal request first. No new place provider or inferred-name sharing authorized.
  Final JVM: **3,028 tests, zero failures/errors, five existing skips**. Six emulator UI
  checks pass, including saved-answer/source links after task status. Lint: zero errors,
  197 warnings, 12 hints. All 161 protected runtime entries preserved against 176,
  matching signature and 16 KB alignment; optimized cold launch 536 ms, remains foreground,
  crash buffer unchanged. APK/evidence: Os `work/store-177/`; SHA-256
  `dce9f1db761e6c8b214ff5c2129763620b740a6e2f591019039d8b1d95caaa3e`.
  Owner subsequently authorized installation: upgraded Fold R5GL75XFCXD from173 to177 in place
  at2026-09-25 13:49:34 MST. Same certificate, all160 installed-runtime entries retained,
  original firstInstallTime/appId preserved; Play155 unchanged. Home UI verified and fresh
  process survived62seconds with empty crash buffer. Phone launches reported WARM, not COLD;
  another app took foreground during observation, so sustained foreground is not established.
  Details/limits and phone receipt: `docs/SHOPPING_177.md`, Os `work/store-177/validation/phone-install/`.
  **177 consumed; next unallocated code 178**.

- September 25: **176 / 1.5.10 built by Codex for the owner-requested chat/behavior audit**.
  Classifier-vs-chat guards, nonhuman ASR filtering with human-sound observations, quiet and
  driving/media speech rules, emoji/GPS speech cleanup, avatar quick launch, app completion,
  verified calendar cancellation, and honest music/navigation/skill action receipts.
  Final full JVM: **2,997 tests, zero failures/errors, five skips**; four emulator UI tests pass.
  Full daily lint: zero errors, 197 warnings, 12 hints. Signature, 16 KB alignment and runtime
  comparison with 175 pass (161 entries checked, only generated ART profiles changed).
  Optimized emulator cold launch passes (3,132 ms), stays foreground, crash buffer unchanged.
  APK/evidence: Os workspace `work/behavior-176/`; SHA-256
  `775e6b76bc2e5db1531236ad56f84831822681c79b5e139a4641df9d1beec1e3`.
  No phone install or actual calendar mutation; Fold remains verified preview173. Automatic
  public map downloads/nearest-intersection data require additional location-sharing approval.
  Audit/limits: `docs/BEHAVIOR_176.md`. **176 consumed; next unallocated code 177**.

- September 25: **175 / 1.5.9 built by Codex for robot separation**. Cozmo and LOOI
  have separate menu entries, panels, tool owners and command routing; no connected-robot
  substitution. 124 focused JVM tests and two emulator UI tests pass; full daily lint:
  zero errors, 197 warnings, 12 hints. All 161 protected entries unchanged from 174;
  matching preview certificate and 16 KB alignment verified. Optimized emulator cold
  launch passes (1,279 ms), remains foreground, no new crash-buffer entries. APK/evidence:
  Os workspace `work/cozmo-175/`; SHA-256
  `42f2cb0e4323ca23db38a8d624d31451c4182713eb5f4eb398cf26c91519ec7b`.
  No phone install or physical robot test. **175 consumed; next unallocated code 176**.

- September 25: **174 / 1.5.8 built by Codex for Cozmo integration** in this checkout.
  Full JVM run: 2,958 tests, zero failures/errors, five skips; final targeted run: 14 pass;
  emulator UI test passes; full daily lint zero errors. Matching signature, 16 KB alignment
  and all 160 protected runtime entries verified against 173. APK/evidence in the Os
  workspace at `work/cozmo-174/`. Cozmo firmware 2381 only; hardware tests outstanding.
  No phone install; 173 is the last verified Fold install. **174 consumed; next 175**.
  Feature/validation receipt: `docs/COZMO_INTEGRATION.md`.

## Current recovery

> **Codex, start here:** `docs/HANDOFF_CLAUDE_2026-09-23.md` — state after builds 158–166, how they
> were built (full runtime flags), owner decisions, open work in priority order, and gotchas.
> Codex is the build owner; **180 is the last verified Fold install, including Development Studio and the earlier179 Cozmo repair**; next unallocated code **181**. Cozmo and LOOI remain separate. Current Desk update:
> `docs/LIVING_DESK_173.md`; preceding review: `docs/REVIEW_AND_ACTIONS_172.md`.

- September 25 **1.5.7/code 173** (Codex), based on 172 / `a1f583c`, source `8d5fc87`: Living Desk.
  Adds irregular room/floor movement, grooming/stretching, computer/letter/board performances,
  an in-Desk Ask Meow action and scrollable real-reply bubbles. The optimized APK and emulator
  Ask/reply smoke check pass. Full daily lint: zero errors, 197 warnings, 12 hints. Full JVM run:
  2,940 pass, five existing skips, one Robolectric dialog-idling scenario moved unchanged to
  Android instrumentation (passes); the remaining three Desk UI checks pass on rerun.
  Same preview signature, 16 KB alignment, all 160 protected entries and 88 native libraries
  verified against 172. Installed in place on Fold8 / SM-F971U / R5GL75XFCXD at 00:21
  America/Phoenix after reconnection. Original first-install time retained, launch passes,
  no new app crash-buffer entries, separate Play 155 unchanged. App data/settings kept.
  Install the archived 173 rather than rebuilding another artifact with that number.
  Receipt: `/Users/prog/Meowmba-Releases/living-desk173-20260924/README.md`.
  Usage: `docs/LIVING_DESK_173.md`.
- September 24 **1.5.6/code 172** (Codex), based on Claude's `1aba37b`, installed in place on
  Fold8 / SM-F971U / R5GL75XFCXD at 22:09 America/Phoenix. Adds the first North Star layer,
  visible QR scanning, one-shot Ask Meow screen help, honest action-result wording, and original
  notification conversation destinations. Claude's Flow/Desk design is preserved. Room 12→13
  migration and older backup compatibility are covered. 2,936 JVM tests (zero failures/errors,
  five existing skips), four passing device tests, lint zero errors (197 warnings, 12 hints).
  Same signature, 16 KB alignment and all 156 protected native/model entries verified.
  First-install time retained, preview launches with no new crash entry, separate Play 155 retained.
  Receipt: `/Users/prog/Meowmba-Releases/northstar172-20260924/README.md`. Next code: **173**.
- September 24 Claude builds **169–171**: leader lines on Flow items (`5dc0ed0`), permanent line
  into the ask box and moving dots (`927eacb`), top-bar Needs attention panel (`1aba37b`). Build 171
  / 1.5.5 was on the Fold before this update; these additions remain in 172.

- September 24 **1.5.2/code 168** (Claude, owner request), source `cc983fb`, installed in place on
  SM-F971U: music check only while the mic is live (playing yes/no; song/artist only when asked; no
  auto-dance; lyrics without the wake phrase ignored); Flow notices package texts/emails. 2,911 tests,
  lint clean. Receipt: `/Users/prog/Meowmba-Releases/fix168-20260924/`.
- September 24 **1.5.1/code 167** (Claude): fixes the startup crash loop when music plays at launch
  (it made Android drop Meow as default home); home-chooser button always available.
  Receipt: `/Users/prog/Meowmba-Releases/fix167-20260924/`.
- Owner vision 2026-09-24: `docs/MEOW_NORTH_STAR.md` ("4th dimension" goal layer) — first implementation in 172; broader integrations remain future work.

- September 23 **1.5.0/code 166** (Claude, owner request), installed on SM-F971U in place: idle
  avatar (Lumen default / Orb / off, 1–10 min) over Flow and the Desk with new Flow items crossing
  the screen as a ribbon; swipes everywhere: left-to-right Flow, right-to-left chat. 2,908 tests,
  lint clean. Receipt: `/Users/prog/Meowmba-Releases/context166-20260923/`. Next number: 167.
- September 23 **1.4.9/code 165** (Claude): dock grip keeps one size and place open or closed.
  Receipt: `/Users/prog/Meowmba-Releases/context165-20260923/`.
- September 23 **1.4.8/code 164** (Claude, owner request): the side dock now behaves like the Lumen
  screen's edge tab — rests hidden as a grip on the edge, tap or swipe inward to open, swipe outward
  to tuck away, drag to move or switch edges. Receipt: `/Users/prog/Meowmba-Releases/context164-20260923/`.
  Next number: 165.
- September 23 **1.4.7/code 163** installed by Claude (owner request) on SM-F971U, in place.
  Desk clock opens Android Clock; drawers are app/tool folders; movable 6-slot dock on Flow and Desk;
  speech default IMPORTANT (owner: proactive more than reactive). Full runtime kept. 2,905 tests,
  lint clean. Receipt: `/Users/prog/Meowmba-Releases/context163-20260923/README.md`.
- Builds 163–166 were built while git was blocked by an unaccepted Xcode licence; the owner
  accepted it and the source is committed as `7cc8544` on `feature/meow-flow-core`.
- September 23 **1.4.6/code 162** installed by Claude (owner request) on SM-F971U, in place.
  Talks less (SpeechMode), heading-home navigation, mic switch in Flow/Desk, + for camera/photo/
  file and paste with on-phone reading and package tracking. Full runtime kept (86+1 libs).
  2,902 tests, lint clean. Receipt: `/Users/prog/Meowmba-Releases/context162-20260923/README.md`.
  Next number: 163.

- September 23 **1.4.5/code 161** installed by Claude at the owner's request on SM-F971U /
  R5GL75XFCXD at 15:55 (in place, data kept). Built like 159/160 (MWDAT enabled, verified-jni);
  all 86 native libraries of 160 plus Laya's `libonnxruntime4j_jni.so`; same certificate.
  Adds Laya on-device (budgeted), Meow context C1–C2, Room v12. 2,897 tests, lint clean.
  Receipt: `/Users/prog/Meowmba-Releases/context161-20260923/README.md`. Next number: 162.

- **Correction (Claude, 2026-09-23 15:10):** after Codex's 160 install on SM-F971U /
  R5GL75XFCXD (12:39), Claude installed five further owner-requested daily builds on that
  phone, all carrying version code **160** from the shared build file: `5154ebd` (heat/mic/
  dateline), `763d535` (Flow/Desk swipes), and three `c197e39` Laya builds. The earlier notes
  below that call these "158 reinstalls" are wrong. They were in-place updates (data kept).
  The phone now runs `c197e39` (Laya on-device; owner asked for it after the 159 note).
  These APKs are not archived artifacts. Claude has stopped installing; Codex should cut the
  next number (161) from the current branch head and own installs again.

- September 23 backup/update release **1.4.4/code 160**, app source `813bcc5`, installed
  in place on Fold8 / SM-F971U / R5GL75XFCXD at12:39 America/Phoenix. Adds encrypted
  backups/restores, verified in-place update and guarded cross-edition cleanup.
  2,887 tests: zero failures/errors, five expected no-SDK skips; daily lint zero
  errors. Signature, alignment and full runtime parity pass. Data/firstInstallTime
  retained; backup warning verified and no new crashes after installation. Other
  Play edition155 retained. Historical159 music-startup initialization crash remains
  a separate follow-up. Receipt, limits and workflow: `docs/SAFE_UPDATES_160.md`.
  Codex owns builds;160 is consumed. Laya remains deferred. Server SSH unavailable.

- September 23 Fold update: **1.4.3/code 159** installed in place on SM-F966U1 /
  RFCY61NJEVM at 10:40 America/Phoenix. Source `a72c62b` changes release metadata only
  from the requested `a31ac43`; Claude's application source is identical. The full
  runtime bundle, app data and installed settings are retained. Signature/alignment/
  runtime checks and cold launch pass; no preview crash entries. Receipt:
  `docs/FLOW_159_2026-09-23.md`. Codex is the sole build, version and installation
  owner going forward; do not reuse 158 or 159 for another installable artifact.
  The owner chose **local rules for now**; Laya downloads and implementation remain deferred.

- September 23 Flow/Desk repair: **1.4.2/code 158**, source `639ebd3` + version bump, daily
  variant, owner-requested fresh install on a second phone SM-F971U / R5GL75XFCXD (API 37) at
  09:05 America/Phoenix. The Fold (RFCY61NJEVM) was still on 157 at that checkpoint. Full suite 2,846 tests, zero
  failures; lint clean. Receipt: `docs/FLOW_DESK_REPAIR_2026-09-23.md`. Reinstalled 09:23 from
  `a854481` (desk fills inner/cover screens, no bottom band; setup rows off the desk; 2,850 tests).
  Reinstalled from `5154ebd`: motion sensor off in Flow/Desk, people-only notifications,
  Flow mic enables mic + shows voice state, wrapping dateline (2,888 tests, lint clean).
  Heat finding on this phone: app ≈3% of one core over 28 min on screen; the hot period
  (skin 38.0°C) had Wi-Fi off, weak signal and cellular high-TX power. Laya download was
  blocked by the session's auto-mode safety check; needs the owner to allow it.

- September 23 UI checkpoint: **1.4.1/code 157**, source `c9f74c9`, installed in place
  on the Fold. Visible Menu/Settings and object-specific Desk sheets restored; approved
  fonts bundled. Owner requested a stop after launch verification. Physical acceptance
  is unfinished. Four new touch-navigation tests pass; full 2,837-test run has four stale
  menu-label assertions to update (all other tests pass; five skips). Daily lint has zero
  errors. Runtime/signature/alignment checks pass. Resume from
  `docs/UI_157_CHECKPOINT_2026-09-23.md`. No server push or public release.

- September 23 installed preview: **1.4.0/code 156**, source `1ef898c`, integrates
  Claude's Flow/Desk branch and reviewed idle-performance work. Room-backed tasks,
  calendar/delivery corrections, hidden-scene cleanup and hardware-aware setup are wired.
  Flow currently uses local rules; Laya/Jev are not connected. Light phones start with
  Classic/still/15 FPS defaults and skip Computer setup. Explicit settings are preserved.
  Daily preview is shrunk/non-debuggable with the existing preview certificate; separate
  Play package and website remain unchanged. Full 2,833 tests: zero failures/errors,
  five skips; final UI-focused 117 pass; daily lint zero errors (172 warnings remain).
  Installed in place on SM-F966U1 / RFCY61NJEVM, preserving data. Native/model entries
  match verified 155 exactly. Receipt: `docs/FLOW_156_REVIEW_2026-09-23.md`.
  Flow/Desk each passed ten-minute foreground runs: 0.96% / 1.78% of one CPU core,
  all 21 thermal samples per run normal (status 0), USB powered, sensors off.
  APK/archive: `/Users/prog/Meowmba-Releases/flow156-20260923/`.
  Meow server SSH was unavailable; do not assume a server push happened.

- September 21 latest companion update: signed release **1.3.99/code 155** installed
  in place on the connected Fold at 02:00 America/Phoenix; app data preserved.
  Adds 32 prop scenes and 16 character personalities, repairs chat voice controls,
  moves privacy controls clear of chat/text, and keeps Needle 3 status in Models.
  Nearby-song identification is implemented with explicit 10-second capture and
  AudD setup; live matching still needs a configured key and physical acceptance.
  Final suite: 403 suites / 2,730 tests, zero failures/errors, five skips. Release
  assembly and lint pass with zero lint errors. Phone checks confirm code 155,
  clear chat controls, song setup navigation, sensors still off, and no Meow crash
  buffer entries. All 149 protected runtime/model entries match build 153.
  Receipt: docs/COMPANION_155_2026-09-21.md. Play/website remain on release 152.

- September 21 companion update: signed release 1.3.97/code 153 installed in place
  on the connected Fold. Selected-avatar restoration, sporadic idle visits, Needle 3
  status, speech echo protection, and optional music awareness/dancing are implemented.
  Full suite: 2,717 tests, zero failures/errors, five skips; release lint zero errors.
  Home UI confirms Needle ready/on-demand with sensors off. Physical audio/music
  acceptance remains; metadata requires user-enabled Music access. Receipt:
  docs/COMPANION_153_2026-09-21.md. Play/website remain on the previous release.

- Integrator: Codex, this recovery task. One owner performs builds and phone installs.
- Integration branch: `feature/meow-flow-core`.
- Integration checkout: `/Users/prog/AndroidStudioProjects/Meowmba-flow-20260922`.
- Earlier recovery checkout: `/Users/prog/AndroidStudioProjects/Meowmba-repair-20260914`.
- Baseline: `5efed2f`, version 1.3.82/code 138. Historical evidence is dated September 15.
- Verified original APK: `/Users/prog/Meowmba-Releases/idle136-20260915/meow-preview138.apk`.
- Grok source to recover: `529cc69`, preserved as `recovery/grok-lumen` remote-tracking ref.
- Previous status: preview152 (1.3.96) installed in place; original debug certificate and data
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
- Follow-up: default en-US Play short/full descriptions updated and submitted with 152;
  Google required restarting review. All three items now show “Changes in review”.
  Receipt/copy: docs/store/PLAY_LISTING_152.md. Current 23-page user manual source:
  docs/manual/USER_MANUAL_152.md; PDF updated in Documents/Meowmba-User-Manual.pdf,
  with the original build-124 PDF preserved in the play152 archive.
- September 20 website media: eight captioned screenshots/render previews and two
  short animation clips are published at the Meow page's #mx-gallery. Desktop/mobile
  layout and both clip playbacks verified. Receipt and complete editable embed:
  docs/youtube/weebly/MEOW_MEDIA_GALLERY_2026-09-20.md.
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
