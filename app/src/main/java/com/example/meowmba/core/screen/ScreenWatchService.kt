package com.example.meowmba.core.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Watches the phone's own screen while "watch mode" is on.
 *
 * Deliberately low-impact: one frame every 8 seconds, downscaled to at most
 * 512px wide JPEG-quality bitmap. The user grants MediaProjection once via
 * the system consent dialog; the persistent notification makes the capture
 * visible at all times.
 */
class ScreenWatchService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private val captureLoop = object : Runnable {
        override fun run() {
            captureFrame()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            virtualDisplay?.release(); virtualDisplay = null
            imageReader?.close(); imageReader = null
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (resultCode != RESULT_OK_CODE || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection == null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val p = mgr.getMediaProjection(resultCode, data)
            if (p == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            p.registerCallback(projectionCallback, handler)
            projection = p
            startVirtualDisplay()
            handler.post(captureLoop)
        }
        return START_NOT_STICKY
    }

    private fun startVirtualDisplay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "meowmba-screen-watch", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null,
        )
    }

    private fun captureFrame() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(bytes))
            if (bmp.width > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / bmp.width
                val small = Bitmap.createScaledBitmap(bmp, MAX_EDGE, (bmp.height * scale).toInt(), true)
                bmp.recycle()
                bmp = small
            }
            ScreenWatchCoordinator.postFrame(bmp)
        } catch (_: Exception) {
            // Frame glitches are expected occasionally; the next tick retries.
        } finally {
            image.close()
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "Screen watch", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        val stopIntent = Intent(this, ScreenWatchService::class.java).setAction(ACTION_STOP)
        val stopAction = Notification.Action.Builder(
            null, "Stop watching",
            PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Meow is watching the screen")
            .setContentText("One glance every few seconds while watch mode is on.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(stopAction)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    packageManager.getLaunchIntentForPackage(packageName),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureLoop)
        virtualDisplay?.release()
        imageReader?.close()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.meowmba.screen.STOP"
        const val EXTRA_RESULT = "projection_result"
        const val EXTRA_DATA = "projection_data"
        private const val CHANNEL_ID = "screen_watch"
        private const val NOTIF_ID = 4242
        private const val INTERVAL_MS = 8_000L
        private const val MAX_EDGE = 512
        private const val RESULT_OK_CODE = -1

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenWatchService::class.java)
                .putExtra(EXTRA_RESULT, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenWatchService::class.java).setAction(ACTION_STOP))
        }
    }
}
