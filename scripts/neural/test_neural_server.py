import base64
import hashlib
import hmac
import http.client
import json
from pathlib import Path
import os
import signal
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import unittest

from install_neural_server import verify


class PackageTest(unittest.TestCase):
    def test_corruption_and_path_escape_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'model').write_bytes(b'original')
            manifest = root / 'SHA256SUMS.json'
            manifest.write_text(json.dumps({'model': hashlib.sha256(b'original').hexdigest()}))
            verify(root)
            (root / 'model').write_bytes(b'corrupt')
            with self.assertRaises(ValueError): verify(root)
            manifest.write_text(json.dumps({'../outside': '0' * 64}))
            with self.assertRaises(ValueError): verify(root)


class LiveServiceTest(unittest.TestCase):
    def test_tls_pairing_authentication_and_neural_only_surface(self):
        repo = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as tmp:
            state = Path(tmp)
            with socket.socket() as reserve:
                reserve.bind(('127.0.0.1', 0)); port = reserve.getsockname()[1]
            (state / 'config.json').write_text(json.dumps({'name': 'Synthetic test server', 'port': port,
                'workspace_roots': [], 'malecns_enabled': False}))
            log = (state / 'test.log').open('w')
            process = subprocess.Popen([sys.executable, str(Path(__file__).with_name('neural_server.py')),
                '--root', str(repo), '--state', str(state), '--bind', '127.0.0.1', '--advertise-host', '127.0.0.1'],
                stdout=log, stderr=log)
            try:
                for _ in range(100):
                    if process.poll() is not None: self.fail('Service exited during startup')
                    try:
                        with socket.create_connection(('127.0.0.1', port), timeout=.1): break
                    except OSError: time.sleep(.05)
                else: self.fail('Service did not listen')
                context = ssl.create_default_context(cafile=str(state / 'cert.pem'))
                secret = os.urandom(32)

                def post(path, data, signed=False):
                    body = json.dumps(data).encode()
                    headers = {'Content-Type': 'application/json'}
                    if signed:
                        ts = str(int(time.time()))
                        message = f'POST\n/rpc\n{ts}\n{hashlib.sha256(body).hexdigest()}'.encode()
                        headers.update({'X-Meowmba-Timestamp': ts,
                            'X-Meowmba-Signature': base64.b64encode(hmac.new(secret, message, hashlib.sha256).digest()).decode()})
                    client = http.client.HTTPSConnection('127.0.0.1', port, context=context, timeout=3)
                    try:
                        client.request('POST', path, body, headers)
                        result = client.getresponse()
                        return result.status, json.loads(result.read())
                    finally: client.close()

                self.assertEqual(428, post('/rpc', {'method': 'ping'})[0])
                self.assertEqual(409, post('/pair', {})[0])
                process.send_signal(signal.SIGUSR1)
                payload_path = state / 'pairing-payload.txt'
                for _ in range(60):
                    if payload_path.exists(): break
                    time.sleep(.05)
                self.assertEqual(0o600, payload_path.stat().st_mode & 0o777)
                payload = json.loads(base64.urlsafe_b64decode(payload_path.read_text()))
                pairing = {'token': payload['pair_token'], 'phone_name': 'Synthetic test phone',
                           'secret': base64.b64encode(secret).decode()}
                self.assertEqual(200, post('/pair', pairing)[0])
                self.assertEqual(409, post('/pair', pairing)[0])
                self.assertEqual(401, post('/rpc', {'method': 'ping'})[0])
                self.assertEqual(200, post('/rpc', {'method': 'ping'}, True)[0])
                for method in ('read_file', 'write_file', 'run_script', 'open_app', 'logic_edit', 'status'):
                    self.assertEqual(403, post('/rpc', {'method': method}, True)[0], method)
                self.assertEqual(400, post('/rpc', {'method': 'malecns_step', 'params': {}}, True)[0])
                self.assertEqual(400, post('/rpc', ['bad'], True)[0])
                # A silent non-TLS client cannot block new legitimate connections.
                with socket.create_connection(('127.0.0.1', port), timeout=1):
                    self.assertEqual(200, post('/rpc', {'method': 'ping'}, True)[0])
                self.assertFalse(payload_path.exists())
            finally:
                process.terminate()
                process.wait(timeout=5)
                log.close()


if __name__ == '__main__': unittest.main()
