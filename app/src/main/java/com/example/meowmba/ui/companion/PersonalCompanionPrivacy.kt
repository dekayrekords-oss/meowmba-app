package com.example.meowmba.ui.companion

import com.example.meowmba.core.security.SecureStorageManager
import com.example.meowmba.core.vision.FaceTrack
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Only opt-in settings are shared with the companion. Private answers never enter chat/memory/tools. */
internal object PersonalCompanionPrivacy {
    val reactions = MutableStateFlow(false)
    fun restore(storage: SecureStorageManager?) { reactions.value = storage?.getBooleanPreference("personal_reactions", false) ?: false }
    fun enable(storage: SecureStorageManager, value: Boolean) {
        storage.saveBooleanPreference("personal_reactions", value); reactions.value = value
    }
}

internal class PrivateProfileStore(private val storage: SecureStorageManager) {
    companion object {
        private const val KEY = "private_interview_v1"
        val questions = listOf("What name would you like me to use?", "What activities do you enjoy?",
            "Do you prefer quiet company or playful reactions?", "What helps you focus while working?",
            "What topics would you rather I avoid?", "What kind of creative project interests you?",
            "When would you prefer not to be interrupted?")
    }
    fun answers(): Map<String, String> = runCatching {
        val json = JSONObject(storage.getPreference(KEY).orEmpty().ifBlank { "{}" })
        questions.mapNotNull { q -> json.optString(q).takeIf { it.isNotBlank() }?.let { q to it } }.toMap()
    }.getOrDefault(emptyMap())
    fun save(question: String, answer: String) {
        require(question in questions && answer.length <= 1500)
        val updated = answers().toMutableMap()
        if (answer.isBlank()) updated.remove(question) else updated[question] = answer.trim()
        storage.savePreference(KEY, JSONObject(updated as Map<*, *>).toString())
    }
    fun deleteAll() { storage.savePreference(KEY, "{}") }
}

/** Session-only two-step approval bound to exact text; edits, cancel and completion revoke it. */
internal class PrivateCloudApproval {
    private var selected: String? = null
    var step = 0; private set
    fun begin(text: String) { require(text.isNotBlank()); selected = text; step = 1 }
    fun acknowledge(text: String) { check(step == 1 && selected == text); step = 2 }
    fun consume(text: String): String {
        check(step == 2 && selected == text) { "Approve the exact text twice before sending." }
        cancel(); return text
    }
    fun cancel() { selected = null; step = 0 }
}

/** Observable movement only, not an emotion/diagnosis classifier. No video/audio/history retained. */
internal class PersonalReactionDirector {
    private var previousX: Float? = null
    private var direction = 0
    private var turns = 0
    private var windowAt = 0L
    private var lastReaction = -30_000L
    fun reset() { previousX = null; turns = 0; direction = 0; windowAt = 0L }
    fun sample(track: FaceTrack?, now: Long, soundActive: Boolean): String? {
        if (track == null || !track.hasFace || track.faceCount != 1 || track.confidence < .7f ||
            now < track.observedAtElapsedMs || now - track.observedAtElapsedMs > 800) { reset(); return null }
        if (now - windowAt > 5_000) { turns = 0; windowAt = now }
        val delta = track.frameX - (previousX ?: track.frameX)
        if (kotlin.math.abs(delta) > .055f) {
            val next = if (delta > 0) 1 else -1
            if (direction != 0 && direction != next) turns++
            direction = next; previousX = track.frameX
        } else if (previousX == null) previousX = track.frameX
        // Require several reversals: one quick movement is not "dancing" or distress.
        if (turns >= (if (soundActive) 3 else 4) && now - lastReaction >= 18_000) {
            lastReaction = now; turns = 0
            return "happy bop"
        }
        return null
    }
}
