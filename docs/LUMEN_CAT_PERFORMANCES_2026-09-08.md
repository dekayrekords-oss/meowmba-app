# Lumen Cat and original performance library

Local candidate: **1.3.46 / versionCode 50**. Adds to the approved Lumen design;
does not replace existing avatars, change the default, include the paused Blender
model, enable a camera, install an APK, or publish a release.

## Implemented

- **Avatar → Lumen Cat:** the existing glass-eye rig plus floating ears, a small
  sculpted nose, curved mouth and damped whiskers. Speech energy drives the mouth.
  Shares attention, variable blinks, secondary motion and active-clock lifecycle
  with Lumen, not an additional competing gaze/idle loop.
- Independent eye, glow and feature colors use the existing encrypted preference
  keys. Lumen also exposes ear color. Dark, light and system appearance remain.
  Swatches have distinct screen-reader labels for their color channel.
- **39 original performances** in ten categories. Each has anticipation,
  action, a readable hold and neutral recovery, with bounded 1.8–6 second clips.
  They are new choreography, not a claim to reproduce another robot's library.
- Native vector hearts, tears, stars, sparkles, questioning, sleep/music,
  bubbles and weather accents. Real unilateral wink and soft peek/hide channels
  supplement the older shared-eye keyframe schema. Privacy marks never fade out
  with a hiding face. No external animation, sprite download or inference loop.
- Search all animations by title/alias; category filters; twelve initial rows
  and an explicit expand control. **Surprise me** chooses playful performances
  while avoiding the last three IDs and last two families where alternatives exist.
- Six gentle, unaccented idle performances have a separate no-repeat history.
  They never close a panel and yield to conversation or an opened panel. Dramatic
  weather/costume/celebration scenes are explicitly selected, not ambient events.
- Named voice commands go through the existing avatar tool/audit path. For
  example, “play Snow Wonder animation,” “show me another trick,” or “act like a
  sloth.” Casual weather/pet mentions do not become new deterministic commands.
- Existing saved animations take precedence over new aliases. Custom-authored
  clips only receive catalog props if the complete spec actually matches the
  catalog; naming arbitrary frames “hide” cannot unexpectedly hide the face.

## Reference evidence and the limit of “every animation”

