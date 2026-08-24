package com.example.meowmba

import com.example.meowmba.core.alerts.RoadAlert
import com.example.meowmba.core.alerts.RoadAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadAlertTest {

    @Test
    fun `waze police report becomes a face alert`() {
        val a = RoadAlerts.fromNotification(
            "com.waze",
            "Police reported",
            "Ahead on Main St",
        )!!
        assertEquals(RoadAlert.Kind.POLICE, a.kind)
        assertEquals("Waze", a.source)
    }

    @Test
    fun `maps speed camera is a camera alert`() {
        val a = RoadAlerts.fromNotification(
            "com.google.android.apps.maps",
            "Speed camera",
            null,
        )!!
        assertEquals(RoadAlert.Kind.CAMERA, a.kind)
    }

    @Test
    fun `unrelated notifications are ignored`() {
        assertNull(RoadAlerts.fromNotification("com.spotify.music", "Now playing", "song"))
        assertNull(RoadAlerts.fromNotification("com.waze", "Traffic", "slowdown ahead"))
    }
}
