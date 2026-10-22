import tempfile
from pathlib import Path
import unittest
import zipfile
from verify_apk_runtime import verify


class RuntimePreservationTest(unittest.TestCase):
    def test_only_generated_profile_changes_are_exempt_and_missing_still_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            baseline, candidate = Path(tmp) / 'before.apk', Path(tmp) / 'after.apk'
            original = {'lib/arm64-v8a/voice.so': b'voice', 'assets/vad.onnx': b'model',
                        'assets/dexopt/baseline.prof': b'old dex profile'}
            def write(path, contents):
                with zipfile.ZipFile(path, 'w') as z:
                    for name, value in contents.items(): z.writestr(name, value)
            write(baseline, original)
            changed = dict(original, **{'assets/dexopt/baseline.prof': b'new dex profile'})
            write(candidate, changed)
            self.assertTrue(verify(baseline, candidate)['passed'])
            for member in ('lib/arm64-v8a/voice.so', 'assets/vad.onnx'):
                write(candidate, dict(changed, **{member: b'different'}))
                self.assertFalse(verify(baseline, candidate)['passed'])
            del changed['assets/dexopt/baseline.prof']
            write(candidate, changed)
            self.assertFalse(verify(baseline, candidate)['passed'])


if __name__ == '__main__': unittest.main()
