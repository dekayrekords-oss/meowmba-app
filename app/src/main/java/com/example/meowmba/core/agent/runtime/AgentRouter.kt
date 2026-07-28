package com.example.meowmba.core.agent.runtime

import com.example.meowmba.core.audio.WakePhraseMatcher

/**
 * Routes user text to an agent intent.
 *
 * Phase 5 uses an honest keyword router: what the companion can actually do
 * is a small, explicit set — anything else falls through to normal
 * conversation. When the model provider drives tool-calling later, this
 * router is replaced by structured tool calls; the orchestrator behind it
 * does not change.
 *
 * Pure Kotlin, fully unit-testable.
 */
object AgentRouter {

    data class AgentIntent(
        val agentId: String,
        val toolId: String,
        val args: Map<String, String>,
        val title: String
    )

    fun route(text: String): AgentIntent? {
        val raw = text.trim()
        val normalized = WakePhraseMatcher.normalize(raw)

        // Exact-phrase commands (normalized for tolerance)
        if (normalized in setOf("list files", "list the files", "show files", "show me the files",
                "what files do i have", "show workspace", "list workspace")) {
            return AgentIntent("file", "file.list", emptyMap(), "list workspace files")
        }
        if (normalized in setOf("list tasks", "show tasks", "what tasks are running",
                "what are you working on", "show agent tasks")) {
            return AgentIntent("main", "task.list", emptyMap(), "list agent tasks")
        }

        // Prefix commands: match case-insensitively, but extract arguments
        // from the RAW text so filenames keep their punctuation ("notes.txt")
        // and projects keep their capitalization ("Website").
        extractAfter(raw, listOf("create a new file called ", "create a new file named ",
            "create new file called ", "create a file called ", "create a file named ",
            "create file called ", "create file named ", "create file "))?.let { name ->
            return AgentIntent("file", "file.write", mapOf("path" to name, "content" to ""), "create file $name")
        }

        extractAfter(raw, listOf("delete the file ", "delete file "))?.let { name ->
            return AgentIntent("file", "file.delete", mapOf("path" to name), "delete file $name")
        }

        extractAfter(raw, listOf("read the file ", "read file ", "open the file ", "open file "))?.let { name ->
            return AgentIntent("file", "file.read", mapOf("path" to name), "read file $name")
        }

        extractAfter(raw, listOf("create a new project called ", "create a new project named ",
            "create new project called ", "create a project called ", "create a project named ",
            "create project called ", "create project named ", "create a new project ",
            "create new project ", "create a project ", "create project "))?.let { name ->
            return AgentIntent("project", "project.create", mapOf("name" to name), "create project $name")
        }

        extractAfter(raw, listOf("search my memory for ", "search memory for ",
            "search memories for "))?.let { query ->
            return AgentIntent("memory", "memory.search", mapOf("query" to query), "search memory: $query")
        }

        return null
    }

    /** Case-insensitive prefix match; returns the RAW remainder (punctuation intact). */
    private fun extractAfter(raw: String, prefixes: List<String>): String? {
        val lowered = raw.lowercase()
        for (prefix in prefixes) {
            if (lowered.startsWith(prefix)) {
                return raw.substring(prefix.length).trim().ifBlank { null }
            }
        }
        return null
    }
}
