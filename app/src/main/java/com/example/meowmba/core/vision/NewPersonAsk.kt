package com.example.meowmba.core.vision

/**
 * When a new face shows up that we do not have a name for, Meow should ask —
 * once, after they have actually stayed in frame.
 */
object NewPersonAsk {

    /** Face must stay visible this long so a flicker is not a guest. */
    const val SETTLE_MS = 1_800L

    /** Do not stack two introductions back to back. */
    const val ASK_COOLDOWN_MS = 12_000L

    /** How long we treat the next utterance as their name. */
    const val AWAIT_MS = 25_000L

    /**
     * A second box this small vs the largest face is a reflection / phantom,
     * not another person sitting next to the phone.
     */
    const val GHOST_AREA_RATIO = 0.35f

    /**
     * Newest unnamed, settled face, or null if we should stay quiet.
     *
     * Needs two real faces: the person already using the phone is not "new".
     * Camera tracking ids reset on every rebind, so a known owner plus fresh
     * ids is not a stranger — and one unanswered intro is enough per session
     * until a name is actually bound.
     */
    fun pickFaceToAsk(
        faceIds: List<Int>,
        namedIds: Set<Int>,
        askedIds: Set<Int>,
        firstSeenAt: Map<Int, Long>,
        nowMs: Long,
        lastAskAtMs: Long,
        busy: Boolean,
        alreadyAwaiting: Boolean,
        hasKnownPeople: Boolean = false,
        faceAreas: Map<Int, Float> = emptyMap(),
    ): Int? {
        if (busy || alreadyAwaiting) return null
        val real = realFaceIds(faceIds, faceAreas)
        if (real.size < 2) return null
        if (lastAskAtMs > 0L && nowMs - lastAskAtMs < ASK_COOLDOWN_MS) return null
        if (namedIds.none { it in real }) {
            if (hasKnownPeople) return null
            if (lastAskAtMs > 0L) return null
        }
        val candidates = real.filter { id ->
            id !in namedIds &&
                id !in askedIds &&
                nowMs - (firstSeenAt[id] ?: nowMs) >= SETTLE_MS
        }
        return candidates.maxByOrNull { firstSeenAt[it] ?: 0L }
    }

    /**
     * After ML Kit issues new tracking ids, pin the persisted owner to the
     * largest face so we do not treat them as a stranger.
     */
    fun rebindOwner(
        faceIds: List<Int>,
        namedIds: Set<Int>,
        ownerName: String?,
        faceAreas: Map<Int, Float> = emptyMap(),
    ): Pair<Int, String>? {
        val name = ownerName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val real = realFaceIds(faceIds, faceAreas)
        if (real.isEmpty()) return null
        if (real.any { it in namedIds }) return null
        val primary = real.maxByOrNull { faceAreas[it] ?: 0f } ?: real.first()
        return primary to name
    }

    fun realFaceIds(
        faceIds: List<Int>,
        faceAreas: Map<Int, Float>,
    ): List<Int> {
        val ids = faceIds.filter { it >= 0 }
        if (ids.isEmpty() || faceAreas.isEmpty()) return ids
        val largest = ids.maxOf { faceAreas[it] ?: 0f }
        if (largest <= 0f) return ids
        return ids.filter { (faceAreas[it] ?: largest) >= largest * GHOST_AREA_RATIO }
    }

    fun declined(raw: String): Boolean {
        val t = raw.lowercase()
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return t in DECLINED
    }

    private val DECLINED = setOf(
        "no", "nope", "nah", "later", "not now", "never mind", "nevermind",
        "skip", "don't", "dont", "no thanks",
    )
}
