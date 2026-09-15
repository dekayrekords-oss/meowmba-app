package com.example.meowmba.ui.companion

import org.json.JSONArray
import org.json.JSONObject

/**
 * Launch-frequency tracking for the app drawer "most used" panel.
 * Pure JSON helpers — persistence lives in CompanionViewModel prefs.
 */
object DrawerUsage {
    /** Cap of the frequent panel (~two adaptive rows on a phone). */
    const val FREQUENT_LIMIT = 8

    data class Snapshot(
        val launches: Map<String, Int> = emptyMap(),
        /** Packages the user asked never to promote into the frequent panel. */
        val neverFrequent: Set<String> = emptySet(),
        /** Soft removals from the frequent list until launches rise again. */
        val hiddenFrequent: Set<String> = emptySet(),
    )

    fun recordLaunch(snapshot: Snapshot, packageName: String): Snapshot {
        if (packageName.isBlank()) return snapshot
        val next = snapshot.launches.toMutableMap()
        next[packageName] = (next[packageName] ?: 0) + 1
        // Launching again re-admits a soft-hidden app.
        val hidden = snapshot.hiddenFrequent - packageName
        return snapshot.copy(launches = next, hiddenFrequent = hidden)
    }

    fun hideFromFrequent(snapshot: Snapshot, packageName: String): Snapshot =
        snapshot.copy(hiddenFrequent = snapshot.hiddenFrequent + packageName)

    fun setNeverFrequent(snapshot: Snapshot, packageName: String, never: Boolean): Snapshot {
        val neverSet = if (never) snapshot.neverFrequent + packageName else snapshot.neverFrequent - packageName
        val hidden = if (never) snapshot.hiddenFrequent + packageName else snapshot.hiddenFrequent
        return snapshot.copy(neverFrequent = neverSet, hiddenFrequent = hidden)
    }

    fun allowFrequent(snapshot: Snapshot, packageName: String): Snapshot =
        snapshot.copy(
            neverFrequent = snapshot.neverFrequent - packageName,
            hiddenFrequent = snapshot.hiddenFrequent - packageName,
        )

    /**
     * Most-launched apps that are still installed, not banned from frequent,
     * and not soft-hidden. Favorites may also appear here; the UI can dedupe.
     */
    fun frequentPackages(snapshot: Snapshot, installed: Set<String>, limit: Int = FREQUENT_LIMIT): List<String> =
        snapshot.launches.entries
            .asSequence()
            .filter { (pkg, count) ->
                count > 0 &&
                    pkg in installed &&
                    pkg !in snapshot.neverFrequent &&
                    pkg !in snapshot.hiddenFrequent
            }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(limit)
            .toList()

    fun encode(snapshot: Snapshot): String = JSONObject().apply {
        put("launches", JSONObject().apply { snapshot.launches.forEach { (k, v) -> put(k, v) } })
        put("never", JSONArray(snapshot.neverFrequent.toList().sorted()))
        put("hidden", JSONArray(snapshot.hiddenFrequent.toList().sorted()))
    }.toString()

    fun parse(raw: String?): Snapshot {
        if (raw.isNullOrBlank()) return Snapshot()
        return try {
            val o = JSONObject(raw)
            val launchesObj = o.optJSONObject("launches")
            val launches = buildMap {
                if (launchesObj != null) {
                    launchesObj.keys().forEach { key ->
                        val n = launchesObj.optInt(key, 0)
                        if (n > 0) put(key, n)
                    }
                }
            }
            fun stringSet(field: String): Set<String> {
                val arr = o.optJSONArray(field) ?: return emptySet()
                return buildSet {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            Snapshot(launches = launches, neverFrequent = stringSet("never"), hiddenFrequent = stringSet("hidden"))
        } catch (_: Exception) {
            Snapshot()
        }
    }
}
