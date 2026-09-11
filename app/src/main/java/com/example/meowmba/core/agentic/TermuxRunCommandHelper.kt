package com.example.meowmba.core.agentic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Termux integration helper for agentic local / SSH shell paths.
 *
 * Preference order (mirrors [AgenticSshCapability]):
 * 1. Direct [ProcessBuilder] against Termux / system binaries when executable
 *    from this UID (best — synchronous stdout/stderr).
 * 2. Termux `com.termux.RUN_COMMAND` Intent → `RunCommandService` when Termux
 *    is installed and **Allow external apps** is enabled. This path is
 *    fire-and-forget from Meowmba's perspective (Termux runs the command
 *    asynchronously); we still attempt best-effort sync capture via a short
 *    ProcessBuilder `sh -c` when a Termux `sh` binary path is visible.
 * 3. Documented hard limits when neither works.
 *
 * Do **not** invoke from the Lumen frame / avatar loop.
 */
object TermuxRunCommandHelper {
    const val TERMUX_PACKAGE = AgenticSshCapability.TERMUX_PACKAGE
    const val RUN_COMMAND_ACTION = AgenticSshCapability.TERMUX_RUN_COMMAND_ACTION
    const val RUN_COMMAND_SERVICE = AgenticSshCapability.TERMUX_RUN_COMMAND_SERVICE
    const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    data class Probe(
        val termuxInstalled: Boolean,
        val termuxPrefixExists: Boolean,
        val shBinary: String?,
        val bashBinary: String?,
        val sshBinary: String?,
        val sshpassBinary: String?,
        val processBuilderUsable: Boolean,
        val runCommandLikely: Boolean,
        val summary: String,
    )

