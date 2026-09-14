# MaleCNS open model — runnable research handoff

The user explicitly authorized the open-source fruit-fly brain models and linked
the [Minecraft discussion](https://www.reddit.com/r/Minecraft/comments/1wcg3cy/google_just_opensourced_a_complete_fruit_fly/).
This pass verified the applicable release, downloaded a runnable implementation
onto Meow and the Mac, and tested real anatomical connectivity. **The phone
remains Build 111. MaleCNS is not yet an Android substrate or a running service.**

## What is available and why this changes the earlier boundary

[Google's announcement](https://research.google/blog/a-connectomics-milestone-mapping-the-complete-male-fruit-fly-brain/)
describes the male CNS reconstruction. The originating
[Janelia dataset page](https://www.janelia.org/project-team/flyem/male-cns-connectome)
links its data license to [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
It covers the brain and ventral nerve cord. This is **MaleCNS**, distinct from
the FAFB/FlyWire v783 data and the BANC vignette used in Build 110/111. Do not
carry the older FAFB noncommercial finding over to this release. Attribution
and recording modifications are required; there is no blanket licensing block
on using this MaleCNS release in the app.

The runnable [Blendi Remade / fal.ai implementation](https://github.com/blendi-remade/fly-brain-minecraft)
has an [MIT code license](https://github.com/blendi-remade/fly-brain-minecraft/blob/6cfa30175003ef25da68a237d5eda958f8047b82/LICENSE)
and a [data provenance record](https://github.com/blendi-remade/fly-brain-minecraft/blob/6cfa30175003ef25da68a237d5eda958f8047b82/PROVENANCE.md).
It is a third-party simulator using the Google/Janelia/Cambridge reconstruction;
Google did not supply the Minecraft behavior controller. The audited revision
is `6cfa30175003ef25da68a237d5eda958f8047b82`.

The downloaded derivative contains **176,422 neuron records, 6,287,749 directed
pairs and 90,296,905 anatomical synapse counts**. It keeps pairs with at least
five synapses and removes autapses. It is not all 125 million source synapses.
No neurons were grouped by our probe. The 22,964,094-byte compressed artifact
matches SHA-256 `e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3`.
We verified that identity, sorted IDs, CSR bounds, endpoints and counts. We did
not independently repeat the original neuPrint extraction.

Data attribution: FlyEM Project Team, HHMI Janelia Research Campus; Drosophila
Connectomics Group, University of Cambridge / MRC LMB; Google Research
Connectomics. Berg et al., *Sexual dimorphism in the complete Drosophila male
central nervous system connectome*, Cell (2026),
[DOI 10.1016/j.cell.2026.08.015](https://doi.org/10.1016/j.cell.2026.08.015).
The derivative's upstream transformations include thresholding, autapse removal,
reindexing, transmitter-sign assignments and retinal column assignment. Our
normal runs leave that data unchanged; explicitly labeled ablations zero its
weights only in memory as a negative control.

## What changed and how to reproduce it

`scripts/neural/evaluate_malecns.py` verifies the pinned code and asset hashes,
copies four pure Java model classes to an output directory, compiles them with
`MaleCnsProbe.java`, and runs short deterministic sensory episodes. It does not
run the external Gradle build, launch Minecraft, modify the upstream checkout,
download at runtime or transmit phone inputs. No app dependencies were added.
Each Java process has one worker/active processor, a 512 MiB heap ceiling and a
120-second timeout. The graph stays outside the APK checkout. Generated source
copies include upstream's full `UPSTREAM_LICENSE.txt` (MIT, copyright 2026
Blendi Remade / fal.ai); preserve that notice if moving them.

Meow's downloaded source is `/home/prog/research/malecns-2026-09-13`.
Scripts are also available at `/home/prog/research/evaluate_malecns.py` and
`/home/prog/research/MaleCnsProbe.java`. Completed generated classes, logs and
report are in `/home/prog/research/malecns-probe-01`. No permanent process is
left running. To reproduce, choose a new output directory:

```sh
python3 /home/prog/research/evaluate_malecns.py \
  --source /home/prog/research/malecns-2026-09-13 \
  --output-dir /home/prog/research/malecns-probe-rerun \
  --jdk /home/claude/.local-toolchain/jdk-21.0.12+8
```

The Mac clone is `/tmp/meow-malecns-audit-2026-09-13`; its successful run is
`/tmp/meow-malecns-mac-probe-04`, using JBR 17.0.14. Temporary paths may expire;
the pinned public clone can be recreated. The tracked Mac and Meow JSON reports
preserve source hashes, Java versions, configuration, per-case state hashes,
population rates, timing and limitations. The independent probe is original
Meow code; four small replacements to upstream `LifNetwork` are explicit in
the runner's `replay_patch`, and both variant hashes are recorded.

## Two issues reproduced and solved before reuse

1. **Observing spikes changed the simulation.** The upstream reservoir sampler
   and Poisson sensory drive consume the same random generator. On the grooming
   episode, enabling the 8,192-entry spike log changed total spikes from 161,977
   to 308,698, with the same sensory seed. A brain info panel must not have this
   effect. The corrected variant gives the reservoir an independent seeded RNG.
   Both logging states now produce 161,977 spikes and identical per-neuron
   membrane/spike-count hashes across all observation ticks.
2. **Reset did not restart the random sequence.** Upstream reset cleared neural
   state but retained the advanced RNG. The corrected variant reseeds sensory
   and observer RNGs from the recorded configuration. Replaying the sugar
   episode after reset now reproduces the complete observed neuron-state hash.

Both problems failed in the untouched source and passed in the corrected copy
on both machines. All nine corrected checks pass: quiet baseline, reset replay,
observer independence, sugar response, bitter suppression, grooming response,
looming response and two edge-ablation controls. Zeroing connectivity removed
the proboscis/jump motor responses while directly stimulated sensory cells
continued firing. This supports a connectivity-dependent simulated response;
it does not establish unique biological correctness of every edge or parameter.

The harness rejects absent populations. Our first attempt used exact `DNg02`,
which is absent; the source groups wing-power subtypes using `prefix:DNg02`.
Use the annotation resolver and verify nonzero population sizes instead of
silently converting a missing biological label into a zero readout.

Mac javac initially interpreted source as US-ASCII under the shell locale.
Passing explicit `-encoding UTF-8` fixed compilation without changing upstream
text. The Mac Python installation also lacked a trusted HTTPS certificate chain;
Git's working TLS transport was used to fetch source, without disabling TLS
verification. The reproducible evaluator itself has no network dependency.

## What the measurements establish

Every recorded neuron-state hash matches between ARM64 Mac/JDK 17 and x86_64
Meow/JDK 21 for the same variant and episode. Each episode is **600 ms of
simulated time**, not a long endurance run. The corrected Meow single-worker
run produced these means and timings (p95 over twelve 50 ms ticks, including
cold JIT and tick observation/reset; hashing excluded):

| Episode | Motor population response | Compute p95 / 50 ms tick |
| --- | --- | ---: |
| Sugar | MN9 73.33 Hz | 64.52 ms |
| Sugar + bitter | MN9 0 Hz | 120.70 ms |
| Grooming | DNg62 180 Hz; DNge078 129.17 Hz | 85.54 ms |
| Looming | DNp01 340.83 Hz; TTMn 70 Hz | 54.04 ms |

These short compute samples are already over a 50 ms tick budget in some
conditions. They are not phone measurements and do not justify enabling this
model in Android's render loop. Timing excludes model load and full-state hash
calculation. Source-derived connectivity does not remove the need to profile
the actual deployment. The existing phone thermal suspension stays in place.

## Next integration work for Meow

Use this tested model as a candidate additional substrate; do not describe the
installed seven-population BANC adapter as this graph. Keep RATE, SPIKING and
BANC options and their saved preferences. Start the full model in a bounded
worker on Meow, with an explicit simulated clock, structured sensory features,
timestamped output snapshots, backpressure and stale-output fallback to the
existing local controller. Reuse the established authenticated private bridge
if connecting the phone; do not create an unauthenticated public brain service.
Do not send camera frames, microphones or transcripts for these abstract events.

Connect food/touch/loom/groom events to named inputs and measured motor readouts
before exposing the full-model option. Gate network behavior on enabled mode
and visibility, and stop idle or stale work. Map motor proposals through existing
avatar arbitration, care rules, scene bounds, privacy and thermal limits. The
brain info panel must use the isolated observer RNG and bounded snapshots.
This graph has 90.3 million retained counts, so it must be labeled as a retained
MaleCNS model; do not present it as all original synapses.

Uniform point-neuron dynamics, transmitter sign heuristics, gain, delays,
sensory drive and avatar motor decoding remain assumptions. Our looming test
stimulates LC4/LPLC2 directly. The upstream project documents incomplete retinal
propagation and odor saturation; its flight state machine is authored. This
pass does not verify biological flight, feeling/consciousness, learned care
preferences, sustained phone heat/battery behavior or robot transfer. Physical
robot controls remain quarantined, and the user has no robot to validate.

All additions are research scripts/reports/continuity docs. They are recorded in
local Git and the owner-authorized private Meow repository only. No Android
source or research derivative was published publicly.
