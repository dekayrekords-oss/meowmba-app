# Image Generation: Routing + Remote Backend + Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in remote image-generation backend and a routing layer that
picks between it and the on-device generator, per the design in
`docs/superpowers/specs/2026-08-03-image-generation-design.md`.

**Architecture:** A shared `ImageGenerator` interface lets `MediaGenerateTool`
try backends in a configurable order (local-first by default) and fall back
automatically — first to the other backend, then to the existing canvas
placeholder. `OnDeviceImageGenerator` (today's MediaPipe SD1.5 generator)
implements the interface unchanged for now; it gets replaced by
`SDCppImageGenerator` in a separate, later plan (native `stable-diffusion.cpp`
integration) without touching any code in this plan.

**Tech Stack:** Kotlin, Ktor client 3.5.1 (already a dependency, used the same
way `RemoteModelProvider.kt` already calls remote HTTP APIs), JUnit + Ktor's
`ktor-client-mock` for tests.

## Global Constraints

- The remote backend is constructed **only** when the user has entered a
  non-blank server URL in Settings — never assumed, never defaulted to a
  specific host (per spec: MEOWMBA is distributed, cannot assume any
  particular home server exists).
- Precedence defaults to **local first**; a Settings toggle lets the user
  switch to server-first. Default must be local, since a fresh distributed
  install has no server configured at all.
- If the primary backend fails (unavailable or `generate()` returns
  `Result.failure`), fall back to the other configured backend, then to the
  existing canvas placeholder — the tool must always produce something, per
  the behavior already established in `MediaGenerateTool`.
- Every new setting follows the exact persistence pattern already used by
  `hermesAgentUrl` (`CompanionState.kt` field → `CompanionViewModel.kt`
  setter + `SecureStorageManager` key constant → `SettingsScreen.kt` UI).

---

## File Structure

- Create: `app/src/main/java/com/example/meowmba/core/media/ImageGenerator.kt`
  — shared interface, implemented by both backends.
- Modify: `app/src/main/java/com/example/meowmba/core/media/OnDeviceImageGenerator.kt`
  — implement `ImageGenerator` (one-line class-declaration change, no behavior
  change).
- Create: `app/src/main/java/com/example/meowmba/core/media/RemoteImageGenerator.kt`
  — Ktor-based HTTP client backend.
- Modify: `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt`
  — `MediaGenerateTool` gets a second generator supplier, a preference
  supplier, and a routing/fallback helper.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionState.kt`
  — two new fields.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt`
  — two new setters, two new storage keys, updated `MediaGenerateTool`
  registration.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt`
  — new URL field + toggle in the existing "Image generation" section.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt`
  — thread the two new params through to `SettingsScreen(...)`.
- Modify: `app/build.gradle.kts` and `gradle/libs.versions.toml` — add
  `ktor-client-mock` as a test dependency.
- Test: `app/src/test/java/com/example/meowmba/MediaGenerateToolRoutingTest.kt`
- Test: `app/src/test/java/com/example/meowmba/RemoteImageGeneratorTest.kt`
- Test: additions to `app/src/test/java/com/example/meowmba/CompanionViewModelTest.kt`

---

### Task 1: `ImageGenerator` interface + routing/fallback logic

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/media/ImageGenerator.kt`
- Modify: `app/src/main/java/com/example/meowmba/core/media/OnDeviceImageGenerator.kt:29` (class declaration line)
- Modify: `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt:23-89` (`MediaGenerateTool` class)
- Test: `app/src/test/java/com/example/meowmba/MediaGenerateToolRoutingTest.kt`

**Interfaces:**
- Produces: `interface ImageGenerator { fun isAvailable(): Boolean; suspend fun generate(prompt: String, iterations: Int = 20, seed: Int = 0): Result<Bitmap> }` — Task 2 and later tasks implement/consume this.
- Produces: `MediaGenerateTool(workspace: Workspace, localGenerator: (() -> ImageGenerator?)? = null, remoteGenerator: (() -> ImageGenerator?)? = null, preferServer: () -> Boolean = { false })` — the new constructor shape Task 3's wiring code calls.

- [ ] **Step 1: Write the failing routing test**

Create `app/src/test/java/com/example/meowmba/MediaGenerateToolRoutingTest.kt`:

```kotlin
package com.example.meowmba

import android.graphics.Bitmap
import com.example.meowmba.core.agent.tools.MediaGenerateTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.media.ImageGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/** Fake backend so routing logic is testable with no real model or network. */
private class FakeImageGenerator(
    private val available: Boolean,
    private val succeeds: Boolean,
    val name: String
) : ImageGenerator {
    var callCount = 0
    override fun isAvailable() = available
    override suspend fun generate(prompt: String, iterations: Int, seed: Int): Result<Bitmap> {
        callCount++
        return if (succeeds) {
            Result.success(mock(Bitmap::class.java))
        } else {
            Result.failure(IllegalStateException("$name failed"))
        }
    }
}

class MediaGenerateToolRoutingTest {

    private fun workspace(): Workspace {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        return Workspace(dir)
    }

    @Test
    fun `local-first default tries local before server`() = runTest {
        val local = FakeImageGenerator(available = true, succeeds = true, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(1, local.callCount)
        assertEquals(0, remote.callCount)
    }

    @Test
    fun `prefer-server setting tries server before local`() = runTest {
        val local = FakeImageGenerator(available = true, succeeds = true, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { true }
        )

        tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(1, remote.callCount)
        assertEquals(0, local.callCount)
    }

    @Test
    fun `falls back to the other backend when the primary fails`() = runTest {
        val local = FakeImageGenerator(available = true, succeeds = false, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        val result = tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(1, local.callCount)
        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `falls back to the canvas placeholder when nothing is configured`() = runTest {
        val tool = MediaGenerateTool(workspace())

        val result = tool.execute(mapOf("prompt" to "a cat"))

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("canvas_"))
    }

    @Test
    fun `unavailable backend is skipped without being called`() = runTest {
        val local = FakeImageGenerator(available = false, succeeds = true, name = "local")
        val remote = FakeImageGenerator(available = true, succeeds = true, name = "remote")
        val tool = MediaGenerateTool(
            workspace(),
            localGenerator = { local },
            remoteGenerator = { remote },
            preferServer = { false }
        )

        tool.execute(mapOf("prompt" to "a cat"))

        assertEquals(0, local.callCount)
        assertEquals(1, remote.callCount)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.MediaGenerateToolRoutingTest"`
Expected: compile failure — `ImageGenerator` doesn't exist yet, and
`MediaGenerateTool`'s constructor doesn't accept `remoteGenerator`/`preferServer`.

- [ ] **Step 3: Create the `ImageGenerator` interface**

Create `app/src/main/java/com/example/meowmba/core/media/ImageGenerator.kt`:

```kotlin
package com.example.meowmba.core.media

import android.graphics.Bitmap

/**
 * A backend that turns a text prompt into an image. Implemented by
 * [OnDeviceImageGenerator] (on-device, default) and `RemoteImageGenerator`
 * (opt-in, only constructed when a server URL is configured) so
 * `MediaGenerateTool` can route between them without knowing which is which.
 */
interface ImageGenerator {
    /** Cheap, synchronous check — no network calls. Actual reachability is
     *  discovered by attempting [generate] and letting it fail honestly. */
    fun isAvailable(): Boolean

    suspend fun generate(
        prompt: String,
        iterations: Int = 20,
        seed: Int = 0
    ): Result<Bitmap>
}
```

- [ ] **Step 4: Make `OnDeviceImageGenerator` implement it**

In `app/src/main/java/com/example/meowmba/core/media/OnDeviceImageGenerator.kt`,
change line 29 from:

```kotlin
class OnDeviceImageGenerator(
    private val context: Context,
    private val modelDirectory: String
) {
```

to:

```kotlin
class OnDeviceImageGenerator(
    private val context: Context,
    private val modelDirectory: String
) : ImageGenerator {
```

No other change in this file — its existing `isAvailable()` and `generate()`
already match the interface signature exactly.

- [ ] **Step 5: Add routing/fallback logic to `MediaGenerateTool`**

In `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt`,
replace the class declaration and the top of `execute()` (lines 23-89) with:

```kotlin
class MediaGenerateTool(
    private val workspace: Workspace,
    /** Supplies the on-device generator. Null when unavailable. */
    private val localGenerator: (() -> com.example.meowmba.core.media.ImageGenerator?)? = null,
    /** Supplies the remote generator. Null unless the user configured a server URL. */
    private val remoteGenerator: (() -> com.example.meowmba.core.media.ImageGenerator?)? = null,
    /** True when Settings has "prefer server" selected. Defaults to local-first. */
    private val preferServer: () -> Boolean = { false }
) : Tool {
    override val definition = ToolDefinition(
        id = "media.generate_image",
        displayName = "Generate image",
        description = "Create a PNG image from a text description. Tries the " +
            "configured image backend (on-device or a configured server) and " +
            "falls back to a simple drawn canvas if neither is available. " +
            "The result is saved as a .png file in the workspace.",
        inputSchema = mapOf(
            "prompt" to "string",
            "style" to "string"
        ),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 300_000
    )

    /**
     * Tries backends in preference order, falling through to the next one on
     * unavailability or failure. Returns null only when every configured
     * backend failed, so the caller can fall back to the canvas placeholder.
     */
    private suspend fun tryGenerators(fullPrompt: String): Bitmap? {
        val order = if (preferServer()) {
            listOf(remoteGenerator, localGenerator)
        } else {
            listOf(localGenerator, remoteGenerator)
        }
        for (supplier in order) {
            val generator = supplier?.invoke() ?: continue
            if (!generator.isAvailable()) continue
            generator.generate(fullPrompt).getOrNull()?.let { return it }
        }
        return null
    }

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val prompt = args["prompt"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'prompt'")
        val style = args["style"]?.takeIf { it.isNotBlank() }?.lowercase() ?: "auto"
        val fullPrompt = if (style != "auto") "$prompt, $style style" else prompt

        val bitmap = tryGenerators(fullPrompt)
        if (bitmap != null) {
            val name = fileNameFor(prompt, prefix = "image")
            return@withContext try {
                workspace.resolve(name)?.let { file ->
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    ToolResult.Success(
                        "Image saved as \"$name\" in the workspace: $prompt" +
                            "\nMEDIA_FILE:${file.absolutePath}"
                    )
                } ?: ToolResult.Failure("Could not write image — path is outside the workspace.")
            } catch (e: Exception) {
                ToolResult.Failure("Failed to save the generated image: ${e.message}")
            } finally {
                bitmap.recycle()
            }
        }
```

Leave everything from `val width = 512` (the canvas-drawing fallback) through
the end of the file completely unchanged — it's still the final fallback,
just reached by falling out of the `if (bitmap != null)` block above instead
of the old single-generator check.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.MediaGenerateToolRoutingTest"`
Expected: PASS, all 5 tests.

- [ ] **Step 7: Run the full existing test suite to confirm nothing broke**

Run: `./gradlew testDebugUnitTest`
Expected: PASS — in particular
`CustomAnimationTest`/`CompanionViewModelTest`/`WakeWordErrorPolicyTest`
(unrelated to this change) must still be green, confirming the `MediaTools.kt`
edit didn't break anything else importing it.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/media/ImageGenerator.kt \
        app/src/main/java/com/example/meowmba/core/media/OnDeviceImageGenerator.kt \
        app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt \
        app/src/test/java/com/example/meowmba/MediaGenerateToolRoutingTest.kt
git commit -m "feat(media): route image generation between local/remote backends with fallback"
```

---

### Task 2: `RemoteImageGenerator` (Ktor HTTP backend)

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/media/RemoteImageGenerator.kt`
- Modify: `gradle/libs.versions.toml:60` (add `ktor-client-mock` entry)
- Modify: `app/build.gradle.kts:81-83` (add test dependency)
- Test: `app/src/test/java/com/example/meowmba/RemoteImageGeneratorTest.kt`

**Interfaces:**
- Consumes: `ImageGenerator` interface from Task 1.
- Produces: `RemoteImageGenerator(baseUrl: String, engine: HttpClientEngine = Android.create())` — Task 3's wiring code constructs this with the user's configured URL.

- [ ] **Step 1: Add the Ktor mock-engine test dependency**

In `gradle/libs.versions.toml`, after line 60 (`ktor-serialization-kotlinx-json`), add:

```toml
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
```

In `app/build.gradle.kts`, after line 83 (`implementation(libs.ktor.serialization.kotlinx.json)`),
add to the test dependencies block:

```kotlin
testImplementation(libs.ktor.client.mock)
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/example/meowmba/RemoteImageGeneratorTest.kt`:

```kotlin
package com.example.meowmba

import com.example.meowmba.core.media.RemoteImageGenerator
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteImageGeneratorTest {

    @Test
    fun `isAvailable is true whenever a base URL is configured`() {
        val generator = RemoteImageGenerator("http://192.168.1.10:9100", MockEngine { respond("") })
        assertTrue(generator.isAvailable())
    }

    @Test
    fun `generate posts the prompt and returns a decoded bitmap on success`() = runTest {
        val fakePng = RemoteImageGeneratorTest::class.java
            .getResourceAsStream("/one_pixel.png")!!.readBytes()
        val engine = MockEngine { request ->
            respond(
                content = fakePng,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
            )
        }
        val generator = RemoteImageGenerator("http://192.168.1.10:9100", engine)

        val result = generator.generate("a cat")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `generate reports an honest failure on a non-2xx response`() = runTest {
        val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError) }
        val generator = RemoteImageGenerator("http://192.168.1.10:9100", engine)

        val result = generator.generate("a cat")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }
}
```

Add a minimal 1x1 PNG fixture the test above reads: create
`app/src/test/resources/one_pixel.png` — a 1x1 transparent PNG (67 bytes,
standard minimal PNG). Generate it once locally and commit the binary:

```bash
python3 -c "
import struct, zlib
def chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data))
sig = b'\x89PNG\r\n\x1a\n'
ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0))
raw = b'\x00\x00\x00\x00\x00'
idat = chunk(b'IDAT', zlib.compress(raw))
iend = chunk(b'IEND', b'')
open('app/src/test/resources/one_pixel.png', 'wb').write(sig + ihdr + idat + iend)
"
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.RemoteImageGeneratorTest"`
Expected: compile failure — `RemoteImageGenerator` doesn't exist yet.

- [ ] **Step 4: Implement `RemoteImageGenerator`**

Create `app/src/main/java/com/example/meowmba/core/media/RemoteImageGenerator.kt`:

```kotlin
package com.example.meowmba.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Calls a user-configured HTTP server to generate an image — opt-in, only
 * constructed when the user has entered a server URL in Settings (see
 * `CompanionViewModel.setImageServerUrl`). Never assumes any particular
 * host; the URL is entirely the user's own infrastructure.
 *
 * Wire contract this expects the server to speak:
 *   POST {baseUrl}/generate
 *   body: {"prompt": "..."}  (application/json)
 *   response: raw image bytes, any format BitmapFactory can decode (PNG/JPEG)
 */
class RemoteImageGenerator(
    private val baseUrl: String,
    engine: HttpClientEngine = Android.create()
) : ImageGenerator {

    private val client = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    /** No network call — just checks a URL was configured at all. */
    override fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun generate(
        prompt: String,
        iterations: Int,
        seed: Int
    ): Result<Bitmap> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("No image server configured"))
        }
        return try {
            val response = client.post("$baseUrl/generate") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(GenerateRequest.serializer(), GenerateRequest(prompt)))
            }
            if (!response.status.isSuccess()) {
                return Result.failure(
                    IllegalStateException("Image server HTTP ${response.status.value}")
                )
            }
            val bytes = response.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return Result.failure(IllegalStateException("Image server returned an undecodable response"))
            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Image server unreachable: ${e.message ?: "network error"}"))
        }
    }
}

@Serializable
private data class GenerateRequest(val prompt: String)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.RemoteImageGeneratorTest"`
Expected: PASS, all 3 tests.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/java/com/example/meowmba/core/media/RemoteImageGenerator.kt \
        app/src/test/java/com/example/meowmba/RemoteImageGeneratorTest.kt \
        app/src/test/resources/one_pixel.png
git commit -m "feat(media): add opt-in RemoteImageGenerator HTTP backend"
```

---

### Task 3: Settings — server URL field, preference toggle, and wiring

**Files:**
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionState.kt:154-160`
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt:284`, `:545-556`, `:2122-2126`, `:2814`
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt:31-40`, `:134-159`
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt` (wherever `SettingsScreen(...)` is called with `hermesAgentUrl = uiState.hermesAgentUrl`)
- Test: additions to `app/src/test/java/com/example/meowmba/CompanionViewModelTest.kt`

**Interfaces:**
- Consumes: `RemoteImageGenerator` from Task 2, `MediaGenerateTool`'s new constructor from Task 1.
- Produces: `CompanionState.imageServerUrl: String`, `CompanionState.preferServerImageGen: Boolean`, `CompanionViewModel.setImageServerUrl(url: String)`, `CompanionViewModel.setPreferServerImageGen(enabled: Boolean)` — nothing later in this plan consumes these further, they're the leaf UI wiring.

- [ ] **Step 1: Write the failing ViewModel tests**

In `app/src/test/java/com/example/meowmba/CompanionViewModelTest.kt`, add (following the
existing test style in this file):

```kotlin
    @Test
    fun `setImageServerUrl trims and persists the URL`() = runTest {
        val viewModel = CompanionViewModel()
        viewModel.setImageServerUrl("  http://192.168.1.10:9100  ")
        assertEquals("http://192.168.1.10:9100", viewModel.uiState.value.imageServerUrl)
    }

    @Test
    fun `preferServerImageGen defaults to false`() = runTest {
        val viewModel = CompanionViewModel()
        assertFalse(viewModel.uiState.value.preferServerImageGen)
    }

    @Test
    fun `setPreferServerImageGen updates state`() = runTest {
        val viewModel = CompanionViewModel()
        viewModel.setPreferServerImageGen(true)
        assertTrue(viewModel.uiState.value.preferServerImageGen)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.CompanionViewModelTest"`
Expected: compile failure — `imageServerUrl`, `preferServerImageGen`,
`setImageServerUrl`, `setPreferServerImageGen` don't exist yet.

- [ ] **Step 3: Add the new `CompanionState` fields**

In `app/src/main/java/com/example/meowmba/ui/companion/CompanionState.kt`, after
line 160 (`val imageModelDir: String = "",`), add:

```kotlin
    /** Opt-in HTTP server for image generation. Empty = on-device only. */
    val imageServerUrl: String = "",
    /** When true and imageServerUrl is set, try the server before on-device. */
    val preferServerImageGen: Boolean = false,
```

- [ ] **Step 4: Add the new `CompanionViewModel` setters and storage keys**

In `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt`:

At line 284, alongside the existing `hermesAgentUrl = storage.getPreference(KEY_HERMES_URL) ?: "",`
add:

```kotlin
                imageServerUrl = storage.getPreference(KEY_IMAGE_SERVER_URL) ?: "",
                preferServerImageGen = storage.getBooleanPreference(KEY_PREFER_SERVER_IMAGE_GEN, false),
```

At line 2814, alongside `private const val KEY_HERMES_URL = "hermes_agent_url"`, add:

```kotlin
        private const val KEY_IMAGE_SERVER_URL = "image_server_url"
        private const val KEY_PREFER_SERVER_IMAGE_GEN = "prefer_server_image_gen"
```

After the existing `fun setHermesAgentUrl(url: String) { ... }` (around line
2126), add:

```kotlin
    fun setImageServerUrl(url: String) {
        val clean = url.trim()
        _uiState.value = _uiState.value.copy(imageServerUrl = clean)
        secureStorage?.savePreference(KEY_IMAGE_SERVER_URL, clean)
    }

    fun setPreferServerImageGen(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(preferServerImageGen = enabled)
        secureStorage?.saveBooleanPreference(KEY_PREFER_SERVER_IMAGE_GEN, enabled)
    }
```

- [ ] **Step 5: Wire the new generators into `MediaGenerateTool`'s registration**

In `app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt`,
replace the registration block at lines 545-554:

```kotlin
            toolRegistry.register(
                MediaGenerateTool(ws) {
                    val dir = _uiState.value.imageModelDir
                    val context = secureStorage?.context
                    if (dir.isNotBlank() && context != null) {
                        OnDeviceImageGenerator(context, dir)
                    } else {
                        null
                    }
                }
            )
```

with:

```kotlin
            toolRegistry.register(
                MediaGenerateTool(
                    ws,
                    localGenerator = {
                        val dir = _uiState.value.imageModelDir
                        val context = secureStorage?.context
                        if (dir.isNotBlank() && context != null) {
                            OnDeviceImageGenerator(context, dir)
                        } else {
                            null
                        }
                    },
                    remoteGenerator = {
                        val url = _uiState.value.imageServerUrl
                        if (url.isNotBlank()) {
                            com.example.meowmba.core.media.RemoteImageGenerator(url)
                        } else {
                            null
                        }
                    },
                    preferServer = { _uiState.value.preferServerImageGen }
                )
            )
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.CompanionViewModelTest"`
Expected: PASS, all tests including the 3 new ones.

- [ ] **Step 7: Add the Settings UI**

In `app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt`, add
two new parameters to the `SettingsScreen` function signature (alongside the
existing `hermesAgentUrl: String = ""` at line 31 and `onHermesUrlChange:
(String) -> Unit = {}` at line 40):

```kotlin
    imageServerUrl: String = "",
    preferServerImageGen: Boolean = false,
```

and:

```kotlin
    onImageServerUrlChange: (String) -> Unit = {},
    onPreferServerImageGenChange: (Boolean) -> Unit = {},
```

Then, in the existing "Image generation" section (after the "Check image
model" button and its result text, around line 159, before the `Spacer` that
precedes the Desktop Bridge section), add:

```kotlin
            Spacer(modifier = Modifier.height(16.dp))
            SettingsTextField(
                value = imageServerUrl,
                onValueChange = onImageServerUrlChange,
                label = { Text("Image server URL (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Your own image-generation server, e.g. " +
                    "http://192.168.1.10:9100. Leave blank to always use " +
                    "on-device generation.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            VoiceToggle(
                label = "Prefer server for images",
                description = "Try the server above before on-device generation. " +
                    "Falls back to on-device if the server is unreachable.",
                checked = preferServerImageGen,
                onCheckedChange = onPreferServerImageGenChange
            )
```

- [ ] **Step 8: Thread the new params through `CompanionScreen.kt`**

In `app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt`,
find the `SettingsScreen(...)` call (it passes `hermesAgentUrl =
uiState.hermesAgentUrl` and `onHermesUrlChange = { viewModel.setHermesAgentUrl(it) }`
among its arguments) and add, alongside those two lines:

```kotlin
                imageServerUrl = uiState.imageServerUrl,
                preferServerImageGen = uiState.preferServerImageGen,
                onImageServerUrlChange = { viewModel.setImageServerUrl(it) },
                onPreferServerImageGenChange = { viewModel.setPreferServerImageGen(it) },
```

- [ ] **Step 9: Full build + test run**

Run: `./gradlew testDebugUnitTest`
Expected: PASS, entire suite.

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — confirms the Compose UI changes and all wiring
compile end to end, not just the unit-testable Kotlin logic.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/example/meowmba/ui/companion/CompanionState.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt \
        app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt \
        app/src/test/java/com/example/meowmba/CompanionViewModelTest.kt
git commit -m "feat(settings): add opt-in image server URL and prefer-server toggle"
```

---

## Plan Self-Review Notes

- **Spec coverage:** Architecture (two backends + fallback) → Task 1. Remote
  backend component → Task 2. Settings additions → Task 3. Precedence default
  (local-first) → Task 1's routing order + Task 3's field default. Error
  handling (honest failure messages, fallback chain) → Task 1's
  `tryGenerators` + Task 2's `RemoteImageGenerator` error paths. On-device
  `SDCppImageGenerator` replacement and the model-download flow are **not**
  covered here — they're the separate native-integration plan referenced in
  this plan's Architecture section.
- **Placeholder scan:** no TBD/TODO; every step has real code or a real
  command.
- **Type consistency:** `ImageGenerator.generate(prompt, iterations, seed)`
  signature is identical across Task 1's interface, Task 1's
  `OnDeviceImageGenerator` conformance, Task 2's `RemoteImageGenerator`, and
  every call site.
