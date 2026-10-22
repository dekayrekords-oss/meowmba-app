# LLM settings consolidation — 1.3.44 / code48

## Implemented

- Home Hardware tile and page title are now **LLM**; related app guidance uses
  the same name. The legacy Hardware click alias is retained internally.
- OpenRouter and OpenAI each have a settings expander inside their model
  card. Card-header taps still select a configured provider; unconfigured
  cards open setup fields. Editing fields does not trigger header selection.
- Provider fields include masked key, clear key-page button/instructions,
  chat model, optional advanced endpoint, explicit cloud-use acknowledgment,
  and **Save & use**. The save commits configuration/default atomically and
  reloads the active provider. It explicitly says the connection is not tested.
- A separate **Vision** card controls image-understanding models, distinct
  from image generation. The OpenRouter list filters image-capable models;
  OpenAI has a dedicated vision-model field. Existing values are reused.
- Public OpenRouter catalogs load only when those settings are opened. No
  secret is sent for catalog loading. A custom model ID can be entered even
  when the catalog cannot load. Saving performs no paid inference or photo
  upload, and no automatic paid fallback is introduced.
- Vision settings save atomically without changing chat keys/models/default.
  Camera/browser provider construction reads the dedicated OpenAI model and
  matching provider endpoints. OmniRoute with an OpenAI default uses OpenAI
  for camera vision. Existing explicitly configured server-vision priority
  and browser routing order otherwise remain unchanged.
- Removed the duplicate OpenRouter/OpenAI/vision controls and persistence
  from general Settings to prevent stale writes. Settings → AI & models has
  an **Open LLM settings** shortcut. Other agent/local/BigPickle/Hugging Face
  controls remain where they were. A header Back button exits LLM without
  scrolling to the bottom.
- No migration or data clearing. All previous onboarding/skip, avatar and
  tester-recovery changes are retained. Paused Blender integration untouched.

## Verification

Full `:app:testDebugUnitTest` and `:app:assembleDebug` passed: **1,290 tests,
1,285 passed, five conditional skips, zero failures/errors**. Ten new tests
cover lazy card settings, key masking/no implicit writes, explicit provider
save, custom model ID without catalog, vision filtering/isolated save, key
page intent, failed-save retry and actual vision provider configuration.
Updated the old Settings regression to assert the fields moved and cannot
overwrite the new card choices. Initial test-only Mockito matcher failures
were corrected; the final full suite is green.

Inspected `app/build/reports/llm-openrouter-settings.png` and
`llm-vision-settings.png` renders. Test data and mocked storage were used;
this is not a real-account or Android Keystore round-trip acceptance test.

Build used the unchanged debug native bridge cache at
`/tmp/meow-issue-native.QdqMRB` and existing voice/ONNX assets. Not a clean
native rebuild or a Play-package release. `git diff --check` passed.

## Artifact and remaining acceptance

- Package: `com.example.meowmba`, preview version `1.3.44`, code `48`.
- APK: `/Users/prog/Meowmba-Releases/meowmba-1.3.44-code48-preview.apk`.
- Source/archive SHA-256:
  `44f96470a5e2857268645b0342c5dea8a397a5db964b4407597a213feb3da7cd`.
- `apksigner verify` passed using the existing debug certificate, SHA-256
  `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.

Meow fetch timed out. No remote merge/push, phone installation, credentials
access, cloud inference, social action or publication was performed. Obtain
separate installation approval for real-phone save/relaunch and normal UI
acceptance; real-account inference/vision checks require the user's cloud
consent and must not silently use a paid model. The previous uncommitted
increments are intentionally preserved in this local preview.
