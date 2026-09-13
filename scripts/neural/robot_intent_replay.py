#!/usr/bin/env python3
"""Transport-free scripted intent replay with explicitly authored toy dynamics."""
import argparse
from collections import Counter, deque
import json
import math
from pathlib import Path

PARAMETERS = {'positionRange': .35, 'commandSpacingMs': 250, 'commandSlewPerSecond': .25,
              'naturalFrequencyPerSecond': 8., 'dampingRatio': 1.,
              'maxSpeedPerSecond': .5, 'maxAccelerationPerSecondSquared': 2., 'integrationStepMs': 10}


def intent(step, now_ms, value=0., generation=0, **changes):
    record = {'version': 1, 'destination': 'simulation', 'episodeId': 'scripted-01',
              'generation': generation, 'step': step, 'issuedAtMs': now_ms,
              'expiresAtMs': now_ms+400, 'source': 'scripted-already-arbitrated',
              'action': 'neckTarget', 'units': 'normalized-neck', 'value': value}
    record.update(changes)
    return record


class NeckSimulation:
    """No injected output or transport: intent can only change this numeric model."""
    def __init__(self):
        self.time_ms = 0
        self.generation = 0
        self.active = True
        self.last_step = -1
        self.last_issued = -1
        self.last_command_ms = 0
        self.pending = None
        self.command = self.position = self.velocity = 0.
        self.history = deque(maxlen=200)
        self.rejections = Counter()
        self.commands = 0
        self.accepted = 0
        self.peak_speed = self.peak_position = self.peak_acceleration = 0.

    def reject(self, reason):
        self.rejections[reason] += 1
        return False

    def submit(self, record):
        if not isinstance(record, dict): return self.reject('malformed')
        if type(record.get('version')) is not int or record['version'] != 1 or record.get('destination') != 'simulation': return self.reject('destination/version')
        if record.get('source') != 'scripted-already-arbitrated': return self.reject('source')
        if record.get('episodeId') != 'scripted-01': return self.reject('episode')
        if type(record.get('generation')) is not int or record['generation'] != self.generation: return self.reject('generation')
        if not self.active: return self.reject('interrupted')
        if any(type(record.get(k)) is not int for k in ['step','issuedAtMs','expiresAtMs']): return self.reject('timestamp-type')
        if record['step'] < 0 or record['step'] <= self.last_step or record['issuedAtMs'] < self.last_issued: return self.reject('order')
        if not 0 <= record['issuedAtMs'] <= self.time_ms < record['expiresAtMs'] or not 0 < record['expiresAtMs']-record['issuedAtMs'] <= 500: return self.reject('stale/future')
        if record.get('units') != 'normalized-neck': return self.reject('units')
        action = record.get('action')
        value = record.get('value')
        if action == 'gesture':
            # One safe, immediate target, never an autonomous queued gesture clip.
            if value not in ('center','look-up','look-down'): return self.reject('gesture')
            value = {'center':0.,'look-up':-.15,'look-down':.15}[value]
        elif action == 'hold': value = self.position
        elif action != 'neckTarget': return self.reject('action')
        if type(value) not in (int,float) or not math.isfinite(value): return self.reject('nonfinite')
        self.last_step = record['step']; self.last_issued = record['issuedAtMs']; self.accepted += 1
        if action == 'hold':
            self.interrupt('explicit-hold')
            return True
        self.pending = (max(-.35,min(.35,float(value))),record['expiresAtMs'],record['step'])
        return True

    def interrupt(self, reason):
        self.generation += 1; self.active = False; self.pending = None
        self.last_step = self.last_issued = -1
        # Toy emergency damping; this is not a physical brake or stop-distance claim.
        self.command = self.position
        self.history.append({'kind':'interrupt','atMs':self.time_ms,'generation':self.generation,'reason':reason})

    def resume(self):
        self.generation += 1; self.active = True; self.pending = None
        self.last_step = self.last_issued = -1
        self.last_command_ms = self.time_ms
        self.command = self.position
        self.history.append({'kind':'resume','atMs':self.time_ms,'generation':self.generation})

    def advance(self, now_ms):
        if type(now_ms) is not int or now_ms < self.time_ms or now_ms-self.time_ms > 1000 or now_ms%10: raise ValueError('monotonic 10ms clock; advance at most1000ms')
        while self.time_ms < now_ms:
            self.time_ms += 10
            if self.pending and self.pending[1] <= self.time_ms:
                self.pending = None; self.rejections['expired-pending'] += 1
            if self.active and self.pending and self.time_ms-self.last_command_ms >= 250:
                desired,_,step = self.pending
                # Fixed spacing-based budget: idle time must not bank a huge slew jump.
                delta = max(-.0625,min(.0625,desired-self.command))
                self.command += delta; self.last_command_ms = self.time_ms; self.pending = None
                self.commands += 1
                self.history.append({'kind':'command','atMs':self.time_ms,'generation':self.generation,'step':step,'target':self.command})
            acceleration = 64*(self.command-self.position)-16*self.velocity if self.active else -16*self.velocity
            acceleration = max(-2.,min(2.,acceleration))
            self.velocity = max(-.5,min(.5,self.velocity+acceleration*.01))
            self.position += self.velocity*.01
            if abs(self.position) > .35:
                self.position = math.copysign(.35,self.position); self.velocity = 0.
            self.peak_speed = max(self.peak_speed,abs(self.velocity))
            self.peak_position = max(self.peak_position,abs(self.position))
            self.peak_acceleration = max(self.peak_acceleration,abs(acceleration))
            self.history.append({'kind':'state','atMs':self.time_ms,'position':self.position,'velocity':self.velocity})


