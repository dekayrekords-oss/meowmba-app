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
Arcade758/1254, Garden833/1254, Aquarium753/1254, Garage850/1254. Do not substitute
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
removed (about69MB), followed by about247MB of Gradle9.4.1 generated-JAR and Java
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
Build112. No new brain/model, sensor, robot, account or permission workflow.

## Integration findings

The first run passed119/122. All habitat and Google-launcher checks passed.
One new UI test used an unsupported search phrase; its input now uses the
existing supported “search for” grammar, without altering global command rules.
The older scene-pause test tried to construct AndroidKeyStore on the host before
testing its clock. A scoped encrypted-storage construction mock now allows the
real lifecycle, cadence and thermal-listener assertions to execute unchanged.

Visual review found the two-column compact favorite layout too tall. The drawer
now fits three columns at320dp and uses smaller favorite tiles, with a static
aqua/lime console detail. A deep-scroll regression prompted remembered grid
navigation: user query/folder/sort/filter/submission changes reveal their actions;
initial/restored scroll and background catalog refresh do not jump. Successful
empty PackageManager refreshes now write an empty disk cache, preventing removed
apps reappearing. Missing favorites can be removed only through an explicit
Organize action after a successful scan, never automatically.

The second run passed124/125; folder naming still kept Compose busy immediately
on opening. Its field now has bounded full-width layout with an external label
and character counter instead of nested floating/supporting labels. Validation,
folder callbacks and the full create/add/remove/rename/delete/Back assertions
remain intact. The final verification below records the result of this repair.
