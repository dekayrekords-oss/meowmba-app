package com.example.meowmba

import com.example.meowmba.core.agent.*
import com.example.meowmba.core.workflows.*
import com.example.meowmba.core.agent.tools.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BusinessPlannerProtocolTest {
    @Test fun malformedActionGetsCorrectedBeforeExecution() = runTest {
        var calls = 0
        val provider = object : ModelProvider {
            override val name = "Test planner"
            override fun generateStreaming(prompt: String) = emptyFlow<ModelResponse>()
            override suspend fun generate(prompt: String): ModelResponse {
                calls++
                return ModelResponse.Complete(if (calls == 1) """{"action":"read_file","tool":"files.read"}"""
                    else """{"action":"tool","tool":"files.read","args":{"path":"orders.csv"}}""")
            }
        }
        val agent = BusinessAgent(name = "Shop", instructions = "Read the order file", tools = setOf("files.read"))
        val run = BusinessRun(agentId = agent.id, goal = "Review orders.csv")
        val read = ToolDefinition("files.read", "Read", "Read a file", emptyMap(), riskLevel = RiskLevel.SAFE)
        val decision = WorkflowModelBrain { provider }.decide(agent, run.frames.first(), run, listOf(read), emptyList())
        assertEquals(2, calls)
        assertEquals("tool", decision.action)
        assertEquals("orders.csv", decision.args["path"])
    }

    @Test fun remotePlannerUsesFunctionSchemaAndRepairsUnknownAction() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            val body = Json.parseToJsonElement((request.body as io.ktor.http.content.TextContent).text).jsonObject
            assertTrue("Business planner did not send tool schema", body.containsKey("tools"))
            val args = if (calls == 1) """{"action":"invented"}""" else """{"action":"blocked","summary":"Need order input"}"""
            val response = buildJsonObject {
                putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") {
                    putJsonArray("tool_calls") { add(buildJsonObject {
                        put("type", "function"); putJsonObject("function") {
                            put("name", "next_phone_action"); put("arguments", args)
                        }
                    }) }
                } }) }
            }
            respond(response.toString(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val provider = RemoteModelProvider("OpenRouter", "https://test.invalid", "test-key", "test-model", clientOverride = client)
        try {
            val agent = BusinessAgent(name = "Shop", instructions = "Read orders", tools = emptySet(), provider = "openrouter", cloudConsent = true)
            val run = BusinessRun(agentId = agent.id, goal = "Read orders.csv")
            val result = WorkflowModelBrain { provider }.decide(agent, run.frames.first(), run, emptyList(), emptyList())
            assertEquals(2, calls)
            assertEquals("blocked", result.action)
        } finally { provider.close() }
    }

    @Test fun unsupportedActionsAndMissingRequiredFieldsAreRejected() {
        for (raw in listOf("""{"action":"read_file"}""", """{"action":"tool"}""", """{"action":"delegate","agentId":"x"}""", """{"action":"finish"}"""))
            assertTrue(raw, runCatching { WorkflowModelBrain.parse(raw) }.isFailure)
    }

    @Test fun completionUsesOnlySuccessfulEvidenceFromTheCurrentFrame() {
        val agent = BusinessAgent(id="child", name="Stock", instructions="Read stock", tools=setOf("files.read"), completionTools=setOf("files.read"))
        val steps = listOf(
            WorkStep(id="s1", agentId="parent", tool="files.read", args=emptyMap(), status="SUCCEEDED"),
            WorkStep(id="s2", agentId="child", tool="files.read", args=emptyMap(), status="FAILED"),
            WorkStep(id="s3", agentId="child", tool="files.read", args=emptyMap(), status="SUCCEEDED"))
        val run = BusinessRun(agentId="parent", goal="Team report", steps=steps)
        val frame = WorkFrame("child", "Read stock", 1)
        for (ids in listOf(emptyList(), listOf("s1"), listOf("s2"), listOf("invented"))) {
            assertTrue(runCatching { WorkflowModelBrain.validateProposal(WorkDecision("finish", summary="Done", evidence=ids),
                agent, frame, run, emptyList(), emptySet()) }.isFailure)
        }
        WorkflowModelBrain.validateProposal(WorkDecision("finish", summary="Observed stock", evidence=listOf("s3")),
            agent, frame, run, emptyList(), emptySet())
        assertTrue(runCatching { WorkflowModelBrain.validateProposal(WorkDecision("finish", summary="Done", evidence=listOf("s1")),
            agent, frame.copy(startedAtStep=0), run.copy(steps=steps.take(1)), emptyList(), emptySet()) }.isFailure)
    }

    @Test fun modelRepairRejectsInventedEvidenceBeforeReturningAProposal() = runTest {
        var calls = 0
        val provider = object : ModelProvider {
            override val name = "Evidence fixture"
            override fun generateStreaming(prompt: String) = emptyFlow<ModelResponse>()
            override suspend fun generate(prompt: String): ModelResponse {
                calls++
                return ModelResponse.Complete("""{"action":"finish","summary":"Stock read","evidence":["${if(calls==1) "invented" else "s1"}"]}""")
            }
        }
        val agent = BusinessAgent(id="stock", name="Stock", instructions="Read stock", tools=setOf("files.read"), completionTools=setOf("files.read"))
        val run = BusinessRun(agentId=agent.id, goal="Read stock.txt", steps=listOf(
            WorkStep(id="s1", agentId=agent.id, tool="files.read", args=emptyMap(), status="SUCCEEDED", output="2 rolls")))
        val decision = WorkflowModelBrain { provider }.decide(agent, run.frames.first(), run, emptyList(), emptyList())
        assertEquals(2, calls)
        assertEquals(listOf("s1"), decision.evidence)
    }

    @Test fun reasoningBudgetAndTruncatedResponsesCannotExecute() {
        val body = RemoteModelProvider.toolDecisionBody("test", "test", """{"type":"object"}""")
        assertEquals(4096, body["max_tokens"]!!.jsonPrimitive.int)
        var validated = false
        val response = """{"choices":[{"finish_reason":"length","message":{"content":"{\"action\":\"finish\"}"}}]}"""
        val parsed = RemoteModelProvider.parseToolDecision(response) { validated=true; true }
        assertTrue(parsed is ModelResponse.Error)
        assertFalse("A truncated response reached action validation", validated)
    }
}
