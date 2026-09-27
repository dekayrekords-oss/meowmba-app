package com.example.meowmba.ui.desk

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.meowmba.ui.desk.DeskPainter.INK
import com.example.meowmba.ui.desk.DeskPainter.PAPER
import com.example.meowmba.ui.desk.DeskPainter.line
import com.example.meowmba.ui.desk.DeskPainter.rrect
import com.example.meowmba.ui.desk.DeskPainter.text

/**
 * The rest of Meow's place around the office: the living room on the left (x −412…0) and the
 * bedroom on the right (x 412…824), in the Desk's 960-high scene units and cartoon style.
 * Static furniture is drawn once with the room layer; the screens, labels and the big window's
 * sky redraw only when [DeskState] changes. Nothing animates here, for heat.
 */
internal object DeskApartment {
    private const val EXTEND = 2400f
    private const val WOOD = 0xFF8C6A48
    private val SANS = FontFamily.SansSerif

    // ---- walls, floors and doorways --------------------------------------------------------------

    fun DrawScope.drawShell() {
        // Living room: warm wallpaper, plank floor. It also fills everything left of the apartment.
        stripes(-412f - EXTEND, 412f + EXTEND, Color(0xFF33262B), Color(0xFF372A2F))
        drawRect(Color(0xFF3E2B21), Offset(-412f - EXTEND, 740f), Size(412f + EXTEND, EXTEND))
        var px = -412f - EXTEND
        while (px < 0f) { line(Color(0xFF32231B), px, 742f, px, 790f, 2f); px += 46f }
        // Office: the original teal stripes.
        stripes(0f, 412f, Color(0xFF1B3139), Color(0xFF1D343C))
        drawRect(Color(0xFF12232A), Offset(0f, 740f), Size(412f, EXTEND))
        // Bedroom: dusk-blue wallpaper with small stars, soft carpet. Fills everything to the right.
        stripes(412f, 412f + EXTEND, Color(0xFF212C44), Color(0xFF232F48))
        for (row in 0..5) for (col in 0..7) drawCircle(Color(0x33E9EFD9), 1.3f, Offset(430f + col * 52f + (row % 2) * 26f, 60f + row * 90f))
        drawRect(Color(0xFF34405A), Offset(412f, 740f), Size(EXTEND, EXTEND))
        // Skirting along every room.
        drawRect(Color(0xFF0F1D23), Offset(-412f - EXTEND, 732f), Size(1236f + 2 * EXTEND, 10f))
        line(INK, -412f - EXTEND, 732f, 824f + EXTEND, 732f, 2f); line(INK, -412f - EXTEND, 742f, 824f + EXTEND, 742f, 2f)
        // Walls between rooms, with open doorways the cat walks through.
        doorway(0f); doorway(412f)
    }

    private fun DrawScope.stripes(from: Float, width: Float, base: Color, stripe: Color) {
        drawRect(base, Offset(from, -EXTEND), Size(width, 740f + EXTEND))
        var x = from
        while (x < from + width) { drawRect(stripe, Offset(x, -EXTEND), Size(13f, 740f + EXTEND)); x += 26f }
    }

    private fun DrawScope.doorway(x: Float) {
        drawRect(Color(0xFF0B161B), Offset(x - 9f, -EXTEND), Size(18f, 330f + EXTEND))
        rrect(Color(WOOD), x - 13f, 322f, 26f, 12f, 2f, 2f)
        rrect(Color(WOOD), x - 13f, 330f, 7f, 404f, 1f, 2f)
        rrect(Color(WOOD), x + 6f, 330f, 7f, 404f, 1f, 2f)
        drawRect(Color(0x33000000), Offset(x - 6f, 334f), Size(12f, 398f))
    }

    // ---- living room (left) ----------------------------------------------------------------------

