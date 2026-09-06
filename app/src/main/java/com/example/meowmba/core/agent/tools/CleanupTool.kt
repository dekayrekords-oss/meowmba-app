package com.example.meowmba.core.agent.tools

import android.content.Context
import com.example.meowmba.core.cleanup.CleanupGate
import com.example.meowmba.core.cleanup.PhoneCleanup
import com.example.meowmba.ui.companion.DrawerApp
import com.example.meowmba.ui.companion.DrawerFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `phone.cleanup` — scan, organize, or tidy photos, files, music, videos,
 * messages, and apps. Deletes always go through Android's confirm sheet.
 */
class PhoneCleanupTool(
    private val context: Context,
    private val currentFolders: () -> List<DrawerFolder>,
    private val currentApps: () -> List<DrawerApp>,
    private val saveFolders: (List<DrawerFolder>) -> Unit,
    private val requestMediaPermission: () -> Unit,
) : Tool {
    override val definition = ToolDefinition(
        id = "phone.cleanup",
        displayName = "Clean up the phone",
        description = "Scan or tidy photos, screenshots, videos, music, downloads, " +
            "unused apps, and messages. Args: kind = all|photos|videos|music|" +
            "documents|files|messages|apps, action = scan|organize|tidy. " +
            "Scanning opens all suggested files in a compatible file browser for review. " +
            "Deletes require a system confirmation sheet.",
        inputSchema = mapOf("kind" to "string", "action" to "string"),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE,
        timeoutMs = 45_000,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val kind = (args["kind"] ?: "all").lowercase()
        val action = (args["action"] ?: "scan").lowercase()
        when (action) {
            "organize" -> organize(kind)
            "tidy" -> tidy(kind)
            else -> scan(kind)
        }
    }

    private fun scan(kind: String): ToolResult {
        if (needsMedia(kind) && !PhoneCleanup.hasMediaPermission(context)) {
            requestMediaPermission()
            return ToolResult.Failure(
                "I need access to photos and files to clean those. Allow it, then ask again.",
            )
        }
        val plan = PhoneCleanup.scan(context, kind)
        return ToolResult.Success(PhoneCleanup.format(plan) + "\n" + PhoneCleanup.openSuggestions(context, plan))
    }

    private fun organize(kind: String): ToolResult {
        val bits = mutableListOf<String>()
        if (kind == "all" || kind == "apps") {
            val (next, msg) = PhoneCleanup.organizeApps(currentFolders(), currentApps())
            saveFolders(next)
            bits += msg
        }
        when (kind) {
            "messages" -> {
                runCatching { PhoneCleanup.openMessages(context) }
                bits += "Opened Messages — archive or delete old threads there."
            }
            "documents", "files" -> {
                runCatching { PhoneCleanup.openDownloads(context) }
                bits += "Opened Downloads so you can file PDFs and leftover APKs."
            }
            "photos", "videos", "music" -> {
                val plan = if (PhoneCleanup.hasMediaPermission(context)) {
                    PhoneCleanup.scan(context, kind)
                } else {
                    requestMediaPermission()
                    return ToolResult.Failure(
                        "I need access to $kind to sort them. Allow it, then ask again.",
                    )
                }
                bits += PhoneCleanup.format(plan)
                bits += "Say \"clean them\" to delete the junk with a confirm sheet."
            }
            "all" -> {
                if (PhoneCleanup.hasMediaPermission(context)) {
                    bits += PhoneCleanup.format(PhoneCleanup.scan(context, "all"))
                } else {
                    requestMediaPermission()
                    bits += "Allow photo access and ask me to clean photos next."
                }
            }
        }
        return ToolResult.Success(bits.joinToString("\n").ifBlank { "Nothing to organize." })
    }

    private fun tidy(kind: String): ToolResult {
        val plan = PhoneCleanup.pendingFresh()?.takeIf { kind == "all" || it.kind == kind }
            ?: PhoneCleanup.scan(context, kind)
        if (plan.items.isEmpty()) {
            return ToolResult.Success(PhoneCleanup.format(plan).ifBlank { "Nothing to delete." })
        }
        val uris = PhoneCleanup.deleteUris(plan)
        val apps = PhoneCleanup.unusedPackages(plan)
        val bits = mutableListOf<String>()
        if (uris.isNotEmpty()) {
            CleanupGate.offer(uris)
            bits += "Android will ask you to confirm deleting ${uris.size} file${if (uris.size == 1) "" else "s"}."
        }
        if (apps.isNotEmpty()) {
            val first = apps.first()
            runCatching { PhoneCleanup.uninstall(context, first.first) }
            bits += "Opening uninstall for ${first.second}."
            val rest = apps.drop(1).joinToString(", ") { it.second }
            if (rest.isNotBlank()) bits += "Also unused: $rest."
        }
        if (kind == "messages" || (kind == "all" && plan.notes.any { it.contains("Messages") })) {
            runCatching { PhoneCleanup.openMessages(context) }
            bits += "Opened Messages for old threads."
        }
        if (bits.isEmpty()) {
            return ToolResult.Success("I found things to look at, but nothing I can delete from here.")
        }
        PhoneCleanup.clearPending()
        return ToolResult.Success(bits.joinToString(" "))
    }

    private fun needsMedia(kind: String): Boolean =
        kind in setOf("all", "photos", "videos", "music", "documents", "files")
}
