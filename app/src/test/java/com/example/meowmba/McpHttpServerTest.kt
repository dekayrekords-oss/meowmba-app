package com.example.meowmba

import com.example.meowmba.core.mcp.McpHttpServer
import com.example.meowmba.core.mcp.PhoneMcpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Exercises the hosted MCP endpoint: the read-only [PhoneMcpServer] tool set
 * and the [McpHttpServer] HTTP transport together. Pure JVM — both classes use
 * only java.net + coroutines, so a real local socket round-trips a tools/list
 * and a tools/call exactly like Android Studio would.
 */
class McpHttpServerTest {

    private val scope = CoroutineScope(Dispatchers.Default)
    private lateinit var server: McpHttpServer

    @Before
    fun setUp() {
        server = McpHttpServer(phoneServer(), scope)
        assertTrue("server should bind", server.start())
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `device info is served over the LAN http url`() {
        val res = post(JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "tools/call")
            .put("params", JSONObject().put("name", "device.info").put("arguments", JSONObject())))
        assertTrue(res.contains("Pixel 9"))
        assertTrue(res.contains("Meowmba 1.0"))
    }

    @Test
    fun `tools list advertises every read-only tool`() {
        val res = post(JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 2)
            .put("method", "tools/list"))
        assertTrue(res.contains("device.info"))
        assertTrue(res.contains("device.battery"))
        assertTrue(res.contains("asr.status"))
        assertTrue(res.contains("memory.recall"))
        assertTrue(res.contains("audit.recent"))
        assertTrue(res.contains("tools.available"))
    }

    @Test
    fun `unknown tool returns an error result rather than throwing`() {
        val res = post(JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 3)
            .put("method", "tools/call")
            .put("params", JSONObject().put("name", "shell.exec").put("arguments", JSONObject())))
        // A remote caller cannot conjure a tool that does not exist; the
        // response is a well-formed result flagged isError, not a thrown error.
        assertTrue(res.contains("\"isError\":true"))
        assertTrue(res.contains("no such tool: shell.exec"))
    }

    private fun phoneServer() = PhoneMcpServer(
        appName = "Meowmba",
        appVersion = "1.0",
        deviceModel = "Google Pixel 9",
        androidVersion = "15",
        sdkInt = "35",
        batteryPercent = { 72 },
        board = "komodo",
        language = "en",
        asrBackend = { "Whisper (on-device)" },
        asrUsable = { true },
        registeredTools = { listOf("phone.battery", "memory.save") },
        memoryRecall = { listOf("user likes dark roast coffee") },
        auditRecent = { listOf("complete: replied to weather question") },
    )

    private fun retryRequest(body: String): HttpURLConnection? {
        repeat(20) {
            try {
                val conn = URL("http://127.0.0.1:${server.port}/mcp").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                return conn
            } catch (e: Exception) {
                Thread.sleep(50)
            }
        }
        return null
    }

    private fun post(json: JSONObject): String {
        val conn = retryRequest(json.toString()) ?: error("server not reachable")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
