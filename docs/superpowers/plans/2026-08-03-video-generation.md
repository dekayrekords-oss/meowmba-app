# Video Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fake-frames-as-manifest placeholder in
`MediaGenerateVideoTool` with real text-to-video generation via a
self-hosted Wan2.2 TI2V-5B server, opt-in and remote-only, per
`docs/superpowers/specs/2026-08-03-native-image-music-video-design.md`
(Track 3).

**Architecture:** `meow-video` (server-side) wraps the HuggingFace
Diffusers `WanPipeline` directly in a custom FastAPI service (no vendored
reference server exists for this one, unlike ACE-Step) — same house
pattern as `meow-image`. Given ~9-minute generation times, this is a
job/poll API from the start, not a retrofit. `RemoteVideoGenerator`
(app-side) polls and reports progress via a callback so the companion can
say something instead of going silent for nine minutes.

**Tech Stack:** Python/FastAPI + HuggingFace Diffusers (`WanPipeline`),
Kotlin + Ktor.

## Global Constraints

- No on-device fallback exists or is planned — same reasoning as music,
  more extreme (9 minutes on desktop-class hardware rules out mobile
  entirely). The existing frames-manifest placeholder in
  `MediaGenerateVideoTool` remains the fallback when nothing is configured
  or the server fails.
- Verified real API (HuggingFace Diffusers docs, fetched 2026-08-03):
  `from diffusers import AutoencoderKLWan, WanPipeline`; `WanPipeline.from_pretrained("Wan-AI/Wan2.2-TI2V-5B-Diffusers", vae=vae, dtype=torch.bfloat16)`;
  call with `prompt`, `negative_prompt`, `height`, `width`, `num_frames`,
  `num_inference_steps`, `guidance_scale`; result is `.frames[0]`, saved
  via `diffusers.utils.export_to_video(frames, path, fps=16)`. Do not
  deviate from these real names.
- This is a genuinely long-running operation. The server API is job/poll
  from Task 1 onward — there is no synchronous version to build first and
  refactor later.
- Same cross-cutting disciplines as the other two tracks: log failures,
  guard response size before reading into memory, `catch (Throwable)`, GPU
  swap with Ollama before generating, opt-in server URL empty by default.

---

## File Structure

- Create (server-side, on `meow-nb`): `meow-video/Dockerfile`,
  `meow-video/server.py`.
- Create: `app/src/main/java/com/example/meowmba/core/media/VideoGenerator.kt`
- Create: `app/src/main/java/com/example/meowmba/core/media/RemoteVideoGenerator.kt`
- Modify: `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt`
  (`MediaGenerateVideoTool`)
- Modify: `CompanionState.kt`, `CompanionViewModel.kt`, `SettingsScreen.kt`,
  `CompanionScreen.kt` — add `videoServerUrl`.
- Test: `app/src/test/java/com/example/meowmba/RemoteVideoGeneratorTest.kt`
- Test: `app/src/test/java/com/example/meowmba/MediaGenerateVideoToolRoutingTest.kt`

---

### Task 1: `meow-video` — FastAPI service wrapping `WanPipeline`

**Server-side infrastructure work on `meow-nb`.**

**Files:**
- Create: `meow-video/server.py`
- Create: `meow-video/Dockerfile`

- [ ] **Step 1: Write the FastAPI service**

