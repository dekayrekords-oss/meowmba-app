package com.example.meowmba

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.agentic.HttpsRuntimeClient
import com.example.meowmba.core.agentic.SshExecResult
import com.example.meowmba.core.config.FeatureFlags
import com.example.meowmba.core.config.ComputerPowerStore
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.CompanionViewModel
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicit installation acceptance; preserves flags, camera choice and Computer power. */
class Computer120DeviceTest {
    @Test fun computerChatMeasuresExitAndCancellation() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val storage = SecureStorageManager(context)
        val flags = FeatureFlags.from(context)
        val wasEnabled = flags.agenticRuntimeV1
        val power = ComputerPowerStore(storage).state
        val camera = storage.getBooleanPreference("camera_enabled", false)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: CompanionViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[CompanionViewModel::class.java] }
            Thread.sleep(1500)
            fun awaitStopped() {
                val deadline = System.currentTimeMillis() + 60_000
                while (vm.uiState.value.computerTaskRunning && System.currentTimeMillis() < deadline) Thread.sleep(100)
                assertFalse("Computer did not finish", vm.uiState.value.computerTaskRunning)
            }
            try {
                // Avoid scheduling unrelated persisted goals just to test the chat path.
                flags.setAgenticRuntimeV1(true)
                scenario.onActivity { vm.runOnComputer("printf meow120_verified") }
                awaitStopped()
                assertTrue(vm.uiState.value.captionText, vm.uiState.value.captionText.contains("Computer completed"))
                assertTrue(vm.uiState.value.captionText, vm.uiState.value.captionText.contains("meow120_verified"))
                scenario.onActivity { vm.runOnComputer("printf meow120_failed; exit 7") }
                awaitStopped()
                assertTrue(vm.uiState.value.captionText, vm.uiState.value.captionText.contains("BLOCKED"))
                scenario.onActivity { vm.runOnComputer("sleep 3; printf meow120_late") }
                Thread.sleep(300)
                scenario.onActivity { vm.cancelAgenticComputerChat() }
                val stopped = vm.uiState.value.captionText
                Thread.sleep(4500)
                assertFalse(vm.uiState.value.computerTaskRunning)
                assertEquals("Late result replaced cancellation", stopped, vm.uiState.value.captionText)
                assertEquals(camera, storage.getBooleanPreference("camera_enabled", false))
            } finally {
                scenario.onActivity { vm.cancelAgenticComputerChat(); vm.setComputerPowerState(power) }
                flags.setAgenticRuntimeV1(wasEnabled)
            }
        }
    }
    @Test fun authenticatedMeowWorkerReturnsRealOutput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenFile = File(context.filesDir, "computer120-test-token")
        assertTrue("Temporary worker credential not provisioned", tokenFile.isFile)
        val token = tokenFile.readText().trim()
        val base = "http://127.0.0.1:18766"
        assertFalse(HttpsRuntimeClient(baseUrl = base, bearerToken = "wrong").health().ok)
        val client = HttpsRuntimeClient(baseUrl = base, bearerToken = token)
        assertTrue(client.health().detail, client.health().ok)
        val result = client.runArgv(listOf("/bin/sh", "-c", "printf meow120_remote; uname -s"), overallTimeoutMs = 10_000)
        assertTrue(result.toString(), result is SshExecResult.Ok)
        result as SshExecResult.Ok
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("meow120_remoteLinux"))
    }
}
