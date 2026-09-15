#!/usr/bin/env python3
"""Dedicated MaleCNS HTTPS service using the app's existing pinned/HMAC protocol."""
import argparse
import base64
import importlib.util
import json
import os
from pathlib import Path
import signal
import ssl
import sys
import threading
import time


def load_bridge(root):
    sys.path.insert(0, str(root / 'bridge'))
    spec = importlib.util.spec_from_file_location('meow_neural_bridge', root / 'bridge/meowmba-bridge.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    return bridge


def handler_class(bridge, payload_file):
    class NeuralHandler(bridge.BridgeHandler):
        # Bound stalled clients; the model has its own eight-second deadline.
        def setup(self):
            super().setup()
            self.connection.settimeout(12)

        def _read_body(self):
            if self.headers.get('Transfer-Encoding'):
                self.close_connection = True
                self._send(400, {'error': 'Use a bounded content length'})
                return None
            try:
                length = int(self.headers.get('Content-Length', '0'))
            except ValueError:
                self.close_connection = True
                self._send(400, {'error': 'invalid content length'})
                return None
            if not 0 <= length <= 8192:
                self.close_connection = True
                self._send(413, {'error': 'body too large'})
                return None
            return self.rfile.read(length)

        def _handle_pair(self, data):
            if not isinstance(data, dict):
                self._send(400, {'error': 'expected object'})
                return
            super()._handle_pair(data)
            if not bridge._pair_state:
                payload_file.unlink(missing_ok=True)

        def _handle_rpc(self, data, raw_body):
            if not isinstance(data, dict):
                self._send(400, {'error': 'expected object'})
                return
            # Never expose desktop, shell, file, camera, or physical robot tools
            # merely because the same protocol implementation supports them.
            if data.get('method') not in ('ping', 'malecns_step', 'malecns_stop'):
                if self._verify_request(raw_body):
                    self._send(403, {'error': 'This server provides neural simulation only'})
                return
            super()._handle_rpc(data, raw_body)
    return NeuralHandler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--state', type=Path, required=True)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--advertise-host', required=True)
    args = parser.parse_args()
    root, state = args.root.resolve(), args.state.resolve()
    os.umask(0o077)
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    bridge = load_bridge(root)
    advertise = bridge._advertise_host(args.advertise_host)
    bridge.CONFIG_DIR = state
    for key, name in [('CONFIG_PATH', 'config.json'), ('CERT_PATH', 'cert.pem'),
                      ('KEY_PATH', 'key.pem'), ('PAIRED_PATH', 'paired.json'), ('LOG_PATH', 'bridge.log')]:
        setattr(bridge, key, state / name)
    cfg = json.loads(bridge.CONFIG_PATH.read_text())
    bridge._ensure_cert()
    payload_file = state / 'pairing-payload.txt'
    payload_file.unlink(missing_ok=True)

    def pair_window(*_):
        with bridge._pair_lock:
            bridge._pair_state.update(token=bridge._make_pair_code(), expires=time.time() + 600, attempts=0)
            blob = base64.urlsafe_b64encode(json.dumps(bridge._pairing_payload(cfg, advertise)).encode()).decode()
            pending = payload_file.with_suffix('.pending')
            pending.write_text(blob + '\n')
            os.replace(pending, payload_file)
        bridge._log('Neural pairing window opened; payload is in the private state directory')

    signal.signal(signal.SIGUSR1, pair_window)
    def terminate(*_):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, terminate)
    # A bounded request pool prevents unlimited thread creation on a LAN endpoint.
    class Server(bridge.ThreadingHTTPServer):
        daemon_threads = True
        slots = threading.BoundedSemaphore(8)

        def process_request(self, request, address):
            if not self.slots.acquire(blocking=False):
                self.shutdown_request(request)
                return
            try:
                super().process_request(request, address)
            except BaseException:
                self.slots.release()
                raise

        def process_request_thread(self, request, address):
            try:
                super().process_request_thread(request, address)
            finally:
                self.slots.release()

    server = Server((args.bind, cfg['port']), handler_class(bridge, payload_file))
    server.cfg, server.started_at = cfg, time.time()
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(str(bridge.CERT_PATH), str(bridge.KEY_PATH))
    # Perform each handshake in its bounded handler thread; a silent TCP peer
    # must not stall accept() and keep the paired phone from reaching the model.
    server.socket = context.wrap_socket(server.socket, server_side=True, do_handshake_on_connect=False)
    bridge._log(f"Full MaleCNS service listening on {args.bind}:{cfg['port']}; pairing closed")
    try:
        server.serve_forever(poll_interval=.2)
    except KeyboardInterrupt:
        pass
    finally:
        payload_file.unlink(missing_ok=True)
        bridge._malecns.stop()
        server.server_close()


if __name__ == '__main__':
    main()
