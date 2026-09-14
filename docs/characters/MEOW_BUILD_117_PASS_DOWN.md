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

Final test totals, package hashes, visual review and connection status follow
after the Meow run. Host tests do not establish the installed Google version's
UI, actual network results, native GPU behavior or sustained device heat/battery.
Preserve phone data and pairing when installing an update. Prior verified phone:
Build 112. No new brain/model, sensor, robot, account or permission workflow.

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
