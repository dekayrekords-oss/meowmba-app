package com.example.meowmba.ui.companion

/** Silence is not proof that a manual Ubuntu command has finished. DELETE /pty
 * kills its shell, so automatic terminal disconnection is legacy-local only. */
internal fun computerAllowsSilenceDisconnect(targetId: String?): Boolean =
    targetId == CompanionTerminalTarget.LOCAL_ID

/** No new timer: only backs off the existing foreground panel poll. Actions and
 * returning to this screen create a fresh policy, immediately refreshing state. */
internal class ComputerPollCadence {
    private var idlePolls = 0
    private var errors = 0

    fun after(status: String?): Long {
        if (status == null) {
            idlePolls = 0
            errors = (errors + 1).coerceAtMost(4)
            return (3_000L shl errors).coerceAtMost(30_000L)
        }
        errors = 0
        if (status != "idle") {
            idlePolls = 0
            return 3_000L
        }
        idlePolls = (idlePolls + 1).coerceAtMost(3)
        return idlePolls * 10_000L
    }
}
