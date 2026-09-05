# Meowmba appearance upgrade — implementation and verification plan

> Latest revision: [Immersive companion plan](IMMERSIVE_COMPANION_PLAN.md), with [agent assignments](AGENT_PLAN.md). That revision replaces the ribbon/core orb direction with background-diffused floating parameters, replaces the fixed-height chat with a retractable composer, and adds a variation-aware eye/scene/prop animation system. Earlier completion notes below are historical evidence, not completion of this new scope.

Prepared September 4, 2026. Scope: the approved floating-cat concept, Professional app theme, Jarvis orb polish, Settings organization, and efficient use of Meow/local models.

## 1. Outcome

Deliver one app with two selectable presentation styles: the existing Companion experience and a new Professional workspace. Both support Light, Dark, and System appearance. Users can choose their avatar independently, customize Pearl's feature/eye/glow colors, and retain those choices after restarting.

The new Pearl avatar is a native animated interpretation of the concept image—not a claim that a static generated render has become an identical film-quality 3D model. Its expressions must follow the real conversation and preserve privacy indicators.

No installation is included until separately authorized. Do not overwrite the phone's working app, clear app data, change its credentials, or silently replace its selected models.

## 2. Current implementation checkpoint

### Implemented in the working tree

- Professional workspace option and persisted Light/Dark/System appearance selection.
- Pearl avatar option, feature-color picker, existing eye/glow pickers, and live preview on the Avatar page.
- Pearl floating ears, dimensional eye shading, curved whiskers, and reuse of the existing gaze, blink, expression, and speech-motion inputs.
- Professional phone/foldable layout connected to existing chat, microphone, projects, connections, notes, setup, and privacy actions. No fabricated meeting or connection data from the concept image.
- Semantic theme colors across the main screens, chat, tool menu, and privacy dashboard.
- Jarvis orbital ribbons, energy filaments, a brighter nucleus, and activity smoothing, implemented by a lighter-model agent.
- Settings groups that collapse by default, implemented with help from a lighter-model agent: Voice & behavior, Phone & device, and Advanced groups for Media & memory and AI providers.
- Guided setup entry at the top of Settings; removed the floating setup button that could cover bottom controls.
- New appearance tests and a native Pearl render test.

### Verification evidence and limitations

- Kotlin compilation and `assembleDebug` completed; an APK was produced at `app/build/outputs/apk/debug/app-debug.apk`.
- Six new appearance tests and the native Pearl render test passed in a targeted run. The generated Pearl preview was visually inspected.
- Four older expression tests assumed the camera was enabled although its default is off. Their fixtures now explicitly enable the camera for those expression checks; privacy-off tests remain separate. **The corrected batch has now had its clean recorded rerun** — see §11.
- Kotlin compilation of the appearance source is verified: `:app:compileDebugKotlin` re-ran clean after the contrast fixes in §12.
- A subsequent full unit-test run exhausted its JVM memory and was stopped. **This still has not been completed.** Do not describe the complete test suite as passing — see §13 for the four attempts and the diagnosis.
- **No device is currently attached** — `adb devices` returns an empty list, so the earlier
  claim that "the phone is connected" does not hold at this checkpoint. `adb` is available
  (`/Users/prog/Library/Android/sdk/platform-tools/adb`, not on `PATH`). Nothing has been
  installed or device-tested, and nothing will be without explicit authorisation (§9 Gate 4).
- The build still reports the pre-existing missing MWDAT GitHub token. This appearance change does not fix or certify Meta glasses support.
- Meow is reachable. Its GPUs were identified as an RTX 3090 and an RTX 3090 Ti. A local Qwen3-VL 8B model was used for a bounded layout review; two lighter hosted agents handled separate code files. This is not a newly installed persistent agent service.

## 3. Phase A — finish the theme foundation

### Work

1. Keep app layout, brightness, avatar style, and avatar colors as distinct preferences.
2. Preserve existing defaults and unknown-key fallbacks so updates do not reset users' choices.
3. Audit remaining hard-coded surfaces and foregrounds, especially selected controls, dialogs, input fields, setup screens, and warning cards.
4. Check status/navigation bar icon contrast in both brightness modes.
5. Keep user backgrounds available in Companion mode. Explain that Professional mode uses its workspace background.
6. Ensure every screen offers an obvious route back and that changing appearance does not reset a conversation, download, or connection.

### Acceptance

