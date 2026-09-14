#!/usr/bin/env python3
"""Compile and probe a hash-pinned external MaleCNS model without Minecraft/Gradle.

No downloads, services, Android installation, or public uploads. The source clone
is read-only; patched classes and raw result logs stay in the chosen output dir.
"""
import argparse
import hashlib
import json
import pathlib
import platform
import shutil
import subprocess
import time

REVISION = "6cfa30175003ef25da68a237d5eda958f8047b82"
DATA_HASH = "e33df182bed7a6f3ea279daf4790a82b05706d3d41e819a6a80c0473e8c559f3"
SOURCE_HASHES = {
    "Connectome.java": "3cf510dac20bac39b5c91a436942297db352491e5c86da6e154f06ebeb6d7e22",
    "LifConfig.java": "2e87c3dc5e12e73a102bb59ddf2c1760ab1209b20bf926560eceeb1bc5e2516c",
    "LifNetwork.java": "33c30cc556213ded9ca12b9074811d42ae5cf1ab5905caf50f145ce91fa4aaf0",
    "PopulationIndex.java": "e0d3cd729ce35844f31a3187d14353046856977bf6ffa4f70babf62e86c0124c",
}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def checked_replace(source, old, new):
    if source.count(old) != 1:
        raise ValueError("Pinned upstream patch context changed")
    return source.replace(old, new)


def replay_patch(source):
    source = checked_replace(source, "private final SplittableRandom rng;",
                             "private SplittableRandom rng;\n    private SplittableRandom observerRng;")
    source = checked_replace(source, "this.rng = new SplittableRandom(this.cfg.seed);",
                             "this.rng = new SplittableRandom(this.cfg.seed);\n"
                             "        this.observerRng = new SplittableRandom(this.cfg.seed ^ 0x4D454F57L);")
    source = checked_replace(source, "long j = rng.nextLong(seen);", "long j = observerRng.nextLong(seen);")
    return checked_replace(source, "public void reset() {", "public void reset() {\n"
                           "        rng = new SplittableRandom(cfg.seed);\n"
                           "        observerRng = new SplittableRandom(cfg.seed ^ 0x4D454F57L);")


