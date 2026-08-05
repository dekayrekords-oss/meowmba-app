#!/usr/bin/env python3
"""
meowmba-bridge — desktop companion daemon for the Meowmba Android app (Phase 6).

Single file, Python 3.9+ standard library only. No pip installs.

What it does:
  * Serves HTTPS with a locally generated self-signed certificate.
  * DISCOVERY: broadcasts a small JSON packet (name, port, cert fingerprint,
    pairing-window state) over UDP :8766 every 2s, so the phone can find
    desktops on the LAN instead of you transcribing anything.
  * PAIRING: `--pair` shows a short 8-character code (single use, 10-minute
    TTL). The phone discovers this desktop, you type the code, and a shared
    256-bit secret is established over the pinned TLS channel.
    (The full payload string is still printed as a manual fallback.)
  * After pairing, every request must carry a valid HMAC-SHA256 signature
    (method + path + timestamp + body hash) with a 60-second replay window.
  * P6b file tools: "list_files" and "read_file" — read-only, confined to
    the workspace roots declared in ~/.meowmba-bridge/config.json. Path
    escapes (../, absolute tricks, symlink hops) and sensitive names
    (.env, keys, certs) are refused.
  * P6c: "write_file" — same confinement, utf-8 text only, atomic
    (temp file + rename), existing files are backed up first
    (<name>.bak-<timestamp>), and every write is appended to bridge.log.
  * P6d: "run_build" — runs ONLY the named commands pre-declared in
    config.json ("commands": {"name": {"argv": [...], "cwd": "...",
    "timeout_s": 600}}). No shell, no arbitrary commands, cwd confined
    to workspace roots, output capped, every run audit-logged.

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
import shutil
import tempfile
import socket
import ssl
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

VERSION = "0.6.0"
CONFIG_DIR = Path.home() / ".meowmba-bridge"
CERT_PATH = CONFIG_DIR / "cert.pem"
KEY_PATH = CONFIG_DIR / "key.pem"
PAIRED_PATH = CONFIG_DIR / "paired.json"
CONFIG_PATH = CONFIG_DIR / "config.json"
LOG_PATH = CONFIG_DIR / "bridge.log"

DEFAULT_PORT = 8765
DISCOVERY_PORT = 8766
DISCOVERY_INTERVAL_S = 2.0
PAIR_TOKEN_TTL_S = 600
PAIR_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"  # no ambiguous chars
MAX_PAIR_ATTEMPTS = 20
MAX_SKEW_S = 60
MAX_BODY_BYTES = 256 * 1024
MAX_LIST_ENTRIES = 500
DEFAULT_READ_BYTES = 64 * 1024
MAX_READ_BYTES = 256 * 1024
MAX_WRITE_BYTES = 256 * 1024
MAX_BUILD_OUTPUT_BYTES = 32 * 1024
MAX_BUILD_TIMEOUT_S = 3600
MAX_SCRIPT_OUTPUT_BYTES = 64 * 1024
MAX_SCRIPT_TIMEOUT_S = 600

# Never listed or read through the bridge, even inside a workspace root.
DENIED_NAMES = {
    ".env", ".env.local", ".envrc", "id_rsa", "id_dsa", "id_ecdsa",
    "id_ed25519", ".pem", ".key", ".crt", ".p12", ".pfx", ".keystore",
    ".jks", ".netrc", ".pgpass", ".htpasswd", "shadow", "credentials",
    "secrets.json", "local.properties",
}
DENIED_SUFFIXES = (".pem", ".key", ".crt", ".p12", ".pfx")


def _is_denied_name(name: str) -> bool:
    """Sensitive basenames refused by the bridge, incl. .env.* variants.
    Compared case-insensitively: on case-insensitive filesystems (default
    APFS, NTFS) `.ENV` IS `.env`."""
    lowered = name.lower()
    return (
        lowered in DENIED_NAMES
        or lowered.startswith(".env.")
        or lowered.endswith(DENIED_SUFFIXES)
    )

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
# Discovery (LAN broadcast) + short pairing codes
# --------------------------------------------------------------------------

def _make_pair_code() -> str:
    """8 human-typable chars (~40 bits), dashed for readability: XXXX-XXXX."""
    raw = "".join(secrets.choice(PAIR_CODE_ALPHABET) for _ in range(8))
    return f"{raw[:4]}-{raw[4:]}"


def _normalize_code(s: str) -> str:
    return s.upper().replace("-", "").replace(" ", "")


def _discovery_broadcast(cfg: dict, stop: threading.Event) -> None:
    """Broadcast presence JSON over UDP so phones can find this desktop."""
    payload = json.dumps({
        "v": 1,
        "service": "meowmba-bridge",
        "name": cfg["name"],
        "port": cfg["port"],
        "cert_sha256": _cert_fingerprint(),
        "version": VERSION,
        "pairing_open": bool(_pair_state.get("token")) and time.time() < _pair_state.get("expires", 0),
    }).encode()
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        while not stop.wait(DISCOVERY_INTERVAL_S):
            # Refresh pairing_open each round, send to limited + subnet broadcast
            fresh = json.loads(payload)
            fresh["pairing_open"] = bool(_pair_state.get("token")) and time.time() < _pair_state.get("expires", 0)
            blob = json.dumps(fresh).encode()
            for dest in ("255.255.255.255", "192.168.255.255"):
                try:
                    sock.sendto(blob, (dest, DISCOVERY_PORT))
                except OSError:
                    pass
    finally:
        sock.close()


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
        # USB auto-pair: accept from 192.168.42.x without a token.
        client_ip = self.client_address[0] if hasattr(self, "client_address") else ""
        if data.get("usb_auto") and client_ip.startswith("192.168.42."):
            self._do_pair(data, is_usb_auto=True)
            return

        token = _pair_state.get("token")
        if not token or time.time() > _pair_state.get("expires", 0):
            self._send(409, {"error": "no active pairing window — start it with --pair or --auto-pair"})
            return
        if not hmac.compare_digest(_normalize_code(str(data.get("token", ""))), _normalize_code(token)):
            _pair_state["attempts"] = _pair_state.get("attempts", 0) + 1
            if _pair_state["attempts"] >= MAX_PAIR_ATTEMPTS:
                _pair_state.clear()
                _log("pair: too many bad attempts — window closed")
                self._send(409, {"error": "too many attempts — restart with --pair"})
                return
            _log("pair: rejected (bad code)")
            self._send(403, {"error": "bad pairing code"})
            return
        self._do_pair(data, is_usb_auto=False)

    def _do_pair(self, data: dict, is_usb_auto: bool) -> None:
        try:
            secret = base64.b64decode(str(data.get("secret", "")))
        except ValueError:
            secret = b""
        if len(secret) != 32:
            self._send(400, {"error": "secret must be 32 bytes (base64)"})
            return
        phone_name = str(data.get("phone_name", "unknown phone"))[:80]
        phone_name = phone_name.replace("\r", " ").replace("\n", " ")
        _save_pairing({
            "phone_name": phone_name,
            "secret_b64": base64.b64encode(secret).decode(),
            "paired_at": int(time.time()),
            "via_usb": is_usb_auto,
        })
        if not is_usb_auto:
            _pair_state.clear()  # single-use for code-based pairing
        _log(f"pair: paired with '{phone_name}'{' (USB auto)' if is_usb_auto else ''}")
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
        elif method == "list_files":
            self._rpc_list_files(data.get("params") or {})
        elif method == "read_file":
            self._rpc_read_file(data.get("params") or {})
        elif method == "write_file":
            self._rpc_write_file(data.get("params") or {}, pairing)
        elif method == "run_build":
            self._rpc_run_build(data.get("params") or {}, pairing)
        elif method == "run_script":
            self._rpc_run_script(data.get("params") or {}, pairing)
        elif method == "open_app":
            self._rpc_open_app(data.get("params") or {})
        elif method == "open_url":
            self._rpc_open_url(data.get("params") or {})
        elif method == "open_file":
            self._rpc_open_file(data.get("params") or {})
        elif method == "clipboard_get":
            self._rpc_clipboard_get(data.get("params") or {})
        elif method == "clipboard_set":
            self._rpc_clipboard_set(data.get("params") or {}, pairing)
        elif method == "media":
            self._rpc_media(data.get("params") or {})
        elif method == "screenshot":
            self._rpc_screenshot(data.get("params") or {})
        else:
            self._send(400, {"error": f"unknown method '{method}'"})

    # -- workspace file tools (P6b, read-only) --------------------------------

    def _workspace_roots(self) -> list:
        roots = []
        for r in self.server.cfg.get("workspace_roots", []):
            try:
                roots.append(os.path.realpath(os.path.expanduser(str(r))))
            except OSError:
                continue
        return roots

    def _resolve_in_workspace(self, raw_path, allow_root=False):
        """Resolve raw_path inside a declared workspace root.

        Returns (realpath, root) or (None, error-message). Absolute paths must
        land inside a root; relative paths are tried against each root.
        realpath() collapses ../ and symlink hops before the confinement check.
        With allow_root, an empty path means the first workspace root itself.
        """
        roots = self._workspace_roots()
        if not roots:
            return None, "no workspace_roots declared in ~/.meowmba-bridge/config.json"
        p = os.path.expanduser(str(raw_path or "").strip())
        if not p:
            if allow_root:
                return roots[0], None
            return None, "path required"
        candidates = [p] if os.path.isabs(p) else [os.path.join(r, p) for r in roots]
        for cand in candidates:
            real = os.path.realpath(cand)
            for root in roots:
                if real == root or real.startswith(root + os.sep):
                    return real, None
        return None, "path escapes the declared workspace roots"

    def _rpc_list_files(self, params: dict) -> None:
        real, err = self._resolve_in_workspace(params.get("path", ""), allow_root=True)
        if err:
            self._send(403, {"error": err})
            return
        if not os.path.isdir(real):
            self._send(404, {"error": "not a directory"})
            return
        entries = []
        truncated = False
        try:
            with os.scandir(real) as it:
                for e in it:
                    if _is_denied_name(e.name):
                        continue
                    if len(entries) >= MAX_LIST_ENTRIES:
                        truncated = True
                        break
                    try:
                        is_dir = e.is_dir(follow_symlinks=False)
                        size = 0 if is_dir else e.stat(follow_symlinks=False).st_size
                    except OSError:
                        continue
                    entries.append({"name": e.name, "dir": is_dir, "size": size})
        except OSError as ex:
            self._send(403, {"error": f"cannot list: {ex.strerror or ex}"})
            return
        entries.sort(key=lambda x: (not x["dir"], x["name"].lower()))
        self._send(200, {
            "path": real,
            "entries": entries,
            "truncated": truncated,
        })

    def _rpc_read_file(self, params: dict) -> None:
        raw = str(params.get("path", ""))
        name = os.path.basename(raw)
        if _is_denied_name(name):
            self._send(403, {"error": "denied: sensitive file type"})
            return
        real, err = self._resolve_in_workspace(raw)
        if err:
            self._send(403, {"error": err})
            return
        if not os.path.isfile(real):
            self._send(404, {"error": "not a file"})
            return
        try:
            max_bytes = int(params.get("max_bytes", DEFAULT_READ_BYTES))
        except (TypeError, ValueError):
            max_bytes = DEFAULT_READ_BYTES
        max_bytes = max(1, min(max_bytes, MAX_READ_BYTES))
        try:
            size = os.path.getsize(real)
            with open(real, "rb") as f:
                data = f.read(max_bytes + 1)
        except OSError as ex:
            self._send(403, {"error": f"cannot read: {ex.strerror or ex}"})
            return
        truncated = len(data) > max_bytes
        if truncated:
            data = data[:max_bytes]
        try:
            content = data.decode("utf-8")
        except UnicodeDecodeError:
            self._send(415, {"error": "binary file — only utf-8 text is served"})
            return
        self._send(200, {
            "path": real,
            "size": size,
            "content": content,
            "truncated": truncated,
        })

    def _rpc_write_file(self, params: dict, pairing: dict) -> None:
        raw = str(params.get("path", ""))
        if "\0" in raw:
            self._send(400, {"error": "null byte in path"})
            return
        if _is_denied_name(os.path.basename(raw)):
            self._send(403, {"error": "denied: sensitive file type"})
            return
        content = params.get("content")
        if not isinstance(content, str):
            self._send(400, {"error": "content (utf-8 string) required"})
            return
        data = content.encode("utf-8")
        if len(data) > MAX_WRITE_BYTES:
            self._send(413, {"error": f"content exceeds {MAX_WRITE_BYTES // 1024} KB write cap"})
            return
        real, err = self._resolve_in_workspace(raw)
        if err:
            self._send(403, {"error": err})
            return
        parent = os.path.dirname(real) or real
        if not os.path.isdir(parent):
            self._send(404, {"error": "parent directory does not exist"})
            return

        backup = None
        created = not os.path.exists(real)
        if not created:
            # Nanosecond suffix: two writes in the same second never share a
            # backup name (the server is multi-threaded).
            backup = f"{os.path.basename(real)}.bak-{time.strftime('%Y%m%d-%H%M%S')}-{time.time_ns() % 10**9}"
            try:
                with open(real, "rb") as src, open(os.path.join(parent, backup), "wb") as dst:
                    dst.write(src.read())
            except OSError as ex:
                self._send(500, {"error": f"backup failed, refusing to overwrite: {ex}"})
                return

        # Atomic: temp file in the same directory, then rename over the target.
        tmp = f"{real}.bridge-tmp-{os.getpid()}"
        try:
            with open(tmp, "wb") as f:
                f.write(data)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, real)
            # fsync the DIRECTORY so the rename's metadata is durable too
            dir_fd = os.open(parent, os.O_RDONLY)
            try:
                os.fsync(dir_fd)
            finally:
                os.close(dir_fd)
        except OSError as ex:
            try:
                os.unlink(tmp)
            except OSError:
                pass
            self._send(500, {"error": f"write failed: {ex}"})
            return

        _log(
            f"write_file: {real} ({len(data)} bytes, "
            f"{'created' if created else 'backup=' + backup}) "
            f"by '{pairing.get('phone_name')}'"
        )
        self._send(200, {
            "path": real,
            "size": len(data),
            "created": created,
            "backup": backup,
        })

    def _rpc_run_script(self, params: dict, pairing: dict) -> None:
        """Run an arbitrary script on the desktop (no shell)."""
        raw = str(params.get("path", "")).strip()
        if not raw:
            self._send(400, {"error": "path required"})
            return
        if _is_denied_name(os.path.basename(raw)):
            self._send(403, {"error": "denied: sensitive file type"})
            return
        real, err = self._resolve_in_workspace(raw)
        if err:
            self._send(403, {"error": err})
            return
        if not os.path.isfile(real):
            self._send(404, {"error": "not a file"})
            return
        if not os.access(real, os.X_OK):
            self._send(403, {"error": "not executable (chmod +x)"})
            return

        args_raw = str(params.get("args", "")).strip()
        args_list = args_raw.split() if args_raw else []
        try:
            timeout_s = max(1, min(int(params.get("timeout_sec", 60)), MAX_SCRIPT_TIMEOUT_S))
        except (TypeError, ValueError):
            timeout_s = 60

        cwd = os.path.dirname(real)
        argv = [real] + args_list
        started = time.time()
        timed_out = False
        try:
            proc = subprocess.run(
                argv, cwd=cwd, capture_output=True, timeout=timeout_s, check=False
            )
            rc = proc.returncode
            output = (proc.stdout + proc.stderr).decode("utf-8", errors="replace")
        except subprocess.TimeoutExpired as ex:
            rc = -9
            timed_out = True
            partial = (ex.stdout or b"") + (ex.stderr or b"")
            output = partial.decode("utf-8", errors="replace")
        except OSError as ex:
            self._send(500, {"error": f"could not start: {ex}"})
            return
        duration = time.time() - started
        output = output[-MAX_SCRIPT_OUTPUT_BYTES:]

        _log(
            f"run_script: {real} exit={rc} dur={duration:.1f}s "
            f"{'TIMEOUT ' if timed_out else ''}by '{pairing.get('phone_name')}'"
        )
        self._send(200, {
            "name": raw,
            "exit_code": rc,
            "duration_s": round(duration, 1),
            "timed_out": timed_out,
            "output": output,
        })

    # -- control tools (Phase F1): open/clipboard/media/screenshot ----------
    # Every helper degrades to an honest error when the desktop lacks the
    # underlying tool (xclip, playerctl, ...) — never a silent no-op.

    @staticmethod
    def _run_quiet(argv, input_bytes=None, timeout=20):
        try:
            return subprocess.run(
                argv, input=input_bytes, capture_output=True, timeout=timeout, check=False
            )
        except (OSError, subprocess.TimeoutExpired):
            return None

    def _rpc_open_url(self, params: dict) -> None:
        url = str(params.get("url", "")).strip()
        if not url.startswith(("http://", "https://")):
            self._send(400, {"error": "only http(s) URLs are allowed"})
            return
        try:
            import webbrowser
            if webbrowser.open(url):
                self._send(200, {"ok": True, "message": f"Opened {url} on the desktop."})
            else:
                self._send(500, {"error": "no browser available on this desktop"})
        except Exception as ex:
            self._send(500, {"error": f"could not open URL: {ex}"})

    def _rpc_open_file(self, params: dict) -> None:
        path = os.path.expanduser(str(params.get("path", "")).strip())
        if not path or not os.path.exists(path):
            self._send(404, {"error": "file not found"})
            return
        if sys.platform == "darwin":
            argv = ["open", path]
        elif os.name == "nt":
            argv = ["cmd", "/c", "start", "", path]
        else:
            argv = ["xdg-open", path]
        proc = self._run_quiet(argv)
        if proc is None or proc.returncode != 0:
            self._send(500, {"error": "could not open file (is xdg-open installed?)"})
            return
        self._send(200, {"ok": True, "message": f"Opened {path}."})

    def _find_desktop_entry(self, name: str):
        want = name.lower()
        dirs = [Path("/usr/share/applications"), Path.home() / ".local/share/applications"]
        for d in dirs:
            if not d.is_dir():
                continue
            for f in sorted(d.glob("*.desktop")):
                try:
                    txt = f.read_text(errors="replace")
                except OSError:
                    continue
                for line in txt.splitlines():
                    if line.startswith("Name=") and want in line[5:].lower():
                        return f
        return None

    def _rpc_open_app(self, params: dict) -> None:
        name = str(params.get("name", "")).strip()
        if not name:
            self._send(400, {"error": "name required"})
            return
        if sys.platform == "darwin":
            proc = self._run_quiet(["open", "-a", name])
            if proc is None or proc.returncode != 0:
                self._send(404, {"error": f"no app called '{name}' found"})
            else:
                self._send(200, {"ok": True, "message": f"Opened {name}."})
            return
        if os.name == "nt":
            proc = self._run_quiet([
                "powershell", "-NoProfile", "-Command",
                f"Start-Process '{name.replace(chr(39), chr(39)+chr(39))}'",
            ])
            if proc is None or proc.returncode != 0:
                self._send(404, {"error": f"could not start '{name}'"})
            else:
                self._send(200, {"ok": True, "message": f"Started {name}."})
            return
        entry = self._find_desktop_entry(name)
        if entry is None:
            self._send(404, {"error": f"no installed app matching '{name}'"})
            return
        proc = self._run_quiet(["gtk-launch", entry.stem])
        if proc is None or proc.returncode != 0:
            # Fallback: run the first token of the Exec= line.
            exec_cmd = ""
            try:
                for line in entry.read_text(errors="replace").splitlines():
                    if line.startswith("Exec="):
                        exec_cmd = line[5:].split()[0]
                        break
            except OSError:
                pass
            if not exec_cmd or shutil.which(exec_cmd) is None:
                self._send(500, {"error": f"found {entry.name} but could not launch it (gtk-launch missing?)"})
                return
            proc = self._run_quiet([exec_cmd])
        self._send(200, {"ok": True, "message": f"Opened {name}."})

    def _rpc_clipboard_get(self, params: dict) -> None:
        proc = None
        if sys.platform == "darwin":
            proc = self._run_quiet(["pbpaste"])
        elif os.name == "nt":
            proc = self._run_quiet(["powershell", "-NoProfile", "-Command", "Get-Clipboard"])
        else:
            if shutil.which("xclip"):
                proc = self._run_quiet(["xclip", "-selection", "clipboard", "-o"])
            elif shutil.which("xsel"):
                proc = self._run_quiet(["xsel", "--clipboard", "--output"])
            elif shutil.which("wl-paste"):
                proc = self._run_quiet(["wl-paste", "--no-newline"])
            else:
                self._send(500, {"error": "no clipboard tool installed (need xclip, xsel or wl-clipboard)"})
                return
        if proc is None or proc.returncode != 0:
            self._send(500, {"error": "clipboard read failed"})
            return
        self._send(200, {"ok": True, "text": proc.stdout.decode("utf-8", "replace")[:16000]})

    def _rpc_clipboard_set(self, params: dict, pairing: dict) -> None:
        text = str(params.get("text", ""))
        data = text.encode("utf-8")
        proc = None
        if sys.platform == "darwin":
            proc = self._run_quiet(["pbcopy"], input_bytes=data)
        elif os.name == "nt":
            proc = self._run_quiet(["clip"], input_bytes=data)
        else:
            if shutil.which("xclip"):
                proc = self._run_quiet(["xclip", "-selection", "clipboard", "-i"], input_bytes=data)
            elif shutil.which("xsel"):
                proc = self._run_quiet(["xsel", "--clipboard", "--input"], input_bytes=data)
            elif shutil.which("wl-copy"):
                proc = self._run_quiet(["wl-copy"], input_bytes=data)
            else:
                self._send(500, {"error": "no clipboard tool installed (need xclip, xsel or wl-clipboard)"})
                return
        if proc is None or proc.returncode != 0:
            self._send(500, {"error": "clipboard write failed"})
            return
        _log(f"clipboard_set: {len(data)} B by '{pairing.get('phone_name')}'")
        self._send(200, {"ok": True, "message": f"Clipboard updated ({len(data)} chars)."})

    def _rpc_media(self, params: dict) -> None:
        action = str(params.get("action", "")).lower().strip()
        if action == "volume":
            try:
                vol = max(0, min(int(params.get("volume", 50)), 100))
            except (TypeError, ValueError):
                self._send(400, {"error": "volume must be 0-100"})
                return
            if sys.platform == "darwin":
                proc = self._run_quiet(["osascript", "-e", f"set volume output volume {vol}"])
            elif os.name == "nt":
                self._send(500, {"error": "volume control not supported on this desktop"})
                return
            elif shutil.which("pactl"):
                proc = self._run_quiet(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{vol}%"])
            elif shutil.which("playerctl"):
                proc = self._run_quiet(["playerctl", "volume", str(vol / 100.0)])
            else:
                self._send(500, {"error": "no volume tool installed (need pactl or playerctl)"})
                return
            if proc is None or proc.returncode != 0:
                self._send(500, {"error": "could not set volume"})
                return
            self._send(200, {"ok": True, "message": f"Volume set to {vol}%."})
            return
        if action not in ("play", "pause", "next"):
            self._send(400, {"error": "action must be play, pause, next or volume"})
            return
        if sys.platform == "darwin":
            keycode = {"play": 49, "pause": 49, "next": 47}[action]
            proc = self._run_quiet([
                "osascript", "-e", f"tell application \"System Events\" to key code {keycode}",
            ])
        elif os.name == "nt":
            self._send(500, {"error": "media keys not supported on this desktop"})
            return
        elif shutil.which("playerctl"):
            proc = self._run_quiet(["playerctl", action])
        else:
            self._send(500, {"error": "no media tool installed (need playerctl)"})
            return
        if proc is None or proc.returncode != 0:
            self._send(500, {"error": f"media {action} failed"})
            return
        self._send(200, {"ok": True, "message": f"Media: {action}."})

    def _rpc_screenshot(self, params: dict) -> None:
        out = Path(tempfile.gettempdir()) / f"meowmba-shot-{int(time.time())}.png"
        argv = None
        if sys.platform == "darwin":
            argv = ["screencapture", "-x", str(out)]
        elif os.name == "nt":
            ps = (
                "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
                "$b=[System.Windows.Forms.SystemInformation]::VirtualScreen; "
                "$bmp=New-Object Drawing.Bitmap $b.Width,$b.Height; "
                "$g=[Drawing.Graphics]::FromImage($bmp); "
                "$g.CopyFromScreen($b.X,$b.Y,0,0,$bmp.Size); "
                f"$bmp.Save('{out}')"
            )
            argv = ["powershell", "-NoProfile", "-Command", ps]
        else:
            for cand in (["scrot", str(out)], ["gnome-screenshot", "-f", str(out)],
                         ["import", "-window", "root", str(out)], ["grim", str(out)]):
                if shutil.which(cand[0]):
                    argv = cand
                    break
            if argv is None:
                self._send(500, {"error": "no screenshot tool installed (need scrot, gnome-screenshot, imagemagick or grim)"})
                return
        proc = self._run_quiet(argv, timeout=30)
        if proc is None or proc.returncode != 0 or not out.is_file():
            self._send(500, {"error": "screenshot failed"})
            return
        data = out.read_bytes()
        self._send(200, {
            "ok": True,
            "path": str(out),
            "bytes": len(data),
            "data_b64": base64.b64encode(data).decode("ascii"),
        })

    def _rpc_run_build(self, params: dict, pairing: dict) -> None:
        """Run a named, pre-declared command (P6d). No shell, ever."""
        name = str(params.get("name", ""))
        commands = self.server.cfg.get("commands", {})
        entry = commands.get(name)
        if not isinstance(entry, dict):
            available = ", ".join(sorted(commands)) or "none declared"
            self._send(400, {"error": f"unknown command '{name}' (declared: {available})"})
            return
        argv = entry.get("argv")
        if not isinstance(argv, list) or not argv or not all(isinstance(a, str) for a in argv):
            self._send(500, {"error": f"command '{name}' has an invalid argv in config.json"})
            return
        try:
            timeout_s = max(1, min(int(entry.get("timeout_s", 600)), MAX_BUILD_TIMEOUT_S))
        except (TypeError, ValueError):
            timeout_s = 600

        # cwd: declared cwd or the first workspace root — confined either way.
        roots = self._workspace_roots()
        if not roots:
            self._send(403, {"error": "no workspace_roots declared in ~/.meowmba-bridge/config.json"})
            return
        cwd = os.path.realpath(os.path.expanduser(str(entry.get("cwd") or roots[0])))
        if not any(cwd == r or cwd.startswith(r + os.sep) for r in roots):
            self._send(403, {"error": f"command '{name}' cwd escapes the declared workspace roots"})
            return
        if not os.path.isdir(cwd):
            self._send(404, {"error": f"command '{name}' cwd does not exist"})
            return

        started = time.time()
        timed_out = False
        try:
            proc = subprocess.run(
                argv, cwd=cwd, capture_output=True, timeout=timeout_s, check=False
            )
            rc = proc.returncode
            output = (proc.stdout + proc.stderr).decode("utf-8", errors="replace")
        except subprocess.TimeoutExpired as ex:
            rc = -9
            timed_out = True
            partial = (ex.stdout or b"") + (ex.stderr or b"")
            output = partial.decode("utf-8", errors="replace")
        except OSError as ex:
            self._send(500, {"error": f"could not start '{name}': {ex}"})
            return
        duration = time.time() - started
        output = output[-MAX_BUILD_OUTPUT_BYTES:]

        _log(
            f"run_build: {name} exit={rc} dur={duration:.1f}s "
            f"{'TIMEOUT ' if timed_out else ''}by '{pairing.get('phone_name')}'"
        )
        self._send(200, {
            "name": name,
            "exit_code": rc,
            "duration_s": round(duration, 1),
            "timed_out": timed_out,
            "output": output,
        })


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def main() -> None:
    ap = argparse.ArgumentParser(description="Meowmba desktop bridge daemon")
    ap.add_argument("--pair", action="store_true", help="open a 10-minute pairing window")
    ap.add_argument("--auto-pair", action="store_true",
        help="accept pairing from USB-tethered phones (192.168.42.x) without a code")
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
        _pair_state["token"] = _make_pair_code()
        _pair_state["expires"] = time.time() + PAIR_TOKEN_TTL_S
        _pair_state["attempts"] = 0
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
        print()
        print(f"    CODE: {_pair_state['token']}")
        print()
        print("  In Meowmba → Settings → Desktop Bridge → Find desktops,")
        print("  pick this computer and enter the code.")
        print()
        print(f"  Manual fallback payload: {blob}")
        print("═" * 64, flush=True)
    elif args.auto_pair:
        _pair_state["token"] = "AUTO_PAIR_USB"
        _pair_state["expires"] = time.time() + 86400 * 365  # effectively permanent
        _pair_state["attempts"] = 0
        print("═" * 64)
        print("  USB AUTO-PAIR ACTIVE")
        print()
        print("  Plug your phone in via USB tether — pairing happens")
        print("  automatically. No code needed.")
        print()
        print(f"  Listening on https://0.0.0.0:{cfg['port']}")
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

    stop_broadcast = threading.Event()
    broadcaster = threading.Thread(
        target=_discovery_broadcast, args=(cfg, stop_broadcast), daemon=True
    )
    broadcaster.start()

    _log(f"listening on https://0.0.0.0:{cfg['port']} (cert sha256 {_cert_fingerprint()[:16]}…, discovery udp:{DISCOVERY_PORT})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        stop_broadcast.set()


if __name__ == "__main__":
    main()
