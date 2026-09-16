# Natural attention, local model library and ASR quality

Build: **1.3.51 / code 55**, debug package `com.example.meowmba`.
Date: September 8, 2026.

## Delivered

- Reviewed the original 49-second reference video again, including a 4fps
  sequence of the eye motion. Implemented an original native design; no
  manufacturer graphics, video textures or Blender assets are incorporated.
- Lumen now has a seeded score of glances, rests, vertical curiosity and
  recoveries. The whole pair drifts within its available stage space, separate
  from pupil gaze and driving perspective. Repeated neighboring patterns are
  avoided. Head movement follows eye acquisition rather than moving rigidly.
- Larger observed face displacements use faster acquisition; small movements
  retain softer filtering. Camera loss eases home without predicted movement.
  Detector smoothing is time-aware, without increasing camera/inference rate.
- One shared center keeps painted anatomy, props and touch targets aligned.
  Existing camera/mic privacy, thermal pause, lifecycle, driving and temporary
  animal-form boundaries remain enforced. Idle drift yields to live attention,
  speech, driving and explicit scene performances.
- Three original full-scene backgrounds: **Firefly woodland**, **Moonlit lagoon**,
  **Rainy reading loft**. Cached paths/gradients, one Canvas per scene, bounded
  particles and the existing pause-aware clock. Thumbnails are static.
- **LLM → Local** opens a dedicated library with Installed, Recommended,
  Hugging Face and Settings destinations, all visible on narrow screens.
  Browsing does not switch away from the selected AI provider.
- Installed files show real size, selection and deletion confirmation.
  New download/import completion timestamps are recorded; older file
  modification dates are not represented as download dates.
- The phone scan is now actually wired from the LLM screen. It checks app files,
  app-accessible folders and permitted MediaStore Downloads rows. The system
  picker grants access to a selected file in other accessible folders.
  Android does not permit a silent scan of every other app's private storage.
- Imports stage to a temporary file, reject empty/incomplete/over-11-GiB input,
  reject traversal and unsupported extensions, and do not overwrite a model
  that already exists. Import/download actions are separated while one runs.
- Local model path and Hugging Face token settings moved out of Settings.
  Tokens remain masked and encrypted; the new screen has a direct token-page
  button, short license/read-token instructions and explicit Save.
- Model download completion and service errors surface in the library.
  Existing downloaded models are reused instead of redownloaded. Custom
  repository resolution no longer leaves its preliminary progress stuck.

## Hugging Face browsing

Search is explicit, metadata-only, time/size bounded and paginated. It supports
exact repository pages and model-name searches. Files are pinned to immutable
Hub revisions and display exact byte size where published.

Filter/sort options: estimated speed, file size, parameter count, weight
quantization, format, release date, Hub creation/update dates, likes, downloads,
downloaded date, installed-only, no-login and phone eligibility.

Compatibility checks use ARM64/low-RAM status, total/available memory, free
storage, a conservative working-memory allowance and an 11-GiB ceiling.
Chipset-specific NPU, web/Intel, legacy opaque binary, image/embedding, routing,
GGUF and training-weight variants are not offered as eligible generic chat
downloads. A compatible-format candidate is **not a guarantee of successful
inference**. An actual device initialization remains the verification step.

No invented star ratings, benchmark speeds or release dates: likes and download
counts are repository metrics; estimated speed is a relative footprint label;
missing values remain unknown. Base-model release and Hub creation are separate.

Read-only live metadata smoke found generic Qwen3 0.6B/1.7B, Qwen2.5 1.5B Q8
and Gemma4 E2B candidates within a hypothetical 12-GiB total / 6-GiB available /
30-GiB storage profile. Large F32 and chipset-specific variants were excluded.
This was not a phone inference test and downloaded no model weights.

Download security now scopes Hugging Face credentials to its exact HTTPS origin.
Every redirect is a fresh, validated request; signed CDN redirects receive no
Hub authorization header. Custom origins cannot introduce that credential.
Public HTTPS and allowed LAN custom downloads continue to work.

