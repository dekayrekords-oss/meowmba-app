package com.example.meowmba.core.agent.tools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phone control tools.
 *
 * The companion could already touch files, email, calendar, and a paired
 * desktop — but had no way to act on the phone it actually lives on, so it
 * correctly told the user it couldn't open apps. These close that gap.
 *
 * Scope is deliberately narrow: launch an app, list what's installed, open a
 * URL, and open a system settings page. No silent messaging, no dialing, no
 * permission-granting — those either can't be done without user action on
 * modern Android or shouldn't be done without a visible confirmation.
 */

/** Shared app-lookup used by launch and list (and the background wake service). */
internal object InstalledApps {

    data class Entry(val label: String, val packageName: String)

    fun all(context: Context): List<Entry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(launcherIntent, 0)
        return resolved
            .map { Entry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Match a spoken app name against installed labels. Speech recognition
     * mangles names ("you tube", "insta gram"), so match on squashed text
     * rather than requiring an exact label.
     */
    fun find(context: Context, query: String): Entry? = match(all(context), query)

    /**
     * Words a speaker puts around an app name that are never part of it.
     *
     * "open the clock" used to fail outright: squashing produced "theclock",
     * which is not equal to, a prefix of, or a substring of "clock", so every
     * rule missed and the user got 'No installed app matches "the clock"'.
     */
    private val FILLER = setOf(
        "the", "a", "an", "my", "please", "app", "application", "up", "some",
    )

    /**
     * Pure matcher over a supplied app list, so the rules are unit-testable
     * without a PackageManager.
     *
     * Order matters and is least-surprising-first: an exact label wins over a
     * prefix, a prefix over a substring, and only then do we accept the app
     * label appearing *inside* the spoken phrase. That last rule is what makes
     * "open the clock app for me" work, and it prefers the LONGEST matching
     * label so "play music" cannot be won by an app called "Play".
     */
    fun match(apps: List<Entry>, query: String): Entry? {
        val words = query.lowercase()
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
        // Strip filler from both ends but never from the middle: "play store"
        // must keep both words, while "the clock app" reduces to "clock".
        val core = words.dropWhile { it in FILLER }.dropLastWhile { it in FILLER }
        val target = core.joinToString("").ifEmpty { squash(query) }
        if (target.isEmpty()) return null

        apps.filter { squash(it.label) == target }.let { if (it.isNotEmpty()) return it.singleOrNull() }
        // ASR aliases only resolve to an app actually installed on this phone.
        val alias = mapOf("factbook" to "facebook", "facebrook" to "facebook", "whatsup" to "whatsapp", "snapshat" to "snapchat")[target]
        if (alias != null) return apps.filter { squash(it.label) == alias }.singleOrNull()
        if (target.length < 3) return null
        apps.filter { squash(it.label).startsWith(target) }.let { if (it.isNotEmpty()) return it.singleOrNull() }
        apps.filter { squash(it.label).contains(target) }.let { if (it.isNotEmpty()) return it.singleOrNull() }
        // The spoken phrase contains a label: "set my alarm in the clock" → Clock.
        apps.filter { squash(it.label).length >= 3 && target.contains(squash(it.label)) }
            .maxByOrNull { squash(it.label).length }
            ?.let { return it }
        apps.filter { squash(it.packageName).contains(target) }.singleOrNull()?.let { return it }
        // Conservative spelling correction: only a clear, unique winner. Short
        // names and close ties require clarification rather than opening a random app.
        if (target.length !in 5..40) return null
        val ranked = apps.map { it to distance(target, squash(it.label)) }.sortedBy { it.second }
        val best = ranked.firstOrNull() ?: return null
        val limit = if (target.length >= 8) 2 else 1
        return best.first.takeIf { best.second <= limit && (ranked.getOrNull(1)?.second ?: 99) >= best.second + 2 }
    }

    private fun distance(a: String, b: String): Int {
        var row = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val next = IntArray(b.length + 1); next[0] = i + 1
            for (j in b.indices) next[j + 1] = minOf(next[j] + 1, row[j + 1] + 1, row[j] + if (a[i] == b[j]) 0 else 1)
            row = next
        }
        return row[b.length]
    }

