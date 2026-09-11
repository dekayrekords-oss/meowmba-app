package com.example.meowmba.core.agentic

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * HTTP client for [ConnectionProfileType.HTTPS_RUNTIME] worker hosts.
 *
 * Stub server protocol (documented in docs/TERMINAL_CLONE_AGENTIC.md):
 *
 * | Method | Path | Purpose |
 * |--------|------|---------|
 * | GET | `/health` | Liveness; expects JSON `{ "ok": true }` or HTTP 200 |
 * | POST | `/v1/jobs` | Start job: `{ "argv": [...], "cwd"?: "...", "timeoutMs"?: N }` → `{ "jobId": "..." }` |
 * | GET | `/v1/jobs/{id}` | Status: `{ "status": "queued\|running\|succeeded\|failed", "exitCode"?: N }` |
 * | GET | `/v1/jobs/{id}/logs` | Logs: `{ "stdout": "...", "stderr": "..." }` |
 *
 * Auth: optional Bearer token from profile [ConnectionProfile.credentialRef]
 * (resolved via [CredentialResolver] — never logged).
 *
 * Base URL comes from profile.host (full URL, e.g. `https://worker.example:8443`)
 * or `http(s)://{host}:{port}` when host has no scheme. Port defaults to 8443
 * for HTTPS_RUNTIME when not using a full URL.
 *
 * The worker itself is **not** packaged inside the APK (impossible / out of
 * scope for on-device); this client talks to an operator-hosted stub.
 */
class HttpsRuntimeClient(
    private val baseUrl: String,
    private val bearerToken: String? = null,
    private val timeoutMs: Int = 30_000,
) {
    data class HealthResult(val ok: Boolean, val detail: String)
    data class StartJobResult(val jobId: String)
    data class JobStatus(
        val status: String,
        val exitCode: Int? = null,
        val detail: String = "",
    )
    data class JobLogs(val stdout: String, val stderr: String)

    fun health(): HealthResult {
        return try {
            val (code, body) = request("GET", "/health")
            val okFlag = try {
                JSONObject(body).optBoolean("ok", code in 200..299)
            } catch (_: Exception) {
                code in 200..299
            }
            HealthResult(ok = okFlag && code in 200..299, detail = "HTTP $code ${body.take(120)}")
        } catch (e: Exception) {
            HealthResult(ok = false, detail = e.message ?: e.javaClass.simpleName)
        }
    }

    fun startJob(argv: List<String>, cwd: String? = null, jobTimeoutMs: Long? = null): StartJobResult {
        val payload = JSONObject()
            .put("argv", JSONArray(argv))
        if (!cwd.isNullOrBlank()) payload.put("cwd", cwd)
        if (jobTimeoutMs != null) payload.put("timeoutMs", jobTimeoutMs)
        val (code, body) = request("POST", "/v1/jobs", payload.toString())
        if (code !in 200..299) {
            error("startJob HTTP $code ${body.take(200)}")
        }
        val jobId = JSONObject(body).optString("jobId").trim()
        if (jobId.isEmpty()) error("startJob missing jobId")
        return StartJobResult(jobId)
    }

    fun jobStatus(jobId: String): JobStatus {
        val (code, body) = request("GET", "/v1/jobs/${jobId.trim()}")
        if (code !in 200..299) {
            error("jobStatus HTTP $code ${body.take(200)}")
        }
        val root = JSONObject(body)
        return JobStatus(
            status = root.optString("status", "unknown"),
            exitCode = if (root.has("exitCode") && !root.isNull("exitCode")) root.optInt("exitCode") else null,
            detail = root.optString("detail", "").take(200),
        )
    }

    fun jobLogs(jobId: String): JobLogs {
        val (code, body) = request("GET", "/v1/jobs/${jobId.trim()}/logs")
        if (code !in 200..299) {
            error("jobLogs HTTP $code ${body.take(200)}")
        }
        val root = JSONObject(body)
        return JobLogs(
            stdout = root.optString("stdout", "").take(MAX_OUTPUT),
            stderr = root.optString("stderr", "").take(MAX_OUTPUT),
        )
    }

    /**
     * Start a job, poll until terminal status or [overallTimeoutMs], then fetch logs.
     * Maps to [SshExecResult]-shaped success/failure for the agentic executor.
     */
    fun runArgv(
        argv: List<String>,
        cwd: String? = null,
        overallTimeoutMs: Long = 60_000L,
        pollIntervalMs: Long = 750L,
    ): SshExecResult {
        if (argv.isEmpty()) return SshExecResult.Error("argv must not be empty")
        return try {
            val health = health()
            if (!health.ok) {
                return SshExecResult.Error("HTTPS_RUNTIME health failed: ${health.detail.take(160)}")
            }
            val started = startJob(argv, cwd = cwd, jobTimeoutMs = overallTimeoutMs)
            val deadline = System.currentTimeMillis() + overallTimeoutMs.coerceAtLeast(1_000L)
            var last: JobStatus? = null
            while (System.currentTimeMillis() < deadline) {
                last = jobStatus(started.jobId)
                when (last.status.lowercase()) {
                    "succeeded", "success", "ok", "done", "failed", "error", "cancelled" -> break
                    else -> Thread.sleep(pollIntervalMs.coerceAtLeast(200L))
                }
            }
            val status = last ?: return SshExecResult.Error("HTTPS_RUNTIME: no status for job=${started.jobId}")
            val logs = runCatching { jobLogs(started.jobId) }.getOrElse {
                JobLogs(stdout = "", stderr = "logs unavailable: ${it.message ?: it.javaClass.simpleName}")
            }
            val exit = status.exitCode ?: when (status.status.lowercase()) {
                "succeeded", "success", "ok", "done" -> 0
                else -> 1
            }
            if (status.status.lowercase() in setOf("queued", "running", "pending", "unknown") &&
                System.currentTimeMillis() >= deadline
            ) {
                return SshExecResult.Error(
                    "HTTPS_RUNTIME timed out job=${started.jobId} status=${status.status}",
                )
            }
            SshExecResult.Ok(exitCode = exit, stdout = logs.stdout, stderr = logs.stderr)
        } catch (e: Exception) {
            SshExecResult.Error("HTTPS_RUNTIME: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun request(method: String, path: String, body: String? = null): Pair<Int, String> {
        val url = joinUrl(baseUrl, path)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doInput = true
                setRequestProperty("Accept", "application/json")
                val token = bearerToken?.trim().orEmpty()
                if (token.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (body != null) {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            code to text
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val MAX_OUTPUT = 8_192

        fun baseUrlFor(profile: ConnectionProfile): String {
            val raw = profile.host.trim()
            if (raw.startsWith("http://", ignoreCase = true) ||
                raw.startsWith("https://", ignoreCase = true)
            ) {
                return raw.trimEnd('/')
            }
            val port = if (profile.port > 0) profile.port else 8443
            val scheme = if (port == 80) "http" else "https"
            return "$scheme://${raw.trimEnd('/')}:$port"
        }

        private fun joinUrl(base: String, path: String): String {
            val b = base.trimEnd('/')
            val p = if (path.startsWith('/')) path else "/$path"
            return b + p
        }
    }
}
