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
