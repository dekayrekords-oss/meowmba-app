package com.example.meowmba

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.computer.TermuxSetupSupport
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class TermuxSetup144UiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun playEditionOffersGuidedHandoffNotImpossiblePermission() {
        rule.setContent { MeowmbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OpenCodeLocalConnectionCard(supportOverride = TermuxSetupSupport.GUIDED)
        } } }
        rule.onNodeWithText("Guided setup for this Termux edition").assertExists()
        rule.onNodeWithText("Allow Meow to connect to Termux").assertDoesNotExist()
        rule.onNodeWithText("Copy setup & open Termux").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Verify connection").performScrollTo().assertIsDisplayed()
    }
    @Test fun newUsersGetOfficialDownloadAndInstalledUsersGetPreservationGuidance() {
        rule.setContent { MeowmbaTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            TermuxInstallGuidance(TermuxSetupSupport.MISSING) {}
        } } }
        rule.onNodeWithText("Get Termux from F-Droid (recommended)").assertExists()
        rule.onNodeWithText("Alternative: official GitHub releases").assertExists()
        rule.onNodeWithText("NetBird").assertDoesNotExist()
    }
    @Test fun existingEditionNeverOffersAnOverwriteDownload() {
        rule.setContent { MeowmbaTheme { TermuxInstallGuidance(TermuxSetupSupport.GUIDED) {} } }
        rule.onNodeWithText("Termux detected · guided setup needed").assertExists()
        rule.onNodeWithText("Get Termux from F-Droid (recommended)").assertDoesNotExist()
    }
}