```python
# meow-video/server.py
"""
Wraps HuggingFace Diffusers' WanPipeline (Wan2.2 TI2V-5B) as a job/poll
HTTP API. Generation takes minutes, not seconds, so this is async from the
start — never add a synchronous endpoint later, there is no shape this
fits into.
"""
import os
import uuid
import threading
import httpx
import torch
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel
from diffusers import AutoencoderKLWan, WanPipeline
from diffusers.utils import export_to_video

app = FastAPI()

OLLAMA_BASE = os.environ.get("OLLAMA_BASE", "http://127.0.0.1:11434")
OLLAMA_MODEL = os.environ.get("OLLAMA_MODEL", "qwen3.6:35b-a3b")
OUTPUT_DIR = "/tmp/meow-video-outputs"
os.makedirs(OUTPUT_DIR, exist_ok=True)

MODEL_ID = "Wan-AI/Wan2.2-TI2V-5B-Diffusers"
_pipeline = None
_pipeline_lock = threading.Lock()

# job_id -> {"status": "queued"|"running"|"done"|"failed", "path": str|None, "error": str|None}
_jobs = {}
_jobs_lock = threading.Lock()


def _load_pipeline():
    global _pipeline
    with _pipeline_lock:
        if _pipeline is None:
            vae = AutoencoderKLWan.from_pretrained(MODEL_ID, subfolder="vae", torch_dtype=torch.float32)
            _pipeline = WanPipeline.from_pretrained(MODEL_ID, vae=vae, torch_dtype=torch.bfloat16)
            _pipeline.to("cuda")
        return _pipeline


def _unload_ollama_sync():
    httpx.post(f"{OLLAMA_BASE}/api/generate",
               json={"model": OLLAMA_MODEL, "keep_alive": 0}, timeout=10.0)


def _run_generation(job_id: str, prompt: str):
    try:
        with _jobs_lock:
            _jobs[job_id]["status"] = "running"
        _unload_ollama_sync()
        pipe = _load_pipeline()
        negative_prompt = (
            "static, blurred, low quality, worst quality, deformed, "
            "watermark, subtitles"
        )
        output = pipe(
            prompt=prompt,
            negative_prompt=negative_prompt,
            height=720,
            width=1280,
            num_frames=81,          # 4*k+1 per Diffusers Wan docs; 81 frames @ 16fps = ~5s clip
            num_inference_steps=50,
            guidance_scale=5.0,
        ).frames[0]
        out_path = os.path.join(OUTPUT_DIR, f"{job_id}.mp4")
        export_to_video(output, out_path, fps=16)
        with _jobs_lock:
            _jobs[job_id] = {"status": "done", "path": out_path, "error": None}
    except Exception as e:
        with _jobs_lock:
            _jobs[job_id] = {"status": "failed", "path": None, "error": str(e)}


class GenerateRequest(BaseModel):
    prompt: str


@app.post("/generate")
def generate(req: GenerateRequest):
    job_id = str(uuid.uuid4())
    with _jobs_lock:
        _jobs[job_id] = {"status": "queued", "path": None, "error": None}
    thread = threading.Thread(target=_run_generation, args=(job_id, req.prompt), daemon=True)
    thread.start()
    return {"job_id": job_id}


@app.get("/status/{job_id}")
def status(job_id: str):
    with _jobs_lock:
        job = _jobs.get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="unknown job_id")
    return {"status": job["status"], "error": job["error"]}


@app.get("/result/{job_id}")
def result(job_id: str):
    with _jobs_lock:
        job = _jobs.get(job_id)
    if job is None:
        raise HTTPException(status_code=404, detail="unknown job_id")
    if job["status"] != "done":
        raise HTTPException(status_code=409, detail=f"job not finished (status={job['status']})")
    return FileResponse(job["path"], media_type="video/mp4")
```

