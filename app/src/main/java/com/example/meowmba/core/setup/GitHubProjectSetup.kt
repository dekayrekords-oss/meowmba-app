package com.example.meowmba.core.setup

import com.example.meowmba.core.security.SecureStorageManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kid-simple GitHub wiring for first-run / projects.
 *
 * Reuses the existing Connections storage key [STORAGE_KEY] (`connector_github`)
 * that [com.example.meowmba.core.plugins.ServiceIntegrations] and Settings already use.
 * No GitHub App — classic PAT + repo URL only. Never logs the token.
 */
object GitHubProjectSetup {
    const val STORAGE_KEY = "connector_github"
    const val PREF_REPO_URL = "projects_github_repo_url"
    const val PREF_REPO_MODE = "projects_github_mode" // create | link | skip
    const val TOKEN_URL = "https://github.com/settings/tokens?type=beta"
    const val NEW_TOKEN_CLASSIC = "https://github.com/settings/tokens/new?scopes=repo&description=Meowmba"

    data class Result(val ok: Boolean, val message: String, val repoUrl: String? = null)

    fun saveToken(storage: SecureStorageManager, token: String) {
        val t = token.trim()
        if (t.isNotEmpty()) storage.saveApiKey(STORAGE_KEY, t)
    }

    fun saveLinkedRepo(storage: SecureStorageManager, rawUrl: String): Result {
        val url = normalizeRepoUrl(rawUrl) ?: return Result(false, "That doesn’t look like a GitHub address.")
        storage.savePreference(PREF_REPO_URL, url)
        storage.savePreference(PREF_REPO_MODE, "link")
        return Result(true, "Linked $url", url)
    }

    fun markSkipped(storage: SecureStorageManager) {
        storage.savePreference(PREF_REPO_MODE, "skip")
    }

    /**
     * Create a new **private** repo under the authenticated user.
     * Requires a PAT with `repo` scope already saved or passed in [token].
     */
    fun createPrivateRepo(
        storage: SecureStorageManager,
        repoName: String,
        tokenOverride: String? = null,
    ): Result {
        val name = sanitizeRepoName(repoName)
            ?: return Result(false, "Pick a short name with letters or numbers.")
        val token = tokenOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: storage.getApiKey(STORAGE_KEY)?.trim().orEmpty()
        if (token.isEmpty()) return Result(false, "Paste a GitHub token first.")
        return runCatching {
            val body = JSONObject()
                .put("name", name)
                .put("private", true)
                .put("auto_init", true)
                .put("description", "Meowmba project")
            val resp = postJson("https://api.github.com/user/repos", "Bearer $token", body)
            val json = JSONObject(resp)
            val html = json.optString("html_url").ifBlank {
                json.optString("clone_url")
            }
            if (html.isBlank()) return Result(false, "GitHub answered, but no repo address came back.")
            storage.saveApiKey(STORAGE_KEY, token)
            storage.savePreference(PREF_REPO_URL, html)
            storage.savePreference(PREF_REPO_MODE, "create")
            Result(true, "Made private repo $html", html)
        }.getOrElse { e ->
            Result(false, friendlyError(e.message))
        }
    }

    fun normalizeRepoUrl(raw: String): String? {
        val t = raw.trim().removeSuffix(".git")
        if (t.isEmpty()) return null
        val https = Regex("""^https?://(www\.)?github\.com/([\w.-]+)/([\w.-]+)/?$""", RegexOption.IGNORE_CASE)
        val ssh = Regex("""^git@github\.com:([\w.-]+)/([\w.-]+)$""", RegexOption.IGNORE_CASE)
        https.matchEntire(t)?.let { m ->
            return "https://github.com/${m.groupValues[2]}/${m.groupValues[3]}"
        }
        ssh.matchEntire(t)?.let { m ->
            return "https://github.com/${m.groupValues[1]}/${m.groupValues[2]}"
        }
        // owner/name shorthand
        val short = Regex("""^([\w.-]+)/([\w.-]+)$""")
        short.matchEntire(t)?.let { m ->
            return "https://github.com/${m.groupValues[1]}/${m.groupValues[2]}"
        }
        return null
    }

    fun sanitizeRepoName(raw: String): String? {
        val cleaned = raw.trim().lowercase()
            .replace(Regex("""[^a-z0-9._-]+"""), "-")
            .trim('-', '.', '_')
            .take(100)
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return null
        return cleaned
    }

    private fun friendlyError(msg: String?): String {
        val m = msg.orEmpty()
        return when {
            "401" in m || "Bad credentials" in m -> "GitHub said the token is wrong. Make a new one and paste it again."
            "403" in m -> "GitHub said no. Check the token can make private repos."
            "422" in m -> "That repo name may already exist. Try another name."
            m.isBlank() -> "Could not talk to GitHub right now."
            else -> "GitHub: ${m.take(120)}"
        }
    }

    private fun postJson(url: String, bearer: String, body: JSONObject): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Meowmba/1.0")
            connectTimeout = 20_000
            readTimeout = 20_000
            if (bearer.isNotBlank()) setRequestProperty("Authorization", bearer)
            outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }
            ?: ""
        conn.disconnect()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${resp.take(200)}")
        return resp
    }
}
