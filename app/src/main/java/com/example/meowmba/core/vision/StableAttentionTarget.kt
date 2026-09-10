package com.example.meowmba.core.vision

/**
 * Keeps attention on one visible person through box jitter and short occlusion.
 * This is a session-local visual target, never a claim of identity or speaker recognition.
 */
class StableAttentionTarget {
    data class Selection(val face: SeenFace?, val changed: Boolean)

    private var selected: SeenFace? = null
    private var lastSeenAtMs = -1L
    private var challenger: SeenFace? = null
    private var challengerSinceMs = -1L
    private var lastSampleAtMs = -1L

    fun reset() {
        selected = null
        lastSeenAtMs = -1L
        challenger = null
        challengerSinceMs = -1L
        lastSampleAtMs = -1L
    }

    fun select(faces: List<SeenFace>, speechLikely: Boolean, nowMs: Long): Selection {
        // A gap cannot count as sustained evidence for a challenger.
        if (lastSampleAtMs >= 0L &&
            (nowMs < lastSampleAtMs || nowMs - lastSampleAtMs >= AttentionFreshness.STALE_AFTER_MS)
        ) clearChallenger()
        lastSampleAtMs = nowMs
        val valid = faces.filter {
            it.offsetX.isFinite() && it.offsetY.isFinite() && it.area.isFinite() && it.area > 0f
        }
        val previous = selected
        val current = previous?.let { old ->
            valid.firstOrNull { old.trackingId >= 0 && it.trackingId == old.trackingId }
                ?: valid.filter { sameTarget(old, it) }.minByOrNull { distanceSquared(old, it) }
        }
        if (current == null) {
            clearChallenger()
            // No stale face is returned during the grace period, so motors stop immediately.
            if (previous != null && nowMs >= lastSeenAtMs && nowMs - lastSeenAtMs < LOSS_GRACE_MS) {
                return Selection(null, changed = false)
            }
            val next = valid.maxByOrNull { it.area }
            selected = next
            if (next != null) lastSeenAtMs = nowMs
            return Selection(next, changed = next != null)
        }

        selected = current
        lastSeenAtMs = nowMs
        val rankedMouth = if (speechLikely) valid.sortedByDescending { it.mouthOpen } else emptyList()
        val talker = rankedMouth.firstOrNull()?.takeIf { top ->
            top.mouthOpen >= TalkerPicker.MOUTH_TALKING &&
                top.mouthOpen >= (rankedMouth.getOrNull(1)?.mouthOpen ?: 0f) + TalkerPicker.MOUTH_LEAD
        }
        val next = talker ?: valid.maxByOrNull { it.area }?.takeIf { it.area >= current.area * AREA_LEAD }
        if (next == null || sameTarget(current, next)) {
            clearChallenger()
            return Selection(current, changed = false)
        }
        if (challenger?.let { sameTarget(it, next) } != true) {
            challenger = next
            challengerSinceMs = nowMs
        } else {
            challenger = next
        }
        if (nowMs - challengerSinceMs < SWITCH_SUSTAIN_MS) return Selection(current, changed = false)
        selected = next
        clearChallenger()
        return Selection(next, changed = true)
    }

    private fun clearChallenger() {
        challenger = null
        challengerSinceMs = -1L
    }

    private fun sameTarget(a: SeenFace, b: SeenFace): Boolean {
        if (a.trackingId >= 0 && b.trackingId >= 0) return a.trackingId == b.trackingId
        // Missing detector IDs may follow nearby boxes, but never match every -1 as one person.
        val ratio = b.area / a.area
        return ratio in 0.5f..2f && distanceSquared(a, b) <= UNKNOWN_MAX_DISTANCE * UNKNOWN_MAX_DISTANCE
    }

    private fun distanceSquared(a: SeenFace, b: SeenFace): Float =
        (a.offsetX - b.offsetX) * (a.offsetX - b.offsetX) +
            (a.offsetY - b.offsetY) * (a.offsetY - b.offsetY)

    companion object {
        const val LOSS_GRACE_MS = 400L
        const val SWITCH_SUSTAIN_MS = 650L
        const val AREA_LEAD = 1.35f
        const val UNKNOWN_MAX_DISTANCE = 0.35f
    }
}
