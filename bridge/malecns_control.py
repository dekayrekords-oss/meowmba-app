"""One request-driven MaleCNS worker over the owner's configured private SSH connection.

Called only after bridge HMAC authentication. No camera, audio, paths, shell
commands or free text are accepted from the phone. One in-flight request;
duplicate sequence numbers never advance the simulation twice.
"""
import json
import math
import re
import selectors
import subprocess
import threading

FIELDS = ('food', 'touch', 'loom_left', 'loom_right', 'motion_left', 'motion_right')


def validate(params):
    if not isinstance(params, dict) or set(params) != {'session', 'seq', *FIELDS}:
        raise ValueError('Expected a neural session, sequence and six sensory values')
    if not isinstance(params['session'], str) or not re.fullmatch(r'[a-f0-9-]{36}', params['session']):
        raise ValueError('Invalid neural session')
    if type(params['seq']) is not int or not 1 <= params['seq'] <= 9007199254740991:
        raise ValueError('Invalid neural sequence')
    for key in FIELDS:
        value = params[key]
        if type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 1:
            raise ValueError('Invalid sensory value')
    return '\t'.join([params['session'], str(params['seq']), *[str(float(params[k])) for k in FIELDS]]) + '\n'


class MaleCnsController:
    def __init__(self, popen=subprocess.Popen, idle_seconds=15):
        self._popen = popen
        self._lock = threading.Lock()
        self._process = None
        self._timer = None
        self._session = None
        self._idle_seconds = idle_seconds
        self._generation = 0

    def _idle_stop(self, generation):
        with self._lock:
            if generation == self._generation: self._close()

    def _close(self):
        if self._timer: self._timer.cancel(); self._timer = None
        process, self._process = self._process, None
        self._session = None
        if process:
            # EOF stops the remote worker too; terminate is a bounded fallback.
            try: process.stdin.close()
            except (OSError, ValueError): pass
            try: process.wait(timeout=.5)
            except subprocess.TimeoutExpired:
                process.terminate()
                try: process.wait(timeout=.5)
                except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=1)
            if process.stdout: process.stdout.close()

    def stop(self, session=None):
        with self._lock:
            if session is None or session == self._session: self._close()
        return {'stopped': True}

    def step(self, params, config):
        line = validate(params)
        # argv is local owner config, never phone-provided. subprocess uses no shell.
        argv = config.get('malecns_worker_argv') if config.get('malecns_enabled') else None
        if not isinstance(argv, list) or not argv or any(not isinstance(x, str) or not x for x in argv):
            raise ValueError('Enable the verified MaleCNS worker in the computer helper')
        if not self._lock.acquire(blocking=False): raise RuntimeError('Neural worker busy; no request queued')
        try:
            self._generation += 1
            if self._timer: self._timer.cancel()
            if self._process is None or self._process.poll() is not None:
                self._close()
                self._process = self._popen(argv, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                             stderr=subprocess.DEVNULL, bufsize=0)
            self._session = params['session']
            self._process.stdin.write(line.encode('ascii'))
            self._process.stdin.flush()
            with selectors.DefaultSelector() as selector:
                selector.register(self._process.stdout, selectors.EVENT_READ)
                response = bytearray()
                # Total deadline, including partial output; one small JSON response only.
                import time
                end = time.monotonic() + 8
                while len(response) <= 4096:
                    remaining = end - time.monotonic()
                    if remaining <= 0 or not selector.select(remaining): raise TimeoutError('Neural worker timed out')
                    byte = self._process.stdout.read(1)
                    if not byte: raise RuntimeError('Neural worker disconnected')
                    if byte == b'\n': break
                    response.extend(byte)
                else: raise ValueError('Oversized neural response')
            result = json.loads(response)
            if result.get('session') != params['session'] or result.get('seq') != params['seq'] or 'error' in result:
                raise ValueError('Neural session or sequence mismatch')
            self._timer = threading.Timer(self._idle_seconds, self._idle_stop, args=(self._generation,))
            self._timer.daemon = True
            self._timer.start()
            return result
        except Exception:
            self._close()
            raise
        finally:
            self._lock.release()
