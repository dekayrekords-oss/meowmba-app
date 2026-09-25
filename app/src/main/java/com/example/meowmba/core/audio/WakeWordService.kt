package com.example.meowmba.core.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.meowmba.MainActivity
import com.example.meowmba.R
import com.example.meowmba.core.agent.tools.BatteryStatusTool
import com.example.meowmba.core.agent.tools.FlashlightTool
import com.example.meowmba.core.agent.tools.InstalledApps
import com.example.meowmba.core.agent.tools.LaunchAppTool
import com.example.meowmba.core.agent.tools.SetAlarmTool
import com.example.meowmba.core.agent.tools.SetTimerTool
import com.example.meowmba.core.agent.tools.ToolResult
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        const val EXTRA_MODE = "com.example.meowmba.WAKE_MODE"
        const val MODE_FOLLOW_UP = "follow_up"
        const val CHANNEL_ID = "memba_wake"
        const val COMMAND_CHANNEL_ID = "memba_wake_command"
        const val PREF_KEY = "background_listening"
        private const val FG_ID = 42
        private const val CMD_ID = 43
        private const val ACTION_STOP = "com.example.meowmba.WAKE_SERVICE_STOP"

        /**
         * Brief beat before opening the follow-up mic. The app stops its TTS
         * at hand-off, so this no longer has to outlast a full reply — just
         * let the audio pipeline settle. The user's reply usually starts
         * within a second, so the old 1100ms window ate the opening words.
         */
        private const val FOLLOW_UP_SETTLE_MS = 300L

        /** How many extra quiet windows a follow-up gets before falling back to wake. */
        private const val FOLLOW_UP_QUIET_WINDOWS = 2

        /**
         * Start/stop race guard. MainActivity.onResume calls stop() — stopService —
         * and can land milliseconds after onPause's startForegroundService(). The
         * platform then tears the still-starting service down and kills the whole
         * process for never calling startForeground() in time
         * (ForegroundServiceDidNotStartInTimeException). These three flags make
         * stop() a no-op while a start is on the wire, deferring the stop to the
         * service once it is safely foreground.
         */
        @Volatile private var serviceForeground = false
        @Volatile private var startPending = false
        @Volatile private var stopDuringStartPending = false

        fun start(context: Context) {
            if (!microphoneEnabled(context)) { stop(context); return }
            // A fresh start supersedes any stop deferred by a background ->
            // foreground -> background bounce in the same frame.
            stopDuringStartPending = false
            startPending = true
            // runCatching: a refused foreground-service start (background-start
            // restrictions) must never take the whole app down with it.
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, WakeWordService::class.java)
                )
            }.onFailure {
                startPending = false
                android.util.Log.w("WakeWordService", "start refused: ${it.message}")
            }
        }

        /**
         * Follow-up hand-off: the app just went to the background mid-conversation.
         * Skip the wake phrase and capture the user's reply directly.
         */
        fun startFollowUp(context: Context) {
            if (!microphoneEnabled(context)) { stop(context); return }
            stopDuringStartPending = false
            startPending = true
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WakeWordService::class.java)
                        .putExtra(EXTRA_MODE, MODE_FOLLOW_UP)
                )
            }.onFailure {
                startPending = false
                android.util.Log.w("WakeWordService", "follow-up start refused: ${it.message}")
            }
        }

        fun stop(context: Context) {
            // Never stopService() while a start is in flight: the system reports
            // "bringing down service while still waiting for start foreground" and
            // kills the process. Defer instead — the service stops itself right
            // after it has promoted, once the startForeground() deadline is met.
            if (!serviceForeground) {
                if (startPending) stopDuringStartPending = true
                return
            }
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        /** A service entry point cannot depend on a live ViewModel's privacy state. */
        private fun microphoneEnabled(context: Context): Boolean = runCatching {
            MicrophonePrivacy.isEnabled(SecureStorageManager(context))
        }.getOrDefault(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var speechService: SpeechService
    private lateinit var tts: TextToSpeechManager
    private lateinit var secureStorage: SecureStorageManager
    private var wakeEngine: WakeWordEngine? = null
    private var capturingCommand = false
    private var isForeground = false
    private var engineStarted = false
    private var followUpPending = false
    private var followUpRetries = 0
    /** Engine init job, so onDestroy can cancel a start that is still in flight. */
    private var engineInitJob: kotlinx.coroutines.Job? = null
    @Volatile private var micHeld = false
    @Volatile private var destroyed = false
    @Volatile private var privacyStopped = false
    /** Serialize publication/start with teardown, never the expensive engine construction. */
    private val engineLock = Any()

    override fun onCreate() {
        super.onCreate()
        runCatching { createChannels() }

        // The foreground promise comes BEFORE anything that can throw, and NO
        // path out of here may leave it unkept: once startForegroundService()
        // has been called the platform arms a startForeground() deadline that
        // stopSelf() does NOT cancel — leaving early without promoting (or
        // letting a stopService race win) got the whole app killed with
        // ForegroundServiceDidNotStartInTimeException, which as the HOME app
        // showed up as a black screen on every rotation.
        if (!promoteToForeground()) {
            startPending = false
            return
        }
        startPending = false
        serviceForeground = true
        // A stop() raced this start. The startForeground() deadline is already
        // satisfied, so stopping is safe; onStartCommand performs the stop.
        if (stopDuringStartPending) return

        secureStorage = try {
            SecureStorageManager(this)
        } catch (e: Exception) {
            android.util.Log.w("WakeWordService", "secure storage unavailable; stopping", e)
            stopSelf()
            return
        }
        if (!ensureMicrophoneEnabled()) return
        startEngineAsync()
    }

    /**
     * The engine is built OFF the main thread.
     *
     * It used to be built inline in onCreate, which runs on the app's main
     * thread — and this is the HOME app, so that thread is the launcher the
     * user is looking at. Building it means TextToSpeech, a SpeechService and
     * the keyword spotter (a ~5MB model load plus native init), all of which
     * were running inside onPause's synchronous service start. Pressing Back
     * froze the whole phone for that long.
     *
     * Only [promoteToForeground] has to stay synchronous: the platform arms a
     * startForeground() deadline as soon as startForegroundService() is called,
     * and missing it kills the process.
     */
    private fun startEngineAsync() {
        engineInitJob = scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val ok = runCatching { startEngine() }.onFailure { stopSelf() }.getOrDefault(false)
            android.util.Log.i("WakeWordService", "engine init finished ok=$ok")
            // A follow-up hand-off can be requested before the engine exists;
            // arm it now that init has landed (or failed).
            withContext(kotlinx.coroutines.Dispatchers.Main) { maybeStartFollowUpCapture() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // secureStorage is lateinit and only assigned when onCreate got as
            // far as building it; the Stop action must work (and not crash)
            // even from a degraded start.
            runCatching {
                if (::secureStorage.isInitialized) {
                    secureStorage.saveBooleanPreference(PREF_KEY, false)
                }
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.getStringExtra(EXTRA_MODE) == MODE_FOLLOW_UP &&
            !secureStorage.getBooleanPreference("quiet_until_addressed", false)) {
            followUpPending = true
            android.util.Log.i("WakeWordService", "follow-up hand-off requested")
        }
        // Second chance at the promise onCreate may have failed to keep. Giving
        // up here is the graceful path; leaving it unkept is the fatal one.
        if (!isForeground && !promoteToForeground()) {
            followUpPending = false
            stopSelf()
            return START_NOT_STICKY
        }
        // A stop() landed while this start was still on the wire. The deadline is
        // satisfied now, so tearing down is safe.
        if (stopDuringStartPending) {
            stopDuringStartPending = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!ensureMicrophoneEnabled()) return START_NOT_STICKY
        // The engine builds asynchronously (see startEngineAsync); a follow-up
        // requested before it exists is armed when the init lands.
        maybeStartFollowUpCapture()
        // NOT sticky. This is a user-toggled listener whose preference is persisted, so
        // it comes back on the next launch anyway. Sticky restarts resurrected it into a
        // fresh process after every process death — including every reinstall — where it
        // competed with the in-app loop for the recognizer and could not be seen by the
        // in-process MicOwner guard.
        return START_NOT_STICKY
    }

    /**
     * targetSdk 36 / Android 16: startForeground(id, notif) without a type
     * throws, the catch used to return, and the platform then killed the
     * whole process (ForegroundServiceDidNotStartInTimeException). Always
     * pass the microphone type declared in the manifest.
     */
    private fun enterForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            FG_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    /** True once the service is genuinely foreground. Never throws. */
    private fun promoteToForeground(): Boolean = try {
        enterForeground(
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

    /** Builds the wake engine. Call off the main thread. Returns true if it started. */
    private fun startEngine(): Boolean {
        if (!ensureMicrophoneEnabled()) return false
        val speech = SpeechService(this)
        val voice = TextToSpeechManager(this)
        var built: WakeWordEngineFactory.Result? = null
        var published = false
        try {
            // Build off-thread without holding the teardown lock. A kill/resume
            // during native initialization must not publish or start a late engine.
            built = WakeWordEngineFactory.create(
                context = this, scope = scope, companionName = companionName(),
                matcher = WakePhraseMatcher(companionName()), speechEngine = speech,
            )
            val candidate = built ?: run { stopSelf(); return false }
            candidate.engine.onWake = { command -> handleWake(command) }
            candidate.kws?.onFatalError = { reason ->
                android.util.Log.w("WakeWordService", "wake engine stopped: $reason")
                stopSelf()
            }
            synchronized(engineLock) {
                // Check the persisted choice again after potentially slow construction.
                if (microphoneCaptureAllowed() && MicOwner.acquire(MicOwner.SERVICE)) {
                    micHeld = true
                    speechService = speech
                    tts = voice
                    wakeEngine = candidate.engine
                    engineStarted = true
                    published = true
                    candidate.engine.start()
                }
            }
            if (!published) { stopSelf(); return false }
            android.util.Log.i("WakeWordService", "engine started; mic owned by service")
            return true
        } finally {
            if (!published) {
                // A never-started recognizer wrapper must NOT stop the shared
                // recognizer; the foreground app may already own a new session.
                built?.kws?.release()
                speech.destroy()
                voice.shutdown()
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        serviceForeground = false
        engineInitJob?.cancel()
        engineInitJob = null
        synchronized(engineLock) {
            followUpPending = false
            capturingCommand = false
            engineStarted = false
            // Only stop shared capture while this service still owns its slot.
            // A privacy stop may already have released it to the foreground app.
            if (micHeld) {
                wakeEngine?.stop()
                if (::speechService.isInitialized) speechService.stopListening()
                MicOwner.release(MicOwner.SERVICE)
                micHeld = false
            }
            if (::speechService.isInitialized) speechService.destroy()
            if (::tts.isInitialized) tts.shutdown()
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun microphoneCaptureAllowed(): Boolean = !destroyed && !privacyStopped &&
        ::secureStorage.isInitialized && runCatching {
            MicrophonePrivacy.isEnabled(secureStorage)
        }.getOrDefault(false)

    /** Fail closed, without changing the user's separate wake or speaker preferences. */
    private fun ensureMicrophoneEnabled(): Boolean {
        if (microphoneCaptureAllowed()) return true
        synchronized(engineLock) {
            privacyStopped = true
            followUpPending = false
            capturingCommand = false
            if (micHeld) {
                wakeEngine?.stop()
                if (::speechService.isInitialized) speechService.stopListening()
                MicOwner.release(MicOwner.SERVICE)
                micHeld = false
            }
        }
        stopSelf()
        return false
    }

    /** Every wake restart checks privacy, including delayed/error-driven retries. */
    private fun rearmWakeEngine() {
        if (!ensureMicrophoneEnabled()) return
        synchronized(engineLock) {
            if (microphoneCaptureAllowed() && micHeld) wakeEngine?.start()
        }
    }

    // ── Follow-up hand-off ──────────────────────────────────────────────────

    /**
     * Follow-up mode: the conversation was already live when the app went to
     * the background, so don't wait for the wake phrase — capture the user's
     * reply right now and deliver it like any wake command.
     */
    private fun maybeStartFollowUpCapture() {
        if (!ensureMicrophoneEnabled()) return
        if (secureStorage.getBooleanPreference("quiet_until_addressed", false)) { followUpPending = false; return }
        if (!followUpPending || !engineStarted || capturingCommand) {
            android.util.Log.i(
                "WakeWordService",
                "follow-up capture skipped (pending=$followUpPending engine=$engineStarted capturing=$capturingCommand)"
            )
            return
        }
        followUpPending = false
        capturingCommand = true
        followUpRetries = 0
        android.util.Log.i("WakeWordService", "follow-up capture armed")
        // Reflect the conversation turn in the ongoing notification.
        runCatching {
            enterForeground(buildPersistentNotification("Listening for your reply…"))
        }
        // The wake spotter and the recognizer must not fight over the mic.
        // Join the capture loop so AudioRecord is released before STT binds.
        scope.launch {
            wakeEngine?.stopAndAwait()
            delay(FOLLOW_UP_SETTLE_MS)
            if (capturingCommand) startFollowUpListen()
        }
    }

    private fun startFollowUpListen() {
        if (!ensureMicrophoneEnabled() || !capturingCommand || !engineStarted) return
        android.util.Log.i("WakeWordService", "follow-up listen: session starting")
        speechService.startListening(
            onResult = { captured ->
                if (!ensureMicrophoneEnabled()) return@startListening
                android.util.Log.i("WakeWordService", "follow-up captured: \"$captured\"")
                if (captured.isBlank()) {
                    // Quiet window ended empty; open one more before falling
                    // back to the wake loop, same as a silence error.
                    if (followUpRetries < FOLLOW_UP_QUIET_WINDOWS) {
                        followUpRetries++
                        scope.launch {
                            delay(250)
                            if (engineStarted && capturingCommand) startFollowUpListen()
                        }
                    } else {
                        capturingCommand = false
                        rearmWakeEngine()
                    }
                    return@startListening
                }
                followUpRetries = 0
                capturingCommand = false
                // A follow-up is mid-conversation with the screen on, so hand the
                // command straight to the app instead of leaving it as a notification
                // the user would have to tap while another app is in front.
                acknowledgeAndDeliver(captured, launchNow = true)
            },
            onError = { code ->
                if (!ensureMicrophoneEnabled()) return@startListening
                android.util.Log.w("WakeWordService", "follow-up listen error code=$code retry=$followUpRetries")
                // Error 11 and friends are service hiccups; retry silently a
                // couple of times before falling back to the wake loop.
                if (isTransientSpeechCode(code) && followUpRetries < 2) {
                    followUpRetries++
                    scope.launch {
                        delay(600)
                        // capturingCommand stays true across the retry.
                        if (engineStarted) startFollowUpListen()
                    }
                } else if (
                    isSilenceCode(code) &&
                    followUpRetries < FOLLOW_UP_QUIET_WINDOWS
                ) {
                    // Nothing heard in this window. The user may be about to
                    // speak; open one more window before giving up.
                    followUpRetries++
                    android.util.Log.i(
                        "WakeWordService",
                        "follow-up quiet window; opening another (retry=$followUpRetries)"
                    )
                    scope.launch {
                        delay(250)
                        if (engineStarted && capturingCommand) startFollowUpListen()
                    }
                } else {
                    capturingCommand = false
                    rearmWakeEngine()
                }
            }
        )
    }

    private fun isSilenceCode(code: Int): Boolean =
        code == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            code == android.speech.SpeechRecognizer.ERROR_NO_MATCH

    private fun isTransientSpeechCode(code: Int): Boolean =
        code == android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
            code == android.speech.SpeechRecognizer.ERROR_NETWORK ||
            code == android.speech.SpeechRecognizer.ERROR_SERVER ||
            code == android.speech.SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ||
            code == android.speech.SpeechRecognizer.ERROR_SERVER_DISCONNECTED

    // ── Wake handling ───────────────────────────────────────────────────────

    private fun handleWake(command: String?) {
        if (::secureStorage.isInitialized) secureStorage.saveBooleanPreference("quiet_until_addressed", false)
        if (!ensureMicrophoneEnabled()) return
        if (command != null) {
            acknowledgeAndDeliver(command)
        } else if (!capturingCommand) {
            capturingCommand = true
            tts.speak("Yes?")
            speechService.startListening(
                onResult = { captured ->
                    if (!ensureMicrophoneEnabled()) return@startListening
                    capturingCommand = false
                    if (captured.isBlank()) {
                        rearmWakeEngine()
                        return@startListening
                    }
                    acknowledgeAndDeliver(captured)
                },
                onError = {
                    if (!ensureMicrophoneEnabled()) return@startListening
                    capturingCommand = false
                    rearmWakeEngine()
                }
            )
        }
    }

    private fun acknowledgeAndDeliver(command: String, launchNow: Boolean = false) {
        if (!ensureMicrophoneEnabled()) return
        if (NonSpeechSound.parse(command) != null) { rearmWakeEngine(); return }
        if (com.example.meowmba.ui.companion.SpeechInterruptionPolicy.isQuietRequest(command)) {
            secureStorage.saveBooleanPreference("quiet_until_addressed", true)
            followUpPending = false
            capturingCommand = false
            tts.stop()
            rearmWakeEngine()
            return
        }
        wakeEngine?.stop()
        if (launchNow) {
            deliverToApp(command)
        } else if (tryRunInstantCommand(command)) {
            // App already launching — do not say "One moment."
        } else {
            if (com.example.meowmba.core.agent.PhoneAgentLoop.activeGoal == null) {
                tts.speak("One moment.")
            }
            notifyCommand(command)
        }
        rearmWakeEngine()
    }

    // ── Instant commands (no app surfacing needed) ──────────────────────────

    /**
     * Commands the background service can execute on its own, so the user never
     * has to tap a notification. The big one is opening an app: "open YouTube"
     * while in Facebook. Background activity starts are permitted here because
     * the companion's accessibility service is enabled (Android 10+ exemption).
     *
     * Returns true when handled (the result is spoken); false when the command
     * should fall through to the notification path so the full pipeline runs.
     */
    private fun tryRunInstantCommand(command: String): Boolean {
        val text = command.lowercase().trim()

        // Open / launch an app. Only when a real installed app matches — a miss
        // falls through so "start listening" or "open up about your feelings"
        // never get eaten here.
        val routed = com.example.meowmba.core.agent.runtime.AgentRouter.route(command)
        if (routed?.toolId == "phone.launch_app") {
            val appName = routed.args["name"].orEmpty()
            if (appName.isNotBlank() && InstalledApps.find(this, appName) != null) {
                runInstant { LaunchAppTool(applicationContext).execute(mapOf("name" to appName)) }
                return true
            }
            return false
        }

        if (text.startsWith("flashlight") || text.startsWith("flash light") || text.startsWith("torch")) {
            val state = when {
                text.contains("off") -> "off"
                text.contains("on") || text.contains("flash light") || text.startsWith("flashlight") || text.startsWith("torch") -> "on"
                else -> "toggle"
            }
            runInstant { FlashlightTool(applicationContext).execute(mapOf("state" to state)) }
            return true
        }

        if (text in setOf("battery", "battery level", "battery percentage", "how's my battery",
                "how is my battery", "how much battery", "my battery")) {
            runInstant { BatteryStatusTool(applicationContext).execute(emptyMap()) }
            return true
        }

        // "set (an) alarm for/at <time>"
        if (text.startsWith("set an alarm") || text.startsWith("set alarm") || text.startsWith("wake me")) {
            val time = parseClockTime(text)
            if (time != null) {
                runInstant { SetAlarmTool(applicationContext).execute(mapOf("hour" to time.hour.toString(), "minutes" to time.minute.toString())) }
                return true
            }
            return false
        }

        // "set a timer for <N> minutes/seconds"
        if (text.startsWith("set a timer") || text.startsWith("set timer") || text.startsWith("timer for")) {
            val minutes = Regex("(\\d{1,4})\\s*(min|minute|minutes|mins)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val seconds = Regex("(\\d{1,4})\\s*(sec|second|seconds|secs)").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val arg = when {
                seconds != null -> mapOf("seconds" to seconds.toString())
                minutes != null -> mapOf("minutes" to minutes.toString())
                else -> null
            }
            if (arg != null) {
                runInstant { SetTimerTool(applicationContext).execute(arg) }
                return true
            }
            return false
        }

        return false
    }

    /** Runs a tool in the service scope and speaks its outcome. */
    private fun runInstant(block: suspend () -> ToolResult) {
        scope.launch {
            if (!ensureMicrophoneEnabled()) return@launch
            val result = try {
                block()
            } catch (e: Exception) {
                ToolResult.Failure(e.message ?: "Something went wrong.")
            }
            // The user may have said "be quiet" while the tool was running.
            if (secureStorage.getBooleanPreference("quiet_until_addressed", false)) return@launch
            when (result) {
                is ToolResult.Success -> tts.speak(result.output)
                is ToolResult.Failure -> tts.speak(result.error)
            }
        }
    }

    private data class ClockTime(val hour: Int, val minute: Int)

    /** "7:30 pm" → 19:30. Handles "7", "7 am", "7:05 pm". Null if no time is recognizable. */
    private fun parseClockTime(text: String): ClockTime? {
        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.?|p\\.m\\.?)?").find(text)
            ?: return null
        val hourRaw = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        if (hourRaw !in 0..12 || minute !in 0..59) return null
        var hour = hourRaw
        val pm = m.groupValues[3].contains("p")
        if (pm && hourRaw != 12) hour += 12
        if (!pm && hourRaw == 12) hour = 0
        return ClockTime(hour, minute)
    }

    private val APP_OPEN_PREFIXES = listOf(
        "open the app ", "open app ", "launch the app ", "launch app ",
        "launch ", "start the app ", "open up ", "open "
    )

    /**
     * Bring the companion to the front with the captured command so it runs through
     * the normal voice pipeline. The activity is the home launcher, so surfacing it
     * to act on a spoken follow-up is the expected behavior; onResume stops this
     * service and reclaims the mic, ending the background session cleanly.
     */
    private fun deliverToApp(command: String) {
        if (!ensureMicrophoneEnabled()) return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WAKE_COMMAND, command)
        }
        runCatching { startActivity(intent) }
            .onFailure { e ->
                // Fall back to the notification path rather than dropping the command.
                android.util.Log.w("WakeWordService", "could not surface command; notifying", e)
                notifyCommand(command)
            }
        // Keep a visible record either way.
        notifyCommand(command)
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
        if (!ensureMicrophoneEnabled()) return
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

    private fun companionName(): String = runCatching {
        if (::secureStorage.isInitialized) secureStorage.getPreference("companion_name") else null
    }.getOrNull() ?: "Meow"
}
