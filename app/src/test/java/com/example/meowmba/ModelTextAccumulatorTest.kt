package com.example.meowmba

import com.example.meowmba.core.agent.ModelTextAccumulator
import org.junit.Assert.*
import org.junit.Test

class ModelTextAccumulatorTest {
    @Test fun jsonDeltasRetainTheWholeDecision() {
        val stream = ModelTextAccumulator()
        listOf("{", "\"action\"", ":", "\"delegate\"", ",\"agentId\":\"analyst\"").forEach { stream.append(it) }
        assertEquals("""{"action":"delegate","agentId":"analyst"}""", stream.append("}"))
    }
    @Test fun repeatedTokensAndEmptyChunksAreNotRemoved() {
        val stream = ModelTextAccumulator()
        stream.append("ha"); stream.append("ha"); stream.append("")
        assertEquals("hahaha", stream.append("ha"))
    }
    @Test fun unboundedOutputFailsClearly() {
        assertTrue(runCatching { ModelTextAccumulator(2).append("abc") }.isFailure)
    }
}
