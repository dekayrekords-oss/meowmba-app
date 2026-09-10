package com.example.meowmba.core.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Watches the phone's own screen while "watch mode" is on.
 *
 * Deliberately low-impact: one frame every 30 seconds, downscaled to at most
 * 512px wide, and parked entirely while the display is off. The user grants
 * MediaProjection once via the system consent dialog; the persistent
 * notification keeps the capture visible at all times.
 */
class ScreenWatchService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    // Capture off the main thread: Bitmap allocs + buffer copies used to run on
    // the UI looper and hitch scrolling exactly when watch mode was on.
    private var captureThread: android.os.HandlerThread? = null
    private var handler: Handler? = null
    /** True once watch mode is armed; stays true while the screen is merely off. */
    private var captureArmed = false

    /**
     * Capturing while the display is off burns CPU for a frame nobody can change:
     * MediaProjection still composites and the ImageReader still copies, but the
     * content is frozen. This was a major contributor to sustained device heating,
     * so the loop is parked whenever the screen goes off.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> pauseCapture()
                Intent.ACTION_SCREEN_ON -> resumeCapture()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val t = android.os.HandlerThread("screen-watch").also { it.start() }
        captureThread = t
        handler = Handler(t.looper)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive ?: true
    }

    /** Arms the loop but does not start it while the screen is off. */
    private fun armCapture() {
        captureArmed = true
        if (isScreenOn()) scheduleNext()
    }

    private fun pauseCapture() {
        handler?.removeCallbacks(captureLoop)
    }

    private fun resumeCapture() {
        if (!captureArmed) return
        if (!isScreenOn()) return
        scheduleNext()
    }

    private fun scheduleNext() {
        handler?.removeCallbacks(captureLoop)
        handler?.post(captureLoop)
    }

    private val captureLoop = object : Runnable {
        override fun run() {
            captureFrame()
            handler?.postDelayed(this, INTERVAL_MS)
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
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
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
            armCapture()
        }
        return START_NOT_STICKY
    }

    private fun startVirtualDisplay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        // Create the virtual display at capture size, not full pixels: the old
        // code composited e.g. 2208x1840 RGBA (~16MB/frame) then downscaled to
        // 512px on the main thread every 8s. Capturing at 512px wide skips the
        // full-res alloc/copy/scale and the main-thread Bitmap churn entirely.
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val scale = (MAX_EDGE.toFloat() / w).coerceAtMost(1f)
        val sw = (w * scale).toInt().coerceAtLeast(1)
        val sh = (h * scale).toInt().coerceAtLeast(1)
        val sdpi = (metrics.densityDpi * scale).toInt().coerceAtLeast(1)
        imageReader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "meowmba-screen-watch", sw, sh, sdpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null,
        )
    }

    private fun captureFrame() {
        // Defensive: the receiver should already have parked the loop, but never
        // composite a frame for a display that is off.
        if (!isScreenOn()) return
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
            .setContentText("One glance every 30 seconds while the screen is on.")
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
        captureArmed = false
        runCatching { unregisterReceiver(screenReceiver) }
        handler?.removeCallbacks(captureLoop)
        virtualDisplay?.release()
        imageReader?.close()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        captureThread?.quitSafely()
        captureThread = null
        handler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.meowmba.screen.STOP"
        const val EXTRA_RESULT = "projection_result"
        const val EXTRA_DATA = "projection_data"
        private const val CHANNEL_ID = "screen_watch"
        private const val NOTIF_ID = 4242
        // Was 8s. Continuous MediaProjection capture was the single largest
        // contributor to device heating: measured Thermal Status 2 (MODERATE),
        // skin 42.7 C, app holding ~38% CPU with 23+ min accumulated CPU time.
        // 30s keeps watch mode responsive to real screen changes while cutting
        // capture work ~3.7x, and the screen-off gate parks the loop entirely
        // whenever the display is off.
        private const val INTERVAL_MS = 30_000L
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
