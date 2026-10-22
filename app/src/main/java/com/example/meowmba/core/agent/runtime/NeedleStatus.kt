package com.example.meowmba.core.agent.runtime

/** Readiness is distinct from native inference: the model is loaded on demand. */
enum class NeedleStatus(val label: String) {
    MISSING("Not downloaded"), READY("Ready · on demand"),
    LOADING("Loading"), RUNNING("Running · routing an action"),
    IDLE("Loaded · idle"), FAILED("Unavailable · retry in Models")
}
