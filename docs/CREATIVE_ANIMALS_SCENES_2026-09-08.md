# Creative animals and story scenes — 1.3.57 (61)

## Added

Three persistent Lumen forms use the existing eyes, gaze, blink, speech, color controls and morph springs:

- Australian shepherd: original blue-merle patches, white blaze and ruff, tan eyebrows and folded ears. Names include Australian shepherd, Australian shepherd dog and Aussie.
- Owl: feathered eye discs, ear tufts and a hooked beak.
- Duck: rounded feather crown and a broad orange bill.

Five new native scenes bring the catalog to 52 performances and 13 scenes:

| Scene | Acting and props | Example command |
| --- | --- | --- |
| Mouse Chase | Scurry, direction changes, crouch, whole-avatar pursuit and playful pounce; mouse escapes | Chase a mouse |
| Book Reading | Open book, eyes scan lines, page curls across the spine, relaxed finish | Read a book |
| Cowboy Hat | Leather hat, turquoise band, buckle and hat-tip greeting | Wear a cowboy hat |
| Wizard Hat | Indigo hat, stars and a curious upward glance | Wear a wizard hat |
| Painter Beret | Red beret, palette, brush and thoughtful artist pose | Wear a beret |

Choose a form under Avatar → Lumen → Become an animal, or say “become an Australian shepherd”, “be an owl” or “be a duck”. “Go back to normal” returns to eyes only. New stories and hats preserve the selected form. Each is available in the animation picker and through exact authored commands; filenames, negations and appended actions are not accepted as a single scene request.

Random idle scenes use the existing shuffled rotation. All five additions enter that rotation, interleaved with quiet gestures and scene cooldowns. The three hats share a wardrobe family, so another scene family is chosen between hats whenever one remains in the bag. Scene settings, reduced motion, speech/work/panel interruption and drive eligibility continue to apply.

## Geometry and behavior

Original native paths provide the anatomy and props; no downloaded character art, additional rendering engine or animation clock was added. Species channels morph continuously. Mouse pursuit feeds the same body springs and safe stage mapping as normal movement. Hats follow head roll in the same coordinate space as the eyes. Book and prop targets share the animation timeline with gaze.

A dedicated high-idle → hat regression reproduced crown clipping in wide landscape at 0.35 seconds. A spring-controlled headwear allowance now reserves space in the common stage fit and root calculation, keeping avatar, props and touch targets aligned. Fixed privacy indicators are drawn after props. Native review covered dark/light appearances, form differences, eye visibility, strong drive poses, multiple viewport shapes, prop timelines and raised-position handoff.

## Verification and delivery

Full `:app:testDebugUnitTest :app:assembleDebug` succeeded: **1,792 tests, 1,787 passed, 5 skipped, zero failures/errors**. The skipped group is the existing MetaGlassesUnavailableTest group. Exact command and XML are archived. `git diff --check` passed.

APK: `meowmba-1.3.57-debug.apk`, package `com.example.meowmba`, code 61, 427,850,739 bytes. SHA-256: `0c85d00dbcda54d03179dca0809de3df22a1cfab8c120e468fa70c9e9beee3a6`. APK signature verifies against certificate `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`. Sherpa/ONNX native libraries, speech model tokens, VAD and speaker assets are present.

Artifacts: `/Users/prog/Meowmba-Releases/animal-scenes-2026-09-08`. Includes APK, before/after source snapshots, incremental patch, logs, XML, verification JSON, native contact sheets and `native-preview.gif`. The preview is a scripted 24 fps render from production animation code, not a phone recording or a measured device frame-rate claim.

This candidate includes the prior 1.3.56 voice/project/drive fixes. All preexisting working-tree changes were preserved. The LAN Git server remained unreachable on fetch, so remote currency is unverified. The connected Fold was read-only checked at 1.3.55/code59; no installation, microphone activation, physical robot operation, data clearing, push or publication occurred. Installation and device acceptance remain separate gates in AGENTS.md and the active plan.
