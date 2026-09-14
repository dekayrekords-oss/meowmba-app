#!/usr/bin/env bash
# Meow Computer — idempotent server install (no phone-root, no secrets).
# Runs as the SSH user that invoked it. Safe to re-run.
set -euo pipefail

# Port 18766 avoids Desktop Bridge UDP discovery on 8766.
# Bind defaults to 127.0.0.1 (Fold cannot hit that). Options:
#   MEOW_COMPUTER_HOST=100.77.97.236 bash computer/install.sh
#   bash computer/install.sh --bind 100.77.97.236 --port 18766
# Or keep localhost + SSH local-forward from the phone/Termux:
#   ssh -L 18766:127.0.0.1:18766 user@meow
# Day-to-day preferred path: SSH ConnectionProfile (not HTTPS_RUNTIME).


MEOW_LABEL="meow-computer"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { printf '[%s] %s\n' "$MEOW_LABEL" "$*"; }
warn() { printf '[%s] WARN: %s\n' "$MEOW_LABEL" "$*" >&2; }

# Optional: --bind HOST --port PORT (also MEOW_COMPUTER_HOST / MEOW_COMPUTER_PORT)
MEOW_COMPUTER_HOST="${MEOW_COMPUTER_HOST:-127.0.0.1}"
MEOW_COMPUTER_PORT="${MEOW_COMPUTER_PORT:-18766}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --bind|--host)
      [[ $# -ge 2 ]] || { warn "--bind requires an IP"; exit 2; }; MEOW_COMPUTER_HOST="$2"; shift 2 ;;
    --port)
      [[ $# -ge 2 ]] || { warn "--port requires a value"; exit 2; }; MEOW_COMPUTER_PORT="$2"; shift 2 ;;
    -h|--help)
      printf 'Usage: [MEOW_COMPUTER_HOST=..] [MEOW_COMPUTER_PORT=..] %s [--bind HOST] [--port PORT]\n' "$0"
      exit 0 ;;
    *)
      warn "unknown arg: $1"; exit 2 ;;
  esac
done
export MEOW_COMPUTER_HOST MEOW_COMPUTER_PORT
python3 - <<'PYVALID'
import ipaddress, os
host = os.environ['MEOW_COMPUTER_HOST']
if host != 'localhost': ipaddress.ip_address(host)
assert 1 <= int(os.environ['MEOW_COMPUTER_PORT']) <= 65535
PYVALID
log "bind=${MEOW_COMPUTER_HOST} port=${MEOW_COMPUTER_PORT}"

# Prefer /opt when writable; else $HOME
if [[ -w /opt ]] || mkdir -p /opt/meow-computer 2>/dev/null; then
  WORKSPACE="${MEOW_COMPUTER_HOME:-/opt/meow-computer}"
else
  WORKSPACE="${MEOW_COMPUTER_HOME:-$HOME/meow-computer}"
fi
mkdir -p "$WORKSPACE"/{bin,data,logs,workspace}
log "workspace=$WORKSPACE (user=$(id -un) uid=$(id -u))"
# Private token is preserved on reinstall and never printed or passed in argv.
export MEOW_COMPUTER_HOME="$WORKSPACE"
python3 - <<'PYTOKEN'
from pathlib import Path
import os, secrets
p = Path(os.environ['MEOW_COMPUTER_HOME']) / '.worker-token'
if not p.exists():
    fd = os.open(p, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'w') as out: out.write(secrets.token_urlsafe(32))
os.chmod(p, 0o600)
PYTOKEN
probe_worker() {
  python3 - <<'PYPROBE'
import json, os, urllib.request
from pathlib import Path
host = os.environ['MEOW_COMPUTER_HOST']
if host in ('0.0.0.0', '::'): host = '127.0.0.1'
if ':' in host: host = '[' + host + ']'
token = (Path(os.environ['MEOW_COMPUTER_HOME']) / '.worker-token').read_text().strip()
req = urllib.request.Request('http://' + host + ':' + os.environ['MEOW_COMPUTER_PORT'] + '/health', headers={'Authorization': 'Bearer ' + token})
try:
    with urllib.request.urlopen(req, timeout=3) as res:
        data=json.load(res)
        assert data.get('version') == 2 and data.get('ok')
except Exception: raise SystemExit(1)
PYPROBE
}


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
"""Authenticated, bounded Computer worker. Run behind SSH or a private network."""
from __future__ import annotations
import hmac, json, os, signal, subprocess, tempfile, threading, time, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(os.environ.get('MEOW_COMPUTER_HOME', str(Path.home() / 'meow-computer')))
WORK = ROOT / 'workspace'
HOST = os.environ.get('MEOW_COMPUTER_HOST', '127.0.0.1')
PORT = int(os.environ.get('MEOW_COMPUTER_PORT', '18766'))
TOKEN = os.environ.get('MEOW_COMPUTER_TOKEN', '')
if not TOKEN and (ROOT / '.worker-token').is_file():
    TOKEN = (ROOT / '.worker-token').read_text().strip()
JOBS = {}
LOCK = threading.Lock()
MAX_JOBS = 64
MAX_ACTIVE = 2
MAX_OUTPUT = 200_000


def run_job(job_id, argv, cwd, timeout_ms):
    try:
        with tempfile.TemporaryFile() as out, tempfile.TemporaryFile() as err:
            proc = subprocess.Popen(argv, cwd=cwd, stdout=out, stderr=err, start_new_session=True)
            with LOCK:
                JOBS[job_id].update(status='running', process=proc)
            try:
                code = proc.wait(timeout=timeout_ms / 1000)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                proc.wait()
                code = 124
            def tail(stream):
                stream.seek(0, 2)
                stream.seek(max(0, stream.tell() - MAX_OUTPUT))
                return stream.read().decode('utf-8', errors='replace')
            with LOCK:
                job = JOBS[job_id]
                cancelled = job['status'] == 'cancelled'
                job.update(status='cancelled' if cancelled else ('succeeded' if code == 0 else 'failed'),
                           exitCode=code, stdout=tail(out), stderr=tail(err), process=None)
    except Exception as exc:
        with LOCK:
            JOBS[job_id].update(status='failed', exitCode=1, detail=type(exc).__name__, process=None)


class Handler(BaseHTTPRequestHandler):
    server_version = 'MeowComputerWorker/2.0'
    def log_message(self, *_): pass
    def reply(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def authorized(self):
        supplied = self.headers.get('Authorization', '')
        if not TOKEN or not hmac.compare_digest(supplied, 'Bearer ' + TOKEN):
            self.reply(401, {'error': 'Computer worker token required'})
            return False
        return True
    def do_GET(self):
        if not self.authorized(): return
        path = urlparse(self.path).path.rstrip('/')
        if path == '/health':
            self.reply(200, {'ok': True, 'service': 'meow-computer', 'version': 2})
            return
        bits = path.strip('/').split('/')
        if len(bits) in (3, 4) and bits[:2] == ['v1', 'jobs']:
            with LOCK:
                job = JOBS.get(bits[2])
                payload = None if job is None else {k: v for k, v in job.items() if k != 'process'}
            if payload is not None:
                if len(bits) == 4 and bits[3] == 'logs':
                    payload = {'stdout': payload.get('stdout', ''), 'stderr': payload.get('stderr', '')}
                self.reply(200, payload)
                return
        self.reply(404, {'error': 'unknown job'})
    def do_POST(self):
        if not self.authorized(): return
        path = urlparse(self.path).path.rstrip('/')
        if path != '/v1/jobs':
            self.reply(404, {'error': 'not found'}); return
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 65_536: raise ValueError()
            payload = json.loads(self.rfile.read(length))
            argv = payload.get('argv')
            if not isinstance(argv, list) or not 1 <= len(argv) <= 128: raise ValueError()
            if not all(isinstance(x, str) and len(x) <= 32_768 and '\0' not in x for x in argv): raise ValueError()
            timeout = payload.get('timeoutMs', 30_000)
            if type(timeout) is not int or not 1 <= timeout <= 120_000: raise ValueError()
            cwd = Path(payload.get('cwd') or WORK).resolve()
            if not cwd.is_dir(): raise ValueError()
        except (ValueError, TypeError, AttributeError):
            self.reply(400, {'error': 'Invalid argv, directory, timeout or payload size'}); return
        with LOCK:
            active = sum(j['status'] in ('queued', 'running') for j in JOBS.values())
            if active >= MAX_ACTIVE:
                self.reply(429, {'error': 'Computer is busy'}); return
            if len(JOBS) >= MAX_JOBS:
                completed = next((k for k, j in JOBS.items() if j['status'] not in ('queued', 'running')), None)
                if completed: JOBS.pop(completed)
            job_id = uuid.uuid4().hex
            JOBS[job_id] = dict(status='queued', stdout='', stderr='', created=time.time())
        threading.Thread(target=run_job, args=(job_id, argv, str(cwd), timeout), daemon=True).start()
        self.reply(200, {'jobId': job_id})


def main():
    if len(TOKEN) < 24:
        raise SystemExit('Set a Computer worker token of at least 24 characters; refusing unauthenticated execution')
    WORK.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.serve_forever()

if __name__ == '__main__': main()
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
Environment=MEOW_COMPUTER_HOST=$MEOW_COMPUTER_HOST
Environment=MEOW_COMPUTER_PORT=$MEOW_COMPUTER_PORT
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
      MEOW_COMPUTER_HOST: "0.0.0.0"
      MEOW_COMPUTER_PORT: "$MEOW_COMPUTER_PORT"
    command: ["python3", "/opt/meow-computer/bin/meow-computer-worker.py"]
    ports:
      - "$MEOW_COMPUTER_HOST:$MEOW_COMPUTER_PORT:$MEOW_COMPUTER_PORT"
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
  "health": "http://$MEOW_COMPUTER_HOST:$MEOW_COMPUTER_PORT/health",
  "bind": "$MEOW_COMPUTER_HOST",
  "port": $MEOW_COMPUTER_PORT,
  "installedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "user": "$(id -un)"
}
JSON
  # Convenience symlink for discoverability
  ln -sfn "$WORKSPACE" "$HOME/meow-computer" 2>/dev/null || true
}

