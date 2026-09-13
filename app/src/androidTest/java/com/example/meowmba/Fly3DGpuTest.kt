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

/** Actual target-device GLES shader/depth/angle smoke; not a software-renderer screenshot. */
@RunWith(AndroidJUnit4::class)
class Fly3DGpuTest {
 @Test fun realGpuRendersDistinctViewsAndFreezesPausedFrames() {
  val display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
  val versions=IntArray(2);assertTrue(EGL14.eglInitialize(display,versions,0,versions,1))
  val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
  val attrs=intArrayOf(EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_DEPTH_SIZE,16,EGL14.EGL_NONE)
  assertTrue(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0));assertTrue(count[0]>0)
  val context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
  val w=640;val h=800
  val surface=EGL14.eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL14.EGL_WIDTH,w,EGL14.EGL_HEIGHT,h,EGL14.EGL_NONE),0)
  try {
   assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context))
   val r=Fly3DRenderer();r.onSurfaceCreated(null,null);r.onSurfaceChanged(null,w,h);assertNull(r.lastError)
   val output=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"fly3d-gpu").apply {mkdirs()}
   fun render(name:String,f:Fly3DFrame):ByteArray {
    r.frame=f;r.onDrawFrame(null);assertNull(r.lastError);assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError())
    val buffer=ByteBuffer.allocateDirect(w*h*4)
    GLES20.glReadPixels(0,0,w,h,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buffer)
    val raw=ByteArray(w*h*4);buffer.rewind();buffer.get(raw)
    val pixels=IntArray(w*h)
    for(y in 0 until h)for(x in 0 until w){val i=((h-1-y)*w+x)*4;pixels[y*w+x]=(255 shl 24)or((raw[i].toInt()and 255)shl 16)or((raw[i+1].toInt()and 255)shl 8)or(raw[i+2].toInt()and 255)}
    val bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
    File(output,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    return raw
   }
   val neutral=Fly3DFrame(y=2f,timeSeconds=1f)
   val front=render("front",neutral)
   val side=render("side",neutral.copy(yaw=1.57f))
   val back=render("back",neutral.copy(yaw=3.14f))
   assertFalse(front.contentEquals(side));assertFalse(side.contentEquals(back))
   val frozen=render("paused",neutral.copy(active=false,yaw=0f,timeSeconds=8f))
   assertArrayEquals(back,frozen)
   render("flight-quarter",neutral.copy(yaw=.6f,pitch=-.2f,roll=.15f,flight=1f,z=1f,mouth=.7f))
  } finally {
   EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
   EGL14.eglDestroySurface(display,surface);EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display)
  }
 }
}
