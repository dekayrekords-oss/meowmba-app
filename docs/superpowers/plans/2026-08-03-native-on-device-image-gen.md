# Native On-Device Image Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the deprecated, manual-install `OnDeviceImageGenerator`
(MediaPipe/SD1.5) with `SDCppImageGenerator`, a JNI wrapper around
`stable-diffusion.cpp` running an auto-downloaded SDXL-Lightning or
Z-Image-Turbo model, per
`docs/superpowers/specs/2026-08-03-native-image-music-video-design.md`.

**Architecture:** A small native module (`app/src/main/cpp/`) vendors
`stable-diffusion.cpp` via CMake `FetchContent` and exposes a minimal JNI
bridge (create context, generate, free). `SDCppImageGenerator.kt`
implements the existing `ImageGenerator` interface and talks to the native
layer through a thin `SDCppNative` seam (an `object` of `external fun`
declarations), kept separate specifically so the Kotlin-side error handling
and marshalling logic is unit-testable without a real `.so`.

**Tech Stack:** C++ (stable-diffusion.cpp / ggml), Android NDK + CMake,
JNI, Kotlin.

## Global Constraints

- No official Android build exists for stable-diffusion.cpp — Task 1 is a
  literal spike with an explicit, checkable success criterion, not a normal
  TDD task. Do not skip it or assume it will "just work" from the CMake
  flags below; verify each step.
- The interface `SDCppImageGenerator` implements is fixed and already
  proven in production use by the remote-backend feature — do not change
  it: `fun isAvailable(): Boolean`, `suspend fun generate(prompt: String,
  iterations: Int = 20, seed: Int = 0): Result<Bitmap>`.
- Model auto-download must ask before pulling a multi-gigabyte file, never
  pull silently on first use.
- `minSdk = 31` (from `app/build.gradle.kts`) — use `ANDROID_PLATFORM=android-31`
  in the CMake toolchain invocation, not a lower baseline.
- Follow the existing defensive pattern from `OnDeviceImageGenerator.kt`
  and the image-generation final review: catch `Throwable`, not `Exception`,
  around any native/JNI call — a native crash path must not take the app
  down mid-conversation.

---

## File Structure

- Create: `app/src/main/cpp/CMakeLists.txt` — vendors stable-diffusion.cpp,
  builds the JNI bridge `.so`.
- Create: `app/src/main/cpp/sd_jni_bridge.cpp` — the JNI bridge.
- Modify: `app/build.gradle.kts` — `externalNativeBuild { cmake { ... } }`
  block, `ndkVersion`.
- Create: `app/src/main/java/com/example/meowmba/core/media/SDCppNative.kt`
  — `external fun` declarations only (the JNI seam).
- Create: `app/src/main/java/com/example/meowmba/core/media/SDCppImageGenerator.kt`
  — implements `ImageGenerator`, calls through `SDCppNative`.
- Modify: `app/src/main/java/com/example/meowmba/core/agent/ModelCatalog.kt`
  — add the image-model catalog entry.
- Modify: `app/src/main/java/com/example/meowmba/core/agent/ModelDownloader.kt`
  — handle the new catalog entry type if needed.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt`
  — swap `OnDeviceImageGenerator` for `SDCppImageGenerator` in the
  `localGenerator` supplier.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt`
  — replace the manual "Image model folder" field with an auto-download
  status + "Download model" / "Delete model" affordance, matching whatever
  UI pattern the existing LLM `ModelManagerScreen.kt` already uses for its
  own downloads (read that file before writing this task's UI — do not
  invent a new download-UX pattern when one already exists in this app).
- Delete: `app/src/main/java/com/example/meowmba/core/media/OnDeviceImageGenerator.kt`
  (superseded).
- Test: `app/src/test/java/com/example/meowmba/SDCppImageGeneratorTest.kt`
  (JVM-testable Kotlin logic only, via a fake `SDCppNative` seam).

---

### Task 1: Spike — build stable-diffusion.cpp for Android, prove one native round trip

