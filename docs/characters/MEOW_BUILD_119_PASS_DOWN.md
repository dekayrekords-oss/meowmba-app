# Build 119 — describe and become a new cartoon form

**Current status: implemented; all 261 local acceptance tests pass across 35
suites, with zero failures, errors or skips. Not installed.** The owner asked
to continue without the phone. The Mac completed offline JVM/native-render
verification at source `a16cfbb` after the two older agent speech fixtures were
repaired and a missing test-selector group was corrected. Meow's SSH service
still fails to return its banner. Both packages assembled on Meow earlier, but
final signing/asset checks and the release archive remain pending. Do not claim
successful live generation or phone installation.

The owner requested that the avatar turn into anything the user describes.
This build extends `avatar.form` beyond a preset whitelist: explicit voice/text
requests and Avatar → Look → Become anything use the selected AI model to create
validated native cartoon geometry. Presets remain immediate; saved request/name
matches reuse the actual design without inference. These are animated vector
interpretations, not newly generated 3D meshes or guaranteed exact likenesses.

## What changed and why

- `AvatarFormRequest`, `AvatarFormTool`, `AvatarFormInventor`, and both tool
  prompts preserve rich descriptions, route to the actual on-screen avatar,
  validate one bounded JSON proposal and permit one repair. A tool cannot report
  success until the form has been saved and applied. New designs need a working
  selected model; errors leave the prior appearance intact.
- `GeneratedAvatarForms` permits at most 32 outlined ellipse, rounded rectangle
  or polygon parts. All geometry, colors, face placement, motion labels and
  payload lengths are bounded. Model output is data; there is no executable
  drawing code, remote texture, arbitrary file path or new tool permission.
- `GeneratedAvatarPainter` uses the existing Lumen frame for gaze, blinking,
  mouth, expression, perspective and neural wing beat. Arms, legs, antennae and
  tail use the same active clock. Habitat movement, touch play, avatar zoom and
  the fixed privacy markers remain in the existing renderer. The same neural
  system and memory continue across appearance changes.
- `GeneratedAvatarStore` keeps up to 24 forms, with content-derived IDs, atomic
  file replacement, bounded reads, corrupt-file rejection and no silent
  eviction. A separate encrypted selected ID restores the chosen form; selecting
  a preset clears that ID in the existing preference transaction.
- Setup has a description field, examples, explicit Create/Cancel, real status
  and errors, and saved-form selection/removal. Presets remain available while
  a model is working. Selecting or deleting a form does not change camera,
  microphone, neural mode, habitat choice or care preferences.
- ViewModel generation tokens, an independent native cancellation element and
  a scoped job reject stale results after cancellation, a newer choice, a new
  conversation or foreground exit. Validated save/select is a short serialized
  commit; no suspended late write may resurrect a cancelled form.
- The review exposed a pre-existing orchestrator leak: a tool throwing
  cancellation left its task RUNNING. Terminal-first, idempotent cancellation
  now releases approvals/slots, advances queued work only while the parent scope
  is active, and prevents late success from overwriting cancellation. The form
  tool has a 100-second deadline around its 90-second generation budget.

## Teach Meow: debugging this path

1. If the avatar only talks about a change, inspect the deterministic route and
   tool result. `avatar.form` is the appearance action; media generation makes
   files and cannot satisfy this request.
2. If a new form fails, check the chosen model and validated response. Do not
   fabricate a fallback shape under the requested name. The previous form must
   remain visible. Saved designs can be selected without network/model access.
3. If a late response replaces a newer choice, inspect `_form_generation`,
   `avatarFormGeneration`, the independent cancellation scope and conversation
   ownership. Presets need the same queued-request guard as generated forms.
4. If future tasks stay queued, inspect terminal task states and reserved jobs.
   Cancellation must release its slot exactly once, including cancellation
   before coroutine dispatch and during an approval wait.
5. If a design restores incorrectly, compare its canonical content hash and
   encrypted `generated_avatar_id`. Never use a generated display name as a
   filesystem path. A corrupt form is skipped; a preset remains usable.
6. If touch lands on scenery, inspect `GeneratedAvatarPlacement` against the
   exact draw center/unit. Privacy markers stay outside generated geometry.
   Frozen animation must use the frozen shared clock; never add a second timer.
7. A green Gradle result does not prove every intended filter matched. The old
   `ToolCallBrainTest` selector was a filename containing five differently named
   classes. Select the actual classes and compare XML names and testcase counts;
   the corrected acceptance script runs 261 tests and the archive verifier now
   requires all 35 suites.
