package com.example.meowmba.core.ring

import org.junit.Test
import org.junit.Assert.*

class SmartRingTest {
    @Test fun controlPacketsMatchDocumentedR08Mode() {
        assertArrayEquals(byteArrayOf(0x3b, 2, 0, 1, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x43), ringPacket(0))
        assertArrayEquals(byteArrayOf(0x3b, 2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x3e), ringPacket(1))
        assertArrayEquals(byteArrayOf(0x3b, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x41), ringPacket(2))
    }
    @Test fun allowlistRequiresExplicitPackages() {
        assertEquals(emptySet<String>(), ringAllowedPackages(" * , , com.*\n"))
        assertEquals(setOf("com.example.meowmba", "org.mozilla.firefox"), ringAllowedPackages("com.example.meowmba,\norg.mozilla.firefox com.example.meowmba"))
    }
    @Test fun controlsRequireConnectionUnpausedVerifiedGestureAndAllowedApp() {
        val ready = RingState(connected = true, paused = false, verifiedGestures = setOf("Next", "Double tap"))
        assertEquals(RingAction.SCROLL_DOWN, ringActionFor(ready, "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(paused = true), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(connected = false), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(testing = true), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Previous", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Next", "com.android.settings"))
        assertEquals(RingAction.NONE, ringActionFor(ready.copy(allowlist = ""), "Next", "com.example.meowmba"))
        assertEquals(RingAction.NONE, ringActionFor(ready, "Next", null))
        assertEquals(RingAction.SCROLL_UP, ringActionFor(ready.copy(reverse = true), "Next", "com.example.meowmba"))
        assertEquals(RingAction.BACK, ringActionFor(ready.copy(reverse = true), "Double tap", "com.example.meowmba"))
    }
}
