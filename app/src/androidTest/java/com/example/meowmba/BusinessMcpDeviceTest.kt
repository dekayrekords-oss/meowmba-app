package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.workflows.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** A synthetic CRM exists only on this phone's loopback interface. The real
 * local model, MCP transport and policy engine are used. No personal account,
 * external service or actual customer data is involved. */
class BusinessMcpDeviceTest {
    private fun report(text: String) = InstrumentationRegistry.getInstrumentation().sendStatus(0,
        android.os.Bundle().apply { putString("stream", "$text\n") })

    @Test fun localAgentReadsMcpAndWritesOnlyApprovedReport(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = WorkflowRuntime.get(context)
        runtime.mutex.withLock {
            FixtureCrm().use { crm ->
                try {
                    withTimeout(240_000) {
                        while (!runtime.coolEnough()) { report("Waiting for the phone to cool before MCP acceptance"); delay(15_000) }
                    }
                    val endpoint = "http://127.0.0.1:${crm.port}/mcp"
                    val specs = WorkflowMcpClient(endpoint, FixtureCrm.TOKEN).listTools()
                    assertEquals(listOf("ticket_lookup"), specs.map { it.name })
                    assertEquals(0, crm.calls.get())
                    val connection = BusinessMcpConnection(name = "Synthetic CRM", endpoint = endpoint, tools = specs)
                    val toolId = "mcp.${connection.id}.ticket_lookup"
                    val folder = File(context.filesDir, "workflow-tests/mcp-${UUID.randomUUID()}").also { it.mkdirs() }
                    val checkpoint = File(folder, "state.json")
                    var store = WorkflowStore(checkpoint)
                    val workspace = File(folder, "business").also { it.mkdirs() }
                    val registry = WorkflowTools.local(workspace)
                    WorkflowMcpTools.register(registry, listOf(connection), { connection }, { FixtureCrm.TOKEN })
                    val agent = BusinessAgent(name = "Support reporter", instructions =
                        "Look up the requested ticket in the connected CRM. Then save the requested report with its actual ticket ID, owner and status. Never invent an owner or claim a file was written without tool evidence.",
                        tools = setOf(toolId, "files.write"), completionTools = setOf(toolId, "files.write"))
                    store.saveAgent(agent)
                    val id = store.enqueue(agent.id, "Look up ticket QA-73 in Synthetic CRM, then save reports/qa-73.txt with its owner and status.")
                    withTimeout(240_000) {
                        while (store.run(id)!!.status !in WorkflowStore.TERMINAL) {
                            WorkflowEngine(store, registry, runtime.brain, runtime::coolEnough).tick(id)
                            store = WorkflowStore(checkpoint)
                            val run = store.run(id)!!
                            report("MCP job: ${run.status}; ${run.note.take(600)}")
                            if (run.status == "APPROVAL") {
                                val step = run.steps.last()
                                when (step.tool) {
                                    toolId -> {
                                        assertEquals(0, crm.calls.get())
                                        assertEquals("QA-73", WorkflowMcpArguments.decode(specs.single().schema, step.args).getString("ticket_id"))
                                    }
                                    "files.write" -> {
                                        assertEquals(1, crm.calls.get())
                                        assertEquals("reports/qa-73.txt", step.args["path"])
                                        assertTrue(step.args["content"].orEmpty().contains("Alex Fixture"))
                                        assertTrue(step.args["content"].orEmpty().contains("waiting", ignoreCase = true))
                                        assertFalse(File(workspace, "reports/qa-73.txt").exists())
                                    }
                                    else -> error("Test did not authorize this action")
                                }
                                store.approve(id, step.id, true) // this fixture only
                            }
                            if (run.status == "PAUSED") delay(15_000)
                        }
                    }
                    val run = store.run(id)!!
                    assertEquals(run.note, "COMPLETE", run.status)
                    assertEquals(1, crm.calls.get())
                    assertEquals(1, run.steps.count { it.tool == "files.write" && it.status == "SUCCEEDED" })
                    assertTrue(File(workspace, "reports/qa-73.txt").readText().contains("Alex Fixture"))
                    assertTrue(crm.errors.toString(), crm.errors.isEmpty())
                    report("PASS actual local model -> approved MCP call -> SSE evidence -> approved verified file; checkpoint=$checkpoint")
                } finally { runtime.releaseModel() }
            }
        }
    }

    private class FixtureCrm : java.io.Closeable {
        private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val port get() = server.localPort
        val calls = AtomicInteger()
        val errors = CopyOnWriteArrayList<String>()
        private val worker = thread(isDaemon = true, name = "synthetic-crm") {
            while (!server.isClosed) try {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader()
                    check(reader.readLine() == "POST /mcp HTTP/1.1")
                    val headers = mutableMapOf<String, String>()
                    while (true) {
                        val line = reader.readLine() ?: error("Missing HTTP headers")
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                    }
                    check(headers["authorization"] == "Bearer $TOKEN")
                    val length = headers["content-length"]!!.toInt().also { check(it in 1..20_000) }
                    val chars = CharArray(length)
                    var offset = 0
                    while (offset < length) { val n = reader.read(chars, offset, length - offset); check(n > 0); offset += n }
                    val request = JSONObject(String(chars))
                    val method = request.getString("method")
                    if (method != "initialize") {
                        check(headers["mcp-session-id"] == "fixture-session")
                        check(headers["mcp-protocol-version"] == "2025-06-18")
                    }
                    val result = when (method) {
                        "initialize" -> JSONObject("""{"protocolVersion":"2025-06-18","capabilities":{"tools":{}}}""")
                        "notifications/initialized" -> null
                        "tools/list" -> JSONObject().put("tools", JSONArray().put(JSONObject("""{"name":"ticket_lookup","description":"Read a ticket's actual owner and status. Supply ticket_id.","inputSchema":{"type":"object","properties":{"ticket_id":{"type":"string"}},"required":["ticket_id"],"additionalProperties":false},"annotations":{"readOnlyHint":true}}""")))
                        "tools/call" -> {
                            val params = request.getJSONObject("params")
                            check(params.getString("name") == "ticket_lookup")
                            check(params.getJSONObject("arguments").getString("ticket_id") == "QA-73")
                            calls.incrementAndGet()
                            JSONObject("""{"content":[{"type":"text","text":"Ticket QA-73. Owner: Alex Fixture. Status: waiting."}],"structuredContent":{"ticket_id":"QA-73","owner":"Alex Fixture","status":"waiting"}}""")
                        }
                        else -> error("Unexpected method: $method")
                    }
                    val response = result?.let { JSONObject().put("jsonrpc", "2.0").put("id", request.getInt("id")).put("result", it).toString() }.orEmpty()
                    val sse = method == "tools/call"
                    val body = (if (sse) ": heartbeat\n\nevent: message\ndata: $response\n\n" else response).toByteArray()
                    socket.getOutputStream().use { out ->
                        out.write(("HTTP/1.1 ${if (result == null) "202 Accepted" else "200 OK"}\r\nContent-Type: ${if (sse) "text/event-stream" else "application/json"}\r\nContent-Length: ${body.size}\r\nConnection: close\r\n" +
                            (if (method == "initialize") "Mcp-Session-Id: fixture-session\r\n" else "") + "\r\n").toByteArray())
                        out.write(body)
                    }
                }
            } catch (e: Exception) { if (!server.isClosed) errors += e.toString() }
        }
        override fun close() { server.close(); worker.join(1000) }
        companion object { const val TOKEN = "synthetic-test-only-not-a-secret" }
    }
}
