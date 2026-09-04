package com.example.meowmba.core.plugins

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.meowmba.core.access.MeowAccessibilityService
import com.example.meowmba.core.agent.tools.InstalledApps
import com.example.meowmba.core.audio.TextToSpeechManager
import com.example.meowmba.core.notification.NotificationHelper
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

/**
 * Turns a user-created plugin/skill into a REAL on-device action, chosen from
 * the skill's name and the input you pass it.
 *
 * Previously a skill only echoed its input back ("Plugin ran with: …"). Now a
 * skill can actually do something useful: start a countdown timer that chimes,
 * schedule a one-off reminder, open an installed app, show a toast, read the
 * current screen, or improvise a line. The behavior is inferred from keywords
 * in the skill's own name + the requested input, so "Focus Timer" naturally
 * becomes a timer and "Open Spotify" becomes an app launch — no per-skill code.
 *
 * Everything here is pure on-device and reversible (SAFE). It deliberately does
 * not shell out and does not need any credential.
 */
class SkillRunner(private val context: Context) {

    private companion object {
        private val timers = AtomicLong(0)
    }

    /**
     * Run the skill. `input` is whatever the model/user passed (e.g. "12 minutes").
     * Returns the text the assistant should relay to the user.
     */
    fun run(skillName: String, input: String): String {
        val name = skillName.lowercase().trim()
        val text = input.trim().ifEmpty { name }

        return when {
            name.contains("timer") || name.contains("countdown") ->
                startTimer(text)

            name.contains("remind") || name.contains("reminder") || name.contains("alarm") ->
                scheduleReminder(text)

            name.contains("open ") || name.contains("launch ") || name.contains("start ") ->
                openApp(text)

            name.contains("toast") || name.contains("announce") || name.contains("say") ->
                toast(text)

            name.contains("read") || name.contains("screen") -> readScreen()

            name.contains("joke") || name.contains("fact") || name.contains("quote") ->
                improvise(text)

            else -> helpfulFallback(text)
        }
    }

