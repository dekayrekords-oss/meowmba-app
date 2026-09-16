# Build 117: richer play worlds and the space-cartoon drawer

## Scope and implementation

The owner requested more and much better-looking play habitats, a friendlier
space-cartoon app drawer, and web searches through the actual Google app while
retaining the search bar's capabilities. This build adds four rooms with original
illustrated art, native interactive toys and matching 3D room scenery, while
keeping existing characters and saved selections.

The new backgrounds are Portal Arcade, Moon Garden, Alien Aquarium and Meteor
Garage. The built-in image generator made four final square 1254px assets. Exact
prompts, source paths, measured table-edge anchors and SHA-256 values are in
[artwork/build117-environments-manifest.json](artwork/build117-environments-manifest.json).
The sources are copied into app assets. Lighting, ink contours, layered space
scenery and oversized visual jokes frame central avatar airspace and an empty
foreground play surface. Glass/book/ball/food remain owned by the game.

Each final PNG's straight counter edge maps to the shared .78 contact plane:
Arcade 758/1254, Garden 833/1254, Aquarium 753/1254, Garage 850/1254. Do not substitute
prompt percentages: generation does not guarantee their exact position. Existing
cover-without-stretch placement and bounded decode/cache remain. All fifteen
earlier illustrations remain byte-identical. Scenery uses the existing paused,
thermal-aware clock; it has no new animation loop.

Detailed implementation and troubleshooting:

- [BUILD117_HABITAT_NOTES.md](BUILD117_HABITAT_NOTES.md): catalog, scenery, toys, props.
- [BUILD117_DRAWER_NOTES.md](BUILD117_DRAWER_NOTES.md): navigation, layout, folders, search UI.
- [BUILD117_GOOGLE_SEARCH_NOTES.md](BUILD117_GOOGLE_SEARCH_NOTES.md): Google routing and errors.

## Build environment lesson

The Mac could not save images or small shell heredocs despite APFS reporting some
free space. Only reproducible local Gradle, compiled Kotlin and KSP caches were
removed (about 69 MB), followed by about 247 MB of Gradle 9.4.1 generated-JAR and Java
compilation caches through an approved scoped cleanup. Earlier APKs, downloaded
dependencies, signing stores, user data and generated images were preserved.
Builds run on Meow's existing private checkout. Signed APKs are archived there;
local evidence is small JSON/PNG files. Do not reconstruct another full APK on
this Mac without adequate free space. Use Python `-c` for emergency cache cleanup
when a shell cannot allocate a heredoc temporary file.

## Validation and hardware follow-up

Build 117 is complete and ready on Meow. All **131 focused checks across 21
suites pass**, with no failures, errors or skips. Both debug APK and Android-test
APK assemble. Built production source: `016ecdec34a62b859b73be4d81e014c8a125a48e`.
The final acceptance log is `/tmp/meow-build117-final-acceptance.log` on Meow.
Machine-readable evidence: [BUILD_117_VERIFICATION.json](BUILD_117_VERIFICATION.json).

Native host renders cover the compact drawer, light/dark appearances, 1.4x text,
landscape, inline folder editing, seven interactive cartoon rooms and background
selection. Reviewed drawer previews remain readable at 320 dp and in landscape;
all four new source illustrations and portrait/landscape samples were inspected.
The Google failure/voice checks assert actual response visibility and retained
query. The file named `google-unavailable.png` was captured after the next Speak
attempt; it shows the retained query, not the error card, and is not visual proof
of that error. The assertions provide that behavioral evidence.

The phone remains disconnected, last verified on Build 112. Build 117 has **not
been installed**. Actual Google-app behavior, device GPU rendering and sustained
heat/battery remain physical-device checks. The extended `Fly3DGpuTest` is
compiled into the test APK but has not been run on a phone. Full-fly uses native
cel-shaded room geometry; the painted 2D previews do not prove its GLES quality.
Preserve phone data/pairing and use update installation when available. No new
brain/model, sensor, robot, account or permission workflow was added this turn.

### Final release location and integrity

Use `/home/prog/research/meowmba-build117-016ecde/` on `meow-nb`, the owner-designated
`prog@100.77.97.236` server. The earlier `meowmba-build117-0d72ebc` folder is a
superseded review checkpoint; the `016ecde` archive is the final release.

- `meowmba-117.apk`: 464,784,952 bytes; SHA-256 `81371bef3d77c3e6abb257b3d244b177196d97d756d436758ee5f1ebe48feaff`.
- `tests117.apk`: 2,740,566 bytes; SHA-256 `91906071c12d3f3648fa27c27db48e19055a836c1b0699537de7f49cf2b204ae`.

