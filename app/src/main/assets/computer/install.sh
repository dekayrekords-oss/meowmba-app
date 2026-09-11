#!/usr/bin/env bash
# Meow Computer — idempotent server install (no phone-root, no secrets).
# Runs as the SSH user that invoked it. Safe to re-run.
set -euo pipefail

MEOW_LABEL="meow-computer"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { printf '[%s] %s\n' "$MEOW_LABEL" "$*"; }
warn() { printf '[%s] WARN: %s\n' "$MEOW_LABEL" "$*" >&2; }

# Prefer /opt when writable; else $HOME
if [[ -w /opt ]] || mkdir -p /opt/meow-computer 2>/dev/null; then
  WORKSPACE="${MEOW_COMPUTER_HOME:-/opt/meow-computer}"
else
  WORKSPACE="${MEOW_COMPUTER_HOME:-$HOME/meow-computer}"
fi
mkdir -p "$WORKSPACE"/{bin,data,logs,workspace}
log "workspace=$WORKSPACE (user=$(id -un) uid=$(id -u))"

have_cmd() { command -v "$1" >/dev/null 2>&1; }

install_pkgs_apt() {
  local pkgs=(openssh-client tmux curl git python3)
  local missing=()
  local p
  for p in "${pkgs[@]}"; do
    if ! dpkg -s "$p" >/dev/null 2>&1; then
      missing+=("$p")
    fi
  done
  if ((${#missing[@]})); then
    if have_cmd sudo && sudo -n true 2>/dev/null; then
      log "apt installing: ${missing[*]}"
      sudo -n apt-get update -qq || true
      sudo -n DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "${missing[@]}" || warn "apt install partial failure"
    else
      warn "missing packages (${missing[*]}) and no passwordless sudo — install them manually"
    fi
  else
    log "apt packages already present"
  fi
}

install_desktop_optional() {
  if ! have_cmd apt-get; then
    warn "no apt — skipping optional desktop (tigervnc/novnc)"
    return 0
  fi
  if have_cmd Xvnc || have_cmd Xtigervnc || have_cmd vncserver; then
    log "VNC already available"
    return 0
  fi
  if have_cmd sudo && sudo -n true 2>/dev/null; then
    log "attempting optional desktop stack (tigervnc-standalone-server / novnc)"
    if sudo -n DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
        tigervnc-standalone-server novnc websockify 2>/dev/null; then
      log "desktop packages installed"
    else
      warn "optional desktop packages not installed (apt skipped or unavailable) — terminal-only Computer is fine"
    fi
  else
    warn "optional desktop skipped (no passwordless sudo). Terminal Computer still works."
  fi
}

# Worker stub matching HttpsRuntimeClient: GET /health, POST /v1/jobs, GET /v1/jobs/{id}, GET /v1/jobs/{id}/logs
install_worker() {
  local worker="$WORKSPACE/bin/meow-computer-worker.py"
  cat > "$worker" << 'PY'
#!/usr/bin/env python3
"""Meow Computer HTTPS_RUNTIME-compatible worker stub (no secrets)."""
from __future__ import annotations

import json
import os
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

HOST = os.environ.get("MEOW_COMPUTER_HOST", "127.0.0.1")
PORT = int(os.environ.get("MEOW_COMPUTER_PORT", "8766"))
ROOT = Path(os.environ.get("MEOW_COMPUTER_HOME", str(Path.home() / "meow-computer")))
WORK = ROOT / "workspace"
LOGS = ROOT / "logs"
WORK.mkdir(parents=True, exist_ok=True)
LOGS.mkdir(parents=True, exist_ok=True)

_jobs: dict[str, dict] = {}
_lock = threading.Lock()


def _run_job(job_id: str, argv: list[str], cwd: str | None, timeout_ms: int | None) -> None:
    with _lock:
        _jobs[job_id]["status"] = "running"
    timeout = None if timeout_ms is None else max(1.0, timeout_ms / 1000.0)
    try:
        proc = subprocess.run(
            argv,
            cwd=cwd or str(WORK),
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
        with _lock:
            _jobs[job_id].update(
                {
                    "status": "succeeded" if proc.returncode == 0 else "failed",
                    "exitCode": proc.returncode,
                    "stdout": (proc.stdout or "")[-200_000:],
                    "stderr": (proc.stderr or "")[-200_000:],
                }
            )
    except subprocess.TimeoutExpired:
        with _lock:
            _jobs[job_id].update(
                {
                    "status": "failed",
                    "exitCode": 124,
                    "stdout": "",
                    "stderr": "timeout",
                    "detail": "timeout",
                }
            )
    except Exception as exc:  # noqa: BLE001
        with _lock:
            _jobs[job_id].update(
                {"status": "failed", "exitCode": 1, "stderr": str(exc)[:2000], "detail": str(exc)[:200]}
            )


class Handler(BaseHTTPRequestHandler):
    server_version = "MeowComputerWorker/1.0"

    def log_message(self, fmt: str, *args) -> None:  # quieter
        pass

    def _json(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or "0")
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            return {}

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/") or "/"
        if path == "/health":
            self._json(200, {"ok": True, "service": "meow-computer", "workspace": str(WORK)})
            return
        if path.startswith("/v1/jobs/") and path.endswith("/logs"):
            job_id = path[len("/v1/jobs/") : -len("/logs")].strip("/")
            with _lock:
                job = _jobs.get(job_id)
            if not job:
                self._json(404, {"error": "unknown job"})
                return
            self._json(200, {"stdout": job.get("stdout", ""), "stderr": job.get("stderr", "")})
            return
        if path.startswith("/v1/jobs/"):
            job_id = path[len("/v1/jobs/") :].strip("/")
            with _lock:
                job = _jobs.get(job_id)
            if not job:
                self._json(404, {"error": "unknown job"})
                return
            self._json(
                200,
                {
                    "status": job.get("status", "unknown"),
                    "exitCode": job.get("exitCode"),
                    "detail": job.get("detail", ""),
                },
            )
            return
        self._json(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/") or "/"
        if path != "/v1/jobs":
            self._json(404, {"error": "not found"})
            return
        payload = self._read_json()
        argv = payload.get("argv") or []
        if not isinstance(argv, list) or not argv or not all(isinstance(x, str) for x in argv):
            self._json(400, {"error": "argv must be a non-empty string list"})
            return
        cwd = payload.get("cwd")
        timeout_ms = payload.get("timeoutMs")
        job_id = uuid.uuid4().hex
        with _lock:
            _jobs[job_id] = {
                "status": "queued",
                "stdout": "",
                "stderr": "",
                "created": time.time(),
            }
        threading.Thread(
            target=_run_job,
            args=(job_id, list(argv), cwd if isinstance(cwd, str) else None, timeout_ms if isinstance(timeout_ms, int) else None),
            daemon=True,
        ).start()
        self._json(200, {"jobId": job_id})


def main() -> None:
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"meow-computer worker on http://{HOST}:{PORT} workspace={WORK}", flush=True)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
PY
  chmod +x "$worker"
  log "worker stub written: $worker"
}

write_systemd_unit() {
  local unit_src="$WORKSPACE/meow-computer.service"
  cat > "$unit_src" << UNIT
[Unit]
Description=Meow Computer workspace agent worker
After=network.target

[Service]
Type=simple
WorkingDirectory=$WORKSPACE
Environment=MEOW_COMPUTER_HOME=$WORKSPACE
Environment=MEOW_COMPUTER_HOST=127.0.0.1
Environment=MEOW_COMPUTER_PORT=8766
ExecStart=/usr/bin/env python3 $WORKSPACE/bin/meow-computer-worker.py
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
UNIT
  log "unit file: $unit_src"

  if have_cmd systemctl && [[ "$(id -u)" -ne 0 ]]; then
    mkdir -p "$HOME/.config/systemd/user"
    cp "$unit_src" "$HOME/.config/systemd/user/meow-computer.service"
    if systemctl --user daemon-reload 2>/dev/null \
      && systemctl --user enable --now meow-computer.service 2>/dev/null; then
      log "systemd --user meow-computer.service enabled"
    else
      warn "could not enable systemd user unit — start manually: python3 $WORKSPACE/bin/meow-computer-worker.py"
    fi
  elif have_cmd systemctl && [[ "$(id -u)" -eq 0 ]]; then
    cp "$unit_src" /etc/systemd/system/meow-computer.service
    systemctl daemon-reload || true
    systemctl enable --now meow-computer.service || warn "system unit start failed"
  else
    warn "systemd not available — use docker-compose or run the worker by hand"
  fi
}

write_compose() {
  cat > "$WORKSPACE/docker-compose.yml" << COMPOSE
# Optional: run Meow Computer worker in Docker (no secrets baked in).
services:
  meow-computer:
    image: python:3.12-slim
    working_dir: /opt/meow-computer
    volumes:
      - ./:/opt/meow-computer
    environment:
      MEOW_COMPUTER_HOME: /opt/meow-computer
      MEOW_COMPUTER_HOST: 0.0.0.0
      MEOW_COMPUTER_PORT: "8766"
    command: ["python3", "/opt/meow-computer/bin/meow-computer-worker.py"]
    ports:
      - "8766:8766"
    restart: unless-stopped
COMPOSE
  log "docker-compose.yml written"
  if have_cmd docker && docker info >/dev/null 2>&1; then
    (cd "$WORKSPACE" && docker compose up -d) && log "docker compose up -d OK" \
      || warn "docker compose failed — systemd/manual worker is fine"
  else
    log "docker not usable — skipped compose up"
  fi
}

write_marker() {
  cat > "$WORKSPACE/COMPUTER_READY.json" << JSON
{
  "ok": true,
  "service": "meow-computer",
  "workspace": "$WORKSPACE",
  "health": "http://127.0.0.1:8766/health",
  "installedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "user": "$(id -un)"
}
JSON
  # Convenience symlink for discoverability
  ln -sfn "$WORKSPACE" "$HOME/meow-computer" 2>/dev/null || true
}

# --- main ---
log "install begin"
if have_cmd apt-get; then
  install_pkgs_apt
  install_desktop_optional
else
  warn "non-apt distro — ensure openssh-client tmux curl git python3 are present"
fi
install_worker
write_compose
write_systemd_unit
write_marker

# Start worker if nothing listening yet
if ! curl -fsS --max-time 2 http://127.0.0.1:8766/health >/dev/null 2>&1; then
  nohup python3 "$WORKSPACE/bin/meow-computer-worker.py" \
    >"$WORKSPACE/logs/worker.out" 2>&1 &
  echo $! >"$WORKSPACE/logs/worker.pid"
  sleep 1
fi

if curl -fsS --max-time 3 http://127.0.0.1:8766/health; then
  echo
  log "READY — health OK"
else
  warn "worker not responding yet; start: python3 $WORKSPACE/bin/meow-computer-worker.py"
fi
log "install complete workspace=$WORKSPACE"
