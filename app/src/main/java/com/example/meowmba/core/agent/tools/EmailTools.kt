package com.example.meowmba.core.agent.tools

import android.content.Context
import android.content.Intent

/**
 * `email.send` — opens the user's default email app with a pre-filled
 * compose window (to, subject, body). The user hits Send; the tool does
 * not send automatically (no email API is installed on this device).
 *
 * SAFE risk, no confirmation (the OS email app shows its own send UI).
 */
class EmailSendTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        id = "email.send",
        displayName = "Send email",
        description = "Opens the default email app with the given recipient, subject, " +
            "and body pre-filled. The user must tap Send — this tool cannot send " +
            "automatically on this device.",
        inputSchema = mapOf(
            "to" to "string",
            "subject" to "string",
            "body" to "string"
        ),
        riskLevel = RiskLevel.SAFE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val to = args["to"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'to'")
        val subject = args["subject"] ?: ""
        val body = args["body"] ?: ""

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:$to")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return ToolResult.Success("Email composer opened for $to with subject \"$subject\".")
        } else {
            return ToolResult.Failure("No email app found on this device.")
        }
    }
}

/**
 * `email.draft` — saves an email draft as a text file in the workspace.
 * The user can open the file later to review and send.
 */
class EmailDraftTool(private val workspace: Workspace) : Tool {
    override val definition = ToolDefinition(
        id = "email.draft",
        displayName = "Draft email",
        description = "Saves an email draft as a text file in the workspace. " +
            "The user can review and send it later.",
        inputSchema = mapOf(
            "to" to "string",
            "subject" to "string",
            "body" to "string"
        ),
        riskLevel = RiskLevel.MODERATE,
        environments = setOf(ToolEnvironment.PHONE),
        confirmationPolicy = ConfirmationPolicy.NONE
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val to = args["to"]?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'to'")
        val subject = args["subject"]?.takeIf { it.isNotBlank() } ?: "(no subject)"
        val body = args["body"] ?: ""

        val safeName = subject.replace(Regex("[^a-zA-Z0-9_\\- ]"), "").take(40).trim()
            .replace(Regex("\\s+"), "_")
        val fileName = if (safeName.isNotBlank()) "draft_${safeName}.eml" else "draft_${System.currentTimeMillis()}.eml"
        val content = """To: $to
Subject: $subject

$body"""

        return try {
            workspace.resolve(fileName)?.let { file ->
                file.writeText(content)
                ToolResult.Success("Draft saved as \"$fileName\" in the workspace.")
            } ?: ToolResult.Failure("Could not write draft — path is outside the workspace.")
        } catch (e: Exception) {
            ToolResult.Failure("Failed to save draft: ${e.message}")
        }
    }
}
