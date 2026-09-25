package com.example.meowmba.core.screen

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.meowmba.MainActivity
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** One consent token, one display, one frame. Never starts a UI from the background. */
class ScreenCaptureOnceService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("meow-screen-once")
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val finished = AtomicBoolean(false)
    private var started = false
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { fail("Screen sharing stopped before a picture was available.") }
    }
    override fun onCreate() { super.onCreate(); worker.start() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Meow screen capture", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(RESULT_CHANNEL, "Ask Meow", NotificationManager.IMPORTANCE_DEFAULT))
        try {
            ServiceCompat.startForeground(this, CAPTURE_ID, Notification.Builder(this, CHANNEL)
                .setContentTitle("Capturing one screen for Meow").setSmallIcon(android.R.drawable.ic_menu_view).build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            @Suppress("DEPRECATION")
            val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA) ?: error("Capture permission wasn't supplied.")
            val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
            require(code == Activity.RESULT_OK) { "Screen sharing wasn't approved." }
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
                ?: error("Screen sharing couldn't start.")
            projection!!.registerCallback(callback, main)
            // Let the consent activity and system sheet finish before sampling a whole display.
            main.postDelayed({ if (!finished.get()) createDisplay() }, 800)
            main.postDelayed({ fail("No screen arrived. Try again, or share a screenshot with Meow.") }, 10_000)
        } catch (e: Exception) {
            fail("Screen capture couldn't start. Try sharing a screenshot with Meow.")
        }
        return START_NOT_STICKY
    }

    private fun createDisplay() {
        try {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val scale = minOf(1.0, 1600.0 / maxOf(bounds.width(), bounds.height()))
            val width = (bounds.width() * scale).toInt().coerceAtLeast(1)
            val height = (bounds.height() * scale).toInt().coerceAtLeast(1)
            val target = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = target
            target.setOnImageAvailableListener({ r -> receive(r) }, Handler(worker.looper))
            display = projection?.createVirtualDisplay("meow-one-screen", width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, target.surface, null, main)
                ?: error("No capture display.")
        } catch (e: Exception) { fail("This screen couldn't be captured. Try sharing a screenshot.") }
    }

    private fun receive(source: ImageReader) {
        if (finished.get()) return
        var saved: File? = null
        try {
            val image = source.acquireLatestImage() ?: return
            image.use {
                if (!finished.compareAndSet(false, true)) return
                val plane = it.planes[0]
                val packed = ScreenPixels.pack(plane.buffer, it.width, it.height, plane.rowStride, plane.pixelStride)
                val bitmap = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(packed))
                    val directory = File(cacheDir, "screen_help").apply { mkdirs() }
                    directory.listFiles()?.filter { file -> System.currentTimeMillis() - file.lastModified() > ScreenHelp.MAX_AGE_MS }?.forEach { file -> file.delete() }
                    saved = File.createTempFile("screen-", ".jpg", directory)
                    saved!!.outputStream().use { stream -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)) }
                } finally { bitmap.recycle() }
            }
            val file = saved ?: error("No image")
            main.post {
                try {
                    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    val launch = Intent(this, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(EXTRA_CAPTURED_AT, System.currentTimeMillis())
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setDataAndType(uri, "image/jpeg") // Distinct PendingIntent identity for each snapshot.
                        .apply { clipData = ClipData.newUri(contentResolver, "Shared screen", uri) }
                    val pending = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    getSystemService(NotificationManager::class.java).notify(RESULT_ID,
                        Notification.Builder(this, RESULT_CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view)
                            .setContentTitle("Screen ready — ask Meow")
                            .setContentText("Tap to review this picture and ask a question.")
                            .setContentIntent(pending).setAutoCancel(true).setTimeoutAfter(10 * 60_000L).build())
                } catch (e: Exception) { file.delete(); showFailure("Couldn't open the captured image. Try sharing a screenshot.") }
                finally { stopCapture() }
            }
        } catch (e: Exception) {
            saved?.delete()
            finished.set(true)
            main.post { showFailure("Couldn't read this screen. Protected apps may block screenshots."); stopCapture() }
        }
    }

    private fun fail(message: String) {
        if (!finished.compareAndSet(false, true)) return
        main.post { showFailure(message); stopCapture() }
    }
    private fun showFailure(message: String) {
        runCatching { getSystemService(NotificationManager::class.java).notify(RESULT_ID,
            Notification.Builder(this, RESULT_CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Meow screen capture didn't finish").setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message)).setAutoCancel(true).build()) }
    }
    private fun release() {
        main.removeCallbacksAndMessages(null)
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); display = null
        reader?.close(); reader = null
        val p = projection; projection = null
        runCatching { p?.unregisterCallback(callback); p?.stop() }
    }
    private fun stopCapture() { release(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { finished.set(true); release(); worker.quitSafely(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL = "meow_capture"
        private const val RESULT_CHANNEL = "meow_screen_help"
        private const val CAPTURE_ID = 9901
        private const val RESULT_ID = 9902
        const val EXTRA_CODE = "capture_code"
        const val EXTRA_DATA = "capture_data"
        const val EXTRA_CAPTURED_AT = "meow_capture_at"
        fun start(context: Context, code: Int, data: Intent) {
            ContextCompat.startForegroundService(context, Intent(context, ScreenCaptureOnceService::class.java)
                .putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))
        }
    }
}