Primary technical references:

- [Hugging Face API metadata](https://huggingface.co/docs/huggingface_hub/package_reference/hf_api)
- [Meaning of Hub downloads](https://huggingface.co/docs/hub/models-download-stats)
- [LiteRT-LM Android](https://ai.google.dev/edge/litert-lm/android)
- [MediaPipe Android LLM inference](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
- [Device-specific NPU models](https://ai.google.dev/edge/litert/next/litert_lm_npu)

## Speech findings and fixes

The local pipeline uses streaming Zipformer partials followed by batch Whisper
finals when Whisper is selected. The phone's expected primary Zipformer2 and
Whisper files exist and meet their configured size checks. Recent bounded
logcat reads contained no usable ASR comparison; the saved microphone kill was
left enabled throughout inspection. No new recording or cloud speech consent.

Concrete defects corrected:

1. Ordinary “you to” / “you two” could be rewritten into “YouTube.”
2. Whisper finals were subjected to legacy streaming-model word substitutions.
   Finals now get presentation normalization only at the engine/VM boundaries.
3. Every changing live hypothesis appeared immediately. A display-only 240ms
   stable-prefix preview now retracts revised/blank suffixes. It is labeled
   “Hearing (draft)” and never commits a final-capable engine's provisional text.
4. The PCM ring covered 20 seconds while the endpoint could run 30 seconds.
   A bounded 32-second ring now retains the full maximum utterance.
5. A queued final could be paired with a newer utterance's audio. The original
   PCM now crosses the delivery boundary with its own final text.
6. Negative AudioRecord reads now report capture failures rather than spinning.
   Cancellation blocks late decode/callback publication.

Numeric route/rate, PCM size/clipping/silence and decode-duration diagnostics
replace verbatim engine transcript/device-name logging. Models, thread counts,
beam settings and microphone permission/privacy state were not changed.
These are pipeline correctness improvements, **not a measured word-error-rate
claim**. Human reference speech in the intended room/mic configuration is still
needed to assess accents, noise, fast speech and exact recognition accuracy.

## Validation and deployment

Final full-suite rerun and debug assembly **passed: 1,555 tests, 1,550 passed,
five skipped, zero failures/errors**.
Earlier full run: 1,555 tests, one test-harness failure, five skipped. The failure
was the manually advanced Robolectric clock missing a resume baseline; the
test-only transition barrier corrected it and the two pause tests then passed.
Initial UI tests (7), metadata/security, imports, ASR and native motion rendering
passed. Background/native snapshots were visually reviewed in portrait and
landscape. The 30-second preview uses scripted gaze, not live camera evidence.

APK identity, signature and native speech/LLM library presence were checked.
APK SHA-256:
`3dbf3b2134e105b90e42e945e8ff8fd33385966b357f13809c92c993ceddb417`.
Debug signer SHA-256:
`75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.
Archive: `/Users/prog/Meowmba-Releases/natural-library-1.3.51-code55/`
contains the APK, native preview video/contact, three background scenes,
model-library screenshots, test XML and this report.

The connected phone identifies as **Samsung SM-F966U1**, serial RFCY61NJEVM.
The installed package was still 1.3.50/code54 during inspection. Thermal status
was 2 without an override on the latest read; no thermal bypass was used.
Installation of this update was offered asynchronously and remains awaiting
approval. No user data clearing, remote push, Play publication or motor commands.
The configured Meow remote at 192.168.1.69 failed the bounded SSH fetch.

## Ownership

Main: library UI/navigation, shared VM/state/settings, file importing,
download-completion integration, renderer hit geometry, QA, sole Gradle build.
`attention_review`: motion helper/rig, detector smoothing, original background
scenes and tests. `body_safety`: audio/preview/final-PCM fixes and tests, with
read-only import review. `model_catalog`: metadata/search/filter policy,
download credential confinement, live metadata checks and tests. No worker
ran a second Gradle process, changed the microphone or operated the robot base.