- Light, Dark, and System work with both app themes.
- Restart preserves theme, avatar, and all color selections.
- Text and controls remain readable; target 4.5:1 contrast for normal text and 3:1 for large text and essential control boundaries.
- Existing Companion, Godot, Classic, and Jarvis options remain selectable.

## 4. Phase B — finish Pearl and the Avatar page

### Visual and motion work

- Preserve separated ears, eyes, nose, mouth, and whiskers over a soft halo; no solid head or body is required.
- Give eyes priority: independently shaped lids, gaze direction, pupil response, highlights, and controlled iris contrast.
- Keep iris size stable during blinking; lids should cover the eye rather than scale the iris down.
- Check listening, curiosity, delight, concern, thinking, speaking, interruption, sleep, and offline transitions.
- Use subtle breathing, gaze shifts, ear follow-through, and whisker motion without constant exaggerated movement.
- Keep mouth motion tied to real speech activity and preserve mute behavior.

### Controls

- Avatar page contains App theme, Light/Dark/System, live preview, face style, feature color, eye color, glow color, and animation examples.
- Swatches have names, selection feedback, usable touch targets, and horizontal scrolling on narrow screens.
- Preview interactions must not toggle the microphone/camera or navigate away accidentally.
- Optional arbitrary hex-color entry can follow preset validation; it is not required for the initial release.

### Acceptance

- Emotions remain distinguishable without labels.
- Feature, eye, and glow colors change independently and persist.
- No clipping on folded, unfolded, portrait, landscape, or enlarged-text layouts.
- Privacy state remains truthful regardless of expression or custom animation.

## 5. Phase C — polish the Jarvis orb

### Work

1. Review orbital ribbons and front/back particle depth as one coherent sphere.
2. Tune core glow and filament intensity so the orb is impressive without washing out its structure.
3. Verify the idle, listening, thinking, speaking, and offline appearances against actual app state.
4. Prevent noise-driven flicker with smoothed activity energy.
5. Respect pause/reduced-motion behavior and avoid hidden animation work behind full-screen panels.
6. Profile the new drawing work before raising particle counts or adding more effects.

### Acceptance

- Clear visual improvement over the previous orb in side-by-side captures.
- Smooth frame pacing on the target phone; target approximately 16.7 ms per frame at 60 Hz, validated rather than assumed.
- No major memory growth, overheating, or sustained idle GPU load in a device soak test.
- Privacy text and captions stay readable above the effects.

## 6. Phase D — complete the Professional workspace

### Work

- Phone: concise heading, compact live companion, conversation entry, microphone, real projects, connection access, and privacy controls.
- Unfolded display: navigation rail and wider work area, preserving space for the existing split browser.
- Use existing providers and actions; do not add decorative but nonfunctional meeting cards or claim disconnected services are connected.
- Keep generated media, errors, voice-model download status, and approval dialogs visible above the workspace.
- Wrap controls on narrow cover screens and check large fonts, keyboard appearance, rotation, and TalkBack focus order.

### Acceptance

- Chat, voice, projects, connections, notes, settings, and all tools are reachable.
- Risky actions still require the existing approval flow; theming does not change authorization.
- Nothing from the old screen is accidentally hidden or running twice behind the new layout.

## 7. Phase E — finish Settings organization

### Work

1. Keep guided setup prominent and general settings collapsed by default.
2. Keep Voice & behavior and Phone & device understandable without developer terminology.
3. Keep provider credentials/endpoints and media/server configuration in clearly named expandable groups.
4. Keep Save and Close visible and unobstructed; distinguish immediate settings from values saved on confirmation.
5. Preserve all existing fields, validation, key masking, connection actions, and save callbacks.
6. Verify that collapsing a group cannot discard unsaved input.
7. Show robot-base configuration only under the existing actual-attachment rules; do not restore old branding.
8. If navigation is still too cumbersome after testing, add search that reveals the matching section rather than duplicating controls.

### Acceptance

- Initial Settings view is short and understandable.
- A new user can find setup, voice, providers, and device controls without scrolling through every field.
- All previous settings remain available and save correctly.
- No credentials appear in logs, screenshots, agent prompts, or saved plans.

## 8. Agent and Meow resource plan

### Ownership

- Main integrator: shared state, preference wiring, theme integration, privacy review, builds, and final acceptance.
- Lighter Settings agent: Settings file only, with a bounded task and concise completion report.
- Lighter orb agent: particle-rendering file and isolated tests only.
- Meow local reviewer: small code-review or visual-review tasks with explicit output limits; findings must be checked before changing code.

### Cost and load controls

