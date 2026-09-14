package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.meowmba.core.neural.NeuralSubstrate
import com.example.meowmba.ui.companion.MaleCnsServerControls
import com.example.meowmba.ui.companion.MaleCnsServerUiState
import com.example.meowmba.ui.companion.NeuralSettingsSection
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Connection UI never treats merely opening settings, typing or choosing a draft as pairing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MaleCnsServerUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val computer = MaleCnsServerUiState(serverName = "Studio computer", endpoint = "https://computer.example:8765", paired = true)
    private val server = MaleCnsServerUiState(useDedicated = true, serverName = "Lab server", endpoint = "https://brain.example:8765", paired = true)

    @Composable private fun Frame(largeText: Boolean = false, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 1.4f else density.fontScale)) {
            MeowmbaTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
            }
        }
    }

    @Test fun existingComputerRemainsDefaultAndTestingIsExplicit() {
        var pairCalls = 0
        var tests = 0
        var computerChanges = 0
        rule.setContent { Frame {
            MaleCnsServerControls(computer, { pairCalls++ }, { computerChanges++ }, { tests++ }, {})
        } }
        rule.onNodeWithText("Selected · Studio computer").assertExists()
        rule.onNodeWithTag("malecns-pairing-payload").assertDoesNotExist()
        assertEquals(0, pairCalls + tests + computerChanges)
        rule.onNodeWithTag("malecns-test-server").performScrollTo().performClick()
        assertEquals(1, tests)
        rule.onNodeWithText("Use another server").performScrollTo().performClick()
        rule.onNodeWithTag("malecns-connect-server").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Selected · Studio computer").assertExists()
        assertEquals(0, pairCalls + computerChanges)
        render("paired-computer-and-new-server")
    }

    @Test fun unpairedComputerHasAnExplicitSetupActionAndCannotBeTested() {
        var pairing = 0
        rule.setContent { Frame { MaleCnsServerControls(MaleCnsServerUiState(), {}, {}, {}, { pairing++ }) } }
        rule.onNodeWithTag("malecns-test-server").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("No brain server paired").assertExists()
        assertEquals(0, pairing)
        rule.onNodeWithText("Pair a computer").performScrollTo().performClick()
        assertEquals(1, pairing)
    }

    @Test fun hiddenPayloadIsSubmittedOnlyOnConnectAndFailureKeepsCurrentServer() {
        var state by mutableStateOf(computer)
        val submissions = mutableListOf<String>()
        rule.setContent { Frame { MaleCnsServerControls(state, { submissions += it }, {}, {}, {}) } }
        rule.onNodeWithText("Use another server").performScrollTo().performClick()
        val field = rule.onNodeWithTag("malecns-pairing-payload")
        field.performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        field.performTextInput("  example-pairing-payload  ")
        assertTrue(submissions.isEmpty())
        rule.onNodeWithTag("malecns-connect-server").performScrollTo().performClick()
        assertEquals(listOf("example-pairing-payload"), submissions)
        rule.runOnIdle { state = state.copy(message = "The server did not provide the full MaleCNS model. Your previous connection is unchanged.", isError = true) }
        rule.onNodeWithTag("malecns-server-message").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Selected · Studio computer").assertExists()
        field.assertExists()
        rule.onNodeWithTag("malecns-connect-server").assertIsEnabled()
        render("pairing-failure-preserves-computer")
    }

    @Test fun pairingPayloadDoesNotSurviveSavedInstanceRestoration() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { Frame { MaleCnsServerControls(computer, {}, {}, {}, {}) } }
        rule.onNodeWithText("Use another server").performScrollTo().performClick()
        rule.onNodeWithTag("malecns-pairing-payload").performScrollTo().performTextInput("temporary-secret")
        rule.onNodeWithTag("malecns-connect-server").performScrollTo().assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("malecns-pairing-payload").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithTag("malecns-connect-server").performScrollTo().assertIsNotEnabled()
    }

    @Test fun verifiedServerHidesAndClearsPayloadAndReturningToComputerIsExplicit() {
        var state by mutableStateOf(computer)
        var usesComputer = 0
        rule.setContent { Frame { MaleCnsServerControls(state, {}, { usesComputer++ }, {}, {}) } }
        rule.onNodeWithText("Use another server").performScrollTo().performClick()
        rule.onNodeWithTag("malecns-pairing-payload").performScrollTo().performTextInput("temporary-secret")
        rule.runOnIdle { state = state.copy(busy = true) }
        rule.runOnIdle { state = server.copy(message = "Lab server is connected with the full MaleCNS model.") }
        rule.onNodeWithTag("malecns-pairing-payload").assertDoesNotExist()
        rule.onNodeWithText("Selected · Lab server").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Change brain server").performScrollTo().performClick()
        rule.onNodeWithTag("malecns-pairing-payload").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithTag("malecns-connect-server").performScrollTo().assertIsNotEnabled()
        assertEquals(0, usesComputer)
        rule.onNodeWithText("Use paired computer").performScrollTo().performClick()
        assertEquals(1, usesComputer)
    }

    @Test fun activeConnectionOperationDisablesOtherServerActions() {
        var attempts = 0
        var state by mutableStateOf(computer)
        rule.setContent { Frame { MaleCnsServerControls(state, { attempts++ }, { attempts++ }, { attempts++ }, { attempts++ }) } }
        rule.onNodeWithText("Use another server").performScrollTo().performClick()
        rule.onNodeWithTag("malecns-pairing-payload").performScrollTo().performTextInput("temporary-secret")
        rule.runOnIdle { state = computer.copy(busy = true, message = "Checking the full model…") }
        rule.onNodeWithTag("malecns-connect-server").performScrollTo().assertIsNotEnabled().performClick()
        rule.onNodeWithTag("malecns-pairing-payload").assertIsNotEnabled()
        rule.onNodeWithTag("malecns-test-server").performScrollTo().assertIsNotEnabled().performClick()
        rule.onNodeWithText("Use paired computer").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(0, attempts)
    }

    @Test fun neuralSetupShowsConnectionOptionsOnlyForTheRemoteNetwork() {
        var substrate by mutableStateOf(NeuralSubstrate.RATE)
        var tests = 0
        rule.setContent { Frame {
            NeuralSettingsSection(showNeuralCoreControls = true, substrate = substrate,
                onSubstrateChanged = { substrate = it }, maleCnsServer = server,
                onTestMaleCnsServer = { tests++ })
        } }
        rule.onNodeWithTag("malecns-server-setup").assertDoesNotExist()
        rule.onNodeWithText("MaleCNS · server brain").performScrollTo().performClick()
        rule.onNodeWithTag("malecns-test-server").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, tests)
        rule.onNodeWithText("Enable Lumen reflex").assertExists()
        rule.onNodeWithText("Animation target · 30 FPS").assertExists()
        rule.onNodeWithText("Spiking · experimental").performScrollTo().performClick()
        rule.onNodeWithTag("malecns-server-setup").assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w768dp-h420dp")
    fun landscapeLargeTextKeepsConnectionActionsReachable() {
        rule.setContent { Frame(largeText = true) {
            MaleCnsServerControls(server.copy(message = "Lab server is connected with the full MaleCNS model."), {}, {}, {}, {})
        } }
        rule.onNodeWithText("Where should the fly brain run?").assertExists()
        rule.onNodeWithTag("malecns-test-server").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Change brain server").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("malecns-server-message").performScrollTo().assertIsDisplayed()
        render("server-landscape-large-text")
    }

    private fun render(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/server118/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
