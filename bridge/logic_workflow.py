"""Observed Logic menus, region inspectors and dialogs for multi-step workflows.

The phone can only address elements offered by a fresh native snapshot. Menu
paths come from Logic, including installed third-party plug-ins. No key codes,
coordinates, scripts, application names or executable commands cross this API.
An observed UI transition is explicitly distinct from a verified audio export.
"""
import hashlib
import re
import time
from pathlib import Path
from urllib.parse import unquote, urlparse
from logic_control import LogicCancelled, children, finite, fingerprint
from macos_ax import LogicDispatchUncertain
from audio_staging import stage_audio


def context(frame):
    projects = sorted({n['document'] for n in frame['nodes'] if n.get('role') == 'AXWindow' and n.get('document')})
    if len(projects) != 1: raise ValueError('Open exactly one saved working Logic project')
    url = urlparse(projects[0]); path = Path(unquote(url.path))
    if url.scheme != 'file' or url.netloc not in ('', 'localhost') or path.suffix != '.logicx' or not path.is_dir():
        raise ValueError('Save a working copy of the Logic project first')
    return projects[0]


def stable(frame):
    # Peak meters and the running clock are not transaction identities. All
    # track/plugin/region values and selection remain bound to the approval.
    nodes = []
    for n in frame['nodes']:
        if 'peak level' in n.get('description', '').lower(): continue
        item = {k:v for k,v in n.items() if k not in ('help',)}
        if n.get('role') in ('AXMenuItem', 'AXMenuBarItem'): item.pop('selected', None)
        nodes.append(item)
    return dict(pid=frame['pid'], frontmost=frame['frontmost'], focus=frame.get('focus'), nodes=nodes)


def elements(frame):
    by_id = {n['id']: n for n in frame['nodes']}
    result = []
    def label(n):
        return (n.get('title') or n.get('description') or
                (n.get('identifier') or 'Text field' if n.get('role') in ('AXTextField','AXComboBox') else '') or
                (n.get('value') if isinstance(n.get('value'), str) else '') or
                ('Menu' if n.get('role') == 'AXMenu' else 'Close window' if n.get('subrole') == 'AXCloseButton' else ''))
    for node in frame['nodes']:
        if node.get('subrole') == 'AXSecureTextField': continue
        if node.get('url') and not node.get('selected'): continue
        path = node['id']; ancestors = []
        while '.' in path:
            path = path.rsplit('.', 1)[0]
            if path in by_id: ancestors.insert(0, by_id[path])
        top = next((n for n in ancestors + [node] if n.get('role') == 'AXMenuBarItem'), None)
        if top and top.get('title') not in ('File','Edit','Track','Navigate','Record','Mix','View','Window'): continue
        # Sharing, external services and arbitrary recent files are outside a
        # song-editing action. They need their own destination-specific tools.
        if top and any(label(n) in ('Share', 'Services', 'Open Recent') for n in ancestors + [node]): continue
        title = label(node)
        if not title and node.get('value') is None and not node.get('settable'): continue
        roles = ('AXButton','AXCloseButton','AXCheckBox','AXRadioButton','AXPopUpButton','AXMenuButton',
                 'AXMenu','AXMenuItem','AXMenuBarItem','AXDisclosureTriangle','AXTextField',
                 'AXComboBox','AXSlider','AXValueIndicator','AXLayoutItem','AXRow',
                 'AXStaticText','AXWindow','AXSheet','AXDialog')
        if node.get('role') not in roles: continue
        actions = []
        enabled = node.get('enabled') is not False
        if enabled and 'AXPress' in node.get('actions', []) and node.get('role') not in ('AXSlider','AXValueIndicator'):
            actions.append('press')
        if title.lower() in ('mute', 'solo'):
            actions = [] # Use the dedicated group/automation-aware logic.edit.
        if enabled and node.get('settable') and not node.get('url') and node.get('role') in ('AXTextField','AXComboBox'):
            actions.extend(('set_text','commit_text'))
        if node.get('role') == 'AXMenu' and 'AXCancel' in node.get('actions', []): actions.append('cancel_menu')
        if node.get('role') == 'AXWindow' and (node.get('title') == 'Open File' or
                any(n.get('identifier') == 'saveAsNameTextField' and n['id'].startswith(node['id'] + '.') for n in frame['nodes'])):
            actions.append('choose_path')
        if enabled and node.get('settable') and node.get('role') in ('AXSlider','AXValueIndicator') and \
           all(finite(node.get(k)) for k in ('value','min','max')) and node['min'] < node['max']:
            # Dedicated mixer/plug-in controls have stricter native-unit and
            # group/automation rules. They must use logic.edit.
            mixer = any(n.get('role') == 'AXLayoutArea' and label(n) == 'Mixer' for n in ancestors)
            plugin = any(n.get('role') == 'AXWindow' and not n.get('document') and
                         any(child.get('description') == 'view' and child.get('role') == 'AXMenuButton'
                             for child in children(frame['nodes'], n)) for n in ancestors)
            if not mixer and not plugin: actions.append('set_number')
        result.append(dict(id=node['id'], role=node['role'], label=title,
                           path=' / '.join(label(n) for n in ancestors[-8:] + [node] if label(n)),
                           value=node.get('value'), display=node.get('display'), selected=node.get('selected'),
                           enabled=enabled, min=node.get('min'), max=node.get('max'), actions=actions))
    return result


