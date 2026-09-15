#!/usr/bin/env python3
"""Fail before install if an APK loses/changes the installed native/model assets.

Usage: python3 scripts/verify_apk_runtime.py installed.apk candidate.apk [report.json]
Use an explicitly reviewed baseline when intentionally changing runtime assets.
"""
import hashlib
import json
import sys
import zipfile
from pathlib import Path


def verify(baseline: Path, candidate: Path) -> dict:
    with zipfile.ZipFile(baseline) as before, zipfile.ZipFile(candidate) as after:
        entries = [n for n in before.namelist()
                   if not n.endswith("/") and n.startswith(("lib/", "assets/"))]
        missing = [n for n in entries if n not in after.namelist()]
        changed = [n for n in entries if n not in missing and
                   hashlib.sha256(before.read(n)).digest() != hashlib.sha256(after.read(n)).digest()]
    if not any(n.startswith("lib/") for n in entries):
        raise ValueError("Baseline contains no native libraries; choose the installed full APK")
    digest = hashlib.sha256()
    with candidate.open("rb") as apk:
        for block in iter(lambda: apk.read(1024 * 1024), b""):
            digest.update(block)
    return {"baseline": baseline.name, "candidate": candidate.name,
            "protectedEntries": len(entries), "missing": missing, "changed": changed,
            "apkSha256": digest.hexdigest(),
            "passed": not missing and not changed}


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        raise SystemExit(__doc__)
    result = verify(Path(sys.argv[1]), Path(sys.argv[2]))
    report = json.dumps(result, indent=2) + "\n"
    if len(sys.argv) == 4:
        Path(sys.argv[3]).write_text(report)
    print(report, end="")
    raise SystemExit(0 if result["passed"] else 1)
