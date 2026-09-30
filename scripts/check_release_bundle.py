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


def dynamic_exports(data):
    """Read defined, externally visible symbols from an ELF64 dynamic table."""
    if data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected a little-endian ELF64 library")
    shoff = struct.unpack_from("<Q", data, 40)[0]
    shentsize, shnum = struct.unpack_from("<HH", data, 58)
    sections = [struct.unpack_from("<IIQQQQIIQQ", data, shoff + i * shentsize)
                for i in range(shnum)]
    exports = set()
    for section in sections:
        if section[1] != 11:  # SHT_DYNSYM
            continue
        strings = sections[section[6]]
        string_data = data[strings[4]:strings[4] + strings[5]]
        for offset in range(section[4], section[4] + section[5], section[9]):
            name, info, visibility, index, _, _ = struct.unpack_from("<IBBHQQ", data, offset)
            if index and info >> 4 in (1, 2) and visibility & 3 in (0, 3):
                end = string_data.index(b"\0", name)
                exports.add(string_data[name:end].decode("utf-8"))
    return exports


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
        # Android stores literal permission names in the protobuf manifest. This
        # conservative guard also catches a transitive AAR reintroducing them.
        manifest = archive.read("base/manifest/AndroidManifest.xml")
        forbidden_media_permissions = ["android.permission." + permission for permission in (
            "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO", "READ_MEDIA_VISUAL_USER_SELECTED")]
        result["forbidden_media_permissions"] = [permission for permission in forbidden_media_permissions
            if permission.encode() in manifest]
        required = {f"base/lib/arm64-v8a/{name}" for name in (
            "libsherpa-onnx-jni.so", "libonnxruntime.so", "libneedle_jni_bridge.so", "libsd_jni_bridge.so",
            "libmeow_cpu_features.so")}
        result["missing_required_native_libraries"] = sorted(required - set(names))
        image_library = "base/lib/arm64-v8a/libsd_jni_bridge.so"
        # A library can be packaged and aligned yet export obsolete JNI names.
        # Check actual dynamic exports, not strings that might exist in debug data.
        required_image_symbols = {
            "Java_com_example_meowmba_core_media_SDCppJniNative_" + method
            for method in ("createContext", "generate", "freeContext")
        }
        image_symbols = dynamic_exports(archive.read(image_library)) if image_library in names else set()
        result["missing_image_jni_exports"] = sorted(required_image_symbols - image_symbols)
        cpu_library = "base/lib/arm64-v8a/libmeow_cpu_features.so"
        cpu_symbols = dynamic_exports(archive.read(cpu_library)) if cpu_library in names else set()
        result["missing_cpu_probe_jni_exports"] = sorted({
            "Java_com_example_meowmba_core_agent_runtime_NativeCpuCapabilities_arm64Hwcap"
        } - cpu_symbols)
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
    return 0 if (result["all_native_libraries_16kb_aligned"] and
                 not result["forbidden_media_permissions"] and
                 not result["missing_image_jni_exports"] and
                 not result["missing_cpu_probe_jni_exports"] and
                 not result["missing_required_native_libraries"]) else 1


if __name__ == "__main__":
    raise SystemExit(main())
