package com.example.meowmba.core.media

import android.graphics.Bitmap

/**
 * A backend that turns a text prompt into an image. Implemented by
 * [OnDeviceImageGenerator] (on-device, default) and `RemoteImageGenerator`
 * (opt-in, only constructed when a server URL is configured) so
 * `MediaGenerateTool` can route between them without knowing which is which.
 */
interface ImageGenerator {
    /** Cheap, synchronous check — no network calls. Actual reachability is
     *  discovered by attempting [generate] and letting it fail honestly. */
    fun isAvailable(): Boolean

    suspend fun generate(
        prompt: String,
        iterations: Int = 20,
        seed: Int = 0
    ): Result<Bitmap>
}
