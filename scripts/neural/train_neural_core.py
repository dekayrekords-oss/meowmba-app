#!/usr/bin/env python3
"""Small, dependency-free readout trainer for Meowmba neural traces.

The Android controller owns feature extraction and recurrence. This tool fits
only a bounded affine readout against reconstructed recurrent states, preserving the
checkpoint's recurrent core and its safety envelope. It intentionally uses the
standard library so it works on a clean developer machine without silently
installing numpy.
"""

import argparse
import copy
import json
import math
from pathlib import Path


def load_checkpoint(path):
    return json.loads(Path(path).read_text())


def save_checkpoint(path, checkpoint):
    Path(path).write_text(json.dumps(checkpoint, separators=(",", ":")) + "\n")


def load_trace(path):
    lines = [json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()]
    if not lines:
        raise ValueError("empty trace")
    return lines[0], lines[1:]


def _transpose(matrix):
    return [list(column) for column in zip(*matrix)]


def _solve(matrix, vector):
    n = len(vector)
    augmented = [list(matrix[i]) + [vector[i]] for i in range(n)]
    for column in range(n):
        pivot = max(range(column, n), key=lambda row: abs(augmented[row][column]))
        if abs(augmented[pivot][column]) < 1e-12:
            raise ValueError("singular regression matrix")
        augmented[column], augmented[pivot] = augmented[pivot], augmented[column]
        scale = augmented[column][column]
        augmented[column] = [value / scale for value in augmented[column]]
        for row in range(n):
            if row == column:
                continue
            factor = augmented[row][column]
            if factor:
                augmented[row] = [
                    left - factor * right
                    for left, right in zip(augmented[row], augmented[column])
                ]
    return [augmented[i][-1] for i in range(n)]


def affine_fit(inputs, targets, ridge=1e-3):
    if len(inputs) != len(targets) or not inputs:
        raise ValueError("inputs and targets must be non-empty and have equal length")
    if not math.isfinite(ridge) or ridge <= 0:
        raise ValueError("ridge must be finite and positive")
    if any(not math.isfinite(v) for row in inputs + targets for v in row):
        raise ValueError("regression values must be finite")
    width = len(inputs[0])
    output_width = len(targets[0])
    if any(len(row) != width for row in inputs):
        raise ValueError("input rows have different widths")
    if any(len(row) != output_width for row in targets):
        raise ValueError("target rows have different widths")
    design = [list(row) + [1.0] for row in inputs]
    gram = [[0.0 for _ in range(width + 1)] for _ in range(width + 1)]
    for row in design:
        for i, left in enumerate(row):
            for j, right in enumerate(row):
                gram[i][j] += left * right
    for i in range(width):
        gram[i][i] += ridge
    transposed_targets = _transpose(targets)
    coefficients = [
        _solve(gram, [sum(row[i] * value for row, value in zip(design, target)) for i in range(width + 1)])
        for target in transposed_targets
    ]
    return {"weights": coefficients, "inputWidth": width, "outputWidth": output_width}


def predict(row, model):
    augmented = list(row) + [1.0]
    return [sum(weight * value for weight, value in zip(channel, augmented)) for channel in model["weights"]]


def replay_rate_states(checkpoint, samples):
    """Reconstruct hidden states from reset using the Kotlin rate equations.

    A trace must begin at a known reset (t=0 is allowed as an observation).
    Double precision is used offline; Kotlin uses Float, so compare tolerances.
    """
    r = checkpoint.get("rate")
    if not r or checkpoint.get("substrate") != "RATE":
        raise ValueError("trainer currently fits rate checkpoints only")
    n, width = r["units"], len(checkpoint["inputLayout"])
    h, adapt, previous = [0.0] * n, [0.0] * n, 0.0
    states = []
    for sample in samples:
        x, now = sample["input"], sample["tMs"]
        if len(x) != width or not all(math.isfinite(v) for v in x):
            raise ValueError("invalid input features")
        if not math.isfinite(now) or now < previous or now - previous > 1000:
            raise ValueError("trace must be ordered and start at reset, with gaps <= 1000ms")
        dt = now - previous
        previous = now
        steps = max(1, math.ceil(dt / (min(r["tauMs"]) * .25)))
        sub = dt / steps
        for _ in range(steps):
            drive = [r["bias"][i] - adapt[i]
                + sum(r["inputWeights"][i * width + j] * x[j] for j in range(width))
                + sum(r["recurrentWeights"][i * n + j] * h[j] for j in range(n)) for i in range(n)]
            h = [max(-1., min(1., h[i] + sub / r["tauMs"][i] * (math.tanh(drive[i]) - h[i]))) for i in range(n)]
            adapt = [adapt[i] + sub / r["adaptTauMs"][i] * (r["adaptGain"][i] * h[i] - adapt[i]) for i in range(n)]
        states.append(h[:])
    return states


def fit_checkpoint(checkpoint, samples, ridge, target_field="neuralReadout"):
    checkpoint = copy.deepcopy(checkpoint)
    inputs = replay_rate_states(checkpoint, samples)
    targets = [sample[target_field] for sample in samples]
    if any(len(row) != len(checkpoint["outputLayout"]) or not all(math.isfinite(v) for v in row) for row in targets):
        raise ValueError("invalid trace targets")
    model = affine_fit(inputs, targets, ridge=ridge)
    weights = [value for channel in model["weights"] for value in channel[:-1]]
    baseline = [channel[-1] for channel in model["weights"]]
    if any(not math.isfinite(v) or abs(v) > 32 for v in weights):
        raise ValueError("fitted readout exceeds Kotlin output weight bound; increase ridge")
    if any(not low <= b <= high for b, low, high in zip(baseline, checkpoint["outputMin"], checkpoint["outputMax"])):
        raise ValueError("fitted baseline outside checkpoint bounds")
    checkpoint["rate"]["outputWeights"] = weights
    checkpoint["outputBaseline"] = baseline
    checkpoint["notes"] = checkpoint.get("notes", "") + " hidden-state readout fitted offline; target=" + target_field
    return checkpoint


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--trace", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--ridge", type=float, default=1e-3)
    parser.add_argument("--target-field", choices=["neuralReadout", "targetReadout"], default="neuralReadout")
    parser.add_argument("--from-reset", action="store_true", help="confirm the trace starts at core reset; required")
    args = parser.parse_args()
    if not args.from_reset:
        parser.error("--from-reset is required; mid-session traces cannot reconstruct hidden state")
    checkpoint = load_checkpoint(args.checkpoint)
    header, samples = load_trace(args.trace)
    if header.get("inputLayout") != checkpoint["inputLayout"] or header.get("outputLayout") != checkpoint["outputLayout"]:
        raise ValueError("trace layout names/order do not match checkpoint")
    if len(samples) < 2:
        raise ValueError("at least two trace samples are required")
    save_checkpoint(args.output, fit_checkpoint(checkpoint, samples, args.ridge, args.target_field))


if __name__ == "__main__":
    main()