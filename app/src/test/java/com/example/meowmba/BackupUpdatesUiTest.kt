package com.example.meowmba

import android.content.pm.PackageInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.backup.BackupUpdatePolicy
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.ui.companion.BackupUpdatesPanel
import com.example.meowmba.ui.companion.SettingsScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupUpdatesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun installCurrentPackageFixture() {
        val context = compose.activity
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = context.packageName
            versionName = "1.0"
            setLongVersionCode(1L)
        })
    }

    @Test
    fun initialWarningShowsMaskedPasswordsAndSaveIsDisabled() {
        compose.setContent { MaterialTheme { BackupUpdatesPanel() } }

        compose.onNodeWithText("Do not uninstall first", substring = true).assertExists()
        compose.onNodeWithText("Save encrypted backup").assertIsNotEnabled()
        compose.onNodeWithText("Backup password (at least 12 characters)")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.onNodeWithText("Repeat password to save a backup")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun saveRequiresTwelveCharactersAndMatchingRepeatButRestoreNeedsOnlyTwelve() {
        compose.setContent { MaterialTheme { BackupUpdatesPanel() } }
        val password = compose.onNodeWithText("Backup password (at least 12 characters)")
        val repeat = compose.onNodeWithText("Repeat password to save a backup")

        password.performTextInput("short")
        repeat.performTextInput("short")
        compose.onNodeWithText("Save encrypted backup").assertIsNotEnabled()
        compose.onNodeWithText("Restore encrypted backup").assertIsNotEnabled()

        password.performTextClearance()
        repeat.performTextClearance()
        password.performTextInput("long-enough-password")
        repeat.performTextInput("different-password")
        compose.onNodeWithText("Restore encrypted backup").assertIsEnabled()
        compose.onNodeWithText("Save encrypted backup").assertIsNotEnabled()

        repeat.performTextClearance()
        repeat.performTextInput("long-enough-password")
        compose.onNodeWithText("Save encrypted backup").assertIsEnabled()
    }

    @Test
    fun cleanupCannotRemoveOtherEditionWithoutVerifiedReceipt() {
        val other = PackageInfo().apply {
            packageName = BackupUpdatePolicy.PLAY
            versionName = "1.0"
            setLongVersionCode(1L)
        }
        shadowOf(compose.activity.packageManager).installPackage(other)
        compose.setContent { MaterialTheme { BackupUpdatesPanel() } }

        compose.onNodeWithText("Move from another Meow edition").assertExists()
        compose.onNodeWithText("Ask Android to remove other edition")
            .assertIsNotEnabled()
        compose.onNodeWithText("Restore a current backup made by the other edition first.", substring = true)
            .assertExists()
    }

    @Test
    fun backupUpdatesCategoryIsReachableFromSettings() {
        val storage = mock(SecureStorageManager::class.java)
        compose.setContent { MaterialTheme { SettingsScreen(secureStorage = storage, onClose = {}) } }

        compose.onNodeWithText("Backup & updates").performScrollTo().performClick()
        compose.onNodeWithText("Keep your keys and creations safe").assertExists()
        compose.onNodeWithText("Save encrypted backup").assertExists()
        compose.runOnIdle {
            val root = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/backup-updates-page.png").apply { parentFile!!.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