    fun DrawScope.drawLivingRoom() {
        // Rug under the couch
        drawOval(Color(0xFF6B3A4A), Offset(-312f, 726f), Size(210f, 22f))
        // Bookshelf: five shelves of audiobooks (labels are dynamic)
        rrect(Color(0xFF6B4A2B), -402f, 420f, 82f, 320f, 3f, 3f)
        rrect(Color(0xFF4A321D), -396f, 428f, 70f, 304f, 2f)
        val spines = listOf(0xFFB24A62, 0xFF1F4FBF, 0xFFC6EB78, 0xFFE0B66A, 0xFF5E7A76, 0xFF8A2E5C, 0xFFF2B8B5, 0xFF2F7A3E)
        for (shelf in 0..4) {
            val top = 432f + shelf * 60f
            var bx = -394f
            var i = shelf * 3
            while (bx < -330f) {
                val w = 7f + (i * 5 % 4)
                val h = 38f + (i * 7 % 14)
                // The red "manual" book on the second shelf is drawn with its label later.
                if (!(shelf == 1 && bx > -374f && bx < -360f)) {
                    rrect(Color(spines[i % spines.size]), bx, top + 50f - h, w, h, 1f, 1.2f)
                }
                bx += w + 1.5f; i++
            }
            rrect(Color(0xFF6B4A2B), -398f, top + 50f, 74f, 6f, 1f, 1.5f)
        }
        // Cat tower
        rrect(Color(0xFF8E7A5A), -318f, 728f, 48f, 12f, 3f, 2f)
        sisal(-300f, 470f, 12f, 258f)
        rrect(Color(0xFF9C6B8A), -316f, 556f, 44f, 10f, 4f, 2f)
        rrect(Color(0xFF9C6B8A), -320f, 462f, 52f, 12f, 5f, 2.5f)
        // TV mount shadow (the screen is dynamic)
        rrect(Color(0xFF151515), -276f, 332f, 198f, 126f, 6f, 3f)
        line(Color(0xFF2A2A2A), -182f, 458f, -182f, 470f, 4f)
        // Couch
        rrect(Color(0xFF2E5A52), -262f, 636f, 108f, 56f, 10f, 2.5f)
        rrect(Color(0xFF3F6F66), -258f, 642f, 50f, 44f, 8f, 1.5f); rrect(Color(0xFF3F6F66), -206f, 642f, 50f, 44f, 8f, 1.5f)
        rrect(Color(0xFF3F6F66), -262f, 684f, 108f, 28f, 6f, 2.5f)
        rrect(Color(0xFF2E5A52), -272f, 660f, 18f, 56f, 8f, 2.5f); rrect(Color(0xFF2E5A52), -162f, 660f, 18f, 56f, 8f, 2.5f)
        rrect(Color(0xFF274C45), -266f, 712f, 116f, 16f, 3f, 2f)
        line(INK, -256f, 728f, -256f, 738f, 4f); line(INK, -160f, 728f, -160f, 738f, 4f)
        // Stereo: a wide component rack between two tall tower speakers. The receiver's big display
        // (dynamic) shows the song, the artist and whether it's playing.
        speaker(-142f, 24f); speaker(-32f, 24f)
        rrect(Color(0xFF2B2B2E), -116f, 562f, 82f, 178f, 3f, 2.5f)
        rrect(Color(0xFF3A3A3E), -114f, 564f, 78f, 16f, 2f, 1.5f)               // turntable
        drawOval(Color(0xFF151515), Offset(-104f, 566f), Size(44f, 10f)); drawCircle(Color(0xFFE5484D), 1.8f, Offset(-82f, 571f))
        line(Color(0xFFBDBDBD), -44f, 567f, -58f, 575f, 1.5f)
        rrect(Color(0xFF3A3A3E), -114f, 584f, 78f, 52f, 2f, 1.5f)               // receiver (display is dynamic)
        rrect(Color(0xFF3A3A3E), -114f, 640f, 78f, 24f, 2f, 1.5f)               // cassette deck
        rrect(Color(0xFF1A1A1C), -108f, 645f, 30f, 12f, 1f, 1f); rrect(Color(0xFF1A1A1C), -72f, 645f, 30f, 12f, 1f, 1f)
        rrect(Color(0xFF3A3A3E), -114f, 668f, 78f, 30f, 2f, 1.5f)               // amplifier
        for (k in 0..3) { drawCircle(Color(0xFFBDBDBD), 4.5f, Offset(-102f + k * 18f, 683f)); drawCircle(INK, 4.5f, Offset(-102f + k * 18f, 683f), style = Stroke(1f)) }
    }

