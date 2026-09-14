"""Small, original fixtures; no research dataset is copied into the repository."""
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

import numpy as np

from expand_malecns_connectome import (BASE_SHA256, WEIGHTS_MD5, expand, feather_batches,
                                       parse_base, RECORD, sorted_partitions)


def fixture_bytes(edges=((0, 1, 5), (1, 2, 7)), body_ids=(10, 20, 30)):
    n = len(body_ids)
    metadata = json.dumps({"dataset": "synthetic-test", "built": "fixture", "min_weight": 5}).encode()
    data = b"FLYB" + struct.pack("<IIII", 1, n, len(edges), 1)
    data += struct.pack("<H", 14) + b"synthetic-test" + struct.pack("<I", len(metadata)) + metadata
    data += (struct.pack("<HH", 1, 0)) * 10
    data += np.asarray(body_ids, dtype="<i8").tobytes()
    data += np.zeros(n, dtype="<i4").tobytes()  # type
    data += bytes(n * 2) + bytes(n * 2)  # superclass, class, subclass
    data += bytes(n) + np.ones(n, dtype="i1").tobytes() + bytes(n)  # nt, signs, side
    data += b"\xff" * (n * 2) + bytes(n * 4)  # hex coordinates and final four annotations
    data += np.full(n * 3, np.nan, dtype="<f4").tobytes()
    data += np.zeros(n * 2, dtype="<i4").tobytes()
    counts = np.bincount([pre for pre, _, _ in edges], minlength=n)
    data += np.r_[0, np.cumsum(counts)].astype("<i4").tobytes()
    data += np.asarray([post for _, post, _ in edges], dtype="<i4").tobytes()
    data += np.asarray([weight for _, _, weight in edges], dtype="<u2").tobytes()
    data += struct.pack("<iBbbB", 0, 0, 3, 4, 0)
    return data


class ExpansionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.base = self.directory / "base.flyb.gz"
        self.base.write_bytes(gzip.compress(fixture_bytes(), mtime=0))
        self.weights = self.directory / "weights.fixture"
        self.weights.write_bytes(b"synthetic source identity; rows supplied by test")
        self.output = self.directory / "expanded.flyb.gz"
        self.report = self.directory / "report.json"
        self.rows = [(10, 20, 2), (10, 20, 3), (20, 30, 8), (10, 10, 2),
                     (30, 10, 1), (50, 10, 4), (20, 30, 0)]

    def run_expansion(self, rows=None, **kwargs):
        values = np.asarray(self.rows if rows is None else rows, dtype=np.int64).reshape(-1, 3)
        def batches(_):
            # A duplicate pair crosses the batch boundary deliberately.
            yield tuple(values[:1, index] for index in range(3))
            yield tuple(values[1:, index] for index in range(3))
        defaults = dict(expected_base_sha256=hashlib.sha256(self.base.read_bytes()).hexdigest(),
                        expected_weights_md5=hashlib.md5(self.weights.read_bytes()).hexdigest(),
                        batch_factory=batches)
        defaults.update(kwargs)
        return expand(self.base, self.weights, self.output, self.report, **defaults)

    def test_weak_autapse_and_duplicate_pairs_are_exactly_counted(self):
        result = self.run_expansion()
        graph = parse_base(gzip.decompress(self.output.read_bytes()))
        self.assertEqual(graph.row_ptr.tolist(), [0, 2, 3, 4])
        self.assertEqual(graph.post.tolist(), [0, 1, 2, 0])
        self.assertEqual(graph.weight.tolist(), [2, 5, 8, 1])
        self.assertEqual(result["source_rows"], 7)
        self.assertEqual(result["outside_roster_rows"], 1)
        self.assertEqual(result["outside_roster_synapses"], 4)
        self.assertEqual(result["zero_weight_rows"], 1)
        self.assertEqual(result["duplicate_source_rows_aggregated"], 1)
        self.assertEqual(result["synapses"], 16)
        self.assertEqual(result["weak_edges_1_to_4"], 2)
        self.assertEqual(result["weak_synapses_1_to_4"], 3)
        self.assertEqual(result["autapse_edges"], 1)
        self.assertEqual(result["autapse_synapses"], 2)
        self.assertEqual(result["base_weights_increased"], 1)
        self.assertEqual(result["base_added_synapses_on_existing_pairs"], 1)
        self.assertEqual(result["base_weight_change_samples"],
                         [{"pre": 20, "post": 30, "base_weight": 7, "official_weight": 8}])
        self.assertEqual(result, json.loads(self.report.read_text()))

    def test_annotations_roster_and_retina_are_byte_identical(self):
        result = self.run_expansion()
        before = parse_base(gzip.decompress(self.base.read_bytes()))
        after = parse_base(gzip.decompress(self.output.read_bytes()))
        self.assertEqual(before.tables_and_neurons, after.tables_and_neurons)
        self.assertEqual(before.retina, after.retina)
        self.assertEqual(result["roster_sha256"], hashlib.sha256(before.body_ids.tobytes()).hexdigest())
        self.assertEqual(after.metadata["min_weight"], 1)
        self.assertEqual(after.metadata["edges"], 4)
        self.assertEqual(after.metadata["synapses_in_edges"], 16)
        self.assertNotIn("built", after.metadata)
        self.assertEqual(after.metadata["annotation_source_built"], "fixture")

    def test_output_and_report_are_reproducible_across_paths(self):
        report = self.run_expansion()
        first = self.output.read_bytes()
        self.output = self.directory / "other-name.flyb.gz"
        self.report = self.directory / "other-report.json"
        self.assertEqual(report, self.run_expansion())
        self.assertEqual(first, self.output.read_bytes())
        self.assertEqual(first[4:8], b"\0" * 4)  # gzip MTIME

    def test_out_of_roster_pairs_are_not_fabricated(self):
        result = self.run_expansion(self.rows + [(0, 20, 9), (30, 999, 3), (999, 999, 1)])
        self.assertEqual(result["neurons"], 3)
        self.assertEqual(result["outside_roster_rows"], 4)
        self.assertEqual(result["outside_roster_synapses"], 17)
        self.assertEqual(result["synapses"], 16)

    def test_missing_or_decreased_base_pair_fails_without_output(self):
        for rows, expected in (([(10, 20, 5)], '"base_pairs_missing": 1'),
                               ([(10, 20, 4), (20, 30, 7)], '"base_weights_decreased": 1')):
            with self.subTest(rows=rows), self.assertRaisesRegex(ValueError, expected):
                self.run_expansion(rows)
            self.assertFalse(self.output.exists())
            self.assertFalse(self.report.exists())

    def test_individual_and_aggregated_overflow_are_never_clipped(self):
        for rows in (self.rows + [(10, 10, 65536)], self.rows + [(10, 10, 65534)]):
            with self.subTest(rows=rows), self.assertRaisesRegex(ValueError, "refusing clipping"):
                self.run_expansion(rows)
            self.assertFalse(self.output.exists())

    def test_negative_endpoints_or_weights_reject_even_outside_roster(self):
        for row in ((-1, 20, 1), (10, -1, 1), (999, 999, -1)):
            with self.subTest(row=row), self.assertRaisesRegex(ValueError, "nonnegative"):
                self.run_expansion(self.rows + [row])

    def test_float_ids_and_weights_reject_without_rounding(self):
        for column in range(3):
            arrays = [np.asarray([10]), np.asarray([20]), np.asarray([5])]
            arrays[column] = arrays[column].astype(float)
            with self.subTest(column=column), self.assertRaisesRegex(ValueError, "integer values"):
                self.run_expansion(batch_factory=lambda _: iter([tuple(arrays)]))

    def test_unpinned_inputs_reject_before_parsing_or_output(self):
        for settings in ({"expected_base_sha256": BASE_SHA256}, {"expected_weights_md5": WEIGHTS_MD5}):
            with self.subTest(settings=settings), self.assertRaisesRegex(ValueError, "pinned"):
                self.run_expansion(**settings)
        self.assertFalse(self.output.exists())

    def test_input_output_alias_and_existing_output_are_protected(self):
        self.output = self.base
        with self.assertRaisesRegex(ValueError, "distinct"):
            self.run_expansion()
        self.output = self.directory / "existing"
        self.output.write_bytes(b"keep")
        with self.assertRaises(FileExistsError):
            self.run_expansion()
        self.assertEqual(self.output.read_bytes(), b"keep")

    def test_disk_split_matches_normal_sort_and_aggregates_cross_split_duplicates(self):
        expected = self.run_expansion()
        self.output = self.directory / "small-sort.flyb.gz"
        self.report = self.directory / "small-sort-report.json"
        actual = self.run_expansion(sort_bytes=RECORD.itemsize)
        self.assertEqual(actual["synapses"], expected["synapses"])
        self.assertEqual(actual["edges"], expected["edges"])
        self.assertEqual(parse_base(gzip.decompress(self.output.read_bytes())).weight.tolist(), [2, 5, 8, 1])

    def test_binary_bounds_bad_indices_duplicates_and_trailing_bytes_reject(self):
        valid = fixture_bytes()
        for malformed in (valid[:19], valid[:-1], valid + b"x", b"NOPE" + valid[4:],
                          fixture_bytes(body_ids=(20, 10, 30)),
                          fixture_bytes(edges=((0, 1, 5), (0, 1, 7))),
                          fixture_bytes(edges=((0, 3, 5),)), fixture_bytes(edges=((0, 1, 0),))):
            with self.subTest(size=len(malformed)), self.assertRaises(ValueError):
                parse_base(malformed)

    def test_truncated_disk_records_reject(self):
        path = self.directory / "truncated.bin"
        path.write_bytes(b"short")
        with self.assertRaisesRegex(ValueError, "Truncated"):
            list(sorted_partitions(path, 0, 9))

    @unittest.skipUnless(importlib.util.find_spec("pyarrow"), "pyarrow not installed; run on converter host")
    def test_feather_batches_reject_null_float_and_unknown_schema(self):
        import pyarrow as pa
        import pyarrow.feather as feather
        for columns in ({"bodyId_pre": [10], "bodyId_post": [20], "weight": [5]},
                        {"bodyId_pre": [None], "bodyId_post": [20], "weight": [5]},
                        {"bodyId_pre": [10.0], "bodyId_post": [20], "weight": [5]},
                        {"unrecognized": [10], "bodyId_post": [20], "weight": [5]}):
            path = self.directory / "test.feather"
            feather.write_feather(pa.table(columns), path)
            if columns.get("bodyId_pre") == [10] and isinstance(columns["bodyId_pre"][0], int):
                actual = list(feather_batches(path))
                self.assertEqual([array.tolist() for array in actual[0]], [[10], [20], [5]])
            else:
                with self.assertRaises(ValueError):
                    list(feather_batches(path))


if __name__ == "__main__":
    unittest.main()
