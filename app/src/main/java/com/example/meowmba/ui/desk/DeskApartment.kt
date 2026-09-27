package com.example.meowmba.ui.desk

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.meowmba.ui.desk.DeskPainter.INK
import com.example.meowmba.ui.desk.DeskPainter.PAPER
import com.example.meowmba.ui.desk.DeskPainter.line
import com.example.meowmba.ui.desk.DeskPainter.rrect
import com.example.meowmba.ui.desk.DeskPainter.text
import kotlin.math.roundToInt

/**
 * The rest of Meow's place around the office: the living room on the left (x −412…0) and the
 * bedroom on the right (x 412…824), in the Desk's 960-high scene units and cartoon style. A
 * landscape screen ([DeskLayout.LANDSCAPE]) widens both outward. Static furniture is drawn once
 * with the room layer; the screens, labels, the picture and the big window's sky redraw only
 * when [DeskState] changes. Nothing animates here, for heat.
 */
internal object DeskApartment {
    private const val EXTEND = 2400f
    private const val WOOD = 0xFF8C6A48
    private val SANS = FontFamily.SansSerif
    private val MONO = FontFamily.Monospace

    // ---- walls, floors and doorways --------------------------------------------------------------

    fun DrawScope.drawShell(layout: DeskLayout = DeskLayout.STANDARD) {
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
        val cols = 7 + (layout.wing / 52f).toInt()
        for (row in 0..5) for (col in 0..cols) drawCircle(Color(0x33E9EFD9), 1.3f, Offset(430f + col * 52f + (row % 2) * 26f, 60f + row * 90f))
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

    fun DrawScope.drawLivingRoom(layout: DeskLayout = DeskLayout.STANDARD) {
        val p = layout.living
        drawOval(Color(0xFF6B3A4A), Offset(p.couchX - 6f, 726f), Size(p.couchW + 40f, 22f))    // rug
        bookshelf(p.shelfX, p.shelfW)
        // The picture's nail and wire (the framed picture itself is dynamic).
        val pc = p.pictureX + p.pictureW / 2
        line(INK, pc - p.pictureW * .3f, p.pictureY + 6f, pc, p.pictureY - 20f, 1.5f)
        line(INK, pc, p.pictureY - 20f, pc + p.pictureW * .3f, p.pictureY + 6f, 1.5f)
        drawCircle(Color(0xFFBDBDBD), 3f, Offset(pc, p.pictureY - 20f)); drawCircle(INK, 3f, Offset(pc, p.pictureY - 20f), style = Stroke(1f))
        catTower(p.towerX)
        couch(p.couchX)
        // The TV's wall shadow (the set is dynamic).
        drawRoundRect(Color(0x40000000), Offset(p.tvX + 5f, p.tvY + 7f), Size(p.tvW, p.tvH), CornerRadius(8f))
        stereo(p)
    }

    private fun DrawScope.bookshelf(x: Float, w: Float) {
        val spines = listOf(0xFFB24A62, 0xFF1F4FBF, 0xFFC6EB78, 0xFFE0B66A, 0xFF5E7A76, 0xFF8A2E5C, 0xFFF2B8B5, 0xFF2F7A3E)
        rrect(Color(0xFF6B4A2B), x, 420f, w, 320f, 3f, 3f)
        rrect(Color(0xFF4A321D), x + 6f, 428f, w - 12f, 304f, 2f)
        val manual = manualX(x)
        for (shelf in 0..4) {
            val top = 432f + shelf * 60f
            var bx = x + 8f
            var i = shelf * 3
            while (bx < x + w - 16f) {
                val bw = 7f + (i * 5 % 4)
                val h = 38f + (i * 7 % 14)
                // Leave a gap on the second shelf for the red manual, drawn with its label later.
                if (!(shelf == 1 && bx > manual - bw - 2f && bx < manual + 14f)) {
                    // Every fifth book on a wide shelf leans, so the rows don't look printed.
                    if (i % 5 == 4 && w > 100f && bx < x + w - 30f) {
                        rotate(12f, Offset(bx, top + 50f)) { rrect(Color(spines[i % spines.size]), bx, top + 50f - h, bw, h, 1f, 1.2f) }
                        bx += 8f
                    } else rrect(Color(spines[i % spines.size]), bx, top + 50f - h, bw, h, 1f, 1.2f)
                }
                bx += bw + 1.5f; i++
            }
            rrect(Color(0xFF6B4A2B), x + 4f, top + 50f, w - 8f, 6f, 1f, 1.5f)
        }
    }

    private fun manualX(shelfX: Float) = shelfX + 30f

    /**
     * The tall tower behind the couch: two sisal posts, five levels, a cubby with a round door and
     * a dangling toy. The couch hides its base.
     */
    private fun DrawScope.catTower(t: Float) {
        val pad = Color(0xFF9C6B8A)
        sisal(t - 6f, 322f, 12f, 318f)
        sisal(t - 40f, 458f, 10f, 182f)
        rrect(Color(0xFF8C5B7A), t + 2f, 532f, 42f, 62f, 5f, 2.5f)                          // cubby
        drawOval(Color(0xFF2A1A26), Offset(t + 11f, 546f), Size(24f, 26f)); drawOval(INK, Offset(t + 11f, 546f), Size(24f, 26f), style = Stroke(1.5f))
        rrect(pad, t - 44f, 594f, 74f, 10f, 4f, 2f)                                        // level 1
        rrect(pad, t - 2f, 522f, 46f, 10f, 4f, 2f)                                         // level 2 (cubby roof)
        rrect(pad, t - 46f, 452f, 50f, 10f, 4f, 2f)                                        // level 3
        rrect(pad, t - 6f, 384f, 30f, 10f, 4f, 2f)                                         // level 4
        // Top perch: a padded bed with a rim.
        rrect(pad, t - 44f, 316f, 64f, 14f, 6f, 2.5f)
        val rim = Path().apply { moveTo(t - 42f, 318f); quadraticTo(t - 44f, 304f, t - 34f, 306f); lineTo(t + 10f, 306f); quadraticTo(t + 20f, 304f, t + 18f, 318f) }
        drawPath(rim, Color(0xFFB27FA0), style = Stroke(5f)); drawPath(rim, INK, style = Stroke(1.2f))
        // Mouse toy on a string under the top perch.
        line(INK, t - 36f, 330f, t - 36f, 364f, 1f)
        drawOval(Color(0xFF9B9B9B), Offset(t - 42f, 362f), Size(12f, 8f)); line(Color(0xFFF2B8C8), t - 42f, 366f, t - 48f, 370f, 1.2f)
    }

    private fun DrawScope.couch(x: Float) {
        rrect(Color(0xFF2E5A52), x + 10f, 636f, 108f, 56f, 10f, 2.5f)
        rrect(Color(0xFF3F6F66), x + 14f, 642f, 50f, 44f, 8f, 1.5f); rrect(Color(0xFF3F6F66), x + 66f, 642f, 50f, 44f, 8f, 1.5f)
        rrect(Color(0xFFE0B66A), x + 20f, 654f, 20f, 18f, 6f, 1.5f)                          // throw pillow
        rrect(Color(0xFF3F6F66), x + 10f, 684f, 108f, 28f, 6f, 2.5f)
        rrect(Color(0xFF2E5A52), x, 660f, 18f, 56f, 8f, 2.5f); rrect(Color(0xFF2E5A52), x + 110f, 660f, 18f, 56f, 8f, 2.5f)
        rrect(Color(0xFF274C45), x + 6f, 712f, 116f, 16f, 3f, 2f)
        line(INK, x + 16f, 728f, x + 16f, 738f, 4f); line(INK, x + 112f, 728f, x + 112f, 738f, 4f)
    }

    /**
     * The stereo: a component rack (turntable, receiver, cassette deck, amplifier) between two
     * tall tower speakers. The receiver's display (dynamic) spans the rack so the song is readable.
     */
    private fun DrawScope.stereo(p: LivingPlan) {
        val top = p.stereoTop
        speaker(p.stereoX, p.speakerW, top + 14f); speaker(p.stereoX + p.stereoW - p.speakerW, p.speakerW, top + 14f)
        val x = p.rackX + 2f; val w = p.rackW - 4f
        rrect(Color(0xFF2B2B2E), p.rackX, top, p.rackW, 740f - top, 3f, 2.5f)
        rrect(Color(0xFF3A3A3E), x, top + 2f, w, 16f, 2f, 1.5f)                              // turntable
        drawOval(Color(0xFF151515), Offset(x + 8f, top + 4f), Size(w * .45f, 10f)); drawCircle(Color(0xFFE5484D), 1.8f, Offset(x + 8f + w * .225f, top + 9f))
        line(Color(0xFFBDBDBD), x + w - 10f, top + 5f, x + w - 26f, top + 13f, 1.5f)
        rrect(Color(0xFF3A3A3E), x, top + 22f, w, p.displayH + 8f, 2f, 1.5f)                // receiver
        val deck = top + 34f + p.displayH
        rrect(Color(0xFF3A3A3E), x, deck, w, 22f, 2f, 1.5f)                                 // cassette deck
        val window = (w - 18f) / 2
        rrect(Color(0xFF1A1A1C), x + 6f, deck + 5f, window, 12f, 1f, 1f); rrect(Color(0xFF1A1A1C), x + 12f + window, deck + 5f, window, 12f, 1f, 1f)
        rrect(Color(0xFF3A3A3E), x, deck + 26f, w, 30f, 2f, 1.5f)                           // amplifier
        val knobs = (w / 22f).toInt().coerceAtLeast(3)
        for (k in 0 until knobs) {
            val kx = x + w * (k + .5f) / knobs
            drawCircle(Color(0xFFBDBDBD), 4.5f, Offset(kx, deck + 41f)); drawCircle(INK, 4.5f, Offset(kx, deck + 41f), style = Stroke(1f))
        }
        var vx = x + 8f
        while (vx < x + w - 8f) { line(Color(0xFF1A1A1C), vx, deck + 62f, vx, 734f, 2f); vx += 8f }  // vents
    }

    /** A tall tower speaker: tweeter on top, woofers, a port. */
    private fun DrawScope.speaker(x: Float, w: Float, top: Float) {
        val cx = x + w / 2
        val cone = w * .42f
        rrect(Color(0xFF3B2A20), x, top, w, 740f - top, 3f, 2.5f)
        drawCircle(Color(0xFF1A1A1C), cone * .45f, Offset(cx, top + 16f)); drawCircle(Color(0xFF6E6E72), cone * .18f, Offset(cx, top + 16f))
        var y = top + 30f + cone
        repeat(2) {
            drawCircle(Color(0xFF1A1A1C), cone, Offset(cx, y)); drawCircle(Color(0xFF4A4A4E), cone * .45f, Offset(cx, y))
            y += cone * 2 + 12f
        }
        rrect(Color(0xFF1A1A1C), cx - w * .25f, 718f, w * .5f, 5f, 2.5f)
    }

    private fun DrawScope.sisal(x: Float, top: Float, w: Float, h: Float) {
        rrect(Color(0xFFC9B38A), x, top, w, h, 2f, 2f)
        var y = top + 4f
        while (y < top + h) { line(Color(0xFFA8926A), x + 1f, y, x + w - 1f, y + 2f, 1f); y += 6f }
    }

    fun DrawScope.drawLivingRoomObjects(s: DeskState, m: TextMeasurer, layout: DeskLayout = DeskLayout.STANDARD, picture: ImageBitmap? = null) {
        val r = s.room
        val p = layout.living
        tv(r, m, p.tvX, p.tvY, p.tvW, p.tvH)
        receiver(r, m, p)
        picture(p, picture)
        // The manual on the second shelf, and a "now playing" tag when an audiobook is on.
        val mx = manualX(p.shelfX)
        rrect(Color(0xFFC0392B), mx, 490f, 11f, 42f, 1f, 1.2f)
        rotate(-90f, Offset(mx + 5.5f, 511f)) { text(m, "MANUAL", mx + 5.5f, 514f, 6.5f, Color(0xFFFFF3C4), family = SANS, weight = FontWeight.Bold, center = true) }
        r.audiobook?.let { book ->
            val tag = p.shelfW - 12f
            rrect(Color(0xFFFFE27A), p.shelfX + 4f, 394f, tag + 4f, 24f, 2f, 1.5f)
            text(m, fit(m, "Now: " + book.title, tag - 4f, 14f, DeskPainter.hand), p.shelfX + 8f, 412f, 14f, INK)
            // The book being listened to glows on the top shelf.
            drawRoundRect(Color(0x80FFE27A), Offset(p.shelfX + 6f, 430f), Size(p.shelfW - 12f, 52f), CornerRadius(3f))
        }
    }

    /** The big-screen TV: what's on YouTube, big enough to read. Text scales with the set. */
    private fun DrawScope.tv(r: RoomState, m: TextMeasurer, x: Float, y: Float, w: Float, h: Float) {
        val k = w / 224f
        rrect(Color(0xFF151515), x, y, w, h, 6f * k, 3f)
        drawCircle(Color(0xFFE5484D), 1.6f * k, Offset(x + w - 12f * k, y + h - 6f * k))       // standby light
        val sx = x + 6f * k; val sy = y + 6f * k; val sw = w - 12f * k; val sh = h - 18f * k
        val video = r.video
        drawRoundRect(if (video != null) Color(0xFF0F0F12) else Color(0xFF14191C), Offset(sx, sy), Size(sw, sh), CornerRadius(3f))
        val cx = sx + sw / 2
        val pad = 10f * k
        when {
            !r.mediaAccess -> {
                text(m, "Allow media info", cx, sy + sh / 2 - 2f, 14f * k, Color(0xFF8FB0AC), family = SANS, center = true)
                text(m, "to see what's on", cx, sy + sh / 2 + 16f * k, 12f * k, Color(0xFF6E8C8A), family = SANS, center = true)
            }
            video == null -> {
                playBadge(cx, sy + sh / 2 - 12f * k, 10f * k, Color(0xFF5A3030))
                text(m, "Nothing playing", cx, sy + sh / 2 + 24f * k, 13f * k, Color(0xFF6E8C8A), family = SANS, center = true)
            }
            else -> {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF26324A), Color(0xFF0F0F12)), sy, sy + sh), Offset(sx, sy), Size(sw, sh), CornerRadius(3f))
                playBadge(sx + pad + 8f * k, sy + 14f * k, 7f * k, Color(0xFFE5302A))
                text(m, if (video.playing) "PLAYING" else "PAUSED", sx + pad + 22f * k, sy + 18f * k, 10f * k, Color.White, family = SANS, weight = FontWeight.Bold)
                text(m, "YOUTUBE", sx + sw - pad, sy + 18f * k, 9f * k, Color(0xFFB8C4D6), family = SANS, weight = FontWeight.Bold, rightAlign = true)
                lines(m, video.title, sw - 2 * pad, 17f * k, SANS, FontWeight.Bold, 2).forEachIndexed { i, l ->
                    text(m, l, sx + pad, sy + 44f * k + i * 21f * k, 17f * k, Color.White, family = SANS, weight = FontWeight.Bold)
                }
                video.subtitle?.let { text(m, fit(m, it, sw - 2 * pad, 12.5f * k, SANS), sx + pad, sy + 88f * k, 12.5f * k, Color(0xFFB8C4D6), family = SANS) }
                val bar = sw - 2 * pad
                drawRect(Color(0x55FFFFFF), Offset(sx + pad, sy + sh - 10f * k), Size(bar, 3f * k))
                drawRect(Color(0xFFE5302A), Offset(sx + pad, sy + sh - 10f * k), Size(bar * if (video.playing) .38f else .24f, 3f * k))
            }
        }
    }

    /** The receiver's display: status, app, song and artist, and level meters. */
    private fun DrawScope.receiver(r: RoomState, m: TextMeasurer, p: LivingPlan) {
        val x = p.displayX; val y = p.stereoTop + 26f; val w = p.displayW; val h = p.displayH
        val k = minOf(w / 120f, h / 60f)
        val lcd = Color(0xFF7BE06A); val dim = Color(0xFF3E7A43)
        drawRoundRect(Color(0xFF0B1A12), Offset(x, y), Size(w, h), CornerRadius(2f))
        drawRoundRect(Color(0x3322FF66), Offset(x, y), Size(w, h), CornerRadius(2f), style = Stroke(1f))
        val music = r.music
        val pad = 4f * k
        if (music != null) {
            val status = if (music.playing) "▶ PLAYING" else "❚❚ PAUSED"
            text(m, status, x + pad, y + 10f * k, 8f * k, if (music.playing) lcd else Color(0xFFE0B66A), family = MONO, weight = FontWeight.Bold)
            music.app?.let { appLabel(it) }?.let { text(m, it, x + w - pad, y + 10f * k, 7f * k, dim, family = MONO, rightAlign = true) }
            text(m, fit(m, music.title, w - 2 * pad, 14f * k, SANS, FontWeight.Bold), x + pad, y + 26f * k, 14f * k, lcd, family = SANS, weight = FontWeight.Bold)
            text(m, fit(m, music.subtitle ?: "", w - 2 * pad, 11f * k, SANS), x + pad, y + 39f * k, 11f * k, Color(0xFF5FB866), family = SANS)
            // Level meters: tall and lively when playing, flat when paused.
            val bars = 10
            val bw = (w - 2 * pad) / bars
            val shape = listOf(.9f, .6f, .8f, .45f, .7f, .55f, .85f, .5f, .65f, .4f)
            for (i in 0 until bars) {
                val v = if (music.playing) shape[i] else .08f
                val bh = (h - 44f * k) * v
                drawRect(if (i < 6) lcd else Color(0xFF9EE06A), Offset(x + pad + i * bw, y + h - 3f - bh), Size(bw - 2f, bh))
            }
            if (music.playing) { note(p.stereoX - 6f, p.stereoTop - 2f); note(p.stereoX + p.stereoW - 14f, p.stereoTop - 10f) }
        } else {
            text(m, if (r.mediaAccess) "STEREO" else "NO MEDIA INFO", x + w / 2, y + h / 2 - 2f, 9f * k, dim, family = MONO, weight = FontWeight.Bold, center = true)
            text(m, if (r.mediaAccess) "Nothing playing" else "Allow in Settings", x + w / 2, y + h / 2 + 12f * k, 8.5f * k, dim, family = MONO, center = true)
        }
    }

    /**
     * The framed picture on the living-room wall: the user's photo (cropped to fill), or a little
     * painting until one is chosen.
     */
    private fun DrawScope.picture(p: LivingPlan, image: ImageBitmap?) {
        val x = p.pictureX; val y = p.pictureY; val w = p.pictureW; val h = p.pictureH
        drawRoundRect(Color(0x40000000), Offset(x + 4f, y + 5f), Size(w, h), CornerRadius(3f))
        rrect(Color(0xFFB8893E), x, y, w, h, 3f, 2.5f)
        rrect(Color(0xFFD9AE5C), x + 3f, y + 3f, w - 6f, h - 6f, 2f)
        rrect(PAPER, x + 7f, y + 7f, w - 14f, h - 14f, 1f)
        val ix = x + 12f; val iy = y + 12f; val iw = w - 24f; val ih = h - 24f
        if (image != null && image.width > 0 && image.height > 0) {
            // Centre-crop the photo to the frame's shape.
            val frameRatio = iw / ih
            val imageRatio = image.width.toFloat() / image.height
            val (cw, ch) = if (imageRatio > frameRatio) (image.height * frameRatio).roundToInt() to image.height
                else image.width to (image.width / frameRatio).roundToInt()
            drawImage(image, srcOffset = IntOffset((image.width - cw) / 2, (image.height - ch) / 2), srcSize = IntSize(cw, ch),
                dstOffset = IntOffset(ix.roundToInt(), iy.roundToInt()), dstSize = IntSize(iw.roundToInt(), ih.roundToInt()),
                filterQuality = FilterQuality.Medium)
        } else {
            // A small sunset painting: sky, sun, hills and a sailboat.
            drawRect(Brush.verticalGradient(listOf(Color(0xFFF2B8B5), Color(0xFFFFE27A)), iy, iy + ih * .62f), Offset(ix, iy), Size(iw, ih * .62f))
            drawCircle(Color(0xFFE5484D), iw * .13f, Offset(ix + iw * .66f, iy + ih * .46f))
            drawRect(Color(0xFF3F7FA8), Offset(ix, iy + ih * .62f), Size(iw, ih * .38f))
            val hill = Path().apply {
                moveTo(ix, iy + ih * .64f); quadraticTo(ix + iw * .22f, iy + ih * .38f, ix + iw * .48f, iy + ih * .64f); close()
            }
            drawPath(hill, Color(0xFF2F7A3E))
            val boat = Path().apply { moveTo(ix + iw * .56f, iy + ih * .74f); lineTo(ix + iw * .84f, iy + ih * .74f); lineTo(ix + iw * .78f, iy + ih * .8f); lineTo(ix + iw * .6f, iy + ih * .8f); close() }
            drawPath(boat, INK)
            val sail = Path().apply { moveTo(ix + iw * .7f, iy + ih * .72f); lineTo(ix + iw * .7f, iy + ih * .5f); lineTo(ix + iw * .82f, iy + ih * .72f); close() }
            drawPath(sail, PAPER); drawPath(sail, INK, style = Stroke(1f))
        }
        drawRect(INK, Offset(ix, iy), Size(iw, ih), style = Stroke(1.2f))
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

    fun DrawScope.drawBedroom(layout: DeskLayout = DeskLayout.STANDARD) {
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
        bed(layout.bedRight)
        // A landscape bedroom has room for a nightstand and lamp beside the longer bed.
        if (layout == DeskLayout.LANDSCAPE) nightstand(layout.bedRight + 8f)
        // Litter box (its front lip is also drawn over the cat when it's inside)
        rrect(Color(0xFF5E7A76), 506f, 692f, 58f, 48f, 5f, 2.5f)
        drawRect(Color(0xFFE3D3A8), Offset(510f, 694f), Size(50f, 6f))
    }

    /** Frame, mattress, quilt, pillow and headboard; [right] is the headboard's outer edge. */
    private fun DrawScope.bed(right: Float) {
        rrect(Color(WOOD), right - 14f, 588f, 14f, 152f, 3f, 2.5f)
        rrect(Color(WOOD), 684f, 686f, right - 686f, 34f, 3f, 2.5f)
        line(INK, 690f, 720f, 690f, 738f, 5f); line(INK, right - 10f, 720f, right - 10f, 738f, 5f)
        rrect(Color(0xFFEDE6D6), 688f, 656f, right - 698f, 30f, 6f, 2f)
        val q = right - 48f
        val quilt = Path().apply { moveTo(688f, 668f); lineTo(q, 664f); quadraticTo(q + 8f, 690f, q + 2f, 712f); lineTo(690f, 712f); quadraticTo(682f, 690f, 688f, 668f); close() }
        drawPath(quilt, Color(0xFF1F4FBF)); drawPath(quilt, INK, style = Stroke(2f, join = StrokeJoin.Round))
        var sx = 700f
        while (sx < q - 8f) { line(Color(0xFF3F6FDF), sx, 668f, sx - 2f, 710f, 1.2f); sx += 22f }
        rrect(Color(0xFFF4EEDB), right - 46f, 640f, 34f, 20f, 8f, 2f)
    }

    private fun DrawScope.nightstand(x: Float) {
        rrect(Color(0xFF7A5536), x, 680f, 46f, 60f, 3f, 2.5f)
        rrect(Color(0xFF8E6440), x + 5f, 688f, 36f, 18f, 2f, 1.5f); rrect(INK, x + 18f, 695f, 10f, 4f, 2f)
        rrect(Color(0xFFBDB8AE), x + 16f, 664f, 14f, 16f, 3f, 1.5f)                          // lamp base
        val shade = Path().apply { moveTo(x + 10f, 664f); lineTo(x + 36f, 664f); lineTo(x + 30f, 642f); lineTo(x + 16f, 642f); close() }
        drawPath(shade, Color(0xFFFFE27A)); drawPath(shade, INK, style = Stroke(2f, join = StrokeJoin.Round))
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
        // Big window: sky, clouds, a little city. The moon (or sun) is in the office window only,
        // so the unfolded view never shows two.
        val sky = if (s.night) listOf(Color(0xFF0B1A2A), Color(0xFF26405A)) else listOf(Color(0xFF6FA7C4), Color(0xFFCFE4EC))
        drawRoundRect(Brush.verticalGradient(sky, 90f, 380f), Offset(522f, 90f), Size(220f, 290f), CornerRadius(6f))
        if (s.night) {
            listOf(Offset(548f, 124f), Offset(596f, 170f), Offset(640f, 110f), Offset(566f, 214f), Offset(718f, 206f), Offset(702f, 118f))
                .forEach { drawCircle(Color(0xFFE9EFD9), 1.6f, it) }
            // Night clouds, lit from above.
            cloud(650f, 150f, 72f, Color(0xFF3A5068), Color(0xFF6C84A0))
            cloud(544f, 196f, 52f, Color(0xFF30455C), Color(0xFF5A7290))
        } else {
            cloud(650f, 146f, 76f, Color(0xF2FFFFFF), null)
            cloud(546f, 186f, 56f, Color(0xE6FFFFFF), null)
            cloud(610f, 108f, 40f, Color(0xCCFFFFFF), null)
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

    /** A puffy cloud [w] wide, centred on [cx]; [rim] lights its top edge (moonlight at night). */
    private fun DrawScope.cloud(cx: Float, cy: Float, w: Float, body: Color, rim: Color?) {
        val puffs = listOf(Triple(-.30f, .06f, .22f), Triple(-.08f, -.12f, .30f), Triple(.20f, -.04f, .24f), Triple(.36f, .08f, .16f))
        rim?.let { c -> puffs.forEach { (dx, dy, r) -> drawCircle(c, w * r + 1.5f, Offset(cx + dx * w, cy + dy * w - 1.5f)) } }
        puffs.forEach { (dx, dy, r) -> drawCircle(body, w * r, Offset(cx + dx * w, cy + dy * w)) }
        drawRoundRect(body, Offset(cx - w * .5f, cy), Size(w, w * .2f), CornerRadius(w * .1f))
    }

    // ---- text fitting -----------------------------------------------------------------------------

    /** Width of [s] in scene units, measured exactly as [DeskPainter.text] draws it. */
    private fun DrawScope.width(m: TextMeasurer, s: String, size: Float, family: FontFamily, weight: FontWeight?): Float =
        m.measure(s, TextStyle(fontFamily = family, fontSize = (size / (density * fontScale)).sp, fontWeight = weight)).size.width.toFloat()

    /** [s] shortened with "…" to fit [maxWidth] scene units at [size]. */
    internal fun DrawScope.fit(m: TextMeasurer, s: String, maxWidth: Float, size: Float, family: FontFamily, weight: FontWeight? = null): String {
        fun fits(t: String) = width(m, t, size, family, weight) <= maxWidth
        if (s.isEmpty() || fits(s)) return s
        var lo = 0; var hi = s.length
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (fits(s.take(mid).trimEnd() + "…")) lo = mid else hi = mid - 1 }
        return s.take(lo).trimEnd() + "…"
    }

    /** Word-wraps [s] into at most [max] lines that fit [maxWidth]; the last line ends in "…" if cut. */
    private fun DrawScope.lines(m: TextMeasurer, s: String, maxWidth: Float, size: Float, family: FontFamily, weight: FontWeight?, max: Int): List<String> {
        fun fits(t: String) = width(m, t, size, family, weight) <= maxWidth
        val out = ArrayList<String>()
        val words = s.split(' ').filter { it.isNotEmpty() }
        var i = 0
        while (i < words.size && out.size < max) {
            var line = words[i++]
            while (i < words.size && fits("$line ${words[i]}")) line += " " + words[i++]
            out += if (fits(line)) line else fit(m, line, maxWidth, size, family, weight)
        }
        if (i < words.size && out.isNotEmpty()) out[out.lastIndex] = fit(m, out.last() + " " + words.drop(i).joinToString(" "), maxWidth, size, family, weight)
        return out
    }
}
