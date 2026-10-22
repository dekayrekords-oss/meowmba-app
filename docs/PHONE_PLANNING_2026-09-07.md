# Phone planning and agent setup — 1.3.38 / code 42

## Delivered code

1. `open Facebook and start a post` routes the full request to an approved
   phone-control goal. The immediate launcher no longer consumes the trailing
   task. Explicit email composition keeps its earlier dedicated route.
2. App lookup uses the real installed launchable-app inventory. It handles
   `fact book`, whitespace variations, and conservative edit-distance corrections.
   Ambiguous/short names require clarification. `open it` goes to contextual planning.
3. Screen reading and indexed taps share the same Accessibility control list,
   including unlabeled controls; one text action produces at most one tap.
   Empty text falls back to content descriptions, and numeric model tap targets
   refer to screen indices rather than reaction counts. Fresh visible bounds
   provide a touch fallback for custom controls that reject Accessibility clicks.
   Password fields are redacted from summaries. Social/message controls require
   separate human verification; payments, deletion and account permissions stay
   manual. This conservative label guard is not a universal
   proof of safety for every app or language.
4. The planner receives a bounded installed-app inventory once per goal, a larger
   screen summary, and up to 15 recent action results. A new utterance cannot
   silently replace the running task; explicit changes require fresh task approval.
5. Restored and live conversation turns are merged and deduplicated, bounded to
   16 turns / 800 characters per turn. Context is reference data, never approval.
6. Two strong broadcast-ad cues trigger a clarification before tool routing or
   memory insertion. Explicitly addressed requests and discussion about television
   are exempt. Existing nonspeech, garble and own-voice filters remain. Text alone
   cannot reliably tell the owner from a television or another speaker.
7. Onboarding offers a real conversation model first, with foreground download
   progress and automatic verification after completion. Needle is not presented
   as a ready conversation/planning model. Normal Continue requires verification;
   an explicitly labeled basic exploration exit remains available.
8. Cloud setup is optional and consented. It links directly to OpenRouter keys,
   filters the current catalog for tool-capable models, defaults to free choices,
   separates paid choices, tests two synthetic planning/context cases, then saves
   the key securely. A local-only choice no longer silently routes to another
   saved cloud key when its local model is missing.
9. The agent screen adds 30 searchable workflow starters in Operations, Customers,
   Finance, Knowledge, Growth and Workspace. These are configurable recipes over
   actual tools, not automatic account integrations or 30 separately validated services.
10. A stable-ID chief-of-staff team includes three specialists; repeated template
    installation preserves existing agents. It does not activate schedules, cloud
    permissions or service tools by itself.
11. `events.check`, `drafts.prepare`, and `drafts.list` provide a local review outbox.
    A stable service/account + event ID produces one atomic draft; duplicates
    preserve the original. No send function is included. Files are in
    Files → business → drafts. Removing a draft also removes that event's local
    draft deduplication record, so this is NOT an external delivery ledger.

## Verification and device scope

- Connected phone: SM_F966U1 Galaxy Fold, serial RFCY61NJEVM (not the earlier Flip 6).
- Upgraded the previous 1.3.29 installation without clearing app data.
- Transferred the existing cached Gemma 4 E2B LiteRT model over USB; byte-for-byte
  comparison passed. The old Qwen, Needle and image models were preserved.
- Real device: the new local model passed structured navigation and follow-up
  reference probes and its local endpoint was selected after success.
- Real installed-app lookup: `fact book` resolved to installed Facebook.
- Real device scripted plumbing: persisted approval, verified file write and
  reopened workflow checkpoint passed. This test is separate from model inference.
- Live Facebook composer: normal-app navigation reached the empty composer.
  Fixed a restored comment/photo screen path and the newer `New post` title
  instead of only `Create post`. No post was published. UIAutomator inspection
  suppresses Accessibility temporarily; inspect before approval or after the loop,
  never during live navigation. Earlier instrumentation/service failures are not
  evidence of Facebook sign-in failure.
- Normal-app exact draft acceptance at 15:19: launch Facebook → tap composer →
  type supplied `meow ai test` → read back the editable value → report draft ready.
  Independent UI tree inspection confirmed the exact text. The constrained
  action schema produced a valid launch action on its first model decision.
  No text was published. The final review screen is Public; the composer also
  had Threads cross-sharing enabled, which must be reviewed by the user.
