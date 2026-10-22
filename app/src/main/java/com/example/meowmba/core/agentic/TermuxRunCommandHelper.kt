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
 * Preference order (mirrors [AgenticSshCapability] / [LocalLinuxSupport]):
 * 1. Direct [ProcessBuilder] against **Termux** bash/sh when executable
 *    from this UID (best — synchronous stdout/stderr). Operator may then use
 *    proot-distro / `proot -0` for **root-in-guest** and `openssh` /
 *    `openssh-client` to ssh out to meow (network). Guest root ≠ phone root.
 * 2. Termux `com.termux.RUN_COMMAND` Intent → `RunCommandService` when Termux
 *    is installed and **Allow external apps** is enabled (fire-and-forget).
 * 3. Sandbox `/system/bin/sh` with an honest stderr note (no apt).
 * 4. Unsupported — install Termux or use an SSH / HTTPS_RUNTIME profile.
 *
 * App [ConnectionProfile] SSH remains available in parallel with guest ssh.
 * Do **not** invoke from the Lumen frame / avatar loop. No Magisk / SELinux bypass.
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
        val sh = firstExecutable(
            listOf(
                "${AgenticSshCapability.TERMUX_BIN}/bash",
                "${AgenticSshCapability.TERMUX_BIN}/sh",
                "/system/bin/sh",
                "/bin/sh",
            ),
        ) ?: firstExisting(
            listOf(
                "${AgenticSshCapability.TERMUX_BIN}/bash",
                "${AgenticSshCapability.TERMUX_BIN}/sh",
                "/system/bin/sh",
                "/bin/sh",
            ),
        )
        val bash = firstExecutable(listOf("${AgenticSshCapability.TERMUX_BIN}/bash"))
            ?: firstExisting(listOf("${AgenticSshCapability.TERMUX_BIN}/bash"))
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

        // 1) Prefer Termux bash/sh when executable from this UID.
        val termuxShell = firstExecutable(
            listOf(
                "${AgenticSshCapability.TERMUX_BIN}/bash",
                "${AgenticSshCapability.TERMUX_BIN}/sh",
            ),
        )
        if (termuxShell != null) {
            val cwd = workDir ?: "/data/data/com.termux/files/home"
            return runProcessBuilder(termuxShell, listOf("-c", trimmed), timeoutMs, cwd)
        }

        // 2) Termux RUN_COMMAND when installed but binaries not ProcessBuilder-executable.
        if (p.runCommandLikely) {
            val path = p.bashBinary ?: "${AgenticSshCapability.TERMUX_BIN}/bash"
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
                // Fall through to sandbox sh below when Intent was rejected.
            } else {
                return SshExecResult.Ok(
                    exitCode = 0,
                    stdout = "(dispatched via Termux RUN_COMMAND; stdout not captured in-APK)",
                    stderr = "path=termux_run_command executable=$path",
                )
            }
        }

        // 3) Sandbox /system/bin/sh — honest limits (no apt / full Linux).
        val sandbox = firstExecutable(listOf("/system/bin/sh", "/bin/sh"))
        if (sandbox != null) {
            val result = runProcessBuilder(sandbox, listOf("-c", trimmed), timeoutMs, workDir)
            return when (result) {
                is SshExecResult.Ok -> SshExecResult.Ok(
                    exitCode = result.exitCode,
                    stdout = result.stdout,
                    stderr = (result.stderr + "\n[local_shell=sandbox] Install Termux for a real local Linux; sandbox sh cannot do ls/apt").trim(),
                )
                else -> result
            }
        }

        return SshExecResult.Unsupported(
            "No local/Termux shell available. ${p.summary}. " +
                "Install Termux for a real local Linux; sandbox sh cannot do ls/apt. " +
                "Or use an SSH / HTTPS_RUNTIME connection profile for a full machine.",
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

    private fun firstExecutable(candidates: List<String>): String? {
        for (c in candidates) {
            val f = File(c)
            if (f.exists() && f.canExecute()) return c
        }
        return null
    }

    private const val MAX_OUTPUT = 8_192
}
