# Cozmo repair — 179 / 1.5.13

## Evidence and scope

The owner reports that Wi-Fi connects but head, lift and base movement do not
work, and enabling the preview crashes the app. The Fold still runs177.
Read-only crash-buffer inspection on September25 found two main-thread crashes
(14:44 and17:08): WakeWordService.onStartCommand read `secureStorage` before
initialization. This is a confirmed app-wide crash, not proof that JPEG decoding
caused either crash. No robot socket or motor was operated from this task.

## Changes

- Follow-up listening now checks foreground startup, stop races, storage readiness
  and microphone privacy before reading quiet-mode settings.
- Camera JPEG decoding runs on a separate bounded, latest-frame worker. It checks
  320×240 dimensions before allocating pixels and discards malformed frames.
  Decoding cannot stall the UDP worker's telemetry, ACKs or stop deadlines.
  Old-generation/off-camera results are discarded; no images are stored/uploaded.
- Continuous silence animation ticks at approximately30Hz, rather than once per
  face update; faster ACK pings; half-second initialization grace, as in the
  reference client. These correct reference discrepancies, but are not proven
  hardware root causes for the owner's motor failure.
- A command channel with no acknowledgments for2seconds disconnects with a clear
  error even if sensor packets continue. Worker-side expired/blocked actions show
  an explanation rather than silently disappearing.
- Full documented head/lift endpoint presets, middle lift, curved wheel pulses,
  and an explicit head/lift calibration button. Calibration is user-triggered,
  not automatic. The robot must be on a clear floor with fingers clear.
- Six original finite head/lift gestures: nod, greet, celebrate, peek, sleep and
  wake. No wheels in these gestures. Stop/manual motor commands cancel remaining
  gesture steps. Stale/held/falling/cliff/charger conditions cancel sequences.
- Optional pickup/charger/fall/cube face reactions; these never start motors.
- Cozmo/LOOI ownership and separation remain intact. New addressed gesture/curve/
  half-lift voice routes are Cozmo-only. Calibration has an explicit screen button
  and is not advertised in the agent's action list; the controller still validates
  its sensor guards if an explicitly addressed tool request supplies that action.

## Limits / acceptance

This is not official-app parity. Anki games, its animation/audio catalog, face
recognition, cube stacking, navigation and charger seeking remain unimplemented.
No proprietary app assets were downloaded. The six new gestures are original.
Meow still hears/speaks through the phone. Wheel pulses remain limited to350ms
and require the floor-move switch; this is a phone deadline, not a hardware stop
guarantee. Radio/phone failure can prevent stop delivery.

Hardware movement and real preview acceptance are outstanding. A safe owner test
must verify head/lift calibration, all presets, short floor pulses, Stop, and
camera-on motion/link stability. Do not claim the reported motor issue resolved
until that succeeds. This work does not authorize a new phone install.

Reference: [PyCozmo at the pinned revision](https://github.com/zayfod/pycozmo/tree/1b6dcd9b869a3784f1d8b02e820bb033f95fd13a), especially
`client.py`, `anim_controller.py`, `protocol_declaration.py` and `frame.py`.

Validation:75 focused JVM tests pass, zero failures/errors/skips; two emulator UI
checks pass in11.394s. Daily/debug/test packaging and daily lint pass (zero errors,
200warnings,12hints). Matching preview certificate,16KB alignment and runtime
comparison with178 pass; only generated ART profiles differ. Optimized emulator
COLD launch1822ms; PID3311 foreground/alive73seconds, no new crash-buffer entries.
This is a targeted regression run, not a new full-suite run. Emulator stopped after
testing with Settings foreground; its data/home defaults retained.

APK/receipt: Os `work/cozmo-repair-179/README.md`. APK SHA256:
`8c8c7488ff45847e3f9623da28ec784e336f9592da34338e7fd5e2625824eb81`.
179 is consumed;178 remains immutable; next unallocated180. No phone installation,
physical robot commands, new permission grants, account logins or public source push.
