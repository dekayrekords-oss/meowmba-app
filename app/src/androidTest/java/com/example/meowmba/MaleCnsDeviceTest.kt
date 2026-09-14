package com.example.meowmba

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.core.bridge.DesktopBridgeManager
import com.example.meowmba.core.neural.*
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Owner-invoked live bridge/Meow acceptance, never part of default unattended tests. */
class MaleCnsDeviceTest {
    @Test fun liveAvatarUsesRemoteMotorRatesAndPausesWhenHidden() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("maleCnsLive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = SecureStorageManager(context)
        assertTrue("Pair the computer bridge first", DesktopBridgeManager(storage).isPaired())
        val oldMode = storage.getPreference(NeuralModeStore.KEY_MODE).orEmpty()
        val oldCore = storage.getPreference(NeuralModeStore.KEY_SUBSTRATE).orEmpty()
        val oldCare = storage.getPreference("pet_care_v1").orEmpty()
        val oldMic = com.example.meowmba.core.audio.MicrophonePrivacy.isEnabled(storage)
        val oldCamera = storage.getBooleanPreference("camera_enabled", false)
        var passed = false
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                Thread.sleep(3000)
                lateinit var vm: CompanionViewModel
                scenario.onActivity { activity ->
                    vm = ViewModelProvider(activity)[CompanionViewModel::class.java]
                    vm.setNeuralSubstrate(NeuralSubstrate.MALECNS_REMOTE)
                    vm.setNeuralMode(NeuralMode.NEURAL)
                    if (!vm.uiState.value.showNeuralInfo) vm.toggleNeuralInfo()
                }
                val deadline = System.currentTimeMillis() + 40_000L
                var connected: NeuralDiagnostics? = null
                while (System.currentTimeMillis() < deadline) {
                    val d = vm.uiState.value.neuralDiagnostics
                    if (d?.core?.extra?.get("malecns.connected") == 1f) { connected = d; break }
                    Thread.sleep(250)
                }
                assertNotNull("No fresh MaleCNS reply: ${vm.uiState.value.neuralDiagnostics?.core?.status}", connected)
                var maxFeed = 0f; var maxGroom = 0f
                repeat(16) { i ->
                    if (i % 4 == 0) scenario.onActivity { vm.habitatAction(HabitatAction.FEED); vm.onAvatarPetted() }
                    Thread.sleep(500)
                    val extra = vm.uiState.value.neuralDiagnostics?.core?.extra.orEmpty()
                    maxFeed = maxOf(maxFeed, extra["malecns.feed_hz"] ?: 0f)
                    maxGroom = maxOf(maxGroom, extra["malecns.groom_hz"] ?: 0f)
                }
                assertTrue("Food did not reach the proboscis motor population", maxFeed > 0f)
                assertTrue("Touch did not reach the grooming population", maxGroom > 0f)
                val d = vm.uiState.value.neuralDiagnostics!!
                assertEquals(NeuralSubstrate.MALECNS_REMOTE, d.substrate)
                assertEquals(FallbackReason.NONE, d.fallback)
                assertEquals("The phone must be using the verified full graph", 1f, d.core.extra["malecns.full_graph"])
                for (key in listOf("forward", "backward", "halt", "landing", "takeoff", "groom2")) {
                    val hz = d.core.extra["malecns.${key}_hz"]
                    assertTrue("Missing measured $key population", hz != null && hz.isFinite() && hz >= 0f)
                }
                assertTrue("Phone controller exceeded its 20 ms p95 guard", d.p95StepMicros < 20_000)
                scenario.onActivity {
                    if (vm.uiState.value.showNeuralInfo) vm.toggleNeuralInfo()
                    vm.setAvatarPerformanceStageAllowed(false)
                }
                Thread.sleep(1000)
                assertEquals(0f, vm.neuralWingPowerNow())
                assertEquals(FlyMotorIntent.NONE, vm.neuralFlyMotorNow())
                val output = JSONObject().put("build", context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode).put("connected", true)
                    .put("units", d.core.units).put("feedMaxHz", maxFeed).put("groomMaxHz", maxGroom)
                    .put("phoneStepP95Micros", d.p95StepMicros).put("thermalTier", d.thermalTier)
                    .put("graph", "full").put("assetSha256", MaleCnsGraphProfile.FULL.asset)
                    .put("hiddenBodyOutputInactive", true).put("hiddenWingOutputZero", true).put("scope", "Short live phone to paired computer to Meow check; not heat/battery endurance")
                    .put("extra", JSONObject(d.core.extra))
                File(context.getExternalFilesDir(null), "malecns-build115-device.json").writeText(output.toString(2))
                assertEquals(oldMic, com.example.meowmba.core.audio.MicrophonePrivacy.isEnabled(storage))
                assertEquals(oldCamera, storage.getBooleanPreference("camera_enabled", false))
                passed = true
            }
        } finally {
            storage.savePreference("pet_care_v1", oldCare)
            if (!passed || args.getString("activate") != "true") {
                storage.savePreference(NeuralModeStore.KEY_MODE, oldMode)
                storage.savePreference(NeuralModeStore.KEY_SUBSTRATE, oldCore)
            }
        }
    }
}
