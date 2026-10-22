package com.example.meowmba.core.computer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Fixed provisioning only. No AI-supplied commands and no sandbox-shell fallback. */
object PhoneComputerSetup {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    /** Result code the receiver reports when Termux refused the command for allow-external-apps. */
    internal const val EXIT_EXTERNAL_APPS_DISABLED = -2

    /**
     * Paste into a Termux prompt (not inside Ubuntu). Termux has no settings screen for this:
     * it is a line in ~/.termux/termux.properties, applied by termux-reload-settings.
     */
    const val ALLOW_EXTERNAL_APPS_COMMAND =
        "mkdir -p ~/.termux && f=~/.termux/termux.properties && touch \"\$f\" && " +
            "sed -i '/^[[:space:]]*#*[[:space:]]*allow-external-apps/d' \"\$f\" && " +
            "echo 'allow-external-apps=true' >> \"\$f\" && termux-reload-settings && echo 'Meow: external apps allowed'"

    /** True when Termux's error text says external apps are not allowed; the text itself is never kept. */
    internal fun isExternalAppsError(errmsg: String?): Boolean =
        errmsg != null && errmsg.contains("allow-external-apps", ignoreCase = true)
    class PermissionRequired : IllegalStateException("Allow Meow to run commands in Termux, then retry setup.")
    /** Termux refused RUN_COMMAND because `allow-external-apps` is not true in ~/.termux/termux.properties. */
    class ExternalAppsRequired : IllegalStateException("Termux is blocking commands from other apps. Turn on allow-external-apps in Termux, then retry setup.")
    class GuidedSetupRequired : IllegalStateException("This Termux edition needs guided setup. Use Copy setup & open Termux; there is no command permission to grant.")
    private val lock = Mutex()
    private val ids = AtomicInteger(1000)
    internal val results = ConcurrentHashMap<Int, CompletableDeferred<Int>>()

    internal fun scriptForAddress(source: String, address: String): String {
        require(address == OpenCodeLocalConnection.ORIGINAL_ADDRESS || address == OpenCodeLocalConnection.RECOVERY_ADDRESS)
        return if (address == OpenCodeLocalConnection.RECOVERY_ADDRESS)
            source.replace("4096", "4097").replace("setup-server.log", "setup-server-4097.log") else source
    }

    /** Fixed installer only; clipboard contains no password, token or user-supplied shell text.
     * /dev/tty keeps the password out of shell history and hides it while typing. */
    internal fun guidedScript(source: String, address: String): String {
        val script = scriptForAddress(source, address)
        val read = "IFS= read -r meow_password"
        check(script.lines().count { it == read } == 1)
        val prompt = """
            printf '%s\n' 'Meow guided setup: preserves existing Ubuntu and other servers.'
            trap 'meow_exit=${'$'}?; if [ "${'$'}meow_exit" -eq 22 ]; then printf "%s\n" "That local port is already in use. No server was stopped. Return to Meow to verify or choose a separate connection."; elif [ "${'$'}meow_exit" -ne 0 ]; then printf "Setup stopped (code %s). Existing Ubuntu files were kept.\n" "${'$'}meow_exit"; fi' EXIT
            if [ -r /etc/os-release ] && grep -q '^ID=ubuntu' /etc/os-release; then
                printf '%s\n' 'You are already inside Ubuntu. Type exit to return to Termux, then paste setup again.'
                exit 23
            fi
            IFS= read -r -s -p 'Enter the same new Meow connection password (typing is hidden): ' meow_password </dev/tty
            printf '\n' >/dev/tty
            [ "${'$'}{#meow_password}" -ge 12 ] && [ "${'$'}{#meow_password}" -le 256 ] || { printf '%s\n' 'Use a password of 12–256 characters.'; exit 20; }
            IFS= read -r -s -p 'Repeat password: ' meow_confirm </dev/tty
            printf '\n' >/dev/tty
            [ "${'$'}meow_password" = "${'$'}meow_confirm" ] || { printf '%s\n' 'Passwords did not match. Paste setup again to retry.'; exit 20; }
            unset meow_confirm
        """.trimIndent()
        return script.replace(read, prompt) + "\nunset meow_password\nprintf '%s\\n' 'Server started. Return to Meow and tap Verify connection; only that check confirms readiness.'\n"
    }

