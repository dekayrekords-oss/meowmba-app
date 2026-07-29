package com.example.meowmba.core.agent.tools

import android.content.Context
import android.content.Intent
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