- Prefer the already-installed 8B model for narrow reviews. Use the larger local model only when a task demonstrably needs it.
- Send only relevant source excerpts and short summaries, not entire conversation histories or repositories.
- Avoid concurrent editing of the same files and duplicate Gradle builds.
- Check GPU availability before loading another model; do not evict the user's active workloads.
- Use bounded context, bounded output, and short keep-alive periods. Avoid repeated polling and repeated full-file rereads.
- Use both GPUs only when capacity/concurrency warrants it; do not force a small task across both cards merely to occupy them.
- Gradle compilation/tests are primarily CPU/RAM work. GPUs help local inference, not JVM test-memory failures. Moving builds to Meow requires checking its JDK, Android SDK, native dependencies, checkout, and signing setup first.
- Never send API keys, SSH secrets, personal messages, or phone database contents to a review model.

## 9. Validation and release gates

### Gate 1 — targeted tests

- Rerun appearance, Pearl rendering, corrected expression/privacy tests, and orb tests.
- Add targeted checks for state transitions and persisted preference restoration where coverage is missing.
- Verify native renders at multiple expressions and color selections.

### Gate 2 — build and full regression

- Run compile and APK assembly after the final edits.
- Diagnose the full-suite JVM memory failure; use appropriately sized test workers and isolated batches rather than rerunning the same failing workload unchanged.
- Record exact passing/failing test counts and distinguish existing failures from regressions.
- Check the final diff for accidental edits to unrelated voice/model/connection work already in the dirty worktree.

### Gate 3 — visual/device QA

- First use non-installing preview/test rendering where possible.
- After installation approval, test light/dark/system, fold states, large fonts, restart persistence, TalkBack, keyboard overlap, real voice reactions, and frame pacing.
- Verify captions, errors, media previews, privacy toggles, and approval dialogs in both layouts.
- Do not treat the concept images or native unit-render preview as proof of end-to-end phone behavior.

### Gate 4 — controlled release

- Confirm the installed version and signing identity before preparing an upgrade.
- Bump the release version only when ready to deliver; preserve app data and the existing signing key.
- Keep a recoverable previous APK and document how to return to Companion mode.
- Install only after explicit authorization; never uninstall or clear data as a shortcut.

## 10. Completion checklist

- [x] Theme and avatar choices implemented in source.
- [x] Pearl color controls and live preview added to Avatar page.
- [x] Initial Jarvis visual upgrade implemented.
- [x] Initial Settings grouping and setup entry implemented.
- [x] Lighter agents and Meow local inference used for bounded work.
- [x] APK assembly completed at this checkpoint.
- [x] **Final targeted test rerun recorded after all edits** — 60/60 pass, 0 failures
      (2026-09-04, forced with `--rerun` and read from the result XML, not the build status).
      See §11.
- [x] **Remaining contrast/layout audit completed** — four real defects found and fixed.
      See §12.
- [ ] **Full regression run completed without resource failure** — BLOCKED by host memory,
      not by code. Four attempts, all recorded. See §13. This is the one gate that must
      still be closed on a machine with free RAM.
- [ ] Device animation/performance/persistence testing completed — `adb` itself works
      (it is at `/Users/prog/Library/Android/sdk/platform-tools/adb`, just not on `PATH`),
      but **no device is attached**, so Gate 3 could not be started. Attach the Fold
      (serial `RFCY61NJEVM`) and authorise the install to close this.
- [ ] User approves installation.
- [ ] Upgrade installed and verified without data loss.

The upgrade is finished only after the relevant verification gates pass. Source implementation, successful assembly, and on-device release are separate milestones.

**Status summary:** appearance source work and its targeted tests are done and verified. The
outstanding items are a full-suite regression run (needs a machine with free RAM) and
device QA (needs `adb` plus explicit install authorisation). Neither has been marked
complete, and the full test suite must not be described as passing.

---

## 11. Targeted test rerun (recorded, 2026-09-04)

Run with `./gradlew :app:testDebugUnitTest --offline --rerun` scoped to the six suites this
plan is about. `--rerun` was used deliberately: the first attempt reported
`BUILD SUCCESSFUL` with `testDebugUnitTest UP-TO-DATE`, which is stale state and is *not*
evidence. Counts below are read from `app/build/test-results/testDebugUnitTest/*.xml`.

