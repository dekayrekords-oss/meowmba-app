#!/usr/bin/env python3
"""Build/run the SDK-only release harness on an explicitly named disposable emulator.

The target APK must be a QA copy signed with the same debug key. This script does
not sign or upload production bundles, install the target app, or download models.
"""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--qa-keystore", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--phase", choices=("seed", "verify", "inspect-files", "picker"), default="verify")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("Only a disposable emulator serial is allowed")
    adb = [str(args.sdk / "platform-tools/adb"), "-s", args.serial]
    if subprocess.check_output(adb + ["shell", "getprop", "ro.kernel.qemu"], text=True).strip() != "1":
        parser.error("Target must be an Android emulator")
    source = Path(__file__).resolve().parent
    output = args.work_dir.resolve() / "sdk-qa"
    classes = output / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    android = sorted((args.sdk / "platforms").glob("*/android.jar"))[-1]
    build_tools = sorted((args.sdk / "build-tools").glob("*"))[-1]
    env = os.environ.copy()
    env["JAVA_HOME"] = str(args.java_home)
    # Only the standard local QA/debug keystore. Never read the Play upload key.
    env["MEOW_QA_STORE_PASS"] = "android"
    env["MEOW_QA_KEY_PASS"] = "android"

    def run(command, **kwargs):
        return subprocess.run([str(x) for x in command], check=True, env=env, **kwargs)

    run([args.java_home / "bin/javac", "--release", "11", "-classpath", android, "-d", classes,
         source / "PlayReleaseInstrumentation.java"])
    run([build_tools / "d8", "--min-api", "31", "--lib", android, "--output", output,
         *sorted(classes.rglob("*.class"))])
    unsigned = output / "unsigned.apk"
    run([build_tools / "aapt2", "link", "-I", android, "--manifest", source / "AndroidManifest.xml", "-o", unsigned])
    with zipfile.ZipFile(unsigned, "a") as archive:
        archive.write(output / "classes.dex", "classes.dex")
    apk = output / "qa-only-sdk-harness.apk"
    run([build_tools / "apksigner", "sign", "--ks", args.qa_keystore, "--ks-key-alias", "androiddebugkey",
         "--ks-pass", "env:MEOW_QA_STORE_PASS", "--key-pass", "env:MEOW_QA_KEY_PASS", "--out", apk, unsigned])
    run(adb + ["install", "--no-incremental", "-r", apk])
    result = subprocess.check_output(adb + ["shell", "am", "instrument", "-w", "-e", "phase", args.phase,
                                          "com.meow.qa.release/com.meow.qa.release.PlayReleaseInstrumentation"], text=True)
    (output / (args.phase + "-result.txt")).write_text(result)
    print(result)
    if "FAIL:" in result or "PASS:" not in result:
        raise SystemExit("SDK release QA failed; see saved result and emulator evidence")


if __name__ == "__main__":
    main()
