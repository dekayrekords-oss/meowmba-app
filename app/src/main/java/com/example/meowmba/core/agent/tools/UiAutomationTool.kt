package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.access.MeowAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `ui.automate` — full phone control via the opt-in accessibility service.
 * Confirmation is NONE (approval is at the loop/task level, not per-action).
 * When the service is off, the tool fails honestly with instructions.
 */
class UiAutomationTool : Tool {
    override val definition = ToolDefinition(
        id = "ui.automate",
        displayName = "UI automation",
        description = "Operate the current screen: tap(text), tap_index(n), type(field, text), " +
            "scroll(up|down), swipe(direction), long_press(text), back, home, recents, " +
            "notifications, read, read_compact. Requires the Meow accessibility service.",
        inputSchema = mapOf(
            "action" to "tap|tap_index|type|scroll|swipe|long_press|back|home|recents|notifications|read|read_compact",
            "text" to "element text (tap, long_press)",
            "index" to "1-based element index (tap_index)",
            "field" to "field label (type)",
            "value" to "text to type (type)",
            "direction" to "up|down|left|right (scroll, swipe)"
        ),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.NONE
    )

    private fun disabled() = ToolResult.Failure(
        "UI automation is off. Enable it in Settings → Meow Assistant → Accessibility, then ask me again."
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val service = MeowAccessibilityService.instance ?: return@withContext disabled()
        val globalActions = setOf("back", "home", "recents", "notifications")
        if (args["action"]?.trim()?.lowercase() !in globalActions && service.getActivePackage() == "unknown") {
            return@withContext ToolResult.Failure("No readable screen. Reconnect Meow Accessibility; some apps block screen inspection.")
        }
        when (val action = args["action"]?.trim()?.lowercase()) {
            "tap" -> args["text"]?.takeIf { it.isNotBlank() }
                ?.let { actionResult(service.tapByText(it)) }
                ?: ToolResult.Failure("Missing 'text' for tap")
            "type" -> {
                val field = args["field"]?.takeIf { it.isNotBlank() }
                    ?: return@withContext ToolResult.Failure("Missing 'field' for type")
                val value = args["value"] ?: ""
                actionResult(service.typeIntoText(field, value))
            }
            "scroll" -> actionResult(service.scroll(args["direction"]?.lowercase() != "up"))
            "read" -> ToolResult.Success("On screen: " + service.readScreen())
            "tap_index" -> args["index"]?.toIntOrNull()
                ?.let { actionResult(service.tapByIndex(it)) }
                ?: ToolResult.Failure("Missing or invalid 'index' for tap_index")
            "long_press" -> args["text"]?.takeIf { it.isNotBlank() }
                ?.let { actionResult(service.longPress(it)) }
                ?: ToolResult.Failure("Missing 'text' for long_press")
            "swipe" -> args["direction"]?.takeIf { it.isNotBlank() }
                ?.let { actionResult(service.swipe(it)) }
                ?: ToolResult.Failure("Missing 'direction' for swipe")
            "back" -> actionResult(service.globalAction("back"))
            "home" -> actionResult(service.globalAction("home"))
            "recents" -> actionResult(service.globalAction("recents"))
            "notifications" -> actionResult(service.globalAction("notifications"))
            "read_compact" -> ToolResult.Success(service.readScreenCompact())
            else -> ToolResult.Failure(
                "Unknown action '" + (action ?: "") + "'. Use: tap, tap_index, type, scroll, swipe, long_press, back, home, recents, notifications, read, read_compact."
            )
        }
    }

    companion object {
        /** Failure messages from Android actions must not become successful tool results. */
        internal fun actionResult(message: String): ToolResult =
            if (listOf("Tapped ", "Typed ", "Scrolled ", "Swiped ", "Long-pressed ", "Performed ").any { message.startsWith(it) })
                ToolResult.Success(message) else ToolResult.Failure(message)
    }
}