    private fun squash(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }
}

/**
 * `phone.launch_app` — opens an installed app by name.
 *
 * MODERATE risk: visible, reversible, and the user asked for it out loud.
 * No approval card, or every "open Spotify" would need a second tap.
 */
class LaunchAppTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.launch_app",
        displayName = "Open an app",
        description = "Open an installed app on this phone by name. " +
            "Example: \"open Spotify\", \"launch Gmail\".",
        inputSchema = mapOf("name" to "string", "purpose" to "optional: inbox"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val name = args["name"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'name' — which app should I open?")

        val match = InstalledApps.find(context, name)
            ?: return@withContext ToolResult.Failure(
                "I couldn't confidently match \"$name\" to one installed app. Please say its full name, or ask me to list your apps."
            )

        val launch = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return@withContext ToolResult.Failure("${match.label} has no launcher screen to open.")

        return@withContext try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            withContext(Dispatchers.Main.immediate) {
                context.startActivity(launch)
            }
            ToolResult.Success("Opened ${match.label}." +
                if (args["purpose"] == "inbox") " I haven't read any messages. The inbox is open for you to review." else "")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open ${match.label}: ${e.message}")
        }
    }
}

/**
 * `phone.play_music` — start playback of a song, artist, playlist, podcast or
 * album by search query.
 *
 * Uses Android's standard [MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH]
 * intent, which Spotify, YouTube Music and other audio apps honor, so the
 * user does not have to open the app first. "Play sublime" plays in the
 * default music app (Spotify when installed); "play sublime on YouTube Music"
 * routes to the named app instead.
 *
 * MODERATE risk: it changes what is audible, but that is exactly what the user
 * asked for out loud and it is trivially reversible (pause/stop).
 */
class PlayMusicTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.play_music",
        displayName = "Play music",
        description = "Play a song, artist, album, playlist, or podcast by name. " +
            "Optionally say which app to play it in; otherwise the user's music app " +
            "(usually Spotify) is used. Examples: \"play sublime\", " +
            "\"play sublime on Spotify\", \"play lo-fi beats on YouTube Music\". " +
            "Use this to start playback directly — do NOT open the app first.",
        inputSchema = mapOf("query" to "string", "app" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val query = args["query"]?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'query' — what should I play?")
        val appName = args["app"]?.takeIf { it.isNotBlank() }

        // Resolve the target audio app. A named app is looked up among installed
        // apps; otherwise prefer Spotify when present, and fall back to an implicit
        // intent so the user's default music handler picks it up.
        val targetPackage: String? = when {
            appName != null -> InstalledApps.find(context, appName)?.packageName
                ?: return@withContext ToolResult.Failure(
                    "No installed app matches \"$appName\". Ask me to list your apps."
                )
            InstalledApps.find(context, "spotify") != null ->
                InstalledApps.find(context, "spotify")!!.packageName
            else -> null
        }

        val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetPackage?.let { setPackage(it) }
        }
        val appLabel = appName ?: targetPackage?.let { "the music app" }
        return@withContext try {
            context.startActivity(play)
            ToolResult.Success("Playing \"$query\"${appLabel?.let { " in $it" } ?: ""}.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't start playback of \"$query\": ${e.message}")
        }
    }
}

/** `phone.list_apps` — read-only inventory of launchable apps. */
class ListAppsTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.list_apps",
        displayName = "List installed apps",
        description = "List the apps installed on this phone that can be opened.",
        inputSchema = mapOf("filter" to "string"),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val filter = args["filter"]?.takeIf { it.isNotBlank() }?.lowercase()
        val apps = InstalledApps.all(context)
            .let { list -> if (filter == null) list else list.filter { it.label.lowercase().contains(filter) } }

        if (apps.isEmpty()) {
            return@withContext ToolResult.Success(
                if (filter == null) "No launchable apps found." else "No installed app matches \"$filter\"."
            )
        }

        // Cap the spoken/displayed list — reading 200 app names aloud helps nobody.
        val shown = apps.take(40).joinToString(", ") { it.label }
        val suffix = if (apps.size > 40) " …and ${apps.size - 40} more." else ""
        return@withContext ToolResult.Success("${apps.size} apps installed: $shown$suffix")
    }
}

