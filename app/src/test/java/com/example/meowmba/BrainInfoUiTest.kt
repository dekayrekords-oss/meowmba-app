package com.example.meowmba

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.behavior.DriveVector
import com.example.meowmba.core.neural.*
import com.example.meowmba.ui.companion.NeuralInfoPanel
import com.example.meowmba.ui.companion.neuralInfoPresentation
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrainInfoUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun classicExplainsWhyNothingRunsAndOnlyStartsOnExplicitAction() {
        var starts = 0
        var pet = 0
        var settings = 0
        var closed = 0
        val d = snapshot(mode = NeuralMode.LEGACY)
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            NeuralInfoPanel(d, onClose = { closed++ }, onUseLocalBrain = { starts++ },
                onPet = { pet++ }, onOpenSettings = { settings++ })
        } }
        assertEquals(0, starts)
        rule.onNodeWithText("Classic · brain off").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsNotEnabled().performClick()
        rule.onNodeWithText("Feed").assertIsNotEnabled()
        assertEquals(0, pet)
        assertTrue(neuralInfoPresentation(d, false).metrics.all { it.value == null && it.fraction == null })
        rule.onNodeWithTag("brain-details-explanation").assertDoesNotExist()
        render("classic-off-portrait")
        rule.onNodeWithText("Start local brain").performScrollTo().performClick()
        assertEquals(1, starts)
        rule.onNodeWithText("Settings").performClick()
        assertEquals(1, settings)
        rule.onNodeWithText("Details").performClick()
        rule.onNodeWithTag("brain-details-explanation").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Less detail").performClick()
        rule.onNodeWithTag("brain-details-explanation").assertDoesNotExist()
        rule.onNodeWithContentDescription("Close brain activity").performClick()
        assertEquals(1, closed)
    }

    @Test fun missingDiagnosticsIsUnknownRatherThanMeasuredZero() {
        rule.setContent { MeowmbaTheme(darkTheme = false) { NeuralInfoPanel(null, {}) } }
        rule.onNodeWithText("Waiting for brain status").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsNotEnabled()
        rule.onNodeWithText("Feed").assertIsNotEnabled()
        assertTrue(neuralInfoPresentation(null, false).metrics.all { it.value == null })
        rule.onNodeWithContentDescription("Mean signal, unavailable").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Mean signal, 0%").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun compactLiveMonitorPreservesActualZeroAndLetsTouchesReachVisibleAvatar() {
        var pet = 0
        var feed = 0
        var avatarTouches = 0
        var notice by mutableStateOf<String?>(null)
        val d = snapshot(mean = 0f, peak = 0f, silent = 28)
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color(0xFFE8ECCA)).testTag("avatar-under-brain")
                    .clickable { avatarTouches++ })
                NeuralInfoPanel(d, {}, inputNotice = notice,
                    onPet = { pet++; notice = "Touch sent to brain" },
                    onFeed = { feed++; notice = "Food sent to brain" })
            }
        } }
        val viewport = rule.onNodeWithTag("brain-info-overlay").fetchSemanticsNode().boundsInRoot
        val sheet = rule.onNodeWithTag("brain-info-sheet").fetchSemanticsNode().boundsInRoot
        assertTrue("Portrait sheet leaves avatar visible", sheet.height <= viewport.height * .56f + 1f)
        rule.onNodeWithText("Neural · controlling avatar").assertIsDisplayed()
        val metrics = neuralInfoPresentation(d, false).metrics
        assertTrue(metrics.all { it.value == 0f && it.fraction == 0f && it.displayValue == "0%" })
        rule.onNodeWithContentDescription("Mean signal, 0%").assertExists()
        rule.onNodeWithTag("avatar-under-brain").performTouchInput { click(Offset(30f, 30f)) }
        assertEquals("Outside the sheet still reaches the avatar", 1, avatarTouches)
        rule.onNodeWithText("Pet").assertIsEnabled().performClick()
        assertEquals(1, pet)
        rule.onNodeWithText("Touch sent to brain").assertIsDisplayed()
        rule.onNodeWithText("Feed").assertIsEnabled().performClick()
        assertEquals(1, feed)
        rule.onNodeWithText("Food sent to brain").assertIsDisplayed()
        render("compact-neural-zero")
    }

    @Test fun shadowIsClearlyObservingAndAcceptsSensoryInputs() {
        var pet = 0
        var control = 0
        val d = snapshot(mode = NeuralMode.SHADOW)
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            NeuralInfoPanel(d, {}, onPet = { pet++ }, onEnableNeuralControl = { control++ })
        } }
        assertEquals("Opening Shadow never gives the brain control", 0, control)
        rule.onNodeWithText("Shadow · observing").assertIsDisplayed()
        rule.onNodeWithText("The brain reacts to inputs; Classic still controls the avatar.").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsEnabled().performClick()
        rule.onNodeWithText("Feed").assertIsEnabled()
        assertEquals(1, pet)
        render("shadow-observing")
        rule.onNodeWithText("Let brain control avatar").performScrollTo().performClick()
        assertEquals(1, control)
    }

    @Test fun coolingAndErrorsHideLastActivityAndDisableInput() {
        var current by mutableStateOf(snapshot(tier = 2, fallback = FallbackReason.THERMAL_SUSPENDED))
        rule.setContent { MeowmbaTheme(darkTheme = true) { NeuralInfoPanel(current, {}) } }
        rule.onNodeWithText("Paused for cooling").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsNotEnabled()
        rule.onNodeWithText("Feed").assertIsNotEnabled()
        assertTrue(neuralInfoPresentation(current, false).metrics.all { it.value == null })
        render("cooling-paused")
        for (reason in listOf(FallbackReason.NON_FINITE_OUTPUT, FallbackReason.EXCEPTION, FallbackReason.DEADLINE)) {
            rule.runOnIdle { current = snapshot(fallback = reason) }
            rule.onNodeWithText("Classic fallback · check brain").assertIsDisplayed()
            rule.onNodeWithText("Pet").assertIsNotEnabled()
            rule.onNodeWithText("Feed").assertIsNotEnabled()
            rule.onNodeWithText("Start local brain").assertExists()
            assertTrue(neuralInfoPresentation(current, false).metrics.all { it.value == null })
        }
    }

    @Test fun unpairedRemoteExplainsSetupAndOffersOfflineRecovery() {
        var paired = 0
        var started = 0
        val d = snapshot(remote = true, connected = true)
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            NeuralInfoPanel(d, {}, desktopPaired = false, onPairComputer = { paired++ },
                onUseLocalBrain = { started++ })
        } }
        rule.onNodeWithText("Brain server not paired").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsNotEnabled()
        rule.onNodeWithText("Feed").assertIsNotEnabled()
        assertTrue(neuralInfoPresentation(d, false).metrics.all { it.value == null })
        assertEquals(0, paired)
        assertEquals(0, started)
        render("remote-unpaired")
        rule.onNodeWithText("Start local brain").performScrollTo().performClick()
        assertEquals(1, started)
        rule.onNodeWithText("Set up brain server").performScrollTo().performClick()
        assertEquals(1, paired)
    }

    @Test fun remoteConnectionLossCannotShowCachedLiveRates() {
        var current by mutableStateOf(snapshot(remote = true, connected = true))
        rule.setContent { MeowmbaTheme(darkTheme = true) { NeuralInfoPanel(current, {}, desktopPaired = true) } }
        rule.onNodeWithText("Pet").assertIsEnabled()
        rule.runOnIdle { current = snapshot(remote = true, connected = false, fallback = FallbackReason.REMOTE_UNAVAILABLE) }
        rule.onNodeWithText("Server unavailable · Classic active").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsNotEnabled()
        rule.onNodeWithText("Feed").assertIsNotEnabled()
        rule.onNodeWithText("Set up brain server").assertDoesNotExist()
        rule.onNodeWithText("Start local brain").assertExists()
        assertTrue(neuralInfoPresentation(current, true).metrics.all { it.value == null })
        render("remote-unavailable")
    }

    @Test @Config(qualifiers = "w840dp-h400dp")
    fun landscapeRemoteUsesNamedRatesAndLeavesAtLeastHalfTheAvatarAreaVisible() {
        var feed = 0
        val d = snapshot(remote = true, connected = true)
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            NeuralInfoPanel(d, {}, desktopPaired = true, onFeed = { feed++ })
        } }
        val viewport = rule.onNodeWithTag("brain-info-overlay").fetchSemanticsNode().boundsInRoot
        val sheet = rule.onNodeWithTag("brain-info-sheet").fetchSemanticsNode().boundsInRoot
        val density = rule.activity.resources.displayMetrics.density
        assertTrue("Landscape sheet is at most360dp", sheet.width <= 360f * density + 1f)
        assertTrue("Landscape keeps the avatar area", sheet.width <= viewport.width * .48f + 1f)
        val metrics = neuralInfoPresentation(d, true).metrics.associateBy { it.id }
        assertEquals("0 Hz", metrics.getValue("feed").displayValue)
        assertEquals(0f, metrics.getValue("feed").fraction)
        assertEquals("120.5 Hz", metrics.getValue("escape").displayValue)
        assertEquals("—", metrics.getValue("groom").displayValue)
        assertEquals("399 Hz", metrics.getValue("wing").displayValue)
        rule.onNodeWithContentDescription("Feeding, 0 Hz").assertExists()
        rule.onNodeWithContentDescription("Grooming, unavailable").assertExists()
        rule.onNodeWithText("Feed").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, feed)
        rule.onNodeWithText("Settings").assertIsDisplayed()
        render("landscape-malecns-rates")
    }

    @Test fun dedicatedServerWorksWithoutDesktopPairingAndShowsItsActualName() {
        val d = snapshot(remote = true, connected = true)
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            NeuralInfoPanel(d, {}, desktopPaired = false, remoteServerPaired = true, remoteServerName = "Lab server")
        } }
        rule.onNodeWithText("MaleCNS · Lab server").assertIsDisplayed()
        rule.onNodeWithText("Neural · controlling avatar").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsEnabled()
        rule.onNodeWithText("Set up brain server").assertDoesNotExist()
        assertTrue(neuralInfoPresentation(d, false, remoteServerPaired = true).measurementsAvailable)
    }

    @Test fun unpairedSelectedServerCannotUseAnUnrelatedDesktopPairing() {
        var settings = 0
        var legacyPairing = 0
        val d = snapshot(remote = true, connected = true)
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            NeuralInfoPanel(d, {}, desktopPaired = true, remoteServerPaired = false,
                onPairComputer = { legacyPairing++ }, onSetupBrainServer = { settings++ })
        } }
        rule.onNodeWithText("Brain server not paired").assertIsDisplayed()
        rule.onNodeWithText("Pet").assertIsNotEnabled()
        rule.onNodeWithText("Feed").assertIsNotEnabled()
        assertTrue(neuralInfoPresentation(d, true, remoteServerPaired = false).metrics.all { it.value == null })
        rule.onNodeWithText("Set up brain server").performScrollTo().performClick()
        assertEquals(1, settings)
        assertEquals(0, legacyPairing)
    }

    private fun snapshot(
        mode: NeuralMode = NeuralMode.NEURAL,
        remote: Boolean = false,
        connected: Boolean = false,
        tier: Int = 0,
        fallback: FallbackReason = FallbackReason.NONE,
        mean: Float = .25f,
        peak: Float = .6f,
        silent: Int = 7,
    ): NeuralDiagnostics {
        val substrate = if (remote) NeuralSubstrate.MALECNS_REMOTE else NeuralSubstrate.RATE
        return NeuralDiagnostics(mode, substrate, fallback, tier, 2000L, 20L, 50L, 80L,
            0L, 0L, 0L, 0f, 0f, DriveVector(), DriveVector(), BehaviorAction.INSPECT,
            CoreDiagnostics(substrate, if (remote) 176422 else 28, mean, peak, 0, silent, 1, 0L,
                extra = if (remote) mapOf("malecns.connected" to if (connected) 1f else 0f,
                    "malecns.feed_hz" to 0f, "malecns.escape_hz" to 120.5f,
                    "malecns.wing_hz" to 399f, "malecns.sim_ms" to 50f, "malecns.compute_ms" to 30f)
                else emptyMap()))
    }

    private fun render(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/brain114/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
