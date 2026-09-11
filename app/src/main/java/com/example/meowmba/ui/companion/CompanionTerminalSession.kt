package com.example.meowmba.ui.companion

import com.example.meowmba.core.agentic.ConnectionProfileType
import com.example.meowmba.core.agentic.RoomConnectionProfileStore
import com.example.meowmba.core.agentic.SecureStorageCredentialResolver
import com.example.meowmba.core.database.MeowmbaDatabase
import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.ssh.SshHostRegistry
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Connection lifecycle for the companion interactive terminal. */
enum class CompanionTerminalStatus {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

/**
 * Persistent interactive shell for the companion Terminal tab.
 *
 * Remote: JSch [Session] + [ChannelShell] with PTY (stderr merges via PTY).
 * Local Linux (see [LocalLinuxSupport]): prefer Termux bash/sh when executable,
 * else sandbox `/system/bin/sh` with PATH + [LocalShellBuiltins] for `ls`/`pwd`.
 * Clear UI when Termux is missing or SELinux blocks exec. No root / Magisk /
 * SELinux bypass — SSH profiles reach a full machine (meow).
 *
 * Agentic one-shot [ChannelExec] stays on [DefaultAgenticSshExecutor] —
 * this class is session-oriented only. Never logs credential material.
 */
class CompanionTerminalSession(
    private val storage: SecureStorageManager,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val _status = MutableStateFlow(CompanionTerminalStatus.Disconnected)
    val status: StateFlow<CompanionTerminalStatus> = _status.asStateFlow()

    private val _statusDetail = MutableStateFlow<String?>(null)
    val statusDetail: StateFlow<String?> = _statusDetail.asStateFlow()

    private val _output = MutableSharedFlow<String>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val output: SharedFlow<String> = _output.asSharedFlow()

    /** True when the backend will not echo typed input (piped local shell). */
    private val _needsLocalEcho = MutableStateFlow(false)
    val needsLocalEcho: StateFlow<Boolean> = _needsLocalEcho.asStateFlow()

    private val _connectedTargetId = MutableStateFlow<String?>(null)
    val connectedTargetId: StateFlow<String?> = _connectedTargetId.asStateFlow()

    private var jschSession: Session? = null
    private var shellChannel: ChannelShell? = null
    private var localProcess: Process? = null
    private var stdin: OutputStream? = null
    private var readerJob: Job? = null
    private var stderrJob: Job? = null
    private val closed = AtomicBoolean(true)

    /** True while the backend is the piped local shell (not JSch PTY). */
    private val isLocalSession = AtomicBoolean(false)
    /** App sandbox cwd used for local builtins (ls/pwd). */
    @Volatile private var localCwd: File? = null

    private var ptyCols: Int = 80
    private var ptyRows: Int = 24

    suspend fun connect(targetId: String) = mutex.withLock {
        if (_status.value == CompanionTerminalStatus.Connecting) return
        disconnectLocked(emitStatus = false)
        closed.set(false)
        _status.value = CompanionTerminalStatus.Connecting
        _statusDetail.value = null
        _connectedTargetId.value = targetId
        _needsLocalEcho.value = false
        isLocalSession.set(false)
        localCwd = null
        emitSafe("Connecting…\n")

        val result = runCatching {
            when {
                targetId.isBlank() || targetId == CompanionTerminalTarget.LOCAL_ID ->
                    openLocal()
                targetId.startsWith(CompanionTerminalTarget.LEGACY_PREFIX) -> {
                    val name = targetId.removePrefix(CompanionTerminalTarget.LEGACY_PREFIX)
                    openLegacy(name)
                }
                else -> openAgenticProfile(targetId)
            }
        }

        result.exceptionOrNull()?.let { e ->
            disconnectLocked(emitStatus = false)
            _status.value = CompanionTerminalStatus.Error
            _statusDetail.value = e.message ?: e.javaClass.simpleName
            emitSafe("error: ${_statusDetail.value}\n")
            _connectedTargetId.value = null
            return
        }

        if (closed.get()) {
            _status.value = CompanionTerminalStatus.Disconnected
            return
        }
        _status.value = CompanionTerminalStatus.Connected
        _statusDetail.value = null
        emitSafe("[connected]\n")
    }

    fun stdin(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (_status.value != CompanionTerminalStatus.Connected) return
        val stream = stdin ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                stream.write(bytes)
                stream.flush()
            }.onFailure { e ->
                emitSafe("\nerror writing stdin: ${e.message ?: e.javaClass.simpleName}\n")
            }
        }
    }

    fun sendLine(line: String) {
        val raw = if (line.endsWith("\n")) line.dropLast(1) else line
        // Local sandbox: Java builtins for ls/pwd, else rewrite to absolute/toybox.
        if (isLocalSession.get()) {
            val cwd = localCwd ?: storage.context.filesDir
            val builtin = LocalShellBuiltins.tryBuiltin(raw, cwd)
            if (builtin != null) {
                if (_needsLocalEcho.value) {
                    emitSafe(if (line.endsWith("\n")) line else "$line\n")
                }
                emitSafe(builtin)
                return
            }
            val resolved = LocalShellBuiltins.resolveSimpleCommand(raw)
            val payload = if (resolved.endsWith("\n")) resolved else "$resolved\n"
            if (_needsLocalEcho.value) {
                // Echo what the user typed, not the rewritten absolute path.
                emitSafe(if (line.endsWith("\n")) line else "$line\n")
            }
            stdin(payload.toByteArray(Charsets.UTF_8))
            return
        }
        val payload = if (line.endsWith("\n")) line else "$line\n"
        if (_needsLocalEcho.value) {
            emitSafe(payload)
        }
        stdin(payload.toByteArray(Charsets.UTF_8))
    }

    fun interrupt() {
        stdin(byteArrayOf(0x03))
    }

    fun resize(cols: Int, rows: Int) {
        ptyCols = cols.coerceIn(20, 500)
        ptyRows = rows.coerceIn(8, 200)
        val channel = shellChannel ?: return
        runCatching {
            channel.setPtySize(ptyCols, ptyRows, ptyCols * 8, ptyRows * 16)
        }
    }

    suspend fun disconnect() = mutex.withLock {
        disconnectLocked(emitStatus = true)
    }

    fun disconnectAsync() {
        scope.launch(Dispatchers.IO) { disconnect() }
    }

    private fun disconnectLocked(emitStatus: Boolean) {
        closed.set(true)
        readerJob?.cancel()
        readerJob = null
        stderrJob?.cancel()
        stderrJob = null
        runCatching { stdin?.close() }
        stdin = null
        runCatching { shellChannel?.disconnect() }
        shellChannel = null
        runCatching { jschSession?.disconnect() }
        jschSession = null
        runCatching { localProcess?.destroyForcibly() }
        localProcess = null
        isLocalSession.set(false)
        localCwd = null
        _needsLocalEcho.value = false
        _connectedTargetId.value = null
        if (emitStatus) {
            if (_status.value != CompanionTerminalStatus.Disconnected) {
                emitSafe("[disconnected]\n")
            }
            _status.value = CompanionTerminalStatus.Disconnected
            _statusDetail.value = null
        }
    }

    private fun openLocal() {
        val probe = LocalLinuxSupport.probe(storage.context)
        val shell = probe.shellPath
            ?: LocalLinuxSupport.findPreferredShell()
            ?: error(
                "no local shell found (/system/bin/sh or Termux). " +
                    LocalLinuxSupport.INSTALL_TERMUX_MSG,
            )
        val cwd = LocalLinuxSupport.resolveCwd(storage.context.filesDir, shell, probe.kind)
            ?: resolveLocalCwd(storage, shell)
            ?: storage.context.filesDir
        cwd.mkdirs()
        val pb = ProcessBuilder(shell)
        pb.redirectErrorStream(false) // read stderr separately so errors surface
        pb.directory(cwd)
        val env = pb.environment()
        env["TERM"] = "xterm-256color"
        env["HOME"] = cwd.absolutePath
        env["PATH"] = LocalShellBuiltins.mergePreferredPath(env["PATH"])
        env.putIfAbsent("PS1", "\\$ ")
        val proc = try {
            pb.start()
        } catch (e: Exception) {
            // SELinux / execute denied on Termux binary — fall back to sandbox sh.
            val sandbox = LocalLinuxSupport.findSandboxShell()
            if (sandbox == null || sandbox == shell) {
                error(
                    "cannot start local shell (${e.message ?: e.javaClass.simpleName}). " +
                        LocalLinuxSupport.INSTALL_TERMUX_MSG,
                )
            }
            emitSafe(
                "Termux exec blocked (${e.message ?: e.javaClass.simpleName}); " +
                    "falling back to sandbox sh. " +
                    LocalLinuxSupport.INSTALL_TERMUX_MSG + "\n",
            )
            val fb = ProcessBuilder(sandbox)
            fb.redirectErrorStream(false)
            fb.directory(cwd)
            val env2 = fb.environment()
            env2["TERM"] = "xterm-256color"
            env2["HOME"] = cwd.absolutePath
            env2["PATH"] = LocalShellBuiltins.mergePreferredPath(env2["PATH"])
            env2.putIfAbsent("PS1", "\\$ ")
            fb.start()
        }
        localProcess = proc
        stdin = proc.outputStream
        isLocalSession.set(true)
        localCwd = cwd
        _needsLocalEcho.value = true
        // Banner: Termux Linux vs Local sandbox (honest limits).
        emitSafe(LocalShellBuiltins.bannerLine(cwd, probe))
        probe.installHint?.let { hint ->
            emitSafe("$hint\n")
        }
        if (probe.kind == LocalLinuxKind.TERMUX_LINUX) {
            emitSafe(LocalLinuxSupport.PROOT_LOGIN_HINT + "\n")
            emitSafe("Paste to bootstrap: " + LocalLinuxSupport.TERMUX_BOOTSTRAP_CMD + "\n")
        }
        // Also export PATH/HOME and cd inside the shell — ProcessBuilder env is
        // not always honored by Android's /system/bin/sh applets.
        val boot = LocalShellBuiltins.connectExports(cwd)
        runCatching {
            proc.outputStream.write(boot.toByteArray(Charsets.UTF_8))
            proc.outputStream.flush()
        }
        startReader(proc.inputStream, label = null) {
            if (!proc.isAlive) {
                emitSafe("\n[local shell exited ${proc.exitValue()}]\n")
                scope.launch(Dispatchers.IO) {
                    mutex.withLock { disconnectLocked(emitStatus = true) }
                }
            }
        }
        // Surface stderr so "permission denied" is visible (not swallowed).
        stderrJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(2048)
            val err = proc.errorStream
            try {
                while (isActive && !closed.get()) {
                    val n = try {
                        err.read(buf)
                    } catch (_: Exception) {
                        -1
                    }
                    if (n < 0) break
                    if (n == 0) continue
                    emitSafe(String(buf, 0, n, Charsets.UTF_8))
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun openLegacy(hostName: String) {
        val server = SshHostRegistry(storage).get(hostName)
            ?: error("unknown saved server \"$hostName\"")
        val password = server.password
        if (password.isBlank()) {
            error("no password stored for \"$hostName\" (update SSH hosts in Settings)")
        }
        openJschShell(
            username = server.user,
            host = server.host,
            port = server.port,
            secret = password,
            workspaceRoot = null,
        )
    }

    private fun openAgenticProfile(profileId: String) {
        val db = MeowmbaDatabase.getDatabase(storage.context)
        val profiles = RoomConnectionProfileStore(db.connectionProfileDao())
        val profile = profiles.get(profileId)
            ?: error("Unknown connection profile \"$profileId\"")
        if (profile.type != ConnectionProfileType.SSH) {
            error("Profile \"${profile.displayName}\" is ${profile.type}; interactive terminal requires SSH")
        }
        val credentials = SecureStorageCredentialResolver(storage)
        val secret = credentials.resolve(profile.credentialRef)
        if (secret.isNullOrEmpty()) {
            error(
                "No credential configured for ref \"${profile.credentialRef}\". " +
                    "Store it via SecureStorage (agentic_cred_<ref>); never put secrets in prompts.",
            )
        }
        openJschShell(
            username = profile.username,
            host = profile.host,
            port = profile.port,
            secret = secret,
            workspaceRoot = profile.workspaceRoot,
        )
    }

    private fun openJschShell(
        username: String,
        host: String,
        port: Int,
        secret: String,
        workspaceRoot: String?,
    ) {
        val jsch = JSch()
        val session = jsch.getSession(username, host, port)
        session.setPassword(secret)
        session.setConfig("StrictHostKeyChecking", "no")
        session.timeout = 15_000
        session.connect(15_000)
        jschSession = session

        val channel = session.openChannel("shell") as ChannelShell
        channel.setPty(true)
        channel.setPtyType("xterm-256color")
        channel.setPtySize(ptyCols, ptyRows, ptyCols * 8, ptyRows * 16)
        runCatching { channel.setEnv("TERM", "xterm-256color") }
        val out = channel.outputStream
        val inp = channel.inputStream
        // PTY merges stderr into the main stream; also drain extInputStream just in case.
        val err = runCatching { channel.extInputStream }.getOrNull()
        channel.connect(15_000)
        shellChannel = channel
        stdin = out
        _needsLocalEcho.value = false

        startReader(inp, label = null) {
            if (channel.isClosed) {
                val exit = channel.exitStatus
                emitSafe("\n[remote shell closed exit=$exit]\n")
                scope.launch(Dispatchers.IO) {
                    mutex.withLock { disconnectLocked(emitStatus = true) }
                }
            }
        }
        if (err != null) {
            stderrJob = scope.launch(Dispatchers.IO) {
                val buf = ByteArray(2048)
                try {
                    while (isActive && !closed.get()) {
                        val n = try {
                            err.read(buf)
                        } catch (_: Exception) {
                            -1
                        }
                        if (n < 0) break
                        if (n == 0) continue
                        emitSafe(String(buf, 0, n, Charsets.UTF_8))
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }
        }

        // Land in workspaceRoot when set, otherwise home (~). Avoid restricted cwd.
        val root = workspaceRoot?.trim()?.takeIf { it.isNotEmpty() }
        val cdCmd = if (root != null) {
            "cd ${shellSingleQuote(root)} 2>/dev/null || cd \"\$HOME\" 2>/dev/null || true\n"
        } else {
            "cd \"\$HOME\" 2>/dev/null || true\n"
        }
        out.write(cdCmd.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun startReader(stream: InputStream, label: String?, onEof: () -> Unit) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(4096)
            try {
                while (isActive && !closed.get()) {
                    val n = try {
                        stream.read(buf)
                    } catch (_: Exception) {
                        -1
                    }
                    if (n < 0) {
                        onEof()
                        break
                    }
                    if (n == 0) continue
                    val chunk = String(buf, 0, n, Charsets.UTF_8)
                    emitSafe(if (label != null) "[$label]$chunk" else chunk)
                }
            } finally {
                // Reader exit alone does not always mean disconnect (cancel).
            }
        }
    }

    private fun emitSafe(text: String) {
        if (text.isEmpty()) return
        _output.tryEmit(text)
    }

    companion object {
        private fun findLocalShell(): String? =
            LocalLinuxSupport.findPreferredShell()

        /**
         * Prefer Termux HOME when using a Termux shell; otherwise the app [filesDir]
         * (readable). Never start in `/` — `ls` there is permission-denied on Android.
         */
        internal fun resolveLocalCwd(storage: SecureStorageManager, shellPath: String): File? {
            val kind = if (shellPath.contains("com.termux")) {
                LocalLinuxKind.TERMUX_LINUX
            } else {
                LocalLinuxKind.LOCAL_SANDBOX
            }
            LocalLinuxSupport.resolveCwd(storage.context.filesDir, shellPath, kind)?.let { return it }
            val noBackup = storage.context.noBackupFilesDir
            if (noBackup != null) {
                noBackup.mkdirs()
                if (noBackup.isDirectory && noBackup.canRead()) return noBackup
            }
            return null
        }

        internal fun mergePath(existing: String?): String =
            LocalShellBuiltins.mergePreferredPath(existing)

        /** POSIX single-quote escape for a remote cd path / argv element. */
        internal fun shellSingleQuote(raw: String): String =
            "'" + raw.replace("'", "'\"'\"'") + "'"

        /** Join argv for remote shells without smashing spaces inside args. */
        fun shellJoinArgv(argv: List<String>): String =
            argv.joinToString(" ") { shellSingleQuote(it) }
    }
}