The official [DeskMate product page](https://keyirobot.com/en-us/products/deskmate)
describes real-time generated motion, not a fixed preset animation library.
No exhaustive public expression manifest, trigger matrix or reusable animation
pack was found. DeskMate is the phone-based desk dock; the wheeled Loona Petbot
is a different product. Their capabilities must not be conflated.

Four promotional clips embedded on the official DeskMate page were captured as
contact sheets and visually inspected for behavior references:

| Official capture | What was actually visible |
| --- | --- |
| [Day 1](https://cdn.shopify.com/videos/c/o/v/f56fe581f7be40cf9e7e9fe8b952ff7a.mp4) | Closed-line and open amber-eye states with a tilted dock. |
| [Week 1](https://cdn.shopify.com/videos/c/o/v/d6443da918534a0682ab1f1717484a5a.mp4) | Conversational eye posture and small head roll during a design discussion. |
| [Week 3](https://cdn.shopify.com/videos/c/o/v/723014a0bfe0413585d63461a9d1a2ef.mp4) | Friendly/happy eye posture and slight tilt within a proactive-assistance promo. |
| [Month 3](https://cdn.shopify.com/videos/c/o/v/a72daaaceaec456ab522ce2bd1307d60.mp4) | Welcome/attention turn from the side toward the person, followed by roll. |

These establish visible promotional motions, **not independently verified runtime
triggers or complete choreography**. Reference-only contact sheets remain under
`/tmp/meow-deskmate-refs.aNMTbL`; no manufacturer imagery is packaged in the app.

The [official Petbot support command screenshots](https://supportcenter.keyirobot.com/#/detailnew/doc/faq/https:%2F%2Ff4x6dn8llc.feishu.cn%2Fdocx%2FYzm5do7FKo8aIPxhyojcJx8cnDh)
document snow, wind, fire, lightning, cat/sloth acting, talent show, high
temperature and a comic puff command. These are **command-label evidence**, not
captures of their complete animation. Original equivalents are Snow Wonder,
Breezy Whiskers, Warm Embers, Storm Startle, the existing cat performance, Sloth
Stretch, the variety selector, Warm Day and Cheeky Puff. The owner's app contains
a larger changing command list that was not accessed. No claim of completeness.

Physical following, travel and docking are not fabricated as desk-avatar tricks.
Existing paired-base safety gates remain unchanged: unverified sensor data stops
motion and translation remains disabled. Nothing here authorizes physical motion.

## Review and validation

- Bounded catalog author: `body_safety`; only the catalog and its test file.
- Independent official-source and integration reviewer: `attention_review`.
- Main: renderer/rig, state/ViewModel, UI, tools, integration/render tests,
  build, evidence and release staging. One Gradle owner throughout.
- Isolated OpenCode `opencode/big-pickle` reviewed the bounded design risks with
  all tools denied. This CLI response is not evidence of local-GPU inference.
- Review found and corrected saved-alias shadowing, custom-name prop collisions,
  and expression ranges flattening at the rig clamp. Family-based lid attitude
  preserves sadness/annoyance even when authored titles are not emotion words.
- Tests cover catalog bounds/aliases/neutral recovery; UI search and colors;
  saved-name and custom-frame compatibility; preference recreation; interruption;
  native eye/ear/muzzle/prop containment on portrait, square, cover and landscape;
  and privacy visibility during hiding. Render previews use scripted gaze inputs,
  the production rig, keyframe sampler and painter—not a live-camera recording.

Build command:

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --max-workers=4 -PprebuiltDebugNativeDir=/tmp/meow-issue-native.QdqMRB
```

Visual outputs: `app/build/reports/lumen-cat/`. Set
`MEOW_CAT_RENDER_FRAMES=1` and run `LumenCatRenderTest` to export the optional
motion sequence. Do not interpret host render rate as phone frame rate.

Final full suite: **1,389 tests, 1,384 passed, five skipped, zero failures/errors**;
successful debug assembly. APK verification:

- Package `com.example.meowmba`; versionCode 50, versionName 1.3.46; minSdk 31,
  targetSdk 36. This is the debug-preview package, not a Play upload artifact.
- APK SHA-256: `e6c43855749f110f36de29fd8b8e9a89a0006f31332bffc0719345c19152d24f`.
- Signing certificate SHA-256:
  `75b19d5a083d337f430a37ac77269f1eb35d7039057d7e9b0263924008c21d7c`.
- Archive: `/Users/prog/Meowmba-Releases/lumen-cat-1.3.46-code50/`; full-suite
  XML evidence is preserved in `full-test-results/` before optional render-only
  runs replace Gradle's current report. Includes six colorways and captures of
  all 39 original performances. The motion demo is a host-native renderer export.
  Final demo decode verified all 872 frames at 960×720, 30 fps (29.07 seconds).
  Export-only render test passed; storyboard and six-colorway contact sheet were
  visually reviewed. Motion SHA-256:
  `a2b67b41e1d252092e00b1129deb5c00fedbf3c82528842a03537bcb372e489b`.

## Remaining acceptance gates

- User visual approval of Lumen Cat and its original performance direction.
- Actual phone fold/resume, sustained thermals/frame timing, live attention and
  speech behavior. Unit/native-host tests do not establish phone smoothness.
- Actual base sensor decoding/safety commissioning; do not bypass its stop gate.
- Remote synchronization: bounded `git fetch --all` failed because Meow at
  192.168.1.69:22 timed out. Candidate is based on the preserved local dirty tree;
  cannot claim comparison with the latest remote state.
- Installation, signing for distribution, upload and publication require the
  separate authorized workflow. None was performed for this request.
