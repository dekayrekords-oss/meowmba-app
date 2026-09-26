package com.example.meowmba.core.computer

import java.net.URI

/** Fixed local preview boundary, independent of model output and saved credentials. */
internal object DevelopmentStudioPolicy {
    const val PREVIEW = "http://127.0.0.1:8765/"
    fun previewAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port == 8765 &&
            uri.rawUserInfo == null && uri.rawAuthority == "127.0.0.1:8765"
    }.getOrDefault(false)

    private val ansi = Regex("\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))")
    fun appendOutput(previous: String, incoming: String): String =
        (previous + ansi.replace(incoming.take(64_000), "")
            .filter { it == '\n' || it == '\t' || it >= ' ' && it != '\u007f' })
            .takeLast(48_000)
}
