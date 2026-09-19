# Meow — installed-build audit and remaining work

Date: September 19, 2026. Owner request: test the app and provide the whole work list.
This is an acceptance/backlog report, not authorization to publish, enable monitoring,
operate a robot, post to accounts or replace the user's data. Historical plan checkboxes
are not reliable current status; use the latest evidence below and BUILD_MASTER.md.

## Latest follow-up — preview147

The audit below records145; it is not the latest installed-build status. Installed147
(source09db18d) repairs clock/Reflex stacking, extends known-tool result discovery and
adds verified local result previews. Full suite2,646 tests, five skipped, zero failures;
seven phone checks pass. A real UI task also passes exact-file approval → visible preview
→ Files search → Chrome rendering. Three-minute idle battery temperature fell34.7→34.5°C,
thermal1→0, USB connected, but CPU remains54.53% of one core and frame pacing needs work.
See [RESULTS_IDLE_147_PASS_DOWN.md](RESULTS_IDLE_147_PASS_DOWN.md) for evidence and limits.
Large transfers, project/session integration, sustained thermal and fresh-device acceptance
are not completed by this batch. No production release or monitoring enablement.

## Original145 build and scope

- Phone: Samsung SM-F966U1, serial RFCY61NJEVM. This is not the earlier Flip6.
- Installed preview: com.example.meowmba, 1.3.89/code145, app source6b6cab3.
- Canonical checkout: /Users/prog/AndroidStudioProjects/Meowmba-repair-20260914.
- This audit changes tests/documentation only. No replacement app, Play upload, password
  reset, Termux deletion, account action or robot actuation.
- Tests preserve appearance/preferences and leave mic, camera, HUB and personal monitoring off.
- Fresh test results are recorded below. Prior evidence is explicitly dated.

## Findings that are already established

1. The repaired Ubuntu setup now reuses modern PRoot-Distro containers instead of
   trying to reinstall an active Ubuntu. The existing authenticated OpenCode1.18.31
   connection at4097 works; previous4096 server and data were not removed.
2. The current home screen visibly overlaps the clock and Reflex badge. A cold-launch
   screenshot taken during this audit confirms it; this is a real UI defect, not a theory.
3. Computer's automatic result discovery only accepts completed `write`/`edit` tools
   with `filePath`. Files created through other tools may not appear automatically.
   The manual relative-path export is an escape hatch, not a finished results experience.
4. Computer file export uses a 2MB bounded JSON response; its UI honestly advertises
   approximately1.5MB exports. Large images, videos and models need a separate bounded,
   streaming transfer design, not merely removal of the safety limit.
5. Physical robot-base control is intentionally quarantined in current source. The old
   guessed zero-speed packet could itself cause movement. Do not label this operational.
6. Initial Android thermal status was0 (no thermal throttling); current HAL battery34.6°C,
   skin37.5°C. At the end it was1 (LIGHT), battery35.6°C and cached skin39.9°C, with
   USB connected. The mixed UI/test session is not a controlled attribution experiment;
   it does not establish local-model/all-day thermal safety or identify one sole cause.

## Fresh acceptance results

- Fresh full regression rerun:383 suites,2,615 tests, zero failures/errors, five skipped
  (2,610 passed). Gradle successful in2m24s. This is automated coverage, not proof every
  external account/hardware/provider workflow works.
- Six live checks passed in the broader run: authenticated Ubuntu terminal; exact
  no-tools agent response; appearance/weather/home-controls privacy preservation;
  guided Computer setup/recovery and optional modes staying off; isolated onboarding
  animal selection/automatic fit/manual adjustment; paused renderer bounded drawing.
- Productive Computer test subsequently passed in11.662s: create unique synthetic
  workspace file, require one-time exact-target edit approval, retrieve its actual
  text, then correctly recall its random marker in a same-session no-tools follow-up.
  This proves one useful bounded task, not multi-hour autonomy or all-model reliability.
- Test harness corrections are retained in the logs: initial JUnit non-void declaration;
  then conservative rejection of OpenCode's root-relative permission spelling. Final
  matcher requires exact absolute metadata target AND exact allowed pattern, with no
  wildcard. Denied probes were stopped; no broad permission was granted.
- The strict first content check also caught a missing requested final newline. Final
  test checks actual text content allowing only trailing newline differences; diagnostic
  evidence shows the model's write input itself omitted the newline and the exported
  text matched that input. Do not claim arbitrary byte-perfect artifact compliance or
  blame export corruption from this observation. Exact-format tasks need stronger checking.
