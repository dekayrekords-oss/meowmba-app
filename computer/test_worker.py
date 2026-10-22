import importlib.util, json, os, socket, subprocess, sys, tempfile, time, unittest, urllib.request, urllib.error
from pathlib import Path

class WorkerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.home = tempfile.TemporaryDirectory()
        cls.token = 'test-only-worker-token-120-never-production'
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0)); port = sock.getsockname()[1]
        cls.url = f'http://127.0.0.1:{port}'
        cls.proc = subprocess.Popen([sys.executable, str(Path(__file__).with_name('worker.py'))], env={**os.environ, 'MEOW_COMPUTER_HOME': cls.home.name, 'MEOW_COMPUTER_TOKEN': cls.token, 'MEOW_COMPUTER_PORT': str(port)})
        for _ in range(100):
            try: cls.request('/health'); break
            except OSError: time.sleep(.02)
        else: raise AssertionError('Worker did not start')
    @classmethod
    def tearDownClass(cls):
        cls.proc.terminate(); cls.proc.wait(timeout=5); cls.home.cleanup()
    @classmethod
    def request(cls, path, payload=None, token=None):
        req = urllib.request.Request(cls.url + path, data=None if payload is None else json.dumps(payload).encode(), headers={'Authorization': 'Bearer ' + (cls.token if token is None else token)})
        with urllib.request.urlopen(req, timeout=3) as result: return json.load(result)
    def job(self, script, timeout=2000):
        job = self.request('/v1/jobs', {'argv': [sys.executable, '-c', script], 'timeoutMs': timeout})['jobId']
        for _ in range(300):
            result = self.request('/v1/jobs/' + job)
            if result['status'] not in ('queued', 'running'): return result
            time.sleep(.01)
        self.fail('Job did not finish')
    def test_authentication(self):
        with self.assertRaises(urllib.error.HTTPError) as error: self.request('/health', token='wrong')
        self.assertEqual(401, error.exception.code)
    def test_exit_and_logs(self):
        result = self.job('import sys; print("real-output"); sys.exit(7)')
        self.assertEqual(7, result['exitCode']); self.assertEqual('failed', result['status'])
        self.assertIn('real-output', result['stdout'])
    def test_bounded_output(self):
        result = self.job('import sys; sys.stdout.write("x"*1000000); sys.stderr.write("y"*1000000)')
        self.assertEqual(0, result['exitCode'])
        self.assertEqual(200000, len(result['stdout'])); self.assertEqual(200000, len(result['stderr']))
    def test_timeout(self):
        result = self.job('import time; time.sleep(10)', timeout=100)
        self.assertEqual(124, result['exitCode'])
    def test_bad_payload(self):
        with self.assertRaises(urllib.error.HTTPError) as error: self.request('/v1/jobs', {'argv': [], 'timeoutMs': -1})
        self.assertEqual(400, error.exception.code)
if __name__ == '__main__': unittest.main()
