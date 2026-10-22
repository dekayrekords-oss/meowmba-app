"""Small native AX reader/writer for the fixed Logic adapter (stdlib only).

No remote code, key presses, coordinates or application names are accepted.
Every CoreFoundation object copied during a transaction is released together.
"""
import ctypes as C
import json
import math
from pathlib import Path
import plistlib
import re
import subprocess
import sys
import time


class LogicDispatchUncertain(ValueError):
    """AX timed out after dispatch. Observe once; never resend automatically."""


class LogicAX:
    def __init__(self):
        if sys.platform != 'darwin':
            raise ValueError('Logic control requires macOS')
        self.cf = C.CDLL('/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation')
        self.ax = C.CDLL('/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices')
        signatures = {
            'CFRelease': (None, [C.c_void_p]), 'CFRetain': (C.c_void_p, [C.c_void_p]),
            'CFGetTypeID': (C.c_ulong, [C.c_void_p]),
            'CFHash': (C.c_ulong, [C.c_void_p]), 'CFEqual': (C.c_bool, [C.c_void_p, C.c_void_p]),
            'CFStringCreateWithCString': (C.c_void_p, [C.c_void_p, C.c_char_p, C.c_uint32]),
            'CFStringGetCString': (C.c_bool, [C.c_void_p, C.c_void_p, C.c_long, C.c_uint32]),
            'CFStringGetLength': (C.c_long, [C.c_void_p]),
            'CFNumberGetValue': (C.c_bool, [C.c_void_p, C.c_int, C.c_void_p]),
            'CFNumberCreate': (C.c_void_p, [C.c_void_p, C.c_int, C.c_void_p]),
            'CFBooleanGetValue': (C.c_bool, [C.c_void_p]),
            'CFArrayGetCount': (C.c_long, [C.c_void_p]),
            'CFArrayGetValueAtIndex': (C.c_void_p, [C.c_void_p, C.c_long]),
            'CFArrayCreate': (C.c_void_p, [C.c_void_p, C.c_void_p, C.c_long, C.c_void_p]),
            'CFURLGetString': (C.c_void_p, [C.c_void_p]),
        }
        for name, (ret, args) in signatures.items():
            fn = getattr(self.cf, name); fn.restype = ret; fn.argtypes = args
        for name, ret, args in [
            ('AXIsProcessTrusted', C.c_bool, []),
            ('AXUIElementCreateApplication', C.c_void_p, [C.c_int]),
            ('AXUIElementCreateSystemWide', C.c_void_p, []),
            ('AXUIElementGetPid', C.c_int, [C.c_void_p, C.c_void_p]),
            ('AXUIElementCopyAttributeValue', C.c_int, [C.c_void_p, C.c_void_p, C.c_void_p]),
            ('AXUIElementCopyActionNames', C.c_int, [C.c_void_p, C.c_void_p]),
            ('AXUIElementCopyAttributeNames', C.c_int, [C.c_void_p, C.c_void_p]),
            ('AXUIElementIsAttributeSettable', C.c_int, [C.c_void_p, C.c_void_p, C.c_void_p]),
            ('AXUIElementSetAttributeValue', C.c_int, [C.c_void_p, C.c_void_p, C.c_void_p]),
            ('AXUIElementPerformAction', C.c_int, [C.c_void_p, C.c_void_p]),
            ('AXUIElementSetMessagingTimeout', C.c_int, [C.c_void_p, C.c_float]),
            ('AXUIElementCopyElementAtPosition', C.c_int, [C.c_void_p, C.c_float, C.c_float, C.c_void_p]),
            ('AXValueGetValue', C.c_bool, [C.c_void_p, C.c_int, C.c_void_p]),
        ]:
            fn = getattr(self.ax, name); fn.restype = ret; fn.argtypes = args
        self.types = {}
        for kind in ['String', 'Number', 'Boolean', 'Array', 'URL']:
            fn = getattr(self.cf, 'CF' + kind + 'GetTypeID'); fn.restype = C.c_ulong
            self.types[kind] = fn()
        self.refs = []
        self.keys = {}

    def __enter__(self):
        if not self.ax.AXIsProcessTrusted():
            raise ValueError('Grant Accessibility to the desktop bridge host in macOS Settings')
        return self

    def __exit__(self, *_):
        for ref in reversed(self.refs): self.cf.CFRelease(ref)
        self.refs.clear(); self.keys.clear()

    def own(self, ref):
        if ref: self.refs.append(ref)
        return ref

    def string(self, value):
        return self.own(self.cf.CFStringCreateWithCString(None, value.encode('utf-8'), 0x08000100))

    def key(self, name):
        if name not in self.keys: self.keys[name] = self.string(name)
        return self.keys[name]

    def decode(self, ref):
        if not ref: return None
        kind = self.cf.CFGetTypeID(ref)
        if kind == self.types['URL']: return self.decode(self.cf.CFURLGetString(ref))
        if kind == self.types['String']:
            size = self.cf.CFStringGetLength(ref)
            if size > 8192: raise ValueError('Logic exposes oversized text; narrow the view first')
            buf = C.create_string_buffer(size * 4 + 1)
            if not self.cf.CFStringGetCString(ref, buf, len(buf), 0x08000100):
                raise ValueError('Could not decode Logic text')
            return buf.value.decode('utf-8')
        if kind == self.types['Boolean']: return self.cf.CFBooleanGetValue(ref)
        if kind == self.types['Number']:
            out = C.c_double()
            if self.cf.CFNumberGetValue(ref, 13, C.byref(out)): return out.value
        if kind == self.types['Array']:
            return [self.decode(self.cf.CFArrayGetValueAtIndex(ref, i)) for i in range(self.cf.CFArrayGetCount(ref))]
        return None

    def attr_ref(self, element, name):
        out = C.c_void_p()
        error = self.ax.AXUIElementCopyAttributeValue(element, self.key(name), C.byref(out))
        if error in (-25202, -25204): raise ValueError('Logic changed or is not responding; read again')
        if error != 0: return None
        return self.own(out.value)

    def attr(self, element, name): return self.decode(self.attr_ref(element, name))

    def children(self, element, name='AXChildren'):
        arr = self.attr_ref(element, name)
        if not arr: return []
        count = self.cf.CFArrayGetCount(arr)
        if count > 6000: raise ValueError('Logic view has too many controls; narrow the view')
        return [self.cf.CFArrayGetValueAtIndex(arr, i) for i in range(count)]

    def settable(self, element, name):
        out = C.c_bool()
        return self.ax.AXUIElementIsAttributeSettable(element, self.key(name), C.byref(out)) == 0 and out.value

    def actions(self, element):
        out = C.c_void_p()
        if self.ax.AXUIElementCopyActionNames(element, C.byref(out)) != 0: return []
        return self.decode(self.own(out.value)) or []

    @staticmethod
    def pid():
        # Fixed discovery only. Never a remote-supplied process or program.
        proc = subprocess.run(['/usr/bin/pgrep', '-x', 'Logic Pro'], capture_output=True, timeout=3)
        lib = C.CDLL('/usr/lib/libproc.dylib')
        lib.proc_pidpath.argtypes = [C.c_int, C.c_void_p, C.c_uint32]
        matches = []
        for item in proc.stdout.decode().split():
            pid = int(item); buf = C.create_string_buffer(4096)
            if lib.proc_pidpath(pid, buf, len(buf)) <= 0: continue
            exe = Path(buf.value.decode()).resolve()
            for root in [Path('/Applications'), Path.home() / 'Applications']:
                for name in ['Logic Pro.app', 'Logic Pro X.app']:
                    app = root / name
                    if exe != app / 'Contents/MacOS/Logic Pro': continue
                    with (app / 'Contents/Info.plist').open('rb') as f: info = plistlib.load(f)
                    if info.get('CFBundleIdentifier') == 'com.apple.logic10': matches.append(pid)
        if len(matches) != 1: raise ValueError('Open one instance of Logic Pro first')
        return matches[0]

    @staticmethod
    def foreground_pid():
        # Cross-check Launch Services. Some Logic AX instances continued to
        # report AXFrontmost=true while another app was actually in front.
        try:
            asn = subprocess.check_output(['/usr/bin/lsappinfo', 'front'], text=True, timeout=2).strip()
            if not re.fullmatch(r'ASN:0x[0-9a-fA-F]+-0x[0-9a-fA-F]+:', asn): return None
            info = subprocess.check_output(['/usr/bin/lsappinfo', 'info', '-only', 'bundleid,pid', asn], text=True, timeout=2)
            match = re.search(r'^"pid"=(\d+)$', info, re.M)
            if '"CFBundleIdentifier"="com.apple.logic10"' not in info or not match: return None
            return int(match[1])
        except (OSError, subprocess.SubprocessError): return None

    def read(self, workflow=False):
        # Native element references belong to one snapshot. Keeping hundreds
        # of full trees alive while nudging a fader caused unnecessary growth.
        # Callers must use the refs returned by the latest read only.
        self.__exit__()
        pid = self.pid()
        app = self.own(self.ax.AXUIElementCreateApplication(pid))
        self.ax.AXUIElementSetMessagingTimeout(app, 2.0)
        roots = self.children(app, 'AXWindows')
        windows = []
        for root in roots:
            self.ax.AXUIElementSetMessagingTimeout(root, .8)
            role = self.attr(root, 'AXRole')
            if role == 'AXWindow' or (workflow and role in ('AXMenu', 'AXDialog', 'AXSheet')): windows.append(root)
            elif role != 'AXHelpTag':
                raise ValueError('Logic is not exposing an ordinary project window; finish the current dialog or unlock the Mac')
        if not windows: raise ValueError('Open a Logic project on the unlocked Mac')
        if workflow:
            menu = self.attr_ref(app, 'AXMenuBar')
            if menu: windows.append(menu)
        nodes = []; refs = {}; queue = [(e, str(i)) for i, e in enumerate(windows)]
        seen = {}; deadline = time.monotonic() + 8
        while queue:
            if time.monotonic() > deadline: raise ValueError('Logic snapshot timed out; narrow the view')
            element, path = queue.pop(0)
            self.ax.AXUIElementSetMessagingTimeout(element, .8)
            bucket = seen.setdefault(self.cf.CFHash(element), [])
            if any(self.cf.CFEqual(element, old) for old in bucket): continue
            bucket.append(element)
            if len(nodes) >= 6000: raise ValueError('Logic snapshot incomplete; narrow the view')
            node = {'id': path}
            names_ref = C.c_void_p()
            if self.ax.AXUIElementCopyAttributeNames(element, C.byref(names_ref)) != 0:
                raise ValueError('Logic control changed during inspection')
            names = self.decode(self.own(names_ref.value)) or []
            for field, attr in [('role','AXRole'), ('title','AXTitle'), ('description','AXDescription'),
                                ('subrole','AXSubrole'), ('url','AXURL'),
                                ('identifier','AXIdentifier'), ('value','AXValue'), ('document','AXDocument'),
                                ('enabled','AXEnabled'), ('selected','AXSelected'), ('modal','AXModal'),
                                ('min','AXMinValue'), ('max','AXMaxValue'), ('help','AXHelp'), ('display','AXValueDescription')]:
                if attr not in names: continue
                value = self.attr(element, attr)
                if value is not None: node[field] = value
            node['settable'] = self.settable(element, 'AXValue')
            node['selectable'] = self.settable(element, 'AXSelected')
            node['actions'] = self.actions(element)
            refs[path] = element; nodes.append(node)
            if workflow and (node.get('identifier') == 'ColumnView' or
                    (node.get('role') == 'AXScrollArea' and any(n.get('identifier') == 'GoToWindow' and path.startswith(n['id'] + '.') for n in nodes))):
                # File-browser icons/autocomplete rows churn during navigation
                # and are unrelated to the bound path field. Avoid walking
                # those previews, exposing home-directory inventories, or
                # starving the file panel with thousands of AX messages.
                continue
            children = self.children(element)
            if children and path.count('.') >= 30: raise ValueError('Logic snapshot too deep; narrow the view')
            queue.extend((e, path + '.' + str(i)) for i, e in enumerate(children))
        # AXFrontmost alone was observed to disagree with the visible desktop.
        result = {'pid': pid, 'frontmost': self.foreground_pid() == pid and self.attr(app, 'AXFrontmost') is True, 'nodes': nodes}
        if workflow:
            focused = self.attr_ref(app, 'AXFocusedUIElement')
            result['focus'] = None if not focused else dict(role=self.attr(focused, 'AXRole'),
                id=next((key for key, ref in refs.items() if self.cf.CFEqual(ref, focused)), None))
        return result, refs

    def write(self, ref, value=None, press=False, select=False):
        if press:
            result = self.ax.AXUIElementPerformAction(ref, self.key('AXPress'))
        else:
            attr = 'AXSelected' if select else 'AXValue'
            if not self.settable(ref, attr): raise ValueError('Logic control is not writable')
            if isinstance(value, bool):
                value_ref = C.c_void_p.in_dll(self.cf, 'kCFBooleanTrue' if value else 'kCFBooleanFalse').value
            elif isinstance(value, (int, float)):
                number = C.c_double(value)
                value_ref = self.own(self.cf.CFNumberCreate(None, 13, C.byref(number)))
            elif isinstance(value, str): value_ref = self.string(value)
            else: raise ValueError('Unsupported Logic value')
            result = self.ax.AXUIElementSetAttributeValue(ref, self.key(attr), value_ref)
        if result == -25204: raise LogicDispatchUncertain('Logic did not acknowledge the action; inspect its actual state')
        if result != 0: raise ValueError('Logic rejected the edit (AX code ' + str(result) + ')')

    def select_only(self, parent, child):
        if not self.settable(parent, 'AXSelectedChildren'):
            raise ValueError('Logic does not expose exclusive track selection in this view')
        values = (C.c_void_p * 1)(child)
        array = self.own(self.cf.CFArrayCreate(None, values, 1, None))
        result = self.ax.AXUIElementSetAttributeValue(parent, self.key('AXSelectedChildren'), array)
        if result: raise ValueError('Logic rejected track selection')

    def increment(self, ref, up):
        action = 'AXIncrement' if up else 'AXDecrement'
        if action not in self.actions(ref): raise ValueError('Logic control has no bounded adjustment action')
        if self.ax.AXUIElementPerformAction(ref, self.key(action)):
            raise ValueError('Logic rejected parameter adjustment')

    def select_track_name(self, ref, pid):
        """One click on the validated track-name element, never remote coordinates."""
        if self.attr(ref, 'AXRole') != 'AXTextField': raise ValueError('Track name is unavailable')
        self.click_control(ref, pid)

    def workflow_key(self, command, pid, ref=None):
        """Fixed Cocoa file-panel/field commands, never remote key codes."""
        if command not in ('open_path', 'commit_field') or self.foreground_pid() != pid:
            raise ValueError('Logic is no longer foreground')
        if command == 'commit_field':
            if not ref or self.attr(ref, 'AXRole') not in ('AXTextField', 'AXComboBox'):
                raise ValueError('An observed text field is required')
            path_field = self.attr(ref, 'AXIdentifier') == 'PathTextField'
            if path_field:
                # Cocoa can expose a focused field inside an inactive sheet.
                # Raise that exact ancestor before Return; focusing the field
                # alone leaves the Go To panel visible and swallows the key.
                parent = ref
                raised = False
                for _ in range(20):
                    parent = self.attr_ref(parent, 'AXParent')
                    if not parent: break
                    if self.attr(parent, 'AXIdentifier') == 'GoToWindow':
                        if self.ax.AXUIElementPerformAction(parent, self.key('AXRaise')):
                            raise ValueError('Logic could not raise the file path sheet')
                        time.sleep(.15)
                        raised = True
                        break
                if not raised: raise ValueError('The file path sheet is unavailable')
            if self.attr(ref, 'AXFocused') is not True:
                if not self.settable(ref, 'AXFocused'): raise ValueError('Expose and focus the field first')
                yes = C.c_void_p.in_dll(self.cf, 'kCFBooleanTrue').value
                if self.ax.AXUIElementSetAttributeValue(ref, self.key('AXFocused'), yes):
                    raise ValueError('Logic could not focus the field')
            if self.attr(ref, 'AXFocused') is not True: raise ValueError('Logic field focus was not confirmed')
            if not path_field and 'AXConfirm' in self.actions(ref):
                result = self.ax.AXUIElementPerformAction(ref, self.key('AXConfirm'))
                if result == -25204: raise LogicDispatchUncertain('Logic field confirmation needs inspection')
                if result: raise ValueError('Logic rejected field confirmation')
                time.sleep(.2)
                return
        # Fixed Cocoa key equivalents through System Events. Quartz synthetic
        # chords were intermittently ignored by Logic's modal file panels on
        # this host. No text, key codes or scripts are supplied by the phone.
        script = r"""
        function run(argv) {
          const se = Application('System Events');
          const ps = se.applicationProcesses.whose({frontmost:true})();
          if (ps.length !== 1 || ps[0].unixId() !== Number(argv[1]) || ps[0].name() !== 'Logic Pro')
            throw Error('Logic lost foreground before field input');
          if (argv[0] === 'open_path') se.keystroke('g', {using:['command down','shift down']});
          else if (argv[0] === 'commit_field') se.keyCode(36);
          else throw Error('Unsupported Logic field operation');
        }
        """
        result = subprocess.run(['/usr/bin/osascript','-l','JavaScript','-e',script,'--',command,str(pid)],
                                capture_output=True, timeout=8)
        if result.returncode:
            raise ValueError('Logic file navigation needs Automation access to System Events for the Mac helper. ' +
                             result.stderr.decode(errors='replace')[-300:])
        time.sleep(.2)

    def cancel_menu(self, ref):
        if self.attr(ref, 'AXRole') != 'AXMenu' or 'AXCancel' not in self.actions(ref):
            raise ValueError('No observed menu to dismiss')
        if self.ax.AXUIElementPerformAction(ref, self.key('AXCancel')):
            raise ValueError('Logic did not dismiss the menu')

    def pick_menu(self, ref):
        if self.attr(ref, 'AXRole') != 'AXMenuItem' or 'AXPick' not in self.actions(ref):
            return self.write(ref, press=True)
        result = self.ax.AXUIElementPerformAction(ref, self.key('AXPick'))
        if result == -25204: raise LogicDispatchUncertain('Logic menu result needs inspection')
        if result: raise ValueError('Logic rejected the menu selection')

    def click_control(self, ref, pid):
        """Internal dispatch only: controller selects a typed, current AX target."""
        if self.foreground_pid() != pid: raise ValueError('Logic is no longer foreground')
        if self.attr(ref, 'AXRole') not in ('AXTextField', 'AXCheckBox', 'AXButton', 'AXPopUpButton', 'AXMenuItem'):
            raise ValueError('This Logic control does not support bounded clicking')
        class Point(C.Structure): _fields_ = [('x', C.c_double), ('y', C.c_double)]
        pos = Point(); size = Point()
        if not self.ax.AXValueGetValue(self.attr_ref(ref, 'AXPosition'), 1, C.byref(pos)) or not self.ax.AXValueGetValue(self.attr_ref(ref, 'AXSize'), 2, C.byref(size)):
            raise ValueError('Control geometry unavailable')
        if not all(math.isfinite(v) for v in (pos.x, pos.y, size.x, size.y)) or not (1 < size.x < 3000 and 1 < size.y < 300):
            raise ValueError('Invalid Logic control geometry')
        center = Point(pos.x + size.x / 2, pos.y + size.y / 2)
        app = self.own(self.ax.AXUIElementCreateApplication(pid))
        hit = C.c_void_p()
        code = self.ax.AXUIElementCopyElementAtPosition(app, center.x, center.y, C.byref(hit))
        hit_ref = self.own(hit.value)
        if code or not hit_ref or not self.cf.CFEqual(hit_ref, ref):
            raise ValueError('Logic control is covered or moved; expose it before editing')
        create = self.ax.CGEventCreateMouseEvent
        create.argtypes = [C.c_void_p, C.c_uint32, Point, C.c_uint32]; create.restype = C.c_void_p
        post = self.ax.CGEventPost; post.argtypes = [C.c_uint32, C.c_void_p]; post.restype = None
        if self.foreground_pid() != pid: raise ValueError('Logic is no longer foreground')
        for event_type in (1, 2):
            event = self.own(create(None, event_type, center, 0))
            if not event: raise ValueError('Control event unavailable')
            post(0, event)


if __name__ == '__main__':
    with LogicAX() as ax: print(json.dumps(ax.read()[0], ensure_ascii=False))