Note on concurrency: this uses a plain Python `threading.Thread`, not a job
queue, deliberately — this is a single-GPU box serving one companion; a
second concurrent request while one is running would contend for the same
CUDA device and likely OOM or serialize badly regardless of queueing
sophistication. Reject concurrent requests instead of queueing them: add a
check in `/generate` that returns HTTP 429 if any job is currently
`"running"`, so this doesn't silently misbehave under concurrent load — add
this check as part of this task, it's a one-line `if any(j["status"] ==
"running" for j in _jobs.values()): raise HTTPException(429, ...)` guard
before creating the new job.

- [ ] **Step 2: Write the Dockerfile**

```dockerfile
# meow-video/Dockerfile
FROM nvidia/cuda:12.4.1-runtime-ubuntu22.04
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-pip ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN pip3 install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cu124
RUN pip3 install --no-cache-dir diffusers transformers accelerate fastapi uvicorn httpx ftfy
COPY server.py .
EXPOSE 9300
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "9300"]
```

`ffmpeg` is required by `export_to_video`'s default backend — verify this
during Step 3's real test rather than assuming; if `export_to_video` fails
looking for a different backend, check its actual error message and adjust
(the Diffusers docs did not specify which video-writer backend it uses
internally, so confirm empirically).

- [ ] **Step 3: Deploy and verify end-to-end (real, not assumed)**

```bash
ssh meow-nb "mkdir -p /home/claude/meow-video && cd /home/claude/meow-video"
# copy server.py and Dockerfile there, then:
ssh meow-nb "cd /home/claude/meow-video && docker build -t meow-video . && \
  docker run -d --name meow-video --restart unless-stopped --gpus all \
  -p 9300:9300 -v /home/claude/.cache/huggingface:/root/.cache/huggingface \
  meow-video"
```

(The `-v` mount persists the downloaded model weights across container
restarts — Wan2.2-TI2V-5B is several GB and should not re-download every
time the container restarts.)

Run a real end-to-end test:
```bash
curl -X POST http://127.0.0.1:9300/generate -H 'Content-Type: application/json' \
  -d '{"prompt": "a cat playing with yarn in slow motion"}'
# poll GET /status/{job_id} — expect "queued" -> "running" -> "done"
# this WILL take several minutes; do not treat a long wait as a failure
# then GET /result/{job_id} and confirm a real, playable MP4 comes back
# (check file size > 0, check `ffprobe` reports valid video stream info)
```

Record the actual measured generation time in your report — this feeds
Task 2's client-side timeout, and the design doc's ~9-minute estimate was
for an RTX 4090, not the box's actual 3090 Ti, so the real number may
differ.

Do not report this task DONE without having actually produced and verified
one real video file this way.

- [ ] **Step 4: Report**

Include: measured generation wall-clock time, confirmation `ffmpeg`/video
backend worked (or what had to change if not), the deployed port.

---

### Task 2: `VideoGenerator` interface + `RemoteVideoGenerator` client

**Files:**
- Create: `app/src/main/java/com/example/meowmba/core/media/VideoGenerator.kt`
- Create: `app/src/main/java/com/example/meowmba/core/media/RemoteVideoGenerator.kt`
- Test: `app/src/test/java/com/example/meowmba/RemoteVideoGeneratorTest.kt`

**Interfaces:**
- Produces: `interface VideoGenerator { fun isAvailable(): Boolean;
  suspend fun generate(prompt: String, onProgress: (String) -> Unit = {}):
  Result<ByteArray> }` and `RemoteVideoGenerator(baseUrl: String, engine:
  HttpClientEngine = Android.create())`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.meowmba

import com.example.meowmba.core.media.RemoteVideoGenerator
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

class RemoteVideoGeneratorTest {

    @Test
    fun `isAvailable is true whenever a base URL is configured`() {
        val generator = RemoteVideoGenerator("http://192.168.1.10:9300", MockEngine { respond("") })
        assertTrue(generator.isAvailable())
    }

    @Test
    fun `generate reports progress while polling and returns bytes when done`() = runTest {
        var pollCount = 0
        val progressUpdates = mutableListOf<String>()
        val fakeVideoBytes = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70) // real MP4 ftyp box header magic
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath == "/generate" ->
                    respond(
                        """{"job_id": "vid-123"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                request.url.encodedPath == "/status/vid-123" -> {
                    pollCount++
                    val status = when {
                        pollCount == 1 -> "queued"
                        pollCount < 3 -> "running"
                        else -> "done"
                    }
                    respond(
                        """{"status": "$status", "error": null}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                request.url.encodedPath == "/result/vid-123" ->
                    respond(
                        content = fakeVideoBytes, status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "video/mp4")
                    )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteVideoGenerator("http://192.168.1.10:9300", engine, pollIntervalMs = 1L)

        val result = generator.generate("a cat playing", onProgress = { progressUpdates.add(it) })

        assertTrue(result.isSuccess)
        assertEquals(3, pollCount)
        assertTrue(progressUpdates.isNotEmpty())
    }

    @Test
    fun `a failed job status is reported as a Result failure`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/generate" -> respond(
                    """{"job_id": "vid-456"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "/status/vid-456" -> respond(
                    """{"status": "failed", "error": "CUDA out of memory"}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        val generator = RemoteVideoGenerator("http://192.168.1.10:9300", engine, pollIntervalMs = 1L)

        val result = generator.generate("a cat playing")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("CUDA out of memory") == true)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.RemoteVideoGeneratorTest"`
