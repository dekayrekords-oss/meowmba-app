package com.example.meowmba.core.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.meowmba.MainActivity
import com.example.meowmba.R
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Background wake-phrase listener (Phase 2.5).
 *
 * A foreground service with foregroundServiceType="microphone" — the only
 * sanctioned way to hold the mic while the app is in the background. A
 * persistent notification is shown at all times (never silent), with a Stop
 * action that disables background listening until re-enabled.
 *
 * What it does with the screen off:
 *  1. Listens for the wake phrase ("Hey Memba" / "Hey Meow" / "Hey [name]").
 *  2. On wake it acknowledges ("Yes?") and captures one command utterance.
 *  3. Android 10+ forbids launching activities from the background, so the
 *     command is delivered via a high-priority notification: tapping it opens
 *     Meowmba with the command already streaming through the normal pipeline.
 *
 * Lifecycle: MainActivity starts this service in onPause (app going to
 * background) and stops it in onResume (the in-app engine takes over) — the
 * two never hold the mic at once.
 */
class WakeWordService : Service() {

    companion object {
        const val EXTRA_WAKE_COMMAND = "com.example.meowmba.WAKE_COMMAND"
        const val CHANNEL_ID = "memba_wake"
        const val COMMAND_CHANNEL_ID = "memba_wake_command"
        const val PREF_KEY = "background_listening"
        private const val FG_ID = 42
        private const val CMD_ID = 43
        private const val ACTION_STOP = "com.example.meowmba.WAKE_SERVICE_STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, WakeWordService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var speechService: SpeechService
    private lateinit var tts: TextToSpeechManager
    private lateinit var secureStorage: SecureStorageManager
    private var wakeEngine: RecognizerWakeWordEngine? = null
    private var capturingCommand = false
    private var isForeground = false
    private var engineStarted = false

    override fun onCreate() {
        super.onCreate()
        secureStorage = SecureStorageManager(this)
        createChannels()

        // Android 16 refuses a microphone foreground service started from the
        // background. Swallowing that failure and carrying on is what crashed
        // the whole app: startForegroundService() promises the system a
        // startForeground() call, and when none arrives the platform kills the
        // process with ForegroundServiceDidNotStartInTimeException. If we can't
        // go foreground we must stop instead — onStartCommand retries, since by
        // then the app is often in the foreground and the start is permitted.
        if (!promoteToForeground()) return

        startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            secureStorage.saveBooleanPreference(PREF_KEY, false)
            stopSelf()
            return START_NOT_STICKY
        }

        // Second chance at the promise onCreate may have failed to keep. Giving
        // up here is the graceful path; leaving it unkept is the fatal one.
        if (!isForeground && !promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!engineStarted) startEngine()
        // NOT sticky. This is a user-toggled listener whose preference is persisted, so
        // it comes back on the next launch anyway. Sticky restarts resurrected it into a
        // fresh process after every process death — including every reinstall — where it
        // competed with the in-app loop for the recognizer and could not be seen by the
        // in-process MicOwner guard.
        return START_NOT_STICKY
    }

    /** True once the service is genuinely foreground. Never throws. */
    private fun promoteToForeground(): Boolean = try {
        startForeground(
            FG_ID,
            buildPersistentNotification("Listening for \"Hey ${companionName()}\"")
        )
        isForeground = true
        true
    } catch (e: Exception) {
        android.util.Log.w(
            "WakeWordService",
            "Could not enter the foreground; stopping rather than risking a " +
                "system kill. Background listening will resume next time the " +
                "app is open.",
            e
        )
        false
    }

    private fun startEngine() {
        // Exclusive with the in-app wake loop. Without this both drove sessions at once
        // and the platform answered with a steady stream of ERROR_RECOGNIZER_BUSY.
        if (!MicOwner.acquire(MicOwner.SERVICE)) {
            android.util.Log.i(
                "WakeWordService",
                "in-app wake loop owns the mic; not starting a second one"
            )
            stopSelf()
            return
        }
        speechService = SpeechService(this)
        tts = TextToSpeechManager(this)
        wakeEngine = RecognizerWakeWordEngine(
            speechService, WakePhraseMatcher(companionName()), scope
        ).apply {
            onWake = { command -> handleWake(command) }
        }
        wakeEngine?.start()
        engineStarted = true
    }

    override fun onDestroy() {
        MicOwner.release(MicOwner.SERVICE)
        wakeEngine?.stop()
        // These are lateinit and are only assigned by startEngine(), which is
        // skipped when the service stops early. Touching them unguarded would
        // turn a handled failure into an UninitializedPropertyAccessException.
        if (engineStarted) {
            speechService.destroy()
            tts.shutdown()
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Wake handling ───────────────────────────────────────────────────────

    private fun handleWake(command: String?) {
        if (command != null) {
            acknowledgeAndDeliver(command)
        } else if (!capturingCommand) {
            capturingCommand = true
            tts.speak("Yes?")
            speechService.startListening(
                onResult = { captured ->
                    capturingCommand = false
                    acknowledgeAndDeliver(captured)
                },
                onError = {
                    capturingCommand = false
                    wakeEngine?.start()
                }
            )
        }
    }

    private fun acknowledgeAndDeliver(command: String) {
        wakeEngine?.stop()
        tts.speak("One moment.")
        notifyCommand(command)
        wakeEngine?.start()
    }

    // ── Notifications ───────────────────────────────────────────────────────

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wake phrase", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(COMMAND_CHANNEL_ID, "Wake commands", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildPersistentNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Memba is listening")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun notifyCommand(command: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WAKE_COMMAND, command)
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, COMMAND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Meow heard you")
            .setContentText(command)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        getSystemService(NotificationManager::class.java).notify(CMD_ID, notification)
    }

    private fun companionName(): String =
        secureStorage.getPreference("companion_name") ?: "Meow"
}
