#!/usr/bin/env python3
"""Small, dependency-free readout trainer for Meowmba neural traces.

The Android controller owns feature extraction and recurrence. This tool fits
only a bounded affine readout against recorded feature vectors, preserving the
checkpoint's recurrent core and its safety envelope. It intentionally uses the
standard library so it works on a clean developer machine without silently
installing numpy.
"""

import argparse
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


def fit_checkpoint(checkpoint, samples, ridge):
    inputs = [sample["input"] for sample in samples]
    targets = [sample["neuralReadout"] for sample in samples]
    model = affine_fit(inputs, targets, ridge=ridge)
    rate = checkpoint.get("rate")
    if not rate:
        raise ValueError("trainer currently fits rate checkpoints only")
    units = rate["units"]
    if model["outputWidth"] != len(checkpoint["outputLayout"]):
        raise ValueError("trace output width does not match checkpoint")
    if model["inputWidth"] != len(checkpoint["inputLayout"]):
        raise ValueError("trace input width does not match checkpoint")
    rate["outputWeights"] = [value for channel in model["weights"] for value in channel[:-1]]
    checkpoint["notes"] = checkpoint.get("notes", "") + " readout fitted by scripts/neural/train_neural_core.py"
    return checkpoint


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--trace", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--ridge", type=float, default=1e-3)
    args = parser.parse_args()
    checkpoint = load_checkpoint(args.checkpoint)
    _, samples = load_trace(args.trace)
    if len(samples) < 2:
        raise ValueError("at least two trace samples are required")
    save_checkpoint(args.output, fit_checkpoint(checkpoint, samples, args.ridge))


if __name__ == "__main__":
    main()