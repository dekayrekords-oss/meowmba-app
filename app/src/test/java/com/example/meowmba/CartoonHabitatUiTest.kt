package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.core.background.BackgroundKind
import com.example.meowmba.core.background.BackgroundSelection
import com.example.meowmba.ui.companion.BackgroundScreen
import com.example.meowmba.ui.companion.CartoonGagKind
import com.example.meowmba.ui.companion.CartoonHabitatGag
import com.example.meowmba.ui.companion.IllustratedEnvironmentBackground
import com.example.meowmba.ui.companion.cartoonGagLayout
import com.example.meowmba.ui.companion.illustratedRoomPlacement
import com.example.meowmba.ui.companion.illustratedSourceTableAnchor
import com.example.meowmba.ui.companion.illustratedSourceFocusX
import com.example.meowmba.ui.theme.MeowmbaTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real native room renders, not mock thumbnails: a missing/failed bitmap must fail QA. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CartoonHabitatUiTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test @Config(qualifiers = "w360dp-h800dp")
    fun portraitRoomArtLoadsWithVisibleInteractiveToys() = renderRooms("portrait")

    @Test @Config(qualifiers = "w840dp-h400dp")
    fun landscapeRoomArtLoadsWithVisibleInteractiveToys() = renderRooms("landscape")

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun compactPickerReachesEveryNewRoomWithoutChangingSize() = chooseRooms("compact")

    @Test @Config(qualifiers = "w840dp-h400dp")
    fun landscapePickerReachesEveryNewRoomWithoutChangingSize() = chooseRooms("landscape")

    private fun chooseRooms(name: String) {
        var selection by mutableStateOf(BackgroundSelection(dimPercent = 47))
        var habitat by mutableStateOf(false)
        var backgroundChanges = 0
        var scaleChanges = 0
        var closes = 0
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            BackgroundScreen(selection, onSelect = { selection = it; backgroundChanges++ },
                onClose = { closes++ }, avatarScale = 2.35f, onAvatarScale = { scaleChanges++ },
                habitatMode = habitat, onHabitatMode = { habitat = it })
        } }
        rule.onNodeWithText("Habitats").performScrollTo().performClick()
        assertEquals("Browsing does not choose a room", 0, backgroundChanges)
        assertFalse(habitat)
        rooms.forEachIndexed { index, room ->
            rule.onNode(hasText(room.label) and hasClickAction())
                .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            assertEquals(index + 1, backgroundChanges)
            assertEquals(BackgroundKind.ANIMATION, selection.kind)
            assertEquals(room.id, selection.value)
            assertEquals("Existing backdrop dimming survives habitat selection", 47, selection.dimPercent)
            assertTrue(habitat)
        }
        // New rooms do not bury the original collection or the shared scale control.
        rule.onNode(hasText(BackgroundAnimation.KITCHEN.label) and hasClickAction())
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Avatar size").performScrollTo().assertIsDisplayed()
        assertEquals("Selecting rooms must not move the size slider", 0, scaleChanges)
        rule.onNodeWithText("Cartoon worlds").performScrollTo()
        render("picker-$name")
        rule.onNodeWithContentDescription("Close backgrounds").performScrollTo().performClick()
        assertEquals(1, closes)
    }

    private fun renderRooms(name: String) {
        var room by mutableStateOf(rooms.first())
        var toy by mutableStateOf(newToy(room))
        var toyClock by mutableStateOf(0f)
        var stage = Rect.Zero
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { stage = it.boundsInWindow() }) {
                IllustratedEnvironmentBackground(room, Modifier.fillMaxSize(), clock = { .21f }, slow = { .34f })
                Canvas(Modifier.fillMaxSize()) { toy.draw(this, toyClock) }
            }
        } }
        rooms.forEach { current ->
            rule.runOnIdle { room = current; toy = newToy(current); toyClock = 0f }
            val source = rule.activity.assets.open("backgrounds/painted/${current.id}.png").use {
                requireNotNull(BitmapFactory.decodeStream(it)) { "Missing room art: ${current.id}" }
            }
            try {
                // IO decode is asynchronous. Native pixels beneath the atmosphere/interaction
                // plane identify the loaded illustration; merely waiting for Compose idle can
                // capture the vector fallback and make an absent illustration look successful.
                rule.waitUntil(10_000) {
                    if (stage.width < 1f || stage.height < 1f) false else {
                        val actual = captureStage(stage)
                        try { sourcePixelsVisible(actual, source, current) } finally { actual.recycle() }
                    }
                }
                val layout = requireNotNull(cartoonGagLayout(stage.width, stage.height))
                rule.runOnIdle {
                    assertTrue(toy.tap(layout.center, stage.width, stage.height, 0f,
                        active = true, avatarOwnsTap = false, propOwnsTap = false))
                    toyClock = .4f
                }
                render("${current.id}-$name-play")
                val actual = captureStage(stage)
                try {
                    listOf(0 to 0, actual.width - 1 to 0, 0 to actual.height - 1,
                        actual.width - 1 to actual.height - 1).forEach { (x, y) ->
                        assertEquals("Room covers the viewport corner", 255, Color.alpha(actual.getPixel(x, y)))
                    }
                } finally { actual.recycle() }
            } finally { source.recycle() }
        }
    }

    /** Read only unobscured art pixels. Placement mathematics has its own geometry suite. */
    private fun sourcePixelsVisible(actual: Bitmap, source: Bitmap, room: BackgroundAnimation): Boolean {
        val placement = illustratedRoomPlacement(source.width, source.height,
            actual.width.toFloat(), actual.height.toFloat(), illustratedSourceTableAnchor(room), illustratedSourceFocusX(room))
        return listOf(.17f to .86f, .52f to .94f, .83f to .90f).all { (u, v) ->
            val x = (actual.width * u).toInt()
            val y = (actual.height * v).toInt()
            val sx = ((x - placement.x.roundToInt()) * source.width.toFloat() / placement.width.roundToInt())
                .roundToInt().coerceIn(1, source.width - 2)
            val sy = ((y - placement.y.roundToInt()) * source.height.toFloat() / placement.height.roundToInt())
                .roundToInt().coerceIn(1, source.height - 2)
            val pixel = actual.getPixel(x, y)
            // Scaling filters can sample either neighbor beside an ink edge.
            (-1..1).any { dx -> (-1..1).any { dy -> colorDistance(pixel, source.getPixel(sx + dx, sy + dy)) <= 14 } }
        }
    }

    private fun colorDistance(a: Int, b: Int) = maxOf(abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)), abs(Color.blue(a) - Color.blue(b)))

    private fun captureStage(stage: Rect): Bitmap {
        val whole = captureWindow()
        val cropped = Bitmap.createBitmap(whole, stage.left.roundToInt(), stage.top.roundToInt(),
            stage.width.roundToInt(), stage.height.roundToInt())
        if (cropped !== whole) whole.recycle()
        return cropped
    }

    private fun captureWindow(): Bitmap = rule.runOnIdle {
        val root = rule.activity.window.decorView
        Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
            root.draw(android.graphics.Canvas(it))
        }
    }

    private fun render(name: String) {
        rule.waitForIdle()
        val bitmap = captureWindow()
        try {
            File("build/reports/cartoon117/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private fun newToy(room: BackgroundAnimation) = CartoonHabitatGag(requireNotNull(CartoonGagKind.forBackground(room.id)))

    private val rooms = listOf(BackgroundAnimation.CARTOON_PORTAL_ARCADE,
        BackgroundAnimation.CARTOON_MOON_GARDEN, BackgroundAnimation.CARTOON_ALIEN_AQUARIUM,
        BackgroundAnimation.CARTOON_METEOR_GARAGE, BackgroundAnimation.CARTOON_SNACK_LAB,
        BackgroundAnimation.CARTOON_LAUNDROMAT, BackgroundAnimation.CARTOON_DINER)
}
