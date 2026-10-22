package com.example.meowmba.ui.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** A swipe dismisses once it has travelled a third of the width, or was flung. */
internal fun shouldDismissSwipe(offsetPx: Float, velocityPxPerSec: Float, widthPx: Int): Boolean {
    if (widthPx <= 0) return false
    val far = abs(offsetPx) >= widthPx * DISMISS_FRACTION
    // A fling counts only when it points the same way as the drag, so a quick flick back
    // toward the start is never treated as a dismissal.
    val flung = abs(velocityPxPerSec) >= DISMISS_VELOCITY && offsetPx != 0f &&
        (velocityPxPerSec > 0f) == (offsetPx > 0f)
    return far || flung
}

private const val DISMISS_FRACTION = 0.35f
private const val DISMISS_VELOCITY = 1_200f

/**
 * Lets a notice be swiped left or right off the screen. TalkBack gets the same thing as a
 * "Dismiss" action, since a two-finger swipe on a small banner is not a fair ask.
 */
@Composable
internal fun SwipeAway(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offset.value) / (width.coerceAtLeast(1) * 0.9f)).coerceIn(0f, 1f) }
            .semantics { customActions = listOf(CustomAccessibilityAction("Dismiss") { dismiss(); true }) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                onDragStopped = { velocity ->
                    if (shouldDismissSwipe(offset.value, velocity, width)) {
                        val side = if (offset.value != 0f) Math.signum(offset.value) else Math.signum(velocity)
                        offset.animateTo(side * width * 1.2f)
                        dismiss()
                    } else offset.animateTo(0f)
                },
            ),
    ) { content() }
}
