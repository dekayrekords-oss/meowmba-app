# Meow teach-back: separate backdrops, scalable avatars, camera hard stop

## Outcome and boundaries
The companion now has independent Backdrop and Play habitat modes. Backdrop preserves the original plain/image/animated scenery with a large avatar. The game retains ten separately authored3D rooms and movable props. `background` remains the user's backdrop; `habitat_background` and `habitat_mode` store the game choice separately. Selecting a room never overwrites the wallpaper. Old pet-environment selections migrate to the plain backdrop instead of silently forcing game mode. Avatar selection no longer automatically sets the kitchen. Scale is persisted as a finite50–200% value for all avatar styles.

The generated photographic sunroom is a static1024×1536 image; it demonstrates a photographic setting against the cartoon character. Original background choices are retained. Game improvements include room-filling geometry, contrasting materials/window light, furnishings, softer analytical contact shadows and a bounded five-tap edge pass. These are concrete mobile graphics improvements, not a verified10× resolution increase or a claim of AAA production quality. No company's proprietary interface, artwork, or source was copied.

## Interaction and neural behavior
Tap a fly to direct its attention and send the existing TOUCH stimulus; hold to pet; double-tap to play. Habitat taps also choose a bounded flight target. Both modes expose Peekaboo, Tickle and Dance, using existing authored performances and affect/preference pathways. Feed/Rest in backdrop mode produce appropriate facial performances; game mode retains feed, play, rest, read, drink, nudge, put-down and explore. Care remains optional; preference feedback is bounded and resettable. No new neural inference loop, real FlyWire synapses or biological flight claims were introduced.

## UI and scaling
Avatar setup now has a preview, selected avatar, mode controls, size slider/reset, Look/Motion tabs and collapsed advanced options. Background selection has Static/Animated/Habitats categories; irrelevant game dimming is hidden. Shared MaterialTheme shapes/type/semantic color roles and the dismissible workspace menu improve light/dark consistency, contrast and touch targets. All15 workspace destinations remain. Detailed design notes: design.md and AVATAR_SETUP_DESIGN_PASS_DOWN.md.

Scaling applies inside avatar painting; privacy indicators stay in fixed screen positions. Inverse hit transforms keep scaled eye/ear/other zones aligned. FULL_FLY uses transparent TextureView in Backdrop mode; game mode draws the room. A complete immutable frame comparison breaks the draw/invalidation loop and permits one redraw for a paused appearance change. The GL host admits that explicit appearance redraw without re-enabling animation. Do not add another clock to solve pause/size issues.

A phone GPU regression caught landscape saturation: width-first fitting hit the same height cap at50% and200%. The fix fits the default to BOTH dimensions before multiplying by the requested size, then applies a safe edge cap. The original pixel-area and transparent-border assertions remain intact.

## Camera bug and repair
The old home video button toggled PiP, then togglePreview called ensureFaceTrackingOn, restoring the preview and continuing capture. Home now calls setCameraEnabled and shows Turn camera off/on from actual enabled state. Enabling tracking does not open PiP. The explicit OFF action persists false, cancels pending permission intent, clears preview and calls VisionManager.setCaptureEnabled(false).

CameraCaptureGate defaults OFF and uses generations. OFF clears preview provider, analysis, detectors, attention, watchdog and orientation work, unbinds CameraX, and invalidates pending bind/capture/retry callbacks even across OFF→ON. The executor remains idle for the next explicit enable; full manager close shuts it down. Temporary thermal analysis pause remains distinct from explicit privacy OFF. Resume synchronizes the saved gate; MainActivity also calls resumeFaceTrackingIfEnabled after initial VM construction because initial onResume can precede composition. Explicit 'look' requests enable capture; unsolicited/delayed grants after OFF do not. Crash verification respects OFF and reports unavailable vision rather than silently enabling it.

Future diagnosis: compare UI enabled state, saved preference, capture gate generation and Android's Active Camera Clients. Do not infer hardware shutdown just from hiding PiP. On the phone, pressing the home button produced camera-off semantics and Active Camera Clients:[], with the app's disconnect event. The camera remained OFF after process restart/upgrade. Do not publish unrelated camera-service event history.

## Validation record
Builds101 and102 compiled and installed during validation.35 focused JVM tests passed: camera gate5, actual ViewModel privacy5, appearance preferences4, scale/hit mapping3, world6, care6, work policy6. Test-only ScopedCompanionAndroidMocks supplies scheduler time/log mocks and restores the unrelated AgenticRuntime singleton after each fixture; no global return-default or production policy was weakened.

Broader avatar/background/vision/camera batch:149 tests,136 passed,13 failed. All13 names match preexisting failures in `/home/claude/meowmba-fruit-fly/app/build/test-results/testDebugUnitTest`: VisionPipeline10, AvatarMorph1, CoverAvatarPreference1 (mock-context initialization), CompanionSceneBackgroundPause1 (AndroidKeyStore). This is not a green full suite. The new tests pass.

Actual phone UI verification: home camera-off, workspace navigation, avatar setup, fly selection,150% slider update and reset, photographic background selection and home composition. GPU tests cover front/side/back distinction, alltenrooms, draw budgets, transparent stage borders at360×800,800×360,640×800, paused scale changes and pause stability. Build102 exposed the landscape saturation bug, corrected in final103; append final device outcome below.

## Build and handoff
Use isolated `/home/claude/meowmba-fly-habitat`, JDK21 and existing ignored runtime assets plus prebuilt native directory. Do not discard ignored voice/model assets. APK delta transfer reconstructs exact signed bytes from baseline94 and verifies SHA256. Production package com.meow.meowmba remains untouched; debug package updates preserve data. This pass-down and the specialized camera/renderer/design notes teach the main Meow server what changed, why and how to troubleshoot it.

Long-duration battery/heat validation remains open. Camera shutdown removes a confirmed source of work but does not promise that neural/audio/3D workloads cannot heat the phone. Thermal protection remains enabled. Broad device/layout coverage and end-to-end physics interactions beyond tested cases are still limited.

## Final build103 device result
Installed103/versionName1.3.81 successfully with data preserved. APK SHA256 1c3a28ed2558313e72186917a7b2af40a4de77226b022acf50f374ec7c0b6bfe. Allthree GPU tests passed in2.785 seconds on Samsung SM-F966U1 after the landscape fix. Inspected final kitchen, front/stage and landscape scale images. Android Active Camera Clients remained[] after installation/restart. User was also interacting with the phone; final observed display is the kitchen game with camera off, so do not overwrite their mode/mic choice for a prettier screenshot.

A35-second observational102 sample had Meow foreground16/18 samples, approximately31.31% of one core, USB charging and battery34.4→34.5 C with thermal status1. User controls and foreground changed during the session; this is not a controlled photo/game/camera A/B comparison, nor proof of sustained cooling.

Compared all54 existing native/assets entries from installed100 to103 by decompressedSHA256: 0 changed or missing. New sunroom artwork is additional.
