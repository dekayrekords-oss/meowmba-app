# Build 112 — MaleCNS remote avatar integration

Build 112 (1.3.81) is installed in place over Build 111 on SM-F966U1. Assembly,
signing, retained runtime assets, 75 focused Android host tests, 23 Python bridge
tests and the live Mac-to-Meow sensory checks passed. **Phone pairing and live
avatar acceptance remain pending.** The device test stopped at its initial
"Pair the computer bridge first" precondition, before changing neural settings.
The phone then disconnected; the user explicitly said to continue without it.
Do not report MaleCNS as enabled or verified on the phone yet.

## What changed and why

The new `MALECNS_REMOTE` substrate keeps the retained 176,422-neuron graph on
Meow. Android sends six bounded sensory scalars through the existing paired
computer's pinned TLS/HMAC Desktop Bridge. The computer uses the owner's SSH
configuration to a request-driven Java worker on Meow; no new public neural
endpoint is created. This route requires both the paired computer and Meow.
The app sends no raw camera frames, audio or chat through this feature.

`MaleCnsWorker` uses the four hash-pinned model classes from the previous audit,
including its independent observer RNG and deterministic reset correction.
`prepare_malecns_worker.py` compiles only those classes and the worker, copies
the MIT notice, and verifies source and data identity before preparing a runtime.
The 23 MB graph stays outside the app checkout. Its provenance and modeling
limits are in `docs/neural/MEOW_MALECNS_MODEL_PASS_DOWN_2026-09-13.md`.

Each request advances exactly 50 ms of neural time (100 half-millisecond steps).
The brain does not catch up missed wall time. One worker and a 512 MiB Java heap
bound the host workload. The bridge permits only one in-flight request, reuses
the worker, rejects invalid scalar/sequence/session inputs, and stops it after
15 seconds without requests. Closing SSH stdin ends the remote Java process.
Responses echo a session and sequence; exact duplicates do not advance twice.
The idle timer uses a generation lease so an expired timer cannot stop a newer
request. A stop for an old session cannot stop its replacement.

`MaleCnsRemoteCore` never blocks Android's controller thread. It permits one
asynchronous request, at most five per second, and uses the original input's
monotonic age to reject replies older than 1.8 seconds. Failures back off for
2.5 seconds. New sessions and cancellation prevent late replies after a reset,
network switch, hidden avatar, backgrounding, or severe heat. Classic behavior
stays warm and automatically takes over while the remote model is unavailable.
Shadow still publishes Classic; remote wing signals require Neural control.
Hidden/resumed input buffers are cleared to avoid replaying old touches.

Food stimulates named sugar GRNs, touch stimulates the grooming inputs, looming
stimulates LC4/LPLC2, and motion stimulates LC10a populations. Measured MN9,
descending/grooming/escape, DNg02 and turning activity is mapped through bounded
authored readouts to existing attention, actions and wing presentation. Neural
feeding can request `content_smile`, with existing busy/ownership/cooldown
guards. These are avatar retargeting choices, not biological muscle commands.
Pet care remains optional and its existing rules remain authoritative.

Neural setup explains the connection and data boundary. Brain activity displays
actual measured feeding/escape/grooming/wing population rates, connection state,
remote compute and the separate neural clock. It introduces no render timer.
The local trace switch is disabled for MaleCNS: a local feature trace lacks the
remote state needed for honest replay. The separate synthetic phone benchmark
now explicitly selects only RATE/SPIKING instead of mislabeling those runs as
new biological substrates when the enum grows.

## Deployment paths and rollback

- Model source: `/home/prog/research/malecns-2026-09-13`.
- Compiled worker: `/home/prog/research/malecns-worker-112`.
- Java: `/home/claude/.local-toolchain/jdk-21.0.12+8/bin/java`.
- Mac paired helper: `~/.meowmba-bridge/runtime-112/meowmba-bridge.py`, version
  0.10.0; launch agent `com.meowmba.paired-bridge`. The unrelated older
  `com.memba.desktop-bridge` agent was not edited.
- Existing paired helper config retains its certificate, pairing and settings;
  added `malecns_enabled` and a fixed owner-side `malecns_worker_argv`.
  Backup: `~/.meowmba-bridge/config.before-build112.json` (private).
