# Preview148 — driving, avatar creation and five backgrounds

September 19, 2026. Branch recovery/known-good-138. Sole integrator: root Codex.
Automatic in-place install/testing is authorized, preserving credentials/data and keeping
monitoring off. Phone currently not detected on Mac or Meow; installed147 is not replaced yet.

## Implemented

- Driving: compact summary instead of a permanent large trip/camera panel. Single-row
  landscape, short portrait summary, separate scrollable details with a reachable close
  button. Road-source availability is a neutral status; exact source diagnostics remain
  in details. Actual OBD warnings, crash alerts and separate hazard alerts are retained.
- Avatar creation: remote budgets180/210/240s, existing local75/90/120s unchanged; elapsed
  stage feedback and cancellation. Optional review errors retain only a genuinely parsed,
  validated design with an honest completion notice. Failed initial generation still fails,
  preserves the current avatar and shows a redacted actionable error. Failed example
  creation can retry without restarting the entire flow; failed refinement retains its
  previous candidate. The review dialog has a bounded scrolling body, fixed wrapping
  actions and keyboard/safe-area padding for landscape. No silent model/account switch,
  fake fallback or unapproved save. Outer-task cancellation cannot save a completed
  candidate left over from an interrupted optional review. Queued example creation and
  both refinement paths capture the request generation before launch, so cancellation
  before provider entry cannot start or save stale work.
- Five original animated backdrops: Saffron dunes (wind/birds), Alpine cableway (gondolas),
  Coral twilight (fish/bubbles/rays), Lantern courtyard (swaying lights), Crystal cavern
  (mineral glints/reflections). Existing background picker, dimming and saved identities
  retained. Static vectors are cached independently; each detail frame has20–37 Canvas
  primitives, no new clock/bitmap/blur/simulation. Existing lifecycle/thermal pause applies.
- Existing Computer panel backs off repeated idle reads10→20→30s and connection errors
 6→12→24→30s. Active tasks retain3s polling; actions, manual Refresh and screen re-entry
  refresh immediately. It still stops polling when the screen is not active.
- Quiet manual Ubuntu commands no longer trigger the legacy silence-based auto-disconnect:
  deleting an owned PTY can kill a long-running command, and silence is not proof of idle.
  Ubuntu output updates activity bookkeeping. Explicit disconnect remains available.
- Power controls no longer promise zero CPU or a sleeping server merely because the
  terminal disconnected. They identify legacy connection control, warn that manual
  commands/legacy steps can stop, and distinguish separate OpenCode panel tasks.

## Termux heat: not yet solved or measured in this batch

The setup script now chooses OPENCODE_DISABLE_FFF=1, using OpenCode's ripgrep backend.
It enumerates workspace filenames at initialization and performs searches on demand.
This removes dependence on the persistent native search index, not task capabilities;
the same model, loopback authentication and ask-first permissions remain. Existing
running servers are unchanged, because rerunning setup deliberately refuses occupied
ports rather than killing someone else's work. A future launch change is not a live fix.

Verified against official1.18.31 source:
[backend selection](https://raw.githubusercontent.com/anomalyco/opencode/v1.18.31/packages/core/src/filesystem/search.ts)
and [flag definition](https://raw.githubusercontent.com/anomalyco/opencode/v1.18.31/packages/core/src/flag/flag.ts).
That version already disables content indexing; older broad-home indexing reports do NOT
prove the cause on this phone. Do not make that attribution without measurements.

New scripts/profile_termux_phone.py samples every Termux-UID process plus Meow, without
reading argv, credentials or personal files. It reports inaccessible proc CPU as unknown,
handles PID reuse and records thermal/USB context. Two pure parser tests cover scope and
missing data. On reconnection, collect a baseline before restarting anything, inspect task
ownership and duplicate servers, apply only a targeted safe change, and compare idle versus
active-task samples. Preserve demanding tasks and Android thermal protections.

## Validation

Final full regression:397 suites,2,693 tests,2,688 passed, five skipped, zero failures/errors.
Gradle successful in1m50s; app and instrumentation APKs assembled. Two profiling parser
tests, setup shell syntax and diff whitespace checks also pass. Phone remained absent
on final check; no real provider/phone/thermal acceptance is claimed for148.

Initial focused suite:82 tests,80 pass, two failures. One incorrectly used an exact-match
assertion on a substring. A candidate/refinement dialog never reached Compose idleness;
freezing its test clock did not solve it. The subsequent full run had2,688 tests, one
failure and five skipped. Replacing the production wrap-content AlertDialog with a
screen-bounded Dialog fixed the observed test failure while retaining the real Canvas,
editable refinement and approval controls. Do not claim a specific framework root cause.

Focused rerun passes all26 checks (five review UI,21 avatar integration), including real
preview rendering, failed refinement retaining input/candidate, landscape at150% text,
retry state and parent-task timeout with no save. No timeout was increased or assertion
removed to obtain the pass. Profiling parser tests: two pass. Setup shell syntax passes.

A subsequent full run exposed a test-order issue before cancellation was even invoked:
the test chose the review answer and immediately asserted an asynchronously launched task
had started. It now drains scheduled work, first asserts the review exists, then chooses
and asserts the actual busy state before cancellation. No production cancellation code
was weakened. That rerun had2,690 tests, zero failures/errors and five skipped. Further review then found a
separate real cancellation-before-dispatch gap: three new queued-dispatch tests failed,
proving unwanted model calls after cancellation. Each launch now passes the captured
generation to the existing ownership guard. Final regression evidence includes those
three tests, rather than relying only on cancellation after generation has started.

Artifact148 is com.example.meowmba1.3.92, with the unchanged signing certificate SHA256
b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff. Runtime comparison
against installed147 checks148 protected entries: none missing,147 identical, and the
one explicitly reviewed setup-phone.sh change described above. The strict guard correctly
returns false for that change; its raw report is preserved rather than silently rebased.
Packaged/source setup script SHA256 both equal
ca6e59e6d838662dba4b4eeaefddba98fb77739022c481309290f69de7cab0b6.
Final APK SHA256:c6034d1dbd045103573bb9187d0e7941ec5a4d4fa3633120e722a02c500d4452.
Archive:/Users/prog/Meowmba-Releases/drive148-20260919-RwdioX/.
Do not infer live phone or provider success from unit/native rendering tests.

## Remaining work

The52-item backlog remains active. This batch does not complete sustained heat/voice
acceptance, large streaming outputs, unified projects, clean-device setup or arbitrary
long-running cross-app autonomy. Physical robot control remains quarantined. No Play upload.