**This is not a normal TDD task.** There is no official Android build for
this project. The goal is a checked, working build — treat every command
below as a hypothesis to verify, not a guarantee.

**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/smoke_test.cpp` (temporary, deleted at the end
  of this task once Task 2 supersedes it)

**Interfaces:**
- Produces: a working `arm64-v8a` `.so` build pipeline that Task 2 extends.
  No Kotlin-visible interface yet.

- [ ] **Step 1: Confirm the NDK is available**

Run: `echo $ANDROID_HOME && ls $ANDROID_HOME/ndk`
Expected: at least one NDK version directory listed. If none exists, install
one via `sdkmanager --install "ndk;27.0.12077973"` (or the latest available
27.x — check `sdkmanager --list` for what's actually available before
picking a version) before continuing.

- [ ] **Step 2: Write `app/src/main/cpp/CMakeLists.txt`**

```cmake
cmake_minimum_required(VERSION 3.22)
project(sdcpp_bridge)

include(FetchContent)
FetchContent_Declare(
    stable_diffusion_cpp
    GIT_REPOSITORY https://github.com/leejet/stable-diffusion.cpp.git
    GIT_TAG master
    GIT_SUBMODULES_RECURSE TRUE
)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(stable_diffusion_cpp)

add_library(smoke_test SHARED smoke_test.cpp)
target_link_libraries(smoke_test PRIVATE stable-diffusion log)
target_include_directories(smoke_test PRIVATE ${stable_diffusion_cpp_SOURCE_DIR}/include)
```

Note: `GIT_TAG master` pins to the moving branch tip deliberately for this
spike, to get the most current build fixes. Task 2 pins to a specific
commit SHA once a working one is confirmed, so the real build doesn't
silently break from upstream changes later — record the working commit SHA
you land on at the end of this task and carry it into Task 2's brief.

- [ ] **Step 3: Write a minimal smoke-test source file**

```cpp
// app/src/main/cpp/smoke_test.cpp
// Temporary — proves the toolchain links against stable-diffusion.cpp.
// Deleted once Task 2's real bridge supersedes it.
#include <android/log.h>
#include <jni.h>
#include "stable-diffusion.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_meowmba_core_media_SmokeTestKt_nativeSmokeTest(JNIEnv* env, jclass) {
    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    __android_log_print(ANDROID_LOG_INFO, "SDSmokeTest",
                         "sd_ctx_params_init linked and ran successfully");
    return env->NewStringUTF("stable-diffusion.cpp linked OK");
}
```

- [ ] **Step 4: Attempt the CMake configure + build**

Run:
```bash
ANDROID_NDK=$(ls -d $ANDROID_HOME/ndk/* | sort -V | tail -1)
cd app/src/main/cpp
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-31 \
      -DCMAKE_C_FLAGS="-march=armv8.7a" -DCMAKE_CXX_FLAGS="-march=armv8.7a" \
      -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF \
      -B build-android
cmake --build build-android --config Release -j4
```

**If this fails:** read the actual error. Common failure classes for a
first attempt at a project with no official Android support:
- Missing submodules (`ggml`) — check stable-diffusion.cpp's
  `.gitmodules` and whether `GIT_SUBMODULES_RECURSE TRUE` actually pulled
  them; if not, add an explicit `FetchContent_Declare` for `ggml` pointed
  at the submodule's pinned commit, read from `.gitmodules`/`git ls-tree`
  in the fetched source.
- A C++ standard library feature stable-diffusion.cpp assumes that the
  NDK's libc++ doesn't provide at the chosen `ANDROID_PLATFORM` — try
  bumping the platform level before giving up on the whole approach.
- OpenMP linkage errors — `-DGGML_OPENMP=OFF` above should prevent this,
  but if ggml's CMake still probes for OpenMP, check for an
  `-DGGML_NATIVE=OFF` flag too (armv8.7a doesn't necessarily match the
  build host's native arch flags ggml might try to auto-detect).

Do not spend more than a genuinely thorough attempt (several distinct fixes,
not one retry) on any single error before escalating this task as BLOCKED
with the exact error text — a stuck native build is exactly the kind of
thing that needs a human's eyes on the real error, not endless guessing.

Expected on success: `build-android/libsmoke_test.so` exists.

- [ ] **Step 5: Verify the .so is real and linked correctly**

Run: `file build-android/libsmoke_test.so` — expect `ELF 64-bit LSB shared
object, ARM aarch64`.

Run: `$ANDROID_NDK/toolchains/llvm/prebuilt/*/bin/llvm-nm -D build-android/libsmoke_test.so | grep nativeSmokeTest` —
expect the JNI symbol to be present and exported.

- [ ] **Step 6: Record the working state and clean up the spike**

Record (in your final report, not in a file — the next task's dispatcher
reads your report) the exact stable-diffusion.cpp commit SHA that built
successfully (`cd build-android/_deps/stable_diffusion_cpp-src && git rev-parse HEAD`)
and any CMake flag changes you needed beyond what's written above.

Delete `smoke_test.cpp` and the `build-android/` directory (Task 2 starts
the real CMakeLists.txt fresh, informed by what you learned, pinning the
commit SHA you recorded instead of `GIT_TAG master`).

- [ ] **Step 7: Report**

Status DONE only if a real `.so` built and the JNI symbol is confirmed
present. Status BLOCKED with full error text if the build could not be
made to work after genuine effort — do not report DONE on a build that
"almost" worked.

---

### Task 2: JNI bridge — real generation, not a smoke test

**Files:**
- Modify: `app/src/main/cpp/CMakeLists.txt` (pin the commit SHA from Task
  1's report instead of `GIT_TAG master`; replace `smoke_test.cpp` with
  `sd_jni_bridge.cpp`)
- Create: `app/src/main/cpp/sd_jni_bridge.cpp`

**Interfaces:**
- Consumes: the verified stable-diffusion.cpp C API from
  `docs/superpowers/specs/2026-08-03-native-image-music-video-design.md`
  (Track 1's "Verified C API" section — this is the real header content,
  not a guess; do not invent additional fields).
- Produces: three JNI functions Task 3 calls by exact name:
  `Java_com_example_meowmba_core_media_SDCppNative_nativeCreateContext`,
  `Java_com_example_meowmba_core_media_SDCppNative_nativeGenerate`,
  `Java_com_example_meowmba_core_media_SDCppNative_nativeFreeContext`.

- [ ] **Step 1: Update CMakeLists.txt to pin the working commit**

Replace `GIT_TAG master` with `GIT_TAG <the-sha-task-1-recorded>` and
`smoke_test.cpp` with `sd_jni_bridge.cpp` in the `add_library` call.

- [ ] **Step 2: Write the JNI bridge**

```cpp
// app/src/main/cpp/sd_jni_bridge.cpp
#include <android/log.h>
#include <android/bitmap.h>
#include <jni.h>
#include <cstring>
#include "stable-diffusion.h"

#define LOG_TAG "SDCppBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_meowmba_core_media_SDCppNative_nativeCreateContext(
    JNIEnv* env, jobject /* this */, jstring jModelPath, jint nThreads) {
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = modelPath;
    params.n_threads = nThreads;
    params.wtype = SD_TYPE_Q8_0;  // matches the GGUF quant the model catalog downloads

    sd_ctx_t* ctx = new_sd_ctx(&params);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (ctx == nullptr) {
        LOGE("new_sd_ctx returned null — model failed to load");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

// Returns a jbyteArray of raw RGB bytes (width*height*channel), or null on failure.
// Caller (Kotlin) receives width/height/channel via the int[] out-parameter.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_meowmba_core_media_SDCppNative_nativeGenerate(
    JNIEnv* env, jobject /* this */, jlong ctxPtr, jstring jPrompt,
    jint width, jint height, jint steps, jlong seed, jintArray outDims) {
    if (ctxPtr == 0) {
        LOGE("nativeGenerate called with null context");
        return nullptr;
    }
    sd_ctx_t* ctx = reinterpret_cast<sd_ctx_t*>(ctxPtr);
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);

    sd_sample_params_t sampleParams;
    sd_sample_params_init(&sampleParams);
    sampleParams.sample_steps = steps;

    sd_img_gen_params_t genParams;
    sd_img_gen_params_init(&genParams);
    genParams.prompt = prompt;
    genParams.width = width;
    genParams.height = height;
    genParams.sample_params = sampleParams;
    genParams.seed = static_cast<int64_t>(seed);
    genParams.batch_count = 1;

    sd_image_t* images = nullptr;
    int numImages = 0;
    bool ok = generate_image(ctx, &genParams, &images, &numImages);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    if (!ok || images == nullptr || numImages < 1) {
        LOGE("generate_image failed or returned no images");
        return nullptr;
    }

    sd_image_t img = images[0];
    size_t byteCount = static_cast<size_t>(img.width) * img.height * img.channel;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(byteCount));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(byteCount),
                             reinterpret_cast<jbyte*>(img.data));

    jint dims[3] = {static_cast<jint>(img.width), static_cast<jint>(img.height),
                     static_cast<jint>(img.channel)};
    env->SetIntArrayRegion(outDims, 0, 3, dims);

    free_sd_images(images, numImages);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_meowmba_core_media_SDCppNative_nativeFreeContext(
    JNIEnv* env, jobject /* this */, jlong ctxPtr) {
    if (ctxPtr != 0) {
        free_sd_ctx(reinterpret_cast<sd_ctx_t*>(ctxPtr));
    }
}
```

- [ ] **Step 3: Build**

Run the same `cmake -B build-android ... && cmake --build build-android`
sequence from Task 1 Step 4, now against `sd_jni_bridge.cpp`. Expected:
`build-android/libsd_jni_bridge.so` exists, same ELF/symbol checks as
Task 1 Step 5 but for the three new JNI function names.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/cpp/CMakeLists.txt app/src/main/cpp/sd_jni_bridge.cpp
git commit -m "feat(media): JNI bridge for stable-diffusion.cpp on-device generation"
```

