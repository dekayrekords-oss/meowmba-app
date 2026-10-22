# Fruit-fly motor research boundary — September 12, 2026

Historical build/release-specific note. The separate MaleCNS CC BY 4.0 release
and MIT simulator are now downloaded and tested on Meow; see the
[September 13 MaleCNS handoff](MEOW_MALECNS_MODEL_PASS_DOWN_2026-09-13.md).
The FAFB v783 terms below must not be generalized to all fruit-fly data. Build
111 also includes the separate experimental BANC adapter described in its handoff.

## What this build can accurately claim

The full-body character is an original stylized rig. Its humanoid arms, grasping gestures, mouth expressions, and on-screen flight need authored kinematics and contact constraints. Neither the shipped rate network nor its seeded spiking alternative contains measured FlyWire connections. Their role is bounded attention, drives, and action proposals. Adding neurons or calling connections “synapses” does not establish biological provenance or improve movement.

The current motor implementation should receive high-level neural proposals (orient, approach, startle, inspect, rest), map those to a bounded animation controller, and return completion/contact feedback. Retain an independent deterministic layer for scene bounds, grasp/release, interruption, dropped-frame time limits, and thermal suspension. Humanlike posing and biological gait are separate animation targets.

## Primary sources checked

1. [FlyWire public-release guidelines](https://home.flywire.ai/guidelines), accessed 2026-09-12. The page identifies v783 and CC BY-NC 4.0 terms. No FlyWire dataset or derivative weights were downloaded into this commercial app checkout. A code repository's permissive license would not replace separate data conditions. This is a provenance finding, not a claim to commercial rights.
2. [Dorkenwald et al., Neuronal wiring diagram of an adult brain (2024)](https://www.nature.com/articles/s41586-024-07558-y). The brain connectome supplies anatomical wiring; it is not, by itself, a complete calibrated muscle/controller/physics model.
3. [Schlegel et al., Whole-brain annotation and multi-connectome cell typing (2024)](https://doi.org/10.1038/s41586-024-07686-5). Cell annotations help choose named populations. Brain connectivity alone is insufficient to reproduce the ventral nerve cord's body control. Do not invent correspondence between an arbitrary reservoir unit and an identified fly neuron.
4. [NeuroMechFly official documentation](https://neuromechfly.org/). Provides a body/sensory simulation, hierarchical brain-to-VNC control, joint/contact feedback and walking-controller tutorials. Its April 2026 API update is incompatible with older 1.x examples. [NeuroMechFly v2 paper](https://www.nature.com/articles/s41592-024-02497-y) gives the published embodied-control context. Pin a version before experiments.
5. [TuragaLab FlyBody repository](https://github.com/TuragaLab/flybody). A dedicated MuJoCo body model and locomotion RL tasks is the appropriate separate flight/walking research direction; it is not the Android renderer or a drop-in connectome. No training jobs, package installs, or model redistribution were performed here.

## Concrete next research experiment

Run in a separate noncommercial research workspace, not APK assets. Select an identifiable small sensory-to-descending circuit only after confirming the exact release's terms. Save source URL, dataset version, neuron IDs, edge counts, synapse-count threshold, extraction command, checksum, and license alongside the raw subset. Keep anatomical counts separate from assumed efficacy/sign/delay and dynamics parameters. Do not merge data from distinct specimens as if the cells were directly connected without a documented correspondence.

Compare that subset against (a) size- and edge-count-matched shuffled connectivity and (b) a small trained rate network, on the same held-out turn/approach/avoid traces. Freeze targets, splits, dt, parameter budget and latency/energy gates first. Fit only in the training split. Measure control tracking error, stability, clipping/contact failures, response delay and CPU cost. Test ablations so any improvement is causally tied to the circuit. Add whole-body physics only after this small control experiment is reproducible.

For the humanoid character, retain an explicitly authored retargeting layer; no observed fly circuit supplies a human hand grasping a book. For actual aerodynamic flight, use the body's inertia, wing kinematics, force model and control feedback in a simulation. A screen-space path and flapping wings are an animated flight illusion, which is the current product's scope.

No biological comparator has been run in this change. It would be misleading to invent a small circuit, label random connections “real fruit-fly synapses,” or imply a commercial deployment has passed because a simulated avatar moves.
