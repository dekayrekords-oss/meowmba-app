#!/usr/bin/env python3
"""
meowmba-bridge — desktop companion daemon for the Meowmba Android app (Phase 6).

Single file, Python 3.9+ standard library only. No pip installs.

What it does (P6a scope):
  * Serves HTTPS with a locally generated self-signed certificate.
  * Pairs with exactly one phone: on `--pair` it prints a pairing payload
    (host, port, one-time token, certificate fingerprint). Entering that
    payload in Meowmba establishes a shared 256-bit secret over the pinned
    TLS channel. The token is single-use and expires after 10 minutes.
  * After pairing, every request must carry a valid HMAC-SHA256 signature
    (method + path + timestamp + body hash) with a 60-second replay window.
  * v1 methods: "ping" and "status". File/build tools arrive in P6b+ and
    will only ever touch workspace roots declared in config.json.

Security contract (same as the phone):
  * No shell execution. Ever.
  * The pairing secret lives in ~/.meowmba-bridge/paired.json (chmod 600)
    on the desktop and in Android Keystore-encrypted prefs on the phone.
  * Certificate fingerprint is shown out-of-band at pairing; the phone
    pins it and refuses any other certificate.

Requires `openssl` on PATH (preinstalled on macOS and most Linux) for the
one-time certificate generation.

Usage:
  python3 meowmba-bridge.py --pair      # print pairing payload, then serve
  python3 meowmba-bridge.py             # serve (must already be paired)
  python3 meowmba-bridge.py --unpair    # forget the phone
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import secrets
import socket
import ssl
import subprocess
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

VERSION = "0.1.0"
CONFIG_DIR = Path.home() / ".meowmba-bridge"
CERT_PATH = CONFIG_DIR / "cert.pem"
KEY_PATH = CONFIG_DIR / "key.pem"
PAIRED_PATH = CONFIG_DIR / "paired.json"
CONFIG_PATH = CONFIG_DIR / "config.json"
LOG_PATH = CONFIG_DIR / "bridge.log"

DEFAULT_PORT = 8765
PAIR_TOKEN_TTL_S = 600
MAX_SKEW_S = 60
MAX_BODY_BYTES = 256 * 1024

# Holds the single-use pairing token in memory only (never written to disk).
_pair_state: dict = {}


# --------------------------------------------------------------------------
# Setup helpers
# --------------------------------------------------------------------------

def _lan_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("192.0.2.1", 80))  # no traffic is actually sent
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def _load_config() -> dict:
    if CONFIG_PATH.exists():
        try:
            return json.loads(CONFIG_PATH.read_text())
        except (OSError, ValueError):
            pass
    cfg = {
        "name": socket.gethostname(),
        "port": DEFAULT_PORT,
        # Declared but unused until P6b — file tools will be confined here.
        "workspace_roots": [],
    }
    CONFIG_DIR.mkdir(mode=0o700, parents=True, exist_ok=True)
    CONFIG_PATH.write_text(json.dumps(cfg, indent=2))
    return cfg


def _ensure_cert() -> None:
    if CERT_PATH.exists() and KEY_PATH.exists():
        return
    CONFIG_DIR.mkdir(mode=0o700, parents=True, exist_ok=True)
    ip = _lan_ip()
    try:
        subprocess.run(
            [
                "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                "-keyout", str(KEY_PATH), "-out", str(CERT_PATH),
                "-days", "3650", "-subj", "/CN=meowmba-bridge",
                "-addext", f"subjectAltName=DNS:localhost,DNS:{socket.gethostname()},IP:127.0.0.1,IP:{ip}",
            ],
            check=True, capture_output=True,
        )
    except (OSError, subprocess.CalledProcessError) as e:
        sys.exit(f"error: failed to generate self-signed certificate with openssl: {e}")
    os.chmod(KEY_PATH, 0o600)


def _cert_fingerprint() -> str:
    der = subprocess.run(
        ["openssl", "x509", "-in", str(CERT_PATH), "-outform", "DER"],
        check=True, capture_output=True,
    ).stdout
    return hashlib.sha256(der).hexdigest()


def _load_pairing() -> dict | None:
    try:
        return json.loads(PAIRED_PATH.read_text())
    except (OSError, ValueError):
        return None


def _save_pairing(data: dict) -> None:
    PAIRED_PATH.write_text(json.dumps(data, indent=2))
    os.chmod(PAIRED_PATH, 0o600)


def _log(msg: str) -> None:
    line = f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {msg}"
    print(line, flush=True)
    try:
        with LOG_PATH.open("a") as f:
            f.write(line + "\n")
    except OSError:
        pass


# --------------------------------------------------------------------------
# HTTP handler
# --------------------------------------------------------------------------

class BridgeHandler(BaseHTTPRequestHandler):
    server_version = f"meowmba-bridge/{VERSION}"
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # silence default stderr logging
        pass

    # -- helpers -----------------------------------------------------------

    def _read_body(self) -> bytes | None:
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY_BYTES:
            self._send(413, {"error": "body too large"})
            return None
        return self.rfile.read(length) if length else b""

    def _send(self, code: int, obj: dict) -> None:
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _verify_request(self, body: bytes) -> dict | None:
        """Return pairing record if the HMAC signature is valid, else None."""
        pairing = _load_pairing()
        if not pairing:
            self._send(428, {"error": "not paired"})
            return None
        ts = self.headers.get("X-Meowmba-Timestamp", "")
        sig = self.headers.get("X-Meowmba-Signature", "")
        try:
            if abs(time.time() - int(ts)) > MAX_SKEW_S:
                raise ValueError
        except ValueError:
            self._send(401, {"error": "timestamp outside 60s window"})
            return None
        secret = base64.b64decode(pairing["secret_b64"])
        msg = f"POST\n/rpc\n{ts}\n{hashlib.sha256(body).hexdigest()}".encode()
        expected = hmac.new(secret, msg, hashlib.sha256).digest()
        try:
            provided = base64.b64decode(sig)
        except ValueError:
            provided = b""
        if not hmac.compare_digest(expected, provided):
            self._send(401, {"error": "bad signature"})
            return None
        return pairing

    # -- routes ------------------------------------------------------------

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"ok": True, "version": VERSION})
        else:
            self._send(404, {"error": "not found"})

    def do_POST(self):
        body = self._read_body()
        if body is None:
            return
        try:
            data = json.loads(body or b"{}")
        except ValueError:
            self._send(400, {"error": "invalid json"})
            return

        if self.path == "/pair":
            self._handle_pair(data)
        elif self.path == "/rpc":
            self._handle_rpc(data, body)
        else:
            self._send(404, {"error": "not found"})

    def _handle_pair(self, data: dict) -> None:
        token = _pair_state.get("token")
        if not token or time.time() > _pair_state.get("expires", 0):
            self._send(409, {"error": "no active pairing window — restart with --pair"})
            return
        if not hmac.compare_digest(str(data.get("token", "")), token):
            _log("pair: rejected (bad token)")
            self._send(403, {"error": "bad pairing token"})
            return
        try:
            secret = base64.b64decode(str(data.get("secret", "")))
        except ValueError:
            secret = b""
        if len(secret) != 32:
            self._send(400, {"error": "secret must be 32 bytes (base64)"})
            return
        phone_name = str(data.get("phone_name", "unknown phone"))[:80]
        _save_pairing({
            "phone_name": phone_name,
            "secret_b64": base64.b64encode(secret).decode(),
            "paired_at": int(time.time()),
        })
        _pair_state.clear()  # single-use
        _log(f"pair: paired with '{phone_name}'")
        self._send(200, {"ok": True, "desktop_name": self.server.cfg["name"], "version": VERSION})

    def _handle_rpc(self, data: dict, raw_body: bytes) -> None:
        pairing = self._verify_request(raw_body)
        if not pairing:
            return
        method = data.get("method")
        if method == "ping":
            self._send(200, {"pong": True, "ts": int(time.time())})
        elif method == "status":
            self._send(200, {
                "name": self.server.cfg["name"],
                "version": VERSION,
                "platform": sys.platform,
                "uptime_s": int(time.time() - self.server.started_at),
                "workspace_roots": self.server.cfg.get("workspace_roots", []),
                "paired_phone": pairing.get("phone_name"),
            })
        else:
            self._send(400, {"error": f"unknown method '{method}' (P6a supports: ping, status)"})


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def main() -> None:
    ap = argparse.ArgumentParser(description="Meowmba desktop bridge daemon")
    ap.add_argument("--pair", action="store_true", help="open a 10-minute pairing window")
    ap.add_argument("--unpair", action="store_true", help="forget the paired phone and exit")
    ap.add_argument("--port", type=int, default=None, help=f"listen port (default {DEFAULT_PORT})")
    args = ap.parse_args()

    if args.unpair:
        PAIRED_PATH.unlink(missing_ok=True)
        print("unpaired.")
        return

    cfg = _load_config()
    if args.port:
        cfg["port"] = args.port
    _ensure_cert()

    if args.pair:
        _pair_state["token"] = secrets.token_urlsafe(16)
        _pair_state["expires"] = time.time() + PAIR_TOKEN_TTL_S
        payload = {
            "v": 1,
            "host": _lan_ip(),
            "port": cfg["port"],
            "name": cfg["name"],
            "pair_token": _pair_state["token"],
            "cert_sha256": _cert_fingerprint(),
        }
        blob = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode()
        print("═" * 64)
        print("  PAIRING WINDOW OPEN (10 minutes, single use)")
        print("  Enter this payload in Meowmba → Settings → Desktop bridge:")
        print()
        print(f"  {blob}")
        print("═" * 64, flush=True)
    elif not _load_pairing():
        print("warning: not paired — run with --pair to pair a phone", flush=True)

    server = ThreadingHTTPServer(("0.0.0.0", cfg["port"]), BridgeHandler)
    server.cfg = cfg
    server.started_at = time.time()
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(str(CERT_PATH), str(KEY_PATH))
    server.socket = ctx.wrap_socket(server.socket, server_side=True)

    _log(f"listening on https://0.0.0.0:{cfg['port']} (cert sha256 {_cert_fingerprint()[:16]}…)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
