package com.example.meowmba.core.context.live

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import com.example.meowmba.core.audio.InputSoundLevel
import com.example.meowmba.core.database.MeowmbaDatabase

/**
 * The context stamped on every journal row: where, doing what, what was around, what was next.
 * App-level facts (weather, next calendar event, last app before Meow) come from [providers],
 * which the companion sets at start; everything else is read here, cheaply, at stamp time.
 */
object ContextStamp {
    class Providers(
        val weather: () -> String? = { null },
        val nextEvent: () -> String? = { null },
        val recentApp: () -> String? = { null },
        /** The car's Bluetooth address or name, as saved in Car settings. */
        val carDeviceName: () -> String? = { null },
    )

    @Volatile var providers: Providers = Providers()
    @Volatile private var appContext: Context? = null

    fun init(context: Context, providers: Providers) {
        appContext = context.applicationContext ?: context
        this.providers = providers
    }

    fun event(kind: LifeEventKind, text: String, at: Long = System.currentTimeMillis()): LifeEventEntity {
        val c = LiveContext.now.value
        val ctx = appContext
        // A fix older than 30 min is not "where you were" any more.
        val fresh = c.hasFix && at - c.fixAt < 30 * 60_000L
        return LifeEventEntity(
            at = at, kind = kind.name, text = text.take(2_000),
            lat = c.lat.takeIf { fresh }, lon = c.lon.takeIf { fresh }, accuracyM = c.accuracyM.takeIf { fresh },
            place = c.place.takeIf { fresh },
            activity = c.activity.name,
            speedMph = c.speedMph.takeIf { fresh && c.activity == com.example.meowmba.core.flow.Activity.DRIVING },
            noise = LiveContext.noise(InputSoundLevel.monotonicNowMs()),
            app = runCatching { providers.recentApp() }.getOrNull(),
            charging = ctx?.let(::charging) ?: c.charging,
            audio = ctx?.let(::audioRoute) ?: c.audio,
            weather = runCatching { providers.weather() }.getOrNull(),
            nextEvent = runCatching { providers.nextEvent() }.getOrNull(),
        )
    }

    /** Journal a sentence the user said or typed, with the full stamp. Never throws. */
    suspend fun said(text: String) {
        val ctx = appContext ?: return
        val clean = text.trim().takeIf { it.length >= 2 } ?: return
        runCatching { MeowmbaDatabase.getDatabase(ctx).lifeJournalDao().insert(event(LifeEventKind.SAID, clean)) }
    }

    private fun charging(ctx: Context): Boolean {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    /** "car" when the configured car (or a car-named device) is the audio output, "headphones" for others. */
    private fun audioRoute(ctx: Context): String? {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return null
        val car = runCatching { providers.carDeviceName() }.getOrNull()?.lowercase()
        for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                    val name = d.productName?.toString()?.lowercase().orEmpty()
                    val isCar = car != null && (d.address.equals(car, ignoreCase = true) || (name.isNotEmpty() && name in car))
                    return if (isCar || CAR_WORDS.containsMatchIn(name)) "car" else "headphones"
                }
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> return "headphones"
            }
        }
        return null
    }

    private val CAR_WORDS = Regex("(?i)\\b(car|auto|sync|uconnect|carplay|android auto|toyota|honda|ford|chevy|tesla|bmw|audi|kia|hyundai|nissan|subaru|mazda|jeep|ram|gmc|lexus|acura|volvo|vw|volkswagen)\\b")
}
