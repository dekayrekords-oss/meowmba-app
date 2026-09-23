# Idle heat trace and fixes — September 22, 2026

Branch `perf/idle-heat-20260922` (worktree `Meowmba-perf-20260922`), based on
`recovery/known-good-138` at `637697f`. Not built into an APK, not installed. The
integrator owns the build, install and on-phone measurement below.

## What was measured

Read-only trace of the installed preview (`com.example.meowmba` 1.3.96/152, debuggable)
on the Fold7 (RFCY61NJEVM), folded on the cover display (1080×2520, 120 Hz). Lumen avatar
over the live-weather background, Reflex badge visible, mic and camera off, USB power.
Android thermal status 0 at start and end, skin 37.9°C, after a 45 s settle.

| Measure (60 s idle) | Value |
| --- | ---: |
| Process CPU | 55.5% of one core |
| Frames drawn | 32.3 per second (1,519 janky) |
| RenderThread / main / hwuiTask | 22.5% / 22.1% / 9.3% of one core |
| Background loops (drives, watchdog, binder, GC, JIT) | under 2% combined |

`dumpsys gfxinfo framestats` showed 120 consecutive frames exactly 3 vsyncs (25 ms)
apart, alternating heavy (~6 ms record) and light (~2–4 ms) draws. The avatar clock and
the weather `SceneClock` each admitted ~20 FPS on alternating vsyncs, so the window
redrew at 40 FPS while no layer moved faster than 20. Each draw costs 2–15 ms of
RenderThread and 4–10 ms of GPU.

simpleperf (`task-clock:u`, 250 Hz, 30 s, call graphs) of the main thread:

- 45% of main-thread samples are in libart: switch interpreter, generic JNI
  trampolines, CheckJNI and stack walking. This is the cost of running a debuggable
  build daily; a non-debuggable build does not pay it.
- 36% is the avatar draw lambda: `LumenPainter.draw` 28%, of which Compose color
  `lerp` (Oklab convert and back) is 8.7% of the whole thread.
- ~16% is Compose frame dispatch, mostly waking the avatar and scene loops on
  every 120 Hz vsync only to refuse the frame.
- 7% is `View.setRequestedFrameRate`: Compose 1.11 calls it twice per draw on
  API 35+, and this Samsung Android 16 build logs every call at INFO with
  `Debug.getCallers`. Logcat held 112,072 such lines from Meow in 24 minutes.
- 1.6% is `LumenReflexBridge.summaryLabel()`, two `String.format` calls per frame.

## Changes (no visible difference intended)

1. **Shared frame phase** (`AvatarFrameCadence.kt`, `SceneClock.kt`,
   `AvatarRenderer.kt`, `MainActivity.kt`). The avatar clock is the leader and keeps
   its exact admission rule. Background clocks at the same interval draw on the
   leader's vsyncs; at a different interval (performing avatar) or with no leader they
   use their own rule as before. A follower never joins sooner than the active interval.
   Each layer keeps its frame rate; only which vsync the background draws on changes.
   Provided only under `CompanionScreen`; previews, onboarding and isolated tests
   keep private clocks.
2. **Sleep through refused vsyncs.** After an admitted frame the avatar, scene and
   ear loops sleep until two vsyncs before the next admissible one. The avatar's sleep
   is cut short when a clip or expression starts or stops, so performances still begin
   on the next vsync. Tests prove the admitted vsyncs are identical to polling.
3. **Exact color memo** (`ColorLerpCache.kt`, used by `LumenSpeciesParts`,
   `LumenAnimalParts`; one-entry iris memo in `LumenPainter.lens`). Returns the value
   Compose's `lerp` computed earlier for the same three inputs, bit for bit.
4. **Reflex label** reformats only when the action, a half-unit rounding bucket or the
   locale changes. The text is identical to a fresh format.

Expected effect at idle: roughly half as many window redraws (RenderThread, hwuiTask,
GPU and the Samsung per-draw logging scale with draws), far fewer main-thread vsync
wakeups, and ~10% less main-thread drawing work. **Not yet measured on the phone.**

## Verification so far

- New tests: `SharedFramePhaseTest` (7), `ColorLerpCacheTest` (2),
  `ReflexSummaryLabelTest` (3). They cover leader equivalence with the private
  cadence over random mode/thermal/jitter schedules, follower alignment and rate,
  sleeping versus polling equality, bit-exact colors, and label equality including a
  locale switch.
- Full JVM suite on this worktree: 406 suites, 2,742 tests, 0 failures, 0 errors.
- `compileDebugKotlin` passes. No APK assembled, no device test, no lint run.

## How to measure after installing

Same conditions (folded, same avatar and background, thermal 0, USB, 45 s settle):

```bash
ADB=adb python3 scripts/sample_idle_threads.py com.example.meowmba 60 after.json  # CPU, threads, frames/s, thermal
adb shell dumpsys gfxinfo com.example.meowmba framestats          # frame spacing, not 3-vsync alternation
```

Expect frames/s near the avatar's own idle rate rather than double it.

## Recommended next, not done here

- **Daily build.** The phone runs the debuggable preview as its everyday app. ~45% of
  main-thread CPU is debug-runtime overhead. A non-debuggable, R8-shrunk variant with
  the preview id and debug signing would keep app data and remove it. Build owner's call.
- **Samsung frame-rate logging.** Unfixable in-app with Compose 1.11 (ARR voting is
  unconditional on API 35+). Fewer draws is the only lever; worth reporting to Samsung.
- **Music awareness (1.3.97+).** Polls `getActiveSessions` plus playback state and
  metadata (album art bitmaps) over binder every 2 s in the foreground. A
  `MediaController.Callback` / active-sessions listener would make it event-driven.
- Remaining per-frame costs are small individually: per-frame gradient shaders in
  the weather sun rays and plush surfaces, 96 iris fibre `drawLine` calls, and
  `WeatherScene` `forDepth` list allocation.
