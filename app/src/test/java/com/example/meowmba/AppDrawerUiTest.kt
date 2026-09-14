package com.example.meowmba

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w375dp-h812dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppDrawerUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val apps = listOf(
        app("Calculator", DrawerAppType.WORK), app("Camera", DrawerAppType.PHOTO),
        app("Clock", DrawerAppType.OTHER), app("Files", DrawerAppType.WORK),
        app("Galactic Racer", DrawerAppType.GAME), app("Google", DrawerAppType.OTHER),
        app("Maps", DrawerAppType.MAPS), app("Messages", DrawerAppType.SOCIAL),
        app("Music", DrawerAppType.MUSIC), app("Photos", DrawerAppType.PHOTO),
        app("Space Chess", DrawerAppType.GAME), app("Video", DrawerAppType.VIDEO),
    )
    private val folder = DrawerFolder("games", "Games", listOf(apps[4].packageName, apps[10].packageName))

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun compactDrawerFitsEightFavoritesAndKeepsAppsReachable() = viewport("320-dark", true)

    @Test @Config(qualifiers = "w375dp-h812dp")
    fun phoneDrawerUsesTheSharedLightCartoonTheme() = viewport("375-light", false)

    @Test @Config(qualifiers = "w414dp-h896dp")
    fun largeTextDrawerKeepsSearchAndAppActionsReachable() = viewport("414-large-text", true, 1.4f)

    @Test @Config(qualifiers = "w768dp-h432dp")
    fun landscapeDrawerScrollsFavoritesWithoutHidingTheAppGrid() = viewport("768-landscape-large-text", true, 1.4f)

    private fun viewport(name: String, dark: Boolean, fontScale: Float = 1f) {
        var launched: String? = null
        var returns = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MeowmbaTheme(darkTheme = dark) {
                    AppDrawerScreen(onClose = { returns++ }, installedApps = apps,
                        pins = apps.take(8).map { it.packageName }, folders = listOf(folder), onLaunchApp = { launched = it })
                }
            }
        }
        rule.onNodeWithContentDescription("Return to avatar").assertIsDisplayed()
        rule.onNodeWithTag("drawer-search").assertIsDisplayed()
        rule.onNodeWithContentDescription("Search in Google app").assertIsNotEnabled()
        render("$name-top")
        val lastPin = "pin:${apps[7].packageName}"
        scrollTo(lastPin)
        assertInsideRoot(lastPin)
        rule.onNodeWithTag(lastPin).performClick()
        assertEquals(apps[7].packageName, launched)
        val lastApp = "app:${apps.last().packageName}"
        scrollTo(lastApp)
        assertInsideRoot(lastApp)
        rule.onNodeWithTag(lastApp).performClick()
        assertEquals(apps.last().packageName, launched)
        render("$name-apps")
        rule.onNodeWithContentDescription("Return to avatar").performClick()
        assertEquals(1, returns)
    }

    @Test fun searchRetainsAppMatchingTasksGoogleClearAndFailureResponse() {
        val submitted = mutableListOf<Triple<String, Int, String?>>()
        var google: String? = null
        var response by mutableStateOf<String?>(null)
        var microphone = 0
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            AppDrawerScreen(onClose = {}, installedApps = apps,
                onSubmitQuery = { q, count, label -> submitted += Triple(q, count, label) },
                onSearchGoogle = { google = it; response = "Google app is unavailable." },
                answer = response, onMicClick = { microphone++ })
        } }
        rule.onNodeWithTag("drawer-search").performTextInput("Camera")
        rule.onNodeWithContentDescription("Submit search").performClick()
        assertEquals(Triple("Camera", 1, "Camera"), submitted.single())
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithTag("drawer-search").performTextInput("set a timer for five minutes")
        rule.onNodeWithTag("drawer-search").performImeAction()
        assertEquals("set a timer for five minutes", submitted.last().first)
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithTag("drawer-search").performTextInput("search for cartoon planets")
        rule.onNodeWithContentDescription("Search in Google app").performClick()
        assertEquals("cartoon planets", google)
        rule.onNodeWithText("Google app is unavailable.").assertExists()
        rule.onNodeWithTag("drawer-search").assertTextContains("search for cartoon planets")
        rule.onNodeWithContentDescription("Speak").performClick()
        assertEquals(1, microphone)
        render("google-unavailable")
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithText("Google app is unavailable.").assertDoesNotExist()
        rule.onNodeWithContentDescription("Submit search").assertIsNotEnabled()
    }

    @Test fun voiceCommitsExactlyOnceAndKeepsLiveListeningText() {
        var voice by mutableStateOf<String?>(null)
        var partial by mutableStateOf<String?>(null)
        val submissions = mutableListOf<String>()
        var consumed = 0
        rule.setContent { MeowmbaTheme {
            AppDrawerScreen(onClose = {}, installedApps = apps, listening = true, listenPartial = partial,
                voiceCommit = voice, onVoiceConsumed = { consumed++; voice = null },
                onSubmitQuery = { q, _, _ -> submissions += q })
        } }
        rule.runOnIdle { partial = "open camera" }
        rule.onNodeWithTag("drawer-search").assertTextContains("open camera")
        rule.runOnIdle { voice = "open Camera" }
        rule.waitForIdle()
        assertEquals(listOf("open Camera"), submissions)
        assertEquals(1, consumed)
        rule.onNodeWithContentDescription("Stop listening").assertExists()
    }

    @Test fun changingQueryOrEnteringFolderRevealsSearchActionsAfterDeepScroll() {
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            AppDrawerScreen(onClose = {}, installedApps = apps, folders = listOf(folder),
                pins = apps.take(8).map { it.packageName })
        } }
        scrollTo("app:${apps.last().packageName}")
        rule.onNodeWithTag("drawer-search").performTextInput("Camera")
        rule.onNodeWithContentDescription("Search in Google app").assertIsDisplayed()
        rule.onNodeWithContentDescription("Speak").assertIsDisplayed()
        rule.onNodeWithTag("app:${apps[1].packageName}").assertIsDisplayed()
        rule.onNodeWithContentDescription("Clear search").performClick()
        scrollTo("folder:games")
        rule.onNodeWithTag("folder:games").performClick()
        rule.onNodeWithContentDescription("Back to all apps").assertIsDisplayed()
        rule.onNodeWithContentDescription("Search in Google app").assertIsDisplayed()
        rule.onNodeWithContentDescription("Speak").assertIsDisplayed()
    }

    @Test fun foldersSupportCreateAddRemoveRenameDeleteAndBackToAvatar() {
        var folders by mutableStateOf(listOf(folder))
        var closes = 0
        rule.setContent { MeowmbaTheme(darkTheme = true) {
            AppDrawerScreen(onClose = { closes++ }, installedApps = apps, folders = folders,
                onCreateFolder = { name, pkg -> folders = DrawerCatalog.createFolder(folders, name, pkg, "new") },
                onAddToFolder = { id, pkg -> folders = DrawerCatalog.addToFolder(folders, id, pkg) },
                onRemoveFromFolder = { id, pkg -> folders = DrawerCatalog.removeFromFolder(folders, id, pkg) },
                onRenameFolder = { id, name -> folders = DrawerCatalog.renameFolder(folders, id, name) },
                onDeleteFolder = { id -> folders = DrawerCatalog.deleteFolder(folders, id) })
        } }
        scrollTo("folder:games")
        rule.onNodeWithTag("folder:games").performClick()
        rule.onNodeWithContentDescription("Back to all apps").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(0, closes)
        rule.onNodeWithText("App drawer").assertExists()
        rule.onNodeWithTag("drawer-grid").performScrollToNode(hasText("Organize"))
        rule.onNodeWithText("Organize").performClick()
        rule.onNodeWithText("New folder").performClick()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        render("folder-editor")
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("folder-editor").assertDoesNotExist()
        assertEquals(listOf(folder), folders)
        assertEquals(0, closes)
        rule.onNodeWithText("New folder").performClick()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithTag("folder-name").performTextInput("Mission control")
        rule.onNodeWithText("Save").performClick()
        assertEquals("Mission control", folders.last().name)
        scrollTo("app:${apps[0].packageName}")
        rule.onNodeWithTag("app:${apps[0].packageName}").performTouchInput { longClick() }
        rule.onNodeWithText("Add to Mission control").performClick()
        assertTrue(apps[0].packageName in folders.last().packages)
        scrollTo("folder:new")
        rule.onNodeWithTag("folder:new").performClick()
        scrollTo("app:${apps[0].packageName}")
        rule.onNodeWithTag("app:${apps[0].packageName}").performTouchInput { longClick() }
        rule.onNodeWithText("Remove from Mission control").performClick()
        assertTrue(folders.last().packages.isEmpty())
        rule.onNodeWithTag("drawer-grid").performScrollToNode(hasText("Folder options"))
        rule.onNodeWithText("Folder options").performClick()
        rule.onNodeWithText("Rename").performClick()
        rule.onNodeWithTag("folder-name").performTextReplacement("Tools")
        rule.onNodeWithText("Save").performClick()
        assertEquals("Tools", folders.last().name)
        rule.onNodeWithText("Folder options").performClick()
        rule.onNodeWithText("Delete folder").performClick()
        assertEquals(listOf(folder), folders)
        rule.onNodeWithText("App drawer").assertExists()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, closes)
    }

    @Test fun sortingFilteringAndFavoriteCapacityRemainActionable() {
        var pins by mutableStateOf(apps.take(8).map { it.packageName })
        rule.setContent { MeowmbaTheme {
            AppDrawerScreen(onClose = {}, installedApps = apps, pins = pins,
                onTogglePin = { pkg -> pins = if (pkg in pins) pins - pkg else pins + pkg })
        } }
        scrollTo("app:${apps.last().packageName}")
        rule.onNodeWithTag("app:${apps.last().packageName}").performTouchInput { longClick() }
        rule.onNodeWithText("Favorites full (8)").assertIsNotEnabled()
        rule.onNodeWithText("Close").performClick()
        scrollTo("pin:${apps[0].packageName}")
        rule.onNodeWithTag("pin:${apps[0].packageName}").performTouchInput { longClick() }
        rule.onNodeWithText("Unpin from favorites").performClick()
        assertEquals(7, pins.size)
        scrollTo("app:${apps.last().packageName}")
        rule.onNodeWithTag("app:${apps.last().packageName}").performTouchInput { longClick() }
        rule.onNodeWithText("Pin to favorites").performClick()
        assertEquals(8, pins.size)
        assertTrue(apps.last().packageName in pins)
        rule.onNodeWithTag("drawer-grid").performScrollToNode(hasText("Sort: Name"))
        rule.onNodeWithText("Sort: Name").performClick()
        rule.onNodeWithText("Size").performClick()
        rule.onNodeWithText("Sort: Size").assertExists()
        rule.onNodeWithText("All types").performClick()
        rule.onNodeWithText("Games").performClick()
        scrollTo("app:${apps[4].packageName}")
        rule.onNodeWithTag("app:${apps[0].packageName}").assertDoesNotExist()
        rule.onNodeWithTag("app:${apps[4].packageName}").assertExists()
    }

    @Test fun emptyCatalogShowsAnExplanationWithoutInventedApps() {
        rule.setContent { MeowmbaTheme { AppDrawerScreen(onClose = {}, installedApps = emptyList()) } }
        rule.onNodeWithText("No launchable apps found").assertExists()
        rule.onNodeWithText("Loading your apps…").assertDoesNotExist()
        rule.onNodeWithTag("drawer-search").performTextInput("missing")
        rule.onNodeWithText("No matching apps").assertExists()
        rule.onNodeWithText("Clear filters").performClick()
        rule.onNodeWithText("No launchable apps found").assertExists()
    }

    @Test fun unavailableFavoritesAreRemovedOnlyByTheExplicitOrganizeAction() {
        val missing = "preview.uninstalled"
        var pins by mutableStateOf(apps.take(7).map { it.packageName } + missing)
        val removed = mutableListOf<String>()
        rule.setContent { MeowmbaTheme {
            AppDrawerScreen(onClose = {}, installedApps = apps, pins = pins,
                onTogglePin = { pkg -> removed += pkg; pins = pins - pkg })
        } }
        assertTrue(removed.isEmpty())
        rule.onNodeWithText("Organize").performClick()
        rule.onNodeWithText("1 saved favorite is no longer available.").assertExists()
        assertEquals(8, pins.size)
        rule.onNodeWithText("Remove missing pins").performClick()
        assertEquals(listOf(missing), removed)
        assertEquals(apps.take(7).map { it.packageName }, pins)
        rule.onNodeWithText("Remove missing pins").assertDoesNotExist()
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag("drawer-grid").performScrollToNode(hasTestTag(tag))

    private fun assertInsideRoot(tag: String) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val tile = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag left ${tile.left} outside $root", tile.left >= root.left)
        assertTrue("$tag right ${tile.right} outside $root", tile.right <= root.right)
        assertTrue("$tag has no visible height", tile.height > 0f)
    }

    private fun render(name: String) {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            java.io.File("build/reports/drawer117/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun app(label: String, type: DrawerAppType) = DrawerApp(label, "preview.${label.lowercase().replace(' ', '.')}", label.lowercase(),
        installedAtMs = 1_700_000_000_000, sizeBytes = 15L * 1024 * 1024, type = type)
}