Expected: compile failure.

- [ ] **Step 3: Write `VideoGenerator.kt`**

```kotlin
package com.example.meowmba.core.media

interface VideoGenerator {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, onProgress: (String) -> Unit = {}): Result<ByteArray>
}
```

- [ ] **Step 4: Write `RemoteVideoGenerator.kt`**

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
 * Talks to meow-video. Generation genuinely takes minutes (verified
 * against real hardware in this plan's Task 1) — onProgress lets the
 * caller surface something to the user instead of nine minutes of
 * silence.
 *
 * Wire contract: POST {baseUrl}/generate {"prompt"} -> {"job_id"};
 * GET {baseUrl}/status/{id} -> {"status": "queued"|"running"|"done"|"failed", "error"};
 * GET {baseUrl}/result/{id} -> raw MP4 bytes once status is "done".
 */
class RemoteVideoGenerator(
    private val baseUrl: String,
    engine: HttpClientEngine = Android.create(),
    private val pollIntervalMs: Long = 10_000L,
    private val maxPolls: Int = 90 // 15min at 10s intervals — generous given Task 1's ~9min measured baseline
) : VideoGenerator {

    private val client = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024 // video is bigger than audio; 64MB ceiling
    }

    override fun isAvailable(): Boolean = baseUrl.isNotBlank()

    override suspend fun generate(prompt: String, onProgress: (String) -> Unit): Result<ByteArray> {
        if (baseUrl.isBlank()) {
            return Result.failure(IllegalStateException("No video server configured"))
        }
        return try {
            onProgress("Starting video generation...")
            val jobId = startGeneration(prompt)
                ?: return Result.failure(IllegalStateException("Video server did not return a job id"))
            pollUntilDone(jobId, onProgress)
        } catch (e: Throwable) {
            Result.failure(IllegalStateException("Video server unreachable: ${e.message ?: "network error"}"))
        } finally {
            client.close()
        }
    }

    private suspend fun startGeneration(prompt: String): String? {
        val response = client.post("$baseUrl/generate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(GenerateRequest.serializer(), GenerateRequest(prompt)))
        }
        if (!response.status.isSuccess()) return null
        val body = Json.decodeFromString(JobIdResponse.serializer(), String(response.readBytes()))
        return body.job_id
    }

    private suspend fun pollUntilDone(jobId: String, onProgress: (String) -> Unit): Result<ByteArray> {
        repeat(maxPolls) { attempt ->
            val response = client.get("$baseUrl/status/$jobId")
            if (response.status.isSuccess()) {
                val status = Json.decodeFromString(StatusResponse.serializer(), String(response.readBytes()))
                when (status.status) {
                    "done" -> return fetchResult(jobId)
                    "failed" -> return Result.failure(
                        IllegalStateException(status.error ?: "Video generation failed on the server")
                    )
                    "running" -> onProgress("Still generating your video (this can take several minutes)...")
                    "queued" -> onProgress("Video generation queued...")
                }
            }
            delay(pollIntervalMs)
        }
        return Result.failure(IllegalStateException("Video generation timed out waiting for the server"))
    }

    private suspend fun fetchResult(jobId: String): Result<ByteArray> {
        val response = client.get("$baseUrl/result/$jobId")
        if (!response.status.isSuccess()) {
            return Result.failure(IllegalStateException("Video server HTTP ${response.status.value} fetching result"))
        }
        val contentLength = response.contentLength()
        if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Video server response too large (${contentLength} bytes)"))
        }
        val bytes = response.readBytes()
        if (bytes.size > MAX_RESPONSE_BYTES) {
            return Result.failure(IllegalStateException("Video server response too large (${bytes.size} bytes)"))
        }
        return Result.success(bytes)
    }
}

