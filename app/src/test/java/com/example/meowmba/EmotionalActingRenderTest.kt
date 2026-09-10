package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Production rig/materials, scripted local inputs; not a filmed phone or model response. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmotionalActingRenderTest {
    private val out = File("build/reports/emotional-acting").apply { mkdirs() }
    private val eye = Color(0xFF60AFAE)
    private val glow = Color(0xFF7BB6C2)
    private val fur = Color(0xFFE8D3B2)
    private val drawTimes = mutableListOf<Long>()
    private fun draw(frame: LumenFrame, w: Int = 400, h: Int = 300, painter: LumenPainter = LumenPainter()): Bitmap {
        val b = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val before = System.nanoTime()
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(b.asImageBitmap()),Size(w.toFloat(),h.toFloat())) {
            painter.draw(this,frame,eye,glow,featureColor=fur)
        }
        drawTimes += System.nanoTime() - before
        return b
    }
    private fun save(b: Bitmap, name: String) {
        val f=File(out,name);f.parentFile!!.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
        b.recycle()
    }
    @Test fun nativeEmotionSheetShowsReadableRangeInBothAppearances() {
        for (light in listOf(false,true)) {
            val sheet=Bitmap.createBitmap(1600,1360,Bitmap.Config.ARGB_8888)
            val c=android.graphics.Canvas(sheet)
            c.drawColor(if(light) 0xFFF0EDE6.toInt() else 0xFF0B131B.toInt())
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=22f;color=if(light) 0xFF344955.toInt() else 0xFFBCD0D7.toInt() }
            val hashes=mutableSetOf<Int>()
            CompanionEmotion.entries.forEachIndexed { index,emotion ->
                val rig=LumenRig(73);var f=LumenFrame()
                repeat(181) { i -> f=rig.step(i/120f,LumenInput(emotionPreview=emotion,animal=AvatarAnimal.AUSTRALIAN_SHEPHERD,
                    cameraEnabled=true,gazeX=0f,gazeY=0f)) }
                val b=draw(f)
                val pixels=IntArray(400*300);b.getPixels(pixels,0,400,0,0,400,300);hashes+=pixels.contentHashCode()
                val x=(index%4*400).toFloat();val y=(index/4*340).toFloat()
                c.drawBitmap(b,x,y,null);b.recycle()
                c.drawText(emotion.name.lowercase().replaceFirstChar { it.uppercase() },x+25,y+318,p)
            }
            assertEquals(16,hashes.size)
            save(sheet,if(light) "emotions-light.png" else "emotions.png")
        }
    }

    @Test fun optionalProductionActingFilm() {
        if(System.getenv("MEOW_ACTING_PREVIEW")!="1") return
        drawTimes.clear()
        val rig=LumenRig(217);val painter=LumenPainter()
        fun input(t:Float):LumenInput {
            val interaction=when {t<2.4f->InteractionState.LISTENING;t<4.8f->InteractionState.THINKING;
                t<10.6f->InteractionState.SPEAKING;t<12.3f->InteractionState.IDLE;else->InteractionState.LISTENING}
            val emotion=when {t<4.8f->CompanionEmotion.CURIOUS;t<6.5f->CompanionEmotion.CONFUSED;
                t<8.2f->CompanionEmotion.UNDERSTANDING;t<10.6f->CompanionEmotion.HAPPY;
                else->CompanionEmotion.AFFECTIONATE}
            return LumenInput(emotion=emotion,interaction=interaction,animal=AvatarAnimal.AUSTRALIAN_SHEPHERD,
                cameraEnabled=true,gazeX=if(t<1f) .32f else if(t>12.3f) -.3f else 0f,gazeY=-.03f,
                speaking=if(interaction==InteractionState.SPEAKING) ((kotlin.math.sin(t*19f)+1f)*.23f) else 0f,
                affection=t in 10.6f..12.3f)
        }
        repeat(121) { rig.step(it/120f,LumenInput(animal=AvatarAnimal.AUSTRALIAN_SHEPHERD)) }
        repeat(14*120+1) { i ->
            val t=i/120f;val f=rig.step(1f+t,input(t))
            if(i%5!=0) return@repeat
            val b=Bitmap.createBitmap(960,640,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(b);canvas.drawColor(0xFF0B131B.toInt())
            val hero=draw(f,960,580,painter);canvas.drawBitmap(hero,0f,0f,null);hero.recycle()
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFFB8CED3.toInt();textSize=23f;textAlign=Paint.Align.CENTER }
            val label=when {t<2.4f->"Listening — the eyes notice first";t<4.8f->"Considering — a glance, then back to you";
                t<6.5f->"Uncertain — a question in the brow";t<8.2f->"Understanding — a small acknowledgement";
                t<10.6f->"Delight — lift, follow-through, settle";t<12.3f->"Affection — softer eyes and posture";
                else->"Your turn — mouth closes, attention returns"}
            canvas.drawText(label,480f,609f,p)
            save(b,"frames/${(i/5).toString().padStart(4,'0')}.png")
        }
        val measured = drawTimes.drop(24).sorted()
        fun ms(q: Float) = measured[((measured.size-1)*q).toInt()] / 1_000_000.0
        File(out,"render-cost.json").writeText("""{
          "environment": "Robolectric native renderer on development Mac; not phone frame timing",
          "viewport": "960x580", "warmupFrames": 24, "samples": ${measured.size},
          "medianDrawMs": ${ms(.5f)}, "p95DrawMs": ${ms(.95f)}, "maxDrawMs": ${ms(1f)},
          "scope": "CanvasDrawScope and painter only; excludes bitmap allocation, PNG encoding, inference and display scheduling"
        }""")
    }
}
