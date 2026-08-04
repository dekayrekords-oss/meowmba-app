# Music Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the sine-wave placeholder in `MediaGenerateAudioTool` with
real music generation via a self-hosted ACE-Step 1.5 server, opt-in and
remote-only (no on-device path exists for this modality — see
`docs/superpowers/specs/2026-08-03-native-image-music-video-design.md`,
Track 2, for why).

**Architecture:** `meow-music` (server-side, deployed on the author's own
infrastructure) runs the vendored ACE-Step 1.5 repo's own `acestep-api`
server as a sidecar, plus a thin orchestration wrapper that does the
Ollama-unload-before-generate dance and proxies the real (asynchronous,
job-based) ACE-Step API. `RemoteMusicGenerator` (app-side) implements a
`MusicGenerator` interface with a simple suspend-until-done `generate()`
call, hiding the underlying poll loop from callers.

**Tech Stack:** Python/FastAPI (orchestration wrapper), ACE-Step 1.5
(vendored, unmodified), Kotlin + Ktor (app-side client).

## Global Constraints

- Music generation has **no on-device fallback** — the existing procedural
  tone generator in `MediaGenerateAudioTool` remains the only fallback,
  used whenever no server is configured or the configured server fails.
  There is no "prefer local" setting to build, unlike the image feature.
- The real ACE-Step API is **asynchronous** (verified against
  `docs/en/API.md` in the ACE-Step-1.5 repo, 2026-08-03): `POST
  /release_task` returns a `task_id`, `POST /query_result` polls it,
  `GET /v1/audio?path=...` downloads the result once `status: 1`. Do not
  design around a synchronous request/response — it does not exist.
- Every diagnostic/safety discipline established in the image-generation
  branch applies here too: log failures (don't swallow them silently),
  guard response size before decoding, `catch (Throwable)` not
  `catch (Exception)` around any native/codec-adjacent decode path, and the
  server URL setting is opt-in and empty by default — never assumed.
- `meow-music`'s own generation logic is not written by this plan — ACE-Step
  ships its own server. This plan only writes the orchestration wrapper
  around it.

---

## File Structure

- Create (server-side, deployed on `meow-nb`, not part of the app repo):
  `meow-music/Dockerfile`, `meow-music/orchestrator.py`.
- Create: `app/src/main/java/com/example/meowmba/core/media/MusicGenerator.kt`
  — the interface.
- Create: `app/src/main/java/com/example/meowmba/core/media/RemoteMusicGenerator.kt`
  — the Ktor client with polling.
- Modify: `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt`
  — `MediaGenerateAudioTool` gains a `remoteGenerator` supplier and the
  same try-then-fallback shape `MediaGenerateTool` already has.
- Modify: `app/src/main/java/com/example/meowmba/ui/companion/CompanionState.kt`,
  `CompanionViewModel.kt`, `SettingsScreen.kt`, `CompanionScreen.kt` — add
  `musicServerUrl`, following the exact `imageServerUrl` pattern (no
  "prefer" toggle needed here).
- Test: `app/src/test/java/com/example/meowmba/RemoteMusicGeneratorTest.kt`
- Test: `app/src/test/java/com/example/meowmba/MediaGenerateAudioToolRoutingTest.kt`

---

### Task 1: `meow-music` — deploy ACE-Step 1.5 + orchestration wrapper

**This is infrastructure work on `meow-nb`, not app code.** Executed via
SSH against the real host, not a local Android build.

**Files:**
- Create: `meow-music/Dockerfile`
- Create: `meow-music/orchestrator.py`

- [ ] **Step 1: Vendor ACE-Step 1.5 and confirm its own server runs**

```bash
ssh meow-nb "mkdir -p /home/claude/meow-music && cd /home/claude/meow-music && \
  git clone https://github.com/ace-step/ACE-Step-1.5.git vendor"
```

Follow the vendored repo's own README setup instructions (model download,
`uv` environment setup) to get `uv run acestep-api` running standalone
first, on a throwaway port, and confirm with a real `curl` round-trip
against `POST /release_task` → `POST /query_result` → `GET /v1/audio`
before writing any orchestration code around it. Do not write the wrapper
against an API you haven't personally exercised — the request/response
shapes above are correct as of this plan's writing but the vendored repo
may have moved; verify against whatever `docs/en/API.md` says in the
commit you actually clone.

