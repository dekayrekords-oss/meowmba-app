package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.access.MeowAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `ui.automate` — find/tap/type/scroll/read on the current screen via the
 * opt-in accessibility service (Phase F3). Every call goes through the
 * approval card (MODERATE + ALWAYS); when the service is off, the tool
 * fails honestly and tells the user how to enable it.
 */
class UiAutomationTool : Tool {
    override val definition = ToolDefinition(
        id = "ui.automate",
        displayName = "UI automation",
        description = "Operate the current screen: tap(text), type(field, text), " +
            "scroll(up|down), read (summarize what's on screen). Requires the Meow " +
            "accessibility service to be enabled.",
        inputSchema = mapOf(
            "action" to "tap|type|scroll|read",
            "text" to "element text (tap)",
            "field" to "field label (type)",
            "value" to "text to type (type)",
            "direction" to "up|down (scroll)"
        ),
        riskLevel = RiskLevel.MODERATE,
        confirmationPolicy = ConfirmationPolicy.ALWAYS
    )

    private fun disabled() = ToolResult.Failure(
        "UI automation is off. Enable it in Settings → Meow Assistant → Accessibility, then ask me again."
    )

    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val service = MeowAccessibilityService.instance ?: return@withContext disabled()
        when (val action = args["action"]?.trim()?.lowercase()) {
            "tap" -> args["text"]?.takeIf { it.isNotBlank() }
                ?.let { ToolResult.Success(service.tapByText(it)) }
                ?: ToolResult.Failure("Missing 'text' for tap")
            "type" -> {
                val field = args["field"]?.takeIf { it.isNotBlank() }
                    ?: return@withContext ToolResult.Failure("Missing 'field' for type")
                val value = args["value"] ?: ""
                ToolResult.Success(service.typeIntoText(field, value))
            }
            "scroll" -> ToolResult.Success(service.scroll(args["direction"]?.lowercase() != "up"))
            "read" -> ToolResult.Success("On screen: ${service.readScreen()}")
            else -> ToolResult.Failure(
                "Unknown action '${action ?: ""}'. Use: tap, type, scroll, read."
            )
        }
    }
}
