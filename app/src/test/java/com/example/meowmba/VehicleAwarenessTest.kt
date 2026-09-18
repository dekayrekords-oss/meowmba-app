package com.example.meowmba

import com.example.meowmba.core.car.VehicleAwareness
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleAwarenessTest {
    @Test
    fun carPlayRemoteAlwaysFalseOnAndroidPath() {
        val id = VehicleAwareness.fromBluetooth("My Car", "AA:BB", true, hasAndroidAutoFeature = true)
        assertFalse(id.carPlayRemoteControlAvailable)
        assertTrue(id.androidAutoLikely)
        assertTrue(VehicleAwareness.summary(id).contains("CarPlay remote"))
        assertTrue(id.notes.contains("CarPlay"))
    }
}
