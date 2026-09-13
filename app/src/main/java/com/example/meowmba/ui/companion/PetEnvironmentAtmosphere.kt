package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import com.example.meowmba.core.background.BackgroundAnimation
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/** Cached authored materials, not a per-frame palette/gradient construction. */
internal class PetEnvironmentMaterial(val wallTop: Long, val wallBottom: Long, val tabletop: Long) {
    val wall = Brush.verticalGradient(listOf(Color(wallTop), Color(wallBottom)))
    private var tableHeight = -1f
    private var table: Brush = Brush.verticalGradient(listOf(Color(tabletop), Color(tabletop)))
    fun table(height: Float): Brush {
        if (height != tableHeight) {
            tableHeight = height
            val color = Color(tabletop)
            table = Brush.verticalGradient(listOf(lerp(color, Color.White, .09f), color, lerp(color, Color.Black, .22f)),
                startY = height * PET_ENVIRONMENT_TABLE_Y, endY = height)
        }
        return table
    }
}

/** One instance belongs to a painter; its resize cache is never shared by concurrent canvases. */
internal class PetEnvironmentAtmosphere {
    private val materials = mapOf(
        BackgroundAnimation.KITCHEN to PetEnvironmentMaterial(0xFF334A48, 0xFF182B2A, 0xFFAA8663),
        BackgroundAnimation.LIBRARY to PetEnvironmentMaterial(0xFF302C3D, 0xFF191725, 0xFF76533D),
        BackgroundAnimation.GREENHOUSE to PetEnvironmentMaterial(0xFF294C45, 0xFF102F27, 0xFF64775A),
        BackgroundAnimation.WORKSHOP to PetEnvironmentMaterial(0xFF394451, 0xFF19232D, 0xFF967454),
        BackgroundAnimation.POND to PetEnvironmentMaterial(0xFF243C4A, 0xFF122B38, 0xFF566C63),
        BackgroundAnimation.ORCHARD to PetEnvironmentMaterial(0xFF4C4F38, 0xFF263929, 0xFF947052),
        BackgroundAnimation.CAFE to PetEnvironmentMaterial(0xFF45353C, 0xFF241E2C, 0xFF956650),
        BackgroundAnimation.OBSERVATORY to PetEnvironmentMaterial(0xFF182840, 0xFF0C1429, 0xFF4C5B76),
        BackgroundAnimation.BEACH to PetEnvironmentMaterial(0xFF295967, 0xFF173640, 0xFFAE8F62),
        BackgroundAnimation.BEDROOM to PetEnvironmentMaterial(0xFF443850, 0xFF231E34, 0xFF826E76),
    )
    private val beam = Path().apply {
        moveTo(.07f, .12f); lineTo(.23f, .12f); lineTo(.48f, .76f); lineTo(.27f, .76f); close()
    }
    private val beamLight = Brush.verticalGradient(listOf(Color(0x38D6DDB0), Color(0x0CD6DDB0), Color.Transparent), startY = .1f, endY = .77f)
    private val edgeShade = Brush.horizontalGradient(listOf(Color(0x23050D0C), Color.Transparent, Color.Transparent, Color(0x23050D0C)), startX = 0f, endX = 1f)
    private val warmPool = Brush.radialGradient(listOf(Color(0x28E8B16B), Color.Transparent), Offset(.86f, .64f), .26f)
    private val moonPool = Brush.radialGradient(listOf(Color(0x189EC6DD), Color.Transparent), Offset(.83f, .2f), .26f)
    private val rippleStroke = Stroke(1.4f)

    fun material(animation: BackgroundAnimation) = materials[animation] ?: materials.getValue(BackgroundAnimation.BEDROOM)

    /** Light belongs behind furniture, extends only to the stationary stage boundary. */
    fun drawLight(scope: DrawScope, animation: BackgroundAnimation, slow: Float) = with(scope) {
        scale(size.width, size.height, Offset.Zero) {
            drawRect(edgeShade, size = Size(1f, PET_ENVIRONMENT_TABLE_Y))
            when (animation) {
                BackgroundAnimation.KITCHEN, BackgroundAnimation.GREENHOUSE, BackgroundAnimation.ORCHARD ->
                    drawPath(beam, beamLight, alpha = .78f + .12f * sin(slow * TAU))
                BackgroundAnimation.LIBRARY, BackgroundAnimation.CAFE, BackgroundAnimation.BEDROOM ->
                    drawCircle(warmPool, radius = .26f, center = Offset(.86f, .64f), alpha = .87f + .08f * sin(slow * TAU * 3f))
                BackgroundAnimation.POND, BackgroundAnimation.OBSERVATORY ->
                    drawCircle(moonPool, radius = .26f, center = Offset(.83f, .2f))
                else -> Unit
            }
        }
    }

