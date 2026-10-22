# Lumen animal forms — September 8, 2026

## Follow-up: reset phrases, 1.3.49/code53

Added “go back to normal”, “stop being an animal”, “return to your original
animation”, named-animal stop phrases and polite/addressed variants. These
select `none`/Eyes only through `avatar.form`, cancel any temporary performance
and persist the choice. Call mode permits the same appearance tool. Avatar UI
and both model prompt contracts explain the commands. Tests cover routing
gates, negation/compound exclusions and reset during a temporary performance.
All 29 focused tests passed; debug assembly and matching signature verified.
Archive: `/Users/prog/Meowmba-Releases/avatar-reset-1.3.49-code53/`.
Installed with `adb install -r` on the connected Samsung (SM-F966U1), preserving
app data. Device package inspection confirmed 1.3.49/code53 and activity launch
returned Status: ok. Actual microphone recognition of these phrases still needs
user/device acceptance; automated checks exercise recognized text and state.

The earlier 1.3.48 build was installed successfully at the user's request;
the following no-install statements describe its original build checkpoint.

## Delivered behavior

One Lumen character now changes persistent anatomy while retaining its eye rig,
speech, attention, expression, color choices and shared Drive Mode reactions.
The first authored forms are **Cat, Dog, Squirrel and Raccoon**, with **Eyes only**
as the reset. These are native Canvas features, not a Blender/3D integration.

- Say “become a squirrel”, “can you be a raccoon”, or “change your form to eyes
  only”. The form remains selected until changed. “Racoon” is an accepted alias.
- Avatar → Lumen → **Become an animal** exposes the same five choices. Selecting
  a form from the legacy Lumen Cat page switches to the shared Lumen style.
- “Act like a dog/cat/squirrel/raccoon” is a temporary performance and returns
  to the selected form. New original squirrel/raccoon performances use the
  existing bounded animation system.
- Eye, glow and animal colors remain independently editable. Light/dark/system
  choices are preserved. No new model, API, camera permission or asset download
  is required to change form.
- Unsupported forms fail honestly when identified as appearance requests; the
  app does not claim to generate arbitrary animals. Ambiguous conversational
  roles (“be a CEO”, “become a doctor”) remain assistant requests, not avatar
  transformations. Casual animal mentions and image/video requests are separate.

## Reusable implementation

`AvatarFormProfile.kt` defines eleven bounded anatomy channels and four cached
profiles: pointed/floppy/round ears, tufts, short/long muzzle, whiskers, cheeks,
eye mask, bushy tail and ringed tail. Critically damped channel springs run from
the existing active animation clock, including interrupted mid-morph changes.
The shared gaze/blink/pupil/speech phase is not restarted on species changes.

`LumenAnimalParts.kt` draws original ear bowls, tuft strands, layered muzzle,
cheeks, dark raccoon markings and compact curled tails. The mask is behind the
eye lenses; privacy indicators stay outside the hideable character layer.
Feature bounds include mixed forms, small cover stages and driving transforms.
Paths are reused. The short-muzzle partial-alpha layer is bounded and used only
during transitions, not as a permanent full-screen layer.

`avatar.form` is registered to the main agent through the existing orchestrator,
audit and validated tool contracts. Its exact species allowlist cannot execute
model-supplied code, shaders or URLs. Deterministic routing covers explicit
common requests; both model-tool prompt contracts describe persistent versus
temporary behavior. Visual-only results do not open an artifact browser or
fabricate a file. Form selection cancels old performance/hide jobs before an
atomic style-and-form preference edit.

To add another animal, author an enum/alias and bounded profile, reuse existing
anatomy where appropriate, and add an original painter part only if needed.
Then verify neutral/transition silhouettes, interruption, colors, gaze/privacy,
voice routing and actual device performance. This is an extensible authored
framework, not unlimited runtime animal synthesis or species recognition.

## Worker ownership and review

