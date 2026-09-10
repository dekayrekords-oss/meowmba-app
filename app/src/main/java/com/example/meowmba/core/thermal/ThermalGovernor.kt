package com.example.meowmba.core.thermal

import android.content.Context
import android.os.PowerManager
import android.util.Log

/** How hard the device may be driven right now. Ordered coolest to hottest. */
enum class ThermalTier { NORMAL, ECONOMY, SURVIVAL }

/**
 * One process-wide answer to "is the device hot?".
 *
 * Before this, thermal state was re-derived in three places that never agreed:
 * `AvatarWorkPolicy` kept its own listener behind a Composable, `VisionManager`
 * polled `PowerManager.currentThermalStatus` (a binder call) at 10 Hz inside its
 * analysis loop, and `WorkflowRuntime` read it once per run. Nothing covered
 * audio at all — which is why an always-on recognizer could sit at full rate
 * while the device cooked.
 *
 * This object owns a single [PowerManager.OnThermalStatusChangedListener], so
 * consumers read a cached [Int] instead of each making binder calls.
 *
 * Escalation is immediate; de-escalation waits out [RECOVERY_MS]. Without that
 * hysteresis a device sitting exactly on a threshold flaps between tiers, and
 * every flap tears down and restarts a microphone or a camera session — which
 * costs more heat than just staying throttled.
 *
 * Installed from [com.example.meowmba.MeowmbaApp] rather than MainActivity,
 * because `WakeWordService` and the other foreground services can start this
 * process without an activity ever being created.
 */
object ThermalGovernor {

    private const val TAG = "ThermalGovernor"

    /** How long the device must stay below a tier's threshold before we relax. */
    private const val RECOVERY_MS = 60_000L

    private val lock = Any()

    @Volatile private var lastStatus = PowerManager.THERMAL_STATUS_NONE
    @Volatile private var tier = ThermalTier.NORMAL
    @Volatile private var coolSinceMs = 0L
    @Volatile private var installed = false

    private var power: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    /** Idempotent. Safe to call from any process entry point. */
    fun install(context: Context) {
        synchronized(lock) {
            if (installed) return
            val pm = context.getSystemService(PowerManager::class.java) ?: run {
                Log.w(TAG, "PowerManager unavailable; thermal tier stays NORMAL")
                return
            }
            // Keep the manager even if the listener fails: currentStatus() then
            // degrades to live reads instead of reporting a permanently cool
            // device, which would quietly disable every consumer's throttling.
            power = pm
            val app = context.applicationContext
            val l = PowerManager.OnThermalStatusChangedListener { level ->
                lastStatus = level
                val before = tier
                apply(level, System.currentTimeMillis())
                // Logging lives here, not in apply(): android.util.Log is
                // unmocked in unit tests, so apply() must stay callable there.
                if (tier != before) Log.i(TAG, "$before -> $tier (status $level)")
            }
            val registered = runCatching { pm.addThermalStatusListener(app.mainExecutor, l) }.isSuccess
            if (!registered) Log.w(TAG, "thermal listener unavailable; falling back to live reads")
            listener = l
            installed = registered
            val initial = pm.currentThermalStatus
            lastStatus = initial
            apply(initial, System.currentTimeMillis())
        }
    }

    /**
     * Last reported Android thermal status. Falls back to a live read when the
     * listener never registered, so a failed install cannot masquerade as a
     * cool device and switch every consumer's throttling off.
     */
    fun currentStatus(): Int =
        if (installed) lastStatus else power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE

    fun currentTier(): ThermalTier = tier

    /**
     * Always-on capture is the single hottest thing this app does, so it is the
     * first thing to go. Refusing here is cheap: a caller that retries in a
     * loop gets a no-op instead of another microphone session.
     */
    fun allowAlwaysOnMic(): Boolean = tier != ThermalTier.SURVIVAL

    /** Multiplier applied to background/sensor poll intervals. */
    fun pollIntervalScale(): Int = when (tier) {
        ThermalTier.NORMAL -> 1
        ThermalTier.ECONOMY -> 3
        ThermalTier.SURVIVAL -> 6
    }

    /**
     * Multiplier on the speech gate's open/close thresholds. Raising them means
     * ambient noise is less likely to be mistaken for speech and buy decoder
     * time. Never applied to a gate that is already open mid-utterance by the
     * caller, so a quiet talker is not cut off once speaking.
     */
    fun asrGateScale(): Float = if (tier >= ThermalTier.ECONOMY) 2.0f else 1.0f

    /** Decorative avatar work; `AvatarWorkPolicy` already gates on status 2. */
    fun allowDecorativeAnimation(): Boolean = tier == ThermalTier.NORMAL

    internal fun tierFor(status: Int): ThermalTier = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalTier.SURVIVAL
        status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalTier.ECONOMY
        else -> ThermalTier.NORMAL
    }

    /**
     * Pure state transition. Must not touch android.util.Log or any other
     * unmocked framework call: unit tests drive it directly.
     */
    internal fun apply(status: Int, nowMs: Long) {
        val target = tierFor(status)
        val current = tier
        if (target.ordinal > current.ordinal) {
            tier = target
            coolSinceMs = 0L
            return
        }
        if (target.ordinal < current.ordinal) {
            if (coolSinceMs == 0L) {
                coolSinceMs = nowMs
                return
            }
            if (nowMs - coolSinceMs >= RECOVERY_MS) {
                tier = target
                coolSinceMs = 0L
            }
            return
        }
        coolSinceMs = 0L
    }

    /** Drops the system listener. Only meaningful in tests; the app keeps it. */
    internal fun uninstall() {
        synchronized(lock) {
            listener?.let { runCatching { power?.removeThermalStatusListener(it) } }
            listener = null
            power = null
            installed = false
        }
    }

    /** Test seam: returns the governor to a cold state without unregistering. */
    internal fun resetForTest() {
        synchronized(lock) {
            lastStatus = PowerManager.THERMAL_STATUS_NONE
            tier = ThermalTier.NORMAL
            coolSinceMs = 0L
        }
    }
}
