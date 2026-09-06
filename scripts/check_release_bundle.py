#!/usr/bin/env python3
"""Inspect an AAB's native ELF alignment without extracting or executing it.

This is not a substitute for bundletool validation or a 16 KB device test.
JAR signature entries are reported, not cryptographically verified.
"""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path


def load_alignments(data):
    if data[:4] != b"\x7fELF" or data[4:6] != b"\x02\x01":
        raise ValueError("Expected a little-endian ELF64 library")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    alignments = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        fields = struct.unpack_from("<IIQQQQQQ", data, offset)
        if fields[0] == 1:  # PT_LOAD
            alignments.append(fields[7])
    if not alignments:
        raise ValueError("ELF has no loadable segments")
    return alignments


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    args = parser.parse_args()
    result = {"bundle": str(args.bundle), "bytes": args.bundle.stat().st_size}
    with args.bundle.open("rb") as source:
        digest = hashlib.sha256()
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    result["sha256"] = digest.hexdigest()
    libraries = []
    with zipfile.ZipFile(args.bundle) as archive:
        names = archive.namelist()
        result["signature_entries"] = [name for name in names if
            name.upper().startswith("META-INF/") and
            name.upper().endswith((".RSA", ".DSA", ".EC", ".SF"))]
        result["model_asset_entries"] = [name for name in names if
            name.lower().endswith((".glb", ".gltf", ".blend"))]
        for name in sorted(names):
            if not name.endswith(".so"):
                continue
            try:
                alignments = load_alignments(archive.read(name))
                libraries.append({"name": name, "load_alignments": alignments,
                                  "passes_16kb_alignment": all(a >= 16384 for a in alignments)})
            except (ValueError, struct.error) as error:
                libraries.append({"name": name, "error": str(error),
                                  "passes_16kb_alignment": False})
    result["native_libraries"] = libraries
    result["all_native_libraries_16kb_aligned"] = bool(libraries) and all(
        lib["passes_16kb_alignment"] for lib in libraries)
    print(json.dumps(result, indent=2))
    return 0 if result["all_native_libraries_16kb_aligned"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
