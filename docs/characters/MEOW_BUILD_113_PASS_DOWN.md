# Build 113 — three more cartoon worlds

The user requested more cartoon backgrounds, one interactive scene and a couple
with funny background objects. This adds three original rooms without replacing
any existing selection. Source implementation is ready; final build/verification
evidence is appended below by the integrator. Build 112 remains on the phone;
the user's previous direction was to continue without the disconnected device.

## What changed, how and why

- Cartoon snack lab: mint/apricot room, crowned banana specimen and a spring
  jelly toy. Cosmic laundry: portal washers, a sock trophy, enormous sneakers
  and a spring sock puppet. Oddball diner: UFO pancakes, a boot cactus and a
  pancake spring toy. All three are shared habitat choices for every avatar.
- Three 1536×1024 original PNG plates were generated with built-in image_gen,
  copied into `app/src/main/assets/backgrounds/painted/`, and inspected. Exact
  prompts, original generation paths, file hashes, dimensions and measured
  table anchors are in `artwork/painted-environments-manifest.json`. The
  generation originals remain untouched. No external cartoon characters or
  logos were copied into the scene artwork.
- `IllustratedEnvironmentBackground.kt` keeps the actual table edge at the
  existing .78 interaction plane: source rows 679, 685 and 664 respectively.
  Horizontal focal framing retains the banana and pancake joke in portrait,
  clamped so every viewport edge is still covered. Existing rooms keep their
  previous centered framing. Decode size/cache limits remain unchanged.
- The picker places the five cartoon rooms first, new three followed by the
  existing nook and galley, with all ten original painted environments below.
  Stable IDs are appended to the enum; wallpaper, avatar, scale, care and
  neural choices are not reset. Shared outlined native controls are retained.
- Sparse vector loading fallbacks match the three new palettes. At most two
  small ambient accents per room use the existing scene clock and pause rules.
  No independent animation timer, network fetch or larger image cache is added.
- `CartoonHabitatGags.kt` adds native vector push-toys. A tap runs one bounded
  1.8-second squash/spring/bounce and routes PLAY through the existing care,
  affect and neural TOUCH path. Repeated taps replace the response rather than
  queueing animations. Avatar/prop taps take precedence; accessibility offers
  an explicitly named toy action. Paused/severe-heat input is rejected and
  reduced motion keeps still art. See `CARTOON_GAGS_113_PASS_DOWN.md` for exact
  ownership, projection and debugging guidance.
- FULL_FLY uses an opaque GLES room, so adding PNGs alone would leave that
  avatar in a generic room. `Fly3DRenderer.kt` now has sparse matching native
  3D variants: banana specimen, tumbling-sock washer/trophy, and hovering
  saucer/pancakes/boot cactus. Existing mesh helpers, outlines and clock are
  reused. New scenery has thicker ink and flatter color; character rendering
  and old rooms retain their previous material behavior. The exact existing
  table/prop drawing code is shared through `interactionTable` to preserve
  physics/contact ownership. No extra meshes, textures, clocks or runtime IO.

The push-toy is a screen overlay, not a new graspable 3D prop. Activating it
starts the existing avatar PLAY/ball response; the glass/book/ball retain their
existing pickup and physics behavior. This work does not add brain anatomy or
change the Build 112 remote pairing status.

## Checks and lessons for Meow

`CartoonHabitatUiTest` exercises compact and landscape picker reachability,
actual asynchronously decoded images (verified against source pixels) and
pressed-toy native captures. Missing images cannot pass by showing fallbacks.
`CartoonHabitatGagTest` covers pause/foreground ownership, bounded retriggering,
reduced motion, invalid input, sizing and the GPU camera exclusion projection.
The placement suite checks anchored full coverage and portrait joke visibility.

The first local focused run passed the 36 rendering/world/interaction checks;
four existing appearance-preference checks failed before their assertions
because the Mac's selected Temurin test JVM could not attach the Mockito agent.
The server runs those preference checks with its working JDK configuration;
do not describe a VM attachment failure as an app behavior regression. Initial
native previews also exposed cropped-out portrait jokes, fixed with horizontal
focal anchors rather than stretching the image or moving the physics plane.

An old vector-background test still expected ten rooms although twelve already
existed, and its fixed-height contact sheet clipped new rows. It now expects
15 environments/five cartoon rooms and allocates sheet height from the catalog.
Do not remove older scenes or hide tests to satisfy a stale count.

`Fly3DGpuTest.newCartoonRoomsHaveNativeSceneryAndFreezeWithTheAvatarClock` adds
device-side distinctness/opacity/portrait/landscape/pause and geometry-budget
coverage. It must run on real GLES; native Canvas/JVM images do not prove GPU
output. Phone installation, GPU capture review, physical touch occlusion and
sustained heat/battery testing remain pending without a connected device.

## Build and continuity

Debug-only versionCode is 113; release configuration remains unchanged. Build
from the canonical private checkout `/home/claude/meowmba-fly-habitat`, JDK21,
with `-PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview`.
Keep its untracked/ignored voice, pose and acceleration assets. Never clean the
tree, publish Android source or clear production app data. Source goes only to
`/home/claude/git/meowmba-app.git`, `codex/fly-habitat-built`.

Use `scripts/apk_delta.py` against local Build 112 to transfer only changed APK
payloads. Verify target SHA-256, existing signing certificate and protected
runtime entries before offering installation. Recheck the installed version
before a later `adb install -r`; do not downgrade a newer build.

Final verification, signed APK identity and source commit: pending append.
