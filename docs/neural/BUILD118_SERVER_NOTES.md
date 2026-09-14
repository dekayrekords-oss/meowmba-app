# Build 118: choose a MaleCNS server without replacing desktop pairing

The user confirmed that MaleCNS may run on Meow and asked to allow other
servers. The previous runtime sent every MaleCNS request through the general
Desktop Bridge pairing and described every successful connection as Meow.
Build 118 adds an optional, separately authenticated neural server. The
existing paired computer remains the default. A compatible bridge may run
MaleCNS locally or invoke its owner's preconfigured worker on another host;
this is not an adapter for arbitrary HTTP model APIs.

## What changed, how and why

`MaleCnsServerManager` owns one encrypted preference,
`malecns_server_selection_v1`. It contains the selected route and, when
configured, the separate server credentials. It does not write any existing
`bridge_host`, `bridge_port`, `bridge_name`, `bridge_cert_sha256` or
`bridge_secret` preferences. Returning to the paired computer also preserves
the saved custom-server record. The public selection/UI state contains only
the choice, display name, endpoint and paired flag. It cannot reveal codes or
secrets. A corrupt custom record fails closed instead of silently sending
sensory input to the desktop.

Connecting a new server decodes its owner-supplied pairing payload, pins the
certificate, exchanges the one-use code for a fresh 256-bit secret, and probes
one neutral FULL graph step. The actual wire parser still checks protocol 2,
176,422 neurons, 25,862,574 pairs, 125,024,863 anatomical counts, the exact
Build 115 graph SHA-256, all measured body fields, finite values, session and
sequence. Only a successful probe is saved/selected. Probe failure preserves
the previous phone configuration. A fresh pairing can already have registered
this phone on the candidate helper before its model probe fails; it does not
change the previous desktop helper. Fix that candidate's graph and open a
fresh pairing window before retrying.

If the supplied certificate matches an existing trusted desktop/custom
identity, the app reuses its established credentials, including when the
address changes. This is deliberate: the helper supports one paired phone
secret, so blindly re-pairing the same helper would invalidate desktop tools.
Every subsequent request still pins that exact certificate and signs the
request with HMAC-SHA256. A known desktop helper which has lost its pairing must be
repaired through its original pairing route; do not silently rotate a desktop
secret in the neural setup flow. A custom-only helper may use the fresh code
supplied by explicit Connect after an authentication/forgotten-pairing
rejection. A network error never triggers secret rotation.

`DesktopBridgeManager` now stages pairing credentials independently of saving
and can construct a transport bound to an immutable credential snapshot.
Existing desktop `pair`, discovery pairing and general RPC APIs retain their
behavior. A neural core's step and stop always target the same identity,
even after the selected server changes. Root integration pauses remote work
while a probe/change runs and recreates the remote core afterward; resetting
only its sequence is insufficient because that would retain its old transport.
Every probe sends a session-specific stop in non-cancellable cleanup. Its
transport has the existing bounded stop timeout. The existing helper stops
idle workers after 15 seconds as a fallback.

Android endpoint construction now accepts DNS, IPv4 and IPv6 host forms
without allowing URL credentials, paths, query strings or fragments in a
host field. Redirect following is explicitly disabled. Existing pinned
network/USB fallback still retries only connection setup, never a POST whose
response was lost. The fingerprint check remains authoritative on every
connection; no permissive trust manager was added. Server response text is
mapped to fixed, actionable UI errors rather than echoed back with potential
credentials or arbitrary remote text. `MaleCnsRemoteCore` describes the
actual selected server, removing the hardcoded claim that every host is Meow.

The helper adds `--advertise-host HOST` for a DNS/VPN address reachable by the
phone. It changes only the address printed in its pairing payload. The
listener, certificate, HMAC, pairing window, code, discovery and defaults
remain unchanged. Invalid hosts fail during argument parsing, before
certificate creation or network startup. Omitting the flag preserves LAN
auto-detection. This patch does not restart or reconfigure the live service.

## Integration API

- `MaleCnsServerManager(storage)` uses Keystore-backed storage.
- `selection()` returns `MaleCnsServerSelection(useDedicated, serverName,
  endpoint, paired)` without secrets.
- `createTransport()` returns a snapshot transport for a new remote core.
- `pairServer(payload, phoneName)`, `testConnection()` and
  `usePairedComputer()` are suspend functions returning `Result<String>`;
  they serialize changes and perform storage/network work on IO.
- Pause the current remote core before those operations; suppress remote ticks
  until they finish, then recreate the current remote substrate on both
  success and failure. Do not change the user's selected neural mode or
  camera permission. Test probes use the one-worker bridge slot temporarily.

## Run the compatible server on another machine

The source and assets are private in this project. Transfer only to an
owner-authorized server. Keep helper code/configuration outside any writable
phone workspace. Meow's verified Build 115 graph, worker and provenance remain
the reference; see `MEOW_FULL_GRAPH_INTEGRATION_115_PASS_DOWN.md`. These
commands are templates: replace `/srv/malecns` and `/opt/jdk-21` with the
actual owner-controlled paths. They were not executed on another server by
this change.

1. Use Python 3.10+, OpenSSL and a compatible JDK (the worker compiles for
   Java 17; Meow uses Java 21). Transfer the complete `bridge/` directory,
   not just its entry script; imports such as `malecns_control.py` and the
   existing desktop adapter modules must stay alongside it.
