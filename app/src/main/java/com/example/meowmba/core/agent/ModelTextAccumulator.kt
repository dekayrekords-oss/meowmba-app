package com.example.meowmba.core.agent

/** Delta streams may repeat text legitimately; prefix heuristics lose tokens. */
class ModelTextAccumulator(private val maxCharacters: Int = 120_000) {
    private val buffer = StringBuilder()
    fun append(delta: String): String {
        check(buffer.length + delta.length <= maxCharacters) { "Model response exceeded the output limit" }
        buffer.append(delta)
        return buffer.toString()
    }
}