- [ ] **Step 2: Write the orchestration wrapper**

```python
# meow-music/orchestrator.py
"""
Thin wrapper around ACE-Step's own acestep-api. Does the Ollama-unload
dance before generation and re-exposes the same job/poll shape the app's
RemoteMusicGenerator expects, so the app never talks to acestep-api
directly (keeping the swap-orchestration in one place, same pattern as
meow-image).
"""
import os
import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

ACESTEP_BASE = "http://127.0.0.1:8001"
OLLAMA_BASE = os.environ.get("OLLAMA_BASE", "http://127.0.0.1:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "qwen3.6:35b-a3b")


class GenerateRequest(BaseModel):
    prompt: str
    duration_sec: int = 30


async def _unload_ollama():
    async with httpx.AsyncClient(timeout=10.0) as client:
        await client.post(
            f"{OLLAMA_BASE}/api/generate",
            json={"model": OLLAMA_MODEL, "keep_alive": 0},
        )


@app.post("/generate")
async def generate(req: GenerateRequest):
    await _unload_ollama()
    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.post(
            f"{ACESTEP_BASE}/release_task",
            json={
                "prompt": req.prompt,
                "audio_duration": req.duration_sec,
                "audio_format": "mp3",
                "inference_steps": 8,
                "model": "acestep-v15-turbo",
            },
        )
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"ACE-Step release_task HTTP {resp.status_code}")
    body = resp.json()
    return {"task_id": body["data"]["task_id"]}


@app.get("/status/{task_id}")
async def status(task_id: str):
    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.post(
            f"{ACESTEP_BASE}/query_result",
            json={"task_id_list": [task_id]},
        )
    if resp.status_code != 200:
        raise HTTPException(status_code=502, detail=f"ACE-Step query_result HTTP {resp.status_code}")
    results = resp.json()["data"]
    if not results:
        raise HTTPException(status_code=404, detail="unknown task_id")
    entry = results[0]
    return {"status": entry["status"], "result": entry.get("result")}


@app.get("/result/{task_id}")
async def result(task_id: str):
    import json
    status_resp = await status(task_id)
    if status_resp["status"] != 1:
        raise HTTPException(status_code=409, detail="task not finished")
    files = json.loads(status_resp["result"])
    audio_path = files[0]["file"]
    async with httpx.AsyncClient(timeout=30.0) as client:
        audio_resp = await client.get(f"{ACESTEP_BASE}{audio_path}")
    if audio_resp.status_code != 200:
        raise HTTPException(status_code=502, detail="failed to fetch generated audio")
    from fastapi.responses import Response
    return Response(content=audio_resp.content, media_type="audio/mpeg")
```

- [ ] **Step 3: Write the Dockerfile**

```dockerfile
# meow-music/Dockerfile
FROM python:3.11-slim
WORKDIR /app
RUN pip install --no-cache-dir fastapi uvicorn httpx
COPY orchestrator.py .
EXPOSE 9200
CMD ["uvicorn", "orchestrator:app", "--host", "0.0.0.0", "--port", "9200"]
```

This container runs the orchestrator only — ACE-Step's own `acestep-api`
runs as a separate sidecar process/container on the same host, reachable at
`127.0.0.1:8001` from the orchestrator's perspective (adjust
`ACESTEP_BASE` if you deploy it as a separate Docker container rather than
a host process — in that case use the container's Docker-network hostname
instead of `127.0.0.1`, and note this deviation in your report).

- [ ] **Step 4: Deploy and verify end-to-end**

```bash
ssh meow-nb "cd /home/claude/meow-music && docker build -t meow-music . && \
  docker run -d --name meow-music --restart unless-stopped \
  -p 9200:9200 --network host meow-music"
```

