package com.example.meowmba

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.meowmba.ui.companion.Fly3DFrame
import com.example.meowmba.ui.companion.Fly3DRenderer
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual target GLES, including transparent compositing and the bounded postprocess target. */
@RunWith(AndroidJUnit4::class)
class Fly3DGpuTest {
    private class Fixture(val renderer:Fly3DRenderer,val w:Int,val h:Int) {
        fun render(name:String,frame:Fly3DFrame):ByteArray {
            renderer.frame=frame; renderer.onDrawFrame(null)
            assertNull(renderer.lastError); assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
            val buffer=ByteBuffer.allocateDirect(w*h*4)
            GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer)
            val raw=ByteArray(w*h*4); buffer.rewind();buffer.get(raw)
            val pixels=IntArray(w*h)
            for(y in 0 until h)for(x in 0 until w) {
                val i=((h-1-y)*w+x)*4
                pixels[y*w+x]=((raw[i+3].toInt()and 255)shl 24)or((raw[i].toInt()and 255)shl 16)or
                    ((raw[i+1].toInt()and 255)shl 8)or(raw[i+2].toInt()and 255)
            }
            val output=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"fly3d-gpu").apply{mkdirs()}
            val bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
            File(output,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            return raw
        }
    }

    private fun withGpu(w:Int=640,h:Int=800,block:(Fixture)->Unit) {
        val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val versions=IntArray(2);assertTrue(EGL14.eglInitialize(display,versions,0,versions,1))
        val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
        val attrs=intArrayOf(EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE)
        assertTrue(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0));assertTrue(count[0]>0)
        val context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
        val surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,w,EGL14.EGL_HEIGHT,h,EGL14.EGL_NONE),0)
        try {
            assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context))
            val renderer=Fly3DRenderer();renderer.onSurfaceCreated(null,null);renderer.onSurfaceChanged(null,w,h)
            assertNull(renderer.lastError)
            block(Fixture(renderer,w,h))
        } finally {
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
        }
    }

    @Test fun realGpuRendersDistinctViewsAndFreezesPausedFrames() = withGpu { gpu ->
        val neutral=Fly3DFrame(y=2f,timeSeconds=1f)
        val front=gpu.render("front",neutral)
        val side=gpu.render("side",neutral.copy(yaw=1.57f))
        val back=gpu.render("back",neutral.copy(yaw=3.14f))
        assertFalse(front.contentEquals(side));assertFalse(side.contentEquals(back))
        val frozen=gpu.render("paused",neutral.copy(active=false,yaw=0f,timeSeconds=8f))
        assertArrayEquals(back,frozen)
        gpu.render("flight-quarter",neutral.copy(yaw=.6f,pitch=-.2f,roll=.15f,flight=1f,z=1f,mouth=.7f))
    }

    @Test fun stageIsTransparentBoundedAndScaleUpdatesWhileMotionIsPaused() {
        for((w,h) in listOf(360 to 800,800 to 360,640 to 800))withGpu(w,h) { gpu ->
            fun alpha(raw:ByteArray,x:Int,y:Int)=raw[(y*w+x)*4+3].toInt()and 255
            fun visible(raw:ByteArray)=raw.indices.count{it%4==3&&(raw[it].toInt()and 255)>100}
            val base=Fly3DFrame(stageMode=true,timeSeconds=2f,flight=1f,yaw=.4f,avatarScale=.5f)
            val small=gpu.render("stage-small-$w-$h",base)
            val large=gpu.render("stage-large-$w-$h",base.copy(active=false,avatarScale=2f,timeSeconds=100f,yaw=3f))
            assertTrue("scale changes actual character pixels",visible(large)>visible(small)*1.35)
            for(x in 0 until w) {assertEquals(0,alpha(large,x,0));assertEquals(0,alpha(large,x,h-1))}
            for(y in 0 until h) {assertEquals(0,alpha(large,0,y));assertEquals(0,alpha(large,w-1,y))}
            assertEquals("no room in stage corner",0,alpha(large,w/10,h/10))
            val still=gpu.render("stage-still-$w-$h",base.copy(active=false,avatarScale=2f,timeSeconds=200f,yaw=-2f))
            assertArrayEquals(large,still)
            gpu.render("stage-neutral-$w-$h",base.copy(avatarScale=1f,flight=0f,yaw=0f))
        }
    }

    @Test fun antialiasedSilhouetteKeepsPremultipliedAlphaAndPixelBudget() {
        // One viewport can supersample; the other must obey the high-DPI allocation cap.
        for((w,h) in listOf(360 to 800,1100 to 1400))withGpu(w,h) { gpu ->
            val raw=gpu.render("edge-coverage-$w-$h",Fly3DFrame(stageMode=true,
                timeSeconds=2f,flight=.5f,yaw=.47f,roll=.11f))
            assertTrue("offscreen budget",gpu.renderer.lastRenderPixels in 1..1_200_000)
            if(w==360) assertTrue("unused budget improves real geometric coverage",gpu.renderer.lastRenderPixels>w*h)
            var partial=0
            for(i in raw.indices step 4) {
                val alpha=raw[i+3].toInt()and 255
                if(alpha in 2..253)partial++
                // Filtering transparent pixels must never introduce a white/dark unassociated fringe.
                for(channel in 0..2) if((raw[i+channel].toInt()and 255)>alpha+2)
                    fail("non-premultiplied channel at $i")
            }
            assertTrue("real fractional silhouette coverage",partial>50)
        }
    }

    @Test fun allTenRoomsRemainDistinctOpaqueAndWithinDrawBudget()=withGpu { gpu ->
        val hashes=mutableSetOf<Int>()
        for(id in listOf("kitchen","library","greenhouse","workshop","pond","orchard","cafe","observatory","beach","bedroom")) {
            val raw=gpu.render("room-$id",Fly3DFrame(backgroundId=id,timeSeconds=3f,y=1.35f))
            assertEquals(255,raw[3].toInt()and 255)
            assertTrue("distinct room $id",hashes.add(raw.contentHashCode()))
            assertTrue("bounded draw count for $id: ${gpu.renderer.lastDrawCalls}",gpu.renderer.lastDrawCalls<500)
            assertTrue("render pixel budget for $id",gpu.renderer.lastRenderPixels<=1_200_000)
            assertTrue("bounded triangles for $id: ${gpu.renderer.lastTriangles}",gpu.renderer.lastTriangles<500_000)
        }
        gpu.render("game-scale-small",Fly3DFrame(y=1.35f,avatarScale=.5f,handX=.42f,handY=1.15f,handZ=.2f))
        gpu.render("game-scale-large",Fly3DFrame(y=1.35f,avatarScale=2f,handX=.42f,handY=1.15f,handZ=.2f))
    }
}