@Serializable
private data class GenerateRequest(val prompt: String)

@Serializable
private data class JobIdResponse(val job_id: String)

@Serializable
private data class StatusResponse(val status: String, val error: String? = null)
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.RemoteVideoGeneratorTest"`
Expected: PASS, all 3 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/media/VideoGenerator.kt \
        app/src/main/java/com/example/meowmba/core/media/RemoteVideoGenerator.kt \
        app/src/test/java/com/example/meowmba/RemoteVideoGeneratorTest.kt
git commit -m "feat(media): add opt-in RemoteVideoGenerator (polls meow-video's async job API, reports progress)"
```

---

### Task 3: Wire into `MediaGenerateVideoTool` + Settings + progress captions

**Files:**
- Modify: `app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt`
  (`MediaGenerateVideoTool`)
- Modify: `CompanionState.kt`, `CompanionViewModel.kt`, `SettingsScreen.kt`,
  `CompanionScreen.kt` — add `videoServerUrl`, same pattern as
  `imageServerUrl`/`musicServerUrl`, no prefer-toggle.
- Modify: `CompanionViewModel.kt` — the `onProgress` callback from
  `RemoteVideoGenerator.generate()` should update `captionText` (the same
  mechanism `AvatarAnimateTool`/wake-word errors already use to put text on
  screen) so a 9-minute wait isn't silent. Read how `captionText` is
  currently set elsewhere in `CompanionViewModel.kt` before wiring this —
  follow the existing update pattern (`_uiState.value =
  _uiState.value.copy(captionText = ...)`), don't invent a new mechanism.

**Interfaces:**
- Consumes: `VideoGenerator`/`RemoteVideoGenerator` from Task 2.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.meowmba

import com.example.meowmba.core.agent.tools.MediaGenerateVideoTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.agent.tools.Workspace
import com.example.meowmba.core.media.VideoGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeVideoGenerator(
    private val available: Boolean,
    private val succeeds: Boolean
) : VideoGenerator {
    var callCount = 0
    override fun isAvailable() = available
    override suspend fun generate(prompt: String, onProgress: (String) -> Unit): Result<ByteArray> {
        callCount++
        onProgress("testing progress callback")
        return if (succeeds) Result.success(byteArrayOf(1, 2, 3))
               else Result.failure(IllegalStateException("boom"))
    }
}

class MediaGenerateVideoToolRoutingTest {

    private fun workspace(): Workspace =
        Workspace(kotlin.io.path.createTempDirectory().toFile())

    @Test
    fun `uses the remote generator when configured and available`() = runTest {
        val remote = FakeVideoGenerator(available = true, succeeds = true)
        val tool = MediaGenerateVideoTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `falls back to the frame-manifest placeholder when nothing is configured`() = runTest {
        val tool = MediaGenerateVideoTool(workspace())

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).output.contains("video_"))
    }

    @Test
    fun `falls back to the placeholder when the remote generator fails`() = runTest {
        val remote = FakeVideoGenerator(available = true, succeeds = false)
        val tool = MediaGenerateVideoTool(workspace(), remoteGenerator = { remote })

        val result = tool.execute(mapOf("prompt" to "a cat playing"))

        assertEquals(1, remote.callCount)
        assertTrue(result is ToolResult.Success)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.MediaGenerateVideoToolRoutingTest"`
