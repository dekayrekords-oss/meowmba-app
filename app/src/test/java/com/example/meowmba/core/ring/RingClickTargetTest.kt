package com.example.meowmba.core.ring

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingClickTargetTest {
    @Test fun overlayPositionStaysVisibleAndCanBeRemovedBeforeTouchInjection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val target = RingClickTarget(context)
        assertTrue(target.show()); assertTrue(target.visible)
        val center = target.coordinates()
        assertEquals(bounds.width() / 2f, center.first, 1f)
        assertEquals(bounds.height() / 2f, center.second, 1f)
        repeat(100) { target.move(RingAction.POINTER_LEFT); target.move(RingAction.POINTER_DOWN) }
        val corner = target.coordinates()
        assertTrue(corner.first > 0 && corner.first < center.first)
        assertTrue(corner.second < bounds.height() && corner.second > center.second)
        target.hide(); assertFalse(target.visible)
        target.center(); assertEquals(RingPoint(), target.point)
    }
}
