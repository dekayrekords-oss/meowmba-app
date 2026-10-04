package com.example.meowmba

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agent.LiteRtProvider
import com.example.meowmba.core.agent.LocalInferenceRuntime
import com.example.meowmba.core.agent.ModelResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real native inference only; no Activity/UI automation or injected avatar result.
 * Intentionally restricted to the named commercial capture emulator.
 */
class LiteRt206CaptureTest {
    @Test fun installedGemmaReturnsRealStructuredAnswer() = runBlocking {
        assertEquals("emulator-only", InstrumentationRegistry.getArguments().getString("commercialCapture"))
        assertTrue("Never run this capture acceptance on an owner phone", Build.FINGERPRINT.contains("sdk_gphone"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.filesDir.resolve("models/gemma-4-E2B-it.litertlm")
        assertTrue("Use the already installed model; never download or replace it", model.isFile)
        val provider = LiteRtProvider(context, model.absolutePath)
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            val result = withTimeout(180_000) {
                provider.generateStructured(
                    "Return exactly one JSON object describing a purple dragon: {\"color\":\"purple\",\"animal\":\"dragon\"}. Do not add any other keys or explanation.",
                    "{\"type\":\"object\",\"properties\":{\"color\":{\"type\":\"string\"},\"animal\":{\"type\":\"string\"}},\"required\":[\"color\",\"animal\"],\"additionalProperties\":false}",
                )
            }
            val receipt = JSONObject().put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - started)
                .put("model", model.name).put("result_kind", result.javaClass.simpleName)
            when (result) {
                is ModelResponse.Complete -> receipt.put("actual_response", result.text)
                is ModelResponse.Error -> receipt.put("actual_error", result.message)
                else -> Unit
            }
            context.filesDir.resolve("litert206-native-acceptance.json").writeText(receipt.toString(2))
            assertTrue("Native model failed: $result", result is ModelResponse.Complete)
            val answer = JSONObject((result as ModelResponse.Complete).text)
            assertEquals("purple", answer.getString("color").lowercase())
            assertEquals("dragon", answer.getString("animal").lowercase())
        } finally {
            LocalInferenceRuntime.releaseIdle()
        }
    }
}