Run a real end-to-end test from the SSH session:
```bash
curl -X POST http://127.0.0.1:9200/generate -H 'Content-Type: application/json' \
  -d '{"prompt": "upbeat lofi hip hop", "duration_sec": 15}'
# poll GET /status/{task_id} until status: 1
# then GET /result/{task_id} and confirm real audio bytes come back (check file size > 0, check `file` command reports a real audio format)
```

Do not report this task DONE without having actually run this end-to-end
round trip and confirmed real audio bytes were produced — this is
infrastructure the rest of the plan depends on being real, not aspirational.

- [ ] **Step 5: Report**

Include in your report: the exact deployed port, whether ACE-Step's own
server needed to run as a sidecar container or host process (and why), and
the measured wall-clock time for the 15-second test generation (needed by
Task 3 to pick a sane client-side timeout).

---

### Task 2: `MusicGenerator` interface + `RemoteMusicGenerator` client

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/media/MusicGenerator.kt`
- Create: `app/src/main/java/com/example/meowmba/core/media/RemoteMusicGenerator.kt`
- Test: `app/src/test/java/com/example/meowmba/RemoteMusicGeneratorTest.kt`

**Interfaces:**
- Produces: `interface MusicGenerator { fun isAvailable(): Boolean;
  suspend fun generate(prompt: String, durationSec: Int = 30):
  Result<ByteArray> }` and `RemoteMusicGenerator(baseUrl: String, engine:
  HttpClientEngine = Android.create())` — Task 3 wires this into the app.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.meowmba

import com.example.meowmba.core.media.RemoteMusicGenerator
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteMusicGeneratorTest {

    @Test
    fun `isAvailable is true whenever a base URL is configured`() {
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", MockEngine { respond("") })
        assertTrue(generator.isAvailable())
    }

    @Test
    fun `generate polls status until complete then downloads the result`() = runTest {
        var statusCallCount = 0
        val fakeAudioBytes = byteArrayOf(0x49, 0x44, 0x33) // "ID3" — real MP3 header magic bytes
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/generate" ->
                    respond(
                        """{"task_id": "abc-123"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                request.url.encodedPath == "/status/abc-123" -> {
                    statusCallCount++
                    val status = if (statusCallCount < 2) 0 else 1
                    respond(
                        """{"status": $status}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                request.url.encodedPath == "/result/abc-123" ->
                    respond(
                        content = fakeAudioBytes,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "audio/mpeg")
                    )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", engine, pollIntervalMs = 1L)

        val result = generator.generate("upbeat lofi", durationSec = 15)

        assertTrue(result.isSuccess)
        assertEquals(2, statusCallCount)
    }

    @Test
    fun `generate reports an honest failure on a non-2xx response from /generate`() = runTest {
        val engine = MockEngine { respond("server error", HttpStatusCode.InternalServerError) }
        val generator = RemoteMusicGenerator("http://192.168.1.10:9200", engine)

        val result = generator.generate("upbeat lofi")

        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.RemoteMusicGeneratorTest"`
Expected: compile failure — the classes don't exist yet.

- [ ] **Step 3: Write `MusicGenerator.kt`**

```kotlin
package com.example.meowmba.core.media

interface MusicGenerator {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, durationSec: Int = 30): Result<ByteArray>
}
```

- [ ] **Step 4: Write `RemoteMusicGenerator.kt`**

