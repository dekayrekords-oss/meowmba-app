# 1.3.82 (129) release acceptance

## Release gate

Owner requested a fix for the retained `Agentic: blocked on Run on Computer: printf meow120_verified` banner, a milestone and private PR, and a Play release. Before upload, test Computer and a realistic agent workflow with the real planner.

Private milestone: https://github.com/dekayrekords-oss/meowmba-app/milestone/1

## Implemented

- Active Computer work takes precedence over old failures. Newest blocked task appears first.
- Blocked notices have Review and Dismiss controls. Dismissal acknowledges that exact task revision in encrypted preferences; it does not approve, retry, cancel or erase the task. A later failure surfaces again. Pending approvals cannot be dismissed this way.
- Computer acceptance tests retire their own unfinished tasks. A one-time phone test cancelled exactly five known printf/sleep acceptance fixtures, retaining their audit history; no other open tasks were present.
- Preserves preview128 streaming, generated-prop and rendering repairs. See `docs/MEOW_REPAIR_128_PASS_DOWN.md` for their scope and limitations.

## Verified so far

- Debug build and instrumentation package built; 2,520 JVM tests, zero failures/errors/skips.
- Installed preview129 over preview128 via USB, preserving data. All 74 native/model runtime entries match the verified phone122 baseline. APK SHA-256: `b2951f7902a8b3eb8f09beb7eac33f096a2dbf6b905429766cd919fec8b825b6`.
- On phone: notice acknowledgement survives activity recreation, task/audit history remains, and a new blocked revision reappears.
- On phone: real Computer command success; create/edit/read/remove an isolated project file; exit-7 failure; cancellation without a late chat reply; persisted command results.
- On phone: authenticated Meow worker returns real Linux output; wrong credentials rejected. Initial harness run lacked its temporary credential, then passed after normal credential provisioning.
- On phone: app launch opens Gmail without reading inbox content.
- On phone: deterministic planner decisions exercise real workflow storage, CSV arithmetic, approval-before-write, approval denial, exact-once file write and durable evidence. This is tool/policy verification, not proof of live model planning.
- Computer worker: five Python tests pass. Logic adapter/workflow: 45 Python tests pass; these do not prove live Logic Pro mixing or mastering.

## Realistic model scenario

Synthetic shop orders, real configured model, real WorkflowModelBrain/WorkflowEngine and file tools: identify unpaid orders, compute exact total, save a morning briefing and draft customer follow-ups, read the report back, and finish from tool evidence. Approval is permitted only for the scenario's single report file; no customer messages can be sent.

Phone has no selected/installed local conversation model (only Needle routing model). Use its existing free OpenRouter choice without changing user preferences or selecting a paid fallback. First attempt stopped at the production thermal gate (Android MODERATE); successful end-to-end planning is not yet established. Play upload remains pending this check and signed bundle validation.

## Known limits

Overheating is not resolved. Prior free OpenRouter generation returned keepalive whitespace without a completion. Full autonomous app control, neural-server pairing, live Logic Pro work and universal business integrations remain unverified/incomplete. Do not advertise those as completed by this release.

Evidence directory: `/Users/prog/Meowmba-Releases/release129-20260914/`.
Release build directory: `/home/prog/meowmba-release129-20260914` (isolated, pinned native sources, existing external signing and Meta SDK configuration).
Existing Play destination: `com.meow.meowmba`, closed-testing Alpha. Upload/submission/availability are separate states and must be recorded from actual results.
