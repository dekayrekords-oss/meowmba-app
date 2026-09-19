package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.agentic.*
import com.example.meowmba.core.computer.ComputerSetupStatus
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComputerSetup135UiTest {
    @get:Rule val rule=createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val storage=mock(SecureStorageManager::class.java)
    private fun tap(text:String) = rule.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    private fun back() = rule.onNodeWithContentDescription("Back in Computer").performClick()
    private fun fill(label:String,value:String) = rule.onNodeWithText(label).performScrollTo().performTextReplacement(value)

    @Test fun computerIsDirectlyReachableFromSettingsAndNeuralWithoutExpandingForms() {
        var enabled by mutableStateOf(false)
        rule.setContent { MeowmbaTheme(darkTheme=false) { SettingsScreen(storage,
            agenticRuntimeV1=enabled,onAgenticRuntimeV1Change={enabled=it},onClose={}) } }
        tap("Computer & task tools")
        rule.onNodeWithText("Set up Computer").assertExists()
        rule.onNodeWithText("Server address").assertDoesNotExist()
        rule.onNodeWithText("API key (write-only)").assertDoesNotExist()
        render("hub-light")
        tap("Advanced legacy planner")
        rule.onNodeWithText("Base URL").assertExists()
        rule.onNodeWithText("What should Meow do?").assertDoesNotExist()
        back()
        tap("Tasks & approvals")
        rule.onNodeWithText("What should Computer do?").assertExists()
        rule.onNodeWithText("Run with Computer").assertIsNotEnabled()
        rule.onNodeWithText("Base URL").assertDoesNotExist()
        back()
        tap("Saved connections")
        rule.onNodeWithText("Host").assertDoesNotExist()
        assertFalse(enabled)
        tap("Enable task automation")
        assertTrue(enabled)
        tap("Add connection")
        rule.onNodeWithText("Host").assertExists()
        tap("Cancel")
        rule.onNodeWithText("Host").assertDoesNotExist()
        back()
        rule.onNodeWithContentDescription("Back to settings").performClick()
        tap("Neural / Lumen")
        tap("Computer & task tools")
        rule.onNodeWithText("Set up Computer").assertExists()
    }

    @Test fun phoneSetupHasThreeShortStepsAndDoesNotLaunchOrInstallAutomatically() {
        var opened=0;var installs=0
        rule.setContent { MeowmbaTheme(darkTheme=true) { SettingsScreen(storage,
            initialPage=COMPUTER_TOOLS_TITLE,computerPhoneReady=true,
            onComputerOpenTerminal={opened++},onComputerInstall={_,_->installs++},onClose={}) } }
        tap("Set up Computer");tap("This phone")
        rule.onNodeWithText("Phone setup · Step 1 of 3").assertExists()
        rule.onNodeWithText("Copy setup commands").assertDoesNotExist()
        tap("Continue")
        rule.onNodeWithText("Phone setup · Step 2 of 3").assertExists()
        rule.onNodeWithText("Server address").assertDoesNotExist()
        render("phone-prepare-dark")
        rule.onNode(hasText("Setup computer") and hasClickAction()).assertExists()
        rule.onNodeWithText("Copy setup commands").assertDoesNotExist()
        tap("Advanced: terminal and diagnostics")
        rule.onNodeWithText("Phone setup · Step 3 of 3").assertExists()
        assertEquals(0,opened);assertEquals(0,installs)
        tap("Open phone Terminal");assertEquals(1,opened)
    }

    @Test fun serverTestUsesStableIdentityAndChangedAddressNeedsAnotherCheck() {
        var status by mutableStateOf(ComputerSetupStatus.NotSetUp)
        var saved by mutableStateOf<ConnectionProfile?>(null)
        var verified by mutableStateOf<ConnectionProfile?>(null)
        val tested=mutableListOf<ConnectionProfile>()
        val secrets=mutableListOf<String>()
        var installs=0
        rule.setContent { MeowmbaTheme(darkTheme=true) { SettingsScreen(storage,
            initialPage=COMPUTER_TOOLS_TITLE,computerSetupStatus=status,computerSavedProfile=saved,
            computerVerifiedProfile=verified,onComputerTestConnection={p,key->
                tested+=p;secrets+=key;saved=p;verified=p;status=ComputerSetupStatus.Connected
            },onComputerInstall={_,_->installs++},onClose={}) } }
        tap("Set up Computer");tap("A Linux server")
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        fill("Server address","server.example")
        tap("Advanced connection options");fill("Port","70000")
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        fill("Port","22");tap("Continue")
        rule.onNodeWithText("Review connection").assertIsNotEnabled()
        fill("Username","artist")
        rule.onNode(hasText("Password") and hasSetTextAction()).performScrollTo().performTextInput("fixture-password")
        tap("Review connection")
        rule.onNodeWithText("Install Computer").assertIsNotEnabled()
        tap("Save & test connection")
        assertEquals(1,tested.size);assertEquals("fixture-password",secrets.single())
        rule.onNodeWithText("Install Computer").assertIsEnabled()
        assertEquals(0,installs)
        render("server-checked-dark")
        back();back();fill("Server address","second.example");tap("Continue");tap("Review connection")
        rule.onNodeWithText("Install Computer").assertIsNotEnabled()
        tap("Save & test connection")
        assertEquals(tested[0].id,tested[1].id)
        assertEquals("second.example",tested[1].host)
        assertEquals("",secrets[1])
        tap("Install Computer");assertEquals(1,installs)
    }

    @Test fun busyServerDoesNotAcceptDuplicateTestsOrInstallation() {
        var status by mutableStateOf(ComputerSetupStatus.NotSetUp)
        var tests=0
        val profile=ConnectionProfile("existing","Studio server",ConnectionProfileType.SSH,"server.example",22,"artist","studio",workspaceRoot="/opt/meow-computer")
        rule.setContent { MeowmbaTheme { SettingsScreen(storage,initialPage=COMPUTER_TOOLS_TITLE,
            computerSavedProfile=profile,computerSetupStatus=status,
            onComputerTestConnection={_,_->tests++;status=ComputerSetupStatus.Checking},onClose={}) } }
        tap("Set up Computer");tap("A Linux server");tap("Continue");tap("Review connection")
        tap("Save & test connection")
        rule.onNodeWithText("Checking connection…").assertIsNotEnabled()
        rule.onNodeWithText("Install Computer").assertIsNotEnabled()
        assertEquals(1,tests)
    }

    @Test @Config(qualifiers="w840dp-h400dp")
    fun landscapeHasItsOwnBackHistoryAndWorkingPowerAndTaskPages() {
        var enabled by mutableStateOf(true)
        var task="";var approved=""
        val goal=AgenticGoal("review-1","Review a fixture task",GoalStatus.WAITING_APPROVAL)
        rule.setContent { MeowmbaTheme(darkTheme=false) { SettingsScreen(storage,
            initialPage=COMPUTER_TOOLS_TITLE,agenticRuntimeV1=enabled,onAgenticRuntimeV1Change={enabled=it},
            agenticGoals=listOf(goal),onCreateAgenticGoal={task=it},onApproveAgenticGoal={approved=it},onClose={}) } }
        tap("Legacy tasks")
        fill("What should Meow do?","Create a test report")
        tap("Create task");assertEquals("Create a test report",task)
        tap("Approve");assertEquals("review-1",approved)
        back();tap("Power & sleep")
        rule.onNodeWithText("Workspace folder").assertDoesNotExist()
        render("power-landscape")
        back();tap("Set up Computer");tap("A Linux server");fill("Server address","server.example");tap("Continue")
        back()
        rule.onNodeWithText("Server address").assertTextContains("server.example")
    }

    @Test fun setupRestoresItsStepAndAddressWithoutSavingThePasswordInInstanceState() {
        val restoration=StateRestorationTester(rule)
        restoration.setContent { MeowmbaTheme { SettingsScreen(storage,initialPage=COMPUTER_TOOLS_TITLE,onClose={}) } }
        tap("Set up Computer");tap("A Linux server");fill("Server address","server.example");tap("Continue")
        fill("Username","artist")
        rule.onNode(hasText("Password") and hasSetTextAction()).performScrollTo().performTextInput("private-fixture")
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Server setup · Step 2 of 3").assertExists()
        rule.onNodeWithText("Username").assertTextContains("artist")
        rule.onNode(hasText("Password") and hasSetTextAction()).assertTextEquals("Password", "")
        back();rule.onNodeWithText("Server address").assertTextContains("server.example")
    }

    @Test @Config(qualifiers="w320dp-h720dp")
    fun compactLargeTextKeepsEverySetupActionReachable() {
        rule.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                MeowmbaTheme(darkTheme=true) { SettingsScreen(storage,initialPage=COMPUTER_TOOLS_TITLE,onClose={}) }
            }
        }
        tap("Set up Computer");tap("A Linux server")
        fill("Server address","server.example");tap("Continue")
        fill("Username","artist");tap("Review connection")
        rule.onNodeWithText("Save & test connection").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Install Computer").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        render("server-large-text")
    }

    private fun render(name:String) = rule.runOnIdle {
        val root=rule.activity.window.decorView
        val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(b))
        val out=File("build/reports/computer135/$name.png");out.parentFile?.mkdirs()
        out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
}
