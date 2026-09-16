# Meow: expanding the audited MaleCNS graph

The offline converter in `scripts/neural/expand_malecns_connectome.py` adds
official weak connections and autapses to the existing annotated neuron roster.
It does not add invented neurons, change the simulator, install an Android
build, replace the active worker, or establish phone pairing.

## Why this converter exists

The previously audited FLYB retains 176,422 neurons, 6,287,749 neuron pairs and
90,296,905 synapse counts. Its builder discarded pairs below five synapses and
removed autapses. Restoring official positive weights between the same known
neurons expands connectivity while preserving every annotation and existing
body-ID-based input/output mapping. A more connected graph still does not
constitute a validated physiological model of the entire animal.

## Exact inputs and preserved content

The CLI pins both input identities, without an override flag:

- Existing compressed FLYB SHA-256:
  `e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3`.
- [Official MaleCNS v1.0 confidence-0.5 weighted Feather](https://storage.googleapis.com/flyem-male-cns/v1.0/connectome-data/flat-connectome/connectome-weights-male-cns-v1.0-minconf-0.5.feather)
  MD5: `f30e9dcca25cfd021bf1e7b3d975599e`; expected object size observed by the
  integrator: 1,051,241,946 bytes.
- Original sorted int64 body-ID byte SHA-256:
  `a21dcf2576c352eda0f4d455683b648ec28272fd7f4918fdfbf2c97aff9df465`.

The original ten string tables, neuron annotations, neurotransmitter signs,
soma arrays, neuron synapse annotations and 9,619 retina entries are copied
byte for byte. The dataset label stays `male-cns:v1.0`. Metadata describes the
expanded edge set and retains the original annotation build date separately.
The report records separate roster, annotation and retina hashes, both source
hashes, output identity, count accounting and limitations.

## How it works

1. Check pinned compressed-input hashes before parsing or writing output.
2. Validate the original FLYB layout, dimensions, ordered unique body IDs,
   annotation indices, CSR ordering, positive weights and retina indices.
3. Read the Feather with PyArrow IPC memory mapping and selected integer
   columns. The actual official schema is `body_pre`, `body_post`, `weight`;
   its observed first record batch has 65,536 rows. Explicit known aliases
   are supported; ambiguous or unknown schemas are rejected.
4. Map endpoints with NumPy `searchsorted`, retaining only positive rows
   whose two endpoints exist in the original roster. Report all outside-roster
   rows and their synapse totals. Do not synthesize missing annotations.
5. Partition by presynaptic index on disk. Sort at most 32 MiB of edge records
   at once; recursively split denser partitions on disk. Input conversion uses
   chunks of at most 262,144 rows. This avoids a full pandas table or global
   in-memory sort. Final memory use must still be measured on the full run.
6. Sum duplicate neuron pairs, even when split across source batches. Retain
   autapses and pairs of weight one through four. Reject counts above 65,535
   instead of silently clipping the FLYB1 unsigned-16-bit weight field.
7. Compare every original pair against the expanded CSR. Missing pairs or
   decreasing weights fail conversion, with discrepancy counters in the error.
   Increasing weights are accepted but explicitly counted, with total added
   counts and bounded body-ID samples in the report.
8. Write deterministic sorted CSR and gzip (`mtime=0`, no embedded filename).
   New output and report paths are required; existing assets are never
   overwritten. Temporary files are removed on success and exceptions.

## Reproduction

Use the isolated Python environment prepared by the integrator on Meow:
`/home/prog/research/malecns-full-2026-09-13/venv/bin/python`.
It contains PyArrow 25.0.1 and NumPy 2.3.5. Python 3.14 did not have the attempted
PyArrow 19.0.1 wheel, so that failed dependency install was replaced with the
available wheel in this isolated environment; production dependencies were
not changed.

```sh
python -m unittest discover -s scripts/neural -p test_expand_malecns_connectome.py -v
python scripts/neural/expand_malecns_connectome.py \
  --base /absolute/path/to/malecns-v1.0.flyb.gz \
  --weights /absolute/path/to/connectome-weights.feather \
  --output /absolute/path/to/malecns-v1.0-expanded.flyb.gz \
  --report /absolute/path/to/expansion-report.json
```

Fourteen synthetic tests cover weak edges, autapses, duplicate aggregation,
source/base identity checks, outside-roster accounting, preservation, corrupted
binary bounds, fractional/negative data, overflow rejection, reproducibility
and disk splitting. The initial local run passed 13 tests; the Arrow reader
test was skipped because the Mac's Python lacks PyArrow. The actual pinned
base also parsed locally and matched the counts and roster hash above.
The integration passdown/report must record the server Arrow test, complete
conversion counts and observed resource use before claiming the expanded
artifact is verified.

## Troubleshooting and remaining limits

- Hash rejection means a different or incomplete source file. Do not remove
  the check to make a download pass; inspect the official object identity.
- A missing/decreased base pair means the new source is incompatible with the
  promised subset property. Investigate confidence threshold, data version or
  source aggregation. Do not automatically fall back to an old weight or add
  it twice.
- Integer overflow means FLYB1 needs a reviewed format/runtime change. Raising
  a threshold or clipping counts would quietly lose the data this job restores.
- This preserves existing sign assumptions and retina projection. It does not
  validate neuromodulation, dendritic morphology, biological synaptic delays,
  plasticity, muscle physiology or whole-animal flight.
- The expanded file is an offline candidate until the integrator validates
  runtime identity, dynamics, performance and request-driven worker behavior.
  Phone installation and actual phone-to-Meow pairing remain separate checks.
