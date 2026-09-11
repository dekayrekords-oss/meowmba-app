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

/** How long Android thermal must stay at MODERATE+ before we show a user-facing warning. */
internal const val THERMAL_UI_HOLD_MS = 10_000L

/**
 * User-visible thermal messaging. Work throttling may still kick in at MODERATE
 * immediately; this ladder is only for banners/chips so brief spikes and LIGHT
 * never look like "overheating".
 */
internal enum class ThermalUiWarning {
    /** NONE / LIGHT, or MODERATE that has not held long enough. */
    NONE,
    /** Sustained MODERATE — mild copy only. */
    MILD,
    /** Sustained SEVERE+ — stronger but still not "overheating". */
    HOT,
}

/**
 * Pure decision for UI warnings. Escalation onto [ThermalUiWarning.MILD]/[ThermalUiWarning.HOT]
 * waits [THERMAL_UI_HOLD_MS] of continuous MODERATE+; once showing, severity
 * tracks the live status until it drops below MODERATE (clears immediately).
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
    if (thermalStatus < PowerManager.THERMAL_STATUS_MODERATE) {
        return ThermalUiWarning.NONE to 0L
    }
    val since = if (elevatedSinceMs == 0L) nowMs else elevatedSinceMs
    val heldLongEnough = previouslyShowing || (nowMs - since >= holdMs)
    if (!heldLongEnough) {
        return ThermalUiWarning.NONE to since
    }
    val level = if (thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
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
    started && interactive && thermalStatus < PowerManager.THERMAL_STATUS_MODERATE

/** Stop decorative work when invisible or thermally throttled. Uses Android's
 * thermal severity rather than inventing a battery-temperature threshold. */
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
            if (thermal < PowerManager.THERMAL_STATUS_MODERATE || next != ThermalUiWarning.NONE) {
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
