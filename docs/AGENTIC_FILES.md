# Agentic Files — Meowmba 1.3.26 / version code 30

## User experience

- A Files tile opens a dedicated full-screen, dark/light-theme-aware browser.
- Familiar file-manager categories: Photos, Videos, Audio, Documents, Projects,
  Archives, Folders, Recents and Trash; list/grid switch, image thumbnails,
  filename search, sort by name/date/size, multi-selection and folder navigation.
- Import selected documents/media, browse explicitly connected phone folders,
  create folders, move/rename files, share and export through Android pickers.
- Connected folders are read-only. Import copies into the library before AI
  search or organization; originals are untouched. Permissions can be revoked
  with Disconnect. Android blocks some roots and other apps' private directories.
- Library storage remains app-private, not a replacement for Samsung's system
  storage privileges. Export important files before uninstalling Meowmba.

## One asset location

The existing `filesDir/memba_workspace` is retained, so existing workspace
files and folder-based projects remain visible without moving or deleting them.
Room-backed project chats remain in Room; the browser creates a project.json
link under projects/project-ID. New generated photos, video and audio use
unique filenames in type folders, nested under the active project when present.
New saved browser pages go to documents/web-pages. Old files in the legacy
web_pages directory are retained, but are not automatically migrated/indexed.
Glasses photos also use the shared photo/project location. Imported files use
the folder currently being browsed, or the chosen category/imports folder.

## AI and agent boundaries

AI search ranks IDs from up to 200 recent or selected library entries. It uses
the configured model and requires a disclosure/Continue action before sending
names, relative paths, types and sizes. File contents are not sent. This is
metadata-assisted semantic search, not photo recognition, OCR or video analysis.
AI organization proposes at most 40 moves, validates actual source IDs,
relative paths, extensions and destination collisions, and requires an explicit
review/apply action. Project internals and HTML/CSS/JS bundles are not moved by AI.

The existing orchestrator owns file.browser, file.search, file.cleanup,
file.move, file.trash and file.restore. Agent mutations require the normal
approval card. No unrestricted filesystem/shell access was added. AI planning
has no tool executor; a model response cannot directly mutate the library.
Agent text previews are bounded to 4,000 characters, media/archive reads are
rejected, and file listings are bounded and exclude hidden Trash internals.

## Cleanup safety

Cleanup finds empty files, exact-byte duplicates and large files. These are
review candidates, not declarations that files are disposable. Nothing is
preselected. Scans exclude symlinks/hidden internals, cap the index at 5,000
entries / 11 folder levels, and cap duplicate hashing at 1 GB. Partial scans
are labeled. Source size/mtime is rechecked before moves or trash operations.
Trash is recoverable, has no automatic expiry, and still occupies storage.
Permanent deletion is per-item, has a second explicit confirmation, and cannot
be undone. Restore and move never overwrite. Batch operations report partial
successes and failures instead of claiming atomic success.

## Verification and release

Build and targeted JVM/UI checks are run on Meow. Device permission flows,
real-provider AI relevance, OEM document providers and large-file performance
must also be exercised on the Fold after separate installation approval.
This feature does not publish an update to Google Play or grant new phone
permissions automatically.

The actual Compose screen was rendered and visually reviewed in both light and
dark themes at a 412 dp phone width. The test renderer uses View drawing because
Robolectric does not provide PixelCopy's redraw callback. The import menu and
AI data-sharing disclosure are covered by UI assertions. Folder-dialog typing
and picker operations still need device verification; a Robolectric dialog
idling timeout is not counted as a passing interaction test.

Android storage reference:
https://developer.android.com/training/data-storage/shared/documents-files
