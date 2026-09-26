"""Meow's user-operated Ubuntu development menu. No HTTP command executor.

Only launched from the Development Studio terminal. Claude/OpenCode retain their
own authentication, workspace trust and permission prompts. Not a sandbox: project
build scripts and approved agents run with the Ubuntu user's existing authority.
"""
import difflib
import fcntl
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import secrets
import signal
import shutil
import stat
import subprocess
import tempfile
import threading
import urllib.parse

SKIP = {'.git', '.gradle', 'node_modules', 'build', 'dist', '.next', '.venv', '__pycache__'}
MAX_FILE, MAX_TOTAL, MAX_FILES = 2 * 1024 * 1024, 32 * 1024 * 1024, 2000


class BoundedHTTPServer(http.server.ThreadingHTTPServer):
    """Avoid one idle loopback client spawning unbounded threads or holding a socket forever."""
    daemon_threads = True

    def __init__(self, *args, **kwargs):
        self.slots = threading.BoundedSemaphore(4)
        super().__init__(*args, **kwargs)

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(10)
        return connection, address

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


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe(root, relative):
    parts = Path(relative).parts
    if not parts or Path(relative).is_absolute() or any(p in ('..', '.') for p in parts):
        raise ValueError('Use a relative project file path.')
    current = root
    if root.is_symlink():
        raise ValueError('Linked project roots are not supported.')
    for part in parts:
        current = current / part
        if current.is_symlink():
            raise ValueError('Linked files/directories are not supported: ' + relative)
    if root.resolve() not in current.resolve().parents:
        raise ValueError('Path escapes project.')
    return current


def read_file(path):
    # Reject device files/FIFOs and final symlinks without opening them for reading.
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_size > MAX_FILE:
        raise ValueError('Unsupported or oversized source file: ' + str(path))
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise ValueError('File type changed.')
        data = stream.read(MAX_FILE + 1)
    if len(data) > MAX_FILE:
        raise ValueError('Source file exceeds 2 MiB.')
    return data, stat.S_IMODE(info.st_mode)