Expected: compile failure.

- [ ] **Step 3: Update `MediaGenerateVideoTool`**

Add `remoteGenerator: (() -> VideoGenerator?)? = null` and an `onProgress:
(String) -> Unit = {}` constructor parameter (the ViewModel wires this to
its caption-update mechanism; the tool itself stays UI-agnostic and just
forwards the callback). Try the remote generator first if configured and
available (log failures via `Log.w`, don't throw), save the resulting MP4
bytes to `video_<safe-prompt>.mp4` in the workspace on success, fall
through unchanged to the existing frame-manifest placeholder code
otherwise. **Also increase this tool's `timeoutMs`** — it currently sits at
`30_000` (30 seconds), copied from when the tool only drew placeholder
frames instantly; a real video generation is a 9-minute-class operation.
Follow the same reasoning `MediaGenerateTool`'s own `timeoutMs` comment
already documents for the equivalent image-generation problem (find that
comment and mirror its style) — set this to at least `900_000` (15 min),
with a comment explaining why, sourced from Task 1's measured real
generation time.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.example.meowmba.MediaGenerateVideoToolRoutingTest"`
Expected: PASS, all 3 tests.

- [ ] **Step 5: Add Settings + caption wiring**

Follow the `imageServerUrl`/`musicServerUrl` pattern exactly for
`videoServerUrl` end to end (state, setter with URL trim+trailing-slash
normalization, storage key, Settings UI, `CompanionScreen.kt` threading,
and a `CompanionViewModelTest.kt` test using `mockSecureStorage` +
`verify()` — do not repeat the image feature's original "tests don't
actually check persistence" mistake, which the image branch had to fix in
its final review; get it right here from the start).

Wire the tool's `onProgress` callback (from Task 3 Step 3) to update
`captionText` via the ViewModel's existing pattern when constructing
`MediaGenerateVideoTool` in the tool-registration block.

- [ ] **Step 6: Run full suite + build**

Run: `./gradlew testDebugUnitTest && ./gradlew assembleDebug`
Expected: PASS (same 3 pre-existing unrelated failures acceptable),
BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/meowmba/core/agent/tools/MediaTools.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionState.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionViewModel.kt \
        app/src/main/java/com/example/meowmba/ui/companion/SettingsScreen.kt \
        app/src/main/java/com/example/meowmba/ui/companion/CompanionScreen.kt \
        app/src/test/java/com/example/meowmba/MediaGenerateVideoToolRoutingTest.kt \
        app/src/test/java/com/example/meowmba/CompanionViewModelTest.kt
git commit -m "feat(settings): wire opt-in video server URL, progress captions, into MediaGenerateVideoTool"
```

---

## Plan Self-Review Notes

- **Spec coverage:** server (Task 1), client + polling + progress (Task
  2), tool + settings + caption wiring (Task 3) — covers Track 3 in full,
  including the design doc's explicit call for a progress-surfacing
  mechanism given the long generation time.
- **Placeholder scan:** the `WanPipeline` API is the real, verified
  Diffusers docs contract as of 2026-08-03 (fetched directly, not
  paraphrased from a summary) — the one open question flagged honestly is
  which video-encoding backend `export_to_video` actually needs at
  runtime (ffmpeg assumed, verified empirically in Task 1 Step 3 rather
  than asserted here as fact).
- **Type consistency:** `VideoGenerator.generate(prompt, onProgress)`
  signature matches across the interface, `RemoteVideoGenerator`, the fake
  in Task 3's test, and the `MediaGenerateVideoTool` call site; the
  server's job-status vocabulary (`"queued"|"running"|"done"|"failed"`)
  matches exactly between `meow-video/server.py` (Task 1) and
  `RemoteVideoGenerator`'s `when (status.status)` branches (Task 2) —
  a string-literal mismatch here would silently fail to ever reach the
  `"done"` branch, so this is worth double-checking during
  implementation, not just trusting the plan text.
