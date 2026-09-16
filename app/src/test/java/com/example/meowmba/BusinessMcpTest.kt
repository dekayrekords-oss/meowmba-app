package com.example.meowmba

import com.example.meowmba.core.workflows.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class BusinessMcpTest {
    private val schema = """{"type":"object","properties":{"limit":{"type":"integer"}},"required":["limit"]}"""
    private fun tool(name: String) = JSONObject().put("name", name).put("description", "Read sample orders").put("inputSchema", JSONObject(schema))

    @Test fun namedArgumentsPreserveServerTypesAndNestedValues() {
        val typed = """{"type":"object","properties":{"code":{"type":"string"},"limit":{"type":"integer"},"enabled":{"type":"boolean"},"filters":{"type":"object","properties":{"status":{"type":"string","enum":["open"]}},"required":["status"]}},"required":["code","limit"],"additionalProperties":false}"""
        val args = mapOf("code" to "0012", "limit" to "3", "enabled" to "false", "filters" to "{\"status\":\"open\"}")
        val decoded = WorkflowMcpArguments.decode(typed, args)
        assertEquals("0012", decoded.get("code"))
        assertEquals(3, decoded.getInt("limit"))
        assertEquals(false, decoded.get("enabled"))
        assertEquals("open", decoded.getJSONObject("filters").getString("status"))
        assertEquals("integer", WorkflowMcpArguments.describe(typed)["limit"])
        assertTrue(runCatching { WorkflowMcpArguments.decode(typed, args + ("limit" to "three")) }.isFailure)
        assertTrue(runCatching { WorkflowMcpArguments.decode(typed, args + ("filters" to "{\"status\":\"closed\"}")) }.isFailure)
        assertTrue(runCatching { WorkflowMcpArguments.decode(typed, args + ("invented" to "x")) }.isFailure)
    }

    @Test fun jsonEnvelopeIsStrictAndDoesNotStealNativeArgumentsField() {
        assertEquals(3, WorkflowMcpArguments.decode(schema, mapOf("arguments" to "{\"limit\":3}")).getInt("limit"))
        assertTrue(runCatching { WorkflowMcpArguments.decode(schema, mapOf("arguments" to "{\"limit\":\"3\"}")) }.isFailure)
        assertTrue(runCatching { WorkflowMcpArguments.decode(schema, mapOf("arguments" to "{\"limit\":3} garbage")) }.isFailure)
        val native = """{"type":"object","properties":{"arguments":{"type":"string"}},"required":["arguments"]}"""
        assertEquals("text", WorkflowMcpArguments.decode(native, mapOf("arguments" to "text")).getString("arguments"))
    }

    @Test fun invalidServiceArgumentsAreCorrectedBeforeApprovalWithoutNetwork() = runBlocking {
        val c = BusinessMcpConnection(name = "Test", endpoint = "https://example.com/mcp", tools = listOf(BusinessMcpTool("orders.read", "Read", schema)))
        val registry = com.example.meowmba.core.agent.tools.ToolRegistry()
        WorkflowMcpTools.register(registry, listOf(c), { c }, { error("Preflight must not load credentials or contact a server") })
        val store = WorkflowStore(java.io.File(java.nio.file.Files.createTempDirectory("mcp-preflight").toFile(), "state.json"))
        val id = "mcp.${c.id}.orders.read"
        val agent = BusinessAgent(name = "Reader", instructions = "Read orders", tools = setOf(id))
        store.saveAgent(agent)
        val run = store.enqueue(agent.id, "Read three orders")
        WorkflowEngine(store, registry, WorkflowBrain { _, _, _, _, _ -> WorkDecision("tool", id, mapOf("limit" to "three")) }).tick(run)
        assertEquals("QUEUED", store.run(run)!!.status)
        assertEquals("REJECTED", store.run(run)!!.steps.single().status)
        WorkflowEngine(store, registry, WorkflowBrain { _, _, _, _, _ -> WorkDecision("tool", id, mapOf("limit" to "3")) }).tick(run)
        assertEquals("APPROVAL", store.run(run)!!.status)
        assertEquals(mapOf("limit" to "3"), store.run(run)!!.steps.last().args)
    }

    @Test fun novemberHandshakeVersionIsNegotiatedAndSent() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            try {
                val body = JSONObject(exchange.requestBody.bufferedReader().readText())
                val method = body.getString("method")
                if (method == "initialize") assertEquals("2025-11-25", body.getJSONObject("params").getString("protocolVersion"))
                else assertEquals("2025-11-25", exchange.requestHeaders.getFirst("MCP-Protocol-Version"))
                if (method == "notifications/initialized") exchange.sendResponseHeaders(202, -1)
                else {
                    val result = if (method == "initialize") JSONObject("""{"protocolVersion":"2025-11-25","capabilities":{"tools":{}}}""") else JSONObject().put("tools", org.json.JSONArray().put(tool("orders.read")))
                    val bytes = JSONObject().put("jsonrpc", "2.0").put("id", body.getInt("id")).put("result", result).toString().toByteArray()
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
                }
            } catch (e: Throwable) { failures += e }
            finally { exchange.close() }
        }
        server.start()
        try {
            assertEquals(1, WorkflowMcpClient("http://127.0.0.1:${server.address.port}/mcp").listTools().size)
            assertTrue(failures.toString(), failures.isEmpty())
        } finally { server.stop(0) }
    }

    @Test fun actualHttpHandshakeSessionPaginationTypedCallAndSseResults() = runBlocking {
        val requests = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            try {
                assertEquals("Bearer test-only-token", exchange.requestHeaders.getFirst("Authorization"))
                val body = JSONObject(exchange.requestBody.bufferedReader().readText())
                val method = body.getString("method"); requests += method
                if (method != "initialize") {
                    assertEquals("test-session", exchange.requestHeaders.getFirst("Mcp-Session-Id"))
                    assertEquals("2025-06-18", exchange.requestHeaders.getFirst("MCP-Protocol-Version"))
                }
                if (method == "notifications/initialized") { exchange.sendResponseHeaders(202, -1); exchange.close() }
                else {
                    val result = when (method) {
                        "initialize" -> {
                            exchange.responseHeaders.set("Mcp-Session-Id", "test-session")
                            JSONObject("""{"protocolVersion":"2025-06-18","capabilities":{"tools":{}}}""")
                        }
                        "tools/list" -> if (body.getJSONObject("params").has("cursor")) JSONObject().put("tools", org.json.JSONArray().put(tool("orders.read")))
                            else JSONObject().put("tools", org.json.JSONArray().put(tool("orders.list"))).put("nextCursor", "page-2")
                        "tools/call" -> {
                            assertEquals(3, body.getJSONObject("params").getJSONObject("arguments").getInt("limit"))
                            JSONObject("""{"content":[{"type":"text","text":"first"},{"type":"text","text":"second"}],"structuredContent":{"count":3}}""")
                        }
                        else -> error("unexpected method")
                    }
                    val response = JSONObject().put("jsonrpc", "2.0").put("id", body.getInt("id")).put("result", result).toString()
                    val sse = method == "tools/call"
                    val wire = if (sse) ": heartbeat\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/message\"}\n\nid: event-1\nevent: message\ndata: $response\n\n" else response
                    exchange.responseHeaders.set("Content-Type", if (sse) "text/event-stream" else "application/json")
                    val bytes = wire.toByteArray(); exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            } catch (e: Throwable) { failures += e; exchange.close() }
        }
        server.start()
        try {
            val client = WorkflowMcpClient("http://127.0.0.1:${server.address.port}/mcp", "test-only-token")
            assertEquals(listOf("orders.list", "orders.read"), client.listTools().map { it.name })
            val result = client.call("orders.list", JSONObject().put("limit", 3))
            assertTrue(result.contains("first")); assertTrue(result.contains("second")); assertTrue(result.contains("\"count\":3"))
            assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/list", "tools/call"), requests)
            assertTrue(failures.toString(), failures.isEmpty())
        } finally { server.stop(0) }
    }

    @Test fun authorizationErrorIsNotSuccessfulConnection() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { it.sendResponseHeaders(401, -1); it.close() }
        server.start()
        try {
            val result = runCatching { WorkflowMcpClient("http://127.0.0.1:${server.address.port}/mcp").listTools() }
            assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("authorization denied"))
        } finally { server.stop(0) }
    }

    @Test fun redirectsNeverForwardBearerToken() = runBlocking {
        var redirectedCalls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { it.responseHeaders.set("Location", "/other"); it.sendResponseHeaders(302, -1); it.close() }
        server.createContext("/other") { redirectedCalls++; it.sendResponseHeaders(401, -1); it.close() }
        server.start()
        try {
            assertTrue(runCatching { WorkflowMcpClient("http://127.0.0.1:${server.address.port}/mcp", "do-not-forward").listTools() }.isFailure)
            assertEquals(0, redirectedCalls)
        } finally { server.stop(0) }
    }

    @Test fun unsafeEndpointsAndHeaderInjectionRejected() {
        for (url in listOf("https://user:password@example.com/mcp", "https://example.com/mcp?token=secret", "http://example.com/mcp", "file:///tmp/server", "http://192.168.evil.example/mcp")) {
            assertTrue(url, runCatching { WorkflowMcpClient.validateEndpoint(url, true) }.isFailure)
        }
        assertTrue(runCatching { WorkflowMcpClient("https://example.com/mcp", "token\nheader") }.isFailure)
        WorkflowMcpClient.validateEndpoint("https://example.com/mcp")
        WorkflowMcpClient.validateEndpoint("http://192.168.1.69:8080/mcp", true)
    }
    @Test fun aServerReadOnlyHintDoesNotGrantAutomaticExecution() {
        val tool = BusinessMcpTool("orders.read", "Read orders", schema, readOnlyHint = true)
        val connection = BusinessMcpConnection(id = "test", name = "Test", endpoint = "https://example.com/mcp", tools = listOf(tool))
        fun registry(c: BusinessMcpConnection) = com.example.meowmba.core.agent.tools.ToolRegistry().also {
            WorkflowMcpTools.register(it, listOf(c), { c }, { "" })
        }
        assertEquals(com.example.meowmba.core.agent.tools.ConfirmationPolicy.ALWAYS, registry(connection).byId("mcp.test.orders.read")!!.definition.confirmationPolicy)
        assertEquals(com.example.meowmba.core.agent.tools.ConfirmationPolicy.NONE,
            registry(connection.copy(automaticReadTools = setOf("orders.read"))).byId("mcp.test.orders.read")!!.definition.confirmationPolicy)
        val notRead = connection.copy(tools = listOf(tool.copy(readOnlyHint = false)), automaticReadTools = setOf("orders.read"))
        assertEquals(com.example.meowmba.core.agent.tools.ConfirmationPolicy.ALWAYS, registry(notRead).byId("mcp.test.orders.read")!!.definition.confirmationPolicy)
    }
    @Test fun revokedConnectionCannotExecutePendingTool() = runBlocking {
        val c = BusinessMcpConnection(id = "test", name = "Test", endpoint = "https://example.com/mcp", tools = listOf(BusinessMcpTool("orders.read", "Read", schema)))
        val registry = com.example.meowmba.core.agent.tools.ToolRegistry()
        WorkflowMcpTools.register(registry, listOf(c), { null }, { error("Token must not be loaded after revocation") })
        assertTrue(registry.byId("mcp.test.orders.read")!!.execute(mapOf("arguments" to "{\"limit\":3}")) is com.example.meowmba.core.agent.tools.ToolResult.Failure)
    }
}
