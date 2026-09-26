# Meow work and results — preview 182 / 1.5.16

Built on preview 181 (`33d289d`), retaining the wireless-charger avatar and optional
launcher onboarding, Development Studio, Flow/Desk and all existing runtime assets.
This is the native task/result foundation of the owner-approved component integration.
No OpenMuse application, React Native shell, hosted conversation service or additional
model was installed. The implementation uses Meowmba's Kotlin, Compose and Room systems.

## User experience

- Flow: **Meow’s work** under the North Star line.
- Desk: tap the monitor → **Meow’s work**, or open either drawer → **Meow’s work**.
- Companion/chat: tap an activity row to open that exact task. The Agents menu also
  has **Meow’s work · saved results** after the transient activity panel disappears.
- Select a task to see its actual result, lifecycle, source links and supported saved
  files. Stop active work or return to its approval card. Restored history stays quiet.
- **Link to a North Star** records an explicit connection, never marks the goal or its
  ingredients achieved. The North Star's **Open task result** returns to the evidence.
- **Remove saved result** confirms removal of the record and its goal links; created
  files remain. Recent history keeps 200 records, plus active and goal-linked evidence.
- The Desk cat uses its existing computer performance during a running tool. Listening
  and speaking take priority. Real response bubbles continue through the existing path.

## Reliable results

The regular companion orchestrator previously held its task list only in memory.
Computer's separate Room-backed goal runner already existed; it is preserved.
The companion now stores records in `companion_tasks` and commits a checkpoint before
dispatching a tool. An unreadable journal or failed checkpoint blocks a new tool action.
Results are saved before the completion callback; a later storage failure is visible.
Only interrupted records need a recovery write; ordinary startup does not rewrite history.

Each result distinguishes a returned result, an adapter-verified result, a handoff and an
unconfirmed outcome. Text file writes are read back before receiving a verified label.
Camera, text/email composer, dialer, alarm and timer requests identify the handoff instead
of claiming that a photo, message, call or requested clock action has been completed.
Media verification uses the existing observed playback state. A timeout, thrown tool
exception or empty return cannot claim a verified completion.

Project steps and nested desktop/Logic workflows stop at handoffs and unconfirmed
outcomes instead of feeding them into another planned action. Existing tool ownership, approval and concurrency checks remain.
Stopping prevents subsequent work; already dispatched external requests may still finish.

After process death, unfinished tool calls are shown as **Check outcome** if dispatched,
or **Not run** if they had not reached dispatch. They are never replayed, and old approvals
are never restored as authority. The owner can inspect the destination and ask again.

## Data and efficiency

- Additive Room migration 13→14 adds one table. Earlier tables, settings and keys remain.
- Schema 12/13 encrypted backups import into 14 without replacing newer task/goal records.
- Tool arguments and approval grants are not stored in the journal. Stored titles/results
  use the existing credential scrubber; results are bounded to 24,000 characters and long
  saved excerpts are labeled. Task history remains in private local app storage and enters
  the existing encrypted backup. No new history upload or external storage path was added.
- Journal JSON work and verified file writes use background I/O. File verification reads
  in bounded chunks; the sheet scrolls and supports large system text on small phones.
  Task screen fields use the existing grouped state to stay below the JVM argument limit.
- No polling loop, model inference, extra animation clock, service or new permission was
  added. Task transitions drive updates. No measured battery/performance gain is claimed.

## Scope and next components

Existing browser, search, file and remote tools now gain the companion's saved result
handling when executed through this orchestrator. Their actual capabilities, configured
accounts and service availability are unchanged.

This release does not install a server browser/container worker, automatically resume
interrupted jobs, add a recurring website-watch service or new mail/calendar OAuth.
Those are the next server/integration stage, not implied by a saved task card. Android
can suspend/kill phone work; history recovery does not make it an always-running worker.
No CopilotKit Intelligence key is needed for this native implementation.

## Validation and release

Source `05c540b6a668f322949ee881ff0aa91051bb8b6b`. Full JVM suite: 3,094 tests, zero
failures/errors, five existing skips. Lint: zero errors, 205 warnings, 12 hints. The
optimized daily APK passes certificate and 16 KB alignment checks; all 162 protected
runtime entries match preview 181. Native component renders include a 320 dp phone at
2× system text size. No installed-device acceptance or performance comparison is claimed.

APK SHA-256: `4b3f43a227231a4823660c1a7c930826fe30b78602298e157ed1656b489d5e31`.
APK, detailed use instructions and validation evidence: Os `work/tasks-182/README.md`.
The exact tested app snapshot is committed locally; no remote push or Play upload.

The owner then explicitly requested installation. Fold R5GL75XFCXD updated180→182 in
place at21:54:58 Phoenix time, September25. Original firstInstallTime/appId and saved Desk
content retained; separate Play155 unchanged. Installed APK checksum matches the verified
artifact. PID22154 survived109.5seconds foreground with unchanged crash logs; Desk and the
new saved-work screen were visually verified, then returned to Desk. Android auto-resumed
Home (UNKNOWN launch state), so no measured cold-start claim. No emulator installation,
account action, permission grant, charger simulation or robot actuation. Phone receipt:
Os `work/tasks-182/validation/phone-install/README.md`. Real-service acceptance is pending.
