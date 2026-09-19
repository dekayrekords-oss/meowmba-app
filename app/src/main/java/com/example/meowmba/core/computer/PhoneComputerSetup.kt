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
    class PermissionRequired : IllegalStateException("Allow Meow to run commands in Termux, then retry setup.")
    private val lock = Mutex()
    private val ids = AtomicInteger(1000)
    internal val results = ConcurrentHashMap<Int, CompletableDeferred<Int>>()

    internal fun scriptForAddress(source: String, address: String): String {
        require(address == OpenCodeLocalConnection.ORIGINAL_ADDRESS || address == OpenCodeLocalConnection.RECOVERY_ADDRESS)
        return if (address == OpenCodeLocalConnection.RECOVERY_ADDRESS)
            source.replace("4096", "4097").replace("setup-server.log", "setup-server-4097.log") else source
    }

    suspend fun start(context: Context, password: String, address: String = OpenCodeLocalConnection.ADDRESS): String = lock.withLock {
        require(address == OpenCodeLocalConnection.ORIGINAL_ADDRESS || address == OpenCodeLocalConnection.RECOVERY_ADDRESS)
        require(password.length >= 12 && password.length <= 256 && '\n' !in password && '\r' !in password && '\u0000' !in password) {
            "Choose a private password of 12–256 characters on one line."
        }
        // Reuse a healthy server; authentication failures must not trigger a replacement.
        try { return@withLock OpenCodeLocalConnection.verify(password, address) }
        catch (_: java.net.ConnectException) { /* not started */ }
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
            check(context.startService(intent) != null) { "Open Termux once, then retry setup." }
            val exit = withTimeout(20 * 60_000L) { result.await() }
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
                    catch (_: java.net.ConnectException) { kotlinx.coroutines.delay(2_000) }
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
        val exit = if (result.getInt("err", -1) != -1) -1 else result.getInt("exitCode", -1)
        PhoneComputerSetup.results[intent.getIntExtra("execution_id", -1)]?.complete(exit)
        // Never log result strings: third-party errors can contain command/stdin details.
    }
}
