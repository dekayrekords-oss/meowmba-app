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
        assertNull(NeedleAgentBrain.parse("not json", registry()))
        assertNull(NeedleAgentBrain.parse(
            """{"type":"call","function_calls":[{"name":"shell.exec","arguments":{}}]}""",
            registry(),
        ))
    }

    @Test
    fun `tool schema includes declared inputs`() {
        val schema = NeedleAgentBrain.toolsJson(registry().definitions())
        assertTrue(schema.contains("memory.save"))
        assertTrue(schema.contains("text"))
    }
}