- Two uniquely named synthetic QA text files remain in /root/MeowProjects; no personal
  files were read/deleted. Successful test file:
  meow-qa-8e78eae23fe24321b76599db4cb5cef7.txt. Test sessions were stopped afterward.
- App was cold-launched afterward;145 remains installed. No Termux/server force-stop,
  replacement app install, Play submission or user-data reset. Only the test harness
  was installed. Mic/camera and the optional monitoring modes remain off.
- Evidence archive: /Users/prog/Meowmba-Releases/ubuntu145-20260919/audit-5Fanjn/
  (unit XML/logs, device logs including failed intermediate probes, home screenshot).
  A current weather forecast, real voice recording, external viewer opening, robot,
  glasses, HUB capture and fresh-device provisioning were not tested by these checks.

## Existing capabilities: do not rebuild them blindly

- Provider cards, explicit cloud consent/skip, local library, weather location selection,
  compact appearance controls, Auto page and per-form selection already have implementations.
- Needle3 native routing and weights passed a real phone smoke test on preview139. Needle
  is a tool router, not a replacement conversational model. More updater coverage is pending.
- Preview143 produced, validated, saved and equipped a real configured-free-route prop in
  76.446s. Preview142 BALANCED avatar creation passed in96s. Other requests had timed out;
  these successes are not proof of universal generation reliability or exact photo-to-3D.
- Projects/checklists/artifacts, a business workflow engine and OpenCode sessions exist,
  but their user experience and execution state are not yet one unified project system.
- A real manager + two specialist business workflow passed on132 (104.294s, synthetic
  CSV inventory/accounting task, explicit report-write approval). Execution is bounded
  and sequential, not an unrestricted autonomous company or universal concurrent team.
- The full neural graph server was paired and exercised on the phone on September14.
  It is not accurate to call pairing unimplemented. Current-build reconnection, endurance,
  useful adaptive behavior and comparative quality still need testing.
- Manual embedded terminal is an authenticated Ubuntu PTY now, not merely Termux opening
  externally. An authenticated terminal does not prove every productive agent workflow.

## Recommended sequence and complete outstanding work list

Legend: **Fix** = observed defect or concrete implementation gap; **Finish** = partial/new
implementation; **Validate** = existing implementation needs stronger real-world acceptance.
P0 should precede another broad feature expansion. This list is grouped, not a claim that
every listed feature is wholly absent.

### P0 — usable, trustworthy core

1. **Fix — visible results:** discover output from supported creation tools, not only
   write/edit; show verified result cards, thumbnails, paths and Open/Share actions.
2. **Finish — large artifacts:** cancellable bounded streaming/export for images, PDFs,
   videos and models; safe names, MIME handling, partial-transfer recovery and checksums.
3. **Finish — one project workspace:** connect chat, Computer, business agents, Files and
   generated media to the same project, plan, progress, approvals and durable output list.
4. **Validate — productive Computer work:** real file task, approvals, deny, cancel,
   questions, follow-up context, result viewing and interrupted-task recovery; expand beyond
   one successful synthetic file to representative multi-file work.
5. **Validate — setup from a clean device:** official supported Termux install, Android
   permission handoff, Ubuntu/OpenCode download, private password, verified readiness.
   Use a spare/test device; never erase this user's installation to simulate first run.
6. **Validate — setup recovery/lifecycle:** interrupted downloads, low storage, wrong
   password, occupied port, network loss, backgrounding, app restart and phone reboot;
   preserve existing Ubuntu and keep recovery understandable.
7. **Validate — model onboarding:** OpenRouter link/instructions, paste/verify/save,
   actual inference, saved-key restart, explicit local/cloud choice and clear skip;
   provider failures must not trap users or silently opt them into cloud.
8. **Validate — downloads:** find existing compatible files, resume/deduplicate downloads,
   verify integrity/runtime compatibility, show completion and actionable load failures.
9. **Validate — heat/battery:** controlled idle, local chat, voice, camera, neural and
   Computer workloads over longer sessions; profile before optimizing, preserve visuals,
   keep Android thermal safeguards, test older/lower-memory phones.
10. **Fix — home layout:** clock/Reflex collision, fold/rotation/large-text spacing,
    reachable controls and clear mic/camera state without needless duplicate clutter.
11. **Validate — chat usability:** compact retractable composer, keyboard/insets,
    readable final answers, streamed-text scroll behavior and linked result previews.
