# Transport-free robot intent replay — 2026-09-13

Meow: this implements the bounded numeric experiment proposed in `ROBOT_TRANSFER_SIMULATION_PLAN.md`. It runs locally with Python's standard library and does not construct a transport, connect to anything, call a device API, or inject an actuator output. Android code, physical-control quarantine, and existing flight evidence were unchanged.

## What was implemented

`scripts/neural/robot_intent_replay.py` accepts explicitly labeled **scripted already-arbitrated** proposals, not phone/neural recordings. Each proposal carries simulation destination, episode, generation, monotonically ordered step and issue time, expiration, source, action, and units. The only actions are a neck target, explicit hold, or the allowlisted single-target gestures center/look-up/look-down. Gesture strings cannot expand into scripts or hardware commands.

The gate rejects wrong destinations/episodes/generations, malformed timestamps, out-of-order proposals, future or expired records, unknown units/actions, and non-finite targets. Targets are bounded to ±0.35 normalized neck units, matching the app's existing numeric envelope but without claiming that a normalized unit is a measured angle. A pending target replaces the prior pending target; there is never a queue to drain later. Commands are spaced at least 250 ms apart and can advance their target by at most 0.0625 per update, a 0.25-unit/second update budget. Idle time cannot accumulate a larger jump allowance. The discrete target may step while the simulated position follows inertially.

Interruptions invalidate the generation, clear pending intent, latch inactivity, and apply authored damping in the simulation only. Resume advances generation again and requires a fresh proposal; previously pending or old-generation work cannot replay. Explicit hold uses this latch. There is no assertion that interrupt instantly stops inertia, or that a simulated hold corresponds to a valid physical brake packet. An already-issued position target normally remains the numeric target until replaced or interrupted; proposal expiry gates submission/pending dispatch, not a firmware watchdog.

## Authored dynamics and measurement scope

The neck model is deliberately simple and **not calibrated to a robot**. Position is normalized and dimensionless. A semi-implicit 10 ms integrator uses acceleration `64*(target-position) - 16*velocity`, corresponding to authored natural frequency 8/s and damping ratio 1. Acceleration is capped at 2 units/s² and speed at 0.5 units/s; position is constrained to ±0.35. Interrupted motion uses damping only. No mass, friction, motor torque, joint angle calibration, firmware latency, contact, or physical stop distance is represented.

The deterministic 10-second script delivers sinusoidal gaze targets every 50 ms, injected NaN proposals, old-generation callbacks, invalid older issue times, and touch/pause/model-exception interruptions with explicit resumes. All intent is already arbitrated by construction. This experiment does not implement or evaluate the upstream explicit/clip/follow/mood arbitration, and does not compare rate versus spiking model quality. Doing that honestly requires controller-produced proposal traces with their provenance; relabeling the scripted stream as neural output would be false.

## Actual results and tests

`ROBOT_INTENT_REPLAY_2026-09-13.json` records the authored parameters and measured replay:

| Measure | Observed result |
| --- | ---: |
| Simulation duration / integration steps | 10,000 ms / 1,000 |
| Accepted proposals / emitted numeric commands | 171 / 34 |
| Rejections: non-finite / interrupted / wrong generation / order | 3 / 30 / 3 / 2 |
| Peak absolute position | 0.267055504 |
| Peak absolute speed | 0.293246160 units/s |
| Peak absolute acceleration | 2.0 units/s² |
| Final position | 0.099716829 |
| Retained history | 200 entries |

Eight dedicated tests pass. They cover permitted imports and rejected physical destinations; strict schema/stale/order/generation/NaN rejection; newest-only target selection and spacing without idle-time slew banking; expiration before dispatch; no output while interrupted and no stale replay on resume; hold and gesture allowlisting; finite inertial bounds with a 200-entry history over 10 seconds; monotonic clock restrictions and exact complete-replay determinism. Test fixtures exercise stale expiration separately from the scripted report's old-timestamp order rejection. No physical I/O call counter is invented: the module has no transport or device path at all, and the test enforces its standard-library import surface.

An initial test fixture accidentally passed the `step` parameter twice while testing malformed steps. It was corrected by mutating the finished proposal dictionary, leaving production validation unchanged. All eight tests then passed. Commands:

```sh
python3 -m unittest discover -s scripts/neural -p 'robot_*tests.py' -v
python3 scripts/neural/robot_intent_replay.py --output docs/neural/ROBOT_INTENT_REPLAY_2026-09-13.json
git diff --check
```

## Remaining hardware boundary

The specific unresolved blocker is the existing firmware-specific stop protocol: `RobotBaseControlPolicy.CONTROL_AVAILABLE` remains false because a prior supposed brake sent rotation. Exact device/firmware identity, verified command and emergency-stop semantics, calibrated operating limits, sensor decoding, and supervised physical validation are still required. This numeric harness neither supplies those facts nor authorizes acquiring them by moving hardware. A successful simulated slew/interrupt test is not evidence of a safe real motor.

Meow should reproduce the rejection or trajectory against this transport-free model first when debugging intent ordering. Preserve generation invalidation, freshest-only pending work, explicit units, bounded history, and the distinction between command history and physical telemetry. Do not add a transport parameter to this research class; any future hardware adapter belongs behind the existing separately validated gates.

Parent owns staging and the integrated commit. This pass added only two `robot_*` scripts, the aggregate report, and this teaching passdown. No Android, network, device, physical transport, flight evidence, or quarantine file was modified.
