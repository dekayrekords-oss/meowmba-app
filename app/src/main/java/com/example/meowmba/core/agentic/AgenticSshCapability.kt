package com.example.meowmba.core.agentic

import java.io.File

/**
 * Clear probe of how agentic SSH can run on this device.
 *
 * Preference order for terminal-clone MVP:
 * 1. ProcessBuilder against Termux / system `ssh` + `sshpass` when executable
 * 2. Optional JSch in-process fallback (no OpenSSH binary)
 * 3. Termux `RUN_COMMAND` intent (documented; requires Termux + allow-external-apps)
 * 4. HTTPS_RUNTIME worker profile (later packaging)
 */
data class AgenticSshCapability(
    val termuxPrefixExists: Boolean,
    val sshBinary: String?,
    val sshpassBinary: String?,
    val keyscanBinary: String?,
    val sshExecutable: Boolean,
    val sshpassExecutable: Boolean,
    val jschAvailable: Boolean,
    val recommendedPath: String,
    val summary: String,
) {
    companion object {
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val TERMUX_BIN = "$TERMUX_PREFIX/bin"

        /** Termux RUN_COMMAND action (documented path; not auto-fired from Lumen loop). */
        const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val TERMUX_PACKAGE = "com.termux"

        fun probe(jschAvailable: Boolean = true): AgenticSshCapability {
            val prefixExists = File(TERMUX_BIN).isDirectory
            val ssh = DefaultAgenticSshExecutor.findSshBinary()
            val sshpass = DefaultAgenticSshExecutor.findSshpassBinary()
            val keyscan = DefaultAgenticSshExecutor.findKeyscanBinary()
            val sshExec = ssh != null && (File(ssh).canExecute() || !ssh.contains('/'))
            val sshpassExec = sshpass != null && (File(sshpass).canExecute() || !sshpass.contains('/'))

            val path = when {
                sshExec && sshpassExec -> "process_builder_termux_or_system"
                jschAvailable -> "jsch_fallback"
                prefixExists -> "termux_run_command"
                else -> "unsupported"
            }
            // Human-readable for Settings UI (avoid jammed tokens).
            val jschLabel = if (jschAvailable) "yes" else "no"
            val termuxLabel = if (prefixExists) "yes" else "no"
            val summary = buildString {
                append("SSH: JSch ").append(jschLabel)
                append(" · Termux ").append(termuxLabel)
                if (sshExec) append(" · ssh binary ready")
                if (prefixExists && !sshExec && !jschAvailable) {
                    append(" · enable Termux allow-external-apps or use JSch")
                }
            }
            return AgenticSshCapability(
                termuxPrefixExists = prefixExists,
                sshBinary = ssh,
                sshpassBinary = sshpass,
                keyscanBinary = keyscan,
                sshExecutable = sshExec,
                sshpassExecutable = sshpassExec,
                jschAvailable = jschAvailable,
                recommendedPath = path,
                summary = summary,
            )
        }
    }
}
