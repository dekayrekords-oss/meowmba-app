package com.example.meowmba.ui.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun allowAvatarWork(started: Boolean, interactive: Boolean, thermalStatus: Int): Boolean =
    started && interactive && thermalStatus < PowerManager.THERMAL_STATUS_SEVERE

/** Stop decorative work when invisible or thermally throttled. Uses Android's
 * thermal severity rather than inventing a battery-temperature threshold. */
@Composable
internal fun rememberAvatarWorkAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var interactive by remember(power) { mutableStateOf(power.isInteractive) }
    var thermal by remember(power) { mutableIntStateOf(power.currentThermalStatus) }
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
    return allowAvatarWork(started, interactive, thermal)
}
