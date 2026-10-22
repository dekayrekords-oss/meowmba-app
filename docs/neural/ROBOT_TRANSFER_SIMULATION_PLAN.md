# Simulation-only robot transfer exploration — 2026-09-13

This is a source audit and proposed experiment, not hardware readiness certification. No scan, connection, transport write, motor packet, or physical actuation was attempted.

## Existing quarantine and useful interfaces

`core/robot/RobotBaseControlPolicy.kt` sets `CONTROL_AVAILABLE=false`. Its reason is concrete: the firmware-specific stop protocol has not been physically verified; a supposed brake previously sent a rotation script. A zero-valued packet and radio disconnect do not prove a stopped motor. No learned output, preference, app resume, or sensor observation may override this policy.

`RobotManager.kt` rejects legacy TCP/classic-Bluetooth connection paths; `LooiBleLink.kt` guards explicit BLE connects and quarantines motor/light/stop writes. `BodySafety.kt` uses revocable playback leases and priority explicit motion > clip > follow > mood. Pause, touch, connection change, cliff, and unknown sensor data invalidate queued ownership. Sensor stop is latched until explicit resume. DESK is the default; FLOOR remains unavailable until safety decoding is verified. Rotation additionally requires `wheelControlVerified`. These are boundaries to preserve, not obstacles for a neural model to learn around.

Available code is not available hardware. `MotorController` describes a future articulated servo device; `BodyOutput` describes a LOOI neck/wheeled base. They are deliberately different. The latter has no six legs, fly wings, human arms, grasp feedback, or confirmed reverse channel. `SimulatedMotorController`, `SimulatedBodyOutput`, and `LoopbackTransport` provide logging/test sinks. TCP/Bluetooth transport implementations existing in the tree do not establish a verified protocol or an attached device. Hardware model/firmware identity, actuator telemetry, stop bounds, sensor interpretation, and validated control envelope remain unverified by this pass.

## Proposed bounded experiment

Use a standalone pure simulation adapter that cannot construct Android Bluetooth, socket, or serial transports. Feed it replayed already-arbitrated avatar decisions; do not add another neural or audio loop. Lumen gaze can request a simulated neck target, an allowlisted clip can request a simulated gesture, and a privacy/lifecycle stop cancels the simulation trajectory. Fruit-fly leg and wing state remains in the fly simulator because it does not map to a wheeled base.

Proposed simulator-only intent record (not a shipped API):

```json
{
  "version": 1,
  "destination": "simulation",
  "episodeId": "fixed-seed-episode-01",
  "generation": 3,
  "step": 42,
  "issuedAtMs": 2100,
  "expiresAtMs": 2300,
  "source": "arbitrated-avatar",
  "action": "neckTarget",
  "value": 0.15
}
```

Allow only `hold`, `neckTarget`, and a small fixed `gesture` vocabulary in the initial experiment. Reject unknown actions, non-finite numbers, expired/out-of-order records, wrong episode/generation, non-simulation destinations, and unspecified units. Bound neck intent to the existing configured range, apply an explicit slew cap and 250 ms minimum command spacing, and keep only the newest target. Use monotonic simulation time, a 200-entry bounded observation history, and no deferred resume playback. `hold` is a simulated state transition, never translated into an assumed physical brake packet.

Run fixed episodes with cue jitter, stale input, dropped steps, model exceptions, NaN outputs, touch interruption, pause/resume, and old-generation callbacks. Assert bounded positions/velocities, no output after interruption, no stale cue after resume, correct priority, and **zero calls to any real transport**. Compare rate and spiking proposals through the same arbiter and same simulator; record task metrics as well as cost. Existing BodySafety and scheduler tests provide fixtures, but logging alone does not simulate inertia, contact, friction, or stop distance.

A second stage may use a separately installed body physics simulator with pinned model and trajectory licenses, fixed seeds, and held-out perturbations. Biological anatomical adjacency, learned policy parameters, and authored avatar-to-body mappings must be labeled separately. The BANC roster audit supplies provenance practice, not a controller for this stage.

## Physical transfer remains a separate unresolved milestone

Keep quarantine unchanged. Before a future physical test can be considered, obtain exact device/firmware identity, verified command and emergency-stop semantics, measured safe operating bounds and failure behavior, verified sensor decoding, and an explicit supervised test plan. A successful screen avatar or simulation is insufficient evidence. Nothing in this document authorizes connecting or powering a base, relaxing app gates, or converting autonomous avatar motion directly to hardware commands.

Meow: preserve the separation between intent, simulation, safety arbitration, and transport. The earlier brake incident demonstrates why an apparently reasonable numeric command cannot establish hardware safety. Debug future transfer issues by replaying the intent and lease sequence against a transport-free sink first; only measured device evidence can resolve the remaining protocol questions.
