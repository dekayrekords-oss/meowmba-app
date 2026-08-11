package com.example.meowmba.core.agent.prime

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable home for [HarnessState].
 *
 * Plain JSON in filesDir rather than a Room table: this is a single small
 * document rewritten whole, it must survive a schema change without a
 * migration, and — the reason that matters here — the user has to be able to
 * read and delete what the companion decided about itself. A file they can
 * open is a stronger honesty guarantee than a table they cannot.
 */
class HarnessStore(private val file: File) {

    /** Never throws: a corrupt or absent file means "nothing learned yet". */
    fun load(): HarnessState = runCatching {
        if (!file.exists()) return HarnessState()
        val root = JSONObject(file.readText())
        val lessons = root.optJSONArray("lessons")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    Lesson(
                        id = o.getString("id"),
                        text = o.getString("text"),
                        evidence = o.optString("evidence"),
                        createdAt = o.optLong("createdAt"),
                        lastSeenAt = o.optLong("lastSeenAt", o.optLong("createdAt")),
                        confirmations = o.optInt("confirmations", 1),
                    )
                }.getOrNull()
            }
        } ?: emptyList()
        val goal = root.optJSONObject("goal")?.let { o ->
            Goal(
                text = o.getString("text"),
                createdAt = o.optLong("createdAt"),
                progressNotes = o.optJSONArray("notes")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
            )
        }
        // Re-filter on load: a lesson could only have become unsafe by the file
        // being edited, and that is exactly when it must not be trusted.
        HarnessState(lessons.filter { it.isSafe }, goal)
    }.getOrDefault(HarnessState())

    fun save(state: HarnessState): Boolean = runCatching {
        val root = JSONObject()
        root.put(
            "lessons",
            JSONArray().apply {
                state.lessons.filter { it.isSafe }.forEach { l ->
                    put(
                        JSONObject()
                            .put("id", l.id)
                            .put("text", l.text)
                            .put("evidence", l.evidence)
                            .put("createdAt", l.createdAt)
                            .put("lastSeenAt", l.lastSeenAt)
                            .put("confirmations", l.confirmations),
                    )
                }
            },
        )
        state.goal?.let { g ->
            root.put(
                "goal",
                JSONObject()
                    .put("text", g.text)
                    .put("createdAt", g.createdAt)
                    .put("notes", JSONArray().apply { g.progressNotes.forEach { put(it) } }),
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
        true
    }.getOrDefault(false)

    /** The user's off switch. Everything learned, gone. */
    fun clear(): Boolean = runCatching { file.delete(); true }.getOrDefault(false)
}
