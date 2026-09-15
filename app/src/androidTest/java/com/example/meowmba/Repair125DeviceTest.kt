package com.example.meowmba

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.core.agent.RemoteModelProvider
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Explicit device acceptance. HTTP is loopback-only with fixture credentials;
 * appearance preferences are restored and only this test's prop is removed. */
class Repair125DeviceTest {
    /** Opt-in because this calls the owner's configured model. Uses a synthetic
     * request, leaves the selected model/privacy alone, and removes only its prop. */
    @Test fun configuredModelInventsFishingProp() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveInvent") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = com.example.meowmba.core.security.SecureStorageManager(context)
        val props = GeneratedPropStore(File(context.filesDir, "companion_props"))
        val request = "A blue fishing rod with a red reel and curved silver line, three clear shapes. Acceptance ${System.currentTimeMillis()}"
        val report = org.json.JSONObject().put("request", request)
        val started = android.os.SystemClock.elapsedRealtime()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: CompanionViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[CompanionViewModel::class.java] }
            Thread.sleep(2_000)
            val before = vm.uiState.value
            try {
                report.put("selectedModel", before.selectedModel)
                    .put("openRouterModel", storage.getModelName("openrouter"))
                    .put("reasonLevel", InventReasonLevel.fromKey(storage.getPreference("invent_reason_level")).name)
                scenario.onActivity {
                    vm.inventProp(request, "held", "A compact fishing prop for the floating avatar",
                        InventReasonLevel.fromKey(storage.getPreference("invent_reason_level")))
                }
                val deadline = started + 125_000
                do { Thread.sleep(100) } while (vm.uiState.value.propInventGenerating && android.os.SystemClock.elapsedRealtime() < deadline)
                report.put("message", vm.uiState.value.propInventMessage)
                    .put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started)
                assertFalse("Invent did not release the UI", vm.uiState.value.propInventGenerating)
                assertFalse(vm.uiState.value.propInventMessage, vm.uiState.value.propInventError)
                val prop = props.find(request)
                assertNotNull("Successful invent must save actual geometry", prop)
                report.put("prop", org.json.JSONObject(GeneratedPropForms.toJson(prop!!)))
                scenario.onActivity {
                    vm.equipProp(prop.id)
                    vm.returnToAvatar()
                    assertEquals(prop, vm.uiState.value.equippedProp)
                }
                Thread.sleep(1_500)
                assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
                assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
                report.put("passed", true)
            } finally {
                scenario.onActivity {
                    vm.cancelPropInvent()
                    props.find(request)?.let { vm.deleteProp(it.id) }
                    vm.equipProp(before.equippedProp?.id)
                    vm.setAvatarStyle(before.avatarStyle)
                    before.generatedAvatarForm?.let { vm.selectGeneratedAvatarForm(it.id) }
                }
                File(context.getExternalFilesDir(null), "repair126-live-prop.json").writeText(report.toString(2))
            }
        }
    }

    @Test fun androidHttpCancellationAndTimeoutRecover() = runBlocking {
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val workers = Executors.newCachedThreadPool()
        val active = AtomicInteger()
        workers.submit {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: SocketException) { break }
                workers.submit {
                    active.incrementAndGet()
                    try {
                        socket.use {
                            it.soTimeout = 5_000
                            val input = it.getInputStream().bufferedReader()
                            var length = 0
                            while (true) {
                                val line = input.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                            }
                            val body = CharArray(length)
                            var read = 0
                            while (read < length) {
                                val n = input.read(body, read, length - read)
                                if (n < 0) error("Truncated request")
                                read += n
                            }
                            val output = it.getOutputStream()
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n".toByteArray())
                            fun chunk(value: String) {
                                val bytes = value.toByteArray()
                                output.write("${bytes.size.toString(16)}\r\n".toByteArray())
                                output.write(bytes); output.write("\r\n".toByteArray()); output.flush()
                            }
                            repeat(100) {
                                chunk("data: {\"choices\":[{\"delta\":{\"content\":\"hello \"}}]}\n\n")
                                Thread.sleep(10)
                            }
                            chunk("data: [DONE]\n\n")
                            output.write("0\r\n\r\n".toByteArray()); output.flush()
                        }
                    } catch (_: java.io.IOException) {
                        // Client cancellation closes the socket while the body is streaming.
                    } finally { active.decrementAndGet() }
                }
            }
        }
        val provider = RemoteModelProvider("Cancellation fixture", "http://127.0.0.1:${server.localPort}", "fixture", "fixture")
        try {
            repeat(10) {
                val received = CompletableDeferred<Unit>()
                val job = launch { provider.generateStreaming("cancel").collect {
                    if (it is ModelResponse.Partial) received.complete(Unit)
                } }
                withTimeout(5_000) { received.await() }
                withTimeout(2_000) { job.cancelAndJoin() }
            }
            assertNull(withTimeoutOrNull(100) { provider.generateStreaming("timeout").collect(); true })
            var completed = false
            withTimeout(5_000) { provider.generateStreaming("recover").collect {
                if (it is ModelResponse.Complete) completed = true
            } }
            assertTrue(completed)
            withTimeout(3_000) { while (active.get() != 0) delay(10) }
        } finally { provider.close(); server.close(); workers.shutdownNow() }
    }

    @Test fun generatedPropCanBeUsedAndRemovedOnPhone() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val props = GeneratedPropStore(File(context.filesDir, "companion_props"))
        val prop = GeneratedPropForms.parse(GeneratedPropForms.toJson(GeneratedPropForm(
            "", "Acceptance cup ${System.currentTimeMillis()}", "Temporary acceptance fixture",
            GeneratedPropCategory.HELD, "Hold", GeneratedAvatarExamples.coffeeCup().parts.take(2))))
        props.save(prop, "temporary acceptance ${prop.id}")
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                lateinit var vm: CompanionViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[CompanionViewModel::class.java] }
                val before = vm.uiState.value
                try {
                    scenario.onActivity {
                        vm.equipProp(prop.id)
                        vm.returnToAvatar()
                        assertEquals(prop, vm.uiState.value.equippedProp)
                        if (before.avatarStyle in setOf(AvatarStyle.LUMEN, AvatarStyle.LUMEN_CAT))
                            assertEquals(before.generatedAvatarForm, vm.uiState.value.generatedAvatarForm)
                    }
                    Thread.sleep(1500)
                    scenario.recreate()
                    scenario.onActivity {
                        vm = ViewModelProvider(it)[CompanionViewModel::class.java]
                        assertEquals(prop, vm.uiState.value.equippedProp)
                        assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
                        assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
                        vm.deleteProp(prop.id)
                        assertNull(vm.uiState.value.equippedProp)
                    }
                    assertNull(props.load(prop.id))
                } finally {
                    scenario.onActivity {
                        vm.equipProp(before.equippedProp?.id)
                        vm.setAvatarStyle(before.avatarStyle)
                        if (before.generatedAvatarForm != null) vm.selectGeneratedAvatarForm(before.generatedAvatarForm.id)
                    }
                }
            }
        } finally { props.delete(prop.id) }
    }
}
