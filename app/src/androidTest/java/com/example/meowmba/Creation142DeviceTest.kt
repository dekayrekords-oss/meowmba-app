package com.example.meowmba

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Uses only a synthetic appearance prompt with the already configured provider.
 * Never changes credentials, grants permissions or sends personal photos. */
class Creation142DeviceTest {
    @Test fun configuredModelPreviewRequiresApproval() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveInvent") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = GeneratedAvatarStore(File(context.filesDir, "avatar_forms"))
        val priorIds = store.list().map { it.id }.toSet()
        val request = "a turquoise moon creature with two silver antennae and round amber eyes ${System.currentTimeMillis()}"
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: CompanionViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[CompanionViewModel::class.java] }
            Thread.sleep(1_000)
            val before = vm.uiState.value
            var testForm: GeneratedAvatarForm? = null
            try {
                scenario.onActivity { vm.createAvatarForm(request, InventReasonLevel.FAST) }
                await(10_000) { vm.uiState.value.features.avatarCreationReview != null }
                assertEquals(before.generatedAvatarForm, vm.uiState.value.generatedAvatarForm)
                screenshot(context, "creation142-question.png")
                scenario.onActivity { vm.chooseAvatarExample(true) }
                await(100_000) { !vm.uiState.value.avatarFormGenerating }
                assertFalse(vm.uiState.value.avatarFormMessage, vm.uiState.value.avatarFormError)
                val candidate = vm.uiState.value.features.avatarCreationReview?.candidate
                assertNotNull("Preview must contain validated geometry", candidate)
                assertEquals(priorIds, store.list().map { it.id }.toSet())
                assertEquals(before.generatedAvatarForm, vm.uiState.value.generatedAvatarForm)
                Thread.sleep(500)
                screenshot(context, "creation142-example.png")
                assertFalse("Test model reused an existing form id; do not overwrite it", candidate!!.id in priorIds)
                testForm = candidate
                scenario.onActivity { vm.acceptAvatarExample() }
                assertEquals(candidate, vm.uiState.value.generatedAvatarForm)
                assertEquals(candidate, store.load(candidate.id))
                assertNull(vm.uiState.value.features.avatarCreationReview)
                assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
                assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
                Thread.sleep(500)
                screenshot(context, "creation142-accepted.png")
            } finally {
                scenario.onActivity {
                    vm.dismissAvatarReview()
                    testForm?.takeIf { it.id !in priorIds }?.let { if (store.load(it.id) != null) vm.deleteGeneratedAvatarForm(it.id) }
                    vm.setAvatarAnimal(before.avatarAnimal)
                    vm.setAvatarStyle(before.avatarStyle)
                    before.generatedAvatarForm?.let { vm.selectGeneratedAvatarForm(it.id) }
                    vm.returnToAvatar()
                }
            }
        }
    }

    private fun await(timeout: Long, done: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeout
        while (!done() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for app state", done())
    }
    private fun screenshot(context: android.content.Context, name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
