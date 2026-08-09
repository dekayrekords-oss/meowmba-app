package com.example.meowmba.core.memory

import com.example.meowmba.core.database.ImportanceLevel
import com.example.meowmba.core.database.MemoryEntity
import com.example.meowmba.core.database.MemoryType
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sync adapter for the meow-mem0 semantic/episodic memory service
 * (http://<host>:11438, mem0 1.0 REST shim). Room stays the source of truth;
 * this connector only mirrors new local memories out to the shared semantic
 * store so the server-side brain (and other devices later) can recall them.
 *
 * user_id is a stable per-install identity (persisted separately), so a future
 * account/multi-user release is just a matter of scoping.
 *
 * Wire contract (mirrors the official mem0 API):
 *   POST /v1/memories/    body {"messages":[{"role","content"}],"user_id"}
 *   GET  /v1/memories?user_id=   -> {"results":[{"id","memory","event",...}]}
 *   GET  /health                -> 200 when reachable
 */
class Mem0MetaMemoryConnector(
    private val baseUrl: String,
    private val userId: String,
    engine: HttpClientEngine = Android.create(),
) : MetaMemoryConnector {

    private val client = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /** No assumption: linked only when the service actually answers /health. */
    override suspend fun isAvailable(): Boolean {
        return try {
            if (baseUrl.isBlank()) false else client.get("$baseUrl/health").status.isSuccess()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Mirror local memories into the semantic store. The service re-extracts
     * facts with its own LLM, so the whole checkpoint is pushed verbatim.
     * Returns how many were accepted.
     */
    override suspend fun push(memories: List<MemoryEntity>): Result<Int> = runCatching {
        if (baseUrl.isBlank()) throw IllegalStateException("No semantic memory server configured")
        if (memories.isEmpty()) return@runCatching 0
        var synced = 0
        // mem0 batches its own extraction; chunk to keep a single request polite.
        memories.chunked(8).forEach { chunk ->
            val response = client.post("$baseUrl/v1/memories/") {
                contentType(ContentType.Application.Json)
                setBody(
                    Mem0AddRequest(
                        messages = chunk.map { Mem0Message(role = "user", content = it.content) },
                        userId = userId,
                    )
                )
            }
            if (response.status.isSuccess()) synced += chunk.size
        }
        synced
    }

    /**
     * Fetch remote semantic memories for this user as local entities.
     * Callers decide whether/how to merge — this connector never writes Room.
     */
    override suspend fun pull(): Result<List<MemoryEntity>> = runCatching {
        if (baseUrl.isBlank()) throw IllegalStateException("No semantic memory server configured")
        val response = client.get("$baseUrl/v1/memories") {
            url { parameters.append("user_id", userId) }
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Semantic memory server HTTP ${response.status.value}")
        }
        val body = Json { ignoreUnknownKeys = true }
            .decodeFromString<Mem0GetAllResponse>(response.bodyAsText())
        body.results.map { remote ->
            MemoryEntity(
                content = remote.memory,
                timestamp = remote.createdAtEpochMillis() ?: System.currentTimeMillis(),
                type = MemoryType.SEMANTIC,
                importance = ImportanceLevel.MEDIUM,
                tags = listOf("mem0"),
            )
        }
    }
}

@Serializable
internal data class Mem0Message(
    val role: String,
    val content: String,
)

@Serializable
internal data class Mem0AddRequest(
    val messages: List<Mem0Message>,
    @SerialName("user_id") val userId: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
internal data class Mem0RemoteMemory(
    val id: String,
    val memory: String,
    val event: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun createdAtEpochMillis(): Long? {
        val raw = createdAt ?: return null
        // mem0 emits "2026-08-06 05:22:23.123456" (space) or ISO with T/Z.
        return runCatching { java.time.Instant.parse(raw).toEpochMilli() }
            .recoverCatching {
                java.time.LocalDateTime.parse(raw.trim().replace(' ', 'T'))
                    .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }
            .getOrNull()
    }
}

@Serializable
internal data class Mem0GetAllResponse(
    val results: List<Mem0RemoteMemory> = emptyList(),
)