Both packages verify with signer SHA-256
`b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
The application is `com.example.meowmba`, debug versionCode 117/versionName 1.3.81.
All 53 protected runtime entries and all 15 prior painted backgrounds match the
Build 116 hashes exactly. The four new PNGs match the artwork manifest; the APK
contains 19 painted backgrounds and no bundled full MaleCNS graph. Existing
server model assets remain intact and untracked in the build checkout.

Complete APKs, signature logs, verification JSON, 30 native preview PNGs and
Build 115-to-117 deltas are retained on Meow. The small deltas, JSON and previews
were copied to `build-out/habitat/build117/` locally. Delta transfer checks:

- `app117.patch.gz`: `14a2085839bc962520000e197006494c5491333302ebfa2d83020c2f42de47b6`.
- `test117.patch.gz`: `ddbf3c0d57379293af0026981e09afed42ab3174656983755f30734b7434bc97`.

Use `scripts/apk_delta.py apply` only with the exact Build 115 base, enough disk
space and the corresponding delta; verify the resulting APK hash before an
update install. The complete verified APK is already available on Meow. Never
clear app data or delete previous releases to make room without authorization.

One archive approval was initially rejected as a possible transfer to a new
research destination. Read-only checks established that `meow-nb` resolves to
the exact owner-designated server, `/home/prog/research` is its existing local
release archive, and the script hashes match. The same operation was then
approved. No alternative host or indirect transfer was used.

### Reproduce the focused acceptance

From `/home/claude/meowmba-fly-habitat` on Meow, use the existing JDK 21 and Android
SDK. Set `JAVA_HOME=/home/claude/.local-toolchain/jdk-21.0.12+8`, add its `bin` to
`PATH`, and set `ANDROID_HOME=/home/claude/Android/Sdk` before Gradle or apksigner.
Run the following test filters with `:app:testDebugUnitTest`:

`*Drawer*Test`, `GoogleAppSearchTest`, `Cartoon*Test`, `Pet*Test`,
`IllustratedEnvironmentPlacementTest`, `Fly3D*Test`, `OfficeHelpers*Test`,
`CompanionSceneBackground*Test`, `CompanionBrainControlsTest`,
`CompanionSetupUiTest` (all in `com.example.meowmba`). Include
`:app:assembleDebug :app:assembleDebugAndroidTest` and
`-PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview`.
The archive verifier at `/tmp/meow-verify-build117.py` checks the source SHA,
suite totals, signatures, protected assets, artwork and transfer deltas. It will
not overwrite an already finalized verification report.

## Integration findings

The first run passed 119/122. All habitat and Google-launcher checks passed.
One new UI test used an unsupported search phrase; its input now uses the
existing supported “search for” grammar, without altering global command rules.
The older scene-pause test tried to construct AndroidKeyStore on the host before
testing its clock. A scoped encrypted-storage construction mock now allows the
real lifecycle, cadence and thermal-listener assertions to execute unchanged.

Visual review found the two-column compact favorite layout too tall. The drawer
now fits three columns at 320 dp and uses smaller favorite tiles, with a static
aqua/lime console detail. A deep-scroll regression prompted remembered grid
navigation: user query/folder/sort/filter/submission changes reveal their actions;
initial/restored scroll and background catalog refresh do not jump. Successful
empty PackageManager refreshes now write an empty disk cache, preventing removed
apps reappearing. Missing favorites can be removed only through an explicit
Organize action after a successful scan, never automatically.

The second run passed 124/125; opening folder naming kept Compose busy. A
standalone Material3 AlertDialog/TextField reproduced this with only one dialog
composition but 372,849 layouts and 372,855 snapshot applies over 60 seconds.
Changing the label and width did not fix it. Inline naming now keeps editing in
the same drawer grid, with accessible input, 24-character limit, Save, Cancel,
keyboard Done and Back cancellation. The entire original folder CRUD regression
then passed, followed by all 125 checks. Temporary diagnostics were removed;
no assertions or production timing limits were weakened.

Final review identified two additional interaction edges. Manual submissions
now supersede voice capture before publishing a result: detach the capture,
cancel caption/timing jobs, stop engines, clear the queued voice commit and
leave LISTENING. A late engine result must never overwrite the query or search
error. The Stop action shares this cancellation path. UI checks cover command
ordering and microphone error visibility; VM tests run a real UtteranceCapture
with a fake engine to inject late partial/final callbacks. Deleting the folder
currently being renamed cancels that editor so Save cannot target a missing ID.

For future voice issues, inspect interactionState, the caption collector,
utteranceCapture identity and drawerVoiceCommit together. The caption channel is
shared by transcript and response; publishing a response while LISTENING allows
it to be mistaken for a transcript. Do not just hide the error or disable the
voice feature to fix this. For modal host-test stalls, compare composition and
layout counts in a minimal reproduction before changing unrelated app state.