---

### Task 3: Kotlin wrapper — `SDCppNative` seam + `SDCppImageGenerator`

**Files:**
- Modify: `app/build.gradle.kts` (add `externalNativeBuild`/`ndkVersion`)
- Create: `app/src/main/java/com/example/meowmba/core/media/SDCppNative.kt`
- Create: `app/src/main/java/com/example/meowmba/core/media/SDCppImageGenerator.kt`
- Test: `app/src/test/java/com/example/meowmba/SDCppImageGeneratorTest.kt`

**Interfaces:**
- Consumes: the three JNI function names from Task 2.
- Produces: `SDCppImageGenerator(context: Context, modelPath: String) :
  ImageGenerator` — the class Task 5's wiring constructs.

- [ ] **Step 1: Wire the native build into Gradle**

In `app/build.gradle.kts`, inside the `android { }` block, add:

```kotlin
    ndkVersion = "27.0.12077973"  // match whatever Task 1 confirmed available

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        // ... existing contents ...
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_PLATFORM=android-31",
                    "-DCMAKE_C_FLAGS=-march=armv8.7a",
                    "-DCMAKE_CXX_FLAGS=-march=armv8.7a",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF"
                )
                abiFilters += "arm64-v8a"
            }
        }
    }
```

Adjust the exact `ndkVersion` string to whatever Task 1 actually confirmed
installed and working — do not guess a version that wasn't verified.

