import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from logic_control import LogicController, inventory


class LogicTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.root = Path(self.temp.name)
        self.project = self.root / 'Working Copy.logicx'; self.project.mkdir()
        self.frame = dict(pid=7, frontmost=True, nodes=[
            dict(id='0', role='AXWindow', document=self.project.as_uri() + '/', title='Working Copy - Tracks'),
            dict(id='0.0', role='AXCheckBox', title='Play', value=0, enabled=True),
            dict(id='0.1', role='AXCheckBox', title='Record', value=0, enabled=True),
            dict(id='0.2', role='AXLayoutItem', description='Track 1 “Vocal”', selected=True, enabled=True),
            dict(id='0.2.0', role='AXRadioButton', description='Has Focus', value=1, enabled=True, actions=['AXPress']),
            dict(id='0.2.1', role='AXCheckBox', description='Mute', value=0, enabled=True, actions=['AXPress']),
            dict(id='0.3', role='AXLayoutItem'),
            dict(id='0.3.0', role='AXTextField', description='name', value='Vocal'),
            dict(id='0.3.1', role='AXGroup', description='Read, automation enabled'),
            dict(id='0.3.2', role='AXGroup', description='Third Party Compressor'),
            dict(id='0.3.2.0', role='AXButton', description='open'),
            dict(id='0.3.2.1', role='AXCheckBox', description='bypass'),
            dict(id='0.3.2.2', role='AXButton', description='list'),
            dict(id='1', role='AXWindow', title='Vocal'),
            dict(id='1.2', role='AXStaticText', value='Third Party Compressor'),
            dict(id='1.3', role='AXCell'),
            dict(id='1.3.0', role='AXStaticText', value='Threshold (dB):'),
            dict(id='1.0', role='AXMenuButton', description='view', title='Controls'),
            dict(id='1.3.1', role='AXSlider', display='-6 dB', value=-6, min=-60, max=0, enabled=True, settable=True),
        ])
        self.sleep_patch = patch('logic_control.time.sleep'); self.sleep_patch.start()
        self.now = 0; self.writes = []; self.drop = False; self.crash = False
        owner = self
        class AX:
            def __enter__(self): return self
            def __exit__(self, *_): pass
            def read(self): return copy.deepcopy(owner.frame), {n['id']: n['id'] for n in owner.frame['nodes']}
            def click_control(self, ref, pid): self.write(ref, True, press=True)
            def write(self, ref, value=None, press=False):
                owner.writes.append((ref, value, press))
                if owner.crash: raise RuntimeError('lost connection')
                if not owner.drop:
                    node = next(n for n in owner.frame['nodes'] if n['id'] == ref)
                    node['value'] = 1 - node['value'] if press else value
                    if 'display' in node: node['display'] = str(node['value']) + ' dB'
        self.native = AX
        self.controller = LogicController(self.root / 'receipts', AX, clock=lambda: self.now)

    def tearDown(self): self.sleep_patch.stop(); self.temp.cleanup()

    def request(self, kind='set_plugin_parameter', value=-12):
        frame = self.controller.inspect()
        control = next(c for c in frame['controls'] if c['kind'] == kind)
        return dict(snapshot_id=frame['snapshot_id'], project=frame['project'], control_id=control['id'],
                    kind=kind, target=control['target'], parameter=control['parameter'], value=value)

    def test_generic_third_party_parameter_changes_and_verifies(self):
        result = self.controller.edit(self.request())
        self.assertEqual(result['state'], 'verified'); self.assertEqual(result['observed'], -12)
        self.assertEqual(self.writes, [('1.3.1', -12, False)])

    def test_receipt_prevents_repeat_after_helper_restart(self):
        request = self.request(); first = self.controller.edit(request)
        restarted = LogicController(self.root / 'receipts', self.native)
        result = restarted.edit(request)
        self.assertTrue(result['replay']); self.assertEqual(first['operation_id'], result['operation_id'])
        self.assertEqual(len(self.writes), 1)

    def test_same_snapshot_cannot_claim_a_different_edit(self):
        request = self.request(); self.controller.edit(request)
        with self.assertRaises(ValueError): self.controller.edit(dict(request, value=-20))
        self.assertEqual(len(self.writes), 1)

    def test_stale_foreground_project_parameter_track_or_recording_is_rejected(self):
        for change in [lambda: self.frame.update(frontmost=False),
                       lambda: self.frame['nodes'][0].update(document='file:///Different.logicx/'),
                       lambda: self.frame['nodes'][-1].update(value=-10),
                       lambda: self.frame['nodes'][3].update(description='Track 1 “Other”'),
                       lambda: self.frame['nodes'][2].update(value=1)]:
            old = copy.deepcopy(self.frame); req = self.request(); change()
            with self.assertRaises(ValueError): self.controller.edit(req)
            self.frame = old
        self.assertEqual(self.writes, [])

    def test_expired_consumed_and_newer_snapshots_do_not_write(self):
        req = self.request(); self.now = 61
        with self.assertRaises(ValueError): self.controller.edit(req)
        req = self.request(); self.controller.inspect()
        with self.assertRaises(ValueError): self.controller.edit(req)
        self.assertEqual(self.writes, [])

    def test_invalid_values_and_label_spoofing_do_not_write(self):
        for changes in [dict(value=True), dict(value=float('nan')), dict(value=-61), dict(value='-12'),
                        dict(target='Safe plugin'), dict(parameter='Different parameter'), dict(kind='shell')]:
            req = self.request(); req.update(changes)
            with self.assertRaises((ValueError, TypeError)): self.controller.edit(req)
        self.assertEqual(self.writes, [])

    def test_unconfirmed_dispatch_is_not_success_and_not_repeated(self):
        self.drop = True; req = self.request()
        with self.assertRaises(ValueError): self.controller.edit(req)
        self.assertEqual(self.controller.edit(req)['state'], 'uncertain')
        self.assertEqual(len(self.writes), 1)

    def test_exception_after_write_ahead_receipt_stays_uncertain(self):
        self.crash = True; req = self.request()
        with self.assertRaises(RuntimeError): self.controller.edit(req)
        restarted = LogicController(self.root / 'receipts', self.native)
        self.assertEqual(restarted.edit(req)['state'], 'uncertain')
        self.assertEqual(len(self.writes), 1)

    def test_started_receipt_after_process_loss_never_reexecutes(self):
        req = self.request(); result = self.controller.edit(req)
        path = self.root / 'receipts' / (result['operation_id'] + '.json')
        result['state'] = 'started'; path.write_text(json.dumps(result))
        self.assertEqual(LogicController(self.root / 'receipts', self.native).edit(req)['state'], 'started')
        self.assertEqual(len(self.writes), 1)

    def test_unknown_custom_gui_is_not_advertised_as_parameter_access(self):
        self.frame['nodes'][-2]['title'] = '100%'
        self.assertFalse(inventory(self.frame)['plugin_windows'][0]['editable'])
        self.assertFalse(any(c['kind'] == 'set_plugin_parameter' for c in inventory(self.frame)['controls']))

    def test_no_manufacturer_allowlist(self):
        for name in ['Apple Gain', 'Unlisted Synth 2027', 'Independent AU']:
            next(n for n in self.frame['nodes'] if n['id']=='0.3.2')['description'] = name
            next(n for n in self.frame['nodes'] if n['id']=='1.2')['value'] = name
            self.assertEqual(inventory(self.frame)['plugin_windows'][0]['target'], 'Vocal / ' + name)

    def test_missing_or_disabled_metadata_is_not_editable(self):
        for change in [dict(enabled=False), dict(settable=False), dict(min=None), dict(display='')]:
            old = copy.deepcopy(self.frame); self.frame['nodes'][-1].update(change)
            self.assertFalse(any(c['kind'] == 'set_plugin_parameter' for c in inventory(self.frame)['controls']))
            self.frame = old

    def test_modal_and_multiple_projects_stop_inspection(self):
        for node in [dict(id='2', role='AXSheet'), dict(id='2', role='AXWindow', document='file:///Other.logicx/')]:
            self.frame['nodes'].append(node)
            with self.assertRaises(ValueError): self.controller.inspect()
            self.frame['nodes'].pop()

    def test_unsaved_project_never_edits(self):
        self.project.rmdir(); req = self.request()
        with self.assertRaises(ValueError): self.controller.edit(req)
        self.assertEqual(self.writes, [])

    def test_corrupt_receipt_fails_closed(self):
        req = self.request(); result = self.controller.edit(req)
        (self.root / 'receipts' / (result['operation_id'] + '.json')).write_text('broken')
        with self.assertRaises(ValueError): self.controller.edit(req)
        self.assertEqual(len(self.writes), 1)


    def fader(self, kind='pan', raw=0):
        node = dict(id='0.3.3', role='AXSlider', description=kind, value=raw,
                    min=-64 if kind=='pan' else 0, max=63 if kind=='pan' else 233,
                    settable=True, display=str(raw) if kind=='pan' else '0.0 dB')
        self.frame['nodes'].append(node)
        base = self.native
        class NudgingAX(base):
            def write(ax, ref, value, press=False):
                if ref != node['id']: return super().write(ref, value, press)
                self.writes.append((ref, value, False))
                if self.drop: return
                node['value'] += 1 if value > node['value'] else -1
                node['display'] = (f"{(node['value']-173)/10:.1f} dB" if kind!='pan' else str(node['value']))
            def increment(ax, ref, up): raise AssertionError('Coarse ten-unit steps would overshoot')
        self.controller = LogicController(self.root / 'receipts', NudgingAX)
        return node

    def test_pan_reaches_exact_value_when_setter_only_nudges_one(self):
        self.fader(raw=-31)
        result=self.controller.edit(self.request('set_pan', -16))
        self.assertEqual(result['observed'], -16)
        self.assertEqual(len(self.writes), 15)

    def test_volume_uses_native_position_and_verifies_db(self):
        self.fader('volume fader', 173)
        result=self.controller.edit(self.request('set_volume_db', -1.2))
        self.assertEqual(result['observed'], -1.2)
        self.assertEqual(len(self.writes), 12)
        self.assertEqual(self.writes[0][1], 172)

    def test_unreachable_fader_precision_stops_and_keeps_receipt(self):
        self.fader('volume fader',173);self.drop=True;request=self.request('set_volume_db',-1.0)
        with self.assertRaises(ValueError): self.controller.edit(request)
        self.assertEqual(self.controller.edit(request)['state'], 'uncertain')
        self.assertLess(len(self.writes), 4)

    def test_grouped_or_write_automation_disables_track_and_plugin_edits(self):
        for mode in ['Touch, automation enabled', 'Latch, automation enabled']:
            next(n for n in self.frame['nodes'] if n['id']=='0.3.1')['description']=mode
            self.assertFalse(any(c['kind'] in ('set_mute','set_plugin_parameter') for c in self.controller.inspect()['controls']))

    def test_mute_label_suffix_and_implicit_solo_mute_keep_track_identity(self):
        baseline=inventory(self.frame)
        self.frame['nodes'][3]['description']='Track 1 “Vocal”, mute'
        self.frame['nodes'][5]['value']=-1
        state=inventory(self.frame)
        self.assertEqual(state['tracks'], baseline['tracks'])
        self.assertFalse(next(c for c in state['controls'] if c['kind']=='set_mute')['value'])

    def test_absolute_indicator_avoids_thousands_of_nudges(self):
        self.frame['nodes'].append(dict(id='1.3.2',role='AXValueIndicator',value=-6,settable=True))
        base=self.native
        class AbsoluteAX(base):
            def write(ax, ref, value, press=False):
                self.assertEqual(ref,'1.3.2')
                super().write(ref,value,press)
                next(n for n in self.frame['nodes'] if n['id']=='1.3.1')['value']=value
        self.controller=LogicController(self.root/'receipts',AbsoluteAX)
        result=self.controller.edit(self.request())
        self.assertEqual(result['observed'],-12)
        self.assertEqual(len(self.writes),1)

    def test_native_apple_and_third_party_control_trees(self):
        for name,plugin,parameter in [('eq','Channel EQ','Low Shelf Gain'),('studioai','StudioAI','17 Output Trim dB')]:
            fixture=Path(__file__).parent/'fixtures'/f'logic-{name}-controls.json'
            state=inventory(json.loads(fixture.read_text()))
            windows=[w for w in state['plugin_windows'] if w['name']==plugin]
            self.assertEqual(len(windows),1)
            self.assertTrue(windows[0]['editable'])
            controls=[c for c in state['controls'] if c['kind']=='set_plugin_parameter' and c['parameter']==parameter]
            self.assertEqual(len(controls),1)
            self.assertIn('dB',controls[0]['display'])


    def test_controls_view_uses_only_observed_menu_choice(self):
        self.frame['nodes'][-2]['title']='100%'
        base=self.native
        class MenuAX(base):
            def write(ax,ref,value=None,press=False):
                self.writes.append((ref,value,press))
                if ref=='1.0':
                    self.frame['nodes'].extend([dict(id='1.0.0',role='AXMenu'),dict(id='1.0.0.0',role='AXMenuItem',title='Controls',enabled=True)])
                elif ref=='1.0.0.0':
                    self.frame['nodes']=[n for n in self.frame['nodes'] if not n['id'].startswith('1.0.')]
                    next(n for n in self.frame['nodes'] if n['id']=='1.0')['title']='Controls'
                else: raise AssertionError('Unobserved menu action')
        self.controller=LogicController(self.root/'receipts',MenuAX)
        result=self.controller.edit(self.request('show_plugin_controls',True))
        self.assertEqual(result['state'],'verified')
        self.assertEqual(len(self.writes),2)

    def test_duplicate_plugin_instances_do_not_offer_ambiguous_parameters(self):
        slot=[n for n in self.frame['nodes'] if n['id']=='0.3.2' or n['id'].startswith('0.3.2.')]
        self.frame['nodes'].extend([dict(n,id=n['id'].replace('0.3.2','0.3.4')) for n in slot])
        self.assertFalse(any(c['kind']=='set_plugin_parameter' for c in self.controller.inspect()['controls']))

    def test_changed_sibling_after_dispatch_is_never_verified(self):
        base=self.native
        class CollateralAX(base):
            def write(ax,ref,value=None,press=False):
                super().write(ref,value,press)
                self.frame['nodes'][5]['value']=1
        self.controller=LogicController(self.root/'receipts',CollateralAX)
        request=self.request()
        with self.assertRaisesRegex(ValueError,'Other Logic controls changed'):self.controller.edit(request)
        self.assertEqual(self.controller.edit(request)['state'],'uncertain')


if __name__ == '__main__': unittest.main()