/**
 * `phone.open_url` — opens a link in the browser.
 *
 * MODERATE and unconfirmed: opening a page is visible and reversible, and the
 * URL comes from the user's own request.
 */
class OpenUrlTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.open_url",
        displayName = "Open a link",
        description = "Open a web address in the phone's browser.",
        inputSchema = mapOf("url" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val raw = args["url"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult.Failure("Missing 'url'.")
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"

        return@withContext try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("Opened $url.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open $url: ${e.message}")
        }
    }
}

/**
 * `phone.open_settings` — opens a system settings screen.
 *
 * The app can't flip system toggles itself (Android forbids it), so this takes
 * the user to the right page instead of pretending to change anything.
 */
class OpenSettingsTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.open_settings",
        displayName = "Open phone settings",
        description = "Open a system settings page, such as wifi, bluetooth, display, " +
            "sound, battery, or apps. Android does not let this app change system " +
            "settings directly, so it opens the right page for you.",
        inputSchema = mapOf("page" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 10_000
    )

    private val pages = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "wi-fi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "brightness" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "volume" to Settings.ACTION_SOUND_SETTINGS,
        "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "date" to Settings.ACTION_DATE_SETTINGS,
        "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val page = args["page"]?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val action = if (page == null) {
            Settings.ACTION_SETTINGS
        } else {
            pages.entries.firstOrNull { page.contains(it.key) }?.value
                ?: return@withContext ToolResult.Failure(
                    "I don't know a settings page called \"$page\". Try: ${pages.keys.take(8).joinToString(", ")}."
                )
        }

        return@withContext try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult.Success(if (page == null) "Opened Settings." else "Opened $page settings.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open that settings page: ${e.message}")
        }
    }
}


// ────────────────────────────────────────────────────────────────────────
// Phase F2: phone system tools — alarms, timers, flashlight, DND, battery.
// ────────────────────────────────────────────────────────────────────────

/** `phone.set_alarm` — uses the system Clock via AlarmClock intents. */
class SetAlarmTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.set_alarm",
        displayName = "Set an alarm",
        description = "Set an alarm in the system clock. Args: hour (0-23), minutes (0-59), optional label.",
        inputSchema = mapOf("hour" to "0-23", "minutes" to "0-59", "label" to "optional message"),
        riskLevel = RiskLevel.SAFE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val hour = args["hour"]?.toIntOrNull()
        val minutes = args["minutes"]?.toIntOrNull() ?: 0
        if (hour == null || hour !in 0..23 || minutes !in 0..59) {
            return@withContext ToolResult.Failure("I need a valid hour (0-23) and minutes (0-59).")
        }
        try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, args["label"] ?: "Meow alarm")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ToolResult.Success("Alarm set for ${hour}:${minutes.toString().padStart(2, '0')}.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't set the alarm: ${e.message}")
        }
    }
}

/** `phone.set_timer` — countdown timer in the system clock. */
class SetTimerTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.set_timer",
        displayName = "Set a timer",
        description = "Start a countdown timer. Args: minutes (or seconds).",
        inputSchema = mapOf("minutes" to "timer length in minutes", "seconds" to "timer length in seconds"),
        riskLevel = RiskLevel.SAFE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val seconds = args["seconds"]?.toIntOrNull()
            ?: args["minutes"]?.toIntOrNull()?.let { it * 60 }
        if (seconds == null || seconds !in 1..24 * 3600) {
            return@withContext ToolResult.Failure("Tell me the timer length in minutes or seconds.")
        }
        try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "Meow timer")
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ToolResult.Success("Timer started: ${seconds / 60} min ${seconds % 60} s.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't start the timer: ${e.message}")
        }
    }
}

/** `phone.flashlight` — rear-camera torch toggle. */
class FlashlightTool(context: Context) : Tool {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchOn = false

