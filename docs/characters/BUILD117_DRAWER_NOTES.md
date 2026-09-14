# Build 117 — drawer implementation handoff

Scope: `AppDrawerScreen.kt` and new `AppDrawerUiTest.kt`. Root owns build,
verification, final commit and private Meow delivery. No shared ViewModel,
permissions, search router, manifest, habitat source, runtime assets or signing
changes belong to this worker.

## What changed and why

The drawer follows the existing native cartoon console theme: teal surfaces,
lime favorites/selections, aqua icon wells, amber folders, bold headings and
2 dp ink contours. A small static orbit mark identifies the screen. Every color
uses existing Material roles in dark/light and optional Professional appearance.
No image assets, animation engine or timer was added.

The fixed header has an explicit Avatar return and search field. Android Back
first leaves an open folder, then closes the drawer. Folder navigation also has
a labeled back button; Avatar always returns directly. The search field retains
IME/icon submit and adds accessible clear. Everything below it shares one
adaptive LazyVerticalGrid: actions, response, organization controls, favorites,
folders and app tiles. This fixes the fixed eight-icon row overflowing compact
screens and consuming landscape height. Favorites retain saved order, labels and
the existing eight-app limit. Grid tracks grow with font scale.

Sort and type use native dropdowns. Organize exposes Options buttons and New
folder while preserving long-press shortcuts. All folder create/add/remove/
rename/delete callbacks remain. App options can remove membership from any
folder, not only the current one. `Favorites full (8)` is explicitly disabled;
blank folder names cannot be saved. Folder removal never uninstalls apps. Saved
preference formats are unchanged.

Installed apps still load from existing cache followed by background
PackageManager refresh. Loading, empty, no-match and refresh-error states are
distinct. Exceptions retain cached apps; an empty error state offers retry.
Cancellation is rethrown. Shared asynchronous icon decoding remains, with a
readable initial when an icon is unavailable. Optional installedApps/onLaunchApp
parameters support deterministic tests; production callers use original defaults.

Google calls the existing callback with DrawerQuery.webQuery and marks the query
submitted. The accessible label is `Search in Google app`; a neutral Response
panel displays the integration worker's missing/disabled-app error without
discarding query. The separate Google worker owns the actual package intent.
App matching, AI/task submit, microphone, partial voice and committed voice
consumption remain. Editing/clearing hides stale responses; long answers have a
bounded scrollable area to keep app tiles reachable.

## Verification delivered

New native Robolectric suite `com.example.meowmba.AppDrawerUiTest`:

1. compactDrawerFitsEightFavoritesAndKeepsAppsReachable
2. phoneDrawerUsesTheSharedLightCartoonTheme
3. largeTextDrawerKeepsSearchAndAppActionsReachable
4. landscapeDrawerScrollsFavoritesWithoutHidingTheAppGrid
5. searchRetainsAppMatchingTasksGoogleClearAndFailureResponse
6. voiceCommitsExactlyOnceAndKeepsLiveListeningText
7. foldersSupportCreateAddRemoveRenameDeleteAndBackToAvatar
8. sortingFilteringAndFavoriteCapacityRemainActionable
9. emptyCatalogShowsAnExplanationWithoutInventedApps
10. changingQueryOrEnteringFolderRevealsSearchActionsAfterDeepScroll
11. unavailableFavoritesAreRemovedOnlyByTheExplicitOrganizeAction
12. manualGoogleStopsVoiceAndConsumesBeforeDispatchWithoutLosingQueryOnFailure
13. microphoneFailureIsVisibleWithoutOldCaptionsOrLiveDraftResponses
14. deletingTheFolderBeingRenamedCancelsItsInlineEditor

The follow-up navigation review adds remembered grid state. Query, sort,
category, folder and submit changes reveal the search actions and first results;
initial/restored positions and background refresh retain scroll. No autofocus or
animated scroll is added. `DrawerApps.kt` also persists a successfully scanned
empty list, preventing stale launchers reappearing from the previous disk cache.
`DrawerAppsCacheTest.successfulEmptyRefreshCannotResurrectRemovedAppsFromDisk`
exercises that disk-to-memory lifecycle.

