#!/usr/bin/env python3
"""Expand the pinned MaleCNS FLYB roster with official confidence >= 0.5 weights.

This is an offline data converter, not a biological simulator. It retains the
base neuron's annotation/sign and retina bytes, including their limitations.
All positive official edges between those neurons are retained, including
weak connections and autapses. Duplicate source pairs are summed, never
clipped. Missing/decreased base edges fail closed instead of silently changing
the previously audited circuit. No network access or simulator edits occur.

Requires numpy and pyarrow (Feather v2 / Arrow IPC). Work buffers are bounded:
262,144 input rows and 32 MiB sorted partitions. Oversized partitions split on
disk, so neither a full pandas table nor a global edge-sort is needed. Allow
several GiB of temporary disk space in the output directory.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import tempfile
from dataclasses import dataclass

import numpy as np


BASE_SHA256 = "e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3"
WEIGHTS_MD5 = "f30e9dcca25cfd021bf1e7b3d975599e"
WEIGHTS_URL = ("https://storage.googleapis.com/flyem-male-cns/v1.0/"
               "connectome-data/flat-connectome/"
               "connectome-weights-male-cns-v1.0-minconf-0.5.feather")
RECORD = np.dtype([("key", "<u8"), ("weight", "<u8")])
CHUNK_ROWS = 262_144
SORT_BYTES = 32 * 1024 * 1024
BASE_MAX_BYTES = 512 * 1024 * 1024
INT32_MAX = 2**31 - 1
UINT16_MAX = 65_535
SCHEMAS = (("bodyId_pre", "bodyId_post", "weight"),
           ("body_pre", "body_post", "weight"), ("pre", "post", "weight"))


def digest(path: Path, algorithm: str = "sha256") -> str:
    result = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


class Cursor:
    def __init__(self, data: bytes):
        self.data, self.offset = data, 0

    def take(self, size: int) -> memoryview:
        if size < 0 or size > len(self.data) - self.offset:
            raise ValueError("Truncated or invalid FLYB field")
        start = self.offset
        self.offset += size
        return memoryview(self.data)[start:self.offset]

    def number(self, fmt: str) -> int:
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self, fmt: str) -> str:
        return bytes(self.take(self.number(fmt))).decode("utf-8", errors="strict")

    def array(self, dtype: str, count: int) -> np.ndarray:
        return np.frombuffer(self.take(np.dtype(dtype).itemsize * count), dtype=dtype)


@dataclass
class BaseGraph:
    n: int
    edges: int
    retina_count: int
    dataset: str
    metadata: dict
    tables_and_neurons: memoryview
    retina: memoryview
    body_ids: np.ndarray
    row_ptr: np.ndarray
    post: np.ndarray
    weight: np.ndarray


def parse_base(data: bytes) -> BaseGraph:
    """Strictly validate FLYB1 sizes, indices, CSR ordering and trailing bytes."""
    cursor = Cursor(data)
    if cursor.take(4) != b"FLYB" or cursor.number("<I") != 1:
        raise ValueError("Expected FLYB version 1")
    n, edges, retina_count = (cursor.number("<I") for _ in range(3))
    if not 0 < n <= INT32_MAX or edges > INT32_MAX or retina_count > INT32_MAX:
        raise ValueError("FLYB dimensions exceed Java signed-index limits")
    dataset = cursor.string("<H")
    metadata = json.loads(cursor.string("<I"))
    if not isinstance(metadata, dict):
        raise ValueError("FLYB metadata must be an object")
    annotations_start = cursor.offset
    counts = []
    for _ in range(10):
        count = cursor.number("<H")
        if not count:
            raise ValueError("FLYB string tables may not be empty")
        counts.append(count)
        for _ in range(count):
            cursor.string("<H")
    body_ids = cursor.array("<i8", n)
    if np.any(body_ids < 0) or np.any(body_ids[1:] <= body_ids[:-1]):
        raise ValueError("FLYB body IDs must be nonnegative, unique and sorted")
    for dtype, table in (("<i4", 0), ("u1", 1), ("u1", 2), ("<u2", 3), ("u1", 4)):
        values = cursor.array(dtype, n)
        if np.any(values < 0) or np.any(values >= counts[table]):
            raise ValueError("FLYB neuron annotation index out of bounds")
    signs = cursor.array("i1", n)
    if np.any((signs < -1) | (signs > 1)):
        raise ValueError("FLYB neurotransmitter sign out of bounds")
    sides = cursor.array("u1", n)
    if np.any(sides >= counts[5]):
        raise ValueError("FLYB side annotation index out of bounds")
    cursor.take(2 * n)  # Signed hex coordinates; retain missing -1 verbatim.
    for table in range(6, 10):
        if np.any(cursor.array("u1", n) >= counts[table]):
            raise ValueError("FLYB neuron annotation index out of bounds")
    soma = cursor.array("<f4", 3 * n)
    if np.any(np.isinf(soma)):
        raise ValueError("Infinite FLYB soma coordinate")
    if np.any(cursor.array("<i4", 2 * n) < 0):
        raise ValueError("Negative FLYB neuron synapse annotation")
    annotations_end = cursor.offset
    row_ptr = cursor.array("<i4", n + 1)
    post = cursor.array("<i4", edges)
    weight = cursor.array("<u2", edges)
    if row_ptr[0] != 0 or row_ptr[-1] != edges or np.any(row_ptr[1:] < row_ptr[:-1]):
        raise ValueError("Invalid FLYB CSR row pointers")
    if np.any(post < 0) or np.any(post >= n) or np.any(weight == 0):
        raise ValueError("Invalid FLYB edge index or zero weight")
    for start, end in zip(row_ptr[:-1], row_ptr[1:]):
        row = post[start:end]
        if np.any(row[1:] <= row[:-1]):
            raise ValueError("FLYB CSR contains duplicate or unsorted pairs")
    retina = cursor.take(8 * retina_count)
    retina_records = np.frombuffer(retina, dtype=np.dtype([
        ("neuron", "<i4"), ("side", "u1"), ("hex1", "i1"),
        ("hex2", "i1"), ("kind", "u1")]))
    if (np.any(retina_records["neuron"] < 0) or np.any(retina_records["neuron"] >= n)
            or np.any(retina_records["side"] >= counts[5])
            or np.any(retina_records["kind"] > 5)):
        raise ValueError("FLYB retina index out of bounds")
    if cursor.offset != len(data):
        raise ValueError("Unexpected trailing FLYB bytes")
    return BaseGraph(n, edges, retina_count, dataset, metadata,
                     memoryview(data)[annotations_start:annotations_end], retina,
                     body_ids, row_ptr, post, weight)


def feather_batches(path: Path):
    """Read only recognized integer edge columns, batchwise, from Feather v2."""
    import pyarrow as pa
    import pyarrow.ipc as ipc

    with pa.memory_map(str(path), "r") as source:
        schema = ipc.open_file(source).schema
        matches = [columns for columns in SCHEMAS if all(name in schema.names for name in columns)]
        if len(matches) != 1 or len(schema.names) != len(set(schema.names)):
            raise ValueError(f"Unrecognized or ambiguous official edge schema: {schema.names}")
        columns = matches[0]
        for name in columns:
            if not pa.types.is_integer(schema.field(name).type):
                raise ValueError(f"Source column {name} must use an integer Arrow type")
        indices = [schema.get_field_index(name) for name in columns]
        reader = ipc.open_file(source, options=ipc.IpcReadOptions(included_fields=indices))
        for index in range(reader.num_record_batches):
            batch = reader.get_batch(index)
            for offset in range(0, batch.num_rows, CHUNK_ROWS):
                arrays = [batch.column(batch.schema.get_field_index(name)).slice(offset, CHUNK_ROWS)
                          for name in columns]
                if any(array.null_count for array in arrays):
                    raise ValueError("Null source endpoint or weight")
                yield tuple(array.to_numpy(zero_copy_only=False) for array in arrays)


def integer_array(values, label: str) -> np.ndarray:
    values = np.asarray(values)
    if values.ndim != 1 or values.dtype.kind not in "iu":
        raise ValueError(f"{label} must contain integer values, never floating IDs/counts")
    if len(values) and (np.any(values < 0) or np.any(values > np.iinfo(np.int64).max)):
        raise ValueError(f"{label} must fit nonnegative signed 64-bit integers")
    return values.astype("<i8", copy=False)


def exact_sum(values: np.ndarray) -> int:
    if not len(values):
        return 0
    if int(values.max()) * len(values) <= np.iinfo(np.uint64).max:
        return int(values.sum(dtype=np.uint64))
    return sum(map(int, values))


def partition_edges(base: BaseGraph, batches, directory: Path, stats: dict) -> list[tuple[Path, int, int]]:
    """Group by consecutive presynaptic ranges without retaining source rows."""
    width = max(1, (base.n + 255) // 256)
    partitions = [(directory / f"bucket-{start // width}.bin", start * base.n,
                   min(base.n, start + width) * base.n) for start in range(0, base.n, width)]
    for pre_values, post_values, weight_values in batches:
        pre = integer_array(pre_values, "presynaptic IDs")
        post = integer_array(post_values, "postsynaptic IDs")
        weight = integer_array(weight_values, "weights")
        if not len(pre) == len(post) == len(weight):
            raise ValueError("Source edge columns have different lengths")
        for offset in range(0, len(pre), CHUNK_ROWS):
            a, b, w = pre[offset:offset + CHUNK_ROWS], post[offset:offset + CHUNK_ROWS], weight[offset:offset + CHUNK_ROWS]
            stats["source_rows"] += len(a)
            stats["source_synapses"] += exact_sum(w)
            stats["zero_weight_rows"] += int(np.count_nonzero(w == 0))
            pre_idx, post_idx = np.searchsorted(base.body_ids, a), np.searchsorted(base.body_ids, b)
            present = ((pre_idx < base.n) & (post_idx < base.n)
                       & (base.body_ids[np.minimum(pre_idx, base.n - 1)] == a)
                       & (base.body_ids[np.minimum(post_idx, base.n - 1)] == b))
            stats["outside_roster_rows"] += int(np.count_nonzero(~present))
            stats["outside_roster_synapses"] += exact_sum(w[~present])
            keep = present & (w > 0)
            a, b, w = pre_idx[keep], post_idx[keep], w[keep]
            if len(w) and np.any(w > UINT16_MAX):
                raise ValueError("Official pair weight exceeds FLYB1 u16 capacity; refusing clipping")
            stats["accepted_source_rows"] += len(a)
            stats["accepted_source_synapses"] += exact_sum(w)
            group = a // width
            order = np.argsort(group, kind="stable")
            group = group[order]
            records = np.empty(len(order), dtype=RECORD)
            records["key"] = a[order].astype(np.uint64) * base.n + b[order].astype(np.uint64)
            records["weight"] = w[order]
            if not len(group):
                continue
            starts = np.r_[0, np.flatnonzero(group[1:] != group[:-1]) + 1, len(group)]
            for start, end in zip(starts[:-1], starts[1:]):
                with partitions[int(group[start])][0].open("ab") as stream:
                    records[start:end].tofile(stream)
    return partitions


def sorted_partitions(path: Path, low: int, high: int, sort_bytes: int = SORT_BYTES):
    """Yield sorted, summed unique pairs; split unusually dense buckets on disk."""
    if not path.exists():
        return
    size = path.stat().st_size
    if size % RECORD.itemsize:
        raise ValueError("Truncated temporary edge records")
    if size <= sort_bytes:
        records = np.fromfile(path, dtype=RECORD)
        records.sort(order="key", kind="quicksort")
        starts = np.r_[0, np.flatnonzero(records["key"][1:] != records["key"][:-1]) + 1]
        # Individual weights <= 65535 and <= 2M records make u64 addition exact.
        summed = np.add.reduceat(records["weight"], starts)
        if np.any(summed > UINT16_MAX):
            raise ValueError("Aggregated official pair exceeds FLYB1 u16 capacity; refusing clipping")
        yield records["key"][starts], summed
    elif high - low == 1:
        total = 0
        with path.open("rb") as source:
            while len(chunk := np.fromfile(source, dtype=RECORD, count=CHUNK_ROWS)):
                total += exact_sum(chunk["weight"])
                if total > UINT16_MAX:
                    raise ValueError("Aggregated official pair exceeds FLYB1 u16 capacity; refusing clipping")
        yield np.asarray([low], dtype="<u8"), np.asarray([total], dtype="<u8")
    else:
        middle = (low + high) // 2
        left, right = path.with_name(path.name + "l"), path.with_name(path.name + "r")
        with path.open("rb") as source, left.open("wb") as a, right.open("wb") as b:
            while len(chunk := np.fromfile(source, dtype=RECORD, count=CHUNK_ROWS)):
                mask = chunk["key"] < middle
                chunk[mask].tofile(a)
                chunk[~mask].tofile(b)
        path.unlink()
        for child, start, end in ((left, low, middle), (right, middle, high)):
            if child.stat().st_size:
                yield from sorted_partitions(child, start, end, sort_bytes)
            else:
                child.unlink()
    if path.exists():
        path.unlink()


def compare_base(base: BaseGraph, keys: np.ndarray, weights: np.ndarray, stats: dict):
    """Audit every base pair covered by this sorted segment, including gaps."""
    # Root conversion calls with a complete presynaptic partition, not a leaf.
    pre_start, pre_end = int(stats.pop("compare_pre_start")), int(stats.pop("compare_pre_end"))
    start, end = int(base.row_ptr[pre_start]), int(base.row_ptr[pre_end])
    old_pre = np.repeat(np.arange(pre_start, pre_end, dtype=np.uint64),
                        np.diff(base.row_ptr[pre_start:pre_end + 1]))
    old_keys = old_pre * base.n + base.post[start:end].astype(np.uint64)
    positions = np.searchsorted(keys, old_keys)
    present = positions < len(keys)
    if len(keys):
        present &= keys[np.minimum(positions, len(keys) - 1)] == old_keys
    current = np.zeros(len(old_keys), dtype=np.uint64)
    current[present] = weights[positions[present]]
    previous = base.weight[start:end].astype(np.uint64)
    missing, decreased = ~present, present & (current < previous)
    increased = present & (current > previous)
    stats["base_pairs_compared"] += len(old_keys)
    stats["base_pairs_missing"] += int(np.count_nonzero(missing))
    stats["base_weights_decreased"] += int(np.count_nonzero(decreased))
    stats["base_weights_increased"] += int(np.count_nonzero(increased))
    stats["base_added_synapses_on_existing_pairs"] += exact_sum(current[increased] - previous[increased])
    for index in np.flatnonzero(missing | decreased | increased)[:max(0, 20 - len(stats["base_weight_change_samples"]))]:
        key = int(old_keys[index])
        stats["base_weight_change_samples"].append({"pre": int(base.body_ids[key // base.n]),
            "post": int(base.body_ids[key % base.n]), "base_weight": int(previous[index]),
            "official_weight": int(current[index])})


def expand(base_path: Path, weights_path: Path, output: Path, report_path: Path, *,
           expected_base_sha256: str = BASE_SHA256, expected_weights_md5: str = WEIGHTS_MD5,
           batch_factory=feather_batches, sort_bytes: int = SORT_BYTES) -> dict:
    """CLI uses pinned identities; explicit fixture identities are for unit tests."""
    base_path, weights_path, output, report_path = map(Path, (base_path, weights_path, output, report_path))
    if len({path.resolve() for path in (base_path, weights_path, output, report_path)}) != 4:
        raise ValueError("Inputs, output and report must be distinct files")
    if output.exists() or report_path.exists():
        raise FileExistsError("Refusing to overwrite an existing graph or report")
    if digest(base_path) != expected_base_sha256:
        raise ValueError("Base FLYB SHA-256 does not match the pinned audited graph")
    if digest(weights_path, "md5") != expected_weights_md5:
        raise ValueError("Official weights MD5 does not match the pinned public object")
    with gzip.open(base_path, "rb") as stream:
        raw = stream.read(BASE_MAX_BYTES + 1)
    if len(raw) > BASE_MAX_BYTES:
        raise ValueError("Base FLYB exceeds bounded parser capacity")
    base = parse_base(raw)
    output.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    stats = dict.fromkeys(("source_rows", "source_synapses", "zero_weight_rows", "outside_roster_rows",
        "outside_roster_synapses", "accepted_source_rows", "accepted_source_synapses", "edges", "synapses",
        "weak_edges_1_to_4", "weak_synapses_1_to_4", "autapse_edges", "autapse_synapses", "base_pairs_compared",
        "base_pairs_missing", "base_weights_decreased", "base_weights_increased", "base_added_synapses_on_existing_pairs"), 0)
    stats["base_weight_change_samples"] = []
    with tempfile.TemporaryDirectory(prefix="malecns-expand-", dir=output.parent) as temp:
        directory = Path(temp)
        partitions = partition_edges(base, batch_factory(weights_path), directory, stats)
        row_counts = np.zeros(base.n, dtype=np.int64)
        post_path, weight_path = directory / "post.bin", directory / "weight.bin"
        with post_path.open("wb") as post_file, weight_path.open("wb") as weight_file:
            for path, low, high in partitions:
                # A bounded on-disk sorted copy supports base auditing, including
                # missing rows, without keeping a complete dense bucket in RAM.
                key_path, audit_weight_path = directory / "audit-key.bin", directory / "audit-weight.bin"
                with key_path.open("wb") as key_file, audit_weight_path.open("wb") as audit_file:
                    for keys, weights in sorted_partitions(path, low, high, sort_bytes):
                        pre, post = (keys // base.n).astype(np.int64), (keys % base.n).astype("<i4")
                        row_counts += np.bincount(pre, minlength=base.n)
                        keys.tofile(key_file)
                        weights.astype("<u2").tofile(audit_file)
                        post.tofile(post_file)
                        weights.astype("<u2").tofile(weight_file)
                        stats["edges"] += len(keys)
                        stats["synapses"] += exact_sum(weights)
                        weak, autapse = weights < 5, pre == post
                        stats["weak_edges_1_to_4"] += int(np.count_nonzero(weak))
                        stats["weak_synapses_1_to_4"] += exact_sum(weights[weak])
                        stats["autapse_edges"] += int(np.count_nonzero(autapse))
                        stats["autapse_synapses"] += exact_sum(weights[autapse])
                keys = np.memmap(key_path, dtype="<u8", mode="r") if key_path.stat().st_size else np.array([], dtype="<u8")
                weights = np.memmap(audit_weight_path, dtype="<u2", mode="r") if audit_weight_path.stat().st_size else np.array([], dtype="<u2")
                stats.update(compare_pre_start=low // base.n, compare_pre_end=high // base.n)
                compare_base(base, keys, weights, stats)
                del keys, weights
                key_path.unlink()
                audit_weight_path.unlink()
        if stats["base_pairs_missing"] or stats["base_weights_decreased"]:
            raise ValueError("Official graph does not preserve the base: " + json.dumps(stats, sort_keys=True))
        if stats["base_pairs_compared"] != base.edges or stats["synapses"] != stats["accepted_source_synapses"]:
            raise ValueError("Internal edge accounting mismatch")
        if stats["edges"] > INT32_MAX:
            raise ValueError("Expanded graph exceeds FLYB1 signed edge-index capacity")
        stats["duplicate_source_rows_aggregated"] = stats["accepted_source_rows"] - stats["edges"]
        provenance = {"converter_format": 1, "base_sha256": expected_base_sha256,
            "weights_md5": expected_weights_md5, "weights_sha256": digest(weights_path),
            "weights_url": WEIGHTS_URL, "source_confidence_threshold": 0.5,
            "minimum_pair_weight": 1, "autapses_retained": True,
            "duplicate_pairs": "summed", "weight_clipping": False,
            "scope": "all positive official edges whose endpoints are in the original annotated roster",
            "annotations_and_retina_preserved": True,
            "roster_sha256": hashlib.sha256(base.body_ids.tobytes()).hexdigest(),
            "annotation_bytes_sha256": hashlib.sha256(base.tables_and_neurons).hexdigest(),
            "retina_bytes_sha256": hashlib.sha256(base.retina).hexdigest(),
            "base_edges": base.edges, "base_synapses": exact_sum(base.weight),
            "neurons": base.n, "retina_entries": base.retina_count, **stats}
        metadata = dict(base.metadata)
        original_built = metadata.pop("built", None)
        metadata.update(min_weight=1, edges=stats["edges"], synapses_in_edges=stats["synapses"],
                        expansion=provenance)
        if original_built is not None:
            metadata["annotation_source_built"] = original_built
        metadata_bytes = json.dumps(metadata, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()
        dataset_bytes = base.dataset.encode()
        staged = directory / "expanded.flyb.gz"
        with staged.open("wb") as file, gzip.GzipFile(filename="", fileobj=file, mode="wb", mtime=0, compresslevel=6) as stream:
            stream.write(b"FLYB" + struct.pack("<IIII", 1, base.n, stats["edges"], base.retina_count))
            stream.write(struct.pack("<H", len(dataset_bytes)) + dataset_bytes)
            stream.write(struct.pack("<I", len(metadata_bytes)) + metadata_bytes)
            stream.write(base.tables_and_neurons)
            stream.write(np.r_[0, np.cumsum(row_counts)].astype("<i4").tobytes())
            for path in (post_path, weight_path):
                with path.open("rb") as source:
                    shutil.copyfileobj(source, stream, length=1024 * 1024)
            stream.write(base.retina)
        report = {**provenance, "output_sha256": digest(staged), "output_bytes": staged.stat().st_size,
                  "base_subset_verified": True, "source_bytes": weights_path.stat().st_size,
                  "processing_limits": {"source_chunk_rows": CHUNK_ROWS, "sort_partition_bytes": sort_bytes},
                  "limitations": ["Original roster only; no unannotated endpoint is invented.",
                    "Original neurotransmitter signs and retina projection are unchanged.",
                    "Connectivity weights are synapse counts, not a validated whole-animal physiology model."]}
        report_staged = directory / "report.json"
        report_staged.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
        os.replace(staged, output)
        # The report may live on another filesystem; copy to a sibling then rename.
        with tempfile.NamedTemporaryFile(dir=report_path.parent, delete=False) as destination:
            report_temp = Path(destination.name)
            destination.write(report_staged.read_bytes())
        os.replace(report_temp, report_path)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--weights", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = expand(args.base, args.weights, args.output, args.report)
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
