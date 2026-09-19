package com.example.meowmba

import com.example.meowmba.core.computer.OpenCodeComputer
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OpenCodeComputerHttpTest {
    private fun serve(block: (OpenCodeComputer, MutableList<Pair<String, String>>) -> Unit) {
        val requests = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val rule = """[{"permission":"*","pattern":"*","action":"ask"}]"""
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            requests.add("$method $path" to body)
            val auth = exchange.requestHeaders.getFirst("Authorization")
            val code = if (auth == null) 401 else if (path.endsWith("prompt_async")) 204 else 200
            val answer = when {
                path == "/global/health" -> """{"healthy":true,"version":"test"}"""
                path == "/session" -> """{"id":"ses_test","permission":$rule}"""
                path == "/session/ses_test" -> """{"id":"ses_test","directory":"/root/MeowProjects","permission":$rule}"""
                path == "/permission" -> """[{"id":"per_test","sessionID":"ses_test","permission":"edit","patterns":["report.md"],"metadata":{}}]"""
                path == "/session/status" -> """{"ses_test":{"type":"busy"}}"""
                path.endsWith("/message") || path.endsWith("/todo") || path == "/question" -> "[]"
                else -> "true"
            }.toByteArray()
            exchange.sendResponseHeaders(code, if (code == 204) -1 else answer.size.toLong())
            if (code != 204) exchange.responseBody.use { it.write(answer) }
            exchange.close()
        }
        server.start()
        try { block(OpenCodeComputer("a-private-test-password", "http://127.0.0.1:${server.address.port}"), requests) }
        finally { server.stop(0) }
    }

    @Test fun createSendProgressApproveAndAbortUseRealHttp() = serve { api, requests -> runBlocking {
        val session = api.create("Test workspace")
        api.send(session, "Write a report")
        assertEquals("busy", api.snapshot(session).status)
        api.approve(session, "per_test", true)
        api.stop(session)
        val create = requests.first { it.first == "POST /session" }.second
        assertTrue(create.contains("\"action\":\"ask\""))
        val approval = requests.first { it.first == "POST /permission/per_test/reply" }.second
        assertEquals("once", Json.parseToJsonElement(approval).jsonObject["reply"]!!.jsonPrimitive.content)
        assertTrue(requests.any { it.first == "POST /session/ses_test/abort" })
    } }

    @Test fun staleApprovalNeverPosts() = serve { api, requests -> runBlocking {
        assertTrue(runCatching { api.approve("ses_other", "per_test", true) }.isFailure)
        assertFalse(requests.any { it.first.startsWith("POST") })
    } }

    @Test fun refusesRemoteCredentialDestination() {
        assertTrue(runCatching { OpenCodeComputer("secret", "http://example.com:4096") }.isFailure)
        assertTrue(runCatching { OpenCodeComputer("secret", "http://127.0.0.1:4096@evil.example:4096") }.isFailure)
    }
}
