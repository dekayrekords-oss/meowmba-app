import contextlib
import gzip
import hashlib
import io
import json
import pathlib
import tempfile
import unittest
from apk_delta import main


class DeltaApplyTest(unittest.TestCase):
    def test_streamed_copy_and_literal_match_target(self):
        self.exercise()

    def test_corruption_preserves_existing_output_and_removes_partial(self):
        for mutation in ('base', 'copy', 'target', 'bounds'):
            with self.subTest(mutation=mutation): self.exercise(mutation)

    def exercise(self, mutation=None):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            data = b'abcd' * 600000
            base = root / 'base.apk'; base.write_bytes(data)
            output = root / 'output.apk'; output.write_bytes(b'previous valid artifact')
            copied = data[13:-17]; expected = copied + b'hello'
            sha = lambda value: hashlib.sha256(value).hexdigest()
            patch = dict(base=sha(data), target=sha(expected), size=len(expected), parts=[
                dict(copy=13, size=len(copied), sha256=sha(copied)), dict(data='aGVsbG8=')])
            if mutation == 'base': patch['base'] = '0' * 64
            if mutation == 'copy': patch['parts'][0]['sha256'] = '0' * 64
            if mutation == 'target': patch['target'] = '0' * 64
            if mutation == 'bounds': patch['parts'][0]['size'] = len(data) + 1
            path = root / 'patch.gz'
            with gzip.open(path, 'wt') as stream: json.dump(patch, stream)
            with contextlib.redirect_stdout(io.StringIO()):
                if mutation:
                    with self.assertRaises(ValueError): main(['apply', str(base), str(path), str(output)])
                    self.assertEqual(b'previous valid artifact', output.read_bytes())
                else:
                    main(['apply', str(base), str(path), str(output)])
                    self.assertEqual(expected, output.read_bytes())
            self.assertFalse(list(root.glob('*.partial')))


if __name__ == '__main__': unittest.main()
