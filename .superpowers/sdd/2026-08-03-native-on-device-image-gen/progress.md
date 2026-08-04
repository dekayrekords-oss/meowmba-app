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