    override val definition = ToolDefinition(
        id = "phone.flashlight",
        displayName = "Flashlight",
        description = "Turn the flashlight on or off. Args: state = on|off|toggle.",
        inputSchema = mapOf("state" to "on|off|toggle"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val manager = cameraManager ?: return@withContext ToolResult.Failure("No camera service on this device.")
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return@withContext ToolResult.Failure("This phone has no flashlight.")
        try {
            val target = when (args["state"]?.trim()?.lowercase()) {
                "on" -> true
                "off" -> false
                else -> !torchOn
            }
            manager.setTorchMode(cameraId, target)
            torchOn = target
            ToolResult.Success("Flashlight ${if (target) "on" else "off"}.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't toggle the flashlight: ${e.message}")
        }
    }
}

/** `phone.dnd` — Do Not Disturb. Needs notification-policy access once. */
class DndTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.dnd",
        displayName = "Do Not Disturb",
        description = "Turn Do Not Disturb on or off. Args: state = on|off.",
        inputSchema = mapOf("state" to "on|off"),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return@withContext ToolResult.Failure("No notification service on this device.")
        if (!nm.isNotificationPolicyAccessGranted) {
            return@withContext try {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                ToolResult.Failure("I need DND permission — I opened the settings page; allow Meow, then ask me again.")
            } catch (e: Exception) {
                ToolResult.Failure("I need DND permission: Settings → Apps → Special access → Do Not Disturb access.")
            }
        }
        val on = args["state"]?.trim()?.lowercase() != "off"
        try {
            nm.setInterruptionFilter(
                if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ToolResult.Success("Do Not Disturb ${if (on) "on" else "off"}.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't change DND: ${e.message}")
        }
    }
}

/** `phone.battery` — battery level and saver readout (read-only). */
class BatteryStatusTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.battery",
        displayName = "Battery status",
        description = "Report the phone's battery level, charging state, and whether battery saver is on.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        try {
            ToolResult.Success(BatteryReading.spoken(BatteryReading.read(context)))
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't read battery status: ${e.message}")
        }
    }
}

// ────────────────────────────────────────────────────────────────────────
// Phase F4: assistant-class phone tools — volume, camera, messaging, calls,
// media transport, brightness.
// ────────────────────────────────────────────────────────────────────────

/** Resolves "mom" → "+1 555…" via the contacts provider, or passes through digits. */
private fun lookupPhoneNumber(context: Context, raw: String): String {
    val candidate = raw.trim()
    if (candidate.isEmpty()) return ""
    if (candidate.any { it.isDigit() }) {
        val digits = candidate.filter { it.isDigit() }
        if (digits.length >= 3) return candidate
    }
    // It's a name — query the contact book once.
    return try {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val nameLike = "%${candidate.replace("%", "")}%"
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf(nameLike),
            null
        )?.use { c: Cursor ->
            if (c.moveToFirst()) c.getString(0) ?: "" else ""
        } ?: ""
    } catch (_: Exception) {
        ""
    }
}

/** `phone.set_volume` — media volume as a percentage, or up/down/mute/unmute. */
class SetVolumeTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.set_volume",
        displayName = "Set media volume",
        description = "Set the media volume to a percentage, or nudge it. " +
            "Args: level 0-100 for an exact value, or action = up|down|mute|unmute.",
        inputSchema = mapOf("level" to "0-100", "action" to "up|down|mute|unmute"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return@withContext ToolResult.Failure("No audio service on this device.")
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        try {
            args["level"]?.toIntOrNull()?.let { pct ->
                val target = (pct.coerceIn(0, 100) * max / 100).coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                return@withContext ToolResult.Success("Media volume at $pct%.")
            }
            when (args["action"]?.lowercase()) {
                "up" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                    ToolResult.Success("Volume up.")
                }
                "down" -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                    ToolResult.Success("Volume down.")
                }
                "mute" -> {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    ToolResult.Success("Muted.")
                }
                "unmute" -> {
                    // The mute path sets the index to 0 rather than using the
                    // mute flag, so a plain ADJUST_UNMUTE can land back on
                    // silence — raise one step when it does.
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                    }
                    ToolResult.Success("Unmuted.")
                }
                else -> ToolResult.Failure("Tell me a level (0-100) or up, down, or mute.")
            }
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't change the volume: ${e.message}")
        }
    }
}

