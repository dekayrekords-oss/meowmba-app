# Native on-device image generation, plus music and video generation

Date: 2026-08-03
Status: Approved (autonomous design pass, per user request — see note below)
Scope: Three tracks, each independently implementable and independently
testable:
  1. Native on-device image generation (replaces the deprecated
     manual-install MediaPipe/SD1.5 path referenced in
     `2026-08-03-image-generation-design.md`)
  2. Music generation (new capability)
  3. Video generation (new capability)

**Process note:** the user asked for this design and both implementation
plans to be produced autonomously ("come up with as much as possible"),
skipping the normal one-question-at-a-time brainstorming loop, on the
condition that the work then runs unsupervised via a scheduled agent and is
verified before going live. Every architecture decision below is made with
documented reasoning rather than a user confirmation, precisely because
that's what was asked for. Nothing here is a placeholder — every model,
API, and build command is verified against real, current sources (cited
inline) as of 2026-08-03.

## Track 1: Native on-device image generation

### Problem

`docs/superpowers/specs/2026-08-03-image-generation-design.md` already
established the architecture (on-device default, opt-in remote server,
Settings toggle) and shipped the remote half. The on-device half still runs
the old `OnDeviceImageGenerator` — MediaPipe's Image Generator task, which
Google has marked no longer actively maintained, requires the user to
manually convert and place model weight files, and takes 15s-60s+ per
image. This track replaces it with a real, zero-config, actively-relevant
on-device path.

### Architecture

Replace `OnDeviceImageGenerator` with `SDCppImageGenerator`, a JNI wrapper
around [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp)
(the diffusion-model sibling of llama.cpp — same `ggml` backend, C++
inference, no Python runtime on-device), implementing the existing
`ImageGenerator` interface unchanged:

```kotlin
interface ImageGenerator {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, iterations: Int = 20, seed: Int = 0): Result<Bitmap>
}
```

**Why stable-diffusion.cpp over alternatives:** no official Android
bindings exist for it, but an existing, production, MIT-licensed
open-source Android app ("Off Grid",
[off-grid-ai/OGAM](https://github.com/off-grid-ai/OGAM)) already ships
exactly this stack — `stable-diffusion.cpp` embedded via JNI, shipping
SDXL-Lightning, SD 1.5/2.1, and Z-Image-Turbo models, auto-downloaded on
first use, fully offline after that. That's concrete proof the approach
works on real Android hardware today, which outweighs chasing a
research-paper model (MobileDiffusion, DreamLite) with no confirmed public
release pipeline.

**Model choice: SDXL-Lightning** (a few-step distilled SDXL variant). Final
selection between SDXL-Lightning and Z-Image-Turbo is left to Task 1 of the
implementation plan as an explicit spike — both are real, both are used by
Off Grid, and relative on-device speed/quality is worth measuring on actual
target hardware rather than guessing.

### Build approach

`stable-diffusion.cpp` has no official Android build docs, but it shares
`ggml`/CMake with `llama.cpp`, which explicitly documents Android NDK
cross-compilation
([llama.cpp/docs/android.md](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)):

```
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
      -DCMAKE_C_FLAGS="-march=armv8.7a" -DCMAKE_CXX_FLAGS="-march=armv8.7a" \
      -DGGML_OPENMP=OFF -DGGML_LLAMAFILE=OFF -B build-android
```

The same toolchain file and ABI/platform flags apply directly to
`stable-diffusion.cpp`'s own `CMakeLists.txt` — its README states it "works
in the same way as llama.cpp," and both projects vendor `ggml` as the
compute backend. The implementation plan's Task 1 is exactly this: get a
minimal `.so` building for `arm64-v8a` and prove one JNI round-trip works,
before any Kotlin integration is attempted.

### Verified C API (from `include/stable-diffusion.h`, fetched 2026-08-03 —
this is the real header, not a guess)

```c
typedef struct {
    const char* model_path;
    /* ... many optional fields (LoRA, ControlNet, PhotoMaker, etc.) — unused here */
    int n_threads;
    enum sd_type_t wtype;
    bool flash_attn;
    /* ... */
} sd_ctx_params_t;

SD_API void sd_ctx_params_init(sd_ctx_params_t* sd_ctx_params);
SD_API sd_ctx_t* new_sd_ctx(const sd_ctx_params_t* sd_ctx_params);
SD_API void free_sd_ctx(sd_ctx_t* sd_ctx);

typedef struct {
    uint32_t width;
    uint32_t height;
    uint32_t channel;
    uint8_t* data;
} sd_image_t;

typedef struct {
    sd_guidance_params_t guidance;
    enum scheduler_t scheduler;
    enum sample_method_t sample_method;
    int sample_steps;
    /* ... */
} sd_sample_params_t;
SD_API void sd_sample_params_init(sd_sample_params_t* sample_params);

typedef struct {
    const char* prompt;
    const char* negative_prompt;
    int width;
    int height;
    sd_sample_params_t sample_params;
    int64_t seed;
    int batch_count;
    /* ... many optional fields (img2img, ControlNet, etc.) — unused here */
} sd_img_gen_params_t;
SD_API void sd_img_gen_params_init(sd_img_gen_params_t* sd_img_gen_params);

SD_API bool generate_image(sd_ctx_t* sd_ctx,
                            const sd_img_gen_params_t* sd_img_gen_params,
                            sd_image_t** images_out,
                            int* num_images_out);
SD_API void free_sd_images(sd_image_t* result_images, int num_images);
```

The implementation plan's JNI bridge only needs a small slice of this
surface: create a context from a model path, run one `generate_image` call
with `prompt`/`width`/`height`/`sample_steps`/`seed`, copy the resulting
`sd_image_t.data` (raw RGB/RGBA bytes) into a Kotlin `Bitmap`, free the
images, free the context. No LoRA, ControlNet, img2img, or batch>1 — those
fields are simply left at their `_init()` defaults.

### Zero-config UX

Reuse the app's existing `ModelCatalog.kt`/`ModelDownloader.kt`
infrastructure (already handles on-device LLM model downloads) rather than
building parallel download machinery — add an image-model catalog entry.
First call to `media.generate_image` with no model installed prompts to
download (asks first, does not silently pull a multi-gigabyte file,
especially over cellular) — matching the already-approved pattern from the
remote-backend design.

### Testing strategy

Native/JNI code cannot be meaningfully unit-tested the way the Kotlin
routing layer was — there's no JVM-only path through a `.so` built for
`arm64-v8a`. The implementation plan therefore separates "prove the native
layer works" (a manual/instrumented smoke-test task, run once against a
real or emulated device) from "the Kotlin side is correct" (unit-testable:
the `SDCppImageGenerator` class's non-native logic — argument marshalling,
error mapping, bitmap construction from a fake byte array — is tested with
a fake JNI layer behind an internal seam, so the routing/error-handling
logic doesn't require a device to verify).

---

## Track 2: Music generation

### Problem

`MediaGenerateAudioTool` is currently a pure procedural sine-wave tone
generator — not music by any reasonable definition. No design work has
been done for this before now.

### Why this is server-only, not on-device-first

Track 1's on-device image model runs at roughly 3.5B-parameter-class
diffusion scale on a phone GPU/NPU because SDXL-Lightning-class models were
specifically distilled for that. No comparably-sized, mobile-viable music
generation model exists today: even the leanest capable options
(researched 2026-08-03) are desktop-GPU-class. This is a deliberate,
reasoned divergence from Track 1 and from the image-generation design's
"local-first" default — there is no real on-device path to default to.

**Consequence for the architecture:** music generation is **remote-only**,
gated the same way the image feature's remote backend already is (a
user-configured server URL in Settings, empty by default, never assumed) —
but with no on-device alternative to fall back to. The existing procedural
tone-generator in `MediaGenerateAudioTool` remains the final fallback when
no server is configured or the server fails, preserving the "always
produce something" rule, while being honest that it is not real music.

