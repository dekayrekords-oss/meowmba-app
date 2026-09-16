package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.access.MeowAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Task approval covers navigation/drafting; social actions need a second check. */
class PhoneGoalTool(private val start: (String) -> Boolean) : Tool {
    override val definition = ToolDefinition(
        id = "phone.run_goal", displayName = "Navigate inside an app",
        description = "Carry out a multi-step phone task by reading accessible controls. Posting, sending, replying and sharing require separate on-screen verification. Payments and deletion stay manual. Requires Meow Accessibility and a planning model.",
        inputSchema = mapOf("goal" to "complete user request, including app and all steps"),
        riskLevel = RiskLevel.MODERATE, confirmationPolicy = ConfirmationPolicy.ALWAYS,
        environments = setOf(ToolEnvironment.PHONE), timeoutMs = 10_000,
    )
    override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.Main) {
        val goal = args["goal"]?.trim()?.takeIf { it.isNotEmpty() && it.length <= 3000 }
            ?: return@withContext ToolResult.Failure("Please give a phone task under 3,000 characters.")
        if (MeowAccessibilityService.instance == null)
            return@withContext ToolResult.Failure("Meow Accessibility is disconnected. In Android Settings → Accessibility → Installed apps, turn Meow off and back on, then return and retry. No app action was performed.")
        if (start(goal)) ToolResult.Success("Started phone navigation. Completion has not been verified; final send or publish stays under your control.")
        else ToolResult.Failure("Phone planner is not ready. Complete Your AI setup first.")
    }
}
