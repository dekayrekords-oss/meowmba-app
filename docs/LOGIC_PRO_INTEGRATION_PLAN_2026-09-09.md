# Logic Pro integration: verified current state and delivery plan

## Current state — native adapter implemented, full DAW workflow still open

Phone 1.3.63/code67 was installed and verified on the connected SM-F966U1.
The next source revision adds helper 0.8 and four Android tools: `logic.inspect`,
`logic.edit`, `logic.receipt`, `logic.verify_audio`. Registration, model tool
permissions and deterministic inspection routing are wired. The edit approval
shows all bounded target fields instead of truncating project/parameter names.

A new empty two-track project at `/Users/prog/Music/Logic/Untitled.logicx` was
created for native acceptance in Logic Pro 12.3.1. No user song was used. Its
pre-test copy and detailed operation receipts are at
`/Users/prog/Meowmba-Releases/logic-2026-09-09/`.

Verified native results:

- Exclusive track selection; mute and solo on/off with actual state read-back.
- Pan to -16 and back to 0; volume to -12 dB and back to 0 dB.
- Opening an occupied plugin and changing Editor to Controls view.
- Channel EQ low-shelf gain adjustment and restoration.
- Third-party StudioAI output trim to -2 dB and restoration to 0 dB.

Measured quirks now covered: AXPress may acknowledge without changing a custom
button; controls need settling after dispatch; track descriptions acquire mute/
solo suffixes; implicit solo mute reports -1; unsupported AX attributes can return
misleading defaults; AXFrontmost can disagree with Launch Services; AXIncrement
moves ten native units while AXSlider assignment nudges one. The standard plugin
row's AXValueIndicator accepts absolute values, verified with StudioAI. Native
references are released between snapshots instead of accumulating across nudges.

Typed edits bind to a fresh, consumed snapshot, exact saved project and control,
stopped transport and current foreground app. Grouped/write-mode controls are
excluded, other control changes invalidate verification, and native clicks
require a hit-test match to the intended element. Durable write-ahead receipts
prevent repeated edits after disconnection or helper restart. No remote scripts,
program names, arbitrary coordinates or shell commands are accepted.

Plugin identity comes from the actual channel, occupied plugin slot and header,
not a guessed window title (Logic titles plugin windows with the channel name).
Apple's [Controls view](https://support.apple.com/en-us/101781) provides the common
parameter interface. There is no vendor allowlist, but this does **not** prove all
plugins work: unsupported custom controls, missing metadata, duplicate instances
and ambiguous names are excluded. Native parameter values and display units are
both reported; they are not interchangeable.

The new WAV verifier decodes actual PCM/float samples and checks format, complete
frames, duration, unchanged source hash, silence, sample peak, RMS, DC offset and
full-scale samples. It does not calculate LUFS or true peak, generate a bounce,
judge artistic quality or certify a finished master. FFmpeg binaries on this host
were unavailable after installation attempts; this verifier has no FFmpeg dependency.

Still open: region/timeline editing, buses/sends, plugin insertion/replacement,
automation curves, audio-informed mix planning, integrated loudness/true-peak
analysis, mastering workflow, bounce delivery and full song acceptance. A native
adjustment is bounded to 20 seconds, but phone cancellation cannot yet stop it
mid-flight; inspect the receipt before another edit. No Mac helper was previously
paired here, so source/native tests alone do not establish phone-to-Mac operation.

Adjacent reliability: project allocation now journals its reservation before
Room insertion and reconciles it after restart. A crash after database commit or
workspace failure cannot silently create another project on retry. Corrupt
journals stop creation rather than resetting duplicate protection.

## Supported Apple approaches

Apple's [Logic Remote documentation](https://support.apple.com/guide/logicpro/logic-remote-control-pro-projects-lgcp2cf110e3/mac)
describes controlling playback, navigation, recording, patches, mixing and
automation from an iPhone or iPad. That is not an Android API for Meowmba to
impersonate. The Android phone needs its paired Mac helper.

