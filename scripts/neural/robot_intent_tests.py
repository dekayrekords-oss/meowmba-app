import ast
import math
from pathlib import Path
import unittest
from robot_intent_replay import NeckSimulation,intent,replay


class RobotIntentReplayTests(unittest.TestCase):
    def test_transport_free_imports_and_destination_boundary(self):
        tree=ast.parse(Path(__file__).with_name('robot_intent_replay.py').read_text())
        imports={n.names[0].name if isinstance(n,ast.Import) else n.module for n in ast.walk(tree) if isinstance(n,(ast.Import,ast.ImportFrom))}
        self.assertEqual(imports,{'argparse','collections','json','math','pathlib'})
        sim=NeckSimulation()
        for destination in ('tcp','bluetooth','serial','robot','simulation;execute'):
            self.assertFalse(sim.submit(intent(0,0,destination=destination)))
        self.assertEqual(sim.commands,0)

    def test_schema_stale_order_generation_and_finite_rejections(self):
        sim=NeckSimulation();sim.advance(100)
        for patch in [{'value':float('nan')},{'value':float('inf')},{'value':True},{'units':'radians'},
                      {'version':True},{'action':'drive'},{'generation':True},{'generation':1},
                      {'episodeId':'other'},{'issuedAtMs':101},{'expiresAtMs':100},{'step':True}]:
            self.assertFalse(sim.submit(dict(intent(1,100),**patch)),patch)
        self.assertTrue(sim.submit(intent(2,100,.1)))
        self.assertFalse(sim.submit(intent(2,100,.2)))
        self.assertFalse(sim.submit(intent(3,90,.2)))
        self.assertEqual(sim.accepted,1)

    def test_newest_only_spacing_and_no_banked_slew(self):
        sim=NeckSimulation()
        self.assertTrue(sim.submit(intent(1,0,.35)))
        sim.advance(100);self.assertTrue(sim.submit(intent(2,100,-.35)))
        sim.advance(240);self.assertEqual(sim.commands,0)
        sim.advance(250)
        self.assertEqual(sim.commands,1);self.assertEqual(sim.command,-.0625)
        self.assertIsNone(sim.pending)
        sim.advance(1000)
        self.assertTrue(sim.submit(intent(3,1000,.35)))
        sim.advance(1010)
        self.assertEqual(sim.command,0.)  # Idle time cannot bank a larger target jump.

    def test_expired_pending_is_not_emitted(self):
        sim=NeckSimulation();sim.submit(intent(1,0,.35,expiresAtMs=100))
        sim.advance(300)
        self.assertEqual(sim.commands,0);self.assertEqual(sim.command,0.)
        self.assertEqual(sim.rejections['expired-pending'],1)

    def test_interrupt_and_resume_never_replay_queued_or_old_generation(self):
        sim=NeckSimulation();sim.submit(intent(1,0,.35));sim.advance(250)
        sim.submit(intent(2,250,-.35));before=sim.commands
        sim.interrupt('touch');sim.advance(500)
        self.assertEqual(sim.commands,before);self.assertIsNone(sim.pending)
        self.assertFalse(sim.submit(intent(3,500,.35,generation=0)))
        self.assertFalse(sim.submit(intent(3,500,.35,generation=sim.generation)))
        sim.resume();sim.advance(1000)
        self.assertEqual(sim.commands,before)
        self.assertFalse(sim.submit(intent(4,1000,.35,generation=1)))
        self.assertTrue(sim.submit(intent(1,1000,.1,generation=sim.generation)))
        sim.advance(1010);self.assertEqual(sim.commands,before+1)

    def test_hold_is_simulation_latch_and_gestures_are_allowlisted(self):
        sim=NeckSimulation()
        self.assertFalse(sim.submit(intent(0,0,action='gesture',value='dance-and-drive')))
        self.assertTrue(sim.submit(intent(1,0,action='gesture',value='look-up')))
        sim.advance(250);self.assertLess(sim.command,0)
        self.assertTrue(sim.submit(intent(2,250,action='hold')))
        self.assertFalse(sim.active);self.assertIsNone(sim.pending)

    def test_inertial_bounds_and_bounded_history(self):
        sim=NeckSimulation()
        for t in range(0,10001,10):
            sim.advance(t)
            if t%50==0:sim.submit(intent(t,t,100. if t<5000 else -100.))
            self.assertTrue(math.isfinite(sim.position))
            self.assertLessEqual(abs(sim.position),.35)
            self.assertLessEqual(abs(sim.velocity),.5)
            self.assertLessEqual(len(sim.history),200)
        self.assertEqual(len(sim.history),200)
        self.assertLessEqual(sim.peak_acceleration,2.)
        self.assertGreater(sim.peak_speed,0.)
        self.assertGreater(sim.peak_position,0.)

    def test_monotonic_clock_and_full_replay_determinism(self):
        sim=NeckSimulation();sim.advance(100)
        for value in (90,101,True,1200):
            with self.assertRaises(ValueError):sim.advance(value)
        self.assertEqual(replay(),replay())
        result=replay()
        self.assertEqual(result['integratorSteps'],1000)
        self.assertEqual(result['historyLength'],200)
        self.assertGreater(result['rejections']['nonfinite'],0)


if __name__=='__main__':unittest.main()
