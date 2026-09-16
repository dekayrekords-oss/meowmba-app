package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agent.*
import com.example.meowmba.core.agent.tools.InstalledApps
import com.example.meowmba.core.access.MeowAccessibilityService
import com.example.meowmba.core.workflows.WorkflowRuntime
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Opt-in connected-phone checks. No account linking, outgoing message,
 * approval bypass in the app, or edits to an existing Facebook draft. */
class PhonePlanningDeviceTest {
    @Test fun installedInventoryResolvesMisheardFacebook() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.facebook.katana", InstalledApps.find(ctx, "fact book")?.packageName)
    }
    @Test fun realLocalReadinessAndSelection() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val path = File(ctx.filesDir, "models/gemma-4-E2B-it.litertlm").path
        val runtime = WorkflowRuntime.get(ctx)
        runtime.mutex.withLock { runtime.verifyAndSelectLocal(path) }
        val storage = SecureStorageManager(ctx)
        assertEquals(path, storage.getEndpoint("local"))
        assertEquals(path, storage.getPreference("planner_verified_local"))
    }
    @Test fun openFacebookComposerWithoutTypingOrPublishing() = runBlocking {
        org.junit.Assume.assumeTrue("Live account navigation is opt-in: pass -e liveFacebook true", InstrumentationRegistry.getArguments().getString("liveFacebook") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val ctx = instrumentation.targetContext
        val service = withTimeout(60_000) {
            while (MeowAccessibilityService.instance == null) delay(300)
            MeowAccessibilityService.instance!!
        }
        val provider = LiteRtProvider(ctx, File(ctx.filesDir, "models/gemma-4-E2B-it.litertlm").path)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val finished = CompletableDeferred<String>()
        val loop = PhoneAgentLoop(scope, brain = { prompt ->
            val reply = provider.generateStructured(prompt)
            val text = (reply as? ModelResponse.Complete)?.text
            // Test authorization is navigation only: never type in a real account.
            if (text != null && PhoneAgentLoop.parseAction(text)?.type == "type")
                "{\"action\":\"blocked\",\"summary\":\"Test stops before typing\"}" else text
        }, service = { service }, announce = {}, onComplete = { finished.complete(it) }, maxSteps = 8)
        try {
            withContext(Dispatchers.Main) {
                val launch = ctx.packageManager.getLaunchIntentForPackage("com.facebook.katana")!!
                ctx.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            delay(2500)
            withContext(Dispatchers.Main) { loop.setGoal("Open Facebook and start a post. Stop at the empty post composer. Do not type or publish anything.") }
            withTimeout(180_000) { finished.await() }
            val screen = withContext(Dispatchers.Main) { service.readScreenCompact(8000) }
            assertEquals("com.facebook.katana", service.getActivePackage())
            assertTrue("Facebook must show a composer, not just the feed", screen.contains("Create post", true) &&
                (screen.contains("Audience", true) || screen.contains("Post settings", true) || screen.contains("Photo/video", true)))
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS: Facebook composer visible; no typing or publishing performed.\n") })
        } finally {
            withContext(Dispatchers.Main) { loop.cancel(); scope.cancel() }
            provider.release()
        }
    }
}