    fun guidedCommand(context: Context, address: String): String {
        val source = context.assets.open("computer/setup-phone.sh").bufferedReader().use { it.readText() }
        return guidedCommandFromSource(source, address)
    }

    internal fun guidedCommandFromSource(source: String, address: String): String {
        val encoded = java.util.Base64.getEncoder().encodeToString(guidedScript(source, address).toByteArray(Charsets.UTF_8))
        // End the outer command before interactive read, so pasting never stores the password.
        return "bash -c \"${'$'}(printf '%s' '$encoded' | base64 -d)\""
    }

    suspend fun start(context: Context, password: String, address: String = OpenCodeLocalConnection.ADDRESS): String = lock.withLock {
        require(address == OpenCodeLocalConnection.ORIGINAL_ADDRESS || address == OpenCodeLocalConnection.RECOVERY_ADDRESS)
        require(password.length >= 12 && password.length <= 256 && '\n' !in password && '\r' !in password && '\u0000' !in password) {
            "Choose a private password of 12–256 characters on one line."
        }
        // Reuse a healthy server; authentication failures must not trigger a replacement.
        try { return@withLock OpenCodeLocalConnection.verify(password, address) }
        catch (_: java.net.ConnectException) { /* not started */ }
        when (TermuxSetupSupport.detect(context)) {
            TermuxSetupSupport.MISSING -> error("Install the recommended Termux edition first, then reopen setup.")
            TermuxSetupSupport.GUIDED -> throw GuidedSetupRequired()
            TermuxSetupSupport.AUTOMATIC -> Unit
        }
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) throw PermissionRequired()
        val script = withContext(Dispatchers.IO) {
            context.assets.open("computer/setup-phone.sh").bufferedReader().use { scriptForAddress(it.readText(), address) }
        }
        val id = ids.incrementAndGet()
        val result = CompletableDeferred<Int>()
        results[id] = result
        val callback = PendingIntent.getBroadcast(context, id,
            Intent(context, PhoneComputerSetupResult::class.java).putExtra("execution_id", id),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
                putExtra("com.termux.RUN_COMMAND_STDIN", "$password\n")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL", "0")
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
            }
            // After a reboot Termux is not running, and Android refuses a plain startService to an
            // idle app ("Not allowed to start service"). Termux documents startForegroundService
            // for this; its RunCommandService promotes itself to the foreground.
            val started = try { context.startService(intent) }
                catch (_: IllegalStateException) { context.startForegroundService(intent) }
            check(started != null) { "Open Termux once, then retry setup." }
            val exit = withTimeout(20 * 60_000L) { result.await() }
            if (exit == EXIT_EXTERNAL_APPS_DISABLED) throw ExternalAppsRequired()
            check(exit == 0) {
                when (exit) {
                    21 -> "Setup is already running in Termux. Wait for it to finish, then retry."
                    22 -> "The selected local port is already in use. Existing processes were left untouched."
                    else -> "Termux setup did not finish (code $exit). Check external-app access and your internet connection, then retry. Existing Ubuntu files were kept."
                }
            }
            // Process dispatch or a successful script alone is not proof of a usable worker.
            var version: String? = null
            repeat(30) {
                if (version == null) {
                    try { version = OpenCodeLocalConnection.verify(password, address) }
                    // A just-started server can accept and then stall or drop the first request.
                    catch (_: java.io.IOException) { kotlinx.coroutines.delay(2_000) }
                }
            }
            checkNotNull(version) { "OpenCode did not become ready. Retry to check its connection." }
        } finally {
            results.remove(id)
            callback.cancel()
        }
    }
}

/** Non-exported receiver reachable only through our explicit one-shot capability. */
class PhoneComputerSetupResult : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra("result") ?: return
        val failed = result.getInt("err", -1) != -1
        val exit = when {
            failed && PhoneComputerSetup.isExternalAppsError(result.getString("errmsg")) -> PhoneComputerSetup.EXIT_EXTERNAL_APPS_DISABLED
            failed -> -1
            else -> result.getInt("exitCode", -1)
        }
        PhoneComputerSetup.results[intent.getIntExtra("execution_id", -1)]?.complete(exit)
        // Never log result strings: third-party errors can contain command/stdin details.
    }
}
