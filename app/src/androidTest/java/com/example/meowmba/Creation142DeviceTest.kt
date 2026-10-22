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
        val storage = com.example.meowmba.core.security.SecureStorageManager(context)
        val priorReason = storage.getPreference("invent_reason_level").orEmpty()
        val store = GeneratedAvatarStore(File(context.filesDir, "avatar_forms"))
        val priorIds = store.list().map { it.id }.toSet()
        val request = "a turquoise moon creature with two silver antennae and round amber eyes ${System.currentTimeMillis()}"
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: CompanionViewModel
            scenario.onActivity {
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                vm = ViewModelProvider(it)[CompanionViewModel::class.java]
            }
            Thread.sleep(1_000)
            val before = vm.uiState.value
            var testForm: GeneratedAvatarForm? = null
            try {
                scenario.onActivity { vm.createAvatarForm(request, InventReasonLevel.BALANCED) }
                await(10_000) { vm.uiState.value.features.avatarCreationReview != null }
                assertEquals(before.generatedAvatarForm, vm.uiState.value.generatedAvatarForm)
                Thread.sleep(750)
                screenshot(context, "creation142-question.png")
                scenario.onActivity { vm.chooseAvatarExample(true) }
                // Match the bounded remote allowance plus transport/UI cleanup time.
                await(InventTimeBudget.avatar(true, InventReasonLevel.BALANCED) + 30_000) { !vm.uiState.value.avatarFormGenerating }
                File(context.getExternalFilesDir(null), "creation142-result.txt").writeText(
                    "Message: ${vm.uiState.value.avatarFormMessage}\nError: ${vm.uiState.value.avatarFormError}\nReview present: ${vm.uiState.value.features.avatarCreationReview != null}\nCandidate present: ${vm.uiState.value.features.avatarCreationReview?.candidate != null}"
                )
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
                storage.savePreference("invent_reason_level", priorReason)
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

    @Test fun appearanceWeatherAndHomeControlsPreservePrivacy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Optional recovery of a reason preference changed by an older test version.
        InstrumentationRegistry.getArguments().getString("restoreReason")?.let { reason ->
            require(reason in InventReasonLevel.entries.map { it.name })
            com.example.meowmba.core.security.SecureStorageManager(context).savePreference("invent_reason_level", reason)
        }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var vm: CompanionViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[CompanionViewModel::class.java] }
            Thread.sleep(1_000)
            val before = vm.uiState.value
            val router = vm.currentProvider as? com.example.meowmba.core.agent.OmniRoute
            File(context.getExternalFilesDir(null), "creation142-route.txt").writeText(
                "Selected: ${before.selectedModel}\nChat preference: ${vm.preferredChatRouteId()}\nInvent preference: ${vm.preferredFastRouteId()}\n" +
                    router?.plan("Create a cartoon avatar", preferRouteId = vm.preferredFastRouteId())?.joinToString("\n") { "${it.id}: ${it.label} (${it.provider.javaClass.simpleName})" }.orEmpty()
            )
            try {
                scenario.onActivity { vm.toggleAvatarScreen() }
                Thread.sleep(750)
                screenshot(context, "creation142-avatar-settings.png")
                scenario.onActivity {
                    vm.returnToAvatar()
                    vm.setHomeControls(false, "Bottom right", false)
                }
                Thread.sleep(750)
                assertFalse(vm.uiState.value.features.careVisible)
                assertFalse(vm.uiState.value.features.neuralFeedbackVisible)
                screenshot(context, "creation142-hidden-controls.png")
                scenario.onActivity { vm.toggleWeatherScreen() }
                Thread.sleep(3_000)
                screenshot(context, "creation142-weather.png")
                assertEquals(before.isMicEnabled, vm.uiState.value.isMicEnabled)
                assertEquals(before.isCameraEnabled, vm.uiState.value.isCameraEnabled)
                assertEquals(before.carModeEnabled, vm.uiState.value.carModeEnabled)
            } finally {
                scenario.onActivity {
                    vm.setHomeControls(before.features.careVisible, before.features.carePosition, before.features.neuralFeedbackVisible)
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
