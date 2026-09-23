package com.example.meowmba.ui.desk

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of docs/design/pro-desk-avatar.html's SVG. All functions draw in scene units
 * (412 × 960 viewBox); the caller scales the DrawScope. Three layers:
 * [drawRoom] (static, cached), [drawObjects] (redrawn when DeskState changes),
 * and [drawCat] (animated only while walking/blinking).
 */
internal object DeskPainter {
    val INK = Color(0xFF172D34)
    private val PAPER = Color(0xFFF4EEDB)

    /** Handwriting on in-world paper. Patrick Hand (OFL) is not bundled yet; system cursive stands in. */
    val hand: FontFamily = FontFamily(androidx.compose.ui.text.font.Font(com.example.meowmba.R.font.desk_hand))
    var mono: FontFamily = FontFamily.Monospace

    private fun DrawScope.stroked(color: Color, w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    private fun DrawScope.rrect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 0f, stroke: Float = 0f, fill: Brush? = null) {
        if (fill != null) drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r)) else drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))
        if (stroke > 0f) drawRoundRect(INK, Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(stroke))
    }

    private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)

    /**
     * [size] is in scene units, like the SVG's font-size. The DrawScope is already scaled to the
     * scene, so convert back through density/fontScale to avoid scaling text twice.
     */
    private fun DrawScope.text(m: TextMeasurer, s: String, x: Float, baseline: Float, size: Float, color: Color,
                               family: FontFamily = hand, weight: FontWeight? = null, center: Boolean = false) {
        val sp = (size / (density * fontScale)).sp
        val layout = m.measure(s, TextStyle(fontFamily = family, fontSize = sp, color = color, fontWeight = weight))
        val left = if (center) x - layout.size.width / 2f else x
        drawText(layout, topLeft = Offset(left, baseline - layout.firstBaseline))
    }

    // ---- static room --------------------------------------------------------------------------

    fun DrawScope.drawRoom() {
        // Striped wall
        drawRect(Color(0xFF1B3139), size = Size(412f, 960f))
        var x = 0f
        while (x < 412f) { drawRect(Color(0xFF1D343C), Offset(x, 0f), Size(13f, 960f)); x += 26f }
        // Floor + skirting
        drawRect(Color(0xFF12232A), Offset(0f, 740f), Size(412f, 220f))
        drawRect(Color(0xFF0F1D23), Offset(0f, 732f), Size(412f, 10f)); drawRect(INK, Offset(0f, 732f), Size(412f, 10f), style = Stroke(2f))
        // Window frame (the sky/moon/text are dynamic)
        // Wall clock face
        drawCircle(PAPER, 36f, Offset(72f, 112f)); drawCircle(INK, 36f, Offset(72f, 112f), style = Stroke(3f))
        drawCircle(Color(0xFFD5CDB4), 30f, Offset(72f, 112f), style = Stroke(1.5f))
        line(INK, 72f, 84f, 72f, 88f, 2.5f); line(INK, 72f, 136f, 72f, 140f, 2.5f); line(INK, 44f, 112f, 48f, 112f, 2.5f); line(INK, 96f, 112f, 100f, 112f, 2.5f)
        // Corkboard frame
        rrect(Color(0xFFB98A5C), 22f, 178f, 190f, 170f, 6f, 3f)
        rrect(Color(0xFFC99A68), 30f, 186f, 174f, 154f, 3f)
        listOf(Triple(52f, 206f, 1.5f), Triple(170f, 318f, 1.5f), Triple(96f, 326f, 1.3f), Triple(188f, 214f, 1.3f))
            .forEach { (cx, cy, r) -> drawCircle(Color(0xFF9C7249), r, Offset(cx, cy)) }
        // Monitor body + stand
        rrect(Color(0xFF23363C), 26f, 438f, 150f, 104f, 8f, 3f)
        rrect(Color(0xFF0C1A1F), 36f, 448f, 130f, 80f, 3f)
        line(INK, 92f, 542f, 92f, 576f, 7f); line(INK, 70f, 578f, 114f, 578f, 7f)
        line(Color(0xFF3B5960), 92f, 542f, 92f, 576f, 3.5f); line(Color(0xFF3B5960), 70f, 578f, 114f, 578f, 3.5f)
        // Desk
        drawRect(Brush.verticalGradient(listOf(Color(0xFFC98E52), Color(0xFFB27A42)), 598f, 624f), Offset(0f, 598f), Size(412f, 26f))
        drawRect(INK, Offset(0f, 598f), Size(412f, 26f), style = Stroke(3f))
        rrect(Color(0xFF8E5E33), 14f, 624f, 384f, 110f, 0f, 3f)
        rrect(Color(0xFFA26C3C), 30f, 640f, 150f, 40f, 4f, 2.5f)
        rrect(Color(0xFFA26C3C), 30f, 688f, 150f, 34f, 4f, 2.5f)
        rrect(INK, 92f, 656f, 26f, 6f, 3f); rrect(INK, 92f, 702f, 26f, 6f, 3f)
        line(Color(0xFFA8743F), 40f, 606f, 100f, 606f, 2f); line(Color(0xFFA8743F), 150f, 612f, 240f, 612f, 2f); line(Color(0xFFA8743F), 300f, 606f, 370f, 606f, 2f)
        // Jar body + lid (coins/note are dynamic)
        rrect(Color(0x599FC7C4), 286f, 548f, 44f, 52f, 10f, 2.5f)
        rrect(Color(0xFF6E8C8A), 284f, 540f, 48f, 10f, 3f, 2.5f)
        // (Letter tray is drawn in drawObjects, on top of its envelopes.)
        // Lamp arm + shade (glow is dynamic)
        line(INK, 262f, 598f, 262f, 590f, 6f); line(INK, 250f, 598f, 274f, 598f, 6f)
        val arm = Path().apply { moveTo(262f, 590f); lineTo(250f, 522f); lineTo(226f, 470f) }
        drawPath(arm, INK, style = stroked(INK, 7f)); drawPath(arm, Color(0xFF5E7A76), style = stroked(INK, 3.5f))
        drawCircle(Color(0xFF5E7A76), 4f, Offset(250f, 522f)); drawCircle(INK, 4f, Offset(250f, 522f), style = Stroke(2f))
        val shade = Path().apply { moveTo(200f, 474f); quadraticTo(224f, 444f, 252f, 468f); lineTo(247f, 476f); lineTo(205f, 476f); close() }
        drawPath(shade, Color(0xFFC6EB78)); drawPath(shade, INK, style = Stroke(2.5f, join = StrokeJoin.Round))
        drawOval(Color(0xFFFFF3C4), Offset(217f, 472f), Size(18f, 8f)); drawOval(INK, Offset(217f, 472f), Size(18f, 8f), style = Stroke(1.5f))
    }

    // ---- data-driven objects -------------------------------------------------------------------

    fun DrawScope.drawObjects(s: DeskState, m: TextMeasurer) {
        // Lamp glow (idea queued) — behind everything else on the desk
        if (s.lampOn) {
            val glow = Path().apply { moveTo(206f, 478f); lineTo(168f, 600f); lineTo(290f, 600f); lineTo(246f, 478f); close() }
            drawPath(glow, Brush.radialGradient(listOf(Color(0x73FFD98A), Color(0x00FFD98A)), Offset(229f, 478f), 130f))
        }
        // Window: sky, moon/sun, hills, frame, sill, handwritten weather
        drawRoundRect(Brush.verticalGradient(
            if (s.night) listOf(Color(0xFF0D2130), Color(0xFF2A4E61)) else listOf(Color(0xFF6FA7C4), Color(0xFFBFDCE6)), 66f, 242f),
            Offset(238f, 66f), Size(148f, 176f), CornerRadius(6f))
        if (s.night) {
            drawCircle(Color(0xFFE9EFD9), 15f, Offset(344f, 104f)); drawCircle(INK, 15f, Offset(344f, 104f), style = Stroke(2f))
            drawCircle(Color(0xFF1A3547), 13f, Offset(351f, 99f))
            listOf(Triple(268f, 96f, 1.6f), Triple(300f, 120f, 1.2f), Triple(276f, 150f, 1.4f), Triple(364f, 160f, 1.2f))
                .forEach { (cx, cy, r) -> drawCircle(Color(0xFFE9EFD9), r, Offset(cx, cy)) }
        } else {
            drawCircle(Color(0xFFFFE27A), 14f, Offset(344f, 104f)); drawCircle(INK, 14f, Offset(344f, 104f), style = Stroke(2f))
        }
        val hills = Path().apply {
            moveTo(246f, 214f); relativeQuadraticTo(18f, -26f, 44f, -8f); relativeQuadraticTo(14f, -16f, 34f, -4f)
            relativeQuadraticTo(22f, -10f, 36f, 8f); relativeLineTo(0f, 12f); relativeLineTo(-114f, 0f); close()
        }
        drawPath(hills, if (s.night) Color(0xFF16303E) else Color(0xFF7FA58A))
        drawRoundRect(INK, Offset(238f, 66f), Size(148f, 176f), CornerRadius(6f), style = Stroke(3f))
        line(INK, 312f, 66f, 312f, 242f, 3f); line(INK, 238f, 154f, 386f, 154f, 3f)
        rrect(Color(0xFF8C6A48), 230f, 240f, 164f, 12f, 3f, 2.5f)
        s.weatherLine?.let { text(m, it, 312f, 272f, 17f, Color(0xFFBED0CD), center = true) }

        // Clock hands
        val hourA = Math.toRadians(((s.clockHour % 12) * 30 + s.clockMinute * 0.5) - 90.0)
        val minA = Math.toRadians(s.clockMinute * 6.0 - 90.0)
        line(INK, 72f, 112f, 72f + 18f * cos(hourA).toFloat(), 112f + 18f * sin(hourA).toFloat(), 3.5f)
        line(INK, 72f, 112f, 72f + 24f * cos(minA).toFloat(), 112f + 24f * sin(minA).toFloat(), 2.5f)
        drawCircle(Color(0xFFFF7B72), 3f, Offset(72f, 112f)); drawCircle(INK, 3f, Offset(72f, 112f), style = Stroke(1.5f))

        // Corkboard index card: project + tester-day tally
        run {
            rotate(-2.5f, Offset(104f, 250f)) {
                rrect(PAPER, 40f, 200f, 132f, 92f, 3f, 2.5f)
                line(Color(0xFFE08C84), 40f, 222f, 172f, 222f, 1.2f)
                text(m, s.projectTitle.ifBlank { "Your projects" }, 50f, 217f, 17f, INK)
                if (s.tallyTotal > 0) {
                    text(m, "tester days", 50f, 242f, 15f, Color(0xFF3A4F55))
                    repeat(s.tallyTotal) { i ->
                        val x = 50f + i * 8.6f
                        if (i < s.tallyDone) drawRoundRect(Color(0xFFC6EB78), Offset(x, 250f), Size(6f, 16f), CornerRadius(1.5f))
                        drawRoundRect(INK, Offset(x, 250f), Size(6f, 16f), CornerRadius(1.5f), style = Stroke(1.6f))
                    }
                }
                if (s.projectStatus.isNotBlank()) text(m, s.projectStatus, 50f, 284f, 15f, Color(0xFF3A4F55))
                else if (s.projectTitle.isBlank()) text(m, "Tap to open", 50f, 251f, 15f, Color(0xFF3A4F55))
                drawCircle(Color(0xFFFF7B72), 5f, Offset(106f, 200f)); drawCircle(INK, 5f, Offset(106f, 200f), style = Stroke(2f))
            }
        }
        if (s.pinnedNote.isNotEmpty()) rotate(4f, Offset(170f, 318f)) {
            rrect(Color(0xFFC7F4ED), 138f, 296f, 62f, 40f, 2f, 2f)
            s.pinnedNote.forEachIndexed { i, l -> text(m, l, 146f, 314f + i * 15f, 13f, INK) }
            drawCircle(Color(0xFFC6EB78), 4f, Offset(169f, 296f)); drawCircle(INK, 4f, Offset(169f, 296f), style = Stroke(1.8f))
        }

        // Monitor text (static cursor: no blinking loop, for heat)
        s.monitorLines.forEachIndexed { i, l ->
            text(m, l.text, 44f, 466f + i * 16f, 10f, if (l.highlight) Color(0xFFC6EB78) else Color(0xFF6E8C8A), family = mono)
        }
        if (s.monitorLines.isNotEmpty()) drawRect(Color(0xFFC6EB78), Offset(44f, 506f), Size(7f, 11f))

        // Sticky note: next calendar item
        rotate(6f, Offset(158f, 452f)) {
            drawRect(Color(0xFFFFE27A), Offset(128f, 424f), Size(64f, 58f)); drawRect(INK, Offset(128f, 424f), Size(64f, 58f), style = Stroke(2.5f))
            s.sticky.ifEmpty { listOf("Next up", "Tap for tasks") }.forEachIndexed { i, l -> text(m, l, 134f, 444f + i * 15f, if (i == 0) 15f else 13f, INK) }
        }

        // Jar: coins + handwritten note (empty jar when nothing is due)
        s.jarNote?.let { note ->
            listOf(Offset(302f, 590f), Offset(314f, 584f), Offset(305f, 578f)).forEach { c ->
                drawOval(Color(0xFFFFC56F), Offset(c.x - 9f, c.y - 4f), Size(18f, 8f))
                drawOval(INK, Offset(c.x - 9f, c.y - 4f), Size(18f, 8f), style = Stroke(1.8f))
            }
            rotate(-6f, Offset(306f, 524f)) {
                rrect(PAPER, 274f, 510f, 66f, 24f, 2f, 2f)
                text(m, note, 280f, 527f, 14f, Color(0xFFB3261E))
            }
        }

        // Letter tray: envelopes pile up with PEOPLE items; badge shows the count
        val envelopes = s.trayCount.coerceAtMost(3)
        if (envelopes >= 2) rotate(-10f, Offset(360f, 560f)) { envelope(344f, 532f, PAPER) }
        if (envelopes >= 3) rotate(6f, Offset(368f, 560f)) { envelope(350f, 538f, Color(0xFFFFE2B5)) }
        if (envelopes >= 1) rrect(Color(0xFFC7F4ED), 340f, 552f, 62f, 18f, 2f, 2f)
        val tray = Path().apply { moveTo(336f, 570f); lineTo(406f, 570f); lineTo(400f, 600f); lineTo(342f, 600f); close() }
        drawPath(tray, Color(0xFF5E7A76)); drawPath(tray, INK, style = Stroke(2.5f, join = StrokeJoin.Round))
        if (s.trayCount > 0) {
            drawCircle(Color(0xFFF2B8B5), 11f, Offset(398f, 534f)); drawCircle(INK, 11f, Offset(398f, 534f), style = Stroke(2f))
            text(m, if (s.trayCount > 9) "9+" else s.trayCount.toString(), 398f, 539f, 13f, Color(0xFF601410),
                family = FontFamily.SansSerif, weight = FontWeight.Black, center = true)
        }
    }

    private fun DrawScope.envelope(x: Float, y: Float, fill: Color) {
        rrect(fill, x, y, 42f, 30f, 2f, 2f)
        val flap = Path().apply { moveTo(x, y); relativeLineTo(21f, 15f); relativeLineTo(21f, -15f) }
        drawPath(flap, INK, style = Stroke(1.8f))
    }

    // ---- micro cat (viewBox 60 × 50, drawn at 110 × 92 scene units) -------------------------------

    private val CAT_BODY = Color(0xFF0E1D23)
    private val CAT_LINE = Color(0xFF4F7079)

    /**
     * [walkFrame] 0/1 swings the legs; [blink] 0 = open … 1 = shut; [tail] degrees of sway;
     * [gaze] −1 … 1 shifts the irises toward a tapped object; [facingLeft] mirrors the cat.
     */
    fun DrawScope.drawCat(x: Float, y: Float, walking: Boolean, walkFrame: Int, blink: Float, tail: Float, gaze: Float, facingLeft: Boolean) {
        val k = 110f / 60f
        withTransform({
            translate(x, y)
            scale(k, k, Offset.Zero)
            if (facingLeft) scale(-1f, 1f, Offset(30f, 25f))
        }) {
            drawOval(Color(0x73050D10), Offset(13f, 44.4f), Size(34f, 5.2f))
            rotate(tail, Offset(16f, 31f)) {
                val t = Path().apply { moveTo(16f, 31f); cubicTo(7.5f, 30f, 4.5f, 20.5f, 9.5f, 13f) }
                drawPath(t, CAT_LINE, style = Stroke(5.6f, cap = StrokeCap.Round)); drawPath(t, CAT_BODY, style = Stroke(3.8f, cap = StrokeCap.Round))
            }
            val swing = if (walking) (if (walkFrame == 1) 22f else -22f) else 0f
            leg(20.5f, 36f, 44.5f, swing); leg(25.5f, 37f, 45f, -swing)
            val bob = if (walking && walkFrame == 1) -1.2f else 0f
            withTransform({ translate(0f, bob) }) {
                drawOval(CAT_BODY, Offset(14.8f, 24.2f), Size(26.4f, 17.6f)); drawOval(CAT_LINE, Offset(14.8f, 24.2f), Size(26.4f, 17.6f), style = Stroke(1.3f))
                leg(33.5f, 37.5f, 45f, -swing); leg(38.5f, 36.5f, 44.6f, swing)
                ear(31.6f, 13.6f, 33.3f, 3.6f, 38.8f, 10.3f); ear(42.2f, 10f, 47.9f, 4f, 49.4f, 14.2f)
                tri(Color(0xFF2C4A52), 33.5f, 11.4f, 34.4f, 6.4f, 37.1f, 9.9f); tri(Color(0xFF2C4A52), 43.8f, 10.2f, 47.1f, 6.8f, 47.8f, 12.1f)
                drawCircle(CAT_BODY, 12.8f, Offset(40.5f, 21.5f)); drawCircle(CAT_LINE, 12.8f, Offset(40.5f, 21.5f), style = Stroke(1.3f))
                drawOval(Color(0x4DF2B8C8), Offset(31.4f, 26.1f), Size(4f, 2.2f)); drawOval(Color(0x4DF2B8C8), Offset(45.6f, 26.1f), Size(4f, 2.2f))
                withTransform({ scale(1f, 1f - 0.9f * blink.coerceIn(0f, 1f), Offset(40.5f, 21.8f)) }) {
                    eye(35.6f, gaze); eye(45.4f, gaze)
                }
                tri(Color(0xFFF2B8C8), 39.5f, 27.6f, 41.5f, 27.6f, 40.5f, 28.9f)
                val mouth = Path().apply { moveTo(38.9f, 29.5f); quadraticTo(39.7f, 30.4f, 40.5f, 29.5f); quadraticTo(41.3f, 30.4f, 42.1f, 29.5f) }
                drawPath(mouth, Color(0xFFBED0CD), style = Stroke(0.7f, cap = StrokeCap.Round))
                val w = Color(0xB3BED0CD)
                line(w, 35f, 28.2f, 28.4f, 27.4f, 0.55f); line(w, 35.2f, 29.3f, 28.9f, 30.4f, 0.55f)
                line(w, 46f, 28.2f, 52.6f, 27.4f, 0.55f); line(w, 45.8f, 29.3f, 52.1f, 30.4f, 0.55f)
            }
        }
    }

    private fun DrawScope.leg(x: Float, top: Float, bottom: Float, swing: Float) = rotate(swing, Offset(x, top)) {
        line(CAT_LINE, x, top, x, bottom, 5.4f); line(CAT_BODY, x, top, x, bottom, 3.6f)
    }

    private fun DrawScope.ear(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }
        drawPath(p, CAT_BODY); drawPath(p, CAT_LINE, style = Stroke(1.3f, join = StrokeJoin.Round))
    }

    private fun DrawScope.tri(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        drawPath(Path().apply { moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }, c)

    /** Lumen-style glossy lens eye (mint #4DFF85 iris), matching LumenEyeMaterials' palette. */
    private fun DrawScope.eye(cx: Float, gaze: Float) {
        val sclera = Brush.radialGradient(0f to Color(0xFFF7F9F2), .55f to Color(0xFFD9E6E2), 1f to Color(0xFF7E9A9B),
            center = Offset(cx - 1.4f, 20f), radius = 8.5f)
        drawCircle(sclera, 5.7f, Offset(cx, 21.8f)); drawCircle(Color(0xFF07131A), 5.7f, Offset(cx, 21.8f), style = Stroke(0.7f))
        val ix = cx + gaze.coerceIn(-1f, 1f)
        val iris = Brush.radialGradient(0f to Color(0xFFC9FFD9), .45f to Color(0xFF4DFF85), .8f to Color(0xFF1D7A55), 1f to Color(0xFF0A2330),
            center = Offset(ix, 22.6f), radius = 4.9f)
        drawCircle(iris, 4.1f, Offset(ix, 22.2f))
        drawCircle(Color(0xFF020A14), 2.05f, Offset(ix, 22.3f))
        drawCircle(Color.White, 1.2f, Offset(cx - 1.5f, 20f))
    }
}