def replay():
    sim = NeckSimulation(); trajectory=[]
    # Scripted gaze/allowlisted-gesture intents, NOT live neural or phone traces.
    for t in range(0,10001,10):
        sim.advance(t)
        if t in (2000,5000,8000): sim.interrupt({2000:'touch',5000:'pause',8000:'model-exception'}[t])
        if t in (2500,5500,8500): sim.resume()
        if t%50 == 0:
            value = .3*math.sin(2*math.pi*t/4000)
            sim.submit(intent(t//10,t,value,sim.generation))
        if t in (1200,3600,6200): sim.submit(intent(t//10+1,t,float('nan'),sim.generation))
        if t in (2300,5300,8300): sim.submit(intent(t//10+2,t,.3,0))
        if t in (3000,6000): sim.submit(intent(t//10+3,t-600,.3,sim.generation))
        if t%50==0:trajectory.append({'timeMs':t,'position':sim.position,'velocity':sim.velocity,'command':sim.command,'active':sim.active})
    return {'scope':'scripted already-arbitrated proposals into transport-free authored neck dynamics; no actual neural or hardware replay',
            'parameters':PARAMETERS,'units':'dimensionless normalized neck position; rates per second; not calibrated angles/torques',
            'durationMs':sim.time_ms,'integratorSteps':sim.time_ms//10,'acceptedProposals':sim.accepted,'emittedSimulationCommands':sim.commands,
            'rejections':dict(sim.rejections),'historyLength':len(sim.history),'peakPosition':sim.peak_position,
            'peakSpeed':sim.peak_speed,'peakAcceleration':sim.peak_acceleration,'finalPosition':sim.position,'trajectory':trajectory,
            'transport':'none exists in this harness; no network, device, serial, Bluetooth or injected actuator output',
            'limitations':['Authored inertial model has no calibrated mass/friction/firmware latency or stop distance.',
                           'Already-arbitrated scripted intent does not evaluate upstream RNN versus SNN quality.',
                           'No physical robot readiness or biological motor mapping is established.']}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    report=replay();args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='trajectory'}))


if __name__=='__main__':main()