12. **Validate — release-start reliability:** cold start, process death, background return,
    provider errors and offline state without crashes, frozen avatars or repeated greetings.

### P1 — useful agents and reliable integrations

13. **Validate — intent and context:** conversational follow-ups, misheard app names,
    ambiguous commands, ordinary cat/dog conversation and explicit clarification;
    do not create files/videos or launch apps from unrelated chatter.
14. **Validate — phone-app workflows:** separate acceptance cases for Facebook, LinkedIn,
    Reddit, Instagram, X, ChatGPT and Gemini; reach the intended screen, draft accurately,
    verify results and require approval before public posting/sending. No universal claim.
15. **Finish — integration setup:** distinguish real connectors from app launchers/setup
    templates, discover actual server tools, scoped login/token setup and revocation;
    installed apps are not automatically MCP servers.
16. **Finish — broader MCP auth:** browser OAuth/account linking and server compatibility
    where supported; do not promise remote stdio execution merely from a service icon.
17. **Validate — business teams:** current-build manager/specialist routing, bounded task
    ownership, delegation failures, shared artifacts and reliable completion evidence.
18. **Finish — long-running jobs:** clear waiting/running/blocked states, checkpoints,
    resumable schedules, notifications, deduplication and recovery without replaying actions.
19. **Finish — email/new-user monitoring:** real configured sources, least-privilege access,
    incoming-event tracking, permissioned replies, outbox receipts and duplicate prevention.
20. **Validate — background constraints:** Android battery saver, Doze, network changes,
    force-stop and reboot. Offer honest delayed/background status; do not promise desktop24/7.
21. **Finish — desktop productivity:** re-test pairing and supported app/browser actions,
    file transfer and Blender work; expand bounded adapters and prove each advertised task.
    Logic integration needs full-project/export acceptance, not just plugin parameter tests.

### P1 — voice, models and everyday assistance

22. **Validate — speech accuracy:** consented recordings across quiet/noisy/TV conditions,
    names/apps, partial versus final transcript, Whisper selection and fallback behavior.
23. **Finish — natural turn taking:** interruption, echo suppression, end-of-turn latency,
    silence handling, missed-hearing repair and no repeated hello/battery commentary.
24. **Validate — four local voices:** two women/two men, offline availability, pronunciation,
    streaming latency and sustained heat on target phones; human-indistinguishable quality
    is not established and must not be advertised as a guarantee.
25. **Finish — model maintenance:** extend current local-model checksum/status checks to
    speech/image assets, known safe updates and rollback; custom models need honest unknown
    status. Benchmark alternatives such as MiniCPM before replacing a working model.
26. **Validate — weather/navigation:** named city, permission denied, live location,
    units/staleness/network errors and correct navigation destination; no fabricated readings.

### P2 — avatars, creation and files

27. **Validate — natural motion:** smooth eyes AND whole-character travel, quick face
    movement, occlusion/lost-target behavior, idle drift and varied nonrepeating performances.
28. **Validate — all forms:** all saved/named forms as main avatar, all Lumen random forms,
    timing/randomness sliders, reset-to-normal voice intent, Jarvis transformation and color.
29. **Validate — orb:** continuous animation across lifecycle/thermal states, diffuse glow,
    depth, gyro perspective and hard-shake scattering without permanent frozen state.
30. **Finish/validate — creative feedback:** clear generation stages, free-provider queue
    delay, timeout/retry/cancel, schema repair and verified saved/equipped output for all
    Vending lanes, props, scenes and backgrounds.
31. **Validate — reference approval:** ask whether an example is wanted, preview/refine/
    accept, no-preview background flow and explicit photo/vision consent; realistic capability
    disclosure rather than promising exact reconstruction from a single picture.
32. **Finish — richer creative pipeline:** approved concept-to-computer asset construction,
    export/import/rig/animation checks and a reusable safe asset format. The earlier Blender
    cat remains paused/excluded until the user explicitly resumes it; the later full fly is
    a separate, already implemented feature.
33. **Validate — Files:** project/image/video/document indexing, search, organize, preview,
    export and cleanup suggestions with recoverable deletion and explicit confirmation.
34. **Validate — facts and scenes:** persistent repeat filtering, factual quality, context-
    appropriate props/backgrounds and variety. Absolute semantic “never repeat” is not proven.

### P2 — physical devices and optional modes

35. **Validate — Auto/drive mode:** passenger/simulator hard stops, turns, filtering,
    navigation interaction and exit behavior; no driver interaction while moving.
