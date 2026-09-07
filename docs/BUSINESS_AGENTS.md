# Phone-hosted business agents — 1.3.32 / code 36

Status: implemented as a preview, undergoing connected Flip 6 acceptance. Do not describe
this as an unrestricted desktop agent or a verified replacement for all business
software. The existing tool-group agents and the new durable team runtime are
different systems; chat/voice can enqueue and inspect the durable jobs.

## What is implemented

- User-created roles with instructions, local/OpenRouter model selection,
  individual tool grants, permitted delegates and required completion actions.
- One serialized execution slot, depth-4 delegation, no cycles, 32 recorded
  steps / 40 planning-turn limits. Each planning turn has up to three bounded
  model attempts. Inputs, outputs and context have explicit size caps.
  A delegate gets one completed assignment per parent job; bundle its related
  work instead of creating repeated delegation loops. The decoder uses short
  aliases, explicit user file references and actual recorded observations.
  Quote file paths containing spaces. Decoding hints do not grant permissions.
- Durable atomic checkpoints, persisted approvals, cancellation, failure
  records, history archiving into Files. Interrupted actions are held for
  review instead of automatically replayed.
- Exact decimal CSV totals, bounded paginated text reads, sandboxed file lists,
  and approved writes verified by byte readback and SHA-256.
  CSV column names accept only unambiguous case aliases. Value equality is
  case-insensitive by default, explicitly reported in the result; choose
  `case_sensitive=true` when case distinguishes business values. No fuzzy or
  whitespace matching is applied. Errors report the actual available columns.
- WorkManager scheduling at a minimum of 15 minutes. Android decides the actual
  run time; battery constraints, power saver and thermal conditions can delay
  it. Force-stopping suspends work until the app is opened again.
- Work stops or pauses at moderate thermal status, battery temperature of 39°C,
  or power saver; local planning checks memory availability. Shared LiteRT
  sessions are serialized and never closed under a live generation.
  After a thermal pause, wait below 37.5°C before reloading the model. Fresh
  dispatch requests are independent of old retry chains but share the same
  execution mutex, so a cancelled job's backoff cannot hold a new job hostage.
- Authenticated Streamable HTTP MCP: initialization, initialized notification,
  session/protocol headers, paginated tool discovery, typed JSON arguments,
  JSON/SSE responses, structured/text results and honest error handling.
  Supports handshake revisions 2025-11-25, 2025-06-18 and 2025-03-26. The newer
  2026-07-28 stateless transport, legacy split HTTP+SSE, streaming reconnection,
  browser OAuth and stdio are not implemented. Server compatibility must be
  tested; this is not an "every MCP server" claim.
  Named model arguments are converted according to the server's schema; nested
  values can use JSON strings. Required fields/basic types/enums are checked
  before offering approval, with bounded local correction and no network call.
  The server remains responsible for validating its complete JSON Schema.
- MCP metadata does not grant authority. The user grants tools to each agent.
  Service calls require explicit approval unless the user separately grants a
  specific server-labeled read-only tool unattended access. Use least-privilege
  tokens. Destructive/service approvals expire after 60 seconds. No redirects
  forward bearer credentials. There is no shell or stdio execution.
- Job notifications open the team screen; notification permission is optional.
- Verified model selections and service keys use checked, synchronous encrypted
  preference commits on an IO dispatcher before the setup UI reports success.
- Incidental fixes: canonical file-browser root aliases, bounded ASR setup
  retries/no overlapping downloads, and one correctly ordered TTS stop during
  interruption. Full speech acceptance remains a separate release gate.
- Agent editing closes only after a successful save. Failed saves keep the
  user's entries visible and allow retry.
- Voice capture checks the actual microphone grant before starting. Denial
  stops the always-on retry loop, wake capture and pending follow-ups without
  changing the user's microphone privacy toggle. Permission results and
  foreground resume reconcile the state. Startup model-download progress is
  owned by its install coroutine; failed installs no longer claim readiness.
- Interrupting speech with a missing local recognizer returns to idle with
  install/repair guidance, rather than remaining stuck in the speaking state.
- Agent-status questions use actual task/job tools before generic chat routing.
  An immediately completed tool result is not overwritten by an "is on it"
  preamble. Task listings exclude the task that is listing them.

## Getting started

1. Open **Agents → Create & run business agents**, or say **open business agents**.
2. Open **Set up a local planning model**. Download a compatible conversation
   model if necessary, then **Test and select**. Needle is a router, not the
   conversation/planning model. Selection is saved only after real inference.
3. Cloud is optional: **Connect OpenRouter** explains cloud use and tests a real
   generation before storing a key. Each cloud agent also needs explicit cloud
   consent. Charges/rate limits depend on the configured provider/model.
4. Create an agent or add the operations-team template. Grant only its needed
   tools. For a report-writing agent, require successful `files.write` before
   allowing completion. For an analyst, require `files.csv_summary`.
5. Place inputs in **Files → business**, enter a goal and choose **Run job**.
   Review exact action arguments before approval. Inspect **Show steps** to
   distinguish verified tool output from an agent's interpretation.
