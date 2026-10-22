#!/usr/bin/env python3
"""Bounded synthetic held-out check of the readout fitter, not behavior quality."""
import argparse
import json
from pathlib import Path
from train_neural_core import fit_checkpoint, replay_rate_states


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    checkpoint = {"version": 1, "name": "synthetic-two-unit-fixture", "substrate": "RATE", "inputLayout": ["touch"], "outputLayout": ["attention"],
        "outputBaseline": [.2], "outputMin": [0.], "outputMax": [1.], "rate": {
        "units": 2, "tauMs": [100., 400.], "adaptTauMs": [1000., 1000.], "adaptGain": [0., .1],
        "bias": [0., 0.], "inputWeights": [1., .5], "recurrentWeights": [.2, -.1, .1, .3], "outputWeights": [0., 0.]}}
    def trace(period):
        rows = [{"tMs": (i+1)*50, "dtMs": 50, "input": [1. if (i//period)%2 else 0.]} for i in range(400)]
        states = replay_rate_states(checkpoint, rows)
        for row, h in zip(rows, states):
            row['targetReadout'] = [.2 + .3*h[0] + .2*h[1]]
        return rows, states
    training, _ = trace(12)
    held_out, states = trace(19)
    fitted = fit_checkpoint(checkpoint, training, 1e-6, 'targetReadout')
    predictions = [fitted['outputBaseline'][0]+sum(w*v for w,v in zip(fitted['rate']['outputWeights'], h)) for h in states]
    mean = sum(r['targetReadout'][0] for r in training)/len(training)
    mse = lambda values: sum((v-r['targetReadout'][0])**2 for v,r in zip(values,held_out))/len(held_out)
    report = {'scope': 'synthetic known teacher; held-out pulse period; no phone traces or measured behavior improvement',
        'trainingSamples':len(training),'heldOutSamples':len(held_out),'units':2,'fittedReadoutWeights':fitted['rate']['outputWeights'],
        'fittedBaseline': fitted['outputBaseline'], 'heldOutMSE':mse(predictions),'constantTrainingMeanMSE':mse([mean]*len(held_out))}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
