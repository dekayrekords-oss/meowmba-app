# Meow teach-back: Blender and native 3D fly, preview 100

## Delivered
Blender 4.2.0 is installed and verified on Meow at `/home/prog/tools/blender-4.2/blender-4.2.0-linux-x64/blender`. The official portable Linux archive was used without changing system packages. `scripts/build_fly_blender.py` creates editable volumetric geometry, articulated empty joints, five animation clips, a GLB, and cel-shaded previews. See BLENDER_FLY_AUTHORING_PASS_DOWN.md for authoring commands and exporter repair.

The Android FULL_FLY selection now uses genuine perspective/depth-tested GLES geometry in a 3D room, with a cartoon outline and banded lighting. It moves in XYZ, banks, animates six limbs/wings/antennae/mouth, and shares the existing Lumen output and care controls. Ten room motifs and simulated book/glass/ball props are provided. The original floating Lumen face and separate commercial character remain available.

The Blender asset is editable source art. Android currently draws separately authored matching procedural geometry; it does not import the GLB or meshparts file. Do not describe it as a Blender mesh loaded by the app. The supplied YouTube video could not be inspected reliably; exact likeness was not verified.

## Integration and fixes
Fly3DWorld owns deterministic bounded movement, prop approach, grasp/release, and simple gravity. World degrees convert to renderer radians at the existing AvatarRenderer clock bridge. That bridge consumes existing LumenRig outputs; it adds no neural, microphone, or sensor loop. Mouth, gaze, eyelid closure and behavioral biases use the same core. This is stylized kinematics, not biological flight.

Fly3DView owns a window-composited TextureView/EGL worker, so Compose privacy badges and care controls remain above graphics. One pending draw reads the latest immutable frame. Equal avatar timestamps do not request more GPU frames, avoiding a draw/invalidation feedback loop. Repeated unchanged active-state updates do not request frames. Lifecycle/thermal pause freezes the scene. Initial mount can draw one still while paused. The opaque 3D room suppresses the hidden animated 2D background to avoid duplicate work.

A replay test caught a discontinuous hand jump at pickup. The hand now has a vector speed cap of 1.1 world units/second, tested at normal and 20 FPS intervals. Future grasp bugs: inspect approach distance, hand speed, prop ownership, and the degree/radian boundary before altering the renderer.

Blender 4.2 glTF NLA export mutated live joint transforms, producing disconnected preview parts. The saved scene was intact. The authoring script now restores authored GLB rest transforms and reloads the saved scene before rendering, with rest-pose assertions. Always inspect actual images after export; a successful command alone does not prove correct art.

Android instrumentation initially found an older test-only package signed with a different key. Replaced only `com.example.meowmba.test`, preserving companion app data and the separate production package. The new test signature matches the existing debug app.

## Verification and device state
Final debug versionCode100/versionName1.3.81 installed successfully on Samsung SM-F966U1, serial RFCY61NJEVM. Package com.example.meowmba; production com.meow.meowmba remains untouched. Final APK SHA256: 146aeb5f49dc9a90e241e1b0b7b2074c970d7b897f81f993fef7803390e66d9c.

Gradle assembleDebug and assembleDebugAndroidTest passed. All18 focused JVM tests passed (6 world, 6 care, 6 work policy). The actual phone GPU instrumentation test passed again after the final install: shaders compile/render without GL errors, front/side/back differ, paused output is identical, and flight-quarter renders. Inspected actual native GPU and live app images, with Play and mic privacy controls above the room. Verified Blender front/quarter/side/back/flight views separately. Wider-suite baseline failures are documented in HABITAT_BUILD_PASS_DOWN.md; this does not claim a green full suite.

A35-second sampling interval had Meow in the background for all18 samples: approximately0.147% of one CPU core, battery36.8 to36.7 C while USB charging; Android thermal status2. This supports short background suspension only. It is not an active3D performance benchmark or sustained heat/battery validation. Keep cooling protection enabled. Full foreground frame-time, long thermal/battery, repeated orientation/context restoration, and live end-to-end grasp/drop validation remain open.

## Remaining research
The neural core remains authored/seeded rate/spiking behavior logic. No real FlyWire synapses were added. Biological flight, sustained battery/heat gates and robot transfer remain unfinished and separate. Do not claim biological fidelity or deploy robot control from this animation demo.

## Rebuild
Use `/home/claude/meowmba-fly-habitat`, JDK21.0.12+8 and existing SDK. Gradle requires `-PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview` plus existing ignored runtime assets/signing configuration. Transfer APKs through scripts/apk_delta.py using the local phone-baseline-94.apk manifest; SHA verification preserves exact signed APK bytes and avoids repeatedly copying400MB. Store raw phone captures/APKs under ignored build-out. Native renderer-only evidence is saved beside this note without unrelated user content.