36. **Validate — foldable cover:** actual Flip6 cover screen, permission flow, folds,
    rotation and battery behavior. A toggle is not an unrestricted OS cover-home replacement.
37. **Validate — Meta glasses:** complete SDK-containing build, developer pairing,
    permissions, audio/video and disconnect on real glasses; do not infer hardware success
    from a configured GitHub token or SDK presence.
38. **Finish — robot safety first:** documented/verified stop, attachment/loss detection,
    transport disconnect and bench acceptance with wheels safely restrained. Only then
    consider guarded physical tracking/motion. Keep current quarantine until passed.
39. **Validate — Bluetooth/USB devices:** supported-device inventory, permission/reattach
    flows and protocol-specific capabilities; arbitrary devices cannot be controlled by name.
40. **Validate — HUB desktop-helper path:** explicit start/stop, pairing, screenshots,
    accessible controls, reconnect and private-data handling on the actual attached computer.
41. **Finish — capture hardware path if wanted:** compatible UVC/HDMI capture, latency,
    permissions and bounded movie/game commentary. An ordinary USB-C hub supplies no automatic
    access to a computer's screen or controls.

### P2 — personal companion and neural system

42. **Validate — consented cue reactions:** off by default, camera/audio stop/revocation,
    local attention and dance/expression cues; do not diagnose or assert a person's emotions.
43. **Finish — local personalization:** turn encrypted interview answers into useful
    local-only preferences without inserting them into cloud chats, logs or tool prompts.
44. **Validate — privacy controls:** local deletion/export, backup/restore behavior,
    two deliberate red cloud approvals, clear selected data/destination and revocation;
    no claim of an impossible “can never be compromised” guarantee.
45. **Validate — neural pairing:** current145 Meow/server reconnection, stale-input
    rejection, local fallback, lifecycle authority and low-power/background behavior.
46. **Finish/validate — neural usefulness:** repeatable replay/ablation benchmarks against
    simpler behavior, bounded adaptation/reset/forget, longer latency/heat tests. A large
    biological connectivity graph is not proof of superior intelligence or a complete brain.

### P3 — quality, coordination and publication

47. **Validate — accessibility/polish:** font scaling, contrast, screen reader labels,
    light/dark themes, keyboard, fold layouts and consistent grouped settings/navigation.
48. **Finish — support diagnostics:** user-controlled redacted bug report with build,
    readiness, failure phase and useful recovery steps; no raw credentials or giant stack
    traces covering the main screen.
49. **Finish — automated acceptance matrix:** deterministic fixtures plus opt-in real
    provider/device tests; maintain current evidence per feature/provider/device rather than
    treating all old green tests as production signoff.
50. **Maintain — one build authority:** one integrator, exact source/version/signature
    receipts, protected runtime checks, private source destinations, immutable known-good
    builds and recoverable archival; reconcile old plans without deleting evidence.
51. **Validate — broader testing:** spare low-end phone, Flip6, Fold, offline/poor-network,
    accessibility users and longer daily usage. Collect reproducible issues and response times.
52. **Finish — release readiness:** signed/minified release acceptance, runtime assets,
    permissions/data-safety/store policy review, release notes, accurate website/storefront,
    tester instructions and supported-feature matrix. Check current Play status/version
    before a separately authorized upload; preview145 is not a Play release.

## Next work recommendation

First deliver a **complete project-to-result loop** (items1–4), fix the observed home
layout overlap, and run clean-device onboarding/lifecycle acceptance (5–8,12).
Then tackle measured thermal/voice reliability (9,22–24) before expanding autonomous
business/HUB/robot features. Existing features need an understandable, verifiable result
experience more urgently than the app needs another large catalog of options.

## Evidence index

- BUILD_MASTER.md — latest source/install authority and145 setup receipts.
- docs/AGENT_PLAN.md — dated implementation checkpoints, not a flat unfinished checklist.
- docs/neural/SERVER_ACCEPTANCE_2026-09-14.md — actual server pairing and132 business workflow.
- docs/IDLE_LOAD_138_PASS_DOWN.md — measured optimization and its limitations.
- docs/ONBOARDING_THEME_133_PASS_DOWN.md — implemented onboarding and isolated UI fixture.
- docs/BUSINESS_AGENTS.md — bounded agent model, schedule and connector limitations.
- OpenCodeComputer.kt / OpenCodeComputerPanel.kt — result discovery/export limitations.
- LooiBleLink.kt / RobotBaseControlPolicy — current physical-control quarantine.
