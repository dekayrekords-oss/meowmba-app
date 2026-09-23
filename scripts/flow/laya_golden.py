#!/usr/bin/env python3
"""Golden answers from the Laya Python reference for Meow Flow's parity tests.

Master plan §4 S2 / §6.4. Run by the integrator AFTER the owner approves downloading
Laya (third-party model files). Nothing here downloads on import.

    pip install laya            # v0.3.x at time of writing; record the exact version
    ./gradlew :app:testDebugUnitTest --tests 'com.example.meowmba.flow.LayaInputsExportTest'
    python3 scripts/flow/laya_golden.py \
        --inputs app/build/flow-laya/inputs.json \
        --model convaiinnovations/laya-typed-decisions \
        --out app/src/test/resources/flow/laya_golden/replay_v0.json

To check a community ONNX conversion (e.g. nvkudva/laya-web-q8) against the PyTorch
reference, run once with each --model and compare with --compare.

Output per item: {case, intention, answers: {qid: {answer, probability, distribution}}},
plus metadata (model id, laya version, python version, input sha256) so the Kotlin engine's
parity test can refuse to compare against a different model or input file.
"""
import argparse
import hashlib
import json
import platform
import sys


def load_agent(model: str):
    import laya  # imported lazily so --help works without the package

    agent = laya.load(model)
    return agent, getattr(laya, "__version__", "unknown")


def normalise(result: dict) -> dict:
    """Turn Laya's result into {qid: {answer, probability, distribution}}.

    Laya returns choice (top label + probabilities + confidence), score and noul (P(true)).
    Keep the raw distributions so calibration can be refit later.
    """
    out = {}
    for qid, ans in result.get("answers", {}).items():
        if "choice" in ans:
            dist = ans.get("probabilities") or {}
            out[qid] = {"answer": ans["choice"], "probability": float(dist.get(ans["choice"], ans.get("confidence", 0.0))),
                        "distribution": {k: float(v) for k, v in dist.items()}}
        elif "probability" in ans or "p" in ans:
            p = float(ans.get("probability", ans.get("p")))
            out[qid] = {"answer": "yes" if p >= 0.5 else "no", "probability": p, "distribution": {}}
        else:
            out[qid] = {"answer": None, "probability": None, "distribution": {}, "raw": ans}
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--inputs", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--compare", help="another golden file to diff against (max abs probability delta)")
    args = ap.parse_args()

    raw = open(args.inputs, "rb").read()
    doc = json.loads(raw)
    agent, version = load_agent(args.model)
    questions = doc["questions"]
    items = []
    for it in doc["items"]:
        state = json.loads(it["state"])
        result = agent.predict(state, questions)
        items.append({"case": it["case"], "intention": it["intention"], "answers": normalise(result)})

    golden = {
        "model": args.model, "laya_version": version, "python": platform.python_version(),
        "inputs_sha256": hashlib.sha256(raw).hexdigest(), "items": items,
    }
    with open(args.out, "w") as f:
        json.dump(golden, f, indent=2, sort_keys=True)
    print(f"wrote {len(items)} items to {args.out}")

    if args.compare:
        other = {(i["case"], i["intention"]): i for i in json.load(open(args.compare))["items"]}
        worst = 0.0
        for i in items:
            o = other.get((i["case"], i["intention"]))
            if not o:
                continue
            for q, a in i["answers"].items():
                b = o["answers"].get(q, {})
                if a.get("probability") is not None and b.get("probability") is not None:
                    worst = max(worst, abs(a["probability"] - b["probability"]))
        print(f"max |Δp| vs {args.compare}: {worst:.4f}  (plan tolerance ±0.002 for the same ONNX file)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