6. To connect a service, supply its real HTTPS MCP endpoint and token. Verify
   and discover tools first, then explicitly grant selected tools to agents.
   Browser OAuth and desktop-only MCP servers are not implemented by this
   connector. A phone app icon is not proof of an MCP API or account access.
7. Optional schedules have a named goal and interval. Pause them by disabling
   the agent. Archive completed/stopped jobs to keep active history bounded.

Voice examples: `list business agents`, `show business jobs`, and
`ask business agent Operations manager to summarize orders`.
Enqueueing is acknowledged as **queued**, never as goal completion.

## Acceptance evidence

- The 1.3.30 focused JVM run passed **117 tests, 0 failures**: workflow safety,
  real HTTP MCP transport with a synthetic server, files, stream accumulation,
  routing, image-response parsing and compatible voice/model checks.
- A real Flip 6 file/approval/checkpoint test passed.
- Real local Gemma E2B testing exposed a shipped LiteRT stream bug: the provider
  replaced the accumulated answer with each delta, leaving only its last token.
  Accumulation is now tested and fixed. Structured output uses a constrained
  JSON schema and never inherits the conversational three-sentence cap.
- Local AI completed three consecutive explicitly configured analysis →
  delegation → approval → verified report runs in 43/44/44 seconds.
- Broader generic-role testing then exposed invented filenames, repeated
  delegation and instruction-copy summaries. These were not counted as passes.
  User-file constraints, one-pass delegation and preserved tool observations
  corrected the tested operations template.
- The **actual production operations template and WorkManager worker** reached
  the correct approval in 47.2 seconds, using a unique input/output path. After
  force-stopping the debug app, a new process resumed its persisted approval
  through `WorkflowRuntime.recover()`, wrote exactly one report and completed
  locally in 19.1 seconds. Result: unpaid total **25.25**, selected rows **2**.
  Run: `471e45e7-f105-4adc-914a-c991f80cc7f8`; history archived into Files.
- A varied-input run exposed capitalization retries. After correcting matching
  and useful error feedback, the production worker passed **9.33 / 2 rows**:
  approval in 47.2 seconds, force-stop/recovery completion in 15.1 seconds.
  Run `2ab08052-eafa-4fea-adf9-1aedaa3bf00c`.
- A third production run used different column names (`invoice_total`,
  `payment_status`), mixed `unpaid`/`Unpaid` values and larger amounts. It
  corrected two failed reads using actual column feedback, then passed
  **5088.68 / 2 rows**, approval in 69.2 seconds, restart completion in 34.2
  seconds. Run `db6d3b28-8436-404d-af35-5778d37f6a5d`. Both varied runs wrote
  exactly one verified report and were archived. Failed attempts are not passes.
- The real local model completed an Android loopback synthetic-CRM workflow
  in **24.2 seconds**: authenticated discovery, explicit MCP-call approval,
  actual SSE ticket evidence, explicit file-write approval, one verified report.
  First testing exposed a named-arguments/JSON-wrapper mismatch; the corrected
  adapter and preflight checks are covered by new tests. This is real Android
  transport/model execution against a test service, NOT a live-account test.
  Checkpoint: `workflow-tests/mcp-066f1c90-abd8-49e6-b63f-dfe0f3d025b4/state.json`
  in the debug app's private files.
- Three Android emulator UI tests passed: create/read a local agent in the dark
  theme, and inspect/deny exact file-write arguments in the light theme with
  no file written, plus failed-save field preservation and successful retry.
  Screenshots were inspected. A subsequent physical Flip test passed local
  agent creation/readback in a test-only activity displayed above keyguard;
  its screenshot was inspected. This does not verify unlocked MainActivity
  navigation. Light approval-denial and failed-save/retry checks were blocked
  when the secure display relocked; those physical attempts are not passes.
- Heat protection was observed on the physical Flip. Repeated installation,
  local inference and visible Play Store background work kept it around
  38–39°C; two subsequent four-minute cooldown waits expired without running
  another job. The thermal gate was not bypassed.
- The 1.3.30 full regression checkpoint was **1,187 tests: 20 failed,
  5 skipped**. None of the business workflow/MCP tests failed. Remaining
  failures include older voice-capture expectations, router-with-no-model and
  orb-tap expectations, plus ViewModel/Main-dispatcher lifetime contamination.
  These are NOT a green release suite and have not all been baseline-isolated.
  The varying failure count also exposes asynchronous test contamination.
  Results are preserved separately from the later focused run. Do not publish
  this preview based only on the 117 focused passes.
- During the physical follow-up, startup with RECORD_AUDIO denied exposed an
  ERROR_INSUFFICIENT_PERMISSIONS retry every two seconds. After the permission
  guard was installed, the same denial remained in effect and PID 29000 logs
  from 03:21:55 through 03:31:37 MST showed no capture-start/error loop.
  Actual speech and grant/revoke UI acceptance remain unverified.
- The 1.3.31 focused run passed **86 tests, 0 failures**, covering permission
  denial/callbacks, companion and vision states, business workflows and voice
  policy. New tests assert zero capture calls with a denied OS permission,
  including speech-finish/follow-up paths, and no model download from a
  context without app-private storage.