- Final JVM/Compose suite: 1,237 tests, zero failures/errors, five conditional skips
  (1,232 executed successfully).
  Debug APK and Android test APK builds pass; MWDAT SDK is enabled.
- Device preview installed with `adb install -r`; Android reports 1.3.38 / code42.
  Last successful installation before USB disconnected used SHA-256
  `8d0da71492c69cb54109631dad9d0389ff1da2ca14c11c0e1c01c50551747d77`.
  It includes draft creation and the initial social verification guard.
  The final exact-label confirmation refinement and publish/share/reply compound
  routing are built below but NOT installed; ADB lists no devices. Reconnect to
  install and test the native confirmation/cancellation screen. No final Share
  control or positive send/publication verification was executed in this turn.
  Archive: `/Users/prog/Meowmba-Releases/meowmba-1.3.38-code42-agent-preview.apk`.
  Latest archive SHA-256: `7285a9e01a9c7204a386c1696f49152d3a5918a7f9f94b6c335e15d2ce095579`.
  This is the debug/package-preserving device preview, not a Play submission.

### Real model delegation acceptance

The normal-app check also exposed an older wiring error: the phone loop asked
only `omniRoute`, not the directly selected model. It now accepts the selected
provider, uses constrained JSON for LiteRT, and stops after three failed model
attempts with a useful error. A regression test covers direct-provider generation.

The Fold ran the real on-phone Gemma model through manager → analyst → exact
CSV arithmetic → approved report write → completion evidence, three successive
times. All three runs passed (38s, 41s, 48s; 129.493s total test time including
setup). Each report contained the verified 25.25 total for two unpaid rows.
This uses synthetic files in unique test workspaces, not customer records.
It establishes this bounded local delegation workflow, not arbitrary company
operations or external integration reliability. Battery temperature immediately
afterward was 35.8°C; that single reading is not a thermal benchmark.

The workflow-picker UI test exposed an unstable intrinsic-height dialog layout.
The picker now uses a bounded full-screen dialog with a fixed header/search and
scrollable results. Its search/configure test passes in isolation; it grants no
MCP access, schedule or cloud permission merely by choosing a recipe.

### Explicit social-action verification

At the user's additional request, posting/sending/replying/sharing can request
an on-phone verification dialog above the target app. It shows the exact control,
package, request, screen summary and full bounded editable fields. Approval is
single-use, expires after two minutes and rejects changed app/content. It refreshes
the original node; no alternative target or automatic retry is permitted. A click
is reported as dispatched, never as verified delivery. Request/result are audited.
Delete/pay/permission controls and ambiguous targets remain manual. This is not
a general unattended-send policy or provider-side idempotency.

Also closed a standalone `ui.automate` approval bypass and the remaining direct
multi-step loop-start path. LiteRT phone actions now use a constrained action enum
instead of only an unconstrained JSON-object schema. Explicit simple Facebook
drafts can fill an empty composer with supplied text, stop for review and refuse
to overwrite existing text. Positive social delivery is not signed off by unit
tests and is not performed merely to test a confirmation dialog.

The pre-existing file-picker test race was also corrected: it now waits for
background library indexing to enable Add before clicking it. It still asserts
all import/menu options, rather than retrying the click or weakening assertions.

## What remains — do not advertise as complete

An arbitrary autonomous company, all-app control, guaranteed TV rejection, and
unattended auto-replies are not established. Android can defer scheduled work;
force-stop pauses it until reopening. Existing WorkManager jobs have budgets,
checkpoints, cancellation, approvals and thermal pauses, not an unlimited daemon.

Email/new-user monitoring needs a real compatible MCP endpoint, account access,
selected read tools and a schedule. Browser OAuth is not implemented by the
business bearer-token connector. Sending needs a narrow user-approved policy,
verified recipients, trusted event IDs, provider-side idempotency and reconciliation
for uncertain outcomes. No such connection, schedule or permission was silently created.

The remote `meow:/home/claude/git/meowmba-app.git` was unavailable (SSH timeout).
No claim of remote newest version, remote upload or Play Store publication.
