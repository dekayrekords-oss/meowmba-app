package com.example.meowmba.ui.companion

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.meowmba.core.computer.OpenCodeLocalConnection
import com.example.meowmba.core.computer.OpenCodeTerminal
import com.example.meowmba.core.computer.*
import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Development Studio terminal outlives its screen. It used to live in the page's
 * composition, so Back (or any navigation) closed the PTY and killed the running agent,
 * build or preview. Only "Close session" or leaving the menu (0 Exit) ends it now.
 */
internal object DevStudioSession {
    /** Printed by development_studio.py when its menu exits, so the page stops saying "Connected". */
    const val END_MARKER = "[Meow Development Studio ended]"

    var connecting by mutableStateOf(false); private set
    var connected by mutableStateOf(false); private set
    var hasTerminal by mutableStateOf(false); private set
    var output by mutableStateOf("")
    var status by mutableStateOf<String?>(null)
    var receiptText by mutableStateOf("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var terminal: OpenCodeTerminal? = null
    private var pump: Job? = null

    fun connect(context: Context) {
        if (connecting || connected) return
        val app = context.applicationContext
        connecting = true
        status = "Connecting to Ubuntu…"
        scope.launch {
            val events = Channel<String>(64, BufferOverflow.DROP_OLDEST)
            var worker: OpenCodeTerminal? = null
            try {
                worker = withContext(Dispatchers.IO) {
                    val storage = SecureStorageManager(app)
                    OpenCodeLocalConnection.restore(storage)
                    val key = storage.getApiKey(OpenCodeLocalConnection.CREDENTIAL).orEmpty()
                    check(key.isNotBlank()) { "Set up Computer first: Computer → Set up Computer." }
                    OpenCodeTerminal(key)
                }
                val program = withContext(Dispatchers.IO) {
                    app.assets.open("computer/development_studio.py").bufferedReader().use { it.readText() }
                }
                terminal = worker; hasTerminal = true
                pump?.cancel()
                pump = scope.launch { pumpOutput(app, events, worker) }
                var closedEarly = false
                worker.connectDevelopment(
                    onOutput = { events.trySend(it) },
                    onClosed = { closedEarly = true; scope.launch { ended(worker, "Session closed. Review the output, then open the terminal again.") } },
                    developmentProgram = program,
                )
                connected = !closedEarly && terminal === worker
                status = if (connected) "Connected. Answer the project-name prompt below, then choose numbered actions."
                    else "Session exited. Check the output; Python 3 must be installed in Ubuntu."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (terminal === worker) { terminal = null; hasTerminal = false }
                status = friendly(error)
            } finally { connecting = false }
        }
    }

    fun send(text: String) {
        val current = terminal ?: return
        try { current.send(text.toByteArray(Charsets.UTF_8)) }
        catch (error: Exception) { status = error.message ?: "Terminal disconnected."; connected = false }
    }

    fun close() {
        val owned = terminal ?: return
        ended(owned, "Session closed.")
    }

    private fun ended(worker: OpenCodeTerminal, message: String) {
        if (terminal !== worker) return
        terminal = null; hasTerminal = false; connected = false
        status = message
        scope.launch(Dispatchers.IO) { runCatching { worker.close() } }
    }

    private suspend fun pumpOutput(context: Context, events: Channel<String>, worker: OpenCodeTerminal) {
        for (text in events) {
            // Batch bursts instead of recomposing once per terminal frame.
            delay(32)
            val batch = StringBuilder(text)
            while (batch.length < 128_000) {
                val next = events.tryReceive().getOrNull() ?: break
                batch.append(next)
            }
            output = DevelopmentStudioPolicy.appendOutput(output, batch.toString().takeLast(64_000))
            // The Desk computer lists Studio projects from these same status lines (kept on this phone).
            DevStudioProjects.get(context).record(batch.toString())
            DevelopmentApkReceipt.latest(output)?.let { if (receiptText != it.line) receiptText = it.line }
            if (batch.contains(END_MARKER)) ended(worker, "Development Studio menu exited. Open the terminal again to continue.")
        }
    }

    /** Raw transport errors ("timeout") told people nothing. */
    internal fun friendly(error: Throwable): String = when {
        error is java.net.SocketTimeoutException || error.message == "timeout" ->
            "Ubuntu didn't answer in time. It may still be starting after a restart or setup — wait a few seconds and try again."
        error is java.net.ConnectException ->
            "Computer isn't running. Open Computer → Set up Computer and tap Setup computer to start it."
        else -> error.message ?: "Development Studio could not start."
    }
}