    /** Eight or fewer ambient accents. All phases come from the established room clock. */
    fun drawDetails(scope: DrawScope, animation: BackgroundAnimation, clock: Float, slow: Float) = with(scope) {
        when (animation) {
            BackgroundAnimation.KITCHEN, BackgroundAnimation.LIBRARY, BackgroundAnimation.GREENHOUSE,
            BackgroundAnimation.WORKSHOP, BackgroundAnimation.CAFE, BackgroundAnimation.BEDROOM -> {
                // Local light catches drifting motes at the room edges, away from face features.
                val warm = animation == BackgroundAnimation.LIBRARY || animation == BackgroundAnimation.CAFE || animation == BackgroundAnimation.BEDROOM
                val color = if (warm) Color(0xFFC6A574) else Color(0xFFACC7B2)
                repeat(8) { i ->
                    val side = if (i % 2 == 0) .13f else .88f
                    val phase = clock * TAU + i * 1.61f
                    val x = side + sin(phase) * .046f
                    val y = .22f + i % 4 * .125f + cos(phase * 2f) * .018f
                    val alpha = (.14f + .15f * (1f + sin(phase + .7f))).coerceIn(0f, 1f)
                    drawCircle(color, (.9f + i % 3 * .3f) * size.minDimension / 320f,
                        Offset(x * size.width, y * size.height), alpha = alpha)
                }
            }
            BackgroundAnimation.POND -> {
                repeat(3) { i ->
                    val phase = clock * TAU + i * 2.09f
                    val x = .20f + i * .29f + sin(phase) * .038f
                    val y = .649f + i % 2 * .061f
                    drawOval(Color(0xFF97B4B7), Offset((x - .035f) * size.width, y * size.height),
                        Size((.07f + .014f * sin(phase)) * size.width, .012f * size.height),
                        alpha = .21f + .09f * sin(phase), style = rippleStroke)
                }
            }
            BackgroundAnimation.ORCHARD -> {
                repeat(5) { i ->
                    val phase = clock * TAU + i * 1.25f
                    val x = .085f + i * .21f + sin(phase) * .015f
                    val y = .71f + sin(slow * TAU + i) * .009f
                    drawOval(Color(0xFFA89660), Offset(x * size.width, y * size.height), Size(.018f * size.width, .005f * size.height), alpha = .65f)
                }
            }
            BackgroundAnimation.OBSERVATORY -> {
                // A restrained rotating constellation above the fly's main performance space.
                repeat(5) { i ->
                    val a = slow * TAU + i * TAU / 5f
                    val b = slow * TAU + (i + 1) * TAU / 5f
                    val start = Offset((.53f + cos(a) * .115f) * size.width, (.14f + sin(a) * .045f) * size.height)
                    val end = Offset((.53f + cos(b) * .115f) * size.width, (.14f + sin(b) * .045f) * size.height)
                    drawLine(Color(0xFF8DA1BC), start, end, .8f * size.minDimension / 320f, alpha = .28f)
                    drawCircle(Color(0xFFB5C4DC), 1.7f * size.minDimension / 320f, start, alpha = .68f)
                }
            }
            BackgroundAnimation.BEACH -> {
                repeat(8) { i ->
                    val phase = clock * TAU + i * .83f
                    val x = .56f + i * .039f + sin(phase) * .008f
                    val y = .53f + i % 4 * .043f
                    drawLine(Color(0xFFE0C992), Offset(x * size.width, y * size.height),
                        Offset((x + .018f) * size.width, y * size.height),
                        1.1f * size.minDimension / 320f, alpha = .18f + .12f * sin(phase))
                }
            }
            else -> Unit
        }
    }

    private companion object { const val TAU = (PI * 2).toFloat() }
}