    /** A tall, slim tower speaker: tweeter on top, two woofers, a port. */
    private fun DrawScope.speaker(x: Float, w: Float) {
        val cx = x + w / 2
        rrect(Color(0xFF3B2A20), x, 580f, w, 160f, 3f, 2.5f)
        drawCircle(Color(0xFF1A1A1C), 4.5f, Offset(cx, 596f)); drawCircle(Color(0xFF6E6E72), 1.8f, Offset(cx, 596f))
        drawCircle(Color(0xFF1A1A1C), 10f, Offset(cx, 626f)); drawCircle(Color(0xFF4A4A4E), 4.5f, Offset(cx, 626f))
        drawCircle(Color(0xFF1A1A1C), 10f, Offset(cx, 660f)); drawCircle(Color(0xFF4A4A4E), 4.5f, Offset(cx, 660f))
        rrect(Color(0xFF1A1A1C), cx - 6f, 718f, 12f, 5f, 2.5f)
    }

    private fun DrawScope.sisal(x: Float, top: Float, w: Float, h: Float) {
        rrect(Color(0xFFC9B38A), x, top, w, h, 2f, 2f)
        var y = top + 4f
        while (y < top + h) { line(Color(0xFFA8926A), x + 1f, y, x + w - 1f, y + 2f, 1f); y += 6f }
    }

