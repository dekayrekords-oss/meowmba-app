package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.alerts.ArizonaWorkZones
import com.example.meowmba.core.alerts.RoadFeed
import com.example.meowmba.core.audio.*
import com.example.meowmba.core.diagnostics.PossibleBugReports
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Repair125ServicesDeviceTest {
    @Test fun configuredFreeRouterReturnsStructuredProp() = runBlocking<Unit> {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("freeJsonProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = SecureStorageManager(context)
        val model = storage.getModelName("openrouter") ?: "openrouter/free"
        require(model == "openrouter/free" || model.endsWith(":free")) { "Probe only permits the selected free route" }
        val key = requireNotNull(storage.getApiKey("openrouter"))
        val endpoint = (storage.getEndpoint("openrouter")?.takeIf { it.isNotBlank() }
            ?: "https://openrouter.ai/api/v1").trimEnd('/').removeSuffix("/v1")
        val report = JSONObject().put("model", model)
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            val result = withContext(Dispatchers.IO) {
                val connection = java.net.URL("$endpoint/v1/chat/completions").openConnection() as java.net.HttpURLConnection
                try {
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10_000; connection.readTimeout = 40_000
                    connection.setRequestProperty("Authorization", "Bearer $key")
                    connection.setRequestProperty("Content-Type", "application/json")
                    val prompt = com.example.meowmba.core.agent.tools.PropInventor.prompt("A small blue fishing rod with a red reel, three clear shapes")
                    val body = JSONObject().put("model", model).put("stream", false).put("max_tokens", 3500)
                        .put("response_format", JSONObject().put("type", "json_object"))
                        .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    report.put("httpStatus", connection.responseCode)
                    require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                    val raw = connection.inputStream.bufferedReader().use { reader ->
                        val out = StringBuilder()
                        val buffer = CharArray(4096)
                        while (out.length < 65_536) {
                            val count = reader.read(buffer, 0, minOf(buffer.size, 65_536 - out.length))
                            if (count < 0) break
                            out.append(buffer, 0, count)
                        }
                        out.toString()
                    }
                    require(raw.length < 65_536) { "Response exceeded probe bound" }
                    val decoded = JSONObject(raw)
                    report.put("resolvedModel", decoded.optString("model"))
                    val choice = decoded.getJSONArray("choices").getJSONObject(0)
                    report.put("finishReason", choice.optString("finish_reason"))
                    choice.getJSONObject("message").getString("content")
                } finally { connection.disconnect() }
            }
            val form = com.example.meowmba.core.agent.tools.PropInventor.parseResponse(result)
            report.put("form", JSONObject(com.example.meowmba.ui.companion.GeneratedPropForms.toJson(form))).put("passed", true)
        } catch (failure: Exception) {
            report.put("error", failure.message?.take(200)); throw failure
        } finally {
            report.put("elapsedMs", android.os.SystemClock.elapsedRealtime() - started)
            File(context.getExternalFilesDir(null), "repair128-free-json.json").writeText(report.toString(2))
        }
    }

    @Test fun roadSourcesReturnHonestHealthWithoutAnOpenRoute() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Public reference point in Arizona, not the user's current position.
        val az = ArizonaWorkZones.fetch(33.92, -112.14)
        val osm = RoadFeed.fetchOsmResult(33.92, -112.14)
        val out = JSONObject()
        for (r in listOf(az, osm)) out.put(r.source, JSONObject()
            .put("available", r.available).put("count", r.hits.size).put("detail", r.detail))
        File(context.getExternalFilesDir(null), "repair125-road-health.json").writeText(out.toString(2))
        assertTrue("Official Arizona source failed: ${az.detail}", az.available)
        assertTrue("OSM failure must explain its status", osm.available || !osm.detail.isNullOrBlank())
    }

    @Test fun reportedBugPersistsOnPhoneStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "repair125-bugs-${System.nanoTime()}").apply { mkdirs() }
        try {
            val store = PossibleBugReports(root)
            val saved = store.record("The yellow mic button stays on and locks me out", "acceptance fixture")
            assertNotNull(saved)
            assertEquals(saved, PossibleBugReports(root).list().single())
            store.record("The yellow mic button stays on and locks me out", "acceptance fixture")
            assertEquals(2, PossibleBugReports(root).list().single().occurrences)
        } finally { root.deleteRecursively() }
    }

    @Test fun selectedRecognizerTranscribesSyntheticSpeech() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "repair125-speech.pcm")
        assertTrue("Provision the documented synthetic 16kHz PCM fixture", file.isFile)
        val bytes = file.readBytes()
        val pcm = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
        val storage = SecureStorageManager(context)
        val probe = InstrumentationRegistry.getArguments().getString("asrProbe")
        val backend = AsrBackend.fromId(probe ?: storage.getPreference(AsrBackend.PREF_KEY))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val local = SherpaAsrEngine(context, scope)
        val whisper = WhisperAsrEngine(context)
        var cloud: RefiningSpeechEngine? = null
        val output = JSONObject().put("backend", backend.id).put("synthetic", true)
            .put("openAiKeyConfigured", !storage.getApiKey("openai").isNullOrBlank())
            .put("openRouterKeyConfigured", !storage.getApiKey("openrouter").isNullOrBlank())
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            if (backend == AsrBackend.OPENAI_CLOUD) require(output.getBoolean("openAiKeyConfigured")) { "No OpenAI key configured on this installation" }
            if (backend == AsrBackend.GEMINI_CLOUD) require(output.getBoolean("openRouterKeyConfigured")) { "No OpenRouter key configured on this installation" }
            val text = withTimeout(45_000) {
                when (backend) {
                    AsrBackend.OPENAI_CLOUD -> transcribeFixture(OpenAiAsrEngine(local, scope, { storage.getApiKey("openai") })
                        .also { cloud = it }, pcm)
                    AsrBackend.GEMINI_CLOUD -> transcribeFixture(GeminiAsrEngine(local, scope, { storage.getApiKey("openrouter") },
                        model = { storage.getModelName(AsrBackend.OPENROUTER_ASR_PROVIDER) ?: AsrBackend.DEFAULT_GEMINI_MODEL })
                        .also { cloud = it }, pcm)
                    else -> whisper.transcribe(pcm)
                }
            }
            output.put("elapsedMs", android.os.SystemClock.elapsedRealtime() - start).put("transcript", text)
            val normalized = text.orEmpty().lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ")
            val understood = listOf("remember", "green tea", "where are we", "calendar").count { it in normalized }
            output.put("recognizedPhrases", understood).put("totalPhrases", 4)
            assertEquals("Synthetic speech was not understood: $text", 4, understood)
            if (InstrumentationRegistry.getArguments().getString("selectVerifiedAsr") == "true") {
                require(backend.isCloud) { "This acceptance option is only for the user-authorized cloud selection" }
                // Exercise the actual setter and its capture handoff. Closing an
                // activity also flushes pending SharedPreferences writes before
                // the instrumentation process is replaced by the next check.
                androidx.test.core.app.ActivityScenario.launch<MainActivity>(
                    android.content.Intent(context, MainActivity::class.java)).use { scenario ->
                    scenario.onActivity {
                        val vm = androidx.lifecycle.ViewModelProvider(it)[com.example.meowmba.ui.companion.CompanionViewModel::class.java]
                        vm.setAsrBackend(backend)
                        assertEquals(backend, vm.uiState.value.asrBackend)
                    }
                }
                output.put("selectedAfterSuccessfulProbe", true)
            }
        } catch (failure: Exception) {
            output.put("error", failure.message?.take(240))
            throw failure
        } finally {
            val name = if (probe == null) "repair125-recognition.json" else "repair125-recognition-${backend.id}.json"
            File(context.getExternalFilesDir(null), name).writeText(output.toString(2))
            cloud?.destroy() ?: local.destroy()
            whisper.release(); scope.cancel()
        }
    }

    // Invoke the backend with synthetic PCM without opening its live microphone
    // or widening the production API. This measures raw transcription, not VAD.
    private suspend fun transcribeFixture(engine: RefiningSpeechEngine, pcm: ShortArray): String? =
        kotlin.coroutines.suspendCoroutine { continuation ->
            try {
                val method = engine.javaClass.getDeclaredMethod("transcribe", ShortArray::class.java,
                    kotlin.coroutines.Continuation::class.java).apply { isAccessible = true }
                val result = method.invoke(engine, pcm, continuation)
                if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED)
                    continuation.resumeWith(Result.success(result as String?))
            } catch (failure: java.lang.reflect.InvocationTargetException) {
                continuation.resumeWith(Result.failure(failure.targetException))
            }
        }
}