class Review:
    def __init__(self, root, state):
        self.root, self.state = root, state
        state.mkdir(parents=True, exist_ok=True, mode=0o700)
        if state.is_symlink():
            raise ValueError('Linked review storage is not supported.')
        self.objects = state / 'objects'
        self.objects.mkdir(exist_ok=True, mode=0o700)
        if self.objects.is_symlink():
            raise ValueError('Linked object storage is not supported.')
        self.manifest = state / 'checkpoint.json'

    def load(self):
        if not self.manifest.exists():
            return None
        data, _ = read_file(self.manifest)
        entries = json.loads(data)
        if not isinstance(entries, dict) or len(entries) > MAX_FILES:
            raise ValueError('Invalid checkpoint.')
        for name, item in entries.items():
            safe(self.root, name)
            if not re.fullmatch('[0-9a-f]{64}', item['sha']) or not isinstance(item['mode'], int):
                raise ValueError('Invalid checkpoint entry.')
        return entries

    def save(self, entries):
        fd, name = tempfile.mkstemp(prefix='.checkpoint-', dir=self.state)
        try:
            with os.fdopen(fd, 'w') as out:
                json.dump(entries, out, sort_keys=True)
                out.flush()
                os.fsync(out.fileno())
            os.replace(name, self.manifest)
        finally:
            if os.path.exists(name):
                os.unlink(name)

    def store(self, data):
        key = digest(data)
        dest = self.objects / key
        if dest.exists():
            if read_file(dest)[0] != data:
                raise ValueError('Checkpoint object failed verification.')
        else:
            if sum(p.lstat().st_size for p in self.objects.iterdir()) + len(data) > 128 * 1024 * 1024:
                raise ValueError('Review storage reached 128 MiB. Export/archive old review storage first.')
            with dest.open('xb') as out:
                out.write(data)
        return key

    def scan(self, store=False):
        result, total = {}, 0
        for folder, dirs, files in os.walk(self.root, followlinks=False):
            dirs[:] = sorted(d for d in dirs if d not in SKIP)
            for d in dirs:
                if (Path(folder) / d).is_symlink():
                    raise ValueError('Linked source directory: ' + d)
            for name in sorted(files):
                if name == '.env' or name.startswith('.env.') or name.endswith(('.pem', '.key', '.jks', '.keystore')):
                    continue
                path = safe(self.root, str((Path(folder) / name).relative_to(self.root)))
                data, mode = read_file(path)
                total += len(data)
                if total > MAX_TOTAL or len(result) >= MAX_FILES:
                    raise ValueError('Checkpoint exceeds 32 MiB or 2,000 source files. Use Git for larger projects.')
                result[str(path.relative_to(self.root))] = {'sha': self.store(data) if store else digest(data), 'mode': mode}
        return result

    def checkpoint(self):
        old = self.load()
        if old is not None and self.scan() != old:
            raise ValueError('Unreviewed changes exist. Keep/Undo those first; baseline preserved.')
        current = self.scan(store=True)
        self.save(current)
        return len(current)

    def changes(self):
        before = self.load()
        if before is None:
            raise ValueError('Create a checkpoint BEFORE asking an agent to edit.')
        after = self.scan()
        return before, after, [p for p in sorted(before.keys() | after.keys()) if before.get(p) != after.get(p)]

    def diff(self, name, before, after):
        old = read_file(self.objects / before[name]['sha'])[0] if name in before else b''
        new = read_file(safe(self.root, name))[0] if name in after else b''
        if name in after and digest(new) != after[name]['sha']:
            raise ValueError('File changed during review; refresh.')
        if b'\0' in old + new:
            return 'Binary file: ' + str(len(old)) + ' -> ' + str(len(new)) + ' bytes'
        # Bounded even for generated one-line/minified content; no quadratic full-file LCS.
        if max(len(old), len(new)) > 64_000:
            return 'Large file: inspect externally. SHA-256 before/after: ' + str(before.get(name)) + ' / ' + str(after.get(name))
        lines = difflib.unified_diff(old.decode('utf-8', 'replace').splitlines(),
                                     new.decode('utf-8', 'replace').splitlines(), 'before/' + name, 'after/' + name)
        import itertools
        return '\n'.join(itertools.islice(lines, 300)) + '\n(diff display limited to 300 lines; mode changes shown in review entries)'

    def resolve(self, name, expected, keep):
        before = self.load()
        if before is None:
            raise ValueError('No checkpoint.')
        current = self.scan()
        if current.get(name) != expected:
            raise ValueError('File changed since review. Nothing overwritten; review again.')
        target = safe(self.root, name)
        if keep:
            if name in current:
                data, _ = read_file(target)
                if digest(data) != current[name]['sha']:
                    raise ValueError('File changed during review.')
                self.store(data)
                before[name] = current[name]
            else:
                before.pop(name, None)
            self.save(before)
        else:
            # Retain the discarded content as a content-addressed recovery object.
            if name in current:
                data, _ = read_file(target)
                if digest(data) != current[name]['sha']:
                    raise ValueError('File changed during review.')
                print('Recovery object:', self.store(data), flush=True)
            if name not in before:
                target.unlink()
            else:
                data, _ = read_file(self.objects / before[name]['sha'])
                if digest(data) != before[name]['sha']:
                    raise ValueError('Checkpoint content failed verification.')
                target.parent.mkdir(parents=True, exist_ok=True)
                safe(self.root, name)
                fd, temp = tempfile.mkstemp(prefix='.meow-undo-', dir=target.parent)
                try:
                    with os.fdopen(fd, 'wb') as out:
                        out.write(data)
                        out.flush()
                        os.fsync(out.fileno())
                    os.chmod(temp, before[name]['mode'] & 0o777)
                    os.replace(temp, target)
                finally:
                    if os.path.exists(temp):
                        os.unlink(temp)


