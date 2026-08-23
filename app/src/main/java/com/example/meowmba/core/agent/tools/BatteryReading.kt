package com.example.meowmba.core.agent.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * Phone battery, read from the live system — never a guessed or cached percent.
 *
 * [BatteryManager.BATTERY_PROPERTY_CAPACITY] is 0–100 on modern devices.
 * The sticky [Intent.ACTION_BATTERY_CHANGED] extras are the fallback.
 * [Integer.MIN_VALUE] from getIntProperty means "unsupported", not 0%.
 */
object BatteryReading {

    data class Snapshot(
        val percent: Int?,
        val charging: Boolean,
        val saver: Boolean,
    )

    fun read(context: Context): Snapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val fromProp = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        val sticky = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val fromExtras = if (level >= 0 && scale > 0) {
            ((level * 100) / scale).coerceIn(0, 100)
        } else null
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            (bm?.isCharging == true)
        return Snapshot(
            percent = fromProp ?: fromExtras,
            charging = charging,
            saver = pm?.isPowerSaveMode == true,
        )
    }

    fun spoken(s: Snapshot): String = buildString {
        val p = s.percent
        if (p != null) append("Battery at $p%${if (s.charging) ", charging" else ""}. ")
        else append("Battery level is not available from the system. ")
        append("Battery saver is ${if (s.saver) "on" else "off"}.")
    }

    /** Prompt line. Never invent a percent. */
    fun factLine(s: Snapshot): String {
        val p = s.percent
        return if (p != null) {
            "Phone battery is $p% (${if (s.charging) "charging" else "not charging"})."
        } else {
            "Phone battery percent is unknown — do not invent a number."
        }
    }
}
