import argparse
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('meow_bridge_host', Path(__file__).with_name('meowmba-bridge.py'))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class PairingHostTest(unittest.TestCase):
    def test_dns_vpn_and_ipv6_are_valid_addresses(self):
        for host in ('brain.example.org', 'meow', 'fly.home.arpa', '100.77.97.236', '2001:db8::1'):
            self.assertEqual(host, bridge._advertise_host(host))
        self.assertEqual('::1', bridge._advertise_host('[::1]'))

    def test_urls_credentials_paths_and_commands_are_rejected(self):
        for host in ('', ' host ', 'https://host', 'user@host', 'host/path', 'host?key=secret',
                     'host#fragment', 'host;command', 'host\ncommand', '-bad.example', 'bad-.example',
                     'bad..example', 'a' * 64 + '.example'):
            with self.subTest(host=host):
                with self.assertRaises(argparse.ArgumentTypeError):
                    bridge._advertise_host(host)

    def test_override_changes_only_payload_host(self):
        cfg = dict(name='Unit test bridge', port=8765)
        with patch.object(bridge, '_lan_ip', return_value='192.0.2.5'), \
                patch.object(bridge, '_cert_fingerprint', return_value='a' * 64), \
                patch.dict(bridge._pair_state, {'token': 'UNITCODE'}):
            original = bridge._pairing_payload(cfg)
            remote = bridge._pairing_payload(cfg, 'fly.example.org')
        self.assertEqual('192.0.2.5', original['host'])
        self.assertEqual('fly.example.org', remote['host'])
        original.pop('host'); remote.pop('host')
        self.assertEqual(original, remote)
        self.assertEqual(dict(name='Unit test bridge', port=8765), cfg)

    def test_invalid_command_line_fails_before_certificate_or_network_setup(self):
        with patch('sys.argv', ['meowmba-bridge.py', '--pair', '--advertise-host', 'https://invalid']), \
                patch.object(bridge, '_ensure_cert') as certificate, \
                patch.object(bridge, 'ThreadingHTTPServer') as server, \
                patch('sys.stderr'):
            with self.assertRaises(SystemExit) as failure:
                bridge.main()
        self.assertEqual(2, failure.exception.code)
        certificate.assert_not_called(); server.assert_not_called()


if __name__ == '__main__':
    unittest.main()
