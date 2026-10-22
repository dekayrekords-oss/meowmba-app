package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.computer.PhoneComputerSetup
import com.example.meowmba.core.computer.TermuxSetupSupport
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phone Computer setup: buttons that re-check must answer, and Termux's allow-external-apps refusal is explained. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class ComputerSetupFeedbackTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun termuxExternalAppsRefusalIsRecognised() {
        assertTrue(PhoneComputerSetup.isExternalAppsError(
            "RunCommandService requires `allow-external-apps` property to be set to `true` in `~/.termux/termux.properties` file."))
        assertFalse(PhoneComputerSetup.isExternalAppsError("No such file or directory"))
        assertFalse(PhoneComputerSetup.isExternalAppsError(null))
    }

    @Test fun copiedFixSetsThePropertyAndReloadsWithoutDuplicates() {
        val cmd = PhoneComputerSetup.ALLOW_EXTERNAL_APPS_COMMAND
        assertTrue(cmd.contains("echo 'allow-external-apps=true' >> \"\$f\""))
        assertTrue(cmd.contains("sed -i '/^[[:space:]]*#*[[:space:]]*allow-external-apps/d'"))
        assertTrue(cmd.contains("termux-reload-settings"))
        assertFalse(cmd.contains('\n'))
    }

    @Test fun checkInstallationAlwaysShowsAResult() {
        rule.setContent { MeowmbaTheme { TermuxInstallGuidance(TermuxSetupSupport.MISSING) {} } }
        rule.onNodeWithTag("termux_check_result").assertDoesNotExist()
        rule.onNodeWithTag("termux_check_installation").performClick()
        rule.onNodeWithTag("termux_check_result").assertTextContains("Termux not found", substring = true)
        rule.onNodeWithTag("termux_check_installation").performClick()
        rule.onNodeWithTag("termux_check_result").assertTextContains("checked again", substring = true)
    }

    @Test fun checkResultNamesEachEdition() {
        assertTrue(termuxCheckResult(TermuxSetupSupport.AUTOMATIC, 1).startsWith("Termux found"))
        assertTrue(termuxCheckResult(TermuxSetupSupport.GUIDED, 1).contains("guided"))
    }

    @Test fun serverPasswordCanBeShownToDoubleCheck() {
        rule.setContent { MeowmbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OpenCodeLocalConnectionCard(supportOverride = TermuxSetupSupport.AUTOMATIC)
        } } }
        rule.onNodeWithTag("opencode_password").performTextInput("correct horse 42")
        rule.onNodeWithTag("opencode_password").assert(shown("••••••••••••••••"))
        rule.onNodeWithContentDescription("Show password").performClick()
        rule.onNodeWithTag("opencode_password").assert(shown("correct horse 42"))
        rule.onNodeWithContentDescription("Hide password").assertExists()
    }

    private fun shown(text: String) = SemanticsMatcher("shows '$text'") {
        androidx.compose.ui.semantics.SemanticsProperties.EditableText.let { k -> k in it.config && it.config[k].text == text }
    }

    @Test fun automaticEditionOffersTheOneTapTermuxFix() {
        rule.setContent { MeowmbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OpenCodeLocalConnectionCard(supportOverride = TermuxSetupSupport.AUTOMATIC)
        } } }
        rule.onNodeWithTag("termux_allow_external_apps").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("2. Allow Meow to connect to Termux").performScrollTo().assertIsDisplayed()
    }

    private fun section(step: ComputerToolsPage, onRefresh: () -> Unit = {}) = rule.setContent { MeowmbaTheme {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            YourComputerSettingsSection(step, {}, "Termux is installed. Finish Computer setup.", true,
                com.example.meowmba.core.computer.ComputerSetupStatus.entries.first(), "", null, null,
                { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, onRefresh)
        }
    } }

    @Test fun checkPhoneAgainRunsAndVisiblyAnswers() {
        var refreshes = 0
        section(ComputerToolsPage.PHONE_OPEN) { refreshes++ }
        rule.onNodeWithTag("computer_check_phone_time").assertDoesNotExist()
        rule.onNodeWithTag("computer_check_phone").performScrollTo().performClick()
        assertEquals(1, refreshes)
        rule.onNodeWithTag("computer_check_phone_time").assertTextContains("Checked", substring = true)
        rule.onNodeWithTag("computer_check_phone").performClick()
        assertEquals(2, refreshes)
        rule.onNodeWithTag("computer_check_phone_time").assertTextContains("(again)", substring = true)
    }

    @Test fun linuxServerPasswordCanBeShown() {
        section(ComputerToolsPage.SERVER_SIGN_IN)
        rule.onAllNodesWithContentDescription("Show password").onFirst().performClick()
        rule.onAllNodesWithContentDescription("Hide password").onFirst().assertExists()
    }
}
