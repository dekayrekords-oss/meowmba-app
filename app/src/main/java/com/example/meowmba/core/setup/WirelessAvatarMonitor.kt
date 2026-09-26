package com.example.meowmba.core.setup

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.meowmba.MainActivity

/** Event-driven while Meow's process is alive. No polling, wake lock, mic or new service. */
class WirelessAvatarMonitor(private val app: Application) : Application.ActivityLifecycleCallbacks {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val policy = WirelessAvatarPolicy()
    private val handler = Handler(Looper.getMainLooper())
    private var foreground: MainActivity? = null
    private var pending = false
    private var plugged = -1
    private val evaluate = Runnable { evaluate() }
    private val changes = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { evaluate() }
    }

    fun install() {
        prefs.registerOnSharedPreferenceChangeListener(changes)
        app.registerActivityLifecycleCallbacks(this)
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                }
                // Settle coil alignment before navigating. Battery updates do not reopen it.
                handler.removeCallbacks(evaluate)
                handler.postDelayed(evaluate, 750)
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        })
    }

    private fun evaluate() {
        val enabled = prefs.getBoolean(ENABLED, false)
        if (policy.update(plugged, enabled, SystemClock.elapsedRealtime())) pending = true
        if (!enabled) {
            pending = false
            notifications().cancel(NOTIFICATION)
            return
        }
        if (plugged < 0 || plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS == 0) {
            // Never navigate without a current wireless reading. Retain an undelivered
            // request through a brief coil dropout; it can only run if power returns.
            notifications().cancel(NOTIFICATION)
            return
        }
        if (!pending) return
        // Never interrupt first-run guidance, wake the screen or bypass the lock screen.
        if (!SetupWiring.isOnboardingDone(app)) return
        if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !app.getSystemService(PowerManager::class.java).isInteractive) return
        foreground?.let {
            pending = false
            notifications().cancel(NOTIFICATION)
            it.showWirelessAvatar()
            return
        }
        if (prefs.getBoolean(BACKGROUND, false) && Settings.canDrawOverlays(app)) {
            // Explicit opt-in and Android's documented background-start exception.
            runCatching { app.startActivity(avatarIntent(app)) }
        }
        // A successful startActivity call does not prove Android displayed it.
        // Keep a silent tap fallback until onResume confirms foreground delivery.
        val nm = notifications()
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wireless charger avatar", NotificationManager.IMPORTANCE_LOW))
        val tap = PendingIntent.getActivity(app, NOTIFICATION, avatarIntent(app),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            nm.notify(NOTIFICATION, NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(com.example.meowmba.R.mipmap.ic_launcher)
                .setContentTitle("Open your charging companion")
                .setContentText("Tap to show the avatar on this wireless charger.")
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true).build())
        }
        // No repeated background launches or notifications for this charging session.
        pending = false
    }

    private fun notifications() = app.getSystemService(NotificationManager::class.java)
    override fun onActivityResumed(activity: Activity) {
        if (activity is MainActivity) {
            foreground = activity
            notifications().cancel(NOTIFICATION)
            handler.post { evaluate() }
        }
    }
    override fun onActivityPaused(activity: Activity) { if (foreground === activity) foreground = null }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        const val PREFS = "wireless_avatar"
        const val ENABLED = "enabled"
        const val BACKGROUND = "background"
        const val ACTION = "com.example.meowmba.SHOW_CHARGING_AVATAR"
        private const val CHANNEL = "wireless_avatar"
        private const val NOTIFICATION = 18101
        fun avatarIntent(context: Context) = Intent(context, MainActivity::class.java)
            .setAction(ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
