package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agent.runtime.NeedleAgentBrain
import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Read-only smoke tests. The proposed tool is never executed. */
class Recovery139DeviceTest {
    @Test fun needle3LoadsAndProposesARegisteredTool() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "models/needle3.cact")
        assertTrue("Needle 3 download missing", file.isFile)
        val registry = ToolRegistry().apply {
            register(object : Tool {
                override val definition = ToolDefinition(id = "memory.save", displayName = "Save memory",
                    description = "Save a fact the user asks to remember",
                    inputSchema = mapOf("text" to "string"), riskLevel = RiskLevel.SAFE)
                override suspend fun execute(args: Map<String, String>): ToolResult = error("Probe must never execute actions")
            })
        }
        val intent = NeedleAgentBrain(file).route("Remember that my favorite color is blue", registry)
        assertNotNull("Native Needle 3 returned no valid action", intent)
        assertEquals("memory.save", intent?.toolId)
    }
}
