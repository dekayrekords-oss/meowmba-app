"""Bounded macOS Accessibility controls. No generated scripts or shell input.

Install alongside the bridge, outside its writable workspace roots. Requires
the desktop owner to enable gui_enabled and macOS Accessibility/Automation.
"""
import json
import secrets
import subprocess
import sys
import threading
import time

# The program is fixed; all remote values travel as JSON argv data, not code.
SCRIPT = r'''
function run(argv) {
  const req = JSON.parse(argv[0]);
  const se = Application('System Events');
  const ps = se.applicationProcesses.whose({frontmost:true})();
  if (!ps.length) throw Error('No foreground application');
  const p = ps[0];
  const app = p.name(), pid = p.unixId();
  if (/terminal|iterm|warp|console|script editor/i.test(app)) throw Error('Command consoles require manual control');
  if (req.pid !== undefined && req.pid !== pid) throw Error('Foreground app changed');
  const ws = p.windows();
  if (!ws.length) throw Error('No accessible foreground window');
  function get(e, key, fallback) { try { return e[key](); } catch (_) { return fallback; } }
  if (req.action === 'read') {
    let q = [{e:ws[0], path:[]}], nodes = [], visited = 0;
    while (q.length && visited++ < 350 && nodes.length < 100) {
      const n=q.shift(), e=n.e, role=get(e,'role',''), sub=get(e,'subrole','');
      const secure = /secure|password/i.test(role+' '+sub+' '+get(e,'name',''));
      const label = secure ? '[password hidden]' : String(get(e,'name','') || get(e,'description','')).slice(0,240);
      const fullValue = secure ? '[password hidden]' : String(get(e,'value',''));
      const oversized = fullValue.length > 8000;
      const value = fullValue.slice(0,8000);
      let acts=[]; try { acts=e.actions.name(); } catch (_) {}
      if (label || value || acts.indexOf('AXPress')>=0 || /Text(Field|Area)/.test(role))
        nodes.push({id:n.path.join('.'),role:role,label:label,value:value,secure:secure,oversized:oversized,enabled:get(e,'enabled',true),press:acts.indexOf('AXPress')>=0});
      if (n.path.length < 16) {
        let children=[]; try { children=e.uiElements(); } catch (_) {}
        for(let i=0;i<children.length && i<100 && q.length<350;i++) q.push({e:children[i],path:n.path.concat([i])});
      }
    }
    return JSON.stringify({app:app,pid:pid,elements:nodes,bounded:true});
  }
  let e=ws[0];
  req.path.forEach(function(i){e=e.uiElements()[i];});
  const role=get(e,'role',''), sub=get(e,'subrole','');
  if (/secure|password/i.test(role+' '+sub+' '+get(e,'name',''))) throw Error('Password fields require manual input');
  if (get(e,'enabled',true) === false) throw Error('Control disabled');
  if(req.action==='click') e.actions.byName('AXPress').perform();
  else if(req.action==='type') {
    if(!/AXText(Field|Area)/.test(role)) throw Error('Not an editable text control');
    e.value=req.value;
  } else throw Error('Unsupported UI action');
  return JSON.stringify({message:'Action dispatched. Read a fresh snapshot to verify its effect.'});
}
'''


class DesktopUI:
    def __init__(self, run=None, clock=time.monotonic):
        self.run = run or self._run
        self.clock = clock
        self.frames = {}
        self.lock = threading.Lock()

    @staticmethod
    def _run(request):
        if sys.platform != 'darwin':
            raise ValueError('Native UI adapter currently requires macOS; Linux/Windows need a compatible computer-use connector.')
        proc = subprocess.run(['osascript', '-l', 'JavaScript', '-e', SCRIPT, '--', json.dumps(request)], capture_output=True, timeout=12, check=False)
        if proc.returncode:
            raise ValueError('macOS UI access failed. Enable Accessibility and Automation for the bridge host. ' + proc.stderr.decode(errors='replace')[-500:])
        if len(proc.stdout) > 256_000:
            raise ValueError('Accessibility snapshot exceeds the safe size limit')
        return json.loads(proc.stdout)

    def snapshot(self):
        with self.lock:
            frame = self.run({'action':'read'})
            token = secrets.token_urlsafe(24)
            self.frames = {token:(self.clock(), frame)}
            return dict(frame, snapshot_id=token, expires_in_seconds=60)

    def act(self, request):
        with self.lock:
            saved = self.frames.pop(str(request.get('snapshot_id','')), None)
            if saved is None or self.clock()-saved[0] > 60:
                raise ValueError('Snapshot expired or consumed. Read a fresh screen; nothing executed.')
            old = saved[1]
            action = request.get('action')
            if action not in ('click','type'):
                raise ValueError('Only click and type are supported')
            target = next((e for e in old['elements'] if e['id'] == request.get('element_id')), None)
            if any(e.get('oversized') for e in old['elements']):
                raise ValueError('Screen has oversized content; review manually instead of acting on truncated data')
            if not target or target.get('secure') or not target.get('enabled', True):
                raise ValueError('Control unavailable or protected')
            if action == 'type' and target.get('role') not in ('AXTextField', 'AXTextArea'):
                raise ValueError('Not an editable text control')
            if action == 'click' and not target.get('press'):
                raise ValueError('Control does not expose a press action')
            value = request.get('value','')
            if not isinstance(value,str) or len(value)>8000:
                raise ValueError('Text must be at most 8000 characters')
            if self.run({'action':'read'}) != old:
                raise ValueError('Foreground app or content changed. Nothing executed; review a fresh screen.')
            path = [int(i) for i in target['id'].split('.')] if target['id'] else []
            return self.run({'action':action,'pid':old['pid'],'path':path,'value':value})
