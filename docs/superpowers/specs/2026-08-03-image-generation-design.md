# Real image generation for MEOWMBA

Date: 2026-08-03
Status: Approved, ready for implementation planning
Scope: Image generation only. Music and video generation are separate features,
each to get their own design doc following this same process.

## Problem

MEOWMBA has two image-generation code paths today, and neither is good enough:

- `OnDeviceImageGenerator` (MediaPipe Image Generator, Stable Diffusion 1.5):
  real AI generation, but the underlying MediaPipe task is no longer actively
  maintained by Google, requires the user to manually convert model weights
  into a directory and enter its path (no in-app download), and takes 15s on a
  flagship device, "well over a minute" on midrange hardware.
- The canvas placeholder in `MediaGenerateTool` (procedural shapes + text):
  what most users actually get today, since the real path needs manual setup.

MEOWMBA is going to be distributed to other people, each with their own phone
and (usually) no home GPU server. The image-generation feature needs a real,
zero-config default that works out of the box for every install, with an
optional accelerator path for users — like the app's own author — who do have
a home GPU server.

## Architecture

Two interchangeable backends behind the existing `MediaGenerateTool` interface:

- **`SDCppImageGenerator`** (new, on-device, default) — replaces
  `OnDeviceImageGenerator` entirely.
- **`RemoteImageGenerator`** (new, opt-in) — calls a user-configured HTTP
  server. Only constructed when the user has entered a server URL in Settings;
  never assumed, never defaulted to a specific host.

The existing canvas placeholder remains the final fallback when neither real
backend is available or both fail, preserving today's "always produce
something" behavior.

**Precedence** is a Settings toggle ("prefer local" / "prefer server"),
defaulting to **prefer local** — the on-device path is always the first path
for a fresh install; the server is a deliberate opt-in a user reaches for once
they've set one up.

## Components

1. **`SDCppImageGenerator`** — JNI bindings to
   [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp) (the
   diffusion-model sibling of llama.cpp: C++ inference, no Python runtime
   needed on-device). Loads a quantized GGUF-style model from a directory
   managed by the model-download system. Structured the same way as the
   existing local LLM providers (`LiteRtProvider`, `MediaPipeProvider`) so it
   fits the app's established provider pattern.

   Model choice: a fast distilled model, either SDXL-Lightning or
   Z-Image-Turbo (2026, ~8-step). Both are proven on Android today via the
   open-source "Off Grid" app (MIT-licensed, ships exactly this stack). Final
   pick deferred to a short implementation-phase spike comparing the two on
   actual target hardware — Off Grid itself ships multiple options, which
   suggests no single obvious winner across device tiers.

2. **Image model catalog + downloader** — extend the existing
   `ModelCatalog.kt` and `ModelDownloader.kt` (already used for on-device LLM
   models) with an image-model entry, rather than building parallel download
   machinery. First call to `media.generate_image` with no model installed
   prompts to download — it asks first, it does not silently pull a
   multi-gigabyte file, especially over cellular.

3. **`RemoteImageGenerator`** — thin HTTP client, `POST <image server
   URL>/generate` with the prompt, gets image bytes back. Only constructed if
   the new Settings URL field is non-empty, mirroring exactly how
   `HermesAgentBrain` is conditionally constructed from `hermesAgentUrl`
   today. This is a **separate** Settings field from `hermesAgentUrl` — the
   Hermes URL is specifically the agent/brain connection; media generation is
   a distinct concern with its own optional server.

4. **`meow-image` service** (server-side, not part of the distributed app) —
   a small FastAPI container matching the existing `meow-embed`/`meow-telem`
   house style on `meow-nb`. Runs FLUX.2 [klein] (Black Forest Labs, ~13GB
   VRAM, 4-step generation). Owns the GPU-swap with the LLM: tells Ollama to
   unload (`keep_alive: 0`), loads the diffusion pipeline, generates, releases
   VRAM back. This is the author's own deployment; nothing about it is baked
   into the distributed app beyond "call whatever URL the user configured."

5. **Settings screen additions** — one new text field (image server URL, same
   pattern as the existing Hermes URL field) and one new toggle (prefer local
   / prefer server, default local).

## Data flow

1. User asks the companion to draw something → agent tool-call routes to
   `media.generate_image`.
2. `MediaGenerateTool` picks a primary backend: server (if URL configured
   *and* "prefer server" is set) or on-device (default).
3. If the on-device model isn't downloaded yet, prompt to download (first use
   only; cached after).
4. Whichever backend runs, the result comes back as image bytes, saved into
   the workspace exactly as today, returning the same `MEDIA_FILE:<path>`
   marker the chat UI already renders. **Nothing downstream of generation
   changes.**
5. If the primary backend fails, fall back to the other configured backend
   automatically; if both are unavailable, fall back to the canvas
   placeholder — the tool always produces something.

## Error handling

- **Local model not installed, first use**: prompt to download rather than
  silently generating the canvas placeholder — the user should know why their
  first image looks like a procedural drawing instead of assuming that's the
  real feature.
- **Server configured but unreachable**: clear, honest failure message
  (matching the existing "on-device generation failed: X, check Settings"
  precedent), then fall back per the rule above.
- **stable-diffusion.cpp native failure** (OOM, corrupt model, unsupported
  device): caught as `Throwable`, not `Exception` — the same defensive
  pattern `OnDeviceImageGenerator` already uses for native/JNI errors, since a
  bad model load must not crash the app mid-conversation.

## Testing

- Backend routing/precedence logic (which generator is tried in which order,
  given URL-configured / preference / availability combinations) is unit
  tested with fake generators — no real model or network needed, following
  the style already established in `WakeWordErrorPolicyTest.kt` and
  `CompanionViewModelTest.kt`.
- `meow-image` gets its own small Python test suite for the GPU-swap
  orchestration (mocked Ollama calls) — separate from the Android test suite
  since it's a separate deployable, not part of the app repo.

## Explicitly out of scope

- Music and video generation — separate features, separate specs.
- img2img, inpainting, ControlNet, LoRAs — not requested; if wanted later,
  ComfyUI becomes a more attractive option for the server side than it is
  today (see approaches considered below).
- Bundling any image model inside the APK — auto-download-on-first-use was
  chosen instead, to keep install size down for users who never touch this
  feature.

## Approaches considered and rejected

- **ComfyUI or Automatic1111 for the server side**: more flexible long-term,
  but neither has any awareness of Ollama's VRAM lifecycle, so the GPU-swap
  orchestration would still need to be written as glue code in front of
  either — adding a heavier dependency without removing any custom work. A
  minimal FastAPI service was chosen instead, matching the existing
  `meow-embed`-style house pattern.
- **Ollama itself for image generation**: ruled out — Ollama is built for LLM
  serving and doesn't have a reliable diffusion-image-output path.
- **Keeping the manual-install MediaPipe SD1.5 path as the on-device
  default**: rejected because it fails the "zero-config first path" goal —
  most distributed users would never do the manual weight-conversion step and
  would be stuck on the canvas placeholder indefinitely.
