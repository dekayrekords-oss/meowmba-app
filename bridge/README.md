# Desktop helper 0.7 — preview

This helper connects a phone to a computer the owner controls. It is not
installed or authorized by plugging in USB. Pairing persists across USB/network
disconnects; the helper must still be running and reachable.

## One-time setup

1. Install Python 3.10+ and keep `meowmba-bridge.py`, `desktop_ui.py` and
   `blender_scene.py` together, outside every writable workspace. Python uses
   the standard library; Blender supplies its own Python API. OpenSSL is needed
   to create the TLS certificate.
2. Run `python3 meowmba-bridge.py --pair`. In Meow → Settings → Desktop Bridge,
   choose **Find desktops**, verify the computer/certificate, and enter its
   eight-character code. Same LAN or a correctly configured trusted private
   network is required. Do not expose this port directly to the internet.
3. The helper creates `~/.meowmba-bridge/config.json`. Stop it before editing
   configuration. Add only the capabilities you want, preserving existing
   fields. For example, on a Mac (replace the sample username and use a
   dedicated existing output folder):

   ```json
   {
     "name": "My computer",
     "port": 8765,
     "workspace_roots": ["/Users/yourname/MeowProjects"],
     "gui_enabled": true,
     "blender_enabled": true,
     "blender_binary": "/Applications/Blender.app/Contents/MacOS/Blender"
   }
   ```

4. Start `python3 meowmba-bridge.py` again. On macOS, approve Accessibility and
   Automation for the application hosting this helper in System Settings →
   Privacy & Security. Screen Recording is separate and needed for screenshots.
   These OS permissions cannot be granted by the phone. Use **Ping** in Meow to
   check connectivity; it is not a test of all capabilities.
5. Run a harmless task first, such as “on my computer, open TextEdit.” Every
   desktop open/click/type step has its own phone approval. Verify the visible
   result. Say/task-cancel stop if a plan is wrong. Never approve credentials,
   financial actions, or a vague or unexpected recipient/content.

Only the owner should be able to modify the helper/configuration. Do not put
the helper, its configuration or trusted command scripts inside the workspace
that the phone may write. Keep workspace roots narrow; do not choose a home
directory, filesystem root, credentials directory, or the helper installation.

## What is actually supported

- Existing pinned-TLS/HMAC file, launch, URL, clipboard and configured-command
  tools remain. There is no arbitrary shell command endpoint.
- `ui_snapshot` and `ui_action`: **macOS Accessibility only**. Bounded, visible
  foreground-window controls; click and text-entry, not an image/coordinate
  computer-use system. A snapshot is single-use and expires after 60 seconds;
  app/content changes invalidate it. Long approvals may require a fresh task.
  Password fields and command consoles remain manual. Web/browser controls work
  only when exposed to the OS accessibility API. Custom canvases can be absent.
- Linux/Windows UI navigation is **not implemented**. Meow currently reports
  a tty-only Linux session with no Blender/xdotool found on PATH; it cannot be
  represented as a ready GUI workstation.
- `blender_scene`: an explicitly enabled, installed Blender produces a **new**
  1–80 primitive scene in a new directory below the first workspace root.
  Validated JSON only; no model-generated Python, imports or paths. It exports
  `scene.blend`, `scene.glb` and optional `preview.png`, reporting sizes/hashes.
  Existing/open Blender documents are untouched. This is not professional
  sculpting, rigging, editing an arbitrary scene, or installing an app avatar.
- Neither a successful tap nor a planner's “done” is independent delivery
  verification. Failed/uncertain actions and Blender jobs must not be retried
  automatically. Job directories are retained for inspection.
- General HMAC requests have a timestamp freshness window, not a persistent
  replay ledger. The new UI endpoint additionally uses consumed snapshot IDs;
  Blender does not yet have persistent request-id deduplication. Do not enable
  unattended remote publishing or irreversible workflows on this preview.

`--auto-pair` is a deprecated alias for code-based `--pair`, not tokenless USB
trust. A USB subnet address alone never proves ownership.

## Validation

`python3 -m unittest discover -s bridge -p 'test_*.py'` from the repository root
tests stale/replayed UI requests, input bounds, scene schema and pairing gates.
Tests with fake Accessibility controls are not native GUI acceptance. Native
macOS access was denied on the development host and still needs owner approval.
The Blender 5.2.1 smoke fixture was actually rendered/exported on the build Mac.

Relevant primary documentation: [Apple UI scripting](https://developer.apple.com/library/archive/documentation/LanguagesUtilities/Conceptual/MacAutomationScriptingGuide/AutomatetheUserInterface.html)
and [Blender scene export](https://docs.blender.org/api/main/bpy.ops.export_scene.html).
