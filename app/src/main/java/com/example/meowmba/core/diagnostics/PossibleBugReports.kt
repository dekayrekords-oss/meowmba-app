package com.example.meowmba.core.diagnostics

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

@Serializable
data class PossibleBugReport(
    val id: String,
    val report: String,
    val context: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val occurrences: Int = 1,
    val status: String = "possible_bug",
)

/** User reports are evidence of a possible defect, not verified diagnoses or
 * instructions for an autonomous repair. Kept locally and separately from
 * personal memory; no uploads, model requests, or task execution are triggered. */
class PossibleBugReports(workspace: File) {
    private val file = File(workspace, "diagnostics/possible-bugs.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Synchronized fun list(): List<PossibleBugReport> {
        if (!file.isFile) return emptyList()
        check(file.length() <= 512_000) { "Issue-report history is too large to read safely" }
        return json.decodeFromString<List<PossibleBugReport>>(file.readText()).also {
            check(it.size <= 100) { "Issue-report history has too many entries" }
        }
    }

    @Synchronized fun record(text: String, context: String, now: Long = System.currentTimeMillis(), explicit: Boolean = false): PossibleBugReport? {
        val report = if (explicit) sanitize(text).takeIf { it.isNotBlank() } else extract(text)
        if (report == null) return null
        val id = MessageDigest.getInstance("SHA-256").digest(report.lowercase(Locale.ROOT).toByteArray())
            .take(10).joinToString("") { "%02x".format(it) }
        val existing = list()
        val prior = existing.firstOrNull { it.id == id }
        val entry = PossibleBugReport(id, report, context.take(320), prior?.firstSeen ?: now, now,
            ((prior?.occurrences ?: 0) + 1).coerceAtMost(1_000_000))
        val updated = (existing.filterNot { it.id == id } + entry).takeLast(100)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Issue-report folder unavailable" }
        val pending = Files.createTempFile(file.parentFile!!.toPath(), ".bugs-", ".pending")
        try {
            java.io.FileOutputStream(pending.toFile()).use { stream ->
                stream.write(json.encodeToString(updated).toByteArray()); stream.fd.sync()
            }
            Files.move(pending, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(pending) }
        return entry
    }

    companion object {
        private val failure = Regex("\\b(?:not working|isn['’]?t working|isnt working|doesn['’]?t work|doesnt work|" +
            "(?:does|do) not work|(?:is|are|seems?) broken|" +
            "won['’]?t work|wont work|stopped working|keeps crashing|crashed|crashes|" +
            "stuck|freezes|frozen|locks? me out|no response|missing (?:response|reply)|" +
            "(?:gives?|returns?|error|http) (?:a )?404|(?:found|there['’]?s|another) (?:a )?bug|" +
            "overheats?|overheating|(?:voice|speech) recognition (?:is bad|errors|fails)|" +
            "(?:unable|cannot|can['’]?t) (?:to )?report (?:a )?bugs?|traffic (?:is |information is )?(?:dead|unavailable))\\b", RegexOption.IGNORE_CASE)
        private val missingInteraction = Regex("\\b(?:(?:mic|microphone)(?: button)? [^.?!]{0,48}(?:stays on|keeps listening)|" +
            "(?:does not|doesn['’]?t|doesnt|cannot|can['’]?t) hear (?:me|us)|" +
            "(?:do not|don['’]?t|dont|cannot|can['’]?t) see (?:the |your |any )?(?:response|reply)|" +
            "(?:response|reply) (?:is |was )?missing)\\b", RegexOption.IGNORE_CASE)
        private val hypothetical = Regex("^(?:what if|if |suppose |imagine |write (?:a |an )?(?:story|example|test)|" +
            "say |translate |define |what does )", RegexOption.IGNORE_CASE)

        fun extract(text: String): String? {
            val clean = text.trim().replace(Regex("\\s+"), " ").take(4000)
            if (hypothetical.containsMatchIn(clean) || (!failure.containsMatchIn(clean) && !missingInteraction.containsMatchIn(clean))) return null
            return sanitize(clean)
        }

        fun sanitize(text: String): String = text.trim().replace(Regex("\\s+"), " ").take(4000)
                .replace(Regex("(?i)\\b(?:bearer\\s+|sk-)[a-z0-9_\\-]{12,}"), "[redacted token]")
                .replace(Regex("(?i)(password|token|api[_ -]?key)\\s*[:=]\\s*\\S+"), "$1=[redacted]")
                .replace(Regex("\\b[0-9]{12,19}\\b"), "[redacted number]")
                .take(1200)
    }
}
