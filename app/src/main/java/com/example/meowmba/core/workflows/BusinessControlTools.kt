package com.example.meowmba.core.workflows

import com.example.meowmba.core.agent.tools.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chat/voice use the same persistent jobs as the team UI. Enqueueing is not
 * reported as accomplishing the user's goal. No model-created agents or grants. */
object BusinessControlTools {
    fun register(registry: ToolRegistry, runtime: () -> WorkflowRuntime, open: () -> Unit) {
        fun tool(id: String, label: String, schema: Map<String, String> = emptyMap(), confirm: Boolean = false, action: (Map<String, String>) -> String) {
            registry.register(object : Tool {
                override val definition = ToolDefinition(id, label, label, schema, riskLevel = if (confirm) RiskLevel.MODERATE else RiskLevel.SAFE,
                    confirmationPolicy = if (confirm) ConfirmationPolicy.ALWAYS else ConfirmationPolicy.NONE)
                override suspend fun execute(args: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
                    try { ToolResult.Success(action(args)) } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { ToolResult.Failure(e.message ?: "Business agent operation failed") }
                }
            })
        }
        tool("business.agents", "List user-created business agents and their actual IDs") { _ ->
            runtime().store.state.value.agents.joinToString("\n") { "${it.id}: ${it.name} (${if (it.enabled) "enabled" else "disabled"}, ${it.provider})" }
                .ifBlank { "No business agents yet. Open Agents → Create & run business agents." }
        }
        tool("business.start", "Queue a goal for a saved business agent. Use its exact ID or unique name from business.agents; this queues work, not a completed result.", mapOf("agent" to "string", "goal" to "string"), confirm = true) { args ->
            val rt = runtime()
            val query = args["agent"].orEmpty()
            val matches = rt.store.state.value.agents.filter { it.id == query || it.name.equals(query, ignoreCase = true) }
            check(matches.size == 1) { "Name does not identify exactly one agent. Use business.agents first." }
            val id = rt.enqueue(matches.single().id, args["goal"].orEmpty())
            "Job queued, not completed: $id. Check business.jobs or open Your agent team for results and approvals."
        }
        tool("business.jobs", "Read actual saved business-job statuses and result evidence") { _ ->
            runtime().store.state.value.runs.takeLast(8).asReversed().joinToString("\n\n") { "${it.id}: ${it.status}\n${it.goal}\n${it.note.take(1000)}" }.ifBlank { "No business jobs have run." }
        }
        tool("business.open", "Open your business agent team, saved jobs and approvals") { _ -> open(); "Opened your business agent team, saved jobs and approvals." }
    }
}
