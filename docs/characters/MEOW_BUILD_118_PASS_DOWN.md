# Build 118: portable MaleCNS server and restored face tracking

## Requested outcome

The owner wants MaleCNS to run on Meow or another server, and reports broken
face tracking. This build adds a separate optional neural server and repairs
three confirmed camera-pipeline defects. It does not move the entire graph onto
the phone, change neural mathematics or automatically turn on its camera.

## What changed and why

The existing paired computer remains the default. Neural setup can independently
pair and verify another compatible server, test it, or return to the computer.
Pairing authenticates the server and checks the exact full graph before saving
its encrypted record. This never replaces desktop automation credentials.
A compatible server runs the repository's bridge and MaleCNS worker; arbitrary
LLM endpoints do not implement this protocol. The helper's new `--advertise-host`
option supports an owner-selected DNS, VPN or LAN address in its pairing payload.
It changes neither listener exposure nor authentication defaults.

Each remote core snapshots its server identity. Pairing/testing pauses live remote
updates, then recreates the core so old responses and motor output cannot cross
servers. Remote ticks recheck the busy flag while holding the controller lock.
Reset also creates a fresh transport, allowing recovery after server credentials
change. UI pairing input is transient and hidden; status never includes secrets.
The Brain monitor uses the selected server's name and pairing state. A separate
server can work even when the ordinary desktop bridge is not paired.

Camera preview dismissal previously unbound all CameraX use cases but left the
tracking-request flag true. Resuming then returned early without analysis.
Detaching a preview now replaces the session with authorized analysis only.
Normal face detection also requested no landmarks while mouth/talker selection
required them; normal mode restores mouth landmarks, with cheap follow mode
unchanged. A completed face result now publishes before optional pose warmup,
while the shared image remains alive until both readers finish. Existing
freshness bounds still reject stale observations.

The avatar remains visible under Brain and partial chat. The old camera policy
used the bare-face flag and unnecessarily stopped analysis under those panels.
Tracking now follows avatar visibility while full menus, background and hidden
stages pause it. Returning resumes only the user's existing ON choice. A compact
camera control shows actual OFF, starting, waiting, no-face, tracking, paused,
cooling or error state; it never opens a video preview. Retry remains an explicit
user action and cannot override OFF or thermal protection.

## Detailed teaching notes

- [BUILD118_SERVER_NOTES.md](../neural/BUILD118_SERVER_NOTES.md): encrypted selection,
  protocol/identity isolation, portable worker setup, advertised-host option and recovery.
- [BUILD118_FACE_PIPELINE_NOTES.md](BUILD118_FACE_PIPELINE_NOTES.md): camera bind,
  detector completion, image ownership, landmark/status fixes and debugging.
- [BUILD118_SERVER_UI_NOTES.md](BUILD118_SERVER_UI_NOTES.md): controls, pairing/privacy,
  selected-server monitor and native UI verification.

## Build and verification

Build 118 is complete and ready on Meow. **247 Android checks across 34 suites
passed**, with zero failures, errors or skips. Four Python pairing-host checks
also passed on Meow. Both debug and instrumentation APKs assemble and verify.
Built source: `d1a544b3d162aa34aaf8ba083d59dfe41f1c090d`. Final Gradle log:
`/tmp/meow-build118-acceptance.log` on Meow. Machine-readable evidence:
[BUILD_118_VERIFICATION.json](BUILD_118_VERIFICATION.json).

Coverage includes 10 actual VisionManager lifecycle tests with mocked CameraX
hardware, five asynchronous face/pose completion tests, seven camera privacy/VM
checks, five native camera status UI checks, 16 neural server manager checks,
eight native server setup checks, protocol/fallback, neural replacement,
full-graph parsing/control, existing face geometry, Brain, vision routing and
drawer regressions. All 19 existing VisionPipeline tests now reach their original
assertions. This is a focused acceptance suite, not the entire repository suite.

Native previews were reviewed for compact/landscape server setup, failure without
replacing the current computer, and the camera Retry control at 320 dp/1.4x text.
They are stored in `build-out/habitat/build118/server118/`; seven updated Brain
previews are retained under `brain114/` within the same folder. These are
host-native renders. They do not prove camera hardware or network connectivity.

ADB showed no phone both before work and after verification. Last verified phone
installation remains Build 112. Build 118 is **not installed**. Actual face
tracking, camera indicator shutdown, phone-to-server pairing/connection and
sustained heat/battery checks remain physical-device work. A host camera test
cannot establish a hardware diagnosis. No phone settings or data were changed.

### Artifacts and installation handoff

