import copy
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from logic_control import LogicController
from logic_workflow import LogicWorkflow, elements
from macos_ax import LogicDispatchUncertain


class WorkflowTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.root = Path(self.tmp.name)
        self.project = self.root / 'Working.logicx'; self.project.mkdir()
        self.frame = dict(pid=9, frontmost=True, nodes=[
            dict(id='0', role='AXWindow', title='Working', document=self.project.as_uri()),
            dict(id='0.0', role='AXCheckBox', title='Play', value=0),
            dict(id='0.1', role='AXCheckBox', title='Record', value=0),
            dict(id='0.2', role='AXTextField', identifier='RegionName', value='Original', settable=True),
            dict(id='1', role='AXMenuBar'),
            dict(id='1.0', role='AXMenuBarItem', title='File', actions=['AXPress']),
            dict(id='1.0.0', role='AXMenu', actions=['AXCancel']),
            dict(id='1.0.0.0', role='AXMenuItem', title='Import', actions=['AXPress']),
        ])
        self.writes = []; self.drop = False; self.timeout = False; self.after_write = None; self.now=0
        self.close_path = False
        owner = self
        class Native:
            def __enter__(self): return self
            def __exit__(self, *_): pass
            def read(self, workflow=False):
                assert workflow
                return copy.deepcopy(owner.frame), {n['id']:n['id'] for n in owner.frame['nodes']}
            def write(self, ref, value=None, press=False):
                owner.writes.append((ref,value,press))
                if not owner.drop:
                    node = next(n for n in owner.frame['nodes'] if n['id']==ref)
                    if press: owner.frame['nodes'].append(dict(id='2',role='AXWindow',title='Open File'))
                    else: node['value']=value
                if owner.after_write: owner.after_write()
                if owner.timeout: raise LogicDispatchUncertain('No acknowledgement')
            def workflow_key(self, command, pid, ref=None):
                owner.writes.append((command,pid,ref))
                owner.frame['focus']={'role':'AXTextField','id':ref}
                if owner.close_path:
                    owner.frame['nodes']=[n for n in owner.frame['nodes'] if n.get('identifier')!='PathTextField']
        self.c = LogicController(self.root/'receipts', Native, clock=lambda:self.now)
        self.w = LogicWorkflow(self.c)
        self.sleep = patch('logic_workflow.time.sleep'); self.sleep.start()

    def tearDown(self): self.sleep.stop(); self.tmp.cleanup()

    def request(self, action='set_text', value='Edited', query='RegionName'):
        s=self.w.screen(query); e=next(e for e in s['elements'] if action in e['actions'])
        return dict(snapshot_id=s['snapshot_id'],project=s['project'],element_id=e['id'],action=action,value=value)

    def test_text_identity_stays_stable_and_value_is_read_back(self):
        result=self.w.act(self.request())
        self.assertEqual(result['state'],'verified'); self.assertEqual(result['observed'],'Edited')

    def test_menu_dispatch_is_only_an_observed_transition(self):
        result=self.w.act(self.request('press','','Import'))
        self.assertEqual(result['state'],'observed')
        self.assertIn('not verified',result['message'])

    def test_path_autocomplete_or_focus_change_is_not_navigation(self):
        self.frame['nodes'].append(dict(id='2',role='AXTextField',identifier='PathTextField',value=str(self.root),settable=True))
        result=self.w.act(self.request('commit_text','','PathTextField'))
        self.assertEqual(result['state'],'uncertain')
        self.assertEqual(len(self.writes),1)

    def test_opening_menu_with_preloaded_children_observes_selection(self):
        self.drop=True
        node=self.frame['nodes'][-1];node['selected']=False
        self.after_write=lambda:node.update(selected=True)
        result=self.w.act(self.request('press','','Import'))
        self.assertEqual(result['state'],'observed')
        self.assertEqual(len(self.writes),1)

    def test_path_confirmation_waits_for_sheet_to_close(self):
        self.frame['nodes'].append(dict(id='2',role='AXTextField',identifier='PathTextField',value=str(self.root),settable=True))
        self.close_path=True
        result=self.w.act(self.request('commit_text','','PathTextField'))
        self.assertEqual(result['state'],'observed')

    def test_timeout_after_dispatch_observes_without_resending(self):
        self.timeout=True
        result=self.w.act(self.request('press','','Import'))
        self.assertEqual(result['state'],'observed'); self.assertEqual(len(self.writes),1)

    def test_no_change_stays_uncertain_and_replay_never_clicks_again(self):
        self.drop=True; req=self.request('press','','Import')
        result=self.w.act(req)
        self.assertEqual(result['state'],'uncertain')
        self.assertTrue(self.w.act(req)['replay']); self.assertEqual(len(self.writes),1)

    def test_read_only_context_drift_or_recording_blocks_writes(self):
        for change in [lambda:self.frame.update(frontmost=False),
                       lambda:self.frame['nodes'][3].update(value='Human edit'),
                       lambda:self.frame['nodes'][2].update(value=1)]:
            original=copy.deepcopy(self.frame); req=self.request(); change()
            with self.assertRaises(ValueError): self.w.act(req)
            self.frame=original
        self.assertFalse(self.writes)

    def test_already_recording_cannot_edit(self):
        self.frame['nodes'][2]['value']=1
        with self.assertRaises(ValueError): self.w.act(self.request())
        self.assertFalse(self.writes)

    def test_expired_and_superseded_snapshots(self):
        req=self.request();self.now=61
        with self.assertRaises(ValueError):self.w.act(req)
        req=self.request();self.w.screen()
        with self.assertRaises(ValueError):self.w.act(req)
        self.assertFalse(self.writes)

    def test_unknown_action_extra_fields_and_nonfinite_numbers_rejected(self):
        for changes in [dict(action='shell'),dict(script='anything'),dict(value='a\nb')]:
            req=self.request();req.update(changes)
            with self.assertRaises(ValueError): self.w.act(req)
        self.frame['nodes'].append(dict(id='0.3',role='AXSlider',description='bar',settable=True,value=1,min=1,max=8))
        for value in ('NaN','Infinity','9'):
            with self.assertRaises(ValueError):self.w.act(self.request('set_number',value,'bar'))
        self.assertFalse(self.writes)

    def test_cancel_before_delayed_request_survives_restart(self):
        req=self.request();op=hashlib.sha256(req['snapshot_id'].encode()).hexdigest()
        self.c.cancel(op)
        with self.assertRaises(ValueError):self.w.act(req)
        with self.assertRaises(ValueError):LogicController(self.root/'receipts').check_cancelled(op)
        self.assertFalse(self.writes)

    def test_cancel_during_dispatch_records_partial_outcome(self):
        req=self.request();op=hashlib.sha256(req['snapshot_id'].encode()).hexdigest()
        self.after_write=lambda:self.c.cancel(op)
        with self.assertRaises(ValueError):self.w.act(req)
        self.assertEqual(self.c.receipt(op)['state'],'cancelled')
        self.assertEqual(self.frame['nodes'][3]['value'],'Edited')
        self.assertTrue(self.w.act(req)['replay']);self.assertEqual(len(self.writes),1)

    def test_restart_replay_returns_durable_receipt(self):
        req=self.request();self.w.act(req)
        other=LogicWorkflow(LogicController(self.root/'receipts'))
        self.assertTrue(other.act(req)['replay']);self.assertEqual(len(self.writes),1)
        with self.assertRaises(ValueError):other.act(dict(req,value='Other'))

    def test_unrelated_menu_branches_are_not_executable(self):
        self.frame['nodes'].extend([
            dict(id='1.1',role='AXMenuBarItem',title='Apple',actions=['AXPress']),
            dict(id='1.1.0',role='AXMenuItem',title='Shut Down',actions=['AXPress']),
            dict(id='1.0.1',role='AXMenuItem',title='Share',actions=['AXPress']),
            dict(id='1.0.1.0',role='AXMenuItem',title='Mail',actions=['AXPress'])])
        self.assertFalse(any(e['label'] in ('Apple','Shut Down','Share','Mail') for e in elements(self.frame)))

    def test_menu_paths_keep_plugin_identity_without_vendor_allowlist(self):
        self.frame['nodes'].extend([
            dict(id='0.4',role='AXMenu',actions=['AXCancel']),
            dict(id='0.4.0',role='AXMenuItem',title='Independent AU',actions=['AXPress']),
            dict(id='0.4.0.0',role='AXMenuItem',title='Stereo',actions=['AXPress'])])
        row=self.w.screen('Independent AU Stereo')['elements'][0]
        self.assertEqual(row['path'],'Working / Menu / Independent AU / Stereo')
        self.assertIn('press',row['actions'])

    def test_filename_rows_cannot_be_renamed_through_text_action(self):
        self.frame['nodes'][3]['url']='file:///outside/source.wav'
        self.assertEqual(self.w.screen('RegionName')['elements'],[])
        self.frame['nodes'][3]['selected']=True
        self.assertEqual(self.w.screen('RegionName')['elements'][0]['actions'],[])

    def test_file_chooser_rejects_outside_workspace_and_missing_paths(self):
        self.frame['nodes'].append(dict(id='2',role='AXWindow',title='Open File'))
        for path in ('/etc/hosts',str(self.root/'missing')):
            req=self.request('choose_path',path,'Open File')
            with self.assertRaises(ValueError):self.w.act(req,[str(self.root)])

    def test_export_destination_cannot_select_an_existing_audio_file(self):
        source=self.root/'source.wav';source.write_bytes(b'original')
        self.frame['nodes'].extend([dict(id='2',role='AXWindow',title='Bounce'),
            dict(id='2.0',role='AXTextField',identifier='saveAsNameTextField',value='export.wav',settable=True)])
        with self.assertRaisesRegex(ValueError,'Choose a folder'):
            self.w.act(self.request('choose_path',str(source),'Bounce'),[str(self.root)])
        self.assertEqual(source.read_bytes(),b'original');self.assertFalse(self.writes)
        self.assertFalse(self.writes)

    def test_pagination_is_explicit_and_bounded(self):
        self.frame['nodes'].extend(dict(id=f'0.{i}',role='AXStaticText',value=f'Parameter {i}') for i in range(20,140))
        first=self.w.screen('Parameter');second=self.w.screen('Parameter',100)
        self.assertEqual(len(first['elements']),100);self.assertEqual(first['next_offset'],100)
        self.assertEqual(len(second['elements']),20);self.assertIsNone(second['next_offset'])
        for args in [('x'*201,0),('',-1),('',True)]:
            with self.assertRaises(ValueError):self.w.screen(*args)


if __name__=='__main__':unittest.main()
