package com.example.meowmba.ui.companion

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import java.util.concurrent.atomic.AtomicBoolean

/** Window-composited GPU surface: Compose privacy indicators and controls stay above it.
 * The existing avatar clock is the only producer. A single pending draw always reads
 * the newest immutable frame, so a slow GPU cannot accumulate animation jobs. */
internal class Fly3DView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private val worker = HandlerThread("MeowFlyGL").apply { start() }
    private val handler = Handler(worker.looper)
    private val scheduled = AtomicBoolean(false)
    val renderer = Fly3DRenderer()
    @Volatile private var disposed = false
    @Volatile private var active = true
    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var ready = false
    var onFailure: ((String) -> Unit)? = null

    init { isOpaque = false; surfaceTextureListener = this; isClickable = false }
    fun publish(frame: Fly3DFrame) {
        val old = renderer.frame
        renderer.frame = frame
        val appearanceChanged = old.avatarScale != frame.avatarScale || old.stageMode != frame.stageMode || old.backgroundId != frame.backgroundId
        if (!disposed && (active || appearanceChanged)) requestFrame(appearanceChanged)
    }
    fun setStageActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) requestFrame()
    }
    private fun requestFrame(force: Boolean = false) {
        if (disposed || !scheduled.compareAndSet(false, true)) return
        handler.post {
            scheduled.set(false)
            if (ready && (active || force) && !disposed) {
                try {
                    check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) { "EGL context unavailable" }
                    renderer.onDrawFrame(null)
                    renderer.lastError?.let { error(it); return@post }
                    check(EGL14.eglSwapBuffers(display, eglSurface)) { "EGL frame failed" }
                } catch (e: RuntimeException) { error(e.message ?: "GPU rendering failed") }
            }
        }
    }
    private fun error(message: String) { ready = false; post { onFailure?.invoke(message) } }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post {
            if (disposed) return@post
            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                val version = IntArray(2)
                check(EGL14.eglInitialize(display, version, 0, version, 1))
                val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
                val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE)
                check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0)
                config = configs[0]
                eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
                eglSurface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
                check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext))
                renderer.onSurfaceCreated(null, null)
                renderer.onSurfaceChanged(null, width, height)
                ready = renderer.lastError == null
                // One still is allowed on initial mount, even while thermally paused.
                renderer.onDrawFrame(null); EGL14.eglSwapBuffers(display, eglSurface)
                renderer.lastError?.let { error(it) }
            } catch (e: RuntimeException) { error(e.message ?: "3D graphics unavailable") }
        }
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post { if (ready && !disposed) { renderer.onSurfaceChanged(null, width, height); requestFrame() } }
    }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        handler.post { releaseEgl(); texture.release() }
        return false // Release the texture only after the GL thread stops using its native window.
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    private fun releaseEgl() {
        ready = false
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY; eglSurface = EGL14.EGL_NO_SURFACE; eglContext = EGL14.EGL_NO_CONTEXT
    }
    fun dispose() {
        disposed = true; active = false
        val texture = surfaceTexture
        surfaceTextureListener = null
        handler.post { releaseEgl(); texture?.release(); worker.quitSafely() }
    }
}