- [ ] **Step 2: Write `SDCppNative.kt` — the JNI seam, nothing else**

```kotlin
package com.example.meowmba.core.media

/**
 * Raw JNI declarations only — no logic. Kept separate from
 * [SDCppImageGenerator] so the Kotlin-side error handling and marshalling
 * can be unit-tested against a fake implementation of this interface,
 * without a real .so.
 */
internal interface SDCppNative {
    fun createContext(modelPath: String, nThreads: Int): Long
    fun generate(
        ctxPtr: Long,
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long,
        outDims: IntArray
    ): ByteArray?
    fun freeContext(ctxPtr: Long)
}

internal object SDCppJniNative : SDCppNative {
    init {
        System.loadLibrary("sd_jni_bridge")
    }

    external override fun createContext(modelPath: String, nThreads: Int): Long
    external override fun generate(
        ctxPtr: Long,
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        seed: Long,
        outDims: IntArray
    ): ByteArray?
    external override fun freeContext(ctxPtr: Long)
}
```

- [ ] **Step 3: Write the failing test for `SDCppImageGenerator`**

```kotlin
package com.example.meowmba

import android.graphics.Bitmap
import com.example.meowmba.core.media.SDCppImageGenerator
import com.example.meowmba.core.media.SDCppNative
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

private class FakeSDCppNative(
    private val createResult: Long = 1L,
    private val generateResult: ByteArray? = ByteArray(4 * 4 * 4) { 0x7F },
    private val dims: IntArray = intArrayOf(4, 4, 4)
) : SDCppNative {
    var freed = false
    override fun createContext(modelPath: String, nThreads: Int) = createResult
    override fun generate(
        ctxPtr: Long, prompt: String, width: Int, height: Int,
        steps: Int, seed: Long, outDims: IntArray
    ): ByteArray? {
        dims.copyInto(outDims)
        return generateResult
    }
    override fun freeContext(ctxPtr: Long) { freed = true }
}

@RunWith(RobolectricTestRunner::class)
class SDCppImageGeneratorTest {

    @Test
    fun `generate returns a bitmap built from native bytes`() = runTest {
        val fake = FakeSDCppNative()
        val generator = SDCppImageGenerator(
            modelPath = "/fake/model.gguf",
            native = fake
        )

        val result = generator.generate("a cat")

        assertTrue(result.isSuccess)
        assertTrue(fake.freed)
    }

    @Test
    fun `null context (model load failure) is a clean failure, not a crash`() = runTest {
        val fake = FakeSDCppNative(createResult = 0L)
        val generator = SDCppImageGenerator(modelPath = "/fake/model.gguf", native = fake)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
    }

    @Test
    fun `null generate result is a clean failure, not a crash`() = runTest {
        val fake = FakeSDCppNative(generateResult = null)
        val generator = SDCppImageGenerator(modelPath = "/fake/model.gguf", native = fake)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
        assertTrue(fake.freed)  // context must still be released even on a failed generate
    }

    @Test
    fun `a native Throwable is caught, not propagated`() = runTest {
        val throwing = object : SDCppNative {
            override fun createContext(modelPath: String, nThreads: Int) = 1L
            override fun generate(
                ctxPtr: Long, prompt: String, width: Int, height: Int,
                steps: Int, seed: Long, outDims: IntArray
            ): ByteArray? = throw UnsatisfiedLinkError("simulated native crash")
            override fun freeContext(ctxPtr: Long) {}
        }
        val generator = SDCppImageGenerator(modelPath = "/fake/model.gguf", native = throwing)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 4: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.SDCppImageGeneratorTest"`
