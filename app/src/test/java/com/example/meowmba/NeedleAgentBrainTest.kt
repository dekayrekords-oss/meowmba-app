package com.example.meowmba

import com.example.meowmba.core.agent.runtime.NeedleAgentBrain
import com.example.meowmba.core.agent.tools.RiskLevel
import com.example.meowmba.core.agent.tools.Tool
import com.example.meowmba.core.agent.tools.ToolDefinition
import com.example.meowmba.core.agent.tools.ToolRegistry
import com.example.meowmba.core.agent.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedleAgentBrainTest {
    @Test fun `casual chat never loads or calls the native router`() = kotlinx.coroutines.test.runTest {
        val native = object : com.example.meowmba.core.agent.runtime.NeedleNative {
            override fun load(bytes: ByteArray): Int = error("Must not load for casual chat")
            override fun initialize(systemPrompt: String, toolsJson: String): Int = error("Must not initialize")
            override fun complete(input: String, maxTokens: Int): String? = error("Must not infer")
            override fun reset() {}
        }
        val model = java.nio.file.Files.createTempFile("needle-chat-gate", ".cact").toFile()
        try {
            val brain = NeedleAgentBrain(model, native)
            listOf("Just chillin with the cats.", "I watched a video of cats", "my computer is loud", "(buzzing)").forEach {
                assertNull(brain.route(it, registry()))
            }
        } finally { model.delete() }
    }
    @Test fun `native status distinguishes loading running and idle`() = kotlinx.coroutines.test.runTest {
        val states = mutableListOf<com.example.meowmba.core.agent.runtime.NeedleStatus>()
        val native = object : com.example.meowmba.core.agent.runtime.NeedleNative {
            override fun load(bytes: ByteArray) = 0
            override fun initialize(systemPrompt: String, toolsJson: String) = 12
            override fun complete(input: String, maxTokens: Int) = "{}"
            override fun reset() {}
        }
        val model = java.nio.file.Files.createTempFile("needle-status", ".cact").toFile()
        try {
            val brain = NeedleAgentBrain(model, native, onStatus = { synchronized(states) { states.add(it) }; Unit })
            brain.route("Remember that my favorite color is blue", registry())
            assertEquals(listOf(com.example.meowmba.core.agent.runtime.NeedleStatus.LOADING,
                com.example.meowmba.core.agent.runtime.NeedleStatus.RUNNING,
                com.example.meowmba.core.agent.runtime.NeedleStatus.IDLE), states)
        } finally { model.delete() }
    }

    private fun registry() = ToolRegistry().apply {
        register(object : Tool {
            override val definition = ToolDefinition(
                id = "memory.save",
                displayName = "Save memory",
                description = "Save a memory",
                inputSchema = mapOf("text" to "string"),
                riskLevel = RiskLevel.SAFE,
            )
            override suspend fun execute(args: Map<String, String>) = ToolResult.Success("unused")
        })
    }

    @Test
    fun `official call response resolves only a registered tool`() {
        val intent = NeedleAgentBrain.parse(
            """{"type":"call","success":true,"function_calls":[{"name":"memory.save","arguments":{"text":"Phoenix"}}]}""",
            registry(),
        )
        assertEquals("memory.save", intent?.toolId)
        assertEquals("Phoenix", intent?.args?.get("text"))
    }

    @Test
    fun `unknown or malformed output is declined`() {
        assertNull(NeedleAgentBrain.parse("""{"type":{},"function_calls":[]}""", registry()))
        assertNull(NeedleAgentBrain.parse("""{"type":"call","function_calls":[{"name":[]}]}""", registry()))
        assertNull(NeedleAgentBrain.parse("not json", registry()))
        assertNull(NeedleAgentBrain.parse(
            """{"type":"call","function_calls":[{"name":"shell.exec","arguments":{}}]}""",
            registry(),
        ))
    }

    @Test fun `structured arguments do not crash the native response parser`() {
        val intent = NeedleAgentBrain.parse(
            """{"type":"call","function_calls":[{"name":"memory.save","arguments":{"text":{"nested":"value"}}}]}""",
            registry(),
        )
        assertEquals("""{"nested":"value"}""", intent?.args?.get("text"))
    }

    @Test
    fun `tool schema includes declared inputs`() {
        val schema = NeedleAgentBrain.toolsJson(registry().definitions())
        assertTrue(schema.contains("memory.save"))
        assertTrue(schema.contains("text"))
    }
}
