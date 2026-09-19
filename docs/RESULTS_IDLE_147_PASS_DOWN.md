# Results, home layout and idle efficiency — preview147

September19,2026. Integrator: Codex. Source09db18d, recovery/known-good-138.
Installed preview com.example.meowmba1.3.91/code147 at12:57:50 on RFCY61NJEVM.
User authorizes automatic in-place installation/testing of subsequent builds, without
repeated confirmation, preserving data and monitoring-off state. No public release authorized.

## Delivered

- Computer discovers completed write/edit AND known apply_patch metadata, including
  multi-file add/update/move/delete. It does not interpret prose, arbitrary tool output,
  patch source text or foreign paths as a verified artifact. Reported candidates stay
  explicitly unverified until retrieved and saved.
- Direct Save & preview cards, persistent action feedback, automatic preview visibility,
  bounded document/image preview, compatible viewer opening and Files navigation.
  Detailed progress is collapsible so a long transcript cannot bury the results.
- Downloads are bounded, hashed, read back, stored under shared Files/projects/opencode
  and deduplicated by source path/content. Changed copies preserve older files; mismatched
  existing copies fail without overwrite. Long UTF-8 names retain their extension within
  Android's filename limit. No broader transfer size or credentials/tool grants were added.
- Computer panel restores the verified saved loopback endpoint before constructing its client.
- Clock, presence, Stop Computer and transient cards use measured layout rather than
  independent fixed offsets. Tall cards scroll under the header; a new crash alert resets
  that scroll and takes priority without changing saved weather/drive state.
- Unused legacy ear-follow frame loop is inactive when another renderer owns the face.
  Neural trace sample/array allocation is skipped when recording is off; recording-on
  data and controller outputs are covered by equality tests.
- Profile-guided fly eye optimization: reuse facet paint and canvas while preserving
  geometry, colors, alpha, transform order, clipping and every individual facet draw.
  No animation cadence, quality settings or Android thermal thresholds were lowered.

## Verification and intermediate failure

- Final full suite:390 suites,2,646 tests;2,641 passed, five skipped, zero failures/errors.
- Fly renderer:129 native old/new exact bitmap comparisons, plus matrix, clip and
  subsequent-drawing checks. Not a visual-quality reduction masked as an optimization.
- All148 protected runtime assets match145; no voice/model/native libraries removed.
- Matching certificate SHA256:
  b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff.
- APK SHA256:0f595f4b5f584ae278ae962c4967f3d9e65d884016b5925739260f6f8cc7fb1f.
- Seven live phone checks pass in46.224s: actual approved synthetic file generation,
  retrieval, local store/repeated-save/readback and no-tools context follow-up; Ubuntu
  PTY; no-tools worker reply; appearance/weather/home privacy; setup/recovery/privacy;
  isolated onboarding fit; paused renderer work bound.
-146 passed desktop tests but its hard-link publication failed with AccessDeniedException
  on this Android device.147 replaces it with the no-replacement same-directory move API.
  The failure is retained in146's evidence archive; do not erase it or call146 verified.
- App installs were upgrades, not uninstall/reinstall. No credential reset, Termux/server
  replacement, user-file deletion, monitoring enablement, account action or robot movement.

## Thermal evidence and limits

The user accepts warmth under demanding requested work. The target here is unnecessary
idle cost, not disabling safety or promising that charging/ambient temperature never warms
a phone. Hidden/idle and requested heavy workloads must be measured separately.

Baseline145:120s sample,49/60 foreground observations;54.38% of one CPU core across94.2
valid seconds, battery35.7→36.0°C, thermal1→1, USB connected. Mixed foreground/rotation/
random-form conditions prevent a controlled before/after or unplugged battery claim.

The usable sampled ART trace is meow146-idle-art-v3.trace from145:49,228 records, dual
clock/14-byte v3 records. Main-thread compoundEye attribution including Compose descendants
was538ms, about62% of sampled Lumen-painter CPU. This led to the facet change, not guesses
about local-model activity. RenderThread is not measured by this sampled Java trace.
The first empty trace is discarded, not counted as evidence.

Post147: 180-second capture, 90/90 foreground samples, 178.69 valid interval seconds.
Mean process CPU 54.53% of one core, p95 interval 73.89%; battery 34.7→34.5°C, maximum
34.7°C, Android thermal status 1→0, USB connected. No evidence of a controlled overall CPU
improvement versus145; random forms, device rotation and baseline foreground gaps differ.
Cooling during this short observation is encouraging but not an all-day or never-hot signoff.

Graphics counter epoch was unchanged: total frames 734→8,016; janky frames 486→5,404.
That is 4,918 of 7,282 interval frames (67.54%) classified janky by Android. End cumulative
p95 CPU frame duration was25ms and GPU9ms. These counters do not identify the cause or
establish observed FPS; frame pacing/render-thread cost remains an explicit follow-up.
One end snapshot showed RenderThread17.8%, main10.7% and hwuiTask1 3.5%; this is not the
whole-session mean. Do not hide the pacing result behind the passing pixel tests.

## Actual visible-result acceptance

On the installed147 app, used Computer → Tasks & approvals to submit a unique synthetic
text-file task. Only its exact-file edit was approved once; no shell, unrelated file access
or broad permission was granted. The completed-tool card appeared without opening the
detailed transcript. Save & preview retrieved and verified23 bytes and automatically
scrolled the real text MEOW_147_VISIBLE_RESULT into view.

Files opened the library. A local filename search found the exact saved23-byte file.
Opening it offered Android's viewer chooser; Chrome was selected Just once and visibly
rendered the same text through the app's content URI. No default viewer was changed.
The synthetic task was already complete; its session and file remain available:
projects/opencode/ses_f44bc22d3ffe0ys23QoAZM5CLC/
ad0c74f4c180-345b5faedc59-meow-ui147-20260919.txt.

Returned to the avatar, cleared only the temporary search, and confirmed MIC OFF/CAM OFF.
No Meow fatal entries in the sampled crash buffer. This proves the small text workflow;
it is not a claim that all image/video formats, large exports or every viewer are validated.

## Still open

- Large images/videos/models still need a separately designed bounded streaming transfer;
  this batch preserves the existing approximately1.5MB Computer export UI limit.
- Computer sessions are not yet fully joined to the app's Room project/task ownership.
  No fabricated completed project records were added just to make dashboards look complete.
- The Files button opens the library root, not the exact session folder. The saved path
  and local filename search work; direct folder navigation remains a UX follow-up.
- Broader generation, lifecycle, fresh-device onboarding and sustained heat/voice acceptance
  remain in the52-item backlog. Physical robot control remains quarantined.

## Evidence

147:/Users/prog/Meowmba-Releases/results147-20260919-8CDF9a/.
146:/Users/prog/Meowmba-Releases/results146-20260919-ZFX8ax/.
Private source destination:prog@100.77.97.236:/home/prog/git/meowmba-app.git,
branch recovery/known-good-138. No Android source pushed to public GitHub.