After successful catalog loading only, Organize identifies saved favorites that
are no longer launchable and offers `Remove missing pins`. Nothing is removed
automatically. This prevents hidden stale IDs from permanently consuming the
eight-favorite limit while preserving the user's preferences after a failed scan.
The new callback test verifies that only the explicit cleanup removes them.

Initial native 320 dp and landscape renders were reviewed. Favorites were too
large, so the base grid minimum is now 88 dp (three columns at 320), favorite
icons are 42 dp with reduced padding, and a small static cut-panel header accent
strengthens the existing space-console theme. Large text still expands tracks.

The full rerun isolated one native non-idle failure when opening the naming dialog;
all other drawer callbacks and dialogs passed. The attempted bounded-field change
did not fix it. A temporary standalone Material3 AlertDialog/OutlinedTextField
diagnostic reproduced the failure without the drawer: only 2 outer compositions
and 1 dialog composition, but 372,849 layouts and 372,855 snapshot applies over
60 seconds. This is a modal text-field layout loop in the native host/framework
path, not a drawer state recomposition loop. Initial manual-clock cases did not
drain the Android queue and provide no additional behavioral evidence. The
temporary diagnostic is removed after recording these exact results.

Naming now uses an inline editor at the top of the same drawer grid. It opens
within Organize, retains accessible name input, the 24-character limit, empty-name
validation, Save/Cancel and keyboard Done, and never opens a second text-input
window. Navigation scrolls to the editor, while Back cancels editing before
returning from a folder or closing the drawer. CRUD assertions remain intact and
now also check Back cancellation without mutations; the test captures the inline
editor before input. Root must record the confirming run.

The final read-only review found two additional interaction races. Manual typed
submit and Google now cancel voice capture and consume any queued transcript
before dispatch; a locally consumed transcript cannot submit again from a pending
effect. Actual voice commits submit/consume once without invoking the UI manual
cancel callback. The parent owns engine cancellation. This prevents Google launch
errors being treated as live dictation and replacing the original query.

Microphone attempts track a caption baseline separately from submitted searches.
Only a changed, non-listening status becomes mic feedback; earlier conversations
and live partials remain hidden as responses. Editing the query clears that
feedback. A separate lastMicFailure value keeps repeated identical known failures
readable after a search edit/clear, without showing unrelated old captions. The
mic regression covers that repeat attempt. Deleting a folder
that is currently being renamed cancels its inline editor, avoiding a later Save
against a removed ID. Focused UI tests cover all three cases; existing CRUD and
voice assertions remain, including zero manual cancellation for actual voice input.

The first four exercise 320/375/414/768 dp, both appearances and 1.4x text,
including landscape. They reach the eighth favorite and last app, assert
horizontal bounds and invoke launch/Avatar callbacks. Two native screenshots per
viewport and one Google-error capture go to app/build/reports/drawer117.
These are host-native renders, not phone evidence. Root records actual build/test
results; the worker has not run a second competing build.

## Teach Meow: debugging lessons

- Column.background does not provide a readable content color. Preserve the
  explicit role pairs for headings or use a real Surface.
- Eight fixed favorite icons overflow small phones. Place favorites in the same
  adaptive grid instead of reserving a fixed strip below the app list.
- One dropdown each for sort/type keeps every option without long pill strips.
- App tile launch uses the actual package. Search still sends matching count and
  exact label through the established router; do not collapse the two paths.
- Google failure is visible only after marking submitted. Keep the query and
  drawer until the external activity actually launches.
- Partial voice never dispatches. Only the committed transcript submits and
  consumes the parent value, preventing replay after recomposition.
- Tests exercise real callbacks/catalog transforms. Injecting the catalog avoids
  probing the host PackageManager, but real installed-app/Google behavior still
  requires a phone check.

Hallmark adaptation preserves design.md/native route and removes no production
files. No copied media or simulated browser chrome. Pre-emit: Philosophy 4,
Hierarchy 5, Execution 4, Specificity 5, Restraint 4, Variety 4. Final native visual
inspection and physical-phone acceptance are left to the root build passdown.
