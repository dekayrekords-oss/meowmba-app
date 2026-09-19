package com.example.meowmba

import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.computer.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Uses the user's already configured OpenCode provider, never changes account/model.
 * No tool approvals or filesystem actions are granted by this probe. */
class Computer141DeviceTest {
    @Test fun avatarControlsAndAutoPagePreservePrivacy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(android.content.Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: com.example.meowmba.ui.companion.CompanionViewModel
            scenario.onActivity { vm = androidx.lifecycle.ViewModelProvider(it)[com.example.meowmba.ui.companion.CompanionViewModel::class.java] }
            val before = vm.uiState.value
            try {
                com.example.meowmba.ui.companion.AvatarStyle.entries.forEach { style ->
                    runBlocking { vm.generateAvatarForm(style.label) }
                    assertEquals(style, vm.uiState.value.avatarStyle)
                }
                scenario.onActivity {
                    vm.setIdleMorphRandomness(.3f)
                    vm.setIdleMorphIntervalSec(90)
                    vm.toggleAvatarScreen()
                }
                assertEquals(.3f, vm.uiState.value.features.idleMorphRandomness)
                assertEquals(90, vm.uiState.value.idleMorphIntervalSec)
                Thread.sleep(1_000)
                screenshot(context, "avatar141.png")
                scenario.onActivity { vm.openAutoScreen() }
                Thread.sleep(1_000)
                screenshot(context, "auto141.png")
                assertTrue(vm.uiState.value.showNavScreen)
                assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
                assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
                assertEquals(before.carModeEnabled, vm.uiState.value.carModeEnabled)
            } finally {
                scenario.onActivity {
                    vm.setIdleMorphRandomness(before.features.idleMorphRandomness)
                    vm.setIdleMorphIntervalSec(before.idleMorphIntervalSec)
                    vm.setAvatarAnimal(before.avatarAnimal)
                    vm.setAvatarStyle(before.avatarStyle)
                    before.generatedAvatarForm?.let { vm.selectGeneratedAvatarForm(it.id) }
                    vm.returnToAvatar()
                }
            }
        }
    }

    private fun screenshot(context: android.content.Context, name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun authenticatedWorkerRespondsWithoutTools() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val password = SecureStorageManager(context).getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty()
        assertTrue("Enter and verify the server password in Computer setup first; never paste it in chat.", password.isNotBlank())
        OpenCodeLocalConnection.verify(password)
        val api = OpenCodeComputer(password)
        val session = api.create("Meow QA 141 · no tools")
        try {
            api.send(session, "Reply with exactly MEOW_COMPUTER_READY. Do not use tools, inspect files, or change anything.")
            withTimeout(90_000) {
                while (true) {
                    val snapshot = api.snapshot(session)
                    assertTrue("Probe requested tool access; no approval was granted.", snapshot.permissions.isEmpty())
                    if (snapshot.transcript().contains("assistant: MEOW_COMPUTER_READY")) break
                    delay(1_000)
                }
            }
        } finally { withContext(NonCancellable) { runCatching { api.stop(session) } } }
    }
}