Apple's [control-surface overview](https://support.apple.com/en-au/guide/logicpro-css/ctls036b3e21/mac)
describes supported surfaces for transport, mixing, recording and automation.
[Controller assignments](https://support.apple.com/guide/logicpro/controller-assignments-overview-ctls71c31487/10.7/mac/11.0)
can map channel-strip, plug-in and other parameters. A future typed MIDI/control
surface adapter should use a host-approved mapping, test state feedback and
handle relative/absolute controls explicitly. Do not guess protocols or blindly
write parameter values when the active track/bank is unknown.

Apple's [Mastering Assistant instructions](https://support.apple.com/en-us/108294)
start with a finished mix, add analysis and processing on stereo output, then
allow adjustments and a bounce. This is a useful controlled workflow stage,
not a substitute for editing/mixing the source tracks or evidence that a result
sounds good. [Track automation](https://support.apple.com/en-ie/guide/logicpro/lgcpb1a1ea03/mac)
records parameter changes over time and needs explicit mode/target handling.

## Delivery sequence

1. **Read-only session inventory.** On an explicitly chosen test project copy,
   identify project, tracks, channel types, selection, transport state, tempo,
   sample rate, buses, plug-ins and automation mode through proven UI controls
   and any documented feedback available. Report unknown facts as unknown.
   Never infer current fader values from a dispatched command.
2. **Typed editing contract.** Add narrow operations such as select_track,
   set_volume_db, set_pan, set_mute, set_solo and set_playback_region with target
   identity, expected prior state, bounded values, final-state checks and a
   persistent operation ID. Use a versioned adapter and supported host mappings.
   Missing targets, changed projects and inconsistent feedback stop the plan.
3. **Project preservation.** Work in a user-chosen project copy/alternative;
   verify it exists before edits. Preserve source recordings and existing bounce
   files. Scope approvals to the concrete project and operations, show changes
   before applying, and keep cancellation/undo boundaries visible. Do not add
   a model-generated shell/AppleScript execution shortcut.
4. **Audio-informed mixing.** Analyze explicitly selected exported stems or a
   test bounce for peaks, integrated loudness, true peak and clipping. Use a
   trusted implementation and record sample rate, channels, measured duration
   and source hashes. Propose gain/EQ/dynamics changes tied to these observations
   and user taste, with comparative listening required for quality claims.
5. **Mastering and delivery.** Verify stereo-output insertion, analysis progress,
   settings and bypass comparison. Bounce to a new output path. Verify actual
   audio decoding, duration, channels, target format, file hash and measurements;
   attach the playable artifact. A tap, screenshot, model statement or filename
   alone is not proof of a completed master.
6. **Acceptance.** Use disposable project fixtures with known tracks/buses,
   missing plug-ins, changed track order, manual edits, modal dialogs, expired
   approvals, disconnection and cancellation. Prove target selection and undo
   before trying user songs. Live Logic and music-quality acceptance are open.

## Adjacent media and AI apps

The current `ImageGenerator`, `VideoGenerator` and `MusicGenerator` interfaces
accept text prompts and produce new files. They do not accept an existing
image plus mask, a video timeline, source audio tracks or an edit decision list.
Image/video editing, song mixing and native cross-app AI sessions need explicit
adapters and result verification. The existing phone UI agent can use exposed
controls in installed apps, but account access, upload/download handling,
subscriptions, app changes and final artifact retrieval still determine what
works. Do not equate it with a native frontier AI service connection.

A concrete adjacent integrity defect was fixed in this change: failed/missing
music generation no longer emits procedural tones and calls them the requested
song. Audio/video cancellation propagates through provider and tool layers;
partial local writes are removed. WAV containers must include consistent format
metadata and nonempty complete sample data before saving. These checks do not
measure artistic quality or prove that the provider obeyed the prompt.

## Validation

Python preflight tests cover absent/malformed apps, unrelated bundle IDs,
platform limitations, no subprocess launch, disabled permission checks, and
unknown trust status. Kotlin `LogicProPreflightTest` covers backward-compatible
unknown status and prevents installation/access flags from claiming full Logic
operation. Media regression classes are `MediaGenerationIntegrityTest`,
`RemoteMediaCancellationTest`, `MediaGenerateAudioToolRoutingTest`,
`MediaGenerateVideoToolRoutingTest`, `RemoteMusicGeneratorTest`, and
`RemoteVideoGeneratorTest`. The integrator owns Gradle and release validation.
Native acceptance is listed above; full song and phone-to-Mac acceptance remain open.

## September 9 workflow checkpoint

The 1.3.71 implementation and acceptance supersede the initial unimplemented
capability notes above. See `LOGIC_WORKFLOW_1.3.71_2026-09-09.md` for actual
native import/split/plugin/export tests, exact PCM comparison, pairing/login
service setup, cancellation and the remaining real-song/plugin limits.
