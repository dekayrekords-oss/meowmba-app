package com.example.meowmba.ui.companion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Animation identifiers only: no conversation, person, sensor or device data. */
data class PerformanceHistoryEntry(val id: String, val family: String, val playedAtMs: Long)

/**
 * Small restart checkpoint for the idle director. Times are wall-clock milliseconds;
 * elapsedRealtime belongs only to the running selector and is never serialized.
 * An interrupted selection still counts as recent, so relaunch cannot repeat its entrance.
 */
data class PerformanceHistory(
    val savedAtMs: Long = 0L,
    val recent: List<PerformanceHistoryEntry> = emptyList(),
    val lastScene: PerformanceHistoryEntry? = null,
    val remainingSceneIds: List<String> = emptyList(),
) {
    /** Always bounds and validates public constructor input before writing it. */
    fun toJson(): String {
        val history = validated(savedAtMs)
        return buildJsonObject {
            put("version", VERSION)
            put("savedAtMs", history.savedAtMs)
            put("recent", buildJsonArray { history.recent.forEach { add(it.toJson()) } })
            history.lastScene?.let { put("lastScene", it.toJson()) }
            put("remainingSceneIds", buildJsonArray { history.remainingSceneIds.forEach { add(JsonPrimitive(it)) } })
        }.toString()
    }

    internal fun validated(nowMs: Long): PerformanceHistory {
        if (nowMs < 0L || savedAtMs <= 0L || savedAtMs > nowMs || nowMs - savedAtMs > MAX_AGE_MS)
            return PerformanceHistory()
        val known = AvatarPerformanceCatalog.entries.associateBy { it.id }
        fun valid(entry: PerformanceHistoryEntry): PerformanceHistoryEntry? {
            val performance = known[entry.id] ?: return null
            if (entry.playedAtMs <= 0L || entry.playedAtMs > savedAtMs || nowMs - entry.playedAtMs > MAX_AGE_MS)
                return null
            // Family names belong to the current catalog, never to unchecked saved JSON.
            return entry.copy(family = performance.family)
        }
        val scene = lastScene?.let(::valid)?.takeIf { known[it.id]?.category == "Scenes" }
        return PerformanceHistory(
            savedAtMs = savedAtMs,
            recent = recent.takeLast(MAX_RECENT).mapNotNull(::valid).sortedBy { it.playedAtMs },
            lastScene = scene,
            remainingSceneIds = remainingSceneIds.take(MAX_SCENE_BAG).distinct()
                .filter { known[it]?.category == "Scenes" && it != scene?.id },
        )
    }

    companion object {
        const val VERSION = 1
        const val MAX_RECENT = 12
        const val MAX_SCENE_BAG = 128
        const val MAX_JSON_CHARS = 16_384
        const val MAX_AGE_MS = 24 * 60 * 60_000L

        /** Unknown schemas, invalid timestamps and corrupt preferences never block idle. */
        fun fromJson(raw: String?, nowMs: Long = System.currentTimeMillis()): PerformanceHistory {
            if (raw.isNullOrBlank() || raw.length > MAX_JSON_CHARS) return PerformanceHistory()
            return runCatching {
                val root = Json.parseToJsonElement(raw) as? JsonObject ?: return PerformanceHistory()
                if ((root["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull != VERSION)
                    return PerformanceHistory()
                PerformanceHistory(
                    savedAtMs = root.number("savedAtMs") ?: return PerformanceHistory(),
                    recent = (root["recent"] as? JsonArray)?.takeLast(MAX_RECENT)
                        ?.mapNotNull { (it as? JsonObject)?.entry() }.orEmpty(),
                    lastScene = (root["lastScene"] as? JsonObject)?.entry(),
                    remainingSceneIds = (root["remainingSceneIds"] as? JsonArray)?.take(MAX_SCENE_BAG)
                        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty(),
                ).validated(nowMs)
            }.getOrDefault(PerformanceHistory())
        }

        private fun JsonObject.number(key: String): Long? =
            (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull

        private fun JsonObject.entry(): PerformanceHistoryEntry? {
            val id = (this["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val at = number("playedAtMs") ?: return null
            return PerformanceHistoryEntry(id, "", at)
        }

        private fun PerformanceHistoryEntry.toJson() = buildJsonObject {
            put("id", id)
            put("family", family)
            put("playedAtMs", playedAtMs)
        }
    }
}