/** `phone.take_photo` — opens the camera app to take a photo. */
class TakePhotoTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.take_photo",
        displayName = "Take a photo",
        description = "Open the phone's camera so a photo can be taken.",
        inputSchema = emptyMap(),
        riskLevel = RiskLevel.MODERATE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return@withContext try {
            context.startActivity(intent)
            ToolResult.Success("Camera is open — say cheese.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open the camera: ${e.message}")
        }
    }
}

/** `phone.compose_sms` — opens the messaging app with a number and draft ready. */
class ComposeSmsTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.compose_sms",
        displayName = "Text someone",
        description = "Open the messaging app with a recipient and message pre-filled. " +
            "Args: to = contact name or phone number, body = the message. The user presses send.",
        inputSchema = mapOf("to" to "string", "body" to "string"),
        riskLevel = RiskLevel.MODERATE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val recipient = lookupPhoneNumber(context, args["to"] ?: "")
        if (recipient.isEmpty()) return@withContext ToolResult.Failure("Who should I text? Tell me a name or number.")
        val sms = Uri.fromParts("sms", recipient, null)
        val intent = Intent(Intent.ACTION_SENDTO, sms)
            .putExtra("sms_body", args["body"] ?: "")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return@withContext try {
            context.startActivity(intent)
            ToolResult.Success("Opened a text to $recipient — ready when you are.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open the messaging app: ${e.message}")
        }
    }
}

/** `phone.dial_call` — opens the dialer with a number (user presses call). */
class DialCallTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.dial_call",
        displayName = "Call someone",
        description = "Open the dialer with a contact's number ready — the user presses call. " +
            "Args: to = contact name or phone number.",
        inputSchema = mapOf("to" to "string"),
        riskLevel = RiskLevel.MODERATE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val number = lookupPhoneNumber(context, args["to"] ?: "")
        if (number.isEmpty()) return@withContext ToolResult.Failure("Who should I call? Tell me a name or number.")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return@withContext try {
            context.startActivity(intent)
            ToolResult.Success("Dialer ready for $number.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open the dialer: ${e.message}")
        }
    }
}

/** `phone.media_control` — play/pause/skip on the active media session. */
class MediaControlTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.media_control",
        displayName = "Control music",
        description = "Play, pause, resume, or skip to the next or previous track on the " +
            "current media app. Args: action = play|pause|next|previous.",
        inputSchema = mapOf("action" to "play|pause|next|previous"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val action = args["action"]?.trim()?.lowercase()
        if (action !in setOf("play", "pause", "next", "previous")) {
            return@withContext ToolResult.Failure("I need play, pause, next, or previous.")
        }
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val sessions = msm?.getActiveSessions(null)?.toList() ?: emptyList()
            val controller = sessions.firstOrNull { s ->
                s.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull()
                ?: return@withContext ToolResult.Failure("Nothing is playing right now.")
            val controls = controller.transportControls
            when (action) {
                "play" -> controls.play()
                "pause" -> controls.pause()
                "next" -> controls.skipToNext()
                "previous" -> controls.skipToPrevious()
            }
            ToolResult.Success(
                when (action) {
                    "play" -> "Playing."
                    "pause" -> "Paused."
                    "next" -> "Skipped to the next track."
                    else -> "Back to the previous track."
                }
            )
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't control playback: ${e.message}")
        }
    }
}

/** `phone.set_brightness` — screen brightness percent; needs one-time write-settings grant. */
class SetBrightnessTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "phone.set_brightness",
        displayName = "Set screen brightness",
        description = "Set the screen brightness to a percentage. Requires the user to " +
            "grant one-time write-settings permission the first time.",
        inputSchema = mapOf("level" to "0-100"),
        riskLevel = RiskLevel.SAFE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val level = args["level"]?.toIntOrNull() ?: return@withContext ToolResult.Failure("Tell me a brightness level 0-100.")
        if (level !in 0..100) return@withContext ToolResult.Failure("Brightness goes from 0 to 100.")
        try {
            if (!Settings.System.canWrite(context)) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return@withContext ToolResult.Failure(
                    "I need one-time permission to change the screen — I opened it; allow Meow, then ask again."
                )
            }
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            ToolResult.Success("Screen brightness at $level%.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't change the brightness: ${e.message}")
        }
    }
}
