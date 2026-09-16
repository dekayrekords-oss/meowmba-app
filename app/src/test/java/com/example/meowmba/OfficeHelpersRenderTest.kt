package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.*
import com.example.meowmba.ui.theme.MeowmbaTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfficeHelpersRenderTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val helpers = listOf(AvatarAnimal.CLIPPY, AvatarAnimal.WIZARD)
    private val painter = LumenPainter()

    @Test fun helpersHaveDistinctArtAndVisibleBlinkGazeAndSpeech() {
        val plain = render(LumenFrame(), 420, 600)
        val designs = helpers.map { render(settled(it), 420, 600) }
        try {
            assertTrue("Clippy and the wizard have different silhouettes", changedPixels(designs[0], designs[1]) > 800)
            helpers.forEachIndexed { index, helper ->
                assertTrue("$helper adds visible anatomy to Lumen", changedPixels(plain, designs[index]) > 800)
                val base = settled(helper).copy(leftOpen = 1f, rightOpen = 1f, gazeX = 0f, gazeY = 0f, mouthOpen = 0f)
                val idle = render(base, 420, 600)
                try {
                    for ((name, expression) in listOf("blink" to base.copy(leftOpen = 0f, rightOpen = 0f),
                        "gaze" to base.copy(gazeX = .75f, gazeY = -.3f), "speech" to base.copy(mouthOpen = 1f))) {
                        val changed = render(expression, 420, 600)
                        try { assertTrue("$helper $name must visibly move", changedPixels(idle, changed) > 30) }
                        finally { changed.recycle() }
                    }
                } finally { idle.recycle() }
            }
        } finally { plain.recycle(); designs.forEach(Bitmap::recycle) }
    }

    @Test fun helperExtremesStayInsidePortraitAndLandscapeWithFixedPrivacyMarks() {
        for ((width, height) in listOf(360 to 800, 840 to 360)) {
            val privacy = render(LumenFrame(presence = 0f), width, height, privacy = true)
            val blank = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            assertFalse("Privacy remains visible without an avatar", privacy.sameAs(blank))
            blank.recycle()
            try {
                for (helper in helpers) for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                    val frame = settled(helper, x, y).copy(driftX = x, driftY = y)
                    val image = render(frame, width, height)
                    val hidden = render(frame.copy(presence = 0f), width, height, privacy = true)
                    try {
                        assertInside(image, "$helper $width/$height at $x,$y")
                        assertTrue("Privacy cannot morph or roam with $helper", privacy.sameAs(hidden))
                    } finally { image.recycle(); hidden.recycle() }
                }
            } finally { privacy.recycle() }
        }
    }

    @Test fun partialHelperMorphsFitAnAlreadyRoamingCharacter() {
        for (helper in helpers) for (weight in listOf(.03f, .1f, .25f, .5f, .75f)) {
            for (side in listOf(-1f, 1f)) {
                val form = if (helper == AvatarAnimal.CLIPPY) AvatarFormTraits(clippy = weight)
                    else AvatarFormTraits(wizard = weight)
                val frame = LumenFrame(form = form, driftX = side, driftY = -1f,
                    headX = side, roll = side * 24f, screenRoll = side * 14f)
                val image = render(frame, 840, 360)
                try { assertInside(image, "$helper partial $weight") } finally { image.recycle() }
            }
        }
    }

    @Test fun nativeOfficeHelperContactSheetsShowExpressionsAndShortStages() {
        val labels = listOf("Attentive", "Blink", "Look over", "Talking")
        val sheet = Bitmap.createBitmap(1440, 1120, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet).apply { drawColor(0xFF142129.toInt()) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4E3C5.toInt(); textSize = 19f }
        try {
            helpers.forEachIndexed { row, helper ->
                val base = settled(helper).copy(leftOpen = 1f, rightOpen = 1f, gazeX = 0f, gazeY = 0f, mouthOpen = 0f)
                val frames = listOf(base, base.copy(leftOpen = 0f, rightOpen = 0f),
                    base.copy(gazeX = .75f, gazeY = -.3f, roll = 8f), base.copy(mouthOpen = .85f, smile = .45f))
                frames.forEachIndexed { column, frame ->
                    val image = render(frame, 360, 500)
                    try {
                        canvas.drawBitmap(image, column * 360f, row * 560f + 38f, null)
                        canvas.drawText("${helper.label} · ${labels[column]}", column * 360f + 15f, row * 560f + 28f, text)
                    } finally { image.recycle() }
                }
                val landscape = render(base, 840, 360)
                try { save(landscape, "${helper.key}-landscape") } finally { landscape.recycle() }
            }
            save(sheet, "expressions-contact")
        } finally { sheet.recycle() }
    }

    @Test @Config(qualifiers = "w320dp-h720dp")
    fun compactAvatarPickerCanChooseBothHelpersWithoutChangingOtherControls() {
        var animal by mutableStateOf(AvatarAnimal.CAT)
        var formChanges = 0
        var otherChanges = 0
        rule.setContent { MeowmbaTheme(darkTheme = false) {
            AvatarScreen(AvatarStyle.LUMEN, onSelectStyle = { otherChanges++ }, animations = emptyList(),
                onPlayAnimation = { otherChanges++ }, onClose = {}, animal = animal,
                onSelectAnimal = { animal = it; formChanges++ },
                onSelectEyeColor = { otherChanges++ }, onSelectFeatureColor = { otherChanges++ },
                onSelectGlowColor = { otherChanges++ }, onAvatarScale = { otherChanges++ })
        } }
        assertEquals(0, formChanges)
        helpers.forEachIndexed { index, helper ->
            rule.onNodeWithText(helper.label).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            rule.runOnIdle { assertEquals(helper, animal); assertEquals(index + 1, formChanges) }
        }
        assertEquals(0, otherChanges)
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try { root.draw(android.graphics.Canvas(image)); save(image, "avatar-picker-compact") }
            finally { image.recycle() }
        }
    }

    private fun settled(helper: AvatarAnimal, gazeX: Float = 0f, gazeY: Float = 0f): LumenFrame {
        val rig = LumenRig(814)
        var frame = LumenFrame()
        repeat(180) { frame = rig.step(it / 60f, LumenInput(gazeX, gazeY, true, animal = helper,
            lean = gazeX, lurch = gazeY, driveWeight = 1f, brace = 1f,
            screenRoll = gazeX * 14f, tilt = gazeX * 18f)) }
        return frame
    }

    private fun render(frame: LumenFrame, width: Int, height: Int, privacy: Boolean = false): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { image ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image.asImageBitmap()), Size(width.toFloat(), height.toFloat())) {
                painter.draw(this, frame, Color(AvatarColors.DEFAULT_EYE), Color(0xFF419A9C),
                    featureColor = Color(0xFFE8D5B9), cameraOff = privacy, micOff = privacy, speakerOff = privacy)
            }
        }

    private fun changedPixels(a: Bitmap, b: Bitmap): Int {
        var changes = 0
        for (x in 0 until a.width) for (y in 0 until a.height)
            if (a.getPixel(x, y) != b.getPixel(x, y)) changes++
        return changes
    }

    private fun assertInside(image: Bitmap, label: String) {
        for (x in 0 until image.width) {
            assertTrue("$label top", android.graphics.Color.alpha(image.getPixel(x, 0)) < 110)
            assertTrue("$label bottom", android.graphics.Color.alpha(image.getPixel(x, image.height - 1)) < 110)
        }
        for (y in 0 until image.height) {
            assertTrue("$label left", android.graphics.Color.alpha(image.getPixel(0, y)) < 110)
            assertTrue("$label right", android.graphics.Color.alpha(image.getPixel(image.width - 1, y)) < 110)
        }
    }

    private fun save(image: Bitmap, name: String) {
        File("build/reports/office-helpers/$name.png").apply { parentFile.mkdirs() }.outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
