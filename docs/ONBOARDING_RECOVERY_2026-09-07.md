# Onboarding recovery — 1.3.42 / code46

## Follow-up: clear Skip setup — 1.3.43 / code47

At the user's request, setup now has an outlined **Skip setup** button pinned
above the scrollable content on every step. It works without a key, model or
permission grant. Supporting text points to **Settings → Start setup** for
later configuration. It uses the existing persisted wizard-dismissal path;
skipping does not manufacture a verified AI connection. Previously saved
configuration is kept. The ordinary Continue action still requires a verified
brain on step one; skipping is the explicit exception requested by the user.

Added regression checks for visibility after scrolling with Continue disabled
and availability through all five steps. Full suite: 1,280 tests, 1,275 passed,
five skipped, zero failures/errors; debug assembly passed. Inspected updated
light onboarding and `onboarding-skip-scrolled.png` renders. Signed preview:
`/Users/prog/Meowmba-Releases/meowmba-1.3.43-code47-preview.apk`, SHA-256
`56f85180d8edb5ce310ab54645a49ebc34950b4021caa89f62790d513f0aac41`;
source/archive hashes match and `apksigner verify` passed. Remote fetch timed
out; no installation or publication. Code46 details below remain historical
evidence; code47 is the latest local preview.

## Status and scope

Implemented in `/Users/prog/AndroidStudioProjects/Meowmba`, branch
`feat/image-services-settings`. Includes the earlier uncommitted code45 tester
issue fixes; they were preserved. No phone installation, data clearing, remote
push, Play upload, social post, new cloud account or paid inference was done.
The user's paused Blender/3D incorporation remains untouched.

## Causes found

The old Verify & Save button required a manually loaded model catalog and an
explicit selection even after a valid key was pasted. Authentication/key
persistence was coupled to two strict planning probes, so model congestion or
poor planning output made key setup appear broken. Download-completion UI
depended on temporary screen/service state instead of recovering actual files.
The first-run wizard could also overlay a still-running avatar animation.

## Changes

1. A prominent **Get an OpenRouter API key** button opens the official
   [keys page](https://openrouter.ai/settings/keys). Three short steps explain
   account sign-in, Create Key, copying the full secret and returning to Meow.
   A top shortcut brings the cloud section into view. Key input is masked.
2. After explicit cloud consent, a nonblank key enables Save immediately; no
   catalog selection is required. Authentication uses the documented
   [GET /api/v1/key](https://openrouter.ai/docs/api/api-reference/api-keys/get-current-api-key).
   A verified key and the OpenRouter default are committed together to
   encrypted preferences before any inference test. A failed replacement
   retains the previous key; a model/catalog failure retains the new saved key.
3. Automatic setup tries at most three free tool-capable models. A paid model
   needs explicit selection; a previously verified paid choice is preserved.
   There is no automatic paid fallback. One real nonempty reply is required
   before Continue. Advanced navigation/context checks remain available as a
   separate optional test, with honest failure wording.
4. A lifecycle ViewModel holds transient input through rotation. Encrypted
   storage and on-disk metadata recover saved setup after process death. Local
   download intent is persisted, completion triggers a rescan/real reply test,
   and completed bytes are distinguished from a verified working model.
5. Initial/resume scans inspect bounded Meow-accessible folders and the
   configured path. Existing compatible nonempty chat bundles are reused.
   Needle routing weights, image/voice weights and unfinished `.part` files
   are excluded. Android's Files picker enables explicit import of a `.task`
   or `.litertlm` file from other accessible locations. This is not a claim
   to silently read every file or another app's private storage.
6. Download starts reserve active status immediately; incomplete/empty or
   HTML responses are not finalized as model files. Completed local files are
   retained when verification fails. A late optional local test cannot
   overwrite a newer OpenRouter selection.
7. RAM, CPU count, arm64 support and free storage recommend Gemma4 E2B,
   Qwen 1.5B, Qwen 0.5B or cloud/no new local download. These are conservative
   capability heuristics, not a measured GPU/thermal benchmark. First scan
   selects Classic on low-end devices only when no explicit avatar choice
   exists. Existing light/dark themes and avatar choices are preserved.
8. The avatar pauses behind first-run setup. Completing setup reloads the
   selected provider and avatar. Hardware uses full clickable model/provider
   cards, clear setup/select actions and accurate OpenRouter network status.

## Verification

- Full `:app:testDebugUnitTest`: **1,278 tests; 1,273 passed, five conditional
  skips; zero failures/errors**. The final rerun included the cloud render.
- Seventeen added controller/UI regressions cover save enablement without
  catalog selection, invalid key/storage failure, retained keys and retries,
  no paid fallback, prior paid-model preservation, process reconstruction,
  existing models, pending completion, local/cloud selection races, consent,
  device recommendations, official key-button intent and clickable tiles.
- Robolectric render inspection: `app/build/reports/onboarding-light.png` and
  `onboarding-openrouter.png`. The displayed RAM/storage in those snapshots
  belongs to the test environment, not a physical phone.
- `:app:assembleDebug` passed. Used unchanged prebuilt arm64 Needle/SD bridges
  from `/tmp/meow-issue-native.QdqMRB`, with existing voice assets/ONNX JNI.
  This was not a clean native toolchain or release signing build.
- Read-only connected-phone inventory saw SM_F966U1, about 10.85 GiB RAM,
  existing Gemma4/Qwen chat files and separate Needle/image weights. No local
  model was deleted or downloaded during these checks.
- Public OpenRouter catalog responded; no private key or actual account
  inference was used. Fake backend tests do not prove Android Keystore
  persistence or account/provider availability on a tester's device.
- `git diff --check` passed.

## Build artifact

- Package: `com.example.meowmba` (preview, not Play package).
- Version: `1.3.42`, code `46`.
- Archive: `/Users/prog/Meowmba-Releases/meowmba-1.3.42-code46-preview.apk`.
- Source and archived APK SHA-256:
  `79f53b12ded1c1ee9f3da827a6fbbec68c6da927c527cffd5a2d29485e780957`.
- `apksigner verify` passed; existing debug certificate SHA-256:
  `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.

## Required next acceptance

Obtain separate install approval, then upgrade the connected preview without
clearing its data. Through the normal UI, verify saved-key reuse and readiness,
reopen/relaunch persistence, existing model detection, actual local reply,
cloud-default precedence while local work completes, full-tile selection and
arrival at the main screen. Fresh-install/process-death and genuinely low-end
device testing should use an approved test device/profile rather than clearing
the owner's app. Real cloud tests need explicit account consent; do not
silently switch to paid models. Tester rollout still requires a separately
prepared and signed Play-package release.
