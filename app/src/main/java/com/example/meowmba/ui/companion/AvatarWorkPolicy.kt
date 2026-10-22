package com.example.meowmba.ui.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** How long Android thermal must stay at [THERMAL_EASE_AT]+ before we show a user-facing warning. */
internal const val THERMAL_UI_HOLD_MS = 10_000L

/**
 * First Android thermal status where decorative motion eases and the "Cooling animations" notice
 * may show. Raised one step (was MODERATE) so ordinary warm-phone use is not throttled or nagged.
 */
internal const val THERMAL_EASE_AT = PowerManager.THERMAL_STATUS_SEVERE

/**
 * First status where decorative work stops and the notice says the phone is hot. Raised one step
 * (was SEVERE). Camera and microphone protection stays with ThermalGovernor and is not changed here.
 */
internal const val THERMAL_STOP_AT = PowerManager.THERMAL_STATUS_CRITICAL

/**
 * User-visible thermal messaging. Work throttling may still kick in at [THERMAL_EASE_AT]
 * immediately; this ladder is only for banners/chips so brief spikes and anything cooler
 * never look like "overheating".
 */
internal enum class ThermalUiWarning {
    /** Below [THERMAL_EASE_AT], or a spike that has not held long enough. */
    NONE,
    /** Sustained [THERMAL_EASE_AT] — mild copy only. */
    MILD,
    /** Sustained [THERMAL_STOP_AT]+ — stronger but still not "overheating". */
    HOT,
}

/**
 * Pure decision for UI warnings. Escalation onto [ThermalUiWarning.MILD]/[ThermalUiWarning.HOT]
 * waits [THERMAL_UI_HOLD_MS] of continuous [THERMAL_EASE_AT]+; once showing, severity
 * tracks the live status until it drops below [THERMAL_EASE_AT] (clears immediately).
 *
 * @return warning level and the elevated-since timestamp to persist (0 when cool).
 */
internal fun resolveThermalUiWarning(
    thermalStatus: Int,
    elevatedSinceMs: Long,
    nowMs: Long,
    previouslyShowing: Boolean,
    holdMs: Long = THERMAL_UI_HOLD_MS,
): Pair<ThermalUiWarning, Long> {
    if (thermalStatus < THERMAL_EASE_AT) {
        return ThermalUiWarning.NONE to 0L
    }
    val since = if (elevatedSinceMs == 0L) nowMs else elevatedSinceMs
    val heldLongEnough = previouslyShowing || (nowMs - since >= holdMs)
    if (!heldLongEnough) {
        return ThermalUiWarning.NONE to since
    }
    val level = if (thermalStatus >= THERMAL_STOP_AT) {
        ThermalUiWarning.HOT
    } else {
        ThermalUiWarning.MILD
    }
    return level to since
}

internal fun thermalUiBannerText(warning: ThermalUiWarning): String? = when (warning) {
    ThermalUiWarning.NONE -> null
    ThermalUiWarning.MILD -> "Cooling animations — camera and motion ease until the device settles."
    ThermalUiWarning.HOT -> "Phone is hot — animations paused until it cools. They resume automatically."
}

internal fun allowAvatarWork(started: Boolean, interactive: Boolean, thermalStatus: Int): Boolean =
    started && interactive && thermalStatus < THERMAL_STOP_AT

/** The notice stays hidden while the user has swiped away this level; a worse level shows again. */
internal fun thermalBannerVisible(warning: ThermalUiWarning, dismissed: ThermalUiWarning?): Boolean =
    warning != ThermalUiWarning.NONE && warning != dismissed

/** Stop decorative work when invisible or thermally throttled. Uses Android's
 * thermal severity rather than inventing a battery-temperature threshold.
 * [THERMAL_EASE_AT] is a sparse-cadence tier; [THERMAL_STOP_AT] stops decorative work. */
@Composable
internal fun rememberAvatarWorkAllowed(): Boolean = rememberAvatarWorkStatus().allowed

internal data class AvatarWorkStatus(
    val allowed: Boolean,
    /** True when a user-facing thermal banner should show (hysteresis applied). */
    val cooling: Boolean,
    val warning: ThermalUiWarning = ThermalUiWarning.NONE,
)

@Composable
internal fun rememberAvatarWorkStatus(): AvatarWorkStatus {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var interactive by remember(power) { mutableStateOf(power.isInteractive) }
    var thermal by remember(power) { mutableIntStateOf(power.currentThermalStatus) }
    var elevatedSinceMs by remember { mutableLongStateOf(0L) }
    var warning by remember { mutableStateOf(ThermalUiWarning.NONE) }

    DisposableEffect(context, lifecycle, power) {
        val observer = LifecycleEventObserver { _, _ ->
            started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            interactive = power.isInteractive
            thermal = power.currentThermalStatus
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { interactive = power.isInteractive }
        }
        val listener = PowerManager.OnThermalStatusChangedListener { thermal = it }
        lifecycle.addObserver(observer)
        // System-only screen broadcasts, compatible with minSdk 31.
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        power.addThermalStatusListener(context.mainExecutor, listener)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
            power.removeThermalStatusListener(listener)
        }
    }

    // Re-evaluate when status changes; if still warming up to the hold, wait out the remainder.
    LaunchedEffect(thermal) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val (next, since) = resolveThermalUiWarning(
                thermalStatus = thermal,
                elevatedSinceMs = elevatedSinceMs,
                nowMs = now,
                previouslyShowing = warning != ThermalUiWarning.NONE,
            )
            elevatedSinceMs = since
            warning = next
            if (thermal < THERMAL_EASE_AT || next != ThermalUiWarning.NONE) {
                break
            }
            val wait = (THERMAL_UI_HOLD_MS - (now - since)).coerceAtLeast(50L)
            delay(wait)
        }
    }

    return AvatarWorkStatus(
        allowed = allowAvatarWork(started, interactive, thermal),
        cooling = warning != ThermalUiWarning.NONE,
        warning = warning,
    )
}
