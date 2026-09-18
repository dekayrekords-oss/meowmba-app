package com.example.meowmba.core.glasses

import com.example.meowmba.core.security.SecureStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Meta glasses ↔ Meow command bridge.
 *
 * Honest limits (see docs/META_GLASSES_MEOW_BRIDGE.md):
 * - "Hey Meta" is Meta's own assistant. MWDAT 0.8.0 has no public mic / voice-intent
 *   API to hijack that wake word into third-party apps.
 * - The strongest real path is: Ray-Ban Bluetooth SCO mic → Meow wake ("Hey Meow"/
 *   companion name) or tap-to-talk → same companion agent tool loop as in-app chat.
 * - Optional [enabled] gate lets Settings turn the bridge off without disconnecting
 *   the camera session.
 */
class MetaGlassesMeowBridge(
    private val storage: SecureStorageManager?,
) {
    private val _enabled = MutableStateFlow(storage?.getBooleanPreference(PREF_ENABLED, true) ?: true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _defaultSshHost = MutableStateFlow(
        storage?.getPreference(PREF_DEFAULT_SSH_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_SSH_HOST,
    )
    val defaultSshHost: StateFlow<String> = _defaultSshHost.asStateFlow()

    private val _lastIngest = MutableStateFlow<String?>(null)
    val lastIngest: StateFlow<String?> = _lastIngest.asStateFlow()

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        storage?.saveBooleanPreference(PREF_ENABLED, value)
    }

    fun setDefaultSshHost(name: String) {
        val cleaned = name.trim().ifBlank { DEFAULT_SSH_HOST }
        _defaultSshHost.value = cleaned
        storage?.savePreference(PREF_DEFAULT_SSH_HOST, cleaned)
    }

    /**
     * Normalize a glasses / phone voice transcript into a Meow chat command.
     * Returns null when the bridge is off or the text is empty after stripping
     * wake / Meta prefixes.
     */
    fun prepareCommand(raw: String, bridgeMustBeOn: Boolean = true): PreparedCommand? {
        if (bridgeMustBeOn && !_enabled.value) return null
        val normalized = normalizeTranscript(raw) ?: return null
        _lastIngest.value = normalized.text.take(200)
        return normalized
    }

    data class PreparedCommand(
        val text: String,
        /** Hint for the agent / caption UI (ssh / opencode / general). */
        val intent: CommandIntent,
    )

    enum class CommandIntent {
        GENERAL,
        SSH_MEOW,
        OPENCODE,
        SERVER_SHELL,
    }

    companion object {
        const val PREF_ENABLED = "meta_glasses_meow_bridge"
        const val PREF_DEFAULT_SSH_HOST = "meta_glasses_default_ssh_host"
        /** NetBird meow host label commonly saved in Connections / Servers. */
        const val DEFAULT_SSH_HOST = "meow"

        private val WAKE_PREFIX = Regex(
            """(?i)^\s*(hey\s+meta|ok\s+meta|hi\s+meta|meta[,:]?|hey\s+meow|ok\s+meow|hi\s+meow|hey\s+[a-z][\w'-]{0,24}|ok\s+[a-z][\w'-]{0,24})\s*[,:]?\s*""",
        )

        private val SSH_HINT = Regex("""(?i)\b(ssh|on\s+meow|meow\s+server|run\s+on\s+(the\s+)?server)\b""")
        private val OPENCODE_HINT = Regex("""(?i)\b(opencode|open\s*code)\b""")
        private val SHELL_HINT = Regex("""(?i)\b(server\s+shell|shell\s+on\s+meow|run\s+(this\s+)?command\s+on)\b""")

        fun normalizeTranscript(raw: String): PreparedCommand? {
            var text = raw.trim()
            if (text.isEmpty()) return null
            // Strip repeated wake prefixes (user may say "Hey Meta … Hey Meow …").
            repeat(3) {
                val stripped = WAKE_PREFIX.replaceFirst(text, "").trim()
                if (stripped == text) return@repeat
                text = stripped
            }
            if (text.isEmpty()) return null
            val intent = when {
                OPENCODE_HINT.containsMatchIn(text) -> CommandIntent.OPENCODE
                SHELL_HINT.containsMatchIn(text) -> CommandIntent.SERVER_SHELL
                SSH_HINT.containsMatchIn(text) -> CommandIntent.SSH_MEOW
                else -> CommandIntent.GENERAL
            }
            val routed = when (intent) {
                CommandIntent.OPENCODE ->
                    "Use the opencode.run tool on the default meow SSH host. User request: $text"
                CommandIntent.SERVER_SHELL ->
                    "Use the server.shell tool on the default meow SSH host if a command is clear. User request: $text"
                CommandIntent.SSH_MEOW ->
                    "Use the ssh.meow tool (default meow host) when a read-only server check fits. User request: $text"
                CommandIntent.GENERAL -> text
            }
            return PreparedCommand(routed, intent)
        }

        fun statusLine(
            hasSdk: Boolean,
            glassesConnected: Boolean,
            bridgeEnabled: Boolean,
            defaultHost: String,
        ): String = buildString {
            append(
                when {
                    !hasSdk -> "SDK missing — camera bridge needs a glasses-enabled build"
                    glassesConnected -> "Glasses linked"
                    else -> "Glasses not linked"
                },
            )
            append(" · bridge ")
            append(if (bridgeEnabled) "ON" else "OFF")
            append(" · SSH host \"")
            append(defaultHost)
            append('"')
        }
    }
}