8. If Meow is unavailable, use the existing offline Mac toolchain and bounded
   heap overrides described in `BUILD119_LOCAL_VERIFICATION.md`. Keep JVM-only
   native placeholders out of APK builds. Inspect exact pinned cache revisions
   before deleting reproducible objects; preserve source, assets and models.

## Verification and deployment

Native preview inspection passed for three distinct example forms with gaze,
blink and speech states, portrait creation, busy/cancel, failure/retry and
landscape large-text saved forms. These are deterministic renderer examples,
not output from a successful live AI request. Final local acceptance passed all
261 tests in 39.31 seconds at source `a16cfbb`, including all new
form/tool/inventor/render/store/setup/cancellation/integration checks, existing
camera/neural checks and the five previously omitted routing classes. Two old
`AgentViewModelTest` speech assertions had failed on Meow because
`SystemClock.elapsedRealtime` was unmocked; both now pass with the scoped-clock
and joined-shutdown fixture repair. Earlier fixture repairs also provided a real
temporary workspace so integration tests exercised production tool registration.

The local run used JDK 21, cached dependencies, one worker and 2 GiB Gradle/test
heaps. It did not assemble an APK or load production JNI. Evidence, all 35 XML
reports, eight previews and hashes are retained under
`build-out/habitat/build119/local-verification-final/`. An independent audit
confirmed actual testcase counts and the exact expected suite set. Detailed
commands, disk-space recovery and lessons are in `BUILD119_LOCAL_VERIFICATION.md`.

The existing Meow `Qwen3.8-Flash-Next` model returned no content in three
80-second requests and one 90-second streaming retry. Thinking was disabled.
Its process appeared stalled during observation; no specific underlying fault
is proven, and no service was restarted or reconfigured. New form creation
therefore still requires a responsive selected model. All raw test evidence is
in `build-out/habitat/build119/live` and
`/home/prog/research/meowmba-build119-live-forms`.

SSH to the authorized `prog@100.77.97.236` subsequently timed out waiting for its
banner at 8, 15 and 30 seconds, including a QoS-disabled retry. A direct socket
connected in 0.05 seconds but received no banner in eight seconds. A further
10-second retry after local acceptance also timed out. This blocks the Meow
packaging rerun, signing/asset checks, archive and final teaching-copy delivery;
it does not justify changing keys, hosts, authentication or restarting unknown
server workloads. Earlier code/passdown commits through `6425d6b` reached the
private bare repository; the last inspected working checkout was `9fc5ff1`.
Later local commits and this updated teaching document have not reached Meow.

Do not infer that Build 119 is installed. The last verified phone build remains
112 and the owner asked to continue without the phone, so no installation was
attempted during this continuation. Build 118's camera and portable MaleCNS work
is retained.
Physical graphics, camera, battery/thermal and robot checks require the device
or hardware and are not replaced by JVM/native render tests.

Worker details: `BUILD119_FORM_COMMAND_NOTES.md` and
`BUILD119_GENERATED_RENDER_NOTES.md`. Exact incomplete status is recorded in
`BUILD_119_VERIFICATION.json`.

## Resume on Meow

1. Reconnect using the existing `meow-nb` SSH key/alias. Do not use passwords or
   assume another LAN checkout is this build. Push the latest local commit to
   `/home/claude/git/meowmba-app.git`, ref `codex/fly-habitat-built`, then
   fast-forward `/home/claude/meowmba-fly-habitat` after confirming tracked files
   are clean. Preserve the three untracked production asset directories.
2. Use the established JDK 21, Android SDK and actual prebuilt native directory.
   Run `bash scripts/verify_avatar_forms.sh` with the established server Gradle
   options, then assemble debug and Android-test APKs. The script includes all
   35 acceptance classes and currently runs 261 tests. Do not use the empty
   local JVM-only native directory for packaging. The prior Meow log is
   `/tmp/meow-build119-acceptance.log`; it predates the final local pass.
3. Once all tests pass, run the retained
   `docs/characters/verification/verify_build119.py <HEAD prefix>` on Meow. It
   verifies version 119, the existing signing certificate, all 53 runtime assets
   and 19 painted backgrounds, creates the private archive and small Build-115
   deltas, and retains live-inference limitations. Do not run against failed tests
   or present an existing unverified APK as a finalized release.
4. Copy final verification JSON/previews/deltas locally, commit the final status,
   and deliver this teaching document and worker notes to Meow's research
   archive. The initial teaching notes are already in the private Git history.
5. Install only when the phone is available, preserving app data and the user's
   camera setting. Verify actual generation with a working selected model,
   saved-form reuse, touch/habitat behavior and camera/privacy on the device.
