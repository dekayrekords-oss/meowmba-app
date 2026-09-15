# September 14 audit repairs — verification record

This work starts at `7748ed3d97acb7555c3e4568256199f047bccc98` (preview124),
the latest fetched source during the September12–14 audit. The older Mac checkout
and unrelated server worktrees were preserved. Android source stays private.

## Implemented repairs

- Explicit OkHttp transport for RemoteModelProvider. The phone's preview122
  crash was a main-thread cancellation-handler failure in Android URLConnection/
  Okio AsyncTimeout (`Unbalanced enter/exit`). Real chunked-HTTP cancellation,
  timeout and subsequent-request recovery are exercised on JVM and Android.
- Streaming stops on completion/error rather than waiting for a proxy to close
  the body. SSE accepts `data:` without a following space. Truncated connections
  report failure. Ollama's final response characters are retained.
- Generated props now have a visual preview, Use/Put away actions, persistent
  selection, validated attachment anchors, bounded portrait/landscape rendering,
  and motion with the floating avatar. Removing an equipped prop unequips it.
  Generated avatars are preserved when equipping; native3D attachment is not
  claimed. The Use action explicitly switches incompatible styles to floating.
- A fully validated avatar/prop can survive a timeout in optional quality review.
  The UI explains that extra polish timed out. Invalid/incomplete output never
  becomes geometry; user cancellation and later selections still prevent saving.
- Closed chat no longer discovers terminal profiles on every recomposition.
  Discovery runs off the main thread when chat opens and refreshes on reopening.
- Privacy-only rendering skips invisible Lumen anatomy/lenses. Privacy badges
  remain fixed and visible. Existing native image tests cover hidden, moving,
  driving and costumed poses.
- Disabled Computer no longer constructs Room to clear a callback. ViewModel
  elapsed time is injectable without changing Android's production time base.
- Exact app-name matches take precedence over related aliases; actual duplicate
  labels remain ambiguous. Fixed stale UI/boundary tests and leaked test scopes
  without deleting assertions about project deduplication, speech, memory or bugs.
- Added `scripts/verify_apk_runtime.py`: fail before installation if native/model/
  asset entries differ from the explicitly selected installed baseline.

## Build and evidence locations

Mac worktree: `/Users/prog/AndroidStudioProjects/Meowmba-repair-20260914`.
Build worktree on Meow: `/home/prog/meowmba-repair-20260914`.
Private origin: `meow:/home/claude/git/meowmba-app.git`.
Raw acceptance reports/APKs/traces:
`/Users/prog/Meowmba-Releases/repair-20260914/`.

Use JDK21 `/home/claude/java/jdk-21`, Gradle home
`/home/claude/.gradle-epic-ef`, and **this worktree's** `.native-preview` for
`-PprebuiltDebugNativeDir`. The old fruit-fly native bundle lacked both
`libonnxruntime.so` and `libsherpa-onnx-jni.so`; it must not be substituted.
All13 native libraries were recovered from the actual installed preview122 APK.
The installed baseline has74 protected lib/assets entries. Do not replace its
newer needle/sd libraries merely to match an older build120 hash manifest.

