# Project workspace, local inference lifetime and robot motion — September 8, 2026

## Scope and evidence

User identified the local model as the source of heat and requested more natural
robot-base motion plus a usable project brain: clear feedback, a home for assets,
and persistent plans/progress. This revision targets avoidable work and missing
state/UX, not model-quality reductions or a second autonomous executor.

The connected device is **SM-F966U1 / RFCY61NJEVM**, installed preview
`com.example.meowmba` **1.3.50/code54** at inspection. It is not the earlier Flip6.
Initial idle samples showed app CPU6–7%, thermal status1, current HAL AP39.6°C,
skin38.8°C and battery34.4°C. Later idle readings were status0, AP32.2°C,
skin33.8°C and battery30.2°C. The old process had about1.31GB of native-heap
SwapPss. These are old-build idle observations, not matched generation benchmarks
and **not evidence that the new code has resolved overheating**. Cached thermal
temperatures are stale; measurements above use current HAL readings.

## Local inference changes

- MediaPipe's Java dispatcher did not serialize asynchronous native lifetime.
  Closing its callbackFlow did not cancel generation; separate wrappers could
  duplicate engines, and release retained a closed lazy engine reference.
- Both MediaPipe and LiteRT now share one resident native engine and a generation
  lease. Cancellation requests native stop and waits for its terminal callback
  before releasing the conversation/engine lease. Closing underneath JNI is not
  used as a shortcut.
- Value-returning calls use cancellable asynchronous sessions. Engine ownership
  survives cancellation during non-interruptible initialization. Idle time is
  shared and measured from completion, not a wrapper's creation/start time.
- VM housekeeping covers both runtimes and closes native resources off Main.
- Superseded response turns signal only their own native scopes. Remote Ktor
  requests retain the existing safe drain behavior, while epoch fences reject
  stale replies/tool proposals. Cancelled OmniRoute calls cannot start fallback
  inference. No abandoned queued native turn should load another engine.
- The authoritative final MediaPipe text is flushed to partial-driven consumers;
  bounded callback backpressure coalesces text without dropping reply fragments.
- Model choice, sampler settings, existing output budgets, GPU-first policy,
  native inference threading, avatar quality and cloud-consent policy are unchanged.

Local inference still consumes real CPU/GPU power. Native cancellation callbacks,
sustained-generation temperatures and responsiveness need physical-device testing.

## Robot base

Existing neck commands now use monotonic minimum-jerk targets (zero endpoint
velocity/acceleration), within the existing ±0.35 envelope and250ms command
spacing. Fast beats are attenuated to the existing command-speed budget instead
of snapping or running beyond the clip. The existing scheduler/connection lease
owns every sample; interruption and braking remain immediate. Bounce recovery
cannot run after the final brake.

The [official LOOI product description](https://looirobot.com/products/looi-robot)
describes coordinated sensing, animation and physical behavior, but does not
establish a public closed-loop neck controller or validate this app's sensor
decoder. These changes interpolate supported position commands; they do not
establish parity with the official app. No protocol bytes were invented, no
physical moves were performed, and unknown-sensor translation blocking remains.

## Persistent project workspace

- Projects now has a dashboard with an editable objective/decisions, proposed
  checklist, current observed actions, real task history, and files/results.
- New-project UI and natural “help me with a project about…” requests create a
  stable project folder and draft a plan with the selected model. Drafting does
  not execute the plan. Structured local planning avoids the conversational
  sentence cap; missing models/errors keep the saved brief and offer manual edits.
- “Work on this step” is text input and works with the microphone killed. It uses
  the existing model router/tool permissions/approvals, not a new shell or executor.
- Checklist checks are explicitly user confirmations. Completed tool actions are
  counted separately; neither implies the entire project is verified complete.
- Plan edits by AI use `project.plan`, always require approval, and cannot choose
  another project, set completion flags or change runtime task status.
  `project.overview` reads the full bounded saved plan plus observed state.
- Task/project/conversation scope is captured at submission and follows coroutine
  dispatch. Switching projects cannot reassign queued work, approve changes for
  an ambiguously named project, inject a late reply into another chat, or retarget
  output files. Late model proposals after a project switch are rejected.
- Model context includes a bounded quoted project brief, plan, observed statuses
  and file paths. Saved data never supplies fresh execution permission.
- Progress checkpoints are bounded, fsynced and atomically replaced in private
  `filesDir/project_state`, outside the agent-writable Files workspace. Restart
  marks unfinished tasks INTERRUPTED; no tools or approvals are replayed.
- New text/media/comic/script/email outputs stay in
  `Files/projects/project-ID`. Generated assets use unique names so repeated
  titles do not overwrite earlier versions. Existing elsewhere-in-workspace
  artifacts are indexed as links, never moved silently.
- “Open project files” opens that exact folder; result cards open the real file
  through the existing safe viewer. “Plan & files” is reachable directly from chat.
- Muting speech no longer suppresses persistence of the written assistant reply.
- Private files are removed by uninstalling: the dashboard explicitly explains
  import/export and directs users to Files for backups/sharing.

This is a persistent project-work foundation, not proof of unattended end-to-end
business operation, connected accounts, externally delivered messages, or
automatic semantic verification of every plan step. Existing approval and service
availability limits still apply. Plan steps require selection/review; there is no
unbounded unattended executor.

## Validation / release gates

Source version: **1.3.52/code56**. Integrator ran the full unit/Robolectric suite
and debug assembly: **1,652 tests, 1,647 passed, five skipped, zero failures/errors**.
Build completed in43seconds. Project dashboard/plan/files native Compose renders
were inspected, and all five project UI tests passed, including full-screen
project creation/editing. Focused tests cover real tool approval/project scope,
late routing rejection, mic-killed text actions, restart recovery, compare-and-set
draft saving, native-only stop signals, bounded callback bursts, runtime lease
ownership and motor-command safety. These are not physical motor, native model,
OAuth or heat acceptance tests.

Verified package `com.example.meowmba`, versionCode56, versionName1.3.52, minSDK31,
targetSDK36. APK SHA256:
`e4c6cf6c91c4c753128ba46a162a4974f54a7bd87e1d10513f8b68dac3a303f2`.
Debug signing certificate SHA256:
`75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.
Verified packaged arm64 LiteRT, MediaPipe LLM, MediaPipe vision and sherpa JNI
libraries; build reports MWDAT enabled. `git diff --check` passed.

Archive: `/Users/prog/Meowmba-Releases/project-brain-1.3.52-code56/`, containing
the APK, test XML, this report and two native project-dashboard render previews.

Installation was offered separately and is awaiting explicit approval. No phone
data was cleared, microphone enabled, robot actuated, release published or repo
pushed. Bounded `git fetch --all` failed:192.168.1.69 SSH timed out. Meow sync and
production signing remain separate from this local preview build. Blender/3D
integration remains excluded as requested.