- USB: `bash scripts/connect_desktop_usb.sh` restores port 8765 forwarding
  without changing pairing. Network mode uses the same pinned certificate.

To disable the integration, choose Classic or another neural substrate in the
app. To remove the worker route, set `malecns_enabled=false` in the computer
helper config and restart that helper. Never clear app data, overwrite pairing,
disable certificate checks or remove private model assets to repair connectivity.
Do not publish Android source; use only the canonical private Meow Git repo.

## Verification and installed artifact

The focused Kotlin remote tests cover response identity/finite bounds, one
request in flight, freshness, reset sessions, late-result rejection, fallback
recovery, thermal suspension, food events and visual ownership. Five Python
bridge checks pass for malformed input, disabled execution, process reuse,
session-specific stop, timer races, malformed replies and no queue under load.
The 18 existing bridge control tests pass.

The broader neural run exposed an old enum/factory mismatch: the recorded replay
test iterated every substrate but replayed every non-RATE case as SPIKING. It
already misrepresented BANC. The replay check now instantiates each of the three
local cores explicitly; remote replay stays in its separate stateful harness.
Both synthetic comparison reports explicitly enumerate RATE/SPIKING, so a new
enum entry can no longer receive a mislabeled spiking benchmark result.

A live, certificate-verified, HMAC-signed Mac bridge probe reached Meow for six
requests: observed MN9 rates peaked at 100 Hz; round trips ranged 90.34–1616.34
ms including worker startup. Unsigned input received HTTP 401. Repeating the
same sequence returned the identical response without another neural step.
The worker was stopped after the probe. Evidence is in
`build-out/habitat/build112/bridge-acceptance.json`.

The opt-in `MaleCnsDeviceTest` (`maleCnsLive=true`) exercises the actual ViewModel,
paired transport, food/touch motor rates, phone controller p95 under 20 ms and
hidden wing-output suppression. `activate=true` retains the verified neural
selection only on success; ordinary invocation restores earlier choices. Pet
care state is restored. Camera/microphone preferences are checked unchanged.

Final server assembly at source commit `776eb0d` passed with 75 tests, zero
failures/errors/skips. This is the focused neural/BANC/setup suite, not a claim
that the entire app suite ran. The earlier focused Mac run passed 12 tests.
The final server command was:

```sh
JAVA_HOME=/home/claude/.local-toolchain/jdk-21.0.12+8 \
PATH=/home/claude/.local-toolchain/jdk-21.0.12+8/bin:$PATH \
./gradlew :app:testDebugUnitTest --tests 'com.example.meowmba.neural.*' \
  --tests '*BancExperimentalCoreTest' --tests '*CompanionSetupUiTest' \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  -PprebuiltDebugNativeDir=/home/claude/meowmba-fruit-fly/.native-preview \
  --console=plain
```

The install used `adb install -r`, preserving production data. Both app and test
APK installs returned Success. Package inspection reported versionCode 112,
versionName 1.3.81, lastUpdateTime `2026-09-13 20:27:44` (phone local time).
The existing cat avatar selection was preserved; no forced fly selection.

- App: `build-out/habitat/meowmba-112.apk`, 441,901,342 bytes; SHA-256
  `7b861d0c9ef77824bab0980b0ce43a98262569fe802af04e85303be107e9cd9c`.
- Test APK: `build-out/habitat/tests112.apk`, 2,736,070 bytes; SHA-256
  `ae44fc06943049dd336523a0e509b6ba67accabebf168fa123b9792d3a182cbc`.
- Signing certificate SHA-256:
  `b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff`.
- All 53 protected runtime entries (native libraries, bundled models, pose,
  acceleration, Sherpa/VAD/speaker entries) match Build 111 by CRC and length.
- `scripts/apk_delta.py` reconstructed byte-identical APKs using a 4,239,589-byte
  app patch and a 153,536-byte test patch. Avoid sending the entire APK over VPN.

After the user deferred phone work, `scripts/neural/verify_malecns_bridge.py`
completed 124 sensory requests across quiet, food, touch, looming and combined
food/touch episodes using the installed TLS/HMAC helper and real Meow worker.
It verifies the exact local certificate, reads the existing private pairing
record without printing it, and never changes pairing. This is a Mac self-client;
it does not establish or prove Android pairing.