The debug application is `com.example.meowmba`; release is `com.meow.meowmba`.
Release version85 is unchanged. Signing certificate SHA256:
`b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.

## Completed verification before final preview128 acceptance

- Baseline:2509 Android tests with52 failures. Preview125:2512/0; preview126:
  2513/0; preview127:2514/0. Every reported full run had zero errors/skips.
  Final128 counts and phone acceptance are recorded below after completion.
- Preview126 installed over125 using USB on SM-F966U1/RFCY61NJEVM, with all74
  lib/assets entries byte-identical to122. No data clearing.
- Preview126:14 phone checks passed:10 streaming cancellations plus timeout and
  recovery, generated-prop use/recreation/delete, bug persistence, route-free
  traffic health, selected ASR, rapid microphone restarts, local Whisper decode,
  five real-GPU cases, paused sensor smoothing, and opening Gmail from chat.
  Gmail contents were not inspected.
- Additional126 checks:all avatar styles/animals in both habitat modes passed
  real tap/pet/play/menu gesture dispatch; Computer success/failure/cancel and
  persisted chat replies passed; authenticated Computer worker execution passed
  through USB forwarding plus SSH to Meow. Unauthenticated health returned401.
- Live remote neural acceptance failed at the explicit precondition **computer
  bridge not paired**. No remote activation or robot output occurred.
- Local Whisper and configured Gemini cloud each recognized four of four
  phrases from synthetic4.4-second16kHz speech. The cloud selection initially
  failed to persist from a test-only preference write; selecting via the real
  ViewModel setter fixed acceptance setup. A new process then read
  `gemini_cloud` and recognized4/4 in2349ms. No real room recording was uploaded
  for these tests. No OpenAI key was configured; Gemini used configured OpenRouter.
- Arizona511 work-zone and OSM sources responded without an open route at a
  public New River reference point. Both returned zero nearby hits. This verifies
  source availability, not speed-trap/police coverage or incident completeness.
- Logic adapter:45 Python tests passed; Computer worker:5; neural scripts:
  26 passed/1 pyarrow-dependent converter test skipped out of27; robot simulation:8 passed.
  System Python3.9 lacked numpy; neural discovery was rerun successfully using
  the bundled dependency Python. Simulation is not physical robot acceptance.
- Preview125 phone synthetic neural comparison:RATE p95~93µs, SPIKING p95~389µs,
  2000 samples each. This measures local implementation latency, not improved
  intelligence, learned personality or biological fidelity.

## Performance evidence and limits

The ten-minute125 run was charging and lost its original activity's foreground
focus (23/61 foreground samples). It averaged60.4% of one core,11.6 presentedfps,
and had no severe thermal samples; **battery validity and sustained-motion gates
both failed**. A15-second sampling profile ran during that measurement, adding
overhead. This is not a valid unplugged endurance result.

The125 main-thread trace exposed267.7ms of repeated terminal-target discovery
and478.0ms of invisible lens drawing during a3D pass. The126 trace contains no
terminal-target/Room-profile discovery in the closed-chat window. Its selected
avatar was visible Lumen eyes, so lens times cannot be compared to the earlier
3D privacy-only pass. Do not publish a whole-app CPU improvement percentage from
these unmatched workloads. Android method-trace output is asynchronous after
stop: wait for the file to finish before pulling it. Never disable perf hardening
or simulate battery unplug to obtain a passing result.

## Work that is not closed by this repair

- The first live fishing-prop test on126 timed out on the configured OpenRouter
  route at~103s. Final128 live generation also timed out; mocks/render
  fixtures do not substitute for actual model output.
- Unplugged matched thermal/battery endurance, noisy/far-field speech, long
  silence, interruption and real-user transcription accuracy need sustained
  acceptance. A four-phrase synthetic check is not parity with ChatGPT voice.
- Phone bridge pairing/full remote neural acceptance, held-out behavior and
  learning evaluations remain open. Physical robot commands remain quarantined.
- Computer P1 scoped workspace file tools and stronger multi-step/web planning
  remain queued; this patch does not present one successful command as a full
  autonomous business workflow.
- Full Logic Pro timeline/bus/automation/mix/master/bounce acceptance and arbitrary
  plugin coverage remain open. The45 adapter tests do not verify a finished song.
- Broader account integrations, real financial/posting/booking workflows,
  store submission and real app-demo publication are not established here.
- SSH to the separately requested `100.77.97.236` timed out. Private Meow LAN
  storage is reachable; it must not be described as a successful meow-nb upload.

## Final preview128 acceptance

- Full Android suite: **2516 tests, zero failures/errors/skips,357 suites**.
  Both debug and instrumentation APKs assembled successfully.
- APK SHA256: `584f2f043dc7138250fd9f8dc16753b126c1494035f582e59f68490d39229ff4`.
  Signature and package/code128 independently checked. Runtime comparison against
  the actual installed122 APK:74 entries, zero missing, zero changed.
- Installed128 on RFCY61NJEVM over USB at21:28:59 Arizona time, with no data
  clearing.14 core phone checks passed again. One additional live invent check
  failed:the selected `openrouter/free` route, Higher quality, produced no
  validated fishing prop within102.9s. No success was fabricated or preset
  substituted. Validated-design timeout recovery is separately regression tested.
- A direct non-streaming JSON request to the same free route returnedHTTP200
  but only176 whitespace characters and zero non-whitespace characters in45.8s.
  This independently reproduced a provider response that stays alive without
  delivering a design. The first diagnostic had only a per-read timeout and was
  explicitly stopped; the corrected diagnostic has a total budget and bounded
  response storage. No paid fallback or model preference change occurred.
  OpenRouter documents that [its free router randomly chooses compatible free
  models](https://openrouter.ai/docs/cookbook/get-started/free-models-router-playground),
  including filtering for requested structured output. JSON mode did not resolve
  the observed lack of output in this test.
- Final cloud ASR after installation:Gemini cloud,4/4 synthetic phrases,2184ms.
  Local capture/restart and native Whisper tests also passed. The OpenAI key is
  absent; do not describe this as OpenAI voice or broad speech-accuracy parity.
- Neural acceptance now checks the production dedicated-server selection rather
  than the older desktop-only precondition. Result:paired=false,dedicated=false,
  empty endpoint. Pairing is required; no remote or physical mode was activated.
- Final120-second foreground check:12/13 foreground samples,1462 presented
  frames(~12.2fps),71.1% of one core, no severe thermal samples. Charging invalidates
  battery measurement; foreground/sustained-motion gate failed. The run was not
  profiled. The app still needs power/performance work and an unplugged matched
  run; do not claim overheating is fixed or compare this to125 as a controlled A/B.
- Temporary worker test credential and USB/SSH forwarding were removed. The raw
  evidence is retained privately in the release directory. Source checkpoint
  `a192734` was pushed to the private Meow branch; later documentation/test-only
  commits finalize this record.100.77.97.236 remained unreachable over SSH.
