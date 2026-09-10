import json
import math
import tempfile
import unittest
from pathlib import Path

from train_neural_core import (
    affine_fit,
    load_checkpoint,
    load_trace,
    predict,
    save_checkpoint,
)


class TrainerTest(unittest.TestCase):
    def test_affine_fit_beats_constant_baseline(self):
        inputs = [[float(i), 1.0] for i in range(20)]
        targets = [[2.0 * row[0] + 1.0] for row in inputs]
        weights = affine_fit(inputs, targets, ridge=1e-8)
        predictions = [predict(row, weights) for row in inputs]
        fit_error = sum((p[0] - y[0]) ** 2 for p, y in zip(predictions, targets))
        baseline = sum((y[0] - 20.0) ** 2 for y in targets)
        self.assertLess(fit_error, baseline * 0.01)

    def test_checkpoint_round_trip(self):
        checkpoint = {"version": 1, "inputLayout": ["a"], "outputLayout": ["b"], "rate": {"units": 1}}
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "checkpoint.json"
            save_checkpoint(path, checkpoint)
            self.assertEqual(checkpoint, load_checkpoint(path))

    def test_trace_loader_accepts_header_and_samples(self):
        lines = [
            {"version": 1, "inputLayout": ["a"], "outputLayout": ["b"]},
            {"tMs": 0, "input": [1.0], "legacyBias": [0.5], "neuralReadout": [0.25], "legacyDrives": [0.1]},
        ]
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "trace.jsonl"
            path.write_text("\n".join(json.dumps(line) for line in lines) + "\n")
            header, samples = load_trace(path)
            self.assertEqual(header["inputLayout"], ["a"])
            self.assertEqual(len(samples), 1)


if __name__ == "__main__":
    unittest.main()