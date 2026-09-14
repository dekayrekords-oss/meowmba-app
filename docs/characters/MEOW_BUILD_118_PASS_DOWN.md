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

Pending the final focused run. Root owns builds and private synchronization.
The phone was checked with ADB and is disconnected; last verified installation
is Build 112. Build 118 cannot establish physical face tracking, actual camera
indicator behavior, live connection from a phone to another host, or sustained
thermal performance without a device. Continue authorized work without claiming
those checks or altering saved phone choices.

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
