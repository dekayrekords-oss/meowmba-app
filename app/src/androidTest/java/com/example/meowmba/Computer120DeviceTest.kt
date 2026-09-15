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
            val marker = "meow120_" + System.currentTimeMillis()
            fun awaitReply(token: String): String {
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline) {
                    vm.uiState.value.chatMessages.lastOrNull { it.isCompanion && it.text.contains(token) }?.let { return it.text }
                    Thread.sleep(50)
                }
                fail("No persisted Computer reply for acceptance marker; caption=" + vm.uiState.value.captionText)
                return ""
            }
            try {
                // Avoid scheduling unrelated persisted goals just to test the chat path.
                flags.setAgenticRuntimeV1(true)
                scenario.onActivity { vm.runOnComputer("printf ${marker}_verified") }
                awaitStopped()
                val success = awaitReply("${marker}_verified")
                assertTrue(success, success.contains("Computer completed"))
                // The actual Computer host creates, edits and reads a unique project file.
                val workspace = "meow-acceptance-$marker"
                scenario.onActivity { vm.runOnComputer("mkdir -p $workspace && printf ${marker}_draft > $workspace/draft.txt && printf _edited >> $workspace/draft.txt && cat $workspace/draft.txt && rm $workspace/draft.txt && rmdir $workspace") }
                awaitStopped()
                val edited = awaitReply("${marker}_draft_edited")
                assertTrue(edited, edited.contains("Computer completed"))
                scenario.onActivity { vm.runOnComputer("printf ${marker}_failed; exit 7") }
                awaitStopped()
                val failure = awaitReply("${marker}_failed")
                assertTrue(failure, failure.contains("BLOCKED"))
                scenario.onActivity { vm.runOnComputer("sleep 3; printf ${marker}_late") }
                Thread.sleep(300)
                scenario.onActivity { vm.cancelAgenticComputerChat() }
                Thread.sleep(4500)
                assertFalse(vm.uiState.value.computerTaskRunning)
                // Speech captions may legitimately fade; cancellation must prevent a late saved reply.
                assertFalse("Cancelled command published a late result", vm.uiState.value.chatMessages.any {
                    it.isCompanion && it.text.contains("${marker}_late")
                })
                assertEquals(camera, storage.getBooleanPreference("camera_enabled", false))
            } finally {
                scenario.onActivity { vm.cancelAgenticComputerChat(); vm.setComputerPowerState(power) }
                val runtime = com.example.meowmba.core.agentic.AgenticRuntime.from(context)
                runtime.listOpenGoals().filter { it.title.contains(marker) }.forEach { runtime.cancelGoal(it.id) }
                flags.setAgenticRuntimeV1(wasEnabled)
                scenario.onActivity { vm.refreshAgenticPresenceHint() }
            }
        }
    }
    @Test fun pinnedSshKeyExecutesOnMeow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyFile = File(context.filesDir, "computer120-test-key")
        assertTrue("Temporary SSH credential not provisioned", keyFile.isFile)
        val profiles = com.example.meowmba.core.agentic.InMemoryConnectionProfileStore()
        profiles.save(com.example.meowmba.core.agentic.ConnectionProfile(
            id = "build120-test", displayName = "Temporary acceptance", type = com.example.meowmba.core.agentic.ConnectionProfileType.SSH,
            host = "127.0.0.1", port = 18767, username = "prog", credentialRef = "temporary"))
        val resolver = object : com.example.meowmba.core.agentic.CredentialResolver {
            override fun resolve(credentialRef: String): String = keyFile.readText()
        }
        val executor = com.example.meowmba.core.agentic.DefaultAgenticSshExecutor({ true }, profiles, resolver)
        val result = executor.exec("build120-test", listOf("printf", "meow120_ssh"), 10_000)
        assertTrue(result.toString(), result is SshExecResult.Ok)
        result as SshExecResult.Ok
        assertEquals(0, result.exitCode)
        assertEquals("meow120_ssh", result.stdout)
        val pinned = profiles.get("build120-test")!!
        assertTrue(pinned.hostKeyFingerprint.orEmpty().startsWith("SHA256:"))
        profiles.save(pinned.copy(hostKeyFingerprint = "SHA256:deliberately-wrong"))
        assertTrue("Changed server key accepted", executor.exec("build120-test", listOf("true"), 5_000) is SshExecResult.Error)
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
