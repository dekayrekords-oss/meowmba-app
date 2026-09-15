package com.example.meowmba

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.meowmba.core.background.BackgroundAnimation
import com.example.meowmba.ui.companion.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabitatMotionTest {
    private val objects = HabitatObjectAnimations()
    private fun render(source: Bitmap, room: BackgroundAnimation, t: Float, w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(b.asImageBitmap()), Size(w.toFloat(),h.toFloat())) {
            drawIllustratedRoom(source.asImageBitmap(), room, objects, t)
        }
        return b
    }
    private fun source(room: BackgroundAnimation) = RuntimeEnvironment.getApplication().assets
        .open("backgrounds/painted/${room.id}.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }

    @Test fun allNineteenRoomsAnimateOnlySmallDetailsAndNeverMoveTheForeground() {
        val rooms = BackgroundAnimation.entries.filter { it.isPetEnvironment }
        assertEquals(19,rooms.size)
        val report = StringBuilder("room,viewport,changedPixels,totalPixels\n")
        for(room in rooms) {
            val source = source(room)
            for((w,h) in listOf(360 to 800, 800 to 360)) {
                val a=render(source,room,.13f,w,h);val b=render(source,room,.39f,w,h)
                val c=render(source,room,.13f,w,h)
                assertTrue("Paused/redrawn room must replay identically",a.sameAs(c));c.recycle()
                val left=IntArray(w*h);val right=IntArray(w*h)
                a.getPixels(left,0,w,0,0,w,h);b.getPixels(right,0,w,0,0,w,h)
                var changed=0
                for(i in left.indices) if(left[i]!=right[i]) {
                    changed++
                    assertTrue("${room.id}: foreground shifted",i/w < h*.78f)
                }
                // Wide crops may exclude a room's windows. Do not invent motion on the
                // floor to compensate: every room must animate in its portrait composition.
                if(w<h) assertTrue("${room.id} $w x $h has visible animation",changed>3)
                assertTrue("${room.id}: broad image motion/tint detected ($changed)",changed < w*h*.025f)
                report.append("${room.id},${w}x$h,$changed,${w*h}\n")
                if(w==360) save(a,"${room.id}-portrait.png") else save(a,"${room.id}-landscape.png")
                a.recycle();b.recycle()
            }
            source.recycle()
        }
        File("build/reports/habitat134/motion.csv").writeText(report.toString())
    }

    @Test fun aquariumAndArcadeLoopWithoutResettingTheRoom() {
        for(room in listOf(BackgroundAnimation.CARTOON_ALIEN_AQUARIUM,BackgroundAnimation.CARTOON_PORTAL_ARCADE)) {
            val source=source(room)
            val start=render(source,room,0f,600,600);val end=render(source,room,1f,600,600)
            assertTrue("Loop wraps to identical pixels",start.sameAs(end));start.recycle();end.recycle()
            repeat(24) { i -> val b=render(source,room,.10f+i/132f,600,600)
                save(b,"${room.id}/frame-${i.toString().padStart(2,'0')}.png");b.recycle() }
            source.recycle()
        }
    }

    @Test fun thirtyPercentHabitatGrowthPreservesBackdropSizeAndTouchMapping() {
        val frame=LumenFrame()
        for((w,h) in listOf(360f to 800f,800f to 360f)) for(zoom in listOf(.5f,2f,3f)) {
            val backdrop=generatedAvatarPlacement(w,h,frame,zoom)
            val habitat=generatedAvatarPlacement(w,h,frame,zoom,FullFlyPose())
            assertEquals(minOf(w,h)*.42f*zoom,backdrop.unit,.001f)
            assertEquals(backdrop.unit*.55f*1.3f,habitat.unit,.001f)
            assertTrue(habitat.owns(habitat.center+Offset(habitat.unit*.8f,0f)))
            assertFalse(habitat.owns(habitat.center+Offset(habitat.unit*.9f,0f)))
            assertEquals(.4f*zoom*1.3f,habitatFaceScale(zoom),.0001f)
            assertEquals(1.22f*zoom*1.3f,habitatFlyScale(zoom),.0001f)
            val center=lumenCenter(w,h,frame)
            val unit=lumenUnit(w,h,frame)
            val transform=AvatarHitTransform(center,Offset(w*.6f,h*.4f),habitatFaceScale(zoom))
            val visible=transform.destination+Offset(-unit*.213f,0f)*transform.scale
            assertEquals(AvatarZone.EYE_LEFT,lumenZoneAt(transform.inverse(visible),w.toInt(),h.toInt(),frame))
        }
    }

    private fun save(b:Bitmap,name:String) {
        val out=File("build/reports/habitat134/$name");out.parentFile?.mkdirs()
        out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