```kotlin
package com.example.meowmba.core.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to meow-music's orchestration wrapper (not ACE-Step's own API
 * directly — the wrapper owns the Ollama-swap and re-exposes a stable
 * job/poll contract). Opt-in: only constructed when the user has entered a
 * server URL in Settings.
 *
 * Wire contract: POST {baseUrl}/generate {"prompt","duration_sec"} ->
 * {"task_id"}; GET {baseUrl}/status/{id} -> {"status": 0|1|2}; GET
 * {baseUrl}/result/{id} -> raw audio bytes once status is 1.
 */
class RemoteMusicGenerator(
    private val baseUrl: String,
    engine: HttpClientEngine = Android.create(),
    private val pollIntervalMs: Long = 2_000L,
    private val maxPolls: Int = 60 // 2min at 2s intervals; generation is expected in seconds-to-low-tens-of-seconds per Task 1's measured timing
) : MusicGenerator {

    private val client = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024 // 32MB ceiling, same discipline as RemoteImageGenerator
    }

    override fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun generate(prompt: String, durationSec: Int): Result<ByteArray> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("No music server configured"))
        }
        return try {
            val taskId = startGeneration(prompt, durationSec)
                ?: return Result.failure(IllegalStateException("Music server did not return a task id"))
            pollUntilDone(taskId)
        } catch (e: Throwable) {
            Result.failure(IllegalStateException("Music server unreachable: ${e.message ?: "network error"}"))
        } finally {
            client.close()
        }
    }

    private suspend fun startGeneration(prompt: String, durationSec: Int): String? {
        val response = client.post("$baseUrl/generate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(GenerateRequest.serializer(), GenerateRequest(prompt, durationSec)))
        }
        if (!response.status.isSuccess()) return null
        val body = Json.decodeFromString(TaskIdResponse.serializer(), String(response.readBytes()))
        return body.task_id
    }

    private suspend fun pollUntilDone(taskId: String): Result<ByteArray> {
        repeat(maxPolls) {
            val response = client.get("$baseUrl/status/$taskId")
            if (response.status.isSuccess()) {
                val status = Json.decodeFromString(StatusResponse.serializer(), String(response.readBytes()))
                when (status.status) {
                    1 -> return fetchResult(taskId)
                    2 -> return Result.failure(IllegalStateException("Music generation failed on the server"))
                    // 0 = still running — keep polling
                }
            }
            delay(pollIntervalMs)
        }
        return Result.failure(IllegalStateException("Music generation timed out waiting for the server"))
    }

    private suspend fun fetchResult(taskId: String): Result<ByteArray> {
        val response = client.get("$baseUrl/result/$taskId")
        if (!response.status.isSuccess()) {
            return Result.failure(IllegalStateException("Music server HTTP ${response.status.value} fetching result"))
        }
        val contentLength = response.contentLength()
        if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Music server response too large (${contentLength} bytes)"))
        }
        val bytes = response.readBytes()
        if (bytes.size > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Music server response too large (${bytes.size} bytes)"))
        }
        return Result.success(bytes)
    }
}

@Serializable
private data class GenerateRequest(val prompt: String, val duration_sec: Int)

@Serializable
private data class TaskIdResponse(val task_id: String)

@Serializable
private data class StatusResponse(val status: Int)
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.RemoteMusicGeneratorTest"`
Expected: PASS, all 3 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/media/MusicGenerator.kt \
        app/src/main/java/com/example/meowmba/core/media/RemoteMusicGenerator.kt \
        app/src/test/java/com/example/meowmba/RemoteMusicGeneratorTest.kt
git commit -m "feat(media): add opt-in RemoteMusicGenerator (polls meow-music's async job API)"
```

---

### Task 3: Wire into `MediaGenerateAudioTool` + Settings

**Files:**
- Modify: `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt`
  (`MediaGenerateAudioTool`)
- Modify: `CompanionState.kt`, `CompanionViewModel.kt`, `SettingsScreen.kt`,
  `CompanionScreen.kt` — add `musicServerUrl`, following the exact
  `imageServerUrl` pattern from the already-landed image-routing branch
  (read that branch's diff/current code for the precise shape — same
  field-then-setter-then-storage-key-then-UI structure, just no
  "prefer" toggle since there's nothing to prefer between).

**Interfaces:**
- Consumes: `MusicGenerator`/`RemoteMusicGenerator` from Task 2.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.meowmba

import com.example.meowmba.core.agent.tools.MediaGenerateAudioTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.media.MusicGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeMusicGenerator(
    private val available: Boolean,
    private val succeeds: Boolean
) : MusicGenerator {
    var callCount = 0
    override fun isAvailable() = available
    override suspend fun generate(prompt: String, durationSec: Int): Result<ByteArray> {
        callCount++
        return if (succeeds) Result.success(byteArrayOf(1, 2, 3))
               else Result.failure(IllegalStateException("boom"))
    }
}

class MediaGenerateAudioToolRoutingTest {

    private fun workspace(): Workspace =
        Workspace(kotlin.io.path.createTempDirectory().toFile())

    @Test
    fun `uses the remote generator when configured and available`() = runTest {
        val remote = FakeMusicGenerator(available = true, succeeds = true)
        val tool = MediaGenerateAudioTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "lofi beat"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `falls back to the tone generator when nothing is configured`() = runTest {
        val tool = MediaGenerateAudioTool(workspace())

        val result = tool.execute(mapOf("prompt" to "lofi beat"))

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("audio_"))
    }

    @Test
    fun `falls back to the tone generator when the remote generator fails`() = runTest {
        val remote = FakeMusicGenerator(available = true, succeeds = false)
        val tool = MediaGenerateAudioTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "lofi beat"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.MediaGenerateAudioToolRoutingTest"`