def run(args, cwd=None, timeout=120):
    result = subprocess.run(args, cwd=cwd, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"{args[0]} failed ({result.returncode}): {result.stderr[-4000:]}")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--output-dir", type=pathlib.Path, required=True)
    parser.add_argument("--jdk", type=pathlib.Path, required=True)
    parser.add_argument("--duration-ms", type=int, default=600)
    a = parser.parse_args()
    if not 600 <= a.duration_ms <= 5000 or a.duration_ms % 50:
        parser.error("duration must be 600..5000 ms in multiples of 50")
    source, output = a.source.resolve(), a.output_dir.resolve()
    if output == source or source in output.parents:
        parser.error("output must be outside the upstream checkout")
    output.mkdir(parents=True, exist_ok=False)
    revision = run(["git", "rev-parse", "HEAD"], cwd=source).stdout.strip()
    if revision != REVISION:
        raise ValueError("Upstream revision changed; review before execution")
    code = source / "src/main/java/com/fruitfly/brain"
    data = source / "src/main/resources/connectome/malecns-v1.0.flyb.gz"
    if data.stat().st_size != 22964094 or digest(data) != DATA_HASH:
        raise ValueError("Connectome hash/size mismatch")
    for name, sha in SOURCE_HASHES.items():
        if digest(code / name) != sha:
            raise ValueError(f"Upstream code changed: {name}")
    java, javac = str(a.jdk / "bin/java"), str(a.jdk / "bin/javac")
    report = {
        "schema": 1, "source_url": "https://github.com/blendi-remade/fly-brain-minecraft",
        "source_revision": revision, "data_sha256": DATA_HASH,
        "source_sha256": SOURCE_HASHES, "host": platform.platform(),
        "java": run([java, "-version"]).stderr.strip(),
        "dataset": "male-cns:v1.0", "neurons": 176422,
        "directed_pairs": 6287749, "anatomical_synapse_counts": 90296905,
        "min_pair_synapses": 5, "autapses_removed": True,
        "integration_dt_ms": .5, "threads": 1, "seed": 20260903,
        "duration_ms": a.duration_ms, "variants": {},
        "limitations": ["Counts and annotations come from a third-party derivative; original full export not independently re-extracted.",
                        "Uniform LIF dynamics, sign heuristics, gain and sensory drives are modeling assumptions.",
                        "Loom injects LC4/LPLC2 directly, not camera pixels through the retina.",
                        "No body physics, Android runtime, battery, learning or robot validation.",
                        "Timing is a short single-worker host sample, not a sustained or phone performance gate."],
    }
    for variant in ("upstream", "replay_fixed"):
        build = output / variant
        build.mkdir()
        shutil.copy2(source / "LICENSE", build / "UPSTREAM_LICENSE.txt")
        for name in SOURCE_HASHES:
            text = (code / name).read_text()
            if name == "LifNetwork.java" and variant == "replay_fixed":
                text = replay_patch(text)
            (build / name).write_text(text)
        probe = pathlib.Path(__file__).with_name("MaleCnsProbe.java")
        report["probe_sha256"] = digest(probe)
        run([javac, "-encoding", "UTF-8", "--release", "17", "-d", str(build), *[str(build / n) for n in SOURCE_HASHES], str(probe)])
        cases = [("quiet", "off"), ("sugar", "reset"), ("sugar_bitter", "off"),
                 ("groom", "off"), ("groom", "on"), ("loom", "off"),
                 ("sugar", "ablated"), ("loom", "ablated")]
        results = {}
        for scenario, mode in cases:
            start = time.monotonic()
            result = run([java, "-Xmx512m", "-XX:ActiveProcessorCount=1", "-cp", str(build),
                          "MaleCnsProbe", str(data), scenario, mode, str(a.duration_ms)])
            key = scenario + "/" + mode
            (build / (scenario + "-" + mode + ".jsonl")).write_text(result.stdout)
            results[key] = [json.loads(line) for line in result.stdout.splitlines()]
            print(f"{variant} {key}: {time.monotonic() - start:.2f}s", flush=True)
        reset = results["sugar/reset"]
        signature = lambda r: r["state_sha256"]
        checks = {
            "silent_without_stimulus": results["quiet/off"][0]["spikes"] == 0,
            "reset_reproduces_neuron_states": signature(reset[0]) == signature(reset[1]),
            "observer_does_not_change_neuron_states": signature(results["groom/off"][0]) == signature(results["groom/on"][0]),
            "sugar_reaches_proboscis_motor": reset[0]["mean_population_hz"]["MN9"] > 0,
            "bitter_suppresses_sugar_response": results["sugar_bitter/off"][0]["mean_population_hz"]["MN9"] < reset[0]["mean_population_hz"]["MN9"],
            "groom_reaches_descending_neurons": results["groom/off"][0]["mean_population_hz"]["DNg62"] > 0,
            "loom_reaches_jump_motor": results["loom/off"][0]["mean_population_hz"]["TTMn"] > 0,
            "sugar_output_requires_connections": results["sugar/ablated"][0]["mean_population_hz"]["MN9"] == 0,
            "loom_output_requires_connections": results["loom/ablated"][0]["mean_population_hz"]["TTMn"] == 0,
        }
        report["variants"][variant] = {"checks": checks, "runs": results,
            "lif_network_sha256": digest(build / "LifNetwork.java")}
        (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    if not all(report["variants"]["replay_fixed"]["checks"].values()):
        raise SystemExit("Corrected model did not pass all acceptance checks; see report.json")
    print(json.dumps({k: v["checks"] for k, v in report["variants"].items()}, indent=2))


if __name__ == "__main__":
    main()