- Main: profiles, rig, anatomy painter, shared renderer/UI/ViewModel/storage,
  routing integration, regressions, native visual review and the only Gradle
  build owner.
- `body_safety`: bounded form parser/tool and 14 unit tests. Main refined the
  parser to exclude generic roles and added polite request wrappers.
- `attention_review`: independent read-only review. Closed findings included
  direct-question preemption, generic-role hijacking, tool registry/prompt
  completeness and old-performance cancellation. Final review found no blocker.
- OpenCode `opencode/big-pickle`: one isolated read-only design-risk review with
  all tools denied, session `ses_f7ff7a876ffeA0p4laR1jjEUoO`. No paid fallback or
  claim that a locally installed CLI used local GPUs.

## Validation

Final version: **1.3.48 / versionCode 52**, debug package `com.example.meowmba`.
After the last raccoon mask polish, the full suite and debug assembly completed
successfully in 1m16s: **1,453 tests, 1,448 passed, five skipped, zero failures
and errors**. `git diff --check` passed. Build command:

```sh
MEOW_MORPH_RENDER_FRAMES=1 \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  --no-configuration-cache --max-workers=4 \
  -PprebuiltDebugNativeDir=/tmp/meow-issue-native.QdqMRB
```

`aapt2` verified package/version; `apksigner verify --print-certs` succeeded with
the configured Android debug certificate. The initial signature command lacked
JAVA_HOME; rerunning with the bundled JDK above succeeded.

- APK SHA-256: `bdff972c07c06de8a0c6edc2e21a7fa48aeeef1f3578da93d1aa24749a9cf6b6`
- Certificate SHA-256: `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`
- Archive: `/Users/prog/Meowmba-Releases/avatar-morph-1.3.48-code52/`
- APK: `meowmba-1.3.48-code52-debug.apk`
- Preview: `animal-morphs.mp4` (H.264, 960×720, 30fps, 420 decoded frames, 14s)
- Native visuals: `forms-contact.png`, individual form PNGs and recolored light
  variants; test XML archived under `test-results/`.

The final contact sheet, light raccoon and a mid-transition/speech frame were
visually inspected after re-rendering. No manufacturer media or copied assets
are included. Preview encoding and full decode both completed successfully.

Tests added for exact shared eye/speech phase, smooth rapid retargeting,
active-clock pause, role/media/negation routing, tool availability/cancellation,
legacy selection, mocked preference recreation, temporary-form return,
light/dark color controls, mixed-morph portrait/square/cover/wide containment,
and privacy during hiding. Existing driving render checks include new forms.

The 14-second native preview uses the production rig and painter with scripted
gaze/speech inputs: eyes → cat → dog → squirrel → raccoon → eyes. It is not live
phone/camera footage or evidence of on-device frame rate. The contact sheet is
also generated directly from the production renderer, not a concept image.

## Remaining acceptance and authority boundaries

- Inspect transformations and colors on the actual phone, including fold/unfold,
  keyboard/chat, pause/resume, TalkBack and low-cost/reduced-motion behavior.
- Verify Android preference persistence after force-stop/relaunch; JVM tests
  cover the write contract and mocked recreation, not physical Keystore storage.
- Exercise speech aliases with real microphone transcription and optional model
  routing; host tests do not establish every ASR wording works.
- Measure frame pacing, allocations and thermal behavior under matched phone
  conditions. Native screenshot tests are not performance measurements.
- Check driving appearance from the intended mounting orientation while safely
  stationary/simulating inputs. No real road test or physical base motion was
  performed. Existing base safety gates remain authoritative.

Meow fetch was attempted before building and timed out at 192.168.1.69:22.
Remote currency is unverified. Existing dirty work was preserved. This increment
does not install, clear app data, push, sign a Play release, publish, or enable
physical movement. Debug package `com.example.meowmba` remains distinct from
the Play package `com.meow.meowmba`.
