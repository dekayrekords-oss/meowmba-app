package com.example.meowmba.core.agentic

/**
 * Classifies SSH argv and gates side-effecting exec behind approval.
 *
 * Read-only probes (uname/ls/…) may run without approval. Anything else —
 * including shell metacharacters — is treated as side-effecting when
 * [requireApprovalForSideEffects] is true (default for production scaffolding).
 */
object AgenticSshPolicy {
    @Volatile
    var requireApprovalForSideEffects: Boolean = true

    private val READ_ONLY_FIRST = setOf(
        "uname", "ls", "df", "free", "uptime", "whoami", "hostname", "pwd",
        "cat", "head", "tail", "echo", "date", "id", "which", "printenv", "env",
        "stat", "file", "wc", "true", "false",
    )

    private val META = charArrayOf(';', '|', '&', '`', '$', '>', '<', '(', ')', '\n')

    fun isSideEffecting(argv: List<String>): Boolean {
        if (argv.isEmpty()) return true
        if (argv.any { part -> part.any { it in META } }) return true
        val first = argv.first().trim().lowercase().substringAfterLast('/')
        return first !in READ_ONLY_FIRST
    }

    fun requiresApproval(argv: List<String>): Boolean =
        requireApprovalForSideEffects && isSideEffecting(argv)
}
