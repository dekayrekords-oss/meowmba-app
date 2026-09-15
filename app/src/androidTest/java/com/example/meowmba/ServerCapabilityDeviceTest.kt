package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agentic.*
import com.example.meowmba.core.browser.OffscreenBrowser
import com.example.meowmba.core.neural.*
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.workflows.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Explicit owner acceptance. Real transports/model; all task data is synthetic. */
class ServerCapabilityDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun browserReadsInternetAndSubmitsTestForm() = runBlocking {
        val browser = OffscreenBrowser(context)
        val receipt = AtomicReference("")
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val server = Thread {
            while (!socket.isClosed) {
                try { socket.accept().use { client ->
                    client.soTimeout = 5000
                    val reader = client.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    var length = 0
                    while (true) {
                        val line = reader.readLine().orEmpty()
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                    }
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) { val n = reader.read(body, read, length-read); if (n < 0) break; read += n }
                    if (request.startsWith("POST /quote ")) receipt.set(String(body))
                    val page = if (request.startsWith("POST")) "<html><body><h1>Quote saved</h1><p>TEST-QUOTE-42: 12 stickers requested</p></body></html>"
                        else "<html><head><title>Test shop quote</title></head><body><h1>Sample print shop</h1><p>Request a quote</p><form method='post' action='/quote'><input id='item' name='item'><input id='quantity' name='quantity'><button id='submit' type='submit'>Save quote</button></form></body></html>"
                    val bytes = page.toByteArray()
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                    client.getOutputStream().write(bytes)
                } } catch (e: Exception) { if (!socket.isClosed) receipt.set("ERROR:${e.javaClass.simpleName}") }
            }
        }.also { it.isDaemon = true; it.start() }
        try {
            withTimeout(60_000) {
                val loaded = browser.navigate("https://example.com")
                assertFalse(loaded, loaded.startsWith("error:"))
                assertTrue(browser.readText().contains("Example Domain"))
                assertTrue(browser.links().contains("iana.org"))
                val fixture = "http://127.0.0.1:${socket.localPort}/"
                assertFalse(browser.navigate(fixture).startsWith("error:"))
                browser.type("#item", "stickers")
                browser.type("#quantity", "12")
                browser.click("#submit")
                var observed = ""
                repeat(40) { if (!observed.contains("TEST-QUOTE-42")) { delay(100); observed = browser.readText() } }
                assertTrue(observed, observed.contains("TEST-QUOTE-42"))
                assertEquals("item=stickers&quantity=12", receipt.get())
                File(context.getExternalFilesDir(null), "browser-live.json").writeText(JSONObject()
                    .put("internetRead", "https://example.com").put("linkVerified", "iana.org")
                    .put("formPosted", receipt.get()).put("receiptRead", "TEST-QUOTE-42")
                    .put("scope", "Production Android WebView; internet read plus local synthetic form; no real booking or message")
                    .toString(2))
            }
        } finally {
            withContext(Dispatchers.Main) { browser.detach() }
            socket.close(); server.join(1000)
        }
    }

    @Test fun sshReadsActualMeowRepository() {
        val key = File(context.filesDir, "computer120-test-key")
        assertTrue("Provision the owner-authorized temporary SSH key first", key.isFile)
        val profiles = InMemoryConnectionProfileStore()
        val fingerprint = File(context.filesDir, "meow-ssh-fingerprint.txt").readText().trim()
        val host = InstrumentationRegistry.getArguments().getString("meowHost") ?: "100.77.97.236"
        profiles.save(ConnectionProfile(id="server-acceptance", displayName="Meow verification", type=ConnectionProfileType.SSH,
            host=host, port=22, username="prog", credentialRef="temporary", hostKeyFingerprint=fingerprint,
            pinHostKeyOnFirstConnect=false))
        val executor = DefaultAgenticSshExecutor({ true }, profiles, object : CredentialResolver {
            override fun resolve(credentialRef: String) = key.readText()
        })
        val result = executor.exec("server-acceptance", listOf("head", "-n", "12", "/home/prog/meowmba-repair-20260914/AGENTS.md"), 15_000)
        assertTrue(result.toString(), result is SshExecResult.Ok)
        result as SshExecResult.Ok
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Meowmba — guide for AI contributors"))
        profiles.save(profiles.get("server-acceptance")!!.copy(hostKeyFingerprint="SHA256:wrong"))
        assertTrue(executor.exec("server-acceptance", listOf("true"), 5000) is SshExecResult.Error)
        File(context.getExternalFilesDir(null), "ssh-live.json").writeText(JSONObject()
            .put("host", host).put("file", "/home/prog/meowmba-repair-20260914/AGENTS.md")
            .put("readVerified", true).put("wrongHostKeyRejected", true).put("transport", "Phone SSH client, private network")
            .toString(2))
    }

    @Test fun pairAndVerifyFullNeuralServer() = runBlocking {
        val storage = SecureStorageManager(context)
        val manager = MaleCnsServerManager(storage)
        val payloadFile = File(context.filesDir, "neural-pairing-payload.txt")
        try {
            assertTrue("Provision a fresh pairing payload first", payloadFile.isFile)
            assertTrue(manager.pairServer(payloadFile.readText().trim(), "Fold7 acceptance").isSuccess)
            assertTrue(manager.testConnection().isSuccess)
            val transport = manager.createTransport()
            val session = UUID.randomUUID().toString()
            var feed = 0f; var groom = 0f
            try {
                repeat(20) { i ->
                    val value = transport.step(MaleCnsRequest(session, i+1L, 1f, 1f, 0f, 0f, 0f, 0f,
                        MaleCnsGraphProfile.FULL)).getOrThrow()
                    feed = maxOf(feed, value.feedHz); groom = maxOf(groom, value.groomHz)
                    delay(400)
                }
                assertTrue("No feeding response", feed > 0)
                assertTrue("No grooming response", groom > 0)
            } finally { transport.stop(session) }
            File(context.getExternalFilesDir(null), "neural-server-live.json").writeText(JSONObject()
                .put("endpoint", manager.selection().endpoint).put("fullGraphVerified", true)
                .put("neurons", 176422).put("feedMaxHz", feed).put("groomMaxHz", groom)
                .put("transport", "Phone pinned TLS and HMAC, private network").toString(2))
        } finally { payloadFile.delete() }
    }

    @Test fun realManagerDelegatesToTwoSpecialists() = runBlocking<Unit> {
        val runtime = WorkflowRuntime.get(context)
        val storage = SecureStorageManager(context)
        val model = InstrumentationRegistry.getArguments().getString("plannerModel")
            ?: storage.getModelName("openrouter") ?: "openrouter/free"
        require(model == "openrouter/free" || model.endsWith(":free"))
        assertTrue(!storage.getApiKey("openrouter").isNullOrBlank())
        assertTrue("Cool the phone before running the planner", runtime.coolEnough())
        val root = File(context.getExternalFilesDir(null), "team-live-${UUID.randomUUID()}").also { it.mkdirs() }
        val workspace = File(root, "workspace").also { it.mkdirs() }
        File(workspace, "orders.csv").writeText("order,amount,status\nA101,21.50,unpaid\nA102,40.00,paid\nA103,35.25,unpaid\n")
        File(workspace, "stock.txt").writeText("Sticker rolls: 2 available; minimum target 5. Shipping boxes: 20 available; minimum target 10.\n")
        val store = WorkflowStore(File(root, "state.json"))
        val accountant = BusinessAgent(id="accounts", name="Accounts specialist", provider="openrouter", model=model, cloudConsent=true,
            instructions="Read orders.csv, then use files.csv_summary to calculate the exact unpaid amount. Return the total and unpaid IDs with evidence. Do not delegate or write files.",
            tools=setOf("files.read", "files.csv_summary"), completionTools=setOf("files.read", "files.csv_summary"))
        val stock = BusinessAgent(id="stock", name="Stock specialist", provider="openrouter", model=model, cloudConsent=true,
            instructions="Read stock.txt, calculate shortages relative to minimum targets, then finish with your actual findings and that evidence. Do not delegate or write files.",
            tools=setOf("files.read"), completionTools=setOf("files.read"))
        val manager = BusinessAgent(id="manager", name="Shop manager", provider="openrouter", model=model, cloudConsent=true,
            instructions="Delegate accounts analysis to the Accounts specialist and stock analysis to the Stock specialist, exactly once each. After BOTH return, combine their actual results in reports/team.txt. Read the saved report, then finish with evidence. Do not perform their analysis yourself or contact anyone.",
            tools=setOf("files.write", "files.read"), delegates=setOf(accountant.id, stock.id), completionTools=setOf("files.write", "files.read"))
        listOf(accountant, stock, manager).forEach(store::saveAgent)
        val id = store.enqueue(manager.id, "Prepare my shop opening report using two specialists on one task. Accounts must review orders.csv for unpaid order IDs and the exact unpaid total. Stock must review stock.txt and report what is below minimum and by how much. Combine BOTH specialists' findings into reports/team.txt and read it to verify. Do not order stock, send messages or make payments.")
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            runtime.mutex.withLock { withTimeout(360_000) {
                val engine = WorkflowEngine(store, WorkflowTools.local(workspace), runtime.brain, runtime::coolEnough)
                var transitions = 0
                while (transitions < 38) {
                    if (store.run(id)!!.status in WorkflowStore.TERMINAL) return@withTimeout
                    // Cooling is not a workflow transition. A thermal event
                    // arriving just after preflight must not burn the test's
                    // entire transition allowance in an immediate busy loop.
                    if (!runtime.coolEnough()) { delay(2000); continue }
                    engine.tick(id)
                    val run = store.run(id)!!
                    if (run.status == "PAUSED") { delay(2000); continue }
                    transitions++
                    if (run.status == "APPROVAL") {
                        val step = run.steps.last()
                        assertEquals("files.write", step.tool); assertEquals("reports/team.txt", step.args["path"])
                        store.approve(id, step.id, true)
                    }
                }
            } }
            val result = store.run(id)!!
            assertEquals("Inspect saved state for real planner evidence", "COMPLETE", result.status)
            for (member in listOf(accountant, stock)) {
                assertTrue("Missing specialist evidence: ${member.name}", result.steps.any {
                    it.agentId == member.id && it.tool == "agent.result" && it.status == "SUCCEEDED" })
            }
            val report = File(workspace, "reports/team.txt").readText()
            assertTrue(report, report.contains("56.75"))
            assertTrue(report, report.contains("3") && report.contains("sticker", ignoreCase=true))
        } finally {
            runtime.releaseModel()
            File(root, "result.json").writeText(Json.encodeToString(mapOf("status" to store.run(id)!!.status, "model" to model,
                "elapsedMs" to (android.os.SystemClock.elapsedRealtime()-started).toString(), "execution" to "Three model-driven roles; sequential shared worker")))
            println("TEAM_LIVE evidence=${root.name} status=${store.run(id)!!.status}")
        }
    }
}
