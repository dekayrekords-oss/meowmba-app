# Full MaleCNS server package

This owner-hosted package runs all 176,422 annotated MaleCNS v1.0 neurons,
25,862,574 directed weighted pairs and 125,024,863 anatomical synapse counts.
It includes both graph assets, verified Java 17 worker classes and source,
provenance, the upstream MIT license, HTTPS service, installer and acceptance
scripts. It contains no Android source, API keys or pre-paired credentials.

This is a point-neuron LIF simulation of the available annotated graph. It
is not a validated whole animal, a FlyWire reconstruction, consciousness,
or a MuJoCo body simulation. It advances 50 ms of neural time per request;
the app requests at most every 400 ms and interpolates avatar motion locally.
The service accepts six abstract sensory values, not audio or video.
Anatomical synapses are aggregated weights, not separate synaptic compartments.

Dataset attribution: male Drosophila central nervous system / MaleCNS v1.0,
HHMI Janelia FlyEM, University of Cambridge, MRC Laboratory of Molecular
Biology and Google Research, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
See the [official dataset](https://male-cns.janelia.org/). Exact source URLs, hashes,
included/excluded counts and transformations are in the bundled provenance.
Simulator: blendi-remade/fly-brain-minecraft, revision
6cfa30175003ef25da68a237d5eda958f8047b82, MIT; see worker/UPSTREAM_LICENSE.txt.
Meow adds deterministic reset repair, the full graph and protocol adapter.

## Install on an owner-authorized Linux server

Requirements: Python 3.10+, OpenSSL, Java 17+, systemd user services, about
400 MB free disk and 1.5 GiB available memory. Assets are included; no model
downloads, pip packages, GPU or cloud subscription are needed at runtime.
Verify the archive SHA-256 supplied with it before extracting it.

```sh
tar -xzf meow-neural-server.tar.gz
cd meow-neural-server
python3 install_neural_server.py --java /path/to/java \
  --bind 192.168.1.69 --advertise-host 192.168.1.69
systemctl --user status meow-neural.service
systemctl --user kill --signal=USR1 --kill-whom=main meow-neural.service
cat ~/.local/share/meow-neural-server/state/pairing-payload.txt
```

Replace both example addresses with the server interface reachable by your
phone. Binding defaults to loopback; this does not open firewall ports or
configure a VPN. Keep access on your LAN/private VPN. A system administrator
may need to enable user lingering for startup without an interactive login.
The installer preserves certificate/pairing state and keeps versioned releases.

In the app, open Neural setup, choose Use another server, paste the payload,
and select Connect & verify model. Select MaleCNS and Neural to use it. A
successful probe verifies the exact full graph before saving the connection.
The payload expires after ten minutes and is single-use. One paired app is
supported per service; pairing another app replaces that service's secret.
The general desktop pairing is separate and remains unchanged.

Only authenticated ping, neural step and stop are allowed. Desktop tools,
files, arbitrary commands and physical actuators are unavailable. The app
pins TLS and signs requests with HMAC. Idle workers stop after 15 seconds;
the service restarts on failure. Pairing is closed after every restart.

`verify_malecns_worker.py` checks both graphs, deterministic replay, duplicate
requests, sensory responses and measured compute budgets. Run it with the
bundled worker/models/manifest paths, the selected Java executable, and
`--ticks 40 --report worker-check.json` (see `--help` for full arguments).
After pairing, `verify_malecns_bridge.py --run-live --graph full --helper-dir
~/.local/share/meow-neural-server/state --output bridge-check.json` checks
the authenticated service while the app's remote brain is inactive. If the
service binds a LAN address, additionally pass `--host` with that address.
Do not interpret `/health` alone as proof that a graph loaded or the app paired.
