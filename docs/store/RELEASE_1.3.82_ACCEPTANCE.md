# 1.3.82 (130) release acceptance

## Release gate

Owner requested a fix for the retained `Agentic: blocked on Run on Computer: printf meow120_verified` banner, a milestone and private PR, and a Play release. Before upload, test Computer and a realistic agent workflow with the real planner.

Private milestone: https://github.com/dekayrekords-oss/meowmba-app/milestone/1
Private PR: https://github.com/dekayrekords-oss/meowmba-app/pull/6

## Implemented

- Active Computer work takes precedence over old failures. Newest blocked task appears first.
- Blocked notices have Review and Dismiss controls. Dismissal acknowledges that exact task revision in encrypted preferences; it does not approve, retry, cancel or erase the task. A later failure surfaces again. Pending approvals cannot be dismissed this way.
- Computer acceptance tests retire their own unfinished tasks. A one-time phone test cancelled exactly five known printf/sleep acceptance fixtures, retaining their audit history; no other open tasks were present.
- Preserves preview128 streaming, generated-prop and rendering repairs. See `docs/MEOW_REPAIR_128_PASS_DOWN.md` for their scope and limitations.
- The real shop scenario exposed an invalid action from ordinary chat generation. Business planning now uses the existing remote function-calling protocol and bounded proposal correction. Unknown actions and missing action fields are rejected before dispatch. Local/generic models retain bounded correction; remote network/credit errors are not multiplied by an outer retry loop. Approvals and evidence checks still belong to the execution engine.

## Verified so far

- Final debug build and instrumentation package built; 2,523 JVM tests, zero failures/errors/skips.
- Installed preview129 over preview128 via USB, preserving data. All 74 native/model runtime entries match the verified phone122 baseline. APK SHA-256: `b2951f7902a8b3eb8f09beb7eac33f096a2dbf6b905429766cd919fec8b825b6`.
- On phone: notice acknowledgement survives activity recreation, task/audit history remains, and a new blocked revision reappears.
- On phone: real Computer command success; create/edit/read/remove an isolated project file; exit-7 failure; cancellation without a late chat reply; persisted command results.
- On phone: authenticated Meow worker returns real Linux output; wrong credentials rejected. Initial harness run lacked its temporary credential, then passed after normal credential provisioning.
- On phone: app launch opens Gmail without reading inbox content.
- On phone: deterministic planner decisions exercise real workflow storage, CSV arithmetic, approval-before-write, approval denial, exact-once file write and durable evidence. This is tool/policy verification, not proof of live model planning.
- Computer worker: five Python tests pass. Logic adapter/workflow: 45 Python tests pass; these do not prove live Logic Pro mixing or mastering.
- Preview130 installed via USB after the planner fix; its 74 native/model entries remain identical. SHA-256: `ffcea82edf8a8890eea2b761e2ed5e466948da6c9c97f4dfd6bd5b1a59e3b151`.
- Signed, minified release130 installed over release85 without clearing data. Cold MainActivity launch returned OK (620 ms); its process stayed alive with no new crash-buffer entry. This is a release startup check, not a repeat of every debug instrumentation test.

## Realistic model scenario

Synthetic shop orders, real configured model, real WorkflowModelBrain/WorkflowEngine and file tools: identify unpaid orders, compute exact total, save a morning briefing and draft customer follow-ups, read the report back, and finish from tool evidence. Approval is permitted only for the scenario's single report file; no customer messages can be sent.

Phone has no selected/installed local conversation model (only Needle routing model). The test used its existing `openrouter/free` choice without changing user preferences or selecting a paid fallback. Android MODERATE thermal status initially paused the test; it ran only after cooling, without overriding thermal protection. Build129 then failed on an unrecognized model action. That failure led to the protocol fix above.

Build130 PASSED the real scenario in 69,278 ms: the model recovered from an initial CSV call with missing arguments, read the sample input, used exact CSV arithmetic, identified A101/Alex Sample and A103/Casey Sample, calculated $56.75 outstanding, requested one approval, wrote a morning briefing with individual draft follow-ups, read the report back, and finished with durable tool evidence. No customer messages were sent. The model received the task/data, not precomputed answers or scripted decisions.

Evidence: `business-scenario130/{state.json,result.json,morning.txt}` and `phone130-business-scenario.txt` under the release evidence directory. This validates one realistic workflow with this provider at this time; it does not establish Grokbot parity or reliable completion of every business task.

## Known limits

Overheating is not resolved. Prior free OpenRouter generation returned keepalive whitespace without a completion. Full autonomous app control, neural-server pairing, live Logic Pro work and universal business integrations remain unverified/incomplete. Do not advertise those as completed by this release.

Evidence directory: `/Users/prog/Meowmba-Releases/release129-20260914/`.
Release build directory: `/home/prog/meowmba-release129-20260914` (isolated, pinned native sources, existing external signing and Meta SDK configuration).
Existing Play destination: `com.meow.meowmba`, closed-testing Alpha. Upload/submission/availability are separate states and must be recorded from actual results.