- Interim 1.3.31 full suite: **1,194 tests, 11 failed, 5 skipped**. Failures:
  AgentViewModelTest (1), AuraHomeContentTest (1), ModelStatusTest (2), StudioTest
  (1, asynchronous test-lifetime contamination), VoicePipelineTest (6).
  No business-workflow test failed. This is still not release approval; neither
  obsolete expectations nor asynchronous failures were silently skipped.
  XML evidence is in `Meowmba-Releases/business-agents-1.3.31-qa/`, with the
  focused and full runs preserved separately.
- Subsequent fixes resolved these failures. Voice fixtures now explicitly
  supply the current capture backend, use their test dispatcher and close
  continuous listeners before runTest drains its scheduler. The model-status
  fixture no longer starts a real Needle download that finishes in another
  test's Main dispatcher. Provider/orb expectations reflect the actual saved
  configuration and documented microphone tap zone. No production platform
  fallback was restored to make a fake recognizer pass.
  The task-status test exposed and fixed a real generic-chat routing bug; it
  now requires a completed `task.list` tool and exactly the actual spoken
  result. Another regression covers missing-recognizer interruption.
- Two consecutive final full runs: **1,195 tests, 0 failures, 5 conditional
  skips** each (1,190 passed). The skips are all `MetaGlassesUnavailableTest`, which targets the
  no-SDK variant; this build has MWDAT enabled. Evidence is preserved in
  `Meowmba-Releases/business-agents-1.3.32-qa/`. A green JVM suite does not
  sign off real phone speech, notifications or live business-account access.
- The Flip reached 39.3°C during the follow-up; no additional local-model
  workload was run. Temporary stay-awake-while-charging was restored to its
  original value 0. Keyguard remains a physical acceptance blocker.

## Device/build provenance

- Test device: Samsung Galaxy Z Flip 6, SM-F741U.
- Play package `com.meow.meowmba` remains 1.3.27 / code 31, unchanged.
- Side-by-side debug package `com.example.meowmba`: **Meow Assistant Preview**,
  1.3.32 / code 36, signed with
  Meow's debug certificate. SDK enabled. Voice/KWS/VAD assets included.
- Debug-only JNI reuse from the previous 1.3.29 APK; no bridge source changes.
  Release builds explicitly reject `prebuiltDebugNativeDir`.
  - Needle bridge SHA-256: `25d98ac189ac1f7dbd2d5e6f3c42c944b8ab37eb673eafd6ea3cc615093a3ceb`.
  - SD bridge SHA-256: `744b3e29f90424d9cc511d291b06d2dada34ba914496869e80d4d34eacbf7b6a`.
- Test model: `litert-community/gemma-4-E2B-it-litert-lm`, revision
  `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`, 2,588,147,712 bytes.
  SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`
  verified on the Mac and after transfer into the debug app's private storage.
- Handoff APK: `Meowmba-Releases/meowmba-1.3.32-code36-business-preview.apk`
  on the build Mac. It is a debug preview, not an uploadable Play release.
  SHA-256 `7e86e40c3090cb591b9ac158114c0edab7d0be96a3a82ae9cc46daeb6b4b46c3`.
  Final on-phone `base.apk` hash matches. Code implementation `c84fdc4` is
  pushed to Meow's `feat/image-services-settings`; no main merge or Play upload.
  Temporary active QA agents were removed and job histories preserved. There
  are no active business jobs left running. The local model selection remains
  ready for the user to add the operations template or create their own agent.

## Reproducing the device checks

Use the debug package only and synthetic fixtures. Keep the phone cool. Run
`BusinessWorkerDeviceTest#prepareApprovalCheckpoint`, force-stop
`com.example.meowmba`, then run
`BusinessWorkerDeviceTest#resumeCheckpointAndFinishInProductionWorker` using
`am instrument -w -r -e class …
com.example.meowmba.test/androidx.test.runner.AndroidJUnitRunner`.
Optional prepare parameters are `amountB`, `amountC`, `amountColumn` and
`statusColumn`; expected totals are computed independently with BigDecimal.
`BusinessMcpDeviceTest` hosts its own loopback-only synthetic service.
`BusinessAgentUiTest` requires an unlocked display; it passed on a scratch
Android emulator. The existing Play app and its data must remain untouched.

## Boundaries

Successful tool evidence prevents invented actions; it does not prove every
business judgment in an LLM summary is correct. Review important outputs.
No personal/business accounts were connected for these acceptance tests, and
no real mail, financial transaction, or customer data was used. Cross-app
automation still depends on Android permissions and supported app surfaces.
No Play release has been submitted for this business-agent preview.

Remaining acceptance gates: broader varied business-workload coverage, unlocked Flip
navigation/notifications/real speech, and account-authorized MCP service workflows. Browser
OAuth, arbitrary control of every installed app, and desktop-equivalent 24/7
local compute are not claimed.

References: [Android persistent-work scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work),
[MCP Streamable HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports),
[LiteRT Kotlin conversation API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md).