    private fun startTimer(text: String): String {
        val seconds = parseDurationSeconds(text)
        if (seconds <= 0) {
            return "I wasn't sure how long to set that timer for. Try something like \"set it for 5 minutes\"."
        }
        speak("Starting a ${formatDuration(seconds)} timer now.")
        val remaining = AtomicLong(seconds * 1000L)
        val id = timers.incrementAndGet()
        val handler = Handler(Looper.getMainLooper())
        lateinit var ticker: Runnable
        ticker = object : Runnable {
            override fun run() {
                val left = remaining.get()
                if (left <= 0) {
                    postChime("Timer done!", "Your ${formatDuration(seconds)} timer is finished.")
                    capMessage("Timer done — ${formatDuration(seconds)} is up.")
                    return
                }
                remaining.set(left - 1000)
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(ticker, 1000)
        // Keep `handler` referenced so a single timer never gets GC'd mid-flight.
        return "Timer set for ${formatDuration(seconds)}. I'll let you know when it's up."
    }

    private fun scheduleReminder(text: String): String {
        val (atSeconds, note) = parseReminder(text)
        if (atSeconds <= 0) {
            return "Tell me when to remind you, like \"remind me in 10 minutes\" or \"at 3pm\"."
        }
        val whenLong = System.currentTimeMillis() + atSeconds * 1000L
        val hh = Calendar.getInstance().apply { timeInMillis = whenLong }
            .let { String.format("%02d:%02d", it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }
        Handler(Looper.getMainLooper()).postDelayed({
            postChime("Reminder", note.ifBlank { "Time's up!" })
            speak(note.ifBlank { "Here's your reminder." })
        }, atSeconds * 1000L)
        return "Reminder set for $hh: ${note.ifBlank { "your reminder" }}."
    }

    private fun openApp(text: String): String {
        // Strip the leading verb so "open Spotify" / "start Spotify" work.
        val query = text
            .replace(Regex("(?i)^(open|launch|start)\\s+"), "")
            .trim()
        val match = InstalledApps.find(context, query)
            ?: return "I couldn't find an installed app matching \"$query\"."
        val launch = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return "${match.label} has no launcher screen to open."
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
            .getOrElse { return "Couldn't open ${match.label}." }
        return "Opened ${match.label}."
    }

    private fun toast(text: String): String {
        val msg = text.replace(Regex("(?i)^(toast|announce|say)\\s*[,:]?\\s*"), "").trim()
            .ifEmpty { "Meow says hi!" }
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        return "Done."
    }

    private fun readScreen(): String {
        return if (MeowAccessibilityService.isEnabled()) {
            val screen = MeowAccessibilityService.instance?.readScreen()
                ?.trim().orEmpty()
            "Here's what's on screen: ${screen.take(1200)}"
        } else {
            "Screen reading needs the accessibility service. Turn it on in Settings → Meow Assistant → Accessibility and ask me again."
        }
    }

    private fun improvise(text: String): String {
        val topic = text
            .replace(Regex("(?i)^(tell me|give me|a|an)\\s+"), "")
            .replace(Regex("(?i)\\s*(a|an)?\\s*(joke|fact|quote)$"), "")
            .trim()
        return when {
            text.contains("joke") -> {
                speak("Here's one.")
                "Why did the cat sit on the computer? To keep an eye on the mouse!"
            }
            text.contains("fact") -> "Cats spend about two-thirds of the day sleeping — very on-brand."
            text.contains("quote") -> "\"The ideal companion is one that meows.\" Rest assured, I'm on the job."
            else -> "Here's something: ${topic.ifEmpty { "you asked me to improvise" }} — and I always keep it interesting."
        }
    }

    private fun helpfulFallback(text: String): String {
        return "Running \"$text\" — that skill doesn't map to a built-in action yet, but I'm standing by. " +
            "Name a skill after what it should do (timer, reminder, open an app, toast, read screen) and it'll actually work."
    }

    private fun postChime(title: String, body: String) {
        NotificationHelper(context).postThought("$title — $body")
    }

    private fun speak(text: String) {
        runCatching { TextToSpeechManager(context).speak(text) }
    }

    private fun capMessage(text: String) {
        // Reflect the completion on-screen if we can; notification already covers it.
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
    }

    // ── parsing ────────────────────────────────────────────────────────────

    private fun parseDurationSeconds(text: String): Long = SkillParsing.parseDurationSeconds(text)

    /** "in 10 minutes" or "at 3:30pm" → (secondsFromNow, note). */
    private fun parseReminder(text: String): Pair<Long, String> = SkillParsing.parseReminder(text)

    private fun formatDuration(seconds: Long): String = SkillParsing.formatDuration(seconds)
}

/** Pure, Android-free duration/reminder parsing — unit-tested directly. */
internal object SkillParsing {

    fun parseDurationSeconds(text: String): Long {
        val m = Regex("(\\d{1,4})\\s*(minutes|minute|mins|min|hours|hour|hrs|hr|seconds|second|secs|sec)\\b")
            .find(text.lowercase())
            ?: return -1
        val n = m.groupValues[1].toLongOrNull() ?: return -1
        return when (m.groupValues[2]) {
            "hour", "hr", "hours", "hrs" -> n * 3600
            "sec", "secs", "second", "seconds" -> n
            else -> n * 60
        }
    }

    /** "in 10 minutes" or "at 3:30pm" → (secondsFromNow, note). */
    fun parseReminder(text: String): Pair<Long, String> {
        val title = text.replace(Regex("(?i)^remind(er)?\\s*(me)?\\s*"), "").trim()
            .replace(Regex("(?i)\\s*(to|about)\\s*$"), "").trim()

        Regex("(?i)in\\s+(\\d{1,4})\\s*(minutes|minute|mins|min|hours|hour|hrs|hr|seconds|second|secs|sec)\\b").find(title)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return -1L to title
            val secs = when (m.groupValues[2]) {
                "sec", "secs", "second", "seconds" -> n
                "hour", "hr", "hours", "hrs" -> n * 3600
                else -> n * 60
            }
            val note = title.removeRange(m.range).trim()
            return secs to note
        }

        Regex("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(title)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return -1L to title
            val mi = m.groupValues[2].toIntOrNull() ?: 0
            val ap = m.groupValues[3].lowercase()
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            val now = Calendar.getInstance()
            var target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_MONTH, 1)
            val secs = (target.timeInMillis - now.timeInMillis) / 1000
            val note = title.removeRange(m.range).trim()
            return secs to note
        }

        return -1L to title
    }

    fun formatDuration(seconds: Long): String {
        if (seconds >= 3600) {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            return if (m > 0) "$h hour${if (h != 1L) "s" else ""} $m minute${if (m != 1L) "s" else ""}" else "$h hour${if (h != 1L) "s" else ""}"
        }
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m > 0 && s > 0 -> "$m minute${if (m != 1L) "s" else ""} $s second${if (s != 1L) "s" else ""}"
            m > 0 -> "$m minute${if (m != 1L) "s" else ""}"
            else -> "$s second${if (s != 1L) "s" else ""}"
        }
    }
}