    fun probe(context: Context): Probe {
        val pm = context.packageManager
        val installed = try {
            pm.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val prefix = File(AgenticSshCapability.TERMUX_BIN).isDirectory
        val sh = firstExisting(
            listOf(
                "${AgenticSshCapability.TERMUX_BIN}/bash",
                "${AgenticSshCapability.TERMUX_BIN}/sh",
                "/system/bin/sh",
                "/bin/sh",
            ),
        )
        val bash = firstExisting(listOf("${AgenticSshCapability.TERMUX_BIN}/bash"))
        val ssh = DefaultAgenticSshExecutor.findSshBinary()
        val sshpass = DefaultAgenticSshExecutor.findSshpassBinary()
        val pbOk = sh != null && File(sh).canExecute()
        val runLikely = installed && prefix
        val summary = buildString {
            append("termuxInstalled=").append(installed)
            append(" prefix=").append(prefix)
            append(" sh=").append(sh ?: "none")
            append(" processBuilder=").append(pbOk)
            append(" runCommandLikely=").append(runLikely)
            if (installed && !pbOk) {
                append(" hint=enable Termux allow-external-apps; prefer ProcessBuilder when binaries executable")
            }
        }
        return Probe(
            termuxInstalled = installed,
            termuxPrefixExists = prefix,
            shBinary = sh,
            bashBinary = bash,
            sshBinary = ssh,
            sshpassBinary = sshpass,
            processBuilderUsable = pbOk,
            runCommandLikely = runLikely,
            summary = summary,
        )
    }

    /**
     * Run `sh -c <script>` via the best available path.
     * Returns stdout+stderr truncated; never includes secrets.
     */
    fun execSh(
        context: Context,
        script: String,
        timeoutMs: Long = 30_000L,
        workDir: String? = null,
    ): SshExecResult {
        val trimmed = script.trim()
        if (trimmed.isEmpty()) return SshExecResult.Error("empty script")
        val p = probe(context)

        // 1) Direct ProcessBuilder when shell is executable.
        if (p.processBuilderUsable && p.shBinary != null) {
            return runProcessBuilder(p.shBinary, listOf("-c", trimmed), timeoutMs, workDir)
        }

        // 2) Fire Termux RUN_COMMAND Intent (async) + best-effort note.
        if (p.runCommandLikely) {
            val path = p.bashBinary ?: p.shBinary ?: "${AgenticSshCapability.TERMUX_BIN}/bash"
            val sent = runCatching {
                dispatchRunCommand(
                    context = context,
                    executablePath = path,
                    arguments = arrayOf("-c", trimmed),
                    workDir = workDir ?: "/data/data/com.termux/files/home",
                    background = true,
                )
            }.getOrElse { e ->
                return SshExecResult.Error(
                    "Termux RUN_COMMAND failed: ${e.message ?: e.javaClass.simpleName}. " +
                        "Enable Allow external apps in Termux.",
                )
            }
            if (!sent) {
                return SshExecResult.Unsupported(
                    "Termux installed but RUN_COMMAND Intent was not accepted. " +
                        "Enable allow-external-apps in ~/.termux/termux.properties and grant " +
                        "Meowmba permission to start Termux services. Probe: ${p.summary}",
                )
            }
            // Intent path cannot capture stdout into the APK process reliably
            // without Termux:API / PendingIntent result plumbing (not shipped).
            return SshExecResult.Ok(
                exitCode = 0,
                stdout = "(dispatched via Termux RUN_COMMAND; stdout not captured in-APK)",
                stderr = "path=termux_run_command executable=$path",
            )
        }

        return SshExecResult.Unsupported(
            "No local/Termux shell available. ${p.summary}. " +
                "Install Termux + openssh/sshpass, enable allow-external-apps, " +
                "or use an SSH / HTTPS_RUNTIME connection profile.",
        )
    }

    /**
     * Dispatch Termux RUN_COMMAND Intent. Returns true if startService /
     * startForegroundService did not throw.
     */
    fun dispatchRunCommand(
        context: Context,
        executablePath: String,
        arguments: Array<String>,
        workDir: String? = null,
        background: Boolean = true,
    ): Boolean {
        val intent = Intent().apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            action = RUN_COMMAND_ACTION
            putExtra(EXTRA_COMMAND_PATH, executablePath)
            putExtra(EXTRA_ARGUMENTS, arguments)
            if (!workDir.isNullOrBlank()) putExtra(EXTRA_WORKDIR, workDir)
            putExtra(EXTRA_BACKGROUND, background)
            // 0 = don't switch to Termux UI
            putExtra(EXTRA_SESSION_ACTION, 0)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.applicationContext.startService(intent)
            true
        } catch (_: IllegalStateException) {
            // Background start restrictions — try foreground service API.
            try {
                context.applicationContext.startForegroundService(intent)
                true
            } catch (_: Exception) {
                // Last resort: send broadcast-style start via explicit package.
                try {
                    context.applicationContext.startService(intent)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun runProcessBuilder(
        executable: String,
        argv: List<String>,
        timeoutMs: Long,
        workDir: String? = null,
    ): SshExecResult {
        return try {
            val cmd = listOf(executable) + argv
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(false)
            if (!workDir.isNullOrBlank()) {
                val dir = File(workDir)
                if (dir.isDirectory) pb.directory(dir)
            }
            val proc = pb.start()
            val finished = proc.waitFor(timeoutMs.coerceAtLeast(1_000L), TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return SshExecResult.Error("local shell timed out after ${timeoutMs}ms")
            }
            val stdout = proc.inputStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT)
            val stderr = proc.errorStream.bufferedReader().use { it.readText() }.take(MAX_OUTPUT)
            SshExecResult.Ok(exitCode = proc.exitValue(), stdout = stdout, stderr = stderr)
        } catch (e: Exception) {
            SshExecResult.Error("local shell failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun firstExisting(candidates: List<String>): String? {
        for (c in candidates) {
            val f = File(c)
            if (f.exists()) return c
        }
        return null
    }

    private const val MAX_OUTPUT = 8_192
}
