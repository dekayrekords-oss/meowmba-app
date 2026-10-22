package com.example.meowmba

import com.example.meowmba.core.agent.*
import com.example.meowmba.core.agent.tools.LogicSessionTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProviderConnectionsTest {
    @Test fun openaiCatalogUsesAccountKeyAndNormalizesVersionSuffix() = runTest {
        val client = HttpClient(MockEngine { r ->
            assertEquals("https://api.openai.com/v1/models", r.url.toString())
            assertEquals("Bearer test-secret", r.headers[HttpHeaders.Authorization])
            respond("""{"data":[{"id":"gpt-4o-mini"},{"id":"gpt-transcribe"},{"id":"text-embedding-3-small"}]}""")
        })
        try { assertEquals(listOf("gpt-4o-mini"), ProviderModels.fetch("openai", "https://api.openai.com/v1/", "test-secret", clientOverride = client).getOrThrow().map { it.id }) }
        finally { client.close() }
    }
    @Test fun anthropicDiscoveryPaginatesWithCorrectHeaders() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { r ->
            assertEquals("test-secret", r.headers["x-api-key"])
            assertEquals("2023-06-01", r.headers["anthropic-version"])
            assertNull(r.headers[HttpHeaders.Authorization])
            calls++
            if (calls == 1) respond("""{"data":[{"id":"claude-a","display_name":"A"}],"has_more":true,"last_id":"claude-a"}""")
            else { assertEquals("claude-a", r.url.parameters["after_id"]); respond("""{"data":[{"id":"claude-b"}],"has_more":false}""") }
        })
        try { assertEquals(2, ProviderModels.fetch("anthropic", "", "test-secret", clientOverride = client).getOrThrow().size) }
        finally { client.close() }
    }
    @Test fun failedCatalogDoesNotConsumeErrorBodyAsModelsOrExposeIt() = runTest {
        val client = HttpClient(MockEngine { respond("private key and account info", HttpStatusCode.Unauthorized) })
        try {
            val error = ProviderModels.fetch("openai", "", "test-secret", clientOverride = client).exceptionOrNull()!!
            assertTrue(error.message!!.contains("401")); assertFalse(error.message!!.contains("private"))
        } finally { client.close() }
    }
    @Test fun catalogCancellationIsNotConvertedToAnError() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("stop") })
        try {
            try { ProviderModels.fetch("openai", "", "test-secret", clientOverride = client); fail("Cancellation swallowed") }
            catch (_: CancellationException) { }
        } finally { client.close() }
    }
    @Test fun invalidEndpointNeverGetsCredentials() = runTest {
        var called = false
        val client = HttpClient(MockEngine { called = true; respond("{}") })
        try {
            for (url in listOf("", "http://insecure.example", "https://user:pass@example.com", "https://example.com?key=secret", "file:///tmp/test"))
                assertTrue(ProviderModels.fetch("custom", url, "test-secret", clientOverride = client).isFailure)
            assertFalse(called)
        } finally { client.close() }
    }
    @Test fun claudeStreamsTextAndFinishesOnlyOnMessageStop() = runTest {
        val client = HttpClient(MockEngine { r ->
            assertEquals("/v1/messages", r.url.encodedPath)
            assertEquals("test-secret", r.headers["x-api-key"])
            val body = Json.parseToJsonElement((r.body as TextContent).text).jsonObject
            assertEquals(4096, body["max_tokens"]!!.jsonPrimitive.int)
            respond("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n\ndata: {\"type\":\"message_stop\"}\n\n")
        })
        RemoteModelProvider("Claude", "https://api.anthropic.com/v1", "test-secret", "test-model", RemoteModelProvider.ApiStyle.ANTHROPIC, client).use {
            assertEquals(ModelResponse.Complete("Hello"), it.generate("Hi"))
        }
    }
    @Test fun claudeInterruptedStreamIsNotReportedAsSuccess() = runTest {
        for (end in listOf("", "data: {\"type\":\"error\"}\n", "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}\n")) {
            val client = HttpClient(MockEngine { respond("data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"partial\"}}\n$end") })
            RemoteModelProvider("Claude", "https://fixture.invalid", "test-secret", "test-model", RemoteModelProvider.ApiStyle.ANTHROPIC, client).use {
                assertTrue(it.generate("Hi") is ModelResponse.Error)
            }
        }
    }
    @Test fun claudeControllerReceivesRealSchemaAndValidatesReply() = runTest {
        val client = HttpClient(MockEngine { r ->
            val body = Json.parseToJsonElement((r.body as TextContent).text).jsonObject
            assertEquals(Json.parseToJsonElement(LogicSessionTool.ACTION_SCHEMA), body["tools"]!!.jsonArray[0].jsonObject["input_schema"])
            assertTrue(body["tool_choice"]!!.jsonObject["disable_parallel_tool_use"]!!.jsonPrimitive.boolean)
            respond("""{"stop_reason":"tool_use","content":[{"type":"tool_use","name":"next_phone_action","input":{"action":"controls"}}]}""")
        }) { install(HttpTimeout) }
        RemoteModelProvider("Custom", "https://fixture.invalid", "test-secret", "test-model", RemoteModelProvider.ApiStyle.ANTHROPIC, client).use {
            assertEquals(ModelResponse.Complete("""{"action":"controls"}"""), it.generateToolDecision("Inspect Logic", LogicSessionTool.ACTION_SCHEMA, LogicSessionTool::validDecision))
        }
    }
    @Test fun claudeRejectsMultipleOrUnvalidatedControllerCalls() {
        val call = """{"type":"tool_use","name":"next_phone_action","input":{"action":"shell"}}"""
        for (calls in listOf(call, "$call,$call")) assertTrue(RemoteModelProvider.parseAnthropicDecision("""{"stop_reason":"tool_use","content":[$calls]}""", LogicSessionTool::validDecision) is ModelResponse.Error)
    }
    @Test fun zenListsOnlyImplementedProtocolFamilies() {
        assertTrue(ProviderModels.zenCompatible("big-pickle", false))
        assertTrue(ProviderModels.zenCompatible("claude-sonnet-test", true))
        assertFalse(ProviderModels.zenCompatible("claude-sonnet-test", false))
        assertFalse(ProviderModels.zenCompatible("gpt-5-test", false))
        assertFalse(ProviderModels.zenCompatible("gemini-test", false))
    }
}