Expected: compile failure — `SDCppImageGenerator` doesn't exist yet.

- [ ] **Step 5: Implement `SDCppImageGenerator`**

```kotlin
package com.example.meowmba.core.media

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real on-device text-to-image via stable-diffusion.cpp, replacing the
 * deprecated MediaPipe/SD1.5 path. See
 * docs/superpowers/specs/2026-08-03-native-image-music-video-design.md.
 */
class SDCppImageGenerator internal constructor(
    private val modelPath: String,
    private val native: SDCppNative
) : ImageGenerator {

    constructor(modelPath: String) : this(modelPath, SDCppJniNative)

    override fun isAvailable(): Boolean = File(modelPath).isFile

    override suspend fun generate(
        prompt: String,
        iterations: Int,
        seed: Int
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(
                IllegalStateException("No image model installed at $modelPath")
            )
        }
        try {
            val ctxPtr = native.createContext(modelPath, Runtime.getRuntime().availableProcessors())
            if (ctxPtr == 0L) {
                return@withContext Result.failure(
                    IllegalStateException("stable-diffusion.cpp failed to load the model")
                )
            }
            try {
                val dims = IntArray(3)
                val bytes = native.generate(
                    ctxPtr, prompt, width = 512, height = 512,
                    steps = iterations, seed = seed.toLong(), outDims = dims
                ) ?: return@withContext Result.failure(
                    IllegalStateException("stable-diffusion.cpp generation returned no image")
                )
                val (width, height, channel) = Triple(dims[0], dims[1], dims[2])
                val bitmap = bytesToBitmap(bytes, width, height, channel)
                Result.success(bitmap)
            } finally {
                native.freeContext(ctxPtr)
            }
        } catch (e: Throwable) {
            // Throwable, not Exception: a native crash (UnsatisfiedLinkError, etc.)
            // must not take the app down mid-conversation.
            Result.failure(e)
        }
    }

    private fun bytesToBitmap(bytes: ByteArray, width: Int, height: Int, channel: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val base = i * channel
            val r = bytes[base].toInt() and 0xFF
            val g = bytes[base + 1].toInt() and 0xFF
            val b = bytes[base + 2].toInt() and 0xFF
            val a = if (channel >= 4) bytes[base + 3].toInt() and 0xFF else 255
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.SDCppImageGeneratorTest"`
Expected: PASS, all 4 tests. (This test class needs Robolectric, already a
project test dependency as of the image-generation-routing branch — confirm
`testImplementation(libs.robolectric)` or equivalent exists in
`app/build.gradle.kts` before writing the `@RunWith` annotation; if it's
missing, that means this plan is being run before that branch landed, which
is a real blocker — report BLOCKED and note the missing prerequisite rather
than re-adding the dependency yourself, since prerequisite branch ordering
is a controller decision.)

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/example/meowmba/core/media/SDCppNative.kt \
        app/src/main/java/com/example/meowmba/core/media/SDCppImageGenerator.kt \
        app/src/test/java/com/example/meowmba/SDCppImageGeneratorTest.kt
