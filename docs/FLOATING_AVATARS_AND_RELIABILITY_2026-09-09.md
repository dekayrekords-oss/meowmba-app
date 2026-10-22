# Floating characters and assistant reliability — September 9, 2026

## Delivered scope

Lumen gains rabbit, pig, alien, robot and panda forms. All twelve character
forms use distinct eye geometry: cat almonds/slit pupils, owl round lenses,
tall rabbit/squirrel eyes, softer dog eyes, swept dark alien lenses and robot
apertures. They share the existing damped gaze, blink, speech and emotion rig.
The final design follows the user's correction: **floating features only**,
with no whole head, filled face or head shell. Reference pictures are not
bundled. Default iris colors follow the species; custom eye colors remain
in force. Character forms grow by up to 26% in portrait and 8% in landscape at rest
where space permits; the eyes-only form retains its established footprint. The same geometry fits movement, props and hit targets. Partial ears
grow with the morph to avoid clipping before space is reserved. Privacy marks
remain anchored independently of character motion.

The agent/voice audit fixed concrete reported failures:

- Project-start parsing accepted its own spoken “Started … Projects” response,
  bug reports and negated requests. It now accepts explicit requests. Creation
  is single-flight, reuses recent identical requests, verifies record/workspace
  evidence and retains incomplete allocations during in-process cancellation.
  Duplicate requests preserve their original active plan. Navigation cancels
  pending selection before a slow database read. Own speech capture is stopped
  before TTS startup.
- Listening recovery uses session ownership, monotonic deadlines, terminal
  error handling and an explicit second-tap cancel for wedged finalization.
  Muted turns and stopped playback release state. Late callbacks cannot own
  newer turns. This is lifecycle repair, not measured GPT/Grok speech parity.
- Final-only model replies and corrected finals now reach chat. Background
  spoken responses are retained, and explicit typed sends bypass the ASR
  similarity filter that previously dropped similar text questions.
- User statements that something is broken/not working/stuck become local
  **possible**, unverified reports in Files → diagnostics → possible-bugs.json
  and in the audit trail. Reports are bounded, deduplicated, timestamped and
  include version/interaction context. Common secret patterns are redacted.
  Passive notes do not feed this diagnostic path. Nothing is uploaded and no
  repair/tool action is executed from a report.
- Removed the dead traffic404 fallback, preserved the real provider denial,
  added bounded retry backoff and isolated providers so camera data can
  continue independently. See ROAD_PROVIDER_RECOVERY_2026-09-09.md.
- Removed fake procedural-tone success for requested generated music. Audio
  results need valid nonempty WAV data, cancellations propagate, and partial
  writes are removed.

## Business mode and notes

“Open business mode” opens the workspace without starting a job. Business mode
exposes actual configured services, enabled agents, granted
service tools, automatic reads, active jobs, pending approvals and schedules.
It retains the existing30 workflow starters, executive team, local/cloud
selection, persistent action evidence and per-tool approval paths. A quick
daily-brief configuration uses the existing editor. A durable pause switch
stops new scheduled allocations without interrupting already running jobs.
Identical unfinished jobs are coalesced, and repeated successful actions stop
instead of looping. No jobs, schedules or new permissions are auto-enabled.

Explicit local note sessions now require microphone permission, local ASR and
storage. They show the recording state, treat transcripts as data, perform
bounded IO writes and restore prior mute state on stop. Stop, microphone-off
or leaving the app saves a local extractive digest with excerpts, decisions
mentioned and possible follow-ups for review. Source notes remain available in
Vault. The summary creates no reminders or external actions on its own.

## Verified limits and remaining work

- Waze returned403 in a test-area request; the retired fallback returned404.
  Removing the fallback does not restore live traffic/police coverage. A
  supported provider/feed arrangement is still needed. A live OSM request
  timed out; mapped-camera coverage was not field-validated.
- Native render/logic tests use scripted inputs. Live-device speech accuracy,
  turn latency, battery/thermal behavior and on-phone visual acceptance remain
  unverified. Nothing in this change enables physical robot control.
- Notes currently capture while the app is visible and stop on background.
  An all-day background capture service and scheduled end-of-day digest remain
  unfinished. The UI states this limitation.
- Project retry receipts survive cancellation within the running process, not
  process death. Restart recovery for an interrupted creation still needs a
  durable operation identity; automatic confirmation-triggered creation is
  blocked, and plans do not automatically replay on startup.
- Logic Pro12.3.1 was found on this Mac. The paired helper source now reports
  read-only installation/permission facts via its existing signed status RPC.
  An APK update does not deploy the desktop helper. Track/region editing,
  mixing/mastering, audio measurements and verified bounce delivery require a
  dedicated adapter. See LOGIC_PRO_INTEGRATION_PLAN_2026-09-09.md.
- The broader company-assistant request is mapped in
  PROJECT_LOOP_BUSINESS_MODE_2026-09-09.md. Specialized inbox/bill extraction,
  calendar/CRM/finance/commerce actions, source-media editing and native
  frontier-AI sessions require functioning integrations and acceptance evidence;
  a workflow starter or generic screen controller is not proof of those tasks.

## Release verification

**v1.3.63 / code67**: full app suite **2,011 tests: 2,006 passed, five skipped,
zero failures/errors**. Python desktop helper:20 passed. Tests completed before
assembly; all750 source files matched before/after packaging. APK version,
signature and all125 previous native/asset entries verified. Eighty-two tests
were added compared with1.3.62. The first full run caught seven sizing/motion
regressions, which were corrected and revalidated in the final full suite.

APK SHA256: `d99d8f3dbc3c4e0f365bc7fb4d77faaa2b94a3491565d8bf7ea89e7085f5f690`.
Release evidence: `/Users/prog/Meowmba-Releases/character-eyes-2026-09-09/`.
LAN fetch timed out; upstream currency could not be verified. Existing local
work was preserved. No installation,
account access, external posts/messages, live note recording, Logic launch,
user media modification, sensor testing or physical robot operation performed.
