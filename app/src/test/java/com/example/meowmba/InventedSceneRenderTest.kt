package com.example.meowmba

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.*
import com.example.meowmba.core.agent.tools.AvatarAnimationInventor
import com.example.meowmba.ui.companion.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InventedSceneRenderTest {
    @Test fun nativeFishingFixtureRendersAndLeavesPrivacyFixed() {
        val spec=AvatarAnimationInventor.parseResponse(FishingFixture.json).first
        val painter=LumenPainter()
        val out=File("build/reports/local-animation").apply{mkdirs()}
        val sheet=Bitmap.createBitmap(1440,810,Bitmap.Config.ARGB_8888)
        val sheetCanvas=android.graphics.Canvas(sheet)
        var privacy: IntArray? = null
        for((index,time) in listOf(700,1700,3400,4300,5500,6600).withIndex()) {
            val pose=CustomAnimations.sample(spec,time.toLong())!!
            val input=lumenInventedSceneInput(LumenInput(animal=AvatarAnimal.AUSTRALIAN_SHEPHERD,performing=true,
                tilt=pose.headTiltDeg?:0f,bounce=pose.bodyBounce?:0f,openness=pose.eyeOpen?:1f),spec,time.toFloat())
            val rig=LumenRig();var frame=LumenFrame()
            repeat(91){frame=rig.step(it/60f,input)}
            val bitmap=Bitmap.createBitmap(720,540,Bitmap.Config.ARGB_8888)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(bitmap.asImageBitmap()),Size(720f,540f)) {
                drawRect(Color(0xFF0B131B))
                painter.draw(this,frame,Color(0xFF66B8C8),Color(0xFF84D9EE),cameraOff=true,micOff=true,speakerOff=true,
                    inventedScene=spec,inventedTimeMs=time.toFloat())
            }
            File(out,"fishing-$index.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            sheetCanvas.drawBitmap(bitmap,null,android.graphics.Rect(index%3*480,index/3*405,index%3*480+480,index/3*405+360),null)
            assertEquals("top edge",Color(0xFF0B131B).toArgb(),bitmap.getPixel(0,0))
            val pixels=IntArray(180*60)
            bitmap.getPixels(pixels,0,180,0,480,180,60)
            privacy?.let { assertArrayEquals("privacy does not follow props",it,pixels) }
            privacy=pixels
            bitmap.recycle()
        }
        File(out,"fishing-storyboard.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
        sheet.recycle()
    }
}