| Suite | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `AppearanceTest` | 6 | 0 | 0 | 0 |
| `AvatarStateMachineTest` | 12 | 0 | 0 | 0 |
| `CatExpressionCatalogTest` | 12 | 0 | 0 | 0 |
| `CustomAnimationTest` | 23 | 0 | 0 | 0 |
| `ParticleOrbContentTest` | 6 | 0 | 0 | 0 |
| `PearlRenderTest` | 1 | 0 | 0 | 0 |
| **Total** | **60** | **0** | **0** | **0** |

This closes Gate 1 for the appearance, Pearl render, expression/privacy, and orb suites,
including the corrected camera-default expression fixtures.

---

## 12. Contrast / hard-coded colour audit — findings and fixes

Audited `ui/` for hard-coded surfaces and foregrounds, focusing on the categories Phase A
item 3 calls out: selected controls, dialogs, input fields, setup screens, and warning
cards. No hard-coded colours were found on input fields or dialogs. Four real defects were
found; all four are fixed and the result compiles.

### 12.1 Setup wizard ignored Light/Dark/System entirely — FIXED

`ui/onboarding/SetupWizardRoute.kt` was a permanently dark screen: background
`Color(0xFF0C1117)`, headings `Color.White`, secondary text `Color.Gray`, accents
`Color(0xFF58A6FF)`. A first-run user who picked Light appearance still got a dark wizard,
which breaks the Phase A acceptance criterion that Light, Dark, and System work with both
app themes.

Fixed by mapping every hard-coded value onto `MaterialTheme.colorScheme`
(`background` / `onBackground` / `onSurfaceVariant` / `primary`). Button labels that were
pinned to `Color.White` and `Color.Gray` now inherit the Material 3 defaults instead.

### 12.2 Avatar stage panels stayed near-black in Light mode — FIXED (two sites)

`ui/companion/ProfessionalWorkspace.kt` and `ui/companion/AvatarScreen.kt` both painted the
avatar preview panel with `Color(0xFF101719)` — the *dark* Professional background — so the
panel stayed near-black whatever the user chose. Both now use `surfaceVariant`, which keeps
the stage distinct in both brightnesses. The Avatar Screen case matters most: that panel is
the "live preview" the Pearl colour pickers are judged against.

### 12.3 Privacy warning card was unreadable in Light mode — FIXED

`ui/companion/VoiceModelScreen.kt` — the "Audio leaves this device" disclosure has a fixed
dark-amber container (`Color(0xFF1C1600)`) but its body text used the *themed*
`onSurfaceVariant`. In Light mode that resolves to a near-black grey on a near-black amber:
**measured contrast ≈ 1.9:1**, far below the 4.5:1 requirement — on the one piece of text
that tells the user their audio leaves the device.

Because the card never flips brightness, its text must not either. Changed to a fixed cream
`Color(0xFFF0DCC0)`, which measures ≈ 13:1 on that container and is stable in both modes.

### 12.4 Accepted as-is (brand accents, not defects)

These are hard-coded but are deliberate brand/stage colours rather than themeable surfaces;
they read correctly against their own backgrounds and are left alone:

- `ChatOverlay.kt:165` — cyan `Color(0xFF00E5FF)` mic tint while listening.
- `AgentActivityPanel.kt:208`, `AppRailTab.kt` highlights, `ModelManagerScreen.kt:326` —
  accent blue on controls.
- `AppDrawerScreen.kt` — the drawer is intentionally a full-bleed dark surface.
- `WeatherScene.kt`, `GodotCatFace.kt`, `AnimatedBackgrounds.kt`, `ParticleOrbAvatar.kt` —
  artwork palettes, correctly not theme-driven.
- Scrims (`BackgroundTiles.kt`, `AppBackground.kt`, `CompanionScreen.kt:386`) — intentional
  dimming for legibility over media.

---

## 13. Full unit-suite regression — BLOCKED (host memory, not code)

The full suite is ~118 test classes. **Five** attempts were made; none completed.

| # | Configuration | Outcome |
|---|---|---|
| 1 | Gradle defaults (512m worker heap) | `OutOfMemoryError: Java heap space` |
| 2 | 3g heap, `forkEvery = 1`, 2 workers | Still OOM; wrote a 5.0 GB `app/build/oom.hprof` (since deleted) |
| 3 | 6g heap, `forkEvery = 1`, 1 worker | No OOM, but **0 results written in 20 min** — thrashing |
| 4 | 6g heap, no recycling, 1 worker | No OOM, but **0 results written in 10 min** — thrashing |
| 5 | 6g heap, no recycling, 1 worker, host at 64% free | Killed at **22m 28s**: `in-progress-results-*.bin` still **0 bytes**. Swap had climbed to **9.3 of 10.2 GB** and free memory had *fallen* to 41% — the run was degrading the host, not progressing |

