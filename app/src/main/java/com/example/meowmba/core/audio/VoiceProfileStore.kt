package com.example.meowmba.core.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence for enrolled voice prints.
 *
 * sherpa's `SpeakerEmbeddingManager` is memory-only — it forgets everything on process
 * death — so the embeddings themselves are stored here and re-added to a fresh manager on
 * each start.
 *
 * An embedding is a few hundred floats and carries no recoverable audio, but it IS
 * biometric-adjacent: it identifies a person. It therefore never leaves the device, and
 * nothing here touches the network.
 */
class VoiceProfileStore(private val context: Context) {

    private val prefs by lazy {
        context.applicationContext
            .getSharedPreferences("voice_profiles", Context.MODE_PRIVATE)
    }

    data class Profile(
        val name: String,
        /** One embedding per enrolment sample; more samples means a more robust print. */
        val embeddings: List<FloatArray>,
    ) {
        val sampleCount: Int get() = embeddings.size
    }

    /** Everything enrolled, sorted by name. */
    fun all(): List<Profile> = decode(prefs.getString(KEY, null)).sortedBy { it.name }

    fun isEmpty(): Boolean = all().isEmpty()

    /** Appends one enrolment sample to [name], creating the profile if needed. */
    fun addSample(name: String, embedding: FloatArray) {
        val clean = name.trim().lowercase()
        if (clean.isBlank() || embedding.isEmpty()) return
        val existing = all()
        val updated = existing.filterNot { it.name == clean } +
            Profile(
                clean,
                (existing.firstOrNull { it.name == clean }?.embeddings ?: emptyList()) +
                    listOf(embedding),
            )
        write(updated)
    }

    fun delete(name: String) {
        val clean = name.trim().lowercase()
        write(all().filterNot { it.name == clean })
    }

    fun clear() = write(emptyList())

    private fun write(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            val embs = JSONArray()
            p.embeddings.forEach { e ->
                val one = JSONArray()
                e.forEach { one.put(it.toDouble()) }
                embs.put(one)
            }
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("embeddings", embs)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun decode(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val embs = o.optJSONArray("embeddings") ?: return@mapNotNull null
                val list = (0 until embs.length()).mapNotNull { j ->
                    embs.optJSONArray(j)?.let { one ->
                        FloatArray(one.length()) { k -> one.optDouble(k).toFloat() }
                    }?.takeIf { it.isNotEmpty() }
                }
                if (list.isEmpty()) null else Profile(name, list)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY = "profiles"

        /** The profile name used for the phone's owner. */
        const val OWNER = "owner"

        /**
         * Cosine-similarity floor for accepting a wake as the enrolled speaker.
         *
         * 0.5 is deliberately forgiving. A false REJECT is much worse here than a false
         * accept: it looks exactly like the wake word being broken, which is the bug this
         * whole thread has been about. Verification is a second gate on top of the
         * keyword, not the primary defence.
         */
        const val DEFAULT_THRESHOLD = 0.5f
    }
}
