package com.example.meowmba.core.agentic

/**
 * Bridge from [AgenticRuntime] `computer_exec` into the companion Computer
 * terminal (wake guest, stdin / one-shot shell, capture output).
 *
 * Implemented by CompanionViewModel. No Android root. No FlyWire.
 * Never pass secrets through [command] / [ComputerExecResult.output].
 */
fun interface ComputerExecHost {
    fun computerExec(command: String, timeoutMs: Long, approvalGranted: Boolean): ComputerExecResult
}

data class ComputerExecResult(
    val ok: Boolean,
    val output: String,
    val exitCode: Int? = null,
    /** When true, runtime should move the goal to WAITING_APPROVAL. */
    val needsApproval: Boolean = false,
)

/**
 * Computer guest cmds auto-run by default. Only clearly destructive patterns
 * need a simple confirm (goal WAITING_APPROVAL). Keep remote ssh policy separate.
 */
object ComputerGuestPolicy {
    private val DESTRUCTIVE = Regex(
        """(?i)(\brm\s+(-[^\s]*f[^\s]*\s+|--force\b).*|""" +
            """\brm\s+-rf\b|\brm\s+-fr\b|\bmkfs\b|\bdd\s+if=|""" +
            """\bchmod\s+-R\s+777\b|\bchown\s+-R\b)""",
    )

    fun requiresConfirm(command: String): Boolean {
        val c = command.trim()
        if (c.isEmpty()) return false
        return DESTRUCTIVE.containsMatchIn(c)
    }

    fun requiresConfirm(argv: List<String>): Boolean {
        if (argv.isEmpty()) return false
        if (argv.size >= 3 && argv[0] == "sh" && argv[1] == "-c") {
            return requiresConfirm(argv.drop(2).joinToString(" "))
        }
        return requiresConfirm(argv.joinToString(" "))
    }
}