    fun DrawScope.drawLivingRoomObjects(s: DeskState, m: TextMeasurer) {
        val r = s.room
        // Big-screen TV: what's on YouTube, or a quiet screen.
        val video = r.video
        drawRoundRect(if (video != null) Color(0xFF0F0F12) else Color(0xFF14191C), Offset(-270f, 338f), Size(186f, 114f), CornerRadius(3f))
        when {
            !r.mediaAccess -> { text(m, "Allow media info", -177f, 390f, 12f, Color(0xFF8FB0AC), family = SANS, center = true)
                text(m, "to see what's on", -177f, 408f, 11f, Color(0xFF6E8C8A), family = SANS, center = true) }
            video == null -> { playBadge(-177f, 382f, 9f, Color(0xFF5A3030)); text(m, "Nothing playing", -177f, 420f, 12f, Color(0xFF6E8C8A), family = SANS, center = true) }
            else -> {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF26324A), Color(0xFF0F0F12)), 338f, 452f), Offset(-270f, 338f), Size(186f, 114f), CornerRadius(3f))
                playBadge(-256f, 354f, 7f, Color(0xFFE5302A))
                text(m, if (video.playing) "PLAYING" else "PAUSED", -244f, 358f, 8f, Color(0xFFFFFFFF), family = SANS, weight = FontWeight.Bold)
                wrap(video.title, 26).take(2).forEachIndexed { i, l -> text(m, l, -262f, 386f + i * 17f, 13f, Color.White, family = SANS, weight = FontWeight.Bold) }
                video.subtitle?.let { text(m, it.take(30), -262f, 426f, 11f, Color(0xFFB8C4D6), family = SANS) }
                drawRect(Color(0x55FFFFFF), Offset(-262f, 440f), Size(170f, 3f)); drawRect(Color(0xFFE5302A), Offset(-262f, 440f), Size(if (video.playing) 64f else 40f, 3f))
            }
        }
        // Receiver display: status, song and artist, big enough to read.
        val music = r.music
        val lcd = Color(0xFF7BE06A); val dim = Color(0xFF3E7A43)
        drawRoundRect(Color(0xFF0B1A12), Offset(-112f, 586f), Size(74f, 44f), CornerRadius(2f))
        drawRoundRect(Color(0x3322FF66), Offset(-112f, 586f), Size(74f, 44f), CornerRadius(2f), style = Stroke(1f))
        if (music != null) {
            val status = if (music.playing) "▶ PLAYING" else "❚❚ PAUSED"
            text(m, status, -109f, 595f, 6.5f, if (music.playing) lcd else Color(0xFFE0B66A), family = FontFamily.Monospace, weight = FontWeight.Bold)
            music.app?.let { appLabel(it) }?.let { text(m, it, -41f, 595f, 5.5f, dim, family = FontFamily.Monospace, rightAlign = true) }
            text(m, music.title.take(13), -109f, 609f, 9f, lcd, family = FontFamily.Monospace, weight = FontWeight.Bold)
            text(m, (music.subtitle ?: "").take(15), -109f, 620f, 7.5f, Color(0xFF5FB866), family = FontFamily.Monospace)
            // Level meters: tall and lively when playing, flat when paused.
            val levels = if (music.playing) listOf(.9f, .6f, .8f, .45f, .7f, .55f) else List(6) { .08f }
            levels.forEachIndexed { i, v -> drawRect(if (i < 3) lcd else Color(0xFF9EE06A), Offset(-109f + i * 11f, 628f - 5f * v - 1f), Size(8f, 5f * v)) }
            if (music.playing) { note(-150f, 568f); note(-24f, 562f) }
        } else {
            text(m, if (r.mediaAccess) "STEREO" else "NO MEDIA INFO", -75f, 604f, 7.5f, dim, family = FontFamily.Monospace, weight = FontWeight.Bold, center = true)
            text(m, if (r.mediaAccess) "Nothing playing" else "Allow in Settings", -75f, 618f, 7f, dim, family = FontFamily.Monospace, center = true)
        }
        // The manual on the second shelf, and a "now reading" tag when an audiobook is on.
        rrect(Color(0xFFC0392B), -372f, 490f, 11f, 42f, 1f, 1.2f)
        rotate(-90f, Offset(-366.5f, 511f)) { text(m, "MANUAL", -366.5f, 514f, 6.5f, Color(0xFFFFF3C4), family = SANS, weight = FontWeight.Bold, center = true) }
        r.audiobook?.let { book ->
            rrect(Color(0xFFFFE27A), -392f, 402f, 70f, 16f, 2f, 1.5f)
            text(m, "Now: " + book.title.take(9), -389f, 413f, 9f, INK)
            // The book being listened to glows on the top shelf.
            drawRoundRect(Color(0x80FFE27A), Offset(-396f, 430f), Size(70f, 52f), CornerRadius(3f))
        }
    }

    /** A short name for the app playing, e.g. SPOTIFY, from its package. */
    private fun appLabel(pkg: String): String? = when {
        "spotify" in pkg -> "SPOTIFY"
        "youtube.music" in pkg -> "YT MUSIC"
        "sec.android.app.music" in pkg -> "SAMSUNG"
        "pandora" in pkg -> "PANDORA"
        "soundcloud" in pkg -> "SOUNDCLOUD"
        "amazon.mp3" in pkg -> "AMAZON"
        "tidal" in pkg -> "TIDAL"
        else -> null
    }

    private fun DrawScope.playBadge(x: Float, y: Float, h: Float, c: Color) {
        drawRoundRect(c, Offset(x - h * 1.4f, y - h), Size(h * 2.8f, h * 2f), CornerRadius(h * .5f))
        drawPath(Path().apply { moveTo(x - h * .4f, y - h * .55f); lineTo(x + h * .6f, y); lineTo(x - h * .4f, y + h * .55f); close() }, Color.White)
    }

    private fun DrawScope.note(x: Float, y: Float) {
        drawCircle(Color(0xFFC6EB78), 3f, Offset(x, y)); line(Color(0xFFC6EB78), x + 2.6f, y, x + 2.6f, y - 12f, 1.6f)
        line(Color(0xFFC6EB78), x + 2.6f, y - 12f, x + 7f, y - 9f, 1.6f)
    }

    // ---- bedroom (right) -------------------------------------------------------------------------

    fun DrawScope.drawBedroom() {
        // Dresser with the old TV, the console on the floor in front
        rrect(Color(0xFF7A5536), 424f, 610f, 128f, 130f, 3f, 3f)
        for (d in 0..2) { rrect(Color(0xFF8E6440), 432f, 620f + d * 38f, 112f, 32f, 3f, 2f); rrect(INK, 480f, 634f + d * 38f, 16f, 5f, 2.5f) }
        // Old CRT TV with rabbit ears (the screen is dynamic)
        line(INK, 486f, 528f, 462f, 492f, 2f); line(INK, 486f, 528f, 512f, 490f, 2f)
        drawCircle(Color(0xFF9B9B9B), 2.5f, Offset(462f, 492f)); drawCircle(Color(0xFF9B9B9B), 2.5f, Offset(512f, 490f))
        rrect(Color(0xFF6B5B4A), 436f, 526f, 104f, 84f, 8f, 3f)
        rrect(Color(0xFF4E4236), 520f, 538f, 14f, 60f, 3f, 1.5f)
        for (k in 0..1) { drawCircle(Color(0xFFC9B38A), 3.5f, Offset(527f, 552f + k * 16f)); drawCircle(INK, 3.5f, Offset(527f, 552f + k * 16f), style = Stroke(1f)) }
        // Console + controller
        rrect(Color(0xFFBDB8AE), 430f, 718f, 52f, 20f, 3f, 2f)
        rrect(Color(0xFF6E6A62), 434f, 724f, 44f, 5f, 1f)
        drawCircle(Color(0xFFE5484D), 2f, Offset(472f, 733f))
        val cord = Path().apply { moveTo(482f, 728f); quadraticTo(490f, 740f, 496f, 732f) }
        drawPath(cord, INK, style = Stroke(1.5f))
        rrect(Color(0xFF8D8A84), 494f, 726f, 18f, 9f, 2f, 1.5f)
        // Big window frame and curtains (sky and city are dynamic)
        line(INK, 500f, 80f, 766f, 80f, 4f)
        for (cx in listOf(506f, 732f)) {
            val c = Path().apply { moveTo(cx, 80f); lineTo(cx + 28f, 80f); quadraticTo(cx + 18f, 230f, cx + 26f, 388f); lineTo(cx + 2f, 388f); quadraticTo(cx - 4f, 230f, cx, 80f); close() }
            drawPath(c, Color(0xFF8A2E5C)); drawPath(c, INK, style = Stroke(2f, join = StrokeJoin.Round))
        }
        // Cat tower by the window: tall sisal post, three platforms, a dangling mouse toy.
        rrect(Color(0xFF8E7A5A), 568f, 726f, 68f, 14f, 3f, 2f)
        sisal(596f, 380f, 12f, 346f)
        rrect(Color(0xFF9C6B8A), 574f, 632f, 56f, 10f, 4f, 2f)
        rrect(Color(0xFF9C6B8A), 580f, 516f, 50f, 10f, 4f, 2f)
        rrect(Color(0xFF9C6B8A), 564f, 374f, 72f, 14f, 6f, 2.5f)
        line(INK, 624f, 526f, 624f, 566f, 1f)
        drawOval(Color(0xFF9B9B9B), Offset(618f, 564f), Size(12f, 8f)); line(Color(0xFFF2B8C8), 618f, 568f, 612f, 572f, 1.2f)
        // Food tray: kibble and water on a mat
        rrect(Color(0xFFE08C84), 630f, 734f, 56f, 6f, 2f, 1.5f)
        drawBowl(634f, Color(0xFFC6EB78)); drawBowl(660f, Color(0xFF81D8CE))
        for (k in 0..3) drawCircle(Color(0xFF8E5E33), 1.6f, Offset(639f + k * 4f, 723f))
        // Bed: frame, mattress, quilt, pillow, headboard
        rrect(Color(WOOD), 812f, 588f, 14f, 152f, 3f, 2.5f)
        rrect(Color(WOOD), 684f, 686f, 140f, 34f, 3f, 2.5f)
        line(INK, 690f, 720f, 690f, 738f, 5f); line(INK, 816f, 720f, 816f, 738f, 5f)
        rrect(Color(0xFFEDE6D6), 688f, 656f, 128f, 30f, 6f, 2f)
        val quilt = Path().apply { moveTo(688f, 668f); lineTo(778f, 664f); quadraticTo(786f, 690f, 780f, 712f); lineTo(690f, 712f); quadraticTo(682f, 690f, 688f, 668f); close() }
        drawPath(quilt, Color(0xFF1F4FBF)); drawPath(quilt, INK, style = Stroke(2f, join = StrokeJoin.Round))
        for (k in 0..3) line(Color(0xFF3F6FDF), 700f + k * 22f, 668f, 698f + k * 22f, 710f, 1.2f)
        rrect(Color(0xFFF4EEDB), 780f, 640f, 34f, 20f, 8f, 2f)
        // Litter box (its front lip is also drawn over the cat when it's inside)
        rrect(Color(0xFF5E7A76), 506f, 692f, 58f, 48f, 5f, 2.5f)
        drawRect(Color(0xFFE3D3A8), Offset(510f, 694f), Size(50f, 6f))
    }

    private fun DrawScope.drawBowl(x: Float, c: Color) {
        val b = Path().apply { moveTo(x, 724f); lineTo(x + 22f, 724f); lineTo(x + 18f, 736f); lineTo(x + 4f, 736f); close() }
        drawPath(b, c); drawPath(b, INK, style = Stroke(1.8f, join = StrokeJoin.Round))
    }

    /** Drawn after the cat so it sits inside the litter box. */
    fun DrawScope.drawLitterFront() {
        rrect(Color(0xFF5E7A76), 504f, 704f, 62f, 36f, 4f, 2.5f)
        line(Color(0xFF7FA09A), 512f, 712f, 558f, 712f, 1.5f)
    }

    fun DrawScope.drawBedroomObjects(s: DeskState, m: TextMeasurer) {
        // Big window: sky, sun or moon, a little city
        val sky = if (s.night) listOf(Color(0xFF0B1A2A), Color(0xFF26405A)) else listOf(Color(0xFF6FA7C4), Color(0xFFCFE4EC))
        drawRoundRect(Brush.verticalGradient(sky, 90f, 380f), Offset(522f, 90f), Size(220f, 290f), CornerRadius(6f))
        if (s.night) {
            drawCircle(Color(0xFFE9EFD9), 20f, Offset(690f, 140f)); drawCircle(Color(0xFF16304A), 17f, Offset(699f, 134f))
            listOf(Offset(548f, 124f), Offset(596f, 170f), Offset(640f, 110f), Offset(566f, 214f), Offset(718f, 206f))
                .forEach { drawCircle(Color(0xFFE9EFD9), 1.6f, it) }
        } else {
            drawCircle(Color(0xFFFFE27A), 20f, Offset(690f, 140f)); drawCircle(INK, 20f, Offset(690f, 140f), style = Stroke(2f))
            drawOval(Color(0xE6FFFFFF), Offset(548f, 170f), Size(64f, 18f)); drawOval(Color(0xE6FFFFFF), Offset(566f, 160f), Size(34f, 20f))
        }
        val city = if (s.night) Color(0xFF0E1C2C) else Color(0xFF6E8FA6)
        val towers = listOf(Triple(526f, 300f, 34f), Triple(562f, 270f, 28f), Triple(592f, 316f, 40f), Triple(634f, 256f, 30f), Triple(666f, 292f, 36f), Triple(704f, 312f, 38f))
        towers.forEach { (x, top, w) ->
            drawRect(city, Offset(x, top), Size(w, 380f - top))
            if (s.night) for (wy in 0..3) for (wx in 0..1) if ((wy + wx + x.toInt()) % 3 != 0)
                drawRect(Color(0xFFFFE27A), Offset(x + 6f + wx * 12f, top + 10f + wy * 16f), Size(5f, 6f))
        }
        drawRoundRect(INK, Offset(522f, 90f), Size(220f, 290f), CornerRadius(6f), style = Stroke(3.5f))
        line(INK, 632f, 90f, 632f, 380f, 3.5f); line(INK, 522f, 236f, 742f, 236f, 3.5f)
        rrect(Color(WOOD), 512f, 380f, 240f, 12f, 3f, 2.5f)
        s.weatherLine?.let { text(m, it, 690f, 414f, 16f, Color(0xFFB8C4D6), center = true) } // right of the tower post
        // The old TV's screen: an arcade attract screen, inviting a game
        drawRoundRect(Color(0xFF10161A), Offset(446f, 538f), Size(70f, 60f), CornerRadius(10f))
        val px = Color(0xFF7BE06A)
        val invader = listOf("0010000100", "0001111000", "0011011100", "0111111110", "0101111010", "0100000010")
        invader.forEachIndexed { row, cols -> cols.forEachIndexed { col, c -> if (c == '1') drawRect(px, Offset(461f + col * 4f, 546f + row * 4f), Size(3.6f, 3.6f)) } }
        text(m, "INSERT COIN", 481f, 590f, 7f, Color(0xFFFFE27A), family = FontFamily.Monospace, weight = FontWeight.Bold, center = true)
    }

    private fun wrap(text: String, width: Int): List<String> {
        val out = ArrayList<String>(); var cur = ""
        for (w in text.split(' ')) {
            if (cur.isEmpty()) cur = w.take(width) else if (cur.length + 1 + w.length <= width) cur += " $w" else { out += cur; cur = w.take(width) }
        }
        if (cur.isNotEmpty()) out += cur
        return out.mapIndexed { i, l -> if (i == 1 && out.size > 2) l.take(width - 1) + "…" else l }
    }
}
