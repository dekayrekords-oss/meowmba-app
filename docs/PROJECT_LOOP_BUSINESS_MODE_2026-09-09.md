# Project loop repair and Business mode

## Reproduced cause and repair

`ProjectRequestParser` previously accepted any sentence containing `start` and
`project`, including the app's own spoken confirmation: “Started Website. Its
plan, activity and files are together in Projects.” If recognition captured
that response, it could start another project, speak another matching response,
and continue creating numbered projects. Bug reports and negations also matched.

Creation now requires an anchored request to start/create/make a project. Tests
cover the exact spoken confirmation, bug reports, negation, progress discussion,
and valid polite requests. Voice echo filtering remains a separate layer.

`ProjectCreationService` admits one creation at a time and coalesces the same
normalized request for 60 seconds. It verifies the inserted Room record and
actual workspace before returning it. Cancellation or disk failure after the
insert retains the ID, so retry repairs the same allocation rather than making
another numbered project. A changed or unverifiable saved record fails closed.
The creation receipts are limited, process-local data; they are not a durable
cross-process exactly-once protocol. Existing project records/files stay durable.

The residual restart case is specific: if the database insert commits but the
process dies before setup/confirmation finishes, a fresh process has the saved
project but no request receipt. Repeating the same explicit creation request can
allocate a numbered second project. Automatic recognition of the app's own
confirmation is prevented by the parser, and saved projects do not restart their
plans on launch. Full cross-process retry idempotency requires a durable request
identifier tied transactionally to the Room project, or a recoverable allocation
journal; matching names alone cannot distinguish a retry from an intentional new
project.

An independent ViewModel review also found that reusing an already active project
cancelled its pending plan through `applyProject`. Same-project reuse now leaves
that plan running and retains its busy state. Project navigation invalidates a
pending creation before its Room lookup suspends, so an old creation cannot take
the screen while another project is opening. Six ViewModel regressions cover
duplicates, a delayed plan, cancellation/repair, slow navigation, text routing and
leaving the project during an insert.

## Business mode changes

- Existing business-agent entry and page are titled **Business mode**.
- A configuration overview reports enabled agents, active jobs, pending approvals,
  enabled service connections, tools actually granted to enabled agents, expressly
  allowed automatic reads, and configured schedules. Stored connections are not
  described as current successful live connections.
- **Configure daily briefing** opens the existing agent editor from the daily
  briefing starter. It does not create a job, enable a schedule, connect an
  account, or grant tools merely by rendering or opening the card.
- Existing **Add executive team** retains saved agents/permissions and adds only
  missing starter roles. It does not turn on schedules or send messages.
- Optional **Open business files** and **Review possible bug reports** callbacks
  connect the page to the app's real file browser when provided by its caller.
- **Pause new scheduled runs** is persisted. It prevents new scheduled jobs while
  leaving grants, intervals, existing job status, and manual job submission intact.
  The UI explicitly directs users to stop already active jobs in Job history.
- Identical submissions to the same agent reuse an unfinished job, including
  after reloading the store. Completed/cancelled jobs can be intentionally rerun.
- A planner repeating an identical successful mutation is stopped before another
  approval/dispatch. Consecutive identical successful reads also stop. An
  intervening observed action still permits read → write → read verification.

## Capability inventory against the requested assistant

| Requested area | Existing implementation | Material remaining work |
| --- | --- | --- |
| Executive/company assistant | Chief of staff, three delegated specialists, 30 configurable starters, explicit tool grants and completion evidence | End-to-end company workflows need connected data, business policies and acceptance tests; starter copy is not a live integration |
| Proactive tasks | Persistent scheduled goals through WorkManager, minimum 15 minutes, shared execution slot, power/thermal gates, new global scheduling pause | Event-triggered inbox subscriptions and reliable cross-service change tracking; Android force-stop prevents background work until reopening |
| Approval and audit | Per-action approvals, expiring service approvals, durable steps/results, interrupted-action hold, exportable job history | No claim of an unrestricted unattended executor or formally complete audit coverage for every UI fast path |
| Reports/research/finance documents | Local text-file reads/writes with hashes, exact decimal CSV summaries, research and finance starters | Native presentation authoring, specialized accounting connectors, reconciliations against real accounts and actual trading/banking workflows |
| Inbox, sales, support and bills | Connected MCP tools can be expressly granted; durable event-keyed local reply drafts prevent duplicates | Unified inbox model, stable source cursors, bill extraction/due-date verification, calendar synchronization, account-specific acceptance tests |
| Phone app use | Accessibility-based control and verified bounded phone-agent/search workflows | Coverage and live tests for each app/task, account handoffs, unsupported custom controls and changing layouts |
| Marketplace listings and deals | Inventory sibling integration and general web/phone tools | Verified end-to-end listing/publication/edit flows and image-to-item matching for each requested marketplace |
| Images, cartoons and video | Existing media tools and studio artifact creation | General nonlinear YouTube editing, timeline inspection, revisions, export quality measurement and publishing connectors |
| Logic Pro mixing/mastering | Paired desktop bridge with bounded approved accessible UI actions | Dedicated Logic project/track/region control, nondestructive edits, plug-in parameter support, audio analysis, LUFS/true-peak checks, bounce verification and listening evaluation |
| Always-on notes and daily digest | Existing speech capture, conversation memory and meeting-transcript starter | Explicit note-capture mode with visible recording state, durable transcript chunks, retention/stop controls and a verified daily summarization pipeline |

## Validation ownership

The integrator owns Gradle, packaging, device testing and release status. This
subtask adds parser/service, business-repeat, readiness/persistence and native
Compose overview tests. No Gradle run, device access, account operation,
external message, schedule enablement or physical robot action was performed
by this subtask. Refer to the integrator's final test report for actual results.