Final archive on the user-designated `prog@100.77.97.236` (`meow-nb`) server:
`/home/prog/research/meowmba-build118-d1a544b/`.

- `meowmba-118.apk`: 457,439,380 bytes; SHA-256 `2286ba288c2fab8dfb9038914a6967c8a4a1457b7d1b3a01a74e8372628691b5`.
- `tests118.apk`: 2,740,730 bytes; SHA-256 `0a342805441dc9f40af2e75eace7764d1ff2e226ea595640323eb80c7e56ebe0`.

Both packages verify with signer SHA-256
`b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
Application identity stays `com.example.meowmba`, debug versionCode 118,
versionName 1.3.81. All 53 protected runtime entries and all 19 painted backgrounds
match their prior hashes. No full MaleCNS graph was added to the APK. Comparing
Build 117 and 118 ZIP entry lists found no added or removed entries; the smaller
APK does not reflect dropped assets. Only code/signature entry sizes changed.

Complete APKs, signature logs, verification JSON and 11 native previews are on
Meow. The Build 115-based transfer deltas are also retained locally and verified:

- `app118.patch.gz`: SHA-256 `c01ec4e4a664ab5187be0ab88d7bbd62d9370403e5a00c75b878ede4188d5520`.
- `test118.patch.gz`: SHA-256 `8a0876ca0fba645d775fcbdf4d1eb3be79eb72fc26a481162b7d2df497e9f512`.

Use the archived complete APK, or apply `scripts/apk_delta.py` only against the
exact Build 115 base and verify the target hash. Update installation must keep
app data, camera OFF choice and pairing; never uninstall or clear app data to
work around a signing mismatch. Runtime assets remain untracked and preserved
in the private build checkout. No Android source was sent to public GitHub.

After installation, open Settings → Neural / Lumen → MaleCNS and choose the
paired computer or another compatible server. Connect & verify checks its full
graph; Test current server verifies saved credentials. Server changes preserve
the neural mode choice. Select Neural control when the network should influence
the avatar; Shadow remains observational. Detailed portable helper preparation,
model setup and pairing commands are in the server notes linked above.

Use the home camera button to enable tracking if it is off. A camera permission
grant alone does not turn it on. The status distinguishes waiting for frames,
looking for a face, following a face and thermal suspension. ERROR offers explicit
Retry; it rebinds without changing the saved ON choice. Opening Brain keeps the
visible avatar's authorized tracking; full settings pause it until returning.

### Reproduce acceptance

Use Meow's existing `/home/claude/meowmba-fly-habitat` checkout with
`JAVA_HOME=/home/claude/.local-toolchain/jdk-21.0.12+8`, its `bin` on `PATH`, and
`ANDROID_HOME=/home/claude/Android/Sdk`. Gradle tasks:
`:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`, with
`-PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview`.
Filters under `com.example.meowmba` are `*Camera*Test`, `*Face*Test`, `*Vision*Test`,
`*MaleCns*Test`, `*Brain*Test`, `*DesktopBridge*Test`, `*BridgeTransport*Test`,
`neural.NeuralServerReplacementTest`, `CompanionSetupUiTest`,
`CompanionSceneBackgroundPauseTest`, and `*Drawer*Test`.
Run `python3 -m unittest discover -s bridge -p test_pairing_host.py -v` for the
advertised-host helper. The root's local-only archive verifier is
`/tmp/meow-verify-build118.py` on Meow; it refuses to overwrite a finalized report.

The live Meow bridge and neural worker service were not restarted or reconfigured.
The updated portable bridge script is committed in the private repository for
installation on a selected host. Existing Meow clients retain their credentials.

## Integration findings and lessons

The first run compiled both APKs and passed 234 of 247 checks. All new camera
lifecycle, face/pose completion, status UI and privacy/visibility tests passed.
Two host-validation regressions caught actual URI behavior: Java's component
constructor can reinterpret an alleged hostname as user-info, path, query or
fragment. Checking only a non-null parsed host was insufficient. Validation now
checks DNS/IPv6 syntax first, then verifies every final URI component; hostile
input assertions are retained and expanded.

Ten existing VisionPipeline checks and one AgentBrain ViewModel check failed
before assertions because a mock Context could not initialize unrelated Room
and AgenticRuntime state. Existing scoped Android mocks now isolate that setup,
restore the singleton/log/clock afterward, and cancel ViewModel scopes. The
original vision routing, model selection and tool approval assertions remain.
Do not weaken production startup or ignore these checks to hide fixture errors.

Initial standalone server previews used an unthemed Activity background. Their
hosts now use the app's background Surface and content color so visual review
matches Settings. Production controls were already inside themed cards; this
was corrected in the preview fixture, not by changing app colors.
