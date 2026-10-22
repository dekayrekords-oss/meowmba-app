"""Typed Logic transactions. Model input is data, never scripts or gestures.

Inspection binds a single edit to the actual project, control, prior value and
entire visible control inventory. A durable write-ahead receipt prevents replay
after a lost reply or helper restart. Unknown outcomes require a fresh review.
"""
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import threading
import time
from urllib.parse import unquote, urlparse
from macos_ax import LogicAX


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, allow_nan=False, separators=(',', ':')).encode()).hexdigest()


def children(nodes, parent):
    return [n for n in nodes if n['id'].rsplit('.', 1)[0] == parent['id'] and n['id'] != parent['id']]


def plugin_slots(nodes, strip):
    result = []
    for node in children(nodes, strip):
        nested = children(nodes, node)
        if node.get('role') == 'AXGroup' and node.get('description') and \
           {'open', 'bypass', 'list'}.issubset({n.get('description') for n in nested}):
            result.append(dict(name=node['description'], slot_id=node['id']))
    return result


def inventory(frame):
    nodes = frame['nodes']
    documents = sorted(set(n['document'] for n in nodes if n.get('role') == 'AXWindow' and n.get('document')))
    if len(documents) != 1:
        raise ValueError('Open exactly one saved Logic project for editing')
    if any(n.get('modal') or n.get('role') in ('AXSheet', 'AXDialog', 'AXMenu') for n in nodes):
        raise ValueError('Dismiss the Logic dialog before requesting an edit')
    controls = []; tracks = []; strips = []; plugins = []

    def add(node, kind, target, parameter, value=None, lo=None, hi=None):
        if node.get('enabled') is False: return
        current = node.get('value') if value is None else value
        if current is None or (type(current) in (int, float) and not finite(current)): return
        controls.append(dict(id=node['id'], kind=kind, target=target, parameter=parameter,
                             value=current, min=lo, max=hi))

    for node in nodes:
        desc = node.get('description', '')
        match = re.fullmatch(r'Track (\d+) “(.+)”(?:, .*)?', desc)
        if node.get('role') == 'AXLayoutItem' and match and type(node.get('selected')) is bool:
            desc = f'Track {match[1]} “{match[2]}”'
            tracks.append(dict(name=match[2], number=int(match[1]), selected=node.get('selected')))
            for child in children(nodes, node):
                d = child.get('description')
                if child.get('role') == 'AXRadioButton' and 'AXPress' in child.get('actions', []):
                    add(child, 'select_track', desc, 'selected', bool(node.get('selected')))
                if d in ('Mute', 'Solo') and child.get('role') == 'AXCheckBox' and child.get('value') in (-1, 0, 1):
                    # -1 is Logic's implicit mute caused by another soloed
                    # track; it is not this track's explicit mute setting.
                    add(child, 'set_' + d.lower(), desc, d.lower(), child['value'] == 1)
        direct = children(nodes, node)
        name = next((n.get('value') for n in direct if n.get('description') == 'name'), None)
        if node.get('role') == 'AXLayoutItem' and name:
            mode = next((n.get('description') for n in direct if 'automation' in n.get('description', '').lower()), None)
            strip = dict(name=name, id=node['id'], automation=mode,
                         volume_display=next((n.get('title') for n in direct if n.get('description') == 'volume fader level'), None),
                         pan=next((n.get('value') for n in direct if n.get('description') == 'pan'), None),
                         plugins=plugin_slots(nodes, node))
            strips.append(strip)
            # Write modes can record automation even though a fader looks ordinary.
            if mode not in ('Read, automation enabled', 'Off, automation disabled'): continue
            group = next((n for n in direct if n.get('description') == 'group'), None)
            if group and group.get('title') not in ('group, Group', 'group'): continue
            for child in direct:
                d = child.get('description')
                if d == 'pan' and child.get('settable') and child.get('min') == -64 and child.get('max') == 63:
                    add(child, 'set_pan', name, 'pan', lo=-64, hi=63)
                if d == 'volume fader' and child.get('settable') and child.get('min') == 0 and child.get('max') == 233:
                    db = re.fullmatch(r'([+-]?\d+(?:\.\d+)?) dB', child.get('display', ''))
                    if db: add(child, 'set_volume_db', name, 'volume (dB)', float(db[1]), lo=-60, hi=6)

    # Track header buttons share the channel's group and automation state.
    # Do not offer a single-track toggle when its linked side effects are unknown.
    for control in list(controls):
        if control['kind'] not in ('set_mute', 'set_solo'): continue
        match = re.fullmatch(r'Track (\d+) “(.+)”', control['target'])
        owners = [s for s in strips if s['name'] == match[2]]
        usable = bool(owners) and sum(t['name'] == match[2] for t in tracks) == 1
        usable = usable and all(s['automation'] in ('Read, automation enabled', 'Off, automation disabled') for s in owners)
        for strip in owners:
            group = next((n for n in nodes if n['id'].rsplit('.', 1)[0] == strip['id'] and n.get('description') == 'group'), None)
            if group and group.get('title') not in ('group', 'group, Group'): usable = False
        if not usable: controls.remove(control)

    # Apple's Controls view is common to native and third-party Audio Units.
    # The window title is the CHANNEL, not the plugin name. Bind both to an
    # actual occupied slot; repeated instances/names require manual selection.
    for window in [n for n in nodes if n.get('role') == 'AXWindow' and not n.get('document')]:
        descendants = [n for n in nodes if n['id'].startswith(window['id'] + '.')]
        direct = children(nodes, window)
        owner = window.get('title', '')
        view = next((n for n in direct if n.get('description') == 'view' and n.get('role') == 'AXMenuButton'), None)
        if not view: continue
        owners = [s for s in strips if s['name'] == owner]
        names = {p['name'] for strip in owners for p in strip['plugins']}
        titles = [n['value'] for n in direct if n.get('role') == 'AXStaticText' and n.get('value') in names]
        if len(titles) != 1: continue
        name = titles[0]
        usable = bool(owners) and all(sum(p['name'] == name for p in s['plugins']) == 1 for s in owners)
        usable = usable and all(s['automation'] in ('Read, automation enabled', 'Off, automation disabled') for s in owners)
        usable = usable and sum(t['name'] == owner for t in tracks) <= 1
        for strip in owners:
            group = next((n for n in nodes if n['id'].rsplit('.', 1)[0] == strip['id'] and n.get('description') == 'group'), None)
            if group and group.get('title') not in ('group', 'group, Group'): usable = False
        target = owner + ' / ' + name
        parameters = []
        if usable:
            add(view, 'show_plugin_controls', target, 'Controls view', view.get('title') == 'Controls')
        if view.get('title') == 'Controls':
            for n in descendants:
                if n.get('role') != 'AXSlider' or not n.get('settable') or n.get('enabled') is False: continue
                # Use the numeric display slider inside each AXCell. The
                # parallel graphical slider represents the same parameter.
                if not n.get('display'): continue
                ancestors = [a for a in descendants if a.get('role') == 'AXCell' and n['id'].startswith(a['id'] + '.')]
                if len(ancestors) != 1: continue
                labels = [a.get('value') for a in children(nodes, ancestors[0]) if a.get('role') == 'AXStaticText']
                if len(labels) != 1 or not isinstance(labels[0], str) or not labels[0].strip(): continue
                label = labels[0].rstrip(':').strip()
                if not all(finite(n.get(k)) for k in ('value', 'min', 'max')): continue
                if n['min'] >= n['max'] or not n['min'] <= n['value'] <= n['max']: continue
                parameters.append(dict(name=label, native_value=n['value'], display=n['display'], min=n['min'], max=n['max']))
                if usable:
                    add(n, 'set_plugin_parameter', target, label, lo=n['min'], hi=n['max'])
                    controls[-1]['display'] = n['display']
        plugins.append(dict(window=owner, name=name, target=target, parameters=parameters,
                            view=view.get('title'), editable=usable and view.get('title') == 'Controls'))

    for strip in strips:
        for slot in strip['plugins']:
            if sum(p['name'] == slot['name'] for p in strip['plugins']) != 1: continue
            if sum(t['name'] == strip['name'] for t in tracks) > 1: continue
            target = strip['name'] + ' / ' + slot['name']
            slot_node = next(n for n in nodes if n['id'] == slot['slot_id'])
            buttons = [n for n in children(nodes, slot_node) if n.get('description') == 'open' and n.get('role') == 'AXButton']
            if len(buttons) != 1: continue
            add(buttons[0], 'open_plugin', target, 'window open', any(p['target'] == target for p in plugins))

    transport = {}
    for name in ['Play', 'Record']:
        found = [n for n in nodes if n.get('role') == 'AXCheckBox' and n.get('title') == name and n.get('value') in (0, 1)]
        transport[name.lower()] = bool(found[0]['value']) if len(found) == 1 else None
    # Stable observations, not rapidly changing meters or playhead position.
    return dict(project=documents[0], pid=frame['pid'], frontmost=frame['frontmost'], transport=transport,
                tracks=tracks, channel_strips=strips, plugin_windows=plugins, controls=controls,
                limits=['Visible accessible controls only; hidden tracks and plug-in parameters are not inferred. Plugin values are native units; display text supplies their meaning.',
                        'Full song editing, audio analysis and mastering/bounce delivery are not yet verified.'])


