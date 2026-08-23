package com.example.meowmba.core.vision

/**
 * One face in the current camera frame, already in screen space.
 *
 * [mouthOpen] is lip-to-chin over face height (0 = closed, ~0.15 talking).
 */
data class SeenFace(
    val trackingId: Int,
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val mouthOpen: Float,
    val area: Float = scale,
)

data class TalkerPick(
    val face: SeenFace?,
    val reason: Reason,
) {
    enum class Reason { MOUTH, HOLD, LARGEST, NONE }
}

/**
 * Which face to look at when more than one person is in frame.
 *
 * Pure so two-person turn-taking is pinned by tests, not by whichever box
 * happened to be larger that frame.
 */
object TalkerPicker {

    /** Mouth opening that counts as talking, not a resting smile. */
    const val MOUTH_TALKING = 0.07f

    /** Winner must beat the next face by this much or we do not switch. */
    const val MOUTH_LEAD = 0.03f

    /** Keep looking at the last talker this long after their mouth closes. */
    const val HOLD_MS = 900L

    fun mouthOpenRatio(
        mouthLeftY: Float,
        mouthRightY: Float,
        mouthBottomY: Float,
        faceHeight: Float,
    ): Float {
        if (faceHeight <= 1f) return 0f
        val lip = (mouthLeftY + mouthRightY) * 0.5f
        return ((mouthBottomY - lip) / faceHeight).coerceIn(0f, 1f)
    }

    fun pick(
        faces: List<SeenFace>,
        speechLikely: Boolean,
        lastId: Int?,
        nowMs: Long,
        lastAtMs: Long,
    ): TalkerPick {
        if (faces.isEmpty()) return TalkerPick(null, TalkerPick.Reason.NONE)
        if (faces.size == 1) return TalkerPick(faces[0], TalkerPick.Reason.LARGEST)
        if (speechLikely) {
            val ranked = faces.sortedByDescending { it.mouthOpen }
            val top = ranked[0]
            val second = ranked[1]
            if (top.mouthOpen >= MOUTH_TALKING &&
                top.mouthOpen >= second.mouthOpen + MOUTH_LEAD
            ) {
                return TalkerPick(top, TalkerPick.Reason.MOUTH)
            }
        }
        val held = lastId?.let { id -> faces.firstOrNull { it.trackingId == id } }
        if (held != null && lastAtMs > 0L && nowMs - lastAtMs <= HOLD_MS) {
            return TalkerPick(held, TalkerPick.Reason.HOLD)
        }
        return TalkerPick(faces.maxBy { it.scale }, TalkerPick.Reason.LARGEST)
    }

    /** Session label when we do not yet have a voice-print name. */
    fun slotLabel(face: SeenFace, faces: List<SeenFace>): String? {
        if (faces.size < 2) return null
        val ordered = faces.sortedBy { it.offsetX }
        val i = ordered.indexOfFirst { it.trackingId == face.trackingId }
        if (i < 0) return null
        return when (ordered.size) {
            2 -> if (i == 0) "Left" else "Right"
            else -> when (i) {
                0 -> "Left"
                ordered.lastIndex -> "Right"
                else -> "Middle"
            }
        }
    }
}
