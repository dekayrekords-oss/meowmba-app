package com.example.meowmba.core.agent.tools

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.PowerManager
import android.provider.AlarmClock
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

/** Shared app-lookup used by launch and list. */
private object InstalledApps {

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
    fun find(context: Context, query: String): Entry? {
        val target = squash(query)
        if (target.isEmpty()) return null
        val apps = all(context)
        return apps.firstOrNull { squash(it.label) == target }
            ?: apps.firstOrNull { squash(it.label).startsWith(target) }
            ?: apps.firstOrNull { squash(it.label).contains(target) }
            ?: apps.firstOrNull { squash(it.packageName).contains(target) }
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
        inputSchema = mapOf("name" to "string"),
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
                "No installed app matches \"$name\". Ask me to list your apps to see what's there."
            )

        val launch = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return@withContext ToolResult.Failure("${match.label} has no launcher screen to open.")

        return@withContext try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            ToolResult.Success("Opened ${match.label}.")
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't open ${match.label}: ${e.message}")
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
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val saver = pm?.isPowerSaveMode == true
            val batteryIntent = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            ToolResult.Success(buildString {
                if (pct >= 0) append("Battery at $pct%${if (charging) ", charging" else ""}. ")
                append("Battery saver is ${if (saver) "on" else "off"}.")
            })
        } catch (e: Exception) {
            ToolResult.Failure("Couldn't read battery status: ${e.message}")
        }
    }
}
