import base64
import copy
import importlib.util
from pathlib import Path
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from desktop_ui import DesktopUI, SCRIPT
from blender_scene import validate

spec = importlib.util.spec_from_file_location('meow_bridge', Path(__file__).with_name('meowmba-bridge.py'))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)

class DesktopUITest(unittest.TestCase):
    def setUp(self):
        self.now = 0
        self.frame = dict(app='TextEdit', pid=7, elements=[dict(id='0.1', role='AXTextArea', label='Draft', value='', secure=False, enabled=True, press=True)])
        self.actions = []
        def run(req):
            if req['action']=='read': return copy.deepcopy(self.frame)
            self.actions.append(req)
            return dict(message='Dispatched')
        self.ui = DesktopUI(run=run, clock=lambda:self.now)

    def request(self, action='type', value='meow ai test'):
        frame=self.ui.snapshot()
        return dict(snapshot_id=frame['snapshot_id'], element_id='0.1', action=action, value=value)

    def test_single_use_exact_action(self):
        req=self.request()
        self.ui.act(req)
        self.assertEqual(self.actions,[dict(action='type',pid=7,path=[0,1],value='meow ai test')])
        with self.assertRaises(ValueError): self.ui.act(req)
        self.assertEqual(len(self.actions),1)

    def test_expired_request_never_executes(self):
        req=self.request(); self.now=61
        with self.assertRaises(ValueError): self.ui.act(req)
        self.assertEqual(self.actions,[])

    def test_changed_content_never_executes(self):
        req=self.request(); self.frame['elements'][0]['value']='A real user draft'
        with self.assertRaises(ValueError): self.ui.act(req)
        self.assertEqual(self.actions,[])

    def test_changed_foreground_never_executes(self):
        req=self.request(); self.frame['pid']=8
        with self.assertRaises(ValueError): self.ui.act(req)
        self.assertEqual(self.actions,[])

    def test_protected_disabled_oversized_targets(self):
        for key,value in [('secure',True),('enabled',False),('oversized',True)]:
            original=copy.deepcopy(self.frame)
            self.frame['elements'][0][key]=value
            req=self.request()
            with self.assertRaises(ValueError): self.ui.act(req)
            self.frame=original
        self.assertEqual(self.actions,[])

    def test_new_snapshot_invalidates_old(self):
        req=self.request(); self.ui.snapshot()
        with self.assertRaises(ValueError): self.ui.act(req)

    def test_invalid_actions_and_indices(self):
        for change in [dict(action='shell'),dict(element_id='0.99'),dict(value='x'*8001)]:
            req=self.request(); req.update(change)
            with self.assertRaises(ValueError): self.ui.act(req)
        self.assertEqual(self.actions,[])

    def test_noneditable_and_nonpressable(self):
        self.frame['elements'][0].update(role='AXButton',press=False)
        with self.assertRaises(ValueError): self.ui.act(self.request())
        with self.assertRaises(ValueError): self.ui.act(self.request(action='click'))

    def test_remote_text_is_data_not_script(self):
        value="'); Application('Terminal').activate(); //"
        self.ui.act(self.request(value=value))
        self.assertEqual(self.actions[0]['value'],value)
        self.assertNotIn(value,SCRIPT)

class SceneTest(unittest.TestCase):
    def test_valid_bounded_primitives(self):
        for kind in ['sphere','cube','cone','cylinder','torus','plane']:
            value=dict(objects=[dict(kind=kind)], render=False)
            self.assertEqual(validate(value),value)

    def test_no_code_paths_or_unbounded_geometry(self):
        for value in [dict(objects=[]),dict(objects=[dict(kind='sphere')]*81),dict(objects=[dict(kind='python')]),dict(objects=[dict(kind='sphere',path='/tmp/user')]),dict(objects=[dict(kind='sphere')],code='import os'),dict(objects=[dict(kind='sphere',scale=[0,1,1])]),dict(objects=[dict(kind='sphere',position=[float('nan'),0,0])]),dict(objects=[dict(kind='sphere',rotation=[True,0,0])]),dict(objects=[dict(kind='sphere')],camera=[0,0,0]),dict(objects=[dict(kind='sphere')],render='yes')]:
            with self.assertRaises(ValueError): validate(value)

class PairingTest(unittest.TestCase):
    def setUp(self):
        bridge._pair_state.clear()
        bridge._pair_state.update(token='ABCDE234',expires=time.time()+100,attempts=0)
        self.handler=bridge.BridgeHandler.__new__(bridge.BridgeHandler)
        self.handler.client_address=('192.168.42.2',5555)
        self.handler.server=SimpleNamespace(cfg={'name':'Test desktop'})
        self.responses=[]
        self.handler._send=lambda code,body:self.responses.append((code,body))
        self.saved=[]
        self.logging=patch.object(bridge,'_log',lambda _:None); self.logging.start()
        self.saving=patch.object(bridge,'_save_pairing',self.saved.append); self.saving.start()

    def tearDown(self):
        self.logging.stop(); self.saving.stop(); bridge._pair_state.clear()

    def test_usb_without_owner_code_is_rejected(self):
        self.handler._handle_pair(dict(usb_auto=True,secret=base64.b64encode(b'x'*32).decode()))
        self.assertEqual(self.responses[-1][0],403); self.assertEqual(self.saved,[])

    def test_code_is_single_use(self):
        req=dict(token='ABCDE234',secret=base64.b64encode(b'x'*32).decode())
        self.handler._handle_pair(req); self.handler._handle_pair(req)
        self.assertEqual([r[0] for r in self.responses],[200,409]); self.assertEqual(len(self.saved),1)

    def test_expired_code_is_rejected(self):
        bridge._pair_state['expires']=time.time()-1
        self.handler._handle_pair(dict(token='ABCDE234'))
        self.assertEqual(self.responses[-1][0],409); self.assertEqual(self.saved,[])

if __name__=='__main__': unittest.main()