def view_change_only(before, after):
    if before['tracks'] != after['tracks']: return False
    def settings(state):
        # Opening a window can renumber AX paths and replace another linked
        # plugin window; channel settings and the plugin chain must stay put.
        strips = [dict((k, v) for k, v in s.items() if k not in ('id', 'plugins')) |
                  {'plugins': [p['name'] for p in s['plugins']]} for s in state['channel_strips']]
        toggles = [{k:v for k,v in c.items() if k != 'id'} for c in state['controls'] if c['kind'] in ('set_mute','set_solo')]
        return sorted(map(fingerprint,strips)), sorted(map(fingerprint,toggles))
    return settings(before) == settings(after)


def unrelated_controls_unchanged(before, after, target):
    """Detect grouped edits and concurrent changes, including duplicate views."""
    if before['tracks'] != after['tracks']: return False
    # Inspector and mixer may expose the same channel twice. Only matching
    # copies of this precise parameter may change; sibling tracks may not.
    def rest(state):
        return [c for c in state['controls'] if not all(c[k] == target[k] for k in ('kind', 'target', 'parameter'))]
    if rest(before) != rest(after): return False
    def routing(state):
        return [(s['id'], s['name'], s['automation'], s['plugins']) for s in state['channel_strips']]
    return routing(before) == routing(after)