Expected: compile failure.

- [ ] **Step 3: Update `MediaGenerateAudioTool`**

Add a `remoteGenerator: (() -> MusicGenerator?)? = null` constructor
parameter. At the top of `execute()`, before the existing tone-generation
code, try the remote generator the same way `MediaGenerateTool.tryGenerators`
already does (log the failure with `Log.w`, don't throw, fall through to
the existing tone-generator code unchanged on any failure or when nothing
is configured). Save the returned bytes to a `.mp3` file in the workspace
(the existing tone generator writes `.wav` via `writeWav` — name the new
file path distinctly, e.g. `audio_<safe-prompt>.mp3` vs the tone
generator's `audio_<safe-prompt>.wav`, so which path produced a given file
is visible from its extension, mirroring the existing `image_*` vs
`canvas_*` naming convention already used for images).

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.MediaGenerateAudioToolRoutingTest"`
Expected: PASS, all 3 tests.

- [ ] **Step 5: Add Settings wiring**

Following the exact `imageServerUrl`/`setImageServerUrl`/`KEY_IMAGE_SERVER_URL`
pattern in `CompanionState.kt`/`CompanionViewModel.kt`/`SettingsScreen.kt`/
`CompanionScreen.kt`, add `musicServerUrl` end to end. No boolean toggle is
needed (there is nothing to prefer between). Add a `CompanionViewModelTest.kt`
test verifying `setMusicServerUrl` trims, normalizes (strip trailing slash,
same as the image field's fix), and persists — using `mockSecureStorage`
and `verify()`, not the no-op construction the image feature's review had
to catch and fix — get it right the first time here.

- [ ] **Step 6: Run full suite + build**

Run: `./gradlew testDebugUnitTest && ./gradlew assembleDebug`
Expected: PASS (same 3 pre-existing unrelated `ModelDownloadViewModelTest`
failures acceptable, nothing else), BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionState.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt \
        app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt \
        app/src/test/java/com/example/meowmba/MediaGenerateAudioToolRoutingTest.kt \
        app/src/test/java/com/example/meowmba/CompanionViewModelTest.kt
git commit -m "feat(settings): wire opt-in music server URL into MediaGenerateAudioTool"
```

---

## Plan Self-Review Notes

- **Spec coverage:** server deployment (Task 1), client + polling (Task 2),
  tool + settings wiring (Task 3) — covers Track 2 of the design doc in
  full.
- **Placeholder scan:** the ACE-Step wire contract in Task 1/Task 2 is the
  real, verified `docs/en/API.md` contract as of 2026-08-03 — flagged
  explicitly in Task 1 Step 1 to re-verify against whatever commit is
  actually cloned, since an upstream API change between now and execution
  is a real (if small) risk this plan cannot eliminate, only flag.
- **Type consistency:** `MusicGenerator.generate(prompt, durationSec)`
  signature matches across the interface, `RemoteMusicGenerator`, the fake
  in Task 3's test, and the `MediaGenerateAudioTool` call site.
