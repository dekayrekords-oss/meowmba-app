package com.example.meowmba.core.vision

/**
 * Six degrees of freedom from one face frame: translation (x, y), distance
 * (scale), and rotation (yaw, pitch, roll). Pure mapping + normalization so
 * the renderer and the tests share the exact same math.
 */
data class FacePose6Dof(
    val offsetX: Float,
    val offsetY: Float,
    /** Head turn left/right, degrees. + = toward the user's right (screen). */
    val yawDeg: Float = 0f,
    /** Head nod up/down, degrees. + = looking up. */
    val pitchDeg: Float = 0f,
    /** Head tilt, degrees. + = clockwise as the viewer sees it. */
    val rollDeg: Float = 0f,
    /** Distance proxy: 1.0 at baseline face size; >1 closer, <1 farther. */
    val scale: Float = 1f,
)

/** Normalize ML Kit head euler angles (-30..30°) to -1..1 for the renderer. */
object FacePose {
    const val YAW_RANGE_DEG = 30f
    const val PITCH_RANGE_DEG = 30f
    const val ROLL_RANGE_DEG = 30f

    /** A face about a third of the frame width is "at rest" distance. */
    const val BASELINE_FACE_FRACTION = 0.35f

    /** How strongly a distance change translates to on-screen head size. */
    const val SIZE_GAIN = 0.35f

    fun normalizedYaw(yawDeg: Float): Float = (yawDeg / YAW_RANGE_DEG).coerceIn(-1f, 1f)

    fun normalizedPitch(pitchDeg: Float): Float = (pitchDeg / PITCH_RANGE_DEG).coerceIn(-1f, 1f)

    fun normalizedRoll(rollDeg: Float): Float = (rollDeg / ROLL_RANGE_DEG).coerceIn(-1f, 1f)

    /** Distance proxy from the largest face's width as a fraction of the frame. */
    fun scaleFromFaceFraction(faceWidth: Float, frameWidth: Float): Float {
        if (faceWidth <= 0f || frameWidth <= 0f) return 1f
        return (faceWidth / frameWidth / BASELINE_FACE_FRACTION).coerceIn(0.6f, 1.5f)
    }

    /** On-screen head size factor for a [scale] distance reading. */
    fun headSizeFactor(scale: Float): Float =
        (1f + (scale - 1f) * SIZE_GAIN).coerceIn(0.85f, 1.15f)
}
