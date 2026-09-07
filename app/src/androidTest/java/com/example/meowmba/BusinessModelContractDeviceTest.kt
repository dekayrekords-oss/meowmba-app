package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.workflows.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Synthetic-only diagnostic contract: a role must carry the user's exact
 * input path, not a path hard-coded into its agent instructions. */
class BusinessModelContractDeviceTest {
    @Test fun localPlannerUsesDynamicJobInputs() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val provider = LiteRtProvider(context, File(context.filesDir, "models/gemma-4-E2B-it.litertlm").path)
        val input = "device-test-2456ef14-70b9-44f0-b0f0-1741f3db2e4c/orders.csv"
        val agent = BusinessAgent(name = "Order analyst", instructions = "Read the CSV named in your goal using files.csv_summary. Sum amount filtered by status equals unpaid. Finish with the selected row count and exact total.", tools = setOf("files.csv_summary"), completionTools = setOf("files.csv_summary"))
        val run = BusinessRun(agentId = agent.id, goal = "Analyze unpaid orders in $input. Use exact CSV arithmetic.")
        val definitions = WorkflowTools.local(File(context.filesDir, "workflow-contract-test")).definitions().filter { it.id in agent.tools }
        try {
            for (strict in listOf(true, false)) {
                val model = if (strict) provider else object : ModelProvider {
                    override val name = "Generic JSON contract diagnostic"
                    override suspend fun generate(prompt: String): ModelResponse = provider.generateStructured(prompt)
                    override fun generateStreaming(prompt: String) = provider.generateStreaming(prompt)
                }
                val decision = WorkflowModelBrain { model }.decide(agent, WorkFrame(agent.id, agent.instructions), run, definitions, emptyList())
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "strict=$strict decision=$decision\n") })
                assertEquals(input, decision.args["path"])
            }
        } finally { provider.release() }
    }
}
