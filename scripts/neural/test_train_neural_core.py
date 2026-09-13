import json
import math
import tempfile
import unittest
from pathlib import Path

from train_neural_core import (
    affine_fit,
    validate_trace_provenance,
    fit_checkpoint,
    replay_rate_states,
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

    def fixture(self):
        return {"version": 1, "substrate": "RATE", "inputLayout": ["touch"], "outputLayout": ["attention"],
            "outputBaseline": [.2], "outputMin": [0.], "outputMax": [1.], "rate": {
            "units": 2, "tauMs": [100., 400.], "adaptTauMs": [1000., 1000.], "adaptGain": [0., .1],
            "bias": [0., 0.], "inputWeights": [1., .5], "recurrentWeights": [.2, -.1, .1, .3],
            "outputWeights": [.3, .2]}}

    def test_fit_uses_hidden_units_and_preserves_intercept(self):
        checkpoint = self.fixture()
        samples = [{"tMs": (i+1)*50, "dtMs": 50, "input": [1. if (i//12)%2 else 0.]} for i in range(200)]
        states = replay_rate_states(checkpoint, samples)
        for sample, state in zip(samples, states):
            sample["targetReadout"] = [.2 + .3 * state[0] + .2 * state[1]]
        fitted = fit_checkpoint(checkpoint, samples, 1e-6, "targetReadout")
        self.assertEqual(len(fitted["rate"]["outputWeights"]), 2)
        self.assertAlmostEqual(fitted["outputBaseline"][0], .2, places=5)
        self.assertAlmostEqual(fitted["rate"]["outputWeights"][0], .3, places=4)
        self.assertEqual(checkpoint, self.fixture())

    def test_replay_rejects_gaps_nonfinite_and_regression_bad_ridge(self):
        for sample in [{"tMs": 5000, "input": [0.]}, {"tMs": 50, "input": [float("nan")]}, {"tMs": -1, "input": [0.]}]:
            with self.assertRaises(ValueError):
                replay_rate_states(self.fixture(), [sample])
        for ridge in [0, -1, float("nan")]:
            with self.assertRaises(ValueError):
                affine_fit([[0.], [1.]], [[0.], [1.]], ridge)

    def test_external_trace_requires_recorder_reset_provenance(self):
        checkpoint = dict(self.fixture(), name="fixture")
        rows = [{"tMs": 100000+i*50, "dtMs": 50, "stepIndex": i+1, "resetGeneration": 0, "input": [0.]} for i in range(2)]
        header = {"version": 2, "startsFromReset": True, "checkpoint": "fixture", "checkpointJson": json.dumps(checkpoint),
            "inputLayout": checkpoint["inputLayout"], "outputLayout": checkpoint["outputLayout"]}
        validate_trace_provenance(header, rows, checkpoint)
        for changed in [{"version": 1}, {"startsFromReset": False}, {"startsFromReset": None}, {"checkpoint": "other"}]:
            with self.assertRaises(ValueError):
                validate_trace_provenance(dict(header, **changed), rows, checkpoint)
        with self.assertRaises(ValueError):
            validate_trace_provenance(header, [{"tMs": 50}, {"tMs": 100}], checkpoint)

    def test_external_trace_rejects_checkpoint_changes_gaps_and_resets(self):
        checkpoint = dict(self.fixture(), name="fixture")
        rows = [{"dtMs": 50, "stepIndex": i+1, "resetGeneration": 0} for i in range(2)]
        header = {"version": 2, "startsFromReset": True, "checkpoint": "fixture",
            "checkpointJson": json.dumps(checkpoint), "inputLayout": checkpoint["inputLayout"],
            "outputLayout": checkpoint["outputLayout"]}
        for value in [None, "{", json.dumps(dict(checkpoint, outputBaseline=[.3]))]:
            with self.assertRaises(ValueError):
                validate_trace_provenance(dict(header, checkpointJson=value), rows, checkpoint)
        for patch in [{"stepIndex": 3}, {"stepIndex": None}, {"stepIndex": 2.0},
                      {"resetGeneration": 1}, {"resetGeneration": None}, {"resetGeneration": False}]:
            with self.assertRaises(ValueError):
                validate_trace_provenance(header, [rows[0], dict(rows[1], **patch)], checkpoint)
        with self.assertRaises(ValueError):
            validate_trace_provenance(header, [dict(rows[0], stepIndex=True), rows[1]], checkpoint)
        validate_trace_provenance(header, rows, checkpoint)

    def test_explicit_dt_not_wall_gap_drives_replay(self):
        initial = [{"tMs": 50, "dtMs": 50, "input": [1.]}, {"tMs": 100, "dtMs": 50, "input": [0.]}]
        suspended = [dict(row, tMs=row["tMs"]+100000*(i+1)) for i,row in enumerate(initial)]
        self.assertEqual(replay_rate_states(self.fixture(), initial), replay_rate_states(self.fixture(), suspended))
        for dt in [None, 0, -1, 1001, float("nan"), True]:
            with self.assertRaises(ValueError):
                replay_rate_states(self.fixture(), [dict(initial[0], dtMs=dt)])


if __name__ == "__main__":
    unittest.main()