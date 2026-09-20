# Preview149 — speech crash repair

September 19, 2026. Recovery checkout `/Users/prog/AndroidStudioProjects/Meowmba-repair-20260914`,
branch `recovery/known-good-138`, base `ee5abe2`. Sole build owner: Codex.

## Device evidence

Phone RFCY61NJEVM was running preview147 (1.3.91), installed September 19 at12:57:50.
Preview148 had not been installed. Original crash/exit records and installed147 APK are
preserved in `/Users/prog/Meowmba-Releases/crash147-20260919/`.

-14:45:09: `SpeechRecognizer should be used only from the application's main thread`.
 Idle mood timer ran on Dispatchers.Default and called speak → stopAlwaysOnListening →
 SpeechService.SessionEngine.stopListening. Android rejected the recognizer call.
-14:50:18: `CompletionHandlerException`, rooted in `IllegalStateException: Unbalanced enter/exit`.
 speakStreaming stopped RefiningSpeechEngine; SpeechFinalizer cancellation closed Ktor's
 RawSourceChannel over Android HttpURLConnection while its gzip/chunked response was being read.
-16:18:10: separate background LOW_MEMORY process exit. This patch does not establish its
 cause or claim to solve whole-device memory pressure.

## Repair

The real-time idle timer remains on Default, but delegates each tick to runIdleMoodTick,
which owns mood/UI updates and the eventual speech handoff on Main.immediate. Eligibility
is checked again after fact generation, so a newly active user interaction wins.
Existing fact history I/O and provider inference retain their existing I/O dispatchers.

Cloud speech clients (OpenAI ASR, Gemini ASR, OpenAI native audio) explicitly select the
already-bundled Ktor OkHttp engine. They retain their existing timeouts, redirect policies,
providers and credentials. This removes auto-selection of Android's unsafe stream-close
path; it does not swallow cancellation exceptions or allow stale transcripts to deliver.

Regression coverage adds a real dispatcher handoff assertion and a post-generation stage
eligibility check to VoicePipelineTest. SpeechCancellation149DeviceTest uses loopback-only
partial gzip/chunked responses:12 cancellation cycles, cancellation from Main, and a fresh
successful request on the same client after every cancellation. No cloud traffic or sensing.

## Validation and installation

Full regression:397 suites,2,695 tests, zero failures/errors, five skipped. Gradle built the
app and instrumentation APKs successfully in2m31s with MWDAT enabled. All148 protected
runtime entries match preview148 exactly. Against installed147, only the already-reviewed
preview148 setup-phone.sh change differs. Against original138, no entries are missing;
the two native bridge differences predate this patch and match148. Raw reports are retained.
Signing certificate matches installed147 and the original preview certificate:
`b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
APK SHA256:`596c5366ad0bbbc7d5bbc77d9ed46d76dae61cd63e2e3f4854f9b4ada9f2965b`.

Installed149 (1.3.93) in place on RFCY61NJEVM at18:20:27. No uninstall, data clearing,
preference change, server restart, sensing enablement or Play upload. The on-phone HTTP
cancellation test passes in1.398s:12 partial-gzip cancellation/reuse cycles, no unhandled
exceptions. Cold start completed in1.388s;90 seconds of observation retained the same process
(PID2266) and foreground activity in19/19 samples. No new crash-buffer entries appeared.
This is bounded crash acceptance, not all-day thermal or memory-pressure validation.

Immutable APKs and evidence: `/Users/prog/Meowmba-Releases/speech149-20260919/`.
Source is local, uncommitted work on baseee5abe2; source.patch/source.json capture provenance.
This also includes the previously prepared148 UI/background work; it is not a Play release.
First focused run used an unconfined fake Main dispatcher (which runs on the caller thread),
so the thread assertion correctly failed for the harness. Using a queued test Main dispatcher
makes the cross-thread test meaningful; all40 focused voice/refinement tests then passed.
An initial debug artifact omitted optional MWDAT dependencies; the runtime verifier rejected it.
It was NOT installed. The full build uses the existing private MWDAT properties file explicitly.
