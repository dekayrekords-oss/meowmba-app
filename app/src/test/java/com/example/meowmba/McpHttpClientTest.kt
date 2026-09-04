package com.example.meowmba

import com.example.meowmba.core.mcp.McpHttpClient
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Exercises the external-MCP wire client against a real in-JVM HTTP server so
 * we prove the JSON-RPC 2.0 handshake (initialize → tools/list → tools/call)
 * works over streamable-HTTP transport.
 */
class McpHttpClientTest {

    private lateinit var server: HttpServer
    private var url: String = ""

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val req = JSONObject(body)
            val resp: JSONObject = when (req.optString("method")) {
                "initialize" -> JSONObject().put("result",
                    JSONObject().put("protocolVersion", "2024-11-05").put("serverInfo", JSONObject()))
                "tools/list" -> JSONObject().put("result",
                    JSONObject().put("tools", org.json.JSONArray().put(
                        JSONObject().put("name", "read_logcat")
                            .put("description", "Reads logcat")
                            .put("inputSchema", JSONObject().put("type", "object").put("properties", JSONObject()))
                    )))
                "tools/call" -> JSONObject().put("result",
                    JSONObject().put("isError", false)
                        .put("content", org.json.JSONArray().put(
                            JSONObject().put("type", "text").put("text", "log line 1")
                        )))
                else -> JSONObject().put("error", JSONObject().put("code", -32601).put("message", "unknown"))
            }
            val out = resp.put("jsonrpc", "2.0").put("id", req.opt("id")).toString()
            val bytes = out.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}/mcp"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `handshake lists remote tools`() = runTest {
        val client = McpHttpClient(url)
        assertTrue(client.ping())
        val tools = client.listTools()
        assertEquals(listOf("read_logcat"), tools.map { it.name })
    }

    @Test
    fun `calls a remote tool and returns its text`() = runTest {
        val client = McpHttpClient(url)
        val result = client.callTool("read_logcat", JSONObject().put("lines", 10))
        assertTrue(!result.isError)
        assertEquals("log line 1", result.text)
    }
}
