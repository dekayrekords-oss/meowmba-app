import json
import sys
import unittest
from malecns_control import MaleCnsController, validate


class MaleCnsTest(unittest.TestCase):
    def request(self, seq=1):
        return dict(session='a' * 36, seq=seq, food=1., touch=0., loom_left=0., loom_right=0., motion_left=0., motion_right=0.)

    def test_rejects_unbounded_non_numeric_and_extra_inputs(self):
        for key, value in [('food', float('nan')), ('touch', float('inf')), ('food', -1), ('food', 2), ('food', True), ('seq', True), ('seq', 1.5), ('session', '$(touch /tmp/anything)')]:
            req = self.request(); req[key] = value
            with self.assertRaises(ValueError): validate(req)
        req = self.request(); req['command'] = 'shell'
        with self.assertRaises(ValueError): validate(req)

    def test_disabled_worker_never_launches(self):
        c = MaleCnsController(popen=lambda *a, **k: self.fail('launched disabled worker'))
        with self.assertRaises(ValueError): c.step(self.request(), {})

    def test_process_reused_stop_is_session_specific_and_idle_lease_is_current(self):
        c = MaleCnsController()
        code = 'import sys,json\nfor line in sys.stdin:\n f=line.strip().split("\\t");print(json.dumps(dict(session=f[0],seq=int(f[1]))),flush=True)'
        config = dict(malecns_enabled=True, malecns_worker_argv=[sys.executable, '-u', '-c', code])
        try:
            self.assertEqual(1, c.step(self.request(), config)['seq'])
            process = c._process; old = c._generation
            c.step(self.request(2), config)
            self.assertIs(process, c._process)
            c._idle_stop(old); self.assertIs(process, c._process)
            c.stop('b' * 36); self.assertIs(process, c._process)
            c.stop('a' * 36); self.assertIsNone(c._process)
        finally: c.stop()

    def test_mismatched_or_malformed_response_closes_worker(self):
        for response in ['{}', 'not-json', '{"session":"other","seq":1}']:
            c = MaleCnsController()
            config = dict(malecns_enabled=True, malecns_worker_argv=[sys.executable, '-u', '-c',
                'import sys;sys.stdin.readline();print(' + repr(response) + ',flush=True)'])
            with self.assertRaises((ValueError, RuntimeError)): c.step(self.request(), config)
            self.assertIsNone(c._process)

    def test_busy_worker_does_not_queue(self):
        c = MaleCnsController(); c._lock.acquire()
        try:
            with self.assertRaises(RuntimeError): c.step(self.request(), dict(malecns_enabled=True, malecns_worker_argv=['unused']))
        finally: c._lock.release()


if __name__ == '__main__': unittest.main()
