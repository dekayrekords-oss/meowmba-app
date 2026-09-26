"""Host-side deterministic tests. No downloads, model calls or developer installs."""
import importlib.util
import contextlib
import http.client
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1] / 'app/src/main/assets/computer/development_studio.py'
spec = importlib.util.spec_from_file_location('studio', SOURCE)
studio = importlib.util.module_from_spec(spec)
spec.loader.exec_module(studio)


class StudioTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'project'
        self.root.mkdir()
        self.review = studio.Review(self.root, self.base / 'state')

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def test_undo_modified_retains_recovery(self):
        path = self.write('main.py', 'before\n')
        self.review.checkpoint()
        path.write_text('after\n')
        before, after, changed = self.review.changes()
        self.assertEqual(['main.py'], changed)
        self.assertIn('+after', self.review.diff('main.py', before, after))
        self.review.resolve('main.py', after['main.py'], False)
        self.assertEqual('before\n', path.read_text())
        self.assertTrue((self.review.objects / studio.digest(b'after\n')).exists())

    def test_undo_added_and_deleted(self):
        deleted = self.write('deleted', 'recover')
        self.review.checkpoint()
        deleted.unlink()
        added = self.write('added', 'new')
        _, after, _ = self.review.changes()
        self.review.resolve('deleted', None, False)
        self.review.resolve('added', after['added'], False)
        self.assertEqual('recover', deleted.read_text())
        self.assertFalse(added.exists())

    def test_keep_updates_only_selected_baseline(self):
        self.write('a', 'old')
        self.write('b', 'old')
        self.review.checkpoint()
        self.write('a', 'new')
        self.write('b', 'new')
        _, after, _ = self.review.changes()
        self.review.resolve('a', after['a'], True)
        self.assertEqual(['b'], self.review.changes()[2])

    def test_reject_edit_since_review(self):
        self.write('a', 'old')
        self.review.checkpoint()
        self.write('a', 'agent')
        _, after, _ = self.review.changes()
        self.write('a', 'user')
        for keep in (True, False):
            with self.assertRaises(ValueError):
                self.review.resolve('a', after['a'], keep)
        self.assertEqual('user', (self.root / 'a').read_text())

    def test_checkpoint_does_not_erase_pending_changes(self):
        self.write('a', 'old')
        self.review.checkpoint()
        baseline = self.review.manifest.read_bytes()
        self.write('a', 'new')
        with self.assertRaises(ValueError):
            self.review.checkpoint()
        self.assertEqual(baseline, self.review.manifest.read_bytes())

    def test_no_symlink_or_traversal(self):
        outside = self.base / 'outside'
        outside.write_text('private')
        (self.root / 'link').symlink_to(outside)
        for name in ('../outside', '/outside', 'link'):
            with self.assertRaises(ValueError):
                studio.safe(self.root, name)
        with self.assertRaises(ValueError):
            self.review.checkpoint()

    def test_reject_linked_parent_during_undo(self):
        self.write('src/a', 'old')
        self.review.checkpoint()
        (self.root / 'src/a').unlink()
        (self.root / 'src').rmdir()
        (self.root / 'src').symlink_to(self.base, target_is_directory=True)
        with self.assertRaises(ValueError):
            self.review.resolve('src/a', None, False)

    def test_limits_fail_without_publishing_checkpoint(self):
        self.write('too-large', 'a' * (studio.MAX_FILE + 1))
        with self.assertRaises(ValueError):
            self.review.checkpoint()
        self.assertFalse(self.review.manifest.exists())

    def test_dependency_and_secret_exclusions(self):
        for name in ('node_modules/a', 'build/a', '.git/config', '.env', '.env.local', 'private.pem'):
            self.write(name, 'not checkpointed')
        self.write('src/a', 'included')
        self.assertEqual(1, self.review.checkpoint())
        self.assertEqual(['src/a'], list(self.review.load()))

    def test_checkpoint_survives_reconnect(self):
        self.write('a', 'old')
        self.review.checkpoint()
        self.write('a', 'new')
        second = studio.Review(self.root, self.review.state)
        self.assertEqual(['a'], second.changes()[2])

    def test_corrupted_recovery_not_restored(self):
        self.write('a', 'old')
        self.review.checkpoint()
        self.write('a', 'new')
        before, after, _ = self.review.changes()
        (self.review.objects / before['a']['sha']).write_text('corrupted')
        with self.assertRaises(ValueError):
            self.review.resolve('a', after['a'], False)
        self.assertEqual('new', (self.root / 'a').read_text())

    def test_nonzero_build_does_not_report_success(self):
        with patch.object(studio.subprocess, 'run') as run:
            run.return_value.returncode = 1
            with self.assertRaises(ValueError):
                studio.run(['npm', 'run', 'build'], self.root)
            self.assertFalse(run.call_args.kwargs.get('shell', False))

    def ephemeral_server(self):
        real = studio.BoundedHTTPServer
        return patch.object(studio, 'BoundedHTTPServer',
                            side_effect=lambda address, handler: real(('127.0.0.1', 0), handler))

    def request(self, server, path, host):
        connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=3)
        try:
            connection.request('GET', path, headers={'Host': host})
            response = connection.getresponse()
            return response.status, response.read(), dict(response.getheaders())
        finally:
            connection.close()

    def stop_server(self, server):
        server.shutdown()
        server.server_close()

    def test_preview_denies_private_links_traversal_and_foreign_host(self):
        self.write('site/index.html', '<h1>Preview</h1>')
        self.write('site/.env', 'secret')
        outside = self.base / 'private'
        outside.write_text('private')
        (self.root / 'site/link').symlink_to(outside)
        with self.ephemeral_server(), patch('builtins.input', return_value='site'):
            server = studio.preview(self.root)
        self.addCleanup(self.stop_server, server)
        status, data, headers = self.request(server, '/', '127.0.0.1:8765')
        self.assertEqual(200, status)
        self.assertIn(b'Preview', data)
        self.assertIn("connect-src 'self'", headers['Content-Security-Policy'])
        for path in ('/.env', '/%2e%2e/private', '/link', '/missing'):
            self.assertEqual(404, self.request(server, path, '127.0.0.1:8765')[0])
        self.assertEqual(404, self.request(server, '/', 'attacker.example')[0])

    def test_apk_export_requires_capability_and_detects_changed_build(self):
        apk = self.write('demo.apk', 'not a real APK; transfer fixture')
        transcript = io.StringIO()
        with self.ephemeral_server(), contextlib.redirect_stdout(transcript):
            server = studio.export_apk(apk)
        self.addCleanup(self.stop_server, server)
        receipt = next(line for line in transcript.getvalue().splitlines() if line.startswith('MEOW_APK '))
        _, token, sha, size = receipt.split()
        self.assertEqual(48, len(token))
        self.assertEqual(404, self.request(server, '/wrong', '127.0.0.1:8766')[0])
        self.assertEqual(404, self.request(server, '/' + token, 'attacker.example')[0])
        status, data, _ = self.request(server, '/' + token, '127.0.0.1:8766')
        self.assertEqual(200, status)
        self.assertEqual(int(size), len(data))
        self.assertEqual(sha, studio.digest(data))
        apk.write_text('changed')
        self.assertEqual(409, self.request(server, '/' + token, '127.0.0.1:8766')[0])

    def test_apk_export_rejects_links(self):
        target = self.write('actual.apk', 'fixture')
        link = self.root / 'link.apk'
        link.symlink_to(target)
        with self.assertRaises(ValueError):
            studio.export_apk(link)

    def test_manual_menu_creates_site_then_checkpoints_without_model_call(self):
        with patch.object(studio.Path, 'home', return_value=self.base), \
                patch('builtins.input', side_effect=['my-site', '6', '2', '0']), \
                patch.object(studio.subprocess, 'run') as process, \
                contextlib.redirect_stdout(io.StringIO()):
            studio.main()
        process.assert_not_called()
        self.assertTrue((self.base / 'MeowProjects/my-site/site/index.html').exists())

    def test_claude_uses_interactive_default_permissions_and_screen_reader(self):
        with patch.object(studio.Path, 'home', return_value=self.base), \
                patch('builtins.input', side_effect=['claude-test', '4', '0']), \
                patch.object(studio.shutil, 'which', return_value='/usr/bin/claude'), \
                patch.object(studio.subprocess, 'run') as process, \
                contextlib.redirect_stdout(io.StringIO()):
            process.return_value.returncode = 0
            studio.main()
        self.assertEqual(['/usr/bin/claude', '--permission-mode', 'default', '--ax-screen-reader'], process.call_args.args[0])

    def test_opencode_overrides_permissions_to_ask_without_background_index(self):
        with patch.object(studio.Path, 'home', return_value=self.base), \
                patch('builtins.input', side_effect=['opencode-test', '5', '0']), \
                patch.object(studio.shutil, 'which', return_value='/usr/bin/opencode'), \
                patch.object(studio.subprocess, 'run') as process, \
                contextlib.redirect_stdout(io.StringIO()):
            process.return_value.returncode = 0
            studio.main()
        env = process.call_args.kwargs['env']
        self.assertEqual('{"permission":{"*":"ask"}}', env['OPENCODE_CONFIG_CONTENT'])
        self.assertEqual('1', env['OPENCODE_DISABLE_FFF'])


if __name__ == '__main__':
    unittest.main()