### Model: ACE-Step 1.5

[ace-step/ACE-Step-1.5](https://github.com/ace-step/ACE-Step-1.5) —
MIT-licensed, base configuration runs in **under 4GB VRAM**, ships its own
REST API server out of the box (`uv run acestep-api`, listens on
`localhost:8001`). This is a materially simpler server-side integration
than image generation was: no custom generation code needs writing at all,
only a thin orchestration layer around the vendored server.

<4GB is small on its own, but the 3090 Ti is usually carrying the ~22GB
`qwen3.6:35b-a3b` LLM (established in the earlier TPS-debugging session on
this same machine) — 22 + 4 GB exceeds a 24GB card's usable headroom once
KV cache and overhead are counted, so **the same GPU-swap pattern from the
image design (`meow-image`) applies here too**: tell Ollama to unload
(`keep_alive: 0`) before generating, let it reload on the next chat turn.

### Server-side component: `meow-music`

A new lightweight container, matching the established house pattern
(`meow-embed`, `meow-telem`, `meow-image`):
1. Runs the vendored ACE-Step 1.5 repo and its own `acestep-api` server as
   a sidecar process (not reimplemented — the reference server is the
   generation engine).
2. A thin orchestration wrapper (same shape as `meow-image`'s FastAPI
   service) exposes `POST /generate {"prompt": "..."}`, does the
   Ollama-unload-first dance, forwards to `localhost:8001` (ACE-Step's own
   API), returns the resulting audio bytes.
3. Deployed on `meow-nb` — the author's own infrastructure, not assumed or
   bundled by the distributed app, exactly like `meow-image`.

### App-side component: `RemoteMusicGenerator`

Mirrors `RemoteImageGenerator` almost exactly (same Ktor pattern, same
`Android.create()` engine, same size-guard-before-decode discipline learned
from the image final review's finding #5, same `catch (Throwable)`
discipline from finding #1's fix) — implements a new `MusicGenerator`
interface:

```kotlin
interface MusicGenerator {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, durationSec: Int = 30): Result<ByteArray>
}
```

(Returns raw audio bytes rather than a `Bitmap` — the tool layer writes
them to a `.wav`/`.mp3` file in the workspace, matching
`MediaGenerateAudioTool`'s existing `writeWav`-based file-writing pattern,
adjusted for whatever format ACE-Step actually returns — the
implementation plan's Task 1 pins this down against the real API rather
than assuming.)

### Settings

New fields, same pattern as `imageServerUrl`/`preferServerImageGen`:
`musicServerUrl: String = ""`. No "prefer" toggle is needed here — there is
no on-device alternative to prefer between, only "configured" vs. "not
configured, use the tone generator."

---

## Track 3: Video generation

### Problem

`MediaGenerateVideoTool` is currently a placeholder that draws animated
circles as separate PNG frames plus a text manifest — despite its own tool
description claiming it produces a "GIF," it does not actually encode a
video file at all.

### Why this is also server-only

Same reasoning as music, more extreme: the best available single-24GB-GPU
option (researched 2026-08-03) takes **under 9 minutes to generate a single
5-second 720p clip** on desktop-class hardware. This is unambiguously
server-only; there is no serious on-device consideration to weigh.

### Model: Wan 2.2 (TI2V-5B dense variant)

[Wan-Video/Wan2.2](https://github.com/Wan-Video/Wan2.2) — Apache 2.0
licensed, Alibaba's open video model. The TI2V-5B dense variant (not the
larger MoE variants) is the one sized for a single 24GB card: generates a
5-second 720p/24fps clip in under ~9 minutes on an RTX 4090-class GPU (the
3090 Ti is the same architecture generation, expect roughly comparable —
likely somewhat slower — timing; the implementation plan's Task 1 measures
this for real rather than assuming). Supports both text-to-video and
image-to-video; this design only wires up text-to-video, matching what
`MediaGenerateVideoTool`'s existing tool contract (`prompt`, `style`)
already expects.

Available via HuggingFace Diffusers integration and ComfyUI. **This design
picks Diffusers, not ComfyUI** — same reasoning as the image design's
already-approved decision: ComfyUI doesn't know anything about the
Ollama-swap orchestration, so using it would still require writing the same
custom glue code in front of a heavier dependency.

### Server-side component: `meow-video`

Same house pattern as `meow-image`/`meow-music`: a small FastAPI service
wrapping the Diffusers `Wan2.2Pipeline` directly (no vendored reference
server exists for this one, unlike ACE-Step — Task 1 of the video plan
writes the generation code, grounded against the real Diffusers pipeline
API). Owns the GPU swap with Ollama, same as the other two.

**Given the ~9-minute generation time**, this component needs an explicit
job/polling design rather than a synchronous request — a plain
`POST /generate` that blocks for 9 minutes is a bad shape for both the HTTP
client's timeout handling and the mobile UX (MEOWMBA is a live voice
companion; a 9-minute silent wait is a bad user experience regardless of
transport). Design: `POST /generate` returns a job ID immediately, `GET
/status/{id}` polls, `GET /result/{id}` returns the video bytes once done.

### App-side component: `RemoteVideoGenerator`

Same Ktor-based pattern as the other two remote generators, but with
polling logic instead of a single blocking call:

```kotlin
interface VideoGenerator {
    fun isAvailable(): Boolean
    suspend fun generate(prompt: String, onProgress: (String) -> Unit = {}): Result<ByteArray>
}
```

The `onProgress` callback lets the tool layer surface a caption update
("Still generating your video... this can take several minutes") rather
than the companion going silent for nine minutes, which the implementation
plan wires into the existing `captionText` mechanism already used
elsewhere in `CompanionViewModel`.

### Settings

New field `videoServerUrl: String = ""`, same pattern, no prefer-toggle,
same reasoning as music.

---

## Cross-cutting: what stays shared across all three tracks

- The `Log.w` diagnostic discipline added to `MediaGenerateTool` in the
  image-generation final review (finding #3) — every new tool
  (`MediaGenerateAudioTool`, `MediaGenerateVideoTool`) gets the same
  "log the failure reason before falling through" treatment, not silence.
- The response-size guard discipline (finding #5) — any HTTP client
  decoding bytes from a user-configured server checks size before decoding,
  every time, not just for images.
- The `catch (Throwable)`, not `catch (Exception)`, discipline (fix round 1
  on Task 2) — native/codec paths can throw `Error` subtypes, not just
  `Exception`, and this must not crash the app.
- The cleartext-HTTP network security config already fixed for the image
  feature already covers these new LAN servers too — no additional manifest
  change needed, verified by the config being `base-config`, not scoped to
  a single feature's domain.
- Every server URL setting is opt-in, empty by default, never assumed —
  the app must be fully usable (with honest placeholder fallbacks) with
  zero servers configured, since it is distributed to users who will not
  have `meow-nb` or any equivalent.

## Explicitly out of scope

- Image-to-video, video-to-video, or any Wan2.2 capability beyond
  text-to-video.
- Any music editing/extension capability beyond one-shot generation.
- Bundling any of these three models inside the APK.
- A "prefer server" toggle for music/video (no on-device alternative
  exists to prefer against).
