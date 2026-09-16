# Build 119 generated avatar drawing and library notes

This worker added the bounded native drawing format, native painter and private
saved-form library for arbitrary requested cartoon transformations. The root
integration owns provider selection, tool routing, cancellation, selection,
placement, touch hit testing, privacy marks, the settings page, and release QA.

## What changed and why

- `GeneratedAvatarForm.kt` defines version 1 drawings as 1–32 layered ellipses,
  rounded rectangles or polygons. Polygon points are local coordinates with a
  maximum of 24 points. Center, size, rotation, outline and opaque colors are
  validated. Every rotated part rectangle must fit the normalized silhouette
  boundary. A separate live-face layout supplies eye and mouth positions.
- `GeneratedAvatarForms.parse` rejects unknown fields, unsupported shapes and
  motions, malformed numbers, invalid colors, excessive counts, empty polygons,
  oversized text and trailing content. Names, descriptions and shapes are data;
  they never become executable code, shaders, remote images or filesystem paths.
- IDs are SHA-256 hashes of canonical JSON. Keys are sorted and coordinates use
  short round-tripping Float values. Android and JVM JSON implementations have
  different object ordering; hashing plain `JSONObject.toString()` would make
  persisted identity dependent on the runtime. Expanding Float coordinates into
  Double decimal strings would also waste the bounded storage budget.
- `GeneratedAvatarPainter` draws the specification with heavy cartoon outlines,
  live pupils, gaze, blinks, eyebrows and speech. It receives Lumen's existing
  frame and pause-aware time; it creates no independent sensor, coroutine, neural
  model or background render loop. Wings use `LumenFrame.wingBeat`, including
  existing neural arousal and wing drive. Other anatomy follows active time,
  ear lag, tail swing, engagement and squash. Touch emotion therefore remains
  visible in new forms. The root draws system privacy marks afterward.
- `GeneratedAvatarStore` keeps at most 24 valid forms with up to eight exact
  normalized request aliases each. Replay can match an exact saved request,
  name or description. Content-hash filenames prevent path injection. Writes
  use a temporary file, `fd.sync()` and an atomic replace where supported.
  Invalid/mismatched files are ignored. A full library asks for removal instead
  of silently evicting an avatar that may currently be selected.
- `GeneratedAvatarExamples` contains three original QA designs: Peach dragon,
  Coffee captain and Pocket starship. They demonstrate the drawing vocabulary;
  the renderer and parser are not restricted to those subjects.

## Verification supplied to the build owner

The worker added 19 tests across `GeneratedAvatarFormTest`,
`GeneratedAvatarStoreTest` and `GeneratedAvatarRenderTest`. They cover strict
validation, canonical IDs, content round trips, restart/replay, corruption,
library capacity without eviction, safe filenames, deletion isolation, distinct
silhouettes, live gaze/blink/speech/touch, shared neural wing output, frozen time,
hidden presence and portrait/landscape bounds. The native render tests write a
contact sheet plus three landscape previews to `build/reports/generated-avatars`.
The root is the single build owner and must record actual test/build results and
inspect those generated previews before claiming verification.

## Meow debugging and practical limits

The format creates bounded 2D vector cartoons; it does not train a new brain,
produce arbitrary 3D meshes or download a character model. Design quality depends
on the configured model's silhouette composition. The same existing companion
brain and interaction frame continue driving the rendering.

For a failed creation, inspect the tool result and validation reason before
changing rendering. Test the returned JSON with `GeneratedAvatarForms.parse`.
If a form saves but cannot reopen, compare its canonical content ID with its
64-character filename and check the envelope version. Never rename a file using
the model's character name. Corrupt data must not replace the active form.

If a winged character appears static, compare successive `LumenFrame.wingBeat`
values while the active clock advances; do not add a second wall clock or bypass
thermal/background pausing. If features move but touches miss, compare the root
placement used for drawing and hit testing. Painter geometry uses .82 internal
normalization and a conservative maximum extent of approximately .85 times its
supplied unit after motion and roll. Placement must account for that bound.

No phone installation, phone performance measurement, remote server modification
or public source publication was performed by this worker.
