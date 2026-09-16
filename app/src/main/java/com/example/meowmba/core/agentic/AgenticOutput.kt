package com.example.meowmba.core.agentic

/** Scrub before storage as well as display; includes complete multiline private keys. */
object AgenticOutput {
    private val sensitive = Regex("(?i)(password|passwd|api[_-]?key|access[_-]?token|refresh[_-]?token|secret|authorization|private[_-]?key)\\s*[=:]|\\bBearer\\s+")
    fun scrub(raw: String, limit: Int = 3500): String {
        var privateKey = false
        val out = StringBuilder()
        for (line in raw.take(200_000).lineSequence()) {
            if (line.contains("-----BEGIN ") && line.contains("PRIVATE KEY-----")) { privateKey = true; out.append("[private key removed]\n"); continue }
            if (privateKey) { if (line.contains("-----END ") && line.contains("PRIVATE KEY-----")) privateKey = false; continue }
            out.append(if (sensitive.containsMatchIn(line)) "[credential removed]" else line).append('\n')
            if (out.length >= limit) break
        }
        return out.toString().trimEnd().take(limit)
    }
}
