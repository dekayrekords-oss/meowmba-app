# Desktop helper 0.9 — preview

This helper connects a phone to a computer the owner controls. It is not
installed or authorized by plugging in USB. Pairing persists across USB/network
disconnects; the helper must still be running and reachable.

### USB fallback (Android 1.3.70+)

With the paired phone connected by a data cable and USB debugging authorized,
run `bash scripts/connect_desktop_usb.sh` from the Android repository (or
`adb -d reverse tcp:8765 tcp:8765`). Use the configured helper port if different.
The app first tries its paired network address. If connection setup fails, it
tries the USB tunnel at `127.0.0.1`, using the same pinned certificate and HMAC
secret. It remembers the working route until restart or a connection failure.
Settings → Desktop Bridge → Ping reports the route used. An established
request is never replayed over another route after a lost response. Re-run
the setup command after the USB/adb connection resets; a cable alone cannot
create an Android debugging tunnel.

## One-time setup

1. Install Python 3.9+ and keep `meowmba-bridge.py`, `desktop_ui.py`,
   `desktop_capabilities.py`, `blender_scene.py`, `macos_ax.py`,
   `logic_control.py`, `logic_workflow.py`, `audio_verify.py`,
   `audio_loudness.py` and `audio_staging.py` together, outside every writable workspace. Python uses
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
     "logic_enabled": true,
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
- `status` reports Logic installation/version, current Accessibility trust,
  adapter availability and its local enable switch. This does not launch Logic
  or prove a song is ready to edit.
- `logic_inspect`, `logic_edit`, `logic_receipt`: saved project identity,
  visible tracks/mixer, stopped transport, exact native parameter ranges and
  read-back. Supported typed operations: select track, explicit mute/solo,
  pan, displayed volume in dB, open an occupied plugin, switch to Controls view,
  and adjust exposed numeric plugin parameters. Each phone edit needs approval.
  Receipts persist before dispatch; replay returns the old receipt, never another
  edit. An interrupted/uncertain edit requires fresh inspection. A parameter
  adjustment is bounded to 20 seconds. Phone cancellation disconnects its read
  immediately and sends `logic_cancel`; the helper checks a durable cancellation
  marker between native steps. Already applied changes remain in the receipt.
- Plugin support uses Apple's common Controls view with no manufacturer list.
  Channel EQ and the installed third-party StudioAI were tested in Logic 12.3.1.
  Controls include both `value` (native units) and `display` (readable units).
  These can differ substantially; **do not send dB/Hz as a native value**.
  Duplicate plugin instances on one channel, ambiguous track names, unavailable
  parameters, other platforms and custom controls without AX access are not
  advertised as editable. Audio Unit installation, activation and licensing are
  not performed by the adapter. Custom GUI-only controls remain unsupported.
- `logic_screen` and `logic_act` expose actual Logic menus, region inspectors,
  insert menus and file dialogs. The phone's `logic.run_goal` plans up to 96
  steps, with normal approval/audit on every mutation, current project identity,
  foreground/stopped checks and no arbitrary scripts or remote key codes.
  Import paths must be in a declared workspace; audio is copied into a new
  `logic-imports` directory first because Logic can alter imported file metadata.
  Each snapshot is consumed once. Repetition, uncertain effects and cancellation
  stop the workflow. A UI transition is reported as **observed**, not completion.
  File navigation waits for the Go To sheet to close; macOS focus/permission
  failures stop the task instead of treating autocomplete as navigation.
- Editing, routing, automation and export use controls the running Logic version
  actually exposes. This is not a prevalidated musical recipe for every task.
  Export/mastering goals additionally require a dispatched export and a new,
  decoded, signal-bearing WAV before delivery can be reported. Listening and
  artistic mix/master quality still require review.
- `logic_verify_audio` decodes a PCM/float WAV under an allowed workspace,
  verifies frames/format/hash and reports silence, sample peak, RMS, DC offset
  and full-scale samples. Optional `libebur128` (on macOS: `brew install libebur128`)
  adds measured integrated LUFS, loudness range and true peak for mono/stereo.
  Missing library or unknown multichannel layout is explicitly unavailable;
  values are never estimated. Measurement does not render or judge musical quality.
  A silent valid file is explicitly reported as silent.
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
The current suite has 81 passing tests, including cancellation/replay, file-panel
stalls, independent import copies and measured loudness calibration.
Tests also include captured native Logic parameter trees. Live Logic 12.3.1
acceptance used a newly created empty project: selection, mute/solo, pan,
volume, plugin opening/view changes, Apple EQ and StudioAI parameter edits.
Additional native acceptance imported calibration audio, inserted Gain, split a
region and exported a measured WAV. The phone was paired over its private VPN
and successfully inspected the saved Mac project. These are separate acceptance
checks; they do not establish a complete autonomous song mix or every plugin.
The Blender 5.2.1 smoke fixture was actually rendered/exported on the build Mac.

Relevant primary documentation: [Apple UI scripting](https://developer.apple.com/library/archive/documentation/LanguagesUtilities/Conceptual/MacAutomationScriptingGuide/AutomatetheUserInterface.html)
and [Blender scene export](https://docs.blender.org/api/main/bpy.ops.export_scene.html).
