package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.memory.MemoryRepository
import com.example.meowmba.core.vault.VaultRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Ask me about anything we ever talked about."
 *
 * Searches the full conversation history (Room messages, FTS), stored
 * memories, and Obsidian-style vault notes, then returns the top snippets
 * so the brain can answer recall questions like "what did we decide about
 * the email we wrote last month?"
 *
 * Read-only, SAFE, runs without confirmation.
 */
class MemoryRecallTool(
    private val memory: MemoryRepository,
    private val vault: VaultRepository? = null,
) : Tool {
    override val definition = ToolDefinition(
        id = "memory.recall",
        displayName = "Recall past conversations",
        description = "Search everything ever discussed: chat history, saved memories, and " +
            "vault notes. Use whenever the user asks about a past conversation, decision, " +
            "email, task, or anything said before. Input: query (what to look for).",
        inputSchema = mapOf("query" to "string"),
        riskLevel = RiskLevel.SAFE,
    )

    override suspend fun execute(args: Map<String, String>): ToolResult {
        val query = args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return ToolResult.Failure("No query provided.")

        return runCatching {
            val messages = memory.searchMessages(query, limit = 8)
            val memories = runCatching { memory.searchMemories(query) }.getOrDefault(emptyList())
            val notes = vault?.search(query)?.take(5) ?: emptyList()

            if (messages.isEmpty() && memories.isEmpty() && notes.isEmpty()) {
                return ToolResult.Success(
                    "I searched my history, memories, and notes for \"$query\" and found nothing. " +
                        "It may never have come up, or try rephrasing the query."
                )
            }

            val out = buildString {
                append("Recall results for \"$query\":\n")
                if (messages.isNotEmpty()) {
                    append("\n— Conversation history —\n")
                    messages.forEach { m ->
                        append("[${TS.format(Date(m.timestamp))}] ${m.role.name.lowercase()}: ")
                        append(snippet(m.content))
                        append("\n")
                    }
                }
                if (memories.isNotEmpty()) {
                    append("\n— Saved memories —\n")
                    memories.take(5).forEach { mem ->
                        append("[${TS.format(Date(mem.timestamp))}] ${snippet(mem.content)}\n")
                    }
                }
                if (notes.isNotEmpty()) {
                    append("\n— Vault notes —\n")
                    notes.forEach { n ->
                        append("**${n.title}** — ${snippet(n.body)}\n")
                    }
                }
            }
            ToolResult.Success(out)
        }.getOrElse { e ->
            ToolResult.Failure("Recall failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun snippet(text: String): String {
        val t = text.replace('\n', ' ').trim()
        return if (t.length <= MAX_SNIPPET) t else t.take(MAX_SNIPPET) + "…"
    }

    companion object {
        private const val MAX_SNIPPET = 300
        private val TS = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }
}