git commit -m "feat(media): SDCppImageGenerator — Kotlin wrapper over the stable-diffusion.cpp JNI bridge"
```

---

### Task 4: Model catalog + auto-download

**Files:**
- Read first (do not guess its shape): `app/src/main/java/com/example/meowmba/core/agent/ModelCatalog.kt`
  and `app/src/main/java/com/example/meowmba/core/agent/ModelDownloader.kt`
  — these already implement download-on-demand for LLM models. This task
  extends the existing pattern; it does not invent a new one.
- Modify: `ModelCatalog.kt` — add an image-model entry.
- Modify: `ModelDownloader.kt` if the existing catalog entry type can't
  represent an image model's shape (e.g. if it's LLM-specific, add a
  parallel `ImageModelCatalog`/appropriate extension point — read the
  existing code before deciding which).

**Interfaces:**
- Consumes: `SDCppImageGenerator(modelPath: String)` from Task 3.
- Produces: a way to ask "is the image model downloaded, and if not, start
  the download" — the exact function names depend on what Task 4's
  implementer finds in `ModelCatalog.kt`/`ModelDownloader.kt`, since this
  plan cannot fabricate signatures for code it hasn't read. Document the
  actual signatures produced in your report so Task 5 can consume them
  correctly — Task 5's brief will be written after Task 4 completes, using
  your report as its source of truth for these signatures. (Controller
  note: unlike the earlier fully-pre-specified tasks in this plan, Task 5's
  exact wiring code is deliberately left for the controller to finalize
  once this task's real interface is known — do not attempt to write
  Task 5's brief in advance of Task 4 completing.)

- [ ] **Step 1: Read the existing model-download pattern**

Read `ModelCatalog.kt` and `ModelDownloader.kt` in full. Identify: how a
catalog entry is structured (name, download URL, install path, file size),
how the download flow is triggered from UI, how "is this model installed"
is checked today for LLM models.

- [ ] **Step 2: Add the image-model catalog entry**

Model: SDXL-Lightning, GGUF-quantized (Q8_0, matching the JNI bridge's
hardcoded `wtype` from Task 2) — find the actual HuggingFace GGUF release
for SDXL-Lightning compatible with stable-diffusion.cpp's expected format
(search for it; do not fabricate a download URL) and use its real URL and
file size in the catalog entry, following the exact structure the existing
LLM entries use.

- [ ] **Step 3: Wire "is installed" / "download" through the existing pattern**

Following whatever mechanism Step 1 found, make the image model
downloadable through the same UI/flow the LLM models already use — reuse,
don't duplicate.

- [ ] **Step 4: Test**

Write a test matching the existing test style for `ModelDownloader`/
`ModelCatalog` (check `app/src/test/java/com/example/meowmba/` for an
existing `ModelDownload*Test.kt` — there is one, referenced elsewhere in
this project's test suite — follow its exact conventions) verifying the
new catalog entry is present and correctly shaped.

- [ ] **Step 5: Run and verify**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.ModelDownload*"`
Expected: all pass, including the new test.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/agent/ModelCatalog.kt \
        app/src/main/java/com/example/meowmba/core/agent/ModelDownloader.kt