# --- main ---
log "install begin"
if [[ "${MEOW_COMPUTER_WORKER_ONLY:-0}" != "1" ]] && have_cmd apt-get; then
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
HEALTH_URL="http://${MEOW_COMPUTER_HOST}:${MEOW_COMPUTER_PORT}/health"
# When bind is 0.0.0.0, probe via 127.0.0.1
PROBE_URL="$HEALTH_URL"
if [[ "$MEOW_COMPUTER_HOST" == "0.0.0.0" ]]; then
  PROBE_URL="http://127.0.0.1:${MEOW_COMPUTER_PORT}/health"
fi
if ! probe_worker; then
  nohup env MEOW_COMPUTER_HOME="$WORKSPACE" \
    MEOW_COMPUTER_HOST="$MEOW_COMPUTER_HOST" \
    MEOW_COMPUTER_PORT="$MEOW_COMPUTER_PORT" \
    python3 "$WORKSPACE/bin/meow-computer-worker.py" \
    >"$WORKSPACE/logs/worker.out" 2>&1 &
  echo $! >"$WORKSPACE/logs/worker.pid"
  sleep 1
fi

if probe_worker; then
  echo
  log "READY — health OK at $PROBE_URL"
  if [[ "$MEOW_COMPUTER_HOST" == "127.0.0.1" || "$MEOW_COMPUTER_HOST" == "localhost" ]]; then
    log "localhost-only: Fold cannot reach this. Use SSH -L ${MEOW_COMPUTER_PORT}:127.0.0.1:${MEOW_COMPUTER_PORT} or --bind <NetBird-IP>."
  fi
else
  warn "worker not responding yet; start: MEOW_COMPUTER_HOST=$MEOW_COMPUTER_HOST MEOW_COMPUTER_PORT=$MEOW_COMPUTER_PORT python3 $WORKSPACE/bin/meow-computer-worker.py"
fi
log "Worker requires its private .worker-token in an HTTP profile; SSH profiles use SSH credentials."
log "install complete workspace=$WORKSPACE"