Observed population peaks: feeding MN9 100 Hz, touch/grooming DNg62 200 Hz,
loom/escape DNp01 480 Hz; combined food/touch yielded 90 Hz feeding and 210 Hz
grooming. Quiet output populations stayed zero. Round-trip median was 145.077 ms,
p95 187.392 ms, maximum 1518.113 ms including cold starts. None exceeded the
phone's 1800 ms freshness boundary in this short run. Worker compute maximum
was 142.120 ms. These are measured desktop/network timings, not phone CPU or
animation frame timings. Duplicate requests were cached without another neural
step; restarting reproduced the first food response exactly apart from timing
and request identity. Unsigned requests returned HTTP 401. No worker remained
on Meow after the test's session-specific stop.

Reproduce while the phone's remote brain is inactive (one worker is shared):

```sh
python3 scripts/neural/verify_malecns_bridge.py --run-live \
  --output build-out/habitat/build112/bridge-sensory-acceptance.json
```

Sanitized summary: `docs/neural/BUILD_112_VERIFICATION.json`. Full numeric host
report and original device precondition failure are retained in the ignored
`build-out/habitat/build112` folder. Do not publish the pre-install screenshot:
it includes an unrelated private caption. No new phone visual acceptance was
completed for the remote controls.

## Teach Meow: connectivity diagnosis and next phone check

The current phone has no saved Desktop Bridge pairing, even though the Mac has
an older private pairing record. An installed APK and an authenticated Mac
self-client are not proof of a paired phone. Preserve that old record until
proper one-time-code pairing is performed; never inject a secret into phone
preferences or bypass the certificate/HMAC checks.

The last connected phone had only a cellular address and no reachable LAN/VPN
address. The Mac's NetBird address is `100.77.138.81`. The configured ADB reverse
tunnel forwards phone `127.0.0.1:8765` to this Mac. Signed `/rpc` requests can
fall back to USB after pairing; the initial `/pair` request uses the payload's
explicit host. With USB-only connectivity, use `127.0.0.1` in a valid manual
payload while retaining the exact helper certificate fingerprint and temporary
pairing token. That pairing will require the USB tunnel until a reachable
network host is configured by the owner.

For the next authorized phone session:

1. Reconnect/unlock and run `bash scripts/connect_desktop_usb.sh`.
2. Use the existing Desktop Bridge settings and a temporary `--pair` helper
   session to perform code/payload pairing. Back up the old pairing privately
   first. Stop the launch agent while using a temporary pairing process on the
   same port; afterward restore its existing non-pairing launch configuration.
   Do not permanently add `--pair` to its KeepAlive launch arguments.
3. Confirm Ping in the phone settings, then run the installed opt-in test:

   ```sh
   adb -d shell am instrument -w -r \
     -e class com.example.meowmba.MaleCnsDeviceTest \
     -e maleCnsLive true -e activate true \
     com.example.meowmba.test/androidx.test.runner.AndroidJUnitRunner
   ```

4. Only on success does that test retain MaleCNS + Neural. Pull
   `malecns-build112-device.json` from the app's external files directory;
   relaunch the avatar, inspect Brain/settings in landscape, and verify the
   user's mic/camera/avatar preferences. Do not infer app heat from controller
   step time: collect a separate sustained thermal/battery sample.

If Ping fails, fix pairing/tunnel/helper availability before debugging anatomy.
If Ping succeeds but the brain is unavailable, check `malecns_enabled`, fixed
SSH argv, `meow-nb` key access, Java/model paths and exact asset hash. Do not use
the timing-out `meow` LAN alias or change SSH to password retries. A slow reply
correctly falls back to Classic rather than animating from stale input. If tests
mislabel substrates, use explicit model factories instead of treating every new
enum value as SPIKING. If observed-spike logging changes deterministic output,
check the separate observer RNG and reset reseeding from the model audit.

This does not complete sustained heat/battery validation, biological flight
simulation, retinal/odor fidelity, or robot transfer. The user has no robot;
physical control remains quarantined.
