package com.example.meowmba.core.neural

/** Trace provenance only. The built-in adapter owns its audited topology. */
object BancExperimentalCheckpoint {
    val value = NeuralCheckpoint(
        name = "banc-v888-identified-circuit-experimental-v1",
        substrate = NeuralSubstrate.BANC_EXPERIMENTAL,
        inputLayout = NeuralFeatures.INPUT.names,
        outputLayout = NeuralFeatures.OUTPUT.names,
        outputBaseline = List(NeuralFeatures.OUTPUT.size) { 0f },
        outputMin = List(NeuralFeatures.OUTPUT.size) { -1f },
        outputMax = List(NeuralFeatures.OUTPUT.size) { 1f },
        notes = "BANC v888 Figure 3h anatomical counts: 76 neurons, 515 directed pairs, 3316 synapse counts; CC BY 4.0. Synthetic bounded dynamics and readout mapping; not FlyWire or physiological flight.",
    )
}