git commit -m "feat(media): add SDXL-Lightning to the on-device model catalog"
```

---

### Task 5: Wire into the app — replace `OnDeviceImageGenerator`, update Settings

**This task's brief must be finalized by the controller after Task 4
completes**, using Task 4's report for the real catalog/downloader function
signatures. Do not write or dispatch this task from the plan text alone.

**Files (confirmed, signatures TBD from Task 4):**
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt`
  — replace the `OnDeviceImageGenerator` construction in the
  `localGenerator` supplier (added by the image-routing branch) with
  `SDCppImageGenerator`, reading the model path from wherever Task 4's
  catalog stores it.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt`
  — replace the "Image model folder" manual-path text field with a
  download-status affordance, matching `ModelManagerScreen.kt`'s existing
  download UI pattern (read that file for the real composable to mirror —
  do not invent a new download-progress UI pattern).
- Delete: `app/src/main/java/com/example/meowmba/core/media/OnDeviceImageGenerator.kt`
- Modify: any file still importing `OnDeviceImageGenerator` (search for
  `OnDeviceImageGenerator` across the whole codebase before deleting it —
  the earlier image-routing branch's `ImageGenerator` interface abstraction
  means this should be a small, contained set of call sites, but verify
  rather than assume).

**Acceptance:** `./gradlew assembleDebug` succeeds with zero remaining
references to `OnDeviceImageGenerator`, and `./gradlew testDebugUnitTest`
passes in full (the 3 pre-existing `ModelDownloadViewModelTest` failures
predating this whole plan are the only acceptable failures — verify this
against the branch's own merge-base the same way prior work in this
codebase has, by checking out the base commit and confirming the same 3
fail there too).

---

## Plan Self-Review Notes

- **Spec coverage:** Native build (Task 1), JNI bridge (Task 2), Kotlin
  wrapper + tests (Task 3), auto-download (Task 4), app wiring (Task 5) —
  covers the design doc's Track 1 in full.
- **Placeholder scan:** Task 5 is deliberately left partially unspecified
  (documented as such, with an explicit reason and an explicit instruction
  not to dispatch it from this text alone) because its real interface
  cannot be known until Task 4 runs against actual, unread code — this is
  not a placeholder in the "TBD, fill in details" sense the process
  forbids; it's an honest dependency the controller must resolve, exactly
  parallel to how Task 1's spike output feeds Task 2's pinned commit SHA.
- **Type consistency:** `SDCppNative`'s three methods have identical
  signatures across the interface (Task 3 Step 2), the JNI bridge's actual
  exported function names (Task 2 Step 2), and the fakes used in Task 3's
  test (Step 3) — the JNI bridge's function names
  (`Java_com_example_meowmba_core_media_SDCppNative_native*`) are derived
  mechanically from the package/class/method names in `SDCppNative.kt`, so
  a mismatch would fail to link at runtime, not silently misbehave —
  Task 3's build step (Step 3 implicitly, via the full app build in Task 5)
  is what actually proves this linkage, since JVM unit tests never touch
  the real `.so`.
