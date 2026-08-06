package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.notification.NotificationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `notification.list` — returns recent unread notifications.
 * `notification.summary` — returns a grouped summary of unread counts by app.
 */
class NotificationListTool : Tool {
    override val definition = ToolDefinition(
        id = "notification.list",
        displayName = "List notifications",
        description = "List recent unread device notifications with app, title, and text.",
        riskLevel = RiskLevel.SAFE,
        confirmationPolicy = ConfirmationPolicy.NONE,
        inputSchema = mapOf("count" to "max number of notifications to return (default 10)")
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Default) {
        val count = args["count"]?.toIntOrNull() ?: 10
        val unread = NotificationStore.getUnread().takeLast(count)
        if (unread.isEmpty()) {
            return@withContext ToolResult.Success("No unread notifications.")
        }
        val sb = StringBuilder()
        sb.append("${unread.size} unread notification(s):\n\n")
        unread.forEach { n ->
            sb.append("[${n.appName}] ${n.title}")
            if (n.text.isNotBlank()) sb.append(": ${n.text.take(120)}")
            sb.append("\n")
        }
        NotificationStore.markAllRead()
        ToolResult.Success(sb.toString())
    }
}

class NotificationSummaryTool : Tool {
    override val definition = ToolDefinition(
        id = "notification.summary",
        displayName = "Notification summary",
        description = "Summarize unread notification counts grouped by app.",
        riskLevel = RiskLevel.SAFE,
        confirmationPolicy = ConfirmationPolicy.NONE,
        inputSchema = emptyMap()
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Default) {
        val summary = NotificationStore.unreadSummary()
        if (summary.isEmpty()) {
            return@withContext ToolResult.Success("No unread notifications.")
        }
        val sb = StringBuilder()
        sb.append("Unread notifications:\n")
        summary.entries.sortedByDescending { it.value }.forEach { (app, count) ->
            sb.append("  $app: $count\n")
        }
        sb.append("\nTotal: ${NotificationStore.unreadCount}")
        ToolResult.Success(sb.toString())
    }
}
