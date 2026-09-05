package com.example.meowmba.core.agent

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/** Turns a human-facing Hugging Face/GitHub page into one compatible model file. */
object ModelSourceResolver {
    data class Resolved(val url: String, val fileName: String, val sizeBytes: Long? = null)
    private val extensions = listOf(".litertlm", ".task", ".cact")
    private val client by lazy { HttpClient(Android) }

    fun isDirect(input: String): Boolean = runCatching {
        val path = URI(input).path.lowercase()
        extensions.any(path::endsWith)
    }.getOrDefault(false)

    suspend fun resolve(input: String, preferredName: String = ""): Result<Resolved> = runCatching {
        val uri = URI(input.trim())
        require(uri.scheme == "https" || uri.scheme == "http") { "Enter an http or https address" }
        if (isDirect(input)) {
            val name = preferredName.ifBlank { uri.path.substringAfterLast('/') }
            require(ModelCatalog.isValidModelFileName(name)) { "Unsupported model file type" }
            val direct = if (uri.host.equals("github.com", true) && uri.path.contains("/blob/")) {
                input.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/")
            } else input
            return@runCatching Resolved(direct, name)
        }
        when (uri.host.lowercase()) {
            "huggingface.co", "www.huggingface.co" -> resolveHuggingFace(uri, preferredName)
            "github.com", "www.github.com" -> resolveGitHub(uri, preferredName)
            else -> error("Use a direct model file, Hugging Face page, or GitHub repository")
        }
    }

    private suspend fun resolveHuggingFace(uri: URI, preferredName: String): Resolved {
        val p = uri.path.trim('/').split('/').filter { it.isNotBlank() }
        val offset = if (p.firstOrNull() == "models") 1 else 0
        require(p.size >= offset + 2) { "Hugging Face address must include owner and model" }
        val repo = p.drop(offset).take(2).joinToString("/")
        val response = client.get("https://huggingface.co/api/models/$repo")
        check(response.status.isSuccess()) { "Hugging Face model page was not found" }
        val siblings = JSONObject(response.bodyAsText()).optJSONArray("siblings") ?: JSONArray()
        val candidates = (0 until siblings.length()).mapNotNull { i ->
            val o = siblings.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("rfilename")
            if (!ModelCatalog.isValidModelFileName(name.substringAfterLast('/'))) return@mapNotNull null
            Triple(name, o.optJSONObject("lfs")?.optLong("size")?.takeIf { it > 0 }, score(name))
        }.filter { it.second == null || it.second!! <= MAX_MODEL_BYTES }
        val chosen = choose(candidates, preferredName) ?: error("No Android-compatible .task, .litertlm, or .cact model was found")
        val fileName = preferredName.ifBlank { chosen.first.substringAfterLast('/') }
        return Resolved("https://huggingface.co/$repo/resolve/main/${chosen.first}", fileName, chosen.second)
    }

    private suspend fun resolveGitHub(uri: URI, preferredName: String): Resolved {
        val p = uri.path.trim('/').split('/')
        require(p.size >= 2) { "GitHub address must include owner and repository" }
        val repo = p.take(2).joinToString("/")
        val release = client.get("https://api.github.com/repos/$repo/releases/latest")
        if (release.status.isSuccess()) {
            val assets = JSONObject(release.bodyAsText()).optJSONArray("assets") ?: JSONArray()
            val candidates = (0 until assets.length()).mapNotNull { i ->
                val o = assets.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name")
                if (!ModelCatalog.isValidModelFileName(name)) return@mapNotNull null
                Triple(name, o.optLong("size").takeIf { it > 0 }, score(name)) to o.optString("browser_download_url")
            }.filter { it.first.second == null || it.first.second!! <= MAX_MODEL_BYTES }
            val picked = candidates.sortedBy { it.first.third }.firstOrNull {
                preferredName.isBlank() || it.first.first == preferredName
            }
            if (picked != null) return Resolved(picked.second, preferredName.ifBlank { picked.first.first }, picked.first.second)
        }
        for (branch in listOf("main", "master")) {
            val tree = client.get("https://api.github.com/repos/$repo/git/trees/$branch?recursive=1")
            if (!tree.status.isSuccess()) continue
            val nodes = JSONObject(tree.bodyAsText()).optJSONArray("tree") ?: continue
            val candidates = (0 until nodes.length()).mapNotNull { i ->
                val o = nodes.optJSONObject(i) ?: return@mapNotNull null
                if (o.optString("type") != "blob") return@mapNotNull null
                val name = o.optString("path")
                if (!ModelCatalog.isValidModelFileName(name.substringAfterLast('/'))) return@mapNotNull null
                Triple(name, o.optLong("size").takeIf { it > 0 }, score(name))
            }.filter { it.second == null || it.second!! <= MAX_MODEL_BYTES }
            val chosen = choose(candidates, preferredName) ?: continue
            val fileName = preferredName.ifBlank { chosen.first.substringAfterLast('/') }
            return Resolved("https://raw.githubusercontent.com/$repo/$branch/${chosen.first}", fileName, chosen.second)
        }
        error("No compatible model file was found in this GitHub repository or its latest release")
    }

    private fun choose(items: List<Triple<String, Long?, Int>>, preferred: String) =
        items.filter { preferred.isBlank() || it.first.substringAfterLast('/') == preferred }.minByOrNull { it.third }

    private fun score(name: String): Int = when {
        name.endsWith(".litertlm", true) -> 0
        name.endsWith(".task", true) -> 1
        else -> 2
    }

    private const val MAX_MODEL_BYTES = 11L * 1024 * 1024 * 1024
}
