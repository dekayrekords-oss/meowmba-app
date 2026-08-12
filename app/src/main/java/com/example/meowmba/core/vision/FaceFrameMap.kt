package com.example.meowmba.core.vision

/**
 * Maps a face position from the camera's upright frame into the DISPLAY frame.
 *
 * ML Kit reports bounding boxes in the camera's upright frame, which follows the
 * SENSOR rotation — it does not change when the phone rotates to landscape, but
 * the display does. Without this remap, holding the phone horizontally reads a
 * user's left/right head motion as up/down on the avatar. Portrait is identity,
 * so normal use is unaffected. Pure and unit-tested.
 */
object FaceFrameMap {

    /**
     * @param fxU normalized upright-frame x (0..1, left to right)
     * @param fyU normalized upright-frame y (0..1, top to bottom)
     * @param displayDeg display rotation in degrees: 0, 90, 180, or 270
     * @return (sx, sy) normalized display-space position (0..1)
     */
    fun toDisplay(fxU: Float, fyU: Float, displayDeg: Int): Pair<Float, Float> =
        when (displayDeg % 360) {
            90 -> Pair(1f - fyU, fxU)
            270 -> Pair(fyU, 1f - fxU)
            180 -> Pair(1f - fxU, 1f - fyU)
            else -> Pair(fxU, fyU)
        }
}