2. Transfer the audited upstream source at revision
   `6cfa30175003ef25da68a237d5eda958f8047b82`, including its retained FLYB
   asset. Transfer the full Build 115 asset and
   `docs/neural/MALECNS_FULL_GRAPH_115_MANIFEST.json`. Verify SHA-256
   `8860e68b59e454d8d4e8bd601dd7a3cb9049d18516f1ea8df34eabe1ae400d08`
   and size 79,963,380 bytes for the full compressed asset. Preserve the
   MIT simulator license and CC BY 4.0 dataset attribution.
3. Build a fresh verified worker directory; do not overwrite the upstream:

   ```sh
   python3 scripts/neural/prepare_malecns_worker.py \
     --source /srv/malecns/source \
     --output /srv/malecns/worker-118 \
     --jdk /opt/jdk-21 \
     --full-asset /srv/malecns/full.flyb.gz \
     --full-manifest docs/neural/MALECNS_FULL_GRAPH_115_MANIFEST.json
   ```

4. Merge the following owner-controlled fields into that server account's
   `~/.meowmba-bridge/config.json`. Preserve existing unrelated configuration
   and pairing/certificate files. `malecns_worker_argv` is a fixed argv array;
   never accept paths or commands from the phone. The worker can be local,
   as below, or reached through the owner's existing authenticated SSH argv.

   ```json
   {
     "name": "My fly server",
     "port": 8765,
     "workspace_roots": [],
     "malecns_enabled": true,
     "malecns_worker_argv": [
       "/opt/jdk-21/bin/java", "-Xmx1g", "-XX:ActiveProcessorCount=1",
       "-cp", "/srv/malecns/worker-118", "MaleCnsWorker",
       "/srv/malecns/source/src/main/resources/connectome/malecns-v1.0.flyb.gz",
       "/srv/malecns/full.flyb.gz", "/srv/malecns/worker-118/full-worker.properties"
     ]
   }
   ```

5. Verify the actual worker, both graph profiles and its compute budget:

   ```sh
   python3 scripts/neural/verify_malecns_worker.py \
     --java /opt/jdk-21/bin/java --classes /srv/malecns/worker-118 \
     --retained-asset /srv/malecns/source/src/main/resources/connectome/malecns-v1.0.flyb.gz \
     --full-asset /srv/malecns/full.flyb.gz \
     --full-properties /srv/malecns/worker-118/full-worker.properties \
     --full-manifest docs/neural/MALECNS_FULL_GRAPH_115_MANIFEST.json \
     --report /srv/malecns/worker-check.json --ticks 40
   ```

   FULL's measured compute target remains 300 ms with Android requests at
   most every 400 ms; this is not real-time whole-animal flight simulation.
6. On the chosen helper, open a pairing window using its reachable address:

   ```sh
   python3 bridge/meowmba-bridge.py --pair --advertise-host fly.example.org
   ```

   Use its actual private DNS/VPN address, not the example. Keep it reachable
   from the phone through the owner's network policy. This flag does not
   open a firewall, create a tunnel or make an IPv4 listener accept IPv6.
   Android accepts IPv6 if the compatible endpoint actually serves it.
7. In Neural setup choose the separate-server action, paste the complete
   **Manual fallback payload** printed by the helper, and press Connect.
   Do not paste only the short code, raw JSON or a normal URL. A successful
   graph probe identifies that server in setup. Select MaleCNS/Neural to
   use it, or Shadow to observe without body control. Use Test connection
   for a fresh check and Use paired computer to restore the default route.

## Tests and debugging lessons for Meow

Added 16 pure manager tests covering default routing, separate-host selection,
full-profile verification, neutral probe input, stop cleanup, failed auth/
probe/persistence, same-certificate reuse, fallback selection, snapshot-bound
stop routing, corrupt credentials/records, cancellation, invalid hosts and
redacted failure text. Added the Android endpoint compatibility/injection
regression alongside existing protocol vectors. Root owns the Gradle run and
records final Android results in the Build 118 acceptance handoff.

Four local Python tests passed for valid DNS/IP/IPv6 inputs, invalid
URL/credential/command syntax, payload-only host override and rejection before
network/certificate startup. Existing bridge/model tests must remain in the
integrator's acceptance run. No real phone connection, alternate-server
installation, sustained heat/battery or robot acceptance is claimed here.

For a seemingly inert remote brain: check the selected endpoint/paired flag,
then Test connection, worker config/graph manifest, Neural versus Shadow,
brain freshness, stage visibility and thermal tier. For a known desktop server which
forgets credentials, repair its original pairing rather than weakening TLS
or overwriting desktop secrets. A custom-only server can be repaired with
Connect and a fresh pairing-window payload after an authentication rejection. For a new candidate failing its first graph
probe, check `malecns_enabled`, fixed argv, full asset/properties and the exact
manifest before opening a new pairing window. For stalled switching, inspect
the ViewModel busy gate and creation of a new transport/core, not just sequence
reset. Never print the encrypted record, pairing payload or raw remote errors
into screenshots, Git, logs or passdown files.


### First-build endpoint validation correction

The first integrated Android build exposed two failing hostile-host tests.
Java's multi-component URI constructor does **not** treat its host argument
as an opaque host: `user@fly.example` becomes a real user-info component,
`fly.example/path` becomes a different path, and `?`/`#` become query/fragment
components. Checking only `uri.host != null` therefore accepted all four.
This behavior was reproduced directly with the installed JDK, without making
network requests. The correction validates DNS labels or the restricted IPv6
literal syntax before URI construction, then verifies exact host, port, path
and the absence of user-info, query and fragment afterward. Every original
hostile-input assertion remains, with additional percent-encoding, embedded
port, bracketed-DNS, control-character and backslash cases. Root reruns the
actual Android suites; do not weaken these tests or rely on URI construction
alone as a security boundary in future endpoint editors.
