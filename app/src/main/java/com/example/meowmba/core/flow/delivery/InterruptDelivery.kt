package com.example.meowmba.core.flow.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.meowmba.MainActivity
import com.example.meowmba.R
import com.example.meowmba.core.audio.SpeechSynthesizer
import com.example.meowmba.core.flow.Channel
import com.example.meowmba.core.flow.FlowInterrupt
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Says a line aloud. Returns false when speaking isn't allowed right now (user talking, mic owned…). */
fun interface FlowSpeaker { fun speak(text: String): Boolean }

interface FlowNotifier {
    fun post(interrupt: FlowInterrupt)
    fun cancel()
}

data class Delivery(val intentionId: String, val spoke: Boolean, val notified: Boolean)

/**
 * Carries an interruption out through the channel FlowPolicy chose (master plan §7.2).
 * The on-screen band is drawn by MeowFlowHome from the snapshot; this class only speaks
 * and posts the system notification. Each interruption is delivered once.
 */
class InterruptDelivery(private val speaker: FlowSpeaker, private val notifier: FlowNotifier) {
    private var delivered: String? = null

    fun deliver(interrupt: FlowInterrupt?): Delivery? {
        if (interrupt == null) { if (delivered != null) notifier.cancel(); delivered = null; return null }
        val id = interrupt.item.intentionId
        if (id == delivered || id == resolvedId) return null
        delivered = id
        resolvedId = null
        val line = interrupt.spokenLine
        return when (interrupt.channel) {
            Channel.SPEAK_AND_SHOW, Channel.SPEAK_ONLY -> Delivery(id, line != null && speaker.speak(line), false)
            Channel.NOTIFY -> {
                notifier.post(interrupt)
                Delivery(id, line != null && speaker.speak(line), true)
            }
            Channel.SHOW_ONLY, Channel.HOLD -> Delivery(id, false, false)
        }
    }

    /** The user answered or the chance passed: remove the notification. */
    fun resolved(intentionId: String) {
        if (delivered == intentionId) { notifier.cancel(); delivered = null; resolvedId = intentionId }
    }

    /** Remembers the last answered interruption so it isn't re-delivered while the snapshot catches up. */
    private var resolvedId: String? = null
}

/** Adapter over the app's speech engine, gated so Meow never talks over the user. */
class SynthesizerSpeaker(private val synth: SpeechSynthesizer, private val canSpeak: () -> Boolean) : FlowSpeaker {
    override fun speak(text: String): Boolean {
        if (!canSpeak()) return false
        synth.speak(text)
        return true
    }
}

/** Notification actions travel back to the controller through this bus. */
object FlowActionBus {
    data class Action(val intentionId: String, val action: String)
    private val flow = MutableSharedFlow<Action>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val actions: SharedFlow<Action> = flow.asSharedFlow()
    fun emit(a: Action) { flow.tryEmit(a) }
}

/**
 * Receives notification action taps. The integrator registers it in the manifest
 * (exported=false) and collects [FlowActionBus.actions] in the FlowController owner.
 */
class FlowActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AndroidFlowNotifier.EXTRA_INTENTION) ?: return
        val action = intent.getStringExtra(AndroidFlowNotifier.EXTRA_ACTION) ?: return
        if (action != AndroidFlowNotifier.ACTION_PRIMARY && action != AndroidFlowNotifier.ACTION_LATER) return
        val pending = goAsync()
        // The process may have been recreated with no UI/controller collecting the bus.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val dao = com.example.meowmba.core.database.MeowmbaDatabase.getDatabase(context.applicationContext).flowDao()
                val now = System.currentTimeMillis()
                val primary = action == AndroidFlowNotifier.ACTION_PRIMARY
                dao.setStatus(id, if (primary) "DONE" else "SNOOZED", if (primary) null else now + com.example.meowmba.core.flow.FlowController.SNOOZE_MS, now)
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(AndroidFlowNotifier.NOTIFICATION_ID)
            } finally { pending.finish() }
        }
    }
}

/**
 * Heads-up notification when Meow isn't on screen (owner decision D3). Own channel so the
 * user can tune it in Android settings without touching other Meowmba notifications.
 */
class AndroidFlowNotifier(private val context: Context) : FlowNotifier {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Meow Flow interruptions", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Things that can't wait, within the limits you set in Meow Flow"
            },
        )
    }

    override fun post(interrupt: FlowInterrupt) {
        val item = interrupt.item
        val isMessage = item.intentionId.startsWith(com.example.meowmba.core.flow.sources.AppIntentions.MESSAGE)
        val conversation = if (isMessage) com.example.meowmba.core.flow.sources.FlowNotificationHub.activePosted().firstOrNull { e ->
            com.example.meowmba.core.flow.sources.AppIntentions.fromMessage(e.packageName, e.appLabel, e.sender, e.at, e.notificationKey)?.id == item.intentionId
        }?.contentIntent else null
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_FLOW, item.intentionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(item.headline)
            .setContentText(item.because)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.because))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(conversation ?: open)
            .addAction(0, if (isMessage && conversation == null) "Open Meow" else item.primary.label,
                if (isMessage) conversation ?: open else action(item.intentionId, ACTION_PRIMARY, 1))
            .addAction(0, "Later", action(item.intentionId, ACTION_LATER, 2))
            .addAction(0, "Open Meow", open)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try { nm.notify(NOTIFICATION_ID, n) } catch (_: SecurityException) { /* permission revoked */ }
    }

    override fun cancel() = nm.cancel(NOTIFICATION_ID)

    private fun action(intentionId: String, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code,
        Intent(context, FlowActionReceiver::class.java).putExtra(EXTRA_INTENTION, intentionId).putExtra(EXTRA_ACTION, action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "meow_flow_interrupts"
        const val NOTIFICATION_ID = 4201
        const val EXTRA_INTENTION = "meow_flow_intention"
        const val EXTRA_ACTION = "meow_flow_action"
        const val EXTRA_OPEN_FLOW = "meow_flow_open"
        const val ACTION_PRIMARY = "primary"
        const val ACTION_LATER = "later"
    }
}