**Diagnosis — this is an environment limit, not a product defect:**

- The Gradle daemon logs contain **zero** OutOfMemoryError entries, so the *test worker* is
  what dies. The build itself is healthy: the same invocation compiles cleanly and runs the
  60 targeted tests in 25 seconds.
- The host is memory-starved independently of this build: 38% free, **5.4 of 6.1 GB swap in
  use**, ~54 MB free pages. Adding workers made it worse, which is why attempts 3 and 4 use
  a single worker.
- Robolectric retains an SDK classloader per test class, so heap climbs monotonically
  across the run. That is why raising the heap alone did not fix it, and why attempt 2's
  recycling helped memory but made wall-clock unusable.

**What was changed and left in place** (`app/build.gradle.kts`, `gradle.properties`):

- Worker heap raised from the 512m default to `6g`, `maxParallelForks = 1`.
- Daemon heap raised from 2g to 4g (2g was too small for this project at all).
- `forkEvery = 1` is left **commented out** with a note: it stops the OOM but is unusably
  slow on a swapping host. Re-enable it on a machine with free RAM.
- `-XX:+HeapDumpOnOutOfMemoryError` deliberately **not** set — it produced the 5.0 GB dump.

**To close this gate:** the whole-suite invocation is not viable on this host. Run it in
**batches** instead — a `--tests` filtered subset does complete (a single-batch probe wrote
real results where the full run wrote none) — or run the full suite on a machine with free
RAM. Either way, record exact pass/fail counts and separate the known pre-existing
`ModelDownload*` JVM-only failures from any regression. Example:

```
./gradlew :app:testDebugUnitTest --offline \
  --tests 'com.example.meowmba.ui.*' --tests 'com.example.meowmba.avatar.*'
```

Do **not** interpret an empty result directory plus a long runtime as "slow but working".
Attempt 5 ran 22 minutes and produced zero bytes of results; the tell is the size of
`app/build/test-results/testDebugUnitTest/binary/in-progress-results-*.bin`, not the clock.

**Also note:** `dl.google.com` is proxy-blocked in this environment, so Gradle dependency
resolution fails outright. All runs above needed `--offline`; the 1.3 GB
`~/.gradle/caches/modules-2` already satisfies the build.

## 14. Install readiness (2026-09-04) — built, NOT installed

The device is attached: `RFCY61NJEVM` — SM-F966U1 (Galaxy Z Fold 7), Android 16.

### 14.1 Why the version had to move

| | Phone (installed) | Source tree (before) |
|---|---|---|
| `versionCode` | 20 | 20 |
| `versionName` | 1.3.16 | 1.3.16 |

They were identical, so `adb install -r` would **not** have been an upgrade — it would have
been a same-version reinstall, and app-data preservation would have been a coin flip. Per the
user's decision the source was bumped:

```
versionCode = 21
versionName = "1.3.17"
```

### 14.2 Signing verified — no `UPDATE_INCOMPATIBLE` risk

An APK signed with the wrong key fails to install over an existing app regardless of version.
The on-device APK was pulled and its signer compared against the debug keystore configured in
`local.properties`:

| | Installed APK | `meowmba-meow-debug.keystore` |
|---|---|---|
| DN | `C=US, O=Android, CN=Android Debug` | `C=US, O=Android, CN=Android Debug` |
| SHA-1 | `23a43012…1d14e941` | `23:A4:30:12:…:1D:14:E9:41` |
| SHA-256 | `75b19d5a…08c21d7c` | `75:B1:9D:5A:…:08:C2:1D:7C` |

Identical. A rebuild from this tree installs over the current app as a genuine in-place
upgrade that keeps the Room database and selected models.

### 14.3 Status

- [x] Version bumped (20 → 21 / 1.3.16 → 1.3.17)
- [x] Signing key confirmed to match the installed app
- [ ] Debug APK rebuilt at the new version
- [ ] **Installation — awaiting explicit authorization. Nothing has been installed.**

The install command, to be run only on explicit go-ahead:

```
/Users/prog/Library/Android/sdk/platform-tools/adb install -r \
  app/build/outputs/apk/debug/app-debug.apk
```

`-r` is a reinstall: it preserves app data. Do **not** substitute `-r` with an uninstall or
`pm clear` as a shortcut — that would destroy the Room database and the user's model
selections, which Gate 4 explicitly forbids.