class LogicWorkflow:
    def __init__(self, controller):
        self.controller = controller
        self.frames = {}

    @staticmethod
    def read_settled(ax):
        for attempt in range(3):
            try: return ax.read(workflow=True)
            except ValueError as ex:
                if attempt == 2 or str(ex) not in ('Logic control changed during inspection', 'Logic changed or is not responding; read again'):
                    raise
                time.sleep(.1)

    def screen(self, query='', offset=0):
        if not isinstance(query, str) or len(query) > 200 or type(offset) is not int or not 0 <= offset <= 6000:
            raise ValueError('Use a query under 200 characters and a page offset from 0 to 6000')
        import secrets
        c = self.controller
        with c.lock, c.native() as ax:
            frame, _ = self.read_settled(ax)
            project = context(frame)
            token = 'workflow-' + secrets.token_urlsafe(24)
            self.frames = {token: (c.clock(), frame)}
            all_items = elements(frame)
            words = query.casefold().split()
            found = [e for e in all_items if all(w in (e['path'] + ' ' + str(e['value']) + ' ' + e['role']).casefold() for w in words)]
            return dict(snapshot_id=token, expires_in_seconds=60, observed_at=time.time(), project=project, frontmost=frame['frontmost'],
                        elements=found[offset:offset+100], total=len(found), offset=offset,
                        next_offset=offset+100 if len(found) > offset+100 else None,
                        message='Observed Logic controls. A UI transition is not proof of a finished edit, mix or master.')

    def act(self, request, workspace_roots=()):
        if not isinstance(request, dict) or set(request) != {'snapshot_id','project','element_id','action','value'}:
            raise ValueError('Use the exact workflow action contract from logic.screen')
        if not all(isinstance(request[k], str) and len(request[k]) <= 1200 for k in request):
            raise ValueError('Workflow fields must be bounded text')
        c = self.controller
        operation_id = hashlib.sha256(request['snapshot_id'].encode()).hexdigest()
        request_hash = fingerprint(request)
        with c.lock:
            prior = c.receipt(operation_id)
            if prior:
                if prior.get('request_hash') != request_hash: raise ValueError('This snapshot already belongs to a different action')
                return dict(prior, replay=True)
            c.check_cancelled(operation_id)
            saved = self.frames.pop(request['snapshot_id'], None)
            if not saved or c.clock() - saved[0] > 60: raise ValueError('Workflow snapshot expired; inspect again')
            before = saved[1]
            project = context(before)
            if request['project'] != project: raise ValueError('Approved Logic project changed')
            target = next((e for e in elements(before) if e['id'] == request['element_id']), None)
            if not target or request['action'] not in target['actions']: raise ValueError('Action is not offered for this observed element')
            action = request['action']; value = request['value']
            if action in ('press','commit_text','cancel_menu') and value: raise ValueError('This action takes an empty value')
            if action == 'set_text' and ('\x00' in value or '\n' in value or '\r' in value):
                raise ValueError('Use a single-line Logic field value')
            if action == 'set_number':
                try: value = float(value)
                except ValueError: raise ValueError('Use a finite numeric value')
                if not finite(value) or not target['min'] <= value <= target['max']:
                    raise ValueError('Value is outside the observed native range')
            if action == 'choose_path':
                path = Path(value)
                roots = [Path(r).resolve() for r in workspace_roots]
                if not path.is_absolute() or not path.exists() or not any(path.resolve() == r or r in path.resolve().parents for r in roots):
                    raise ValueError('Choose an existing file or folder inside a declared desktop workspace')
                if path.is_file() and target['label'] != 'Open File':
                    raise ValueError('Choose a folder for an export destination; import files through the Open File dialog')
            with c.native() as ax:
                frame, refs = self.read_settled(ax)
                if stable(before) != stable(frame) or not frame['frontmost']:
                    raise ValueError('Logic controls or foreground changed. Inspect again; nothing executed')
                # Never make a structural edit while recording or playing. The
                # Stop button is the only workflow mutation allowed then.
                transport = [n for n in frame['nodes'] if n.get('role') == 'AXCheckBox' and n.get('title') in ('Play','Record')]
                stopped = len(transport) == 2 and all(n.get('value') in (0, False) for n in transport)
                if not stopped and not (action == 'press' and target['label'] == 'Stop'):
                    raise ValueError('Stop playback and recording before this workflow edit')
                receipt = dict(operation_id=operation_id, request_hash=request_hash, state='started',
                               project=project, kind='workflow_' + action, target=target['path'],
                               before=target['value'], requested=value, created_at=time.time())
                c._save(operation_id, receipt, first=True)
                try:
                    c.check_cancelled(operation_id)
                    try:
                        if action == 'choose_path':
                            if Path(value).is_file():
                                staged=stage_audio(value,roots[0])
                                value=staged['path'];receipt['import_copy']=staged
                                c._save(operation_id,receipt)
                            c.check_cancelled(operation_id)
                            ax.workflow_key('open_path', frame['pid'])
                            path_deadline=time.monotonic()+8
                            for _ in range(30):
                                if time.monotonic()>=path_deadline:raise ValueError('The file path panel did not open')
                                c.check_cancelled(operation_id); time.sleep(.05)
                                path_frame, path_refs = self.read_settled(ax)
                                if context(path_frame) != project or not path_frame['frontmost']: raise ValueError('Logic context changed')
                                fields = [n for n in path_frame['nodes'] if n.get('identifier') == 'PathTextField' and n.get('role') == 'AXTextField']
                                if len(fields) == 1: break
                            else: raise ValueError('The file path panel did not open')
                            c.check_cancelled(operation_id)
                            ax.write(path_refs[fields[0]['id']], value=value)
                            time.sleep(.15)
                            check_frame, check_refs = self.read_settled(ax)
                            field = next((n for n in check_frame['nodes'] if n.get('identifier') == 'PathTextField' and n.get('value') == value), None)
                            if not field or context(check_frame) != project or not check_frame['frontmost']:
                                raise ValueError('File path was not confirmed')
                            c.check_cancelled(operation_id)
                            ax.workflow_key('commit_field', frame['pid'], check_refs[field['id']])
                        elif action == 'commit_text': ax.workflow_key('commit_field', frame['pid'], refs[target['id']])
                        elif action == 'cancel_menu': ax.cancel_menu(refs[target['id']])
                        elif action == 'press':
                            if target['role'] == 'AXCheckBox' or target['label'] == 'audio plug-in':
                                ax.click_control(refs[target['id']], frame['pid'])
                            elif target['role'] == 'AXMenuItem' and hasattr(ax, 'pick_menu'):
                                ax.pick_menu(refs[target['id']])
                                node = next(n for n in frame['nodes'] if n['id'] == target['id'])
                                if not any(n.get('role') == 'AXMenu' for n in children(frame['nodes'], node)):
                                    # AX can execute a leaf while leaving its
                                    # menu tracking session active above the new
                                    # dialog. Dismiss this exact owned menu,
                                    # never send Escape to an unknown dialog.
                                    menus = [n for n in frame['nodes'] if n.get('role') == 'AXMenu' and target['id'].startswith(n['id'] + '.')]
                                    if menus:
                                        menu = min(menus, key=lambda n: n['id'].count('.'))
                                        try: ax.cancel_menu(refs[menu['id']])
                                        except ValueError: pass # It may already have closed after selection.
                            else: ax.write(refs[target['id']], press=True)
                        else: ax.write(refs[target['id']], value=value)
                    except LogicDispatchUncertain:
                        # Native popup menus sometimes hold AXPress until its
                        # timeout despite having opened. Read the new state,
                        # without dispatching a second click.
                        pass
                    observed = None
                    deadline = time.monotonic() + 12
                    for _ in range(30):
                        c.check_cancelled(operation_id)
                        if time.monotonic() >= deadline: break
                        time.sleep(.05)
                        try: after, _ = ax.read(workflow=True)
                        except ValueError: continue
                        if after['pid'] != frame['pid'] or not after['frontmost'] or context(after) != project: break
                        current = next((e for e in elements(after) if e['id'] == target['id'] and e['role'] == target['role'] and e['label'] == target['label']), None)
                        if action in ('set_text','set_number'):
                            if current and current['value'] == value:
                                observed = dict(state='verified', observed=current['value']); break
                        elif (action == 'choose_path' or action == 'commit_text' and target['label'] == 'PathTextField') and any(
                                n.get('identifier') == 'PathTextField' for n in after['nodes']):
                            # Autocomplete/focus changes are not navigation.
                            # The Go To sheet must actually close first.
                            continue
                        elif stable(frame) != stable(after) or (action == 'press' and
                                target['role'] in ('AXMenuItem','AXMenuBarItem') and current and
                                current['selected'] != target['selected']):
                            # Keep actual evidence for the next planner turn.
                            # Do not label a click's unknown musical effect as
                            # verified merely because focus/menus changed.
                            old_items = {e['path']:e for e in elements(frame)}
                            changed = [e for e in elements(after) if old_items.get(e['path']) != e]
                            observed = dict(state='observed', changed=changed[:30]); break
                    receipt.update(observed or dict(state='uncertain'))
                    receipt['message'] = ('Field value read back.' if receipt['state']=='verified' else
                        'Logic screen changed. Inspect the result before continuing; task completion is not verified.' if receipt['state']=='observed' else
                        'No result verified. Inspect Logic and this receipt; do not replay the action.')
                except Exception as ex:
                    receipt.update(state='cancelled' if isinstance(ex, LogicCancelled) else 'uncertain', message=str(ex)[:500])
                    c._save(operation_id, receipt)
                    raise
                c._save(operation_id, receipt)
                return receipt
