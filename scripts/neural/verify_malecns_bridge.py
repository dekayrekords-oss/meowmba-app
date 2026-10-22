#!/usr/bin/env python3
"""Owner-invoked live acceptance through the installed, authenticated Mac bridge.

Run while the phone's remote brain is inactive: this exercises the single worker.
Secrets stay in the helper's existing private pairing store, never in the report.
This checks the computer-to-Meow route, not Android pairing or phone performance.
"""
import argparse
import base64
import hashlib
import hmac
import http.client
import json
import math
from pathlib import Path
import ssl
import time
import uuid

ASSET = "e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3"
SENSES = ("food", "touch", "loom_left", "loom_right", "motion_left", "motion_right")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-live", action="store_true", help="exercise the live worker while the phone is inactive")
    parser.add_argument("--helper-dir", type=Path, default=Path.home() / ".meowmba-bridge")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--graph", choices=("retained", "full"), default="retained")
    parser.add_argument("--host", default="127.0.0.1", help="Actual bound server address; TLS pin remains mandatory")
    args = parser.parse_args()
    full = args.graph == "full"
    asset = "8860e68b59e454d8d4e8bd601dd7a3cb9049d18516f1ea8df34eabe1ae400d08" if full else ASSET
    if not args.run_live:
        parser.error("Pass --run-live only when the phone's remote brain is inactive")
    config = json.loads((args.helper_dir / "config.json").read_text())
    secret = base64.b64decode(json.loads((args.helper_dir / "paired.json").read_text())["secret_b64"])
    cert = args.helper_dir / "cert.pem"
    pin = hashlib.sha256(ssl.PEM_cert_to_DER_cert(cert.read_text())).digest()
    context = ssl.create_default_context(cafile=str(cert))
    # The locally provisioned certificate is trusted and pinned; it names the
    # computer rather than the USB loopback address used for this self-client.
    context.check_hostname = False

    def rpc(method, params, signed=True):
        body = json.dumps(dict(method=method, params=params), separators=(",", ":")).encode()
        timestamp = str(int(time.time()))
        message = f"POST\n/rpc\n{timestamp}\n{hashlib.sha256(body).hexdigest()}".encode()
        headers = {"Content-Type": "application/json"}
        if signed:
            headers.update({"X-Meowmba-Timestamp": timestamp,
                            "X-Meowmba-Signature": base64.b64encode(hmac.new(secret, message, hashlib.sha256).digest()).decode()})
        connection = http.client.HTTPSConnection(args.host, int(config.get("port", 8765)), context=context, timeout=12)
        started = time.monotonic()
        try:
            connection.connect()
            assert hashlib.sha256(connection.sock.getpeercert(binary_form=True)).digest() == pin, "Certificate pin changed"
            connection.request("POST", "/rpc", body=body, headers=headers)
            response = connection.getresponse()
            result = json.loads(response.read(8192))
            return response.status, result, round((time.monotonic() - started) * 1000, 3)
        finally:
            connection.close()

    def measurement(result):
        return {k: v for k, v in result.items() if k not in ("session", "seq", "compute_ms")}

    episodes = [
        ("quiet", {}, 4),
        ("food", {"food": 1}, 20),
        ("touch", {"touch": 1}, 40),
        ("loom", {"loom_left": 1, "loom_right": 1}, 20),
        ("food_and_touch", {"food": 1, "touch": 1}, 40),
    ]
    report = dict(scope="Live authenticated neural service; phone inactive, no Android acceptance",
                  asset_sha256=asset, graph=args.graph, episodes={})
    status, _, _ = rpc("malecns_step", {}, signed=False)
    assert status == 401, "Unsigned request was not rejected"
    report["unsigned_rejected"] = True
    first_food = None
    for name, stimulus, count in episodes:
        session = str(uuid.uuid4())
        rows = []
        try:
            for seq in range(1, count + 1):
                request = dict(session=session, seq=seq, **dict.fromkeys(SENSES, 0))
                request.update(stimulus)
                if full: request["graph"] = "full"
                status, result, elapsed = rpc("malecns_step", request)
                assert status == 200, f"{name} request failed with HTTP {status}"
                assert result["session"] == session and result["seq"] == seq
                assert result["asset_sha256"] == asset and result["neurons"] == 176422
                assert result["pairs"] == (25862574 if full else 6287749) and result["synapses"] == (125024863 if full else 90296905)
                assert result["protocol"] == (2 if full else 1) and result["graph"] == args.graph
                assert result["sim_ms"] == seq * 50
                assert all(math.isfinite(v) for v in result.values() if isinstance(v, (int, float)))
                rows.append(dict(round_trip_ms=elapsed, **measurement(result), compute_ms=result["compute_ms"]))
                if name == "food" and seq == 1:
                    first_food = measurement(result)
                    code, duplicate, _ = rpc("malecns_step", request)
                    assert code == 200 and duplicate == result, "Duplicate advanced the model"
                    report["duplicate_cached"] = True
            report["episodes"][name] = rows
        finally:
            rpc("malecns_stop", {"session": session})
    timings = [r["round_trip_ms"] for rows in report["episodes"].values() for r in rows[1:]]
    compute = sorted(r["compute_ms"] for rows in report["episodes"].values() for r in rows)
    report["steady_round_trip_max_ms"] = max(timings)
    report["compute_p95_ms"] = compute[math.ceil(.95 * len(compute))-1]
    assert max(timings) <= 1800, "Steady replies exceed Android freshness limit"
    assert report["compute_p95_ms"] <= (300 if full else 200), "Compute exceeds profile cadence budget"
    assert max(r["feed_hz"] for r in report["episodes"]["food"]) > 0
    assert max(r["groom_hz"] for r in report["episodes"]["touch"]) > 0
    assert max(r["escape_hz"] for r in report["episodes"]["loom"]) > 0
    assert max(r["feed_hz"] for r in report["episodes"]["food_and_touch"]) > 0
    assert max(r["groom_hz"] for r in report["episodes"]["food_and_touch"]) > 0
    session = str(uuid.uuid4())
    try:
        request = dict(session=session, seq=1, **dict.fromkeys(SENSES, 0))
        request["food"] = 1
        if full: request["graph"] = "full"
        code, reset, _ = rpc("malecns_step", request)
        assert code == 200 and measurement(reset) == first_food, "Restart changed deterministic neural state"
        report["restart_deterministic"] = True
    finally:
        rpc("malecns_stop", {"session": session})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(dict(passed=True, requests=sum(x[2] for x in episodes), report=str(args.output),
                         motor_peaks={name: {k: max(row[k] for row in rows) for k in ("feed_hz", "groom_hz", "escape_hz", "wing_hz")}
                                      for name, rows in report["episodes"].items()}), indent=2))


if __name__ == "__main__":
    main()
