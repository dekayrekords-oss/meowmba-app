# Logic workflow preview — 1.3.71/code75

Installed in place on Samsung SM-F966U1 at 16:18:02, September 9, 2026.
Package/version and signature were verified. No data was cleared. The final
APK is in `/Users/prog/Meowmba-Releases/logic-workflow-2026-09-09/`.

## Implemented

- The paired Mac helper is version 0.9.0, installed outside the writable
  workspace and running through `com.meowmba.desktop-bridge` at login. Existing
  certificate/pairing were preserved. The phone reached it over the private VPN and, after that network failed, over USB.
- `logic.run_goal` coordinates observed menus, region controls, plugin
  insertion/Controls parameters and export dialogs, with up to 96 planner
  steps. Every mutation goes through the existing approval and audit system.
  It uses the configured planning provider; this is not a new local model.
- Signed screen/action/cancel RPCs extend the existing typed edits. Snapshots
  are single-use, short-lived and tied to the current saved project and
  foreground controls. Uncertain operations cannot silently replay.
- Cancellation releases a blocked phone read promptly and records a durable
  helper cancellation marker. In-flight native loops stop between steps;
  already applied changes are recorded, not represented as rolled back.
- Logic now has its own constrained decision schema and validator across local
  and cloud provider paths. The live 1.3.66 test exposed an inherited phone
  gesture schema; a transport-level regression test now exercises the actual
  Logic schema and response parser together.
- Text chat receives concise Logic results rather than raw JSON. Child edit
  receipts remain available without being spoken over the parent workflow.
- A live test found “In Logic Pro set Audio 1 volume to -3 dB” falling into
  phone-volume routing. Named Logic tasks now win before generic phone/project
  commands. Track numbers and decibel requests cannot become phone percentages.
  Ordinary programming/business “logic” is not treated as the DAW.
- Audio imports are staged as separate, hash-checked copies because Logic can
  modify imported WAV metadata. Paths remain within declared workspaces.
- WAV verification includes actual decoded samples, frame count, format,
  hash, silence, peaks, RMS, DC and full-scale samples. Optional libebur128
  1.2.6 supplies integrated LUFS, loudness range and true peak on this Mac.
  Missing library and unsupported channel layouts report unavailable values.
- Export completion requires an actual export dispatch followed by a new,
  non-silent verified WAV. Metronome state is included in export planning.

- USB fallback connects through the local adb tunnel when network connection
  setup fails. It preserves certificate pinning and HMAC pairing, remembers the
  working route, and never retries an already dispatched request. The Mac setup
  script is `scripts/connect_desktop_usb.sh`; rerun it after adb/cable resets.
- Cloud planning accepts one validated function call or one validated JSON
  content object. Malformed proposals get one format correction; invalid
  control proposals get at most two corrections before stopping. Executed or
  uncertain mutations are never replayed as corrections.
- Exact named-track dB requests use observed mixer controls directly, retaining
  approval and read-back. Duplicate track names, conflicting controls, missing
  targets and out-of-range values stop before dispatch. No model call is needed
  for this precise request type.

## Validation and evidence

Android: **2,050 tests, zero failures/errors, five skipped**. Python: **81 passed**.
The final APK signature passed. All **125 native/asset files** are byte-identical
to 1.3.70, preserving the earlier avatar and voice runtimes. The LAN fetch was
attempted but timed out; no remote synchronization, commit or push is claimed.

The phone displayed **“Pong — desktop alive via USB tunnel”** while retaining
its original paired network address. On final 1.3.71, the typed request **“In
Logic Pro set Audio 1 volume to -3 dB”** completed through the normal parent
and specific-edit approvals. The durable helper receipt records before 0.0,
requested -3.0, observed -3.0, state `verified`. An independent native inspection
confirmed -3.0 in both mixer and inspector; chat displayed the verified result
with Speak and Send available. This was the disposable calibration project,
not a user song. Evidence: `phone-usb-ping.json`,
`phone-logic-edit-receipt.json`, `phone-logic-native-readback.json` and
`phone-logic-volume-result.json` in the release folder.

Native Logic 12.3.1 acceptance used only a disposable `Untitled.logicx` project
and generated calibration tones. It covered importing audio, inserting Gain,
splitting the audio region, inspecting the actual plugin chain, changing export
range and filename, disabling the metronome, and producing a WAV through the
native adapter. Earlier 1.3.64 checks covered volume, pan, mute/solo, selection,
Channel EQ and third-party StudioAI parameter adjustments/restoration.

The final clean export is 8 seconds, stereo 48 kHz/24-bit. All **768,000 decoded
channel samples exactly match** the 16-bit source promoted to 24-bit. Integrated
loudness is -15.99465 LUFS; maximum measured true peak is -14.88766 dBTP. The
measurement fixture is not a song or a finished master. Earlier exports exposed
the enabled metronome; those receipts remain as diagnostic evidence.

Final clean WAV SHA-256:
`0a0040f03622690a7f7d4fe39bc706d2cec65b61c005e59cc09b6ec2e07e338f`.

Final APK SHA-256:
`49137db69ef4b5a5ad3e3fa7b4e105f8b01b1c2fae62f013d634b662bc7f3cf4`.

## Limits that remain

- File-panel focus failures are handled by raising the observed Go To sheet
  before confirming. The corrected adapter navigated to the workspace root and
  back to its acceptance subfolder, with both selections observed and the sheet
  closed. Autocomplete/focus changes alone stay **uncertain**, never completed.
  Export destinations accept folders only, preventing accidental selection of
  an existing audio source. Other macOS versions still need acceptance testing.
- Plugin support has no vendor allowlist, but requires exposed Accessibility
  controls or Apple's common Controls view. Custom inaccessible interfaces,
  ambiguous duplicate instances, plugin licensing/installation and every
  vendor's special functions are not universally supported.
- Native file-panel navigation passed in the foreground test process; its
  System Events Automation permission in the background login service has not
  received a separate end-to-end file-navigation test. Staged import copies
  passed unit tests; a staged-copy import still needs native acceptance.
- Complete real-song arrangement, buses/sends, automation writing and artistic
  mix/master quality have not passed end-to-end acceptance. No user song was
  edited. Technical audio measurements do not establish musical quality.
- A supported live traffic feed, all-day background notes, broad inbox/business
  account integrations and real-acoustic voice quality/latency acceptance
  remain open. Existing floating-feature avatars and reliability fixes are
  preserved; this release does not claim every earlier request is finished.
- The phone briefly reached Android thermal status SEVERE during extended
  testing and paused animations as designed. The pause later cleared; no
  thermal protection was disabled. A cool-device performance/voice soak remains
  necessary before claiming production readiness.
- Physical robot actuation remains quarantined. No user-account transaction,
  external message, purchase or physical movement was used in acceptance.