def run(argv, root, env=None):
    print('Running:', ' '.join(argv), flush=True)
    # The PTY sends Ctrl-C to the entire foreground process group. Let the child
    # handle it without returning to this menu while an interactive agent is alive.
    previous = signal.signal(signal.SIGINT, lambda *_: None)
    try:
        result = subprocess.run(argv, cwd=root, check=False, env=env)
    finally:
        signal.signal(signal.SIGINT, previous)
    print('Exit code:', result.returncode, flush=True)
    if result.returncode:
        raise ValueError('Command failed; no success or artifact is claimed.')


def preview(root):
    # Serve only explicitly selected public output, never the project root or dotfiles.
    name = input('Public folder (site or dist): ').strip()
    if name not in ('site', 'dist'):
        raise ValueError('Choose site or dist.')
    public = safe(root, name)
    if not public.is_dir():
        raise ValueError('Build/create the public folder first.')

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            try:
                if self.headers.get('Host') != '127.0.0.1:8765':
                    raise ValueError('Invalid host')
                relative = urllib.parse.unquote(urllib.parse.urlsplit(self.path).path).lstrip('/') or 'index.html'
                if any(p.startswith('.') for p in Path(relative).parts):
                    raise ValueError('Private path')
                path = safe(public, relative)
                data, _ = read_file(path)
                import mimetypes
                self.send_response(200)
                self.send_header('Content-Type', mimetypes.guess_type(path.name)[0] or 'application/octet-stream')
                self.send_header('Content-Length', str(len(data)))
                self.send_header('Cache-Control', 'no-store')
                self.send_header('X-Content-Type-Options', 'nosniff')
                self.send_header('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'")
                self.end_headers()
                self.wfile.write(data)
            except (ValueError, OSError):
                self.send_error(404)

        def log_message(self, *_):
            pass

    server = BoundedHTTPServer(('127.0.0.1', 8765), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print('Preview: http://127.0.0.1:8765/ — use Open preview. Public files are accessible to other apps on this phone.')
    return server


def export_apk(path):
    """One exact build output, loopback-only with an unguessable temporary capability.

    No directory browsing, user-supplied paths, cookies or Computer credentials.
    Android rechecks size/hash and shows the package before the system installer.
    """
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or not 0 < info.st_size <= 512 * 1024 * 1024:
        raise ValueError('APK must be a regular file between 1 byte and 512 MiB.')
    token = secrets.token_hex(24)
    h = hashlib.sha256()
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, 'rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            h.update(chunk)
    expected = h.hexdigest()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            if self.headers.get('Host') != '127.0.0.1:8766' or not secrets.compare_digest(self.path, '/' + token):
                self.send_error(404)
                return
            try:
                fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
                with os.fdopen(fd, 'rb') as source:
                    now = os.fstat(source.fileno())
                    if not stat.S_ISREG(now.st_mode) or now.st_size != info.st_size or now.st_mtime_ns != info.st_mtime_ns:
                        self.send_error(409, 'Rebuild/export required')
                        return
                    self.send_response(200)
                    self.send_header('Content-Type', 'application/vnd.android.package-archive')
                    self.send_header('Content-Length', str(info.st_size))
                    self.send_header('Cache-Control', 'no-store')
                    self.end_headers()
                    # Limit a concurrent changing file; the Android receiver verifies its hash.
                    remaining = info.st_size
                    while remaining:
                        chunk = source.read(min(1024 * 1024, remaining))
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        remaining -= len(chunk)
            except OSError:
                return

        def log_message(self, *_):
            pass  # Capability tokens must not enter request logs.

    server = BoundedHTTPServer(('127.0.0.1', 8766), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print('MEOW_APK ' + token + ' ' + expected + ' ' + str(info.st_size), flush=True)
    print('Use Fetch built APK in Meow, inspect its package, then request Android installation. Keep this session open.')
    return server


def main():
    os.umask(0o077)
    home = Path.home()
    base = home / 'MeowProjects'
    if base.is_symlink():
        raise ValueError('MeowProjects must not be linked.')
    base.mkdir(exist_ok=True)
    print('Meow Development Studio — existing Ubuntu, no extra always-on worker.\n'
          'Build scripts and agents can access your Ubuntu files. Only open trusted projects.\n'
          'Checkpoints exclude dependencies/build output, .env files and private keys.\n'
          'Stop all other editors/agents before reviewing or undoing files.')
    name = input('Project name inside ~/MeowProjects (letters, digits, - or _): ').strip()
    if not re.fullmatch('[A-Za-z0-9][A-Za-z0-9_-]{0,63}', name):
        raise ValueError('Invalid project name.')
    root = safe(base, name)
    root.mkdir(exist_ok=True)
    state_base = home / '.meow-development'
    if state_base.is_symlink():
        raise ValueError('Linked development state is not supported.')
    state_base.mkdir(exist_ok=True, mode=0o700)
    review = Review(root, state_base / digest(str(root).encode()))
    with (review.state / 'lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        print('Project:', root, '\nReview/recovery data:', review.state)
        server = None
        apk_server = None
        try:
            while True:
                print('\n1 Check tools  2 Checkpoint  3 Review/Keep/Undo\n'
                      '4 Claude Code  5 OpenCode  6 Create starter website\n'
                      '7 npm install/build  8 Preview  9 Android debug build\n'
                      '10 Install Claude Code  11 Stop preview/export  12 Install build prerequisites  0 Exit', flush=True)
                choice = input('Action: ').strip()
                try:
                    if choice == '0':
                        break
                    if choice == '1':
                        for tool in ('python3', 'git', 'node', 'npm', 'java', 'claude', 'opencode'):
                            print(tool + ': ' + (shutil.which(tool) or 'NOT INSTALLED'))
                        print('Android SDK:', os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or 'NOT CONFIGURED')
                    elif choice == '2':
                        print('Checkpoint saved:', review.checkpoint(), 'source files.')
                    elif choice == '3':
                        before, after, changed = review.changes()
                        print('Changed files:', len(changed))
                        for index, path in enumerate(changed):
                            print(index + 1, repr(path), before.get(path), '->', after.get(path))
                        if changed:
                            index = input('File number (Enter to return): ').strip()
                            if not index:
                                continue
                            if not index.isdigit() or not 1 <= int(index) <= len(changed):
                                raise ValueError('Invalid file number.')
                            path = changed[int(index) - 1]
                            print(review.diff(path, before, after))
                            decision = input('Type keep or undo for this file; Enter leaves it pending: ').strip()
                            if decision in ('keep', 'undo'):
                                review.resolve(path, after.get(path), decision == 'keep')
                                print('File decision saved.')
                    elif choice in ('4', '5'):
                        review.checkpoint()  # Never overwrite an unreviewed baseline.
                        tool = 'claude' if choice == '4' else 'opencode'
                        print('Uses your own provider/account; charges may apply. Follow CLI trust/permission prompts.')
                        executable = shutil.which(tool) or (str(home / '.local/bin/claude') if tool == 'claude' else tool)
                        args = [executable, '--permission-mode', 'default', '--ax-screen-reader'] if tool == 'claude' else [executable]
                        env = os.environ.copy()
                        if tool == 'opencode':
                            env['OPENCODE_CONFIG_CONTENT'] = '{"permission":{"*":"ask"}}'
                            env['OPENCODE_DISABLE_FFF'] = '1'
                        run(args, root, env)
                        print('Agent exited. Review source changes with action 3.')
                    elif choice == '6':
                        target = safe(root, 'site/index.html')
                        target.parent.mkdir(exist_ok=True)
                        with target.open('x') as out:
                            out.write('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>My website</title><style>body{font:20px system-ui;max-width:48rem;margin:4rem auto;padding:1rem;background:#101824;color:#eaf4ff}button{padding:1rem}</style><h1>My website</h1><p>Ask Claude Code or OpenCode to build your site in this folder.</p></html>')
                        print('Created site/index.html without replacing existing files.')
                    elif choice == '7':
                        print('npm runs project/dependency scripts and uses the network.')
                        if input('Type build to approve npm ci (or install) and npm run build: ') == 'build':
                            run(['npm', 'ci' if (root / 'package-lock.json').exists() else 'install'], root)
                            run(['npm', 'run', 'build'], root)
                    elif choice == '8':
                        if server:
                            raise ValueError('Stop the current preview first (11).')
                        server = preview(root)
                    elif choice == '9':
                        if input('Runs project Gradle scripts/downloads. Type build to approve :app:assembleDebug: ') == 'build':
                            wrapper = safe(root, 'gradlew')
                            if not wrapper.is_file():
                                raise ValueError('Create/import an Android Gradle project first (app module). Requires JDK, SDK and ARM64-compatible aapt2 on phones.')
                            args = ['bash', str(wrapper), '--no-daemon', '--max-workers=2', ':app:assembleDebug', '--console=plain']
                            override = os.environ.get('MEOW_AAPT2')
                            if override:
                                if not Path(override).is_file() or not os.access(override, os.X_OK):
                                    raise ValueError('MEOW_AAPT2 must name your executable phone-compatible aapt2.')
                                args.append('-Pandroid.aapt2FromMavenOverride=' + override)
                            if apk_server:
                                apk_server.shutdown()
                                apk_server.server_close()
                                apk_server = None
                            run(args, root)
                            metadata = safe(root, 'app/build/outputs/apk/debug/output-metadata.json')
                            info = json.loads(read_file(metadata)[0])
                            if info.get('variantName') != 'debug' or not info.get('elements'):
                                raise ValueError('No verified debug APK metadata.')
                            if len(info['elements']) != 1:
                                raise ValueError('Split APK installation is not supported. Build a universal debug APK.')
                            for element in info['elements']:
                                filename = element['outputFile']
                                if '/' in filename or '\\' in filename or not filename.endswith('.apk'):
                                    raise ValueError('Unexpected APK output path.')
                                apk = safe(root, 'app/build/outputs/apk/debug/' + filename)
                                print('Built APK:', apk)
                                apk_server = export_apk(apk)
                            print('Not installed. Android requires your separate installation confirmation.')
                    elif choice == '10':
                        print('Downloads and runs Anthropic’s official stable installer; does not sign in or purchase anything.')
                        if input('Type install to agree: ') == 'install':
                            with tempfile.TemporaryDirectory(prefix='meow-claude-') as temp:
                                installer = str(Path(temp) / 'install.sh')
                                run(['curl', '--fail', '--show-error', '--silent', '--location', '--proto', '=https', '--proto-redir', '=https', '--max-time', '120', '--max-filesize', '1048576', 'https://claude.ai/install.sh', '-o', installer], root)
                                run(['bash', installer, 'stable'], root)
                            print('Installer exited successfully. Launch Claude Code (4) to authenticate; PRoot compatibility must be checked on your phone.')
                    elif choice == '11':
                        if server:
                            server.shutdown()
                            server.server_close()
                            server = None
                        if apk_server:
                            apk_server.shutdown()
                            apk_server.server_close()
                            apk_server = None
                        print('Preview/export stopped.')
                    elif choice == '12':
                        print('Installs Ubuntu packages: git, curl, Node/npm, OpenJDK 17 and unzip. Does not install Android SDK or accept its licenses.')
                        if input('Type install to agree to package downloads and disk usage: ') == 'install':
                            run(['apt-get', 'update'], root)
                            run(['apt-get', 'install', '-y', 'git', 'curl', 'nodejs', 'npm', 'openjdk-17-jdk', 'unzip'], root)
                            print('Prerequisites installed. Check action 1. Frameworks may require a newer Node/JDK version than your Ubuntu repositories provide.')
                except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
                    print('Stopped:', str(error), flush=True)
                except KeyboardInterrupt:
                    print('\nInterrupted. Review files before restarting a task.', flush=True)
        finally:
            if server:
                server.shutdown()
                server.server_close()
            if apk_server:
                apk_server.shutdown()
                apk_server.server_close()


if __name__ == '__main__':
    try:
        main()
    except (EOFError, KeyboardInterrupt):
        print('\nDevelopment Studio closed.')
    except (OSError, ValueError) as error:
        print('Development Studio unavailable:', str(error))
        raise SystemExit(1)
