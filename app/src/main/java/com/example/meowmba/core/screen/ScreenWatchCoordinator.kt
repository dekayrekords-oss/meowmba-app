package com.example.meowmba.core.screen

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the screen-watch pipeline publishes what it sees. The ContextDirector
 * subscribes here; the capture service pushes downscaled frames.
 */
object ScreenWatchCoordinator {
    data class Frame(val bitmap: Bitmap, val capturedAt: Long)

    private val _latest = MutableStateFlow<Frame?>(null)
    val latest: StateFlow<Frame?> = _latest.asStateFlow()

    fun postFrame(bitmap: Bitmap) {
        _latest.value?.bitmap?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
        _latest.value = Frame(bitmap, System.currentTimeMillis())
    }
}