class LogicController:
    def __init__(self, receipt_dir, native=LogicAX, clock=time.monotonic):
        self.receipt_dir = Path(receipt_dir)
        self.native = native; self.clock = clock
        self.frames = {}; self.lock = threading.Lock()

    def cancel(self, operation_id):
        """A durable revocation also covers a request still waiting to start.

        This deliberately does not take the execution lock: cancellation must
        reach a running adjustment, not queue behind it. It never claims that
        already applied parameter changes have been rolled back.
        """
        self.receipt(operation_id)  # validates the identity and existing journal
        self.receipt_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        if self.receipt_dir.is_symlink(): raise ValueError('Linked Logic receipts are not supported')
        path = self.receipt_dir / (operation_id + '.cancel')
        if path.is_symlink(): raise ValueError('Linked cancellation records are not supported')
        if not path.exists():
            if len(list(self.receipt_dir.glob('*.cancel'))) >= 10000:
                raise ValueError('Cancellation storage is full; archive receipts on the Mac')
            try:
                fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, 'wb') as f:
                    f.write(b'cancelled'); f.flush(); os.fsync(f.fileno())
            except FileExistsError: pass
        result = self.receipt(operation_id)
        return dict(operation_id=operation_id, state='cancel_requested', receipt=result,
                    message='Stop requested. Changes already applied remain; inspect the final receipt and Logic state.')

    def check_cancelled(self, operation_id):
        if (self.receipt_dir / (operation_id + '.cancel')).exists():
            raise LogicCancelled('Logic operation cancelled; changes already applied remain. Inspect before continuing.')

    def inspect(self):
        with self.lock, self.native() as ax:
            frame, _ = ax.read(); state = inventory(frame)
            token = secrets.token_urlsafe(24)
            self.frames = {token: (self.clock(), state)}
            return dict(state, snapshot_id=token, expires_in_seconds=60, adapter_version=1)

    def receipt(self, operation_id):
        if not re.fullmatch(r'[0-9a-f]{64}', str(operation_id)):
            raise ValueError('Invalid Logic operation identity')
        path = self.receipt_dir / (operation_id + '.json')
        if path.is_symlink(): raise ValueError('Linked Logic receipts are not supported')
        if not path.exists(): return None
        if path.stat().st_size > 64_000: raise ValueError('Logic receipt is invalid; review manually')
        return json.loads(path.read_text())

    def _save(self, operation_id, value, first=False):
        self.receipt_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        if self.receipt_dir.is_symlink(): raise ValueError('Linked Logic receipts are not supported')
        if len(list(self.receipt_dir.glob('*.json'))) >= 10000 and first:
            raise ValueError('Logic receipt storage is full; archive receipts on the Mac first')
        path = self.receipt_dir / (operation_id + '.json')
        data = json.dumps(value, allow_nan=False).encode()
        if first:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as f: f.write(data); f.flush(); os.fsync(f.fileno())
        else:
            tmp = path.with_suffix('.pending-' + secrets.token_hex(8))
            try:
                fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, 'wb') as f: f.write(data); f.flush(); os.fsync(f.fileno())
                os.replace(tmp, path)
            finally:
                tmp.unlink(missing_ok=True)
        fd = os.open(self.receipt_dir, os.O_RDONLY)
        try: os.fsync(fd)
        finally: os.close(fd)

    def edit(self, request):
        allowed = {'snapshot_id', 'project', 'control_id', 'kind', 'target', 'parameter', 'value'}
        if set(request) != allowed: raise ValueError('Use the exact Logic edit contract from inspection')
        operation_id = hashlib.sha256(str(request['snapshot_id']).encode()).hexdigest()
        request_hash = fingerprint(request)
        with self.lock:
            old_receipt = self.receipt(operation_id)
            if old_receipt:
                if old_receipt.get('request_hash') != request_hash:
                    raise ValueError('This snapshot already belongs to a different edit')
                return dict(old_receipt, replay=True, message='Prior receipt only; this request was not executed again.')
            self.check_cancelled(operation_id)
            saved = self.frames.pop(str(request['snapshot_id']), None)
            if not saved or self.clock() - saved[0] > 60:
                raise ValueError('Logic snapshot expired or consumed. Inspect again; nothing executed')
            old = saved[1]
            if request['project'] != old['project']: raise ValueError('The approved project does not match')
            target = next((c for c in old['controls'] if c['id'] == request['control_id']), None)
            if not target or any(request[k] != target[k] for k in ('kind', 'target', 'parameter')):
                raise ValueError('The approved track or parameter does not match the observed control')
            value = request['value']; kind = target['kind']
            if kind in ('set_mute', 'set_solo', 'select_track', 'open_plugin', 'show_plugin_controls'):
                if type(value) is not bool or (kind in ('select_track', 'open_plugin', 'show_plugin_controls') and not value):
                    raise ValueError('This operation requires a boolean target state')
            elif not finite(value) or not target['min'] <= value <= target['max']:
                raise ValueError('Parameter value must be finite and inside its observed native range')
            if kind == 'set_pan' and int(value) != value: raise ValueError('Pan must be an integer from -64 to 63')
            if kind == 'set_volume_db' and abs(value * 10 - round(value * 10)) > 1e-6:
                raise ValueError('Volume targets must use the displayed 0.1 dB precision')
            # Snapshot+explicit state values bind approval to one project and one
            # control. A saved project is required; untitled virtual URLs fail.
            url = urlparse(old['project'])
            path = Path(unquote(url.path))
            if url.scheme != 'file' or url.netloc not in ('', 'localhost') or path.suffix != '.logicx' or not path.is_dir():
                raise ValueError('Save a working copy of the Logic project before editing')
            with self.native() as ax:
                frame, refs = ax.read(); current = inventory(frame)
                if current != old: raise ValueError('Logic project or controls changed. Inspect again; nothing executed')
                if not current['frontmost'] or current['transport'] != {'play': False, 'record': False}:
                    raise ValueError('Bring Logic forward and stop playback/recording before editing')
                if len([t for t in current['tracks'] if t.get('selected')]) > 1 and kind != 'select_track':
                    raise ValueError('Select one track first; linked multi-track edits are not approved')
                receipt = dict(operation_id=operation_id, request_hash=request_hash, state='started', project=old['project'], kind=kind,
                               target=target['target'], parameter=target['parameter'], before=target['value'],
                               requested=value, created_at=time.time())
                self._save(operation_id, receipt, first=True)
                try:
                    self.check_cancelled(operation_id)
                    if kind in ('open_plugin', 'show_plugin_controls'):
                        if target['value'] != value:
                            ax.write(refs[target['id']], press=True)
                            if kind == 'show_plugin_controls':
                                choice = None
                                for _ in range(20):
                                    self.check_cancelled(operation_id)
                                    time.sleep(.05)
                                    menu_frame, menu_refs = ax.read()
                                    if menu_frame['pid'] != old['pid'] or not menu_frame['frontmost']:
                                        raise ValueError('Logic focus changed while opening the view menu')
                                    choices = [n for n in menu_frame['nodes'] if n['id'].startswith(target['id'] + '.') and
                                               n.get('role') == 'AXMenuItem' and n.get('title') == 'Controls' and n.get('enabled')]
                                    if len(choices) == 1:
                                        choice = choices[0]; break
                                if not choice: raise ValueError('Controls view menu was not exposed')
                                self.check_cancelled(operation_id)
                                ax.write(menu_refs[choice['id']], press=True)
                    elif kind == 'select_track':
                        row = target['id'].rsplit('.', 1)[0]
                        names = [n for n in frame['nodes'] if n['id'].rsplit('.', 1)[0] == row and n.get('role') == 'AXTextField']
                        if len(names) != 1: raise ValueError('Track name cannot be uniquely identified')
                        ax.select_track_name(refs[names[0]['id']], old['pid'])
                    elif kind in ('set_pan', 'set_volume_db', 'set_plugin_parameter'):
                        # Measured in Logic 12: AXIncrement moves ten native
                        # units; AXValue moves one. Request one native unit at
                        # a time and read the displayed value (dB is nonlinear).
                        # This also works with ordinary absolute AX setters.
                        position = target['value']
                        tolerance = .051 if kind == 'set_volume_db' else 1e-6
                        deadline = time.monotonic() + 20
                        for _ in range(240):
                            self.check_cancelled(operation_id)
                            if time.monotonic() >= deadline: raise ValueError('Parameter adjustment timed out; inspect before another edit')
                            if abs(position - value) <= tolerance: break
                            native_node = next(n for n in frame['nodes'] if n['id'] == target['id'])
                            raw = native_node.get('value')
                            if not finite(raw): raise ValueError('Native parameter position unavailable')
                            step = 1 if value > position else -1
                            write_id = target['id']
                            if kind == 'set_plugin_parameter':
                                # The standard Controls row also exposes the
                                # graphical slider's AXValueIndicator. Unlike
                                # AXSlider, its setter is absolute in Logic 12.
                                cells = [n for n in frame['nodes'] if n.get('role') == 'AXCell' and target['id'].startswith(n['id'] + '.')]
                                if len(cells) == 1:
                                    indicators = [n for n in frame['nodes'] if n['id'].startswith(cells[0]['id'] + '.') and
                                                  n.get('role') == 'AXValueIndicator' and n.get('settable') and n.get('value') == raw]
                                    if len(indicators) == 1: write_id = indicators[0]['id']
                            ax.write(refs[write_id], value=value if kind == 'set_plugin_parameter' else raw + step)
                            time.sleep(.025)
                            frame, refs = ax.read()
                            fresh = inventory(frame)
                            if fresh['project'] != old['project'] or fresh['pid'] != old['pid'] or not fresh['frontmost'] or fresh['transport'] != old['transport']:
                                raise ValueError('Logic context changed during adjustment')
                            if not unrelated_controls_unchanged(old, fresh, target):
                                raise ValueError('Other Logic controls changed during adjustment')
                            new = next((c for c in fresh['controls'] if c['id'] == target['id'] and
                                        all(c[k] == target[k] for k in ('kind', 'target', 'parameter'))), None)
                            if not new or not finite(new['value']): raise ValueError('Parameter disappeared during adjustment')
                            next_value = new['value']
                            if abs(next_value-value) >= abs(position-value): raise ValueError('Requested value is not reachable at this control precision')
                            position = next_value
                        else: raise ValueError('Parameter adjustment budget reached')
                    elif target['value'] != value:
                        if kind in ('set_mute', 'set_solo'):
                            # Some Logic checkboxes acknowledge AXPress without
                            # changing. Click their current native bounds once.
                            ax.click_control(refs[target['id']], old['pid'])
                        else: ax.write(refs[target['id']], value=value)
                    verified = False; observed = None; observed_display = None
                    for _ in range(30):
                        self.check_cancelled(operation_id)
                        try:
                            after = inventory(ax.read()[0])
                        except ValueError:
                            time.sleep(.05)
                            continue
                        if after['project'] != old['project'] or after['pid'] != old['pid'] or not after['frontmost'] or after['transport'] != old['transport']:
                            break
                        if kind not in ('select_track', 'open_plugin', 'show_plugin_controls') and not unrelated_controls_unchanged(old, after, target):
                            break
                        found = [c for c in after['controls'] if (c['id'] == target['id'] or kind in ('open_plugin', 'show_plugin_controls')) and
                                 all(c[k] == target[k] for k in ('target', 'kind', 'parameter'))]
                        if len(found) == 1 or (kind in ('open_plugin', 'show_plugin_controls') and found and all(c['value'] == value for c in found)):
                            observed = found[0]['value']
                            observed_display = found[0].get('display')
                            verified = observed == value or (finite(observed) and finite(value) and abs(observed-value) <= (.051 if kind == 'set_volume_db' else 1e-6))
                            if kind in ('open_plugin', 'show_plugin_controls'):
                                verified = verified and view_change_only(old, after)
                            if kind == 'select_track':
                                verified = verified and len([t for t in after['tracks'] if t.get('selected')]) == 1
                            if verified: break
                        time.sleep(.05)
                    receipt.update(state='verified' if verified else 'uncertain', observed=observed, observed_display=observed_display,
                                   message='Parameter read-back verified.' if verified else 'Result not verified. Inspect Logic; do not automatically retry.')
                except Exception as ex:
                    receipt.update(state='cancelled' if isinstance(ex, LogicCancelled) else 'uncertain',
                                   message=str(ex) if isinstance(ex, LogicCancelled) else 'Logic changed or did not confirm the result. Inspect manually before another edit.')
                    self._save(operation_id, receipt)
                    raise
                self._save(operation_id, receipt)
                return receipt


class LogicCancelled(ValueError):
    pass
