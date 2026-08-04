# SDD Progress Ledger — Native On-Device Image Generation

Plan: docs/superpowers/plans/2026-08-03-native-on-device-image-gen.md
Worktree: /home/claude/meowmba-app/.claude/worktrees/native-image-gen
Branch: feat/native-on-device-image-gen

Note: The `superpowers:using-git-worktrees` and `superpowers:subagent-driven-development`
skills referenced by this plan are not installed in this environment (verified —
no such skill exists in the available skills list, no plugin directory found).
Proceeding by hand-executing the same documented process: isolated worktree
(done, via EnterWorktree), task-by-task implementation with dispatched
implementer/reviewer subagents where the task is well-scoped, controller
(me) doing build/spike work directly where fast iteration against real
tool output matters more than delegation (Task 1's native build spike).

## Task 1: Spike — build stable-diffusion.cpp for Android
Status: DONE
- Built a custom Docker image `meow-sdcpp-builder` (FROM the existing
  `memba-builder:latest` base image, which has JDK21+SDK cmdline-tools
  but NO NDK) with NDK 27.0.12077973 + cmake 3.22.1 (bundles ninja)
  installed via sdkmanager. Did NOT touch the running `memba-builder`
  container (it's bound to a different, unrelated repo — /home/claude/memba-app,
  old naming — verified via `docker inspect` before assuming reuse).
- First real cmake configure + ninja build attempt SUCCEEDED — no fix
  loop needed. FetchContent cloned stable-diffusion.cpp + its `ggml`
  submodule fine, cross-compiled clean against NDK r27 clang for
  arm64-v8a / android-31 / armv8.7a. Only warnings (deprecated
  wstring_convert, missing `override` on virtuals), zero errors.
- `file build-android/libsmoke_test.so` → ELF 64-bit LSB shared object,
  ARM aarch64, for Android 31, built by NDK r27. `llvm-nm -D` confirmed
  `Java_com_example_meowmba_core_media_SmokeTestKt_nativeSmokeTest` exported.
- Verified working commit: `ea7f0c87cfe4c673263b4c201c596c7f1cbe2528`
  (stable-diffusion.cpp, `master` branch tip as of 2026-08-04).
- No CMake flag changes were needed beyond the plan's documented set.
- Cleaned up smoke_test.cpp + build-android/ per Step 6.

## Task 2: JNI bridge — real generation
Status: DONE
- Pinned the CMakeLists.txt to the verified commit SHA, wrote
  `sd_jni_bridge.cpp` exactly per the plan (3 JNI functions:
  nativeCreateContext, nativeGenerate, nativeFreeContext).
  Also linked `android` (harmless/defensive; header includes
  android/bitmap.h though no AndroidBitmap_* functions are called).
- Build succeeded on the first attempt (reused the FetchContent cache
  from Task 1, so this was fast).
- `file` + `llvm-nm -D` confirmed all three JNI symbols exported with
  correct mangled names matching SDCppNative.kt (Task 3).
- Committed as 93bc920.

## Task 3: Kotlin wrapper — SDCppNative seam + SDCppImageGenerator
Status: DONE
- Dispatched to an implementer subagent (fresh context, given the plan's
  exact code + Task 1/2 verification facts).
- Caught and fixed a real bug in the plan's own test text: the plan's
  Step 3 test hardcodes `modelPath = "/fake/model.gguf"`, but
  `SDCppImageGenerator.generate()` (Step 5) gates on
  `File(modelPath).isFile` before ever touching the fake native layer —
  since that path doesn't exist, 2 of 4 tests failed for real, and the
  other 2 passed for the wrong reason. Fixed by creating a real empty
  temp file for the test's modelPath. No production code deviated from
  the plan's exact text.
- All 4 tests pass. Full unit suite: only the 3 pre-existing
  ModelDownloadViewModelTest failures (documented in AGENTS.md as
  known-failing on clean HEAD, unrelated).
- Verified myself (controller): local.properties correctly gitignored
  and not committed; commit touches exactly the 4 intended files.
- Committed as 6feebfb.

## Task 4: Model catalog + auto-download
Status: DONE
- Dispatched to an implementer subagent. Read ModelCatalog.kt/ModelDownloader.kt
  first per plan instructions.
- Found `ModelCatalog.isValidModelFileName` structurally rejects `.gguf`
  (asserted by an existing test) — could not reuse `LocalModelOption`
  without weakening LLM filename validation, so added a parallel
  `ImageModelOption`/`ImageModelCatalog` type (the plan's own anticipated
  fallback for exactly this case). Download mechanics stayed fully
  shared (`ModelDownloader.download` is already generic over url/fileName).
- Verified SDXL-Lightning source myself (controller): real HF repo
  `OlegSkutte/SDXL-Lightning-GGUF`, file `sdxl_lightning_4step.q8_0.gguf`,
  confirmed via `curl -sI -L` → HTTP 302→200, content-length 4098988672
  bytes (~4.1GB), no auth required, Q8_0 quant matching the JNI bridge's
  hardcoded wtype.
- 3 new tests pass. Committed as 0a0b3f1.

## Task 5: Wire into the app — replace OnDeviceImageGenerator, update Settings
Status: DONE
- Controller (me) finalized this task's brief using Task 4's real
  ImageModelCatalog/ModelDownloader signatures, per the plan's explicit
  instruction not to pre-write this task.
- Dispatched to an implementer subagent (two turns — first turn did the
  code changes and kicked off `./gradlew assembleDebug` in its own
  background Monitor but returned before it finished; I picked up
  directly rather than bouncing through more subagent turns, verifying
  the real build/test artifacts myself).
- Design decision (subagent's, verified sound by me): image-model
  installed-state is fully derived from disk via
  `ModelDownloader.installedFile()`, no persisted flag — avoids a
  stale-flag-disagrees-with-reality class of bug. `imageModelDir`/
  `setImageModelDir`/`KEY_IMAGE_MODEL_DIR` removed entirely. Download UI
  reuses the existing `DownloadService`/`CompanionState.modelDownloads`
  map (same infra as LLM downloads), keyed by the image catalog's id —
  no new download machinery.
- Settings UI: replaced the manual folder text field with a proper
  installed/downloading/error/not-installed state machine with a
  progress bar, mirroring ModelManagerScreen's CatalogModelItem pattern.
- Verified myself (controller), not just trusted the report:
  - `./gradlew assembleDebug` → real `app-debug.apk` (354MB) produced;
    unzipped and confirmed `lib/arm64-v8a/libsd_jni_bridge.so` (65.7MB)
    is actually embedded — the native build genuinely worked end-to-end
    through Gradle's `externalNativeBuild`, not just my manual Task 1/2
    docker builds.
  - `./gradlew testDebugUnitTest` → 396 tests, exactly 3 failures (the
    same pre-existing ModelDownloadViewModelTest failures documented
    throughout this plan as unrelated/known).
  - Reviewed the full diff myself before committing (all 8 files) —
    clean, no dead code, `OnDeviceImageGenerator` fully removed with
    zero remaining references outside docs/.
- Committed as 5ac979a.

## Final whole-branch review
Status: DONE — one Minor finding, fixed
Dispatched to a review subagent with instructions to cross-check the JNI
bridge against the real (freshly cloned) stable-diffusion.cpp source at
the pinned commit, check memory safety, Throwable discipline, dead code,
test quality, and to actually run the test suite itself.

Result: everything verified correct except one finding —
`SettingsScreen.kt`'s `imageModelInstalled()` (a disk stat) was called
directly in a composable `when` guard, re-running on every keystroke in
unrelated Settings fields (same composable scope). Fixed directly
(commit b91e0bd): now checked once via `remember` + recomputed only via
`LaunchedEffect(imageModelDownload)` or on explicit delete, not on every
recomposition.

## FINAL STATUS: PLAN COMPLETE
All 5 tasks done, final review clean after one fix. Final
`./gradlew testDebugUnitTest`: 396 tests, 3 failures — the same
pre-existing `ModelDownloadViewModelTest` failures documented
throughout this plan as unrelated to this work (present on origin/main
before this branch).

Branch: feat/native-on-device-image-gen
Worktree: /home/claude/meowmba-app/.claude/worktrees/native-image-gen
Merge-base: origin/main @ 8c422bb
Commits (7): 15c29d0, 93bc920, ec544b0, 6feebfb, 0a0b3f1, 5ac979a, 3e5f37c, b91e0bd
Not pushed, not merged — left for human review per instructions.
